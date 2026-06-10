package ru.ytkab0bp.slicebeam.render;

import static android.opengl.GLES30.*;
import static ru.ytkab0bp.slicebeam.utils.DebugUtils.assertTrue;

import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.util.Log;

import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent;
import ru.ytkab0bp.slicebeam.events.SelectedObjectChangedEvent;
import ru.ytkab0bp.slicebeam.slic3r.Bed3D;
import ru.ytkab0bp.slicebeam.slic3r.GCodeProcessorResult;
import ru.ytkab0bp.slicebeam.slic3r.GCodeViewer;
import ru.ytkab0bp.slicebeam.slic3r.GLModel;
import ru.ytkab0bp.slicebeam.slic3r.GLShaderProgram;
import ru.ytkab0bp.slicebeam.slic3r.GLShadersManager;
import ru.ytkab0bp.slicebeam.slic3r.Model;
import ru.ytkab0bp.slicebeam.slic3r.PaintSession;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rUtils;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.DoubleMatrix;
import ru.ytkab0bp.slicebeam.utils.FillBedPlanner;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.Vec3d;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.GLView;

public class GLRenderer implements GLSurfaceView.Renderer {
    private final static float FOV = 60f;
    private final static float NEAR_PLANE = 10f;
    private final static float FAR_PLANE = 1000f;
    private final static int MAX_FILL_BED_OBJECTS = 256;

    private Camera camera = new Camera();
    private double[] projectionMatrix = new double[16];
    private double[] modelMatrix = new double[16];
    private double[] normalMatrix = new double[12];
    private double[] outModelMatrix = new double[16];

    private int viewportWidth, viewportHeight;

    private boolean cameraIsDirty = true;

    // Instance values, should be released
    private Bed3D bed;
    private int lastConfigUid;
    private GLShadersManager shadersManager;
    private GLModel backgroundModel;
    private GLModel selectionModel;
    private List<GLModel> glModels = new ArrayList<>();

    private Model model;

    private GCodeProcessorResult gcodeResult;
    private GCodeViewer viewer;
    private boolean isViewerEnabled;

    // Primary selection (drives the transform gizmo, flatten, fill-bed, duplicate).
    // selectedObjects holds the full multi-selection; when non-empty it always contains selectedObject.
    private int selectedObject = -1;
    private final Set<Integer> selectedObjects = new LinkedHashSet<>();
    private double selX, selY, selZ;
    private double selRotX, selRotY, selRotZ;
    private double selScaleX = 1, selScaleY = 1, selScaleZ = 1;

    private long lastDraw;
    private GLView glView;
    private Vec3d translate = new Vec3d();
    private Vec3d rotate = new Vec3d();
    private ArrayList<GLModel.HitResult> raycastHits = new ArrayList<>();

    private Vec3d bbMin = new Vec3d(), bbMax = new Vec3d();
    private boolean isInFlattenMode;
    private ArrayList<GLModel> flattenPlanes = new ArrayList<>();

    // --- Multi-color painting ---
    public static final int PAINT_TOOL_BRUSH = 0, PAINT_TOOL_BUCKET = 1, PAINT_TOOL_HEIGHT = 2;
    private static class PaintOverlay { GLModel model; int color; }
    private PaintSession paintSession;
    private int paintObject = -1;
    private boolean paintMode;
    private int paintFilament = 2;          // 1-based palette index; 1 = base, so default to first painted color
    private int paintTool = PAINT_TOOL_BRUSH;
    private double paintBrushRadius = 4.0;   // mm
    private int[] paintPalette = new int[0];
    private final List<PaintOverlay> paintOverlays = new ArrayList<>();
    // Persisted painting per object (built from committed mmu data), so colors stay after exiting paint mode.
    private final java.util.Map<Integer, List<PaintOverlay>> committedOverlays = new java.util.HashMap<>();

    // Cached per-frame values to avoid repeated lookups inside onDrawFrame
    private int cachedAccentColor, cachedHoverColor, cachedBgTop, cachedBgBottom;

    public Camera getCamera() {
        return camera;
    }

    public Bed3D getBed() {
        return bed;
    }

    public double[] getProjectionMatrix() {
        return projectionMatrix;
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }

    public void setGCodeViewer(GCodeProcessorResult result) {
        this.isViewerEnabled = result != null;
        this.gcodeResult = result;

        if (!isViewerEnabled && viewer != null) {
            viewer.release();
            viewer = null;
        }
    }

    public GLRenderer(GLView glView) {
        this.glView = glView;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {}

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (bed != null) {
            onDestroy();
        }
        onCreate();
        glViewport(0, 0, viewportWidth = width, viewportHeight = height);
        updateProjection();
    }

    public void updateProjection() {
        if (bed == null || !bed.isValid()) return;
        // Any external camera position change comes through here — invalidate cached view matrix.
        camera.invalidate();
        float aspectRatio = (float) viewportWidth / viewportHeight;
        float invZoom = 1f / camera.getZoom();
        if (Prefs.isOrthoProjectionEnabled()) {
            Vec3d diff = bed.getVolumeMax().clone().add(bed.getVolumeMin().clone());
            double scale = (Math.max(diff.x, diff.y) / 2f + 10f) * invZoom;

            float ratioHorizontal = aspectRatio > 1 ? aspectRatio : 1;
            float ratioVertical = aspectRatio < 1 ? 1f / aspectRatio : 1;
            DoubleMatrix.orthoM(projectionMatrix, 0, -scale * ratioHorizontal, scale * ratioHorizontal, -scale * ratioVertical, scale * ratioVertical, NEAR_PLANE, FAR_PLANE);
        } else {
            DoubleMatrix.perspectiveM(projectionMatrix, 0, FOV * invZoom * (viewportWidth > viewportHeight ? 1 / aspectRatio : 1), aspectRatio, NEAR_PLANE, FAR_PLANE);
        }
    }

    public int getSelectedObject() {
        return selectedObject;
    }

    public boolean setSelectedObject(int selectedObject) {
        if (model == null && selectedObject != -1) return false;
        if (model != null && (selectedObject < -1 || selectedObject >= model.getObjectsCount())) return false;
        boolean sameSingle = this.selectedObject == selectedObject
                && selectedObjects.size() == (selectedObject == -1 ? 0 : 1)
                && (selectedObject == -1 || selectedObjects.contains(selectedObject));
        if (sameSingle) return false;

        this.selectedObject = selectedObject;
        selectedObjects.clear();
        if (selectedObject != -1) selectedObjects.add(selectedObject);
        selX = selY = selZ = 0;
        selRotX = selRotY = selRotZ = 0;
        selScaleX = selScaleY = selScaleZ = 1;
        SliceBeam.EVENT_BUS.fireEvent(new SelectedObjectChangedEvent());
        return true;
    }

    public boolean isSelected(int i) {
        return selectedObjects.contains(i);
    }

    /**
     * Make {@code i} the primary (gizmo) object when a drag begins. If {@code i} is already part of a
     * multi-selection, the whole selection is preserved so the drag moves the entire group; otherwise
     * this collapses to a single selection like a normal tap.
     */
    public boolean focusForDrag(int i) {
        if (model == null || i < 0 || i >= model.getObjectsCount()) return false;
        if (selectedObjects.size() > 1 && selectedObjects.contains(i)) {
            boolean changed = selectedObject != i;
            selectedObject = i;
            selX = selY = selZ = 0;
            selRotX = selRotY = selRotZ = 0;
            selScaleX = selScaleY = selScaleZ = 1;
            if (changed) SliceBeam.EVENT_BUS.fireEvent(new SelectedObjectChangedEvent());
            return true;
        }
        return setSelectedObject(i);
    }

    public int getSelectedObjectsCount() {
        return selectedObjects.size();
    }

    /** Select every object on the bed. The first object becomes the primary (gizmo) selection. */
    public boolean selectAllObjects() {
        if (model == null || model.getObjectsCount() == 0) return false;
        selectedObjects.clear();
        for (int i = 0; i < model.getObjectsCount(); i++) selectedObjects.add(i);
        selectedObject = 0;
        selX = selY = selZ = 0;
        selRotX = selRotY = selRotZ = 0;
        selScaleX = selScaleY = selScaleZ = 1;
        SliceBeam.EVENT_BUS.fireEvent(new SelectedObjectChangedEvent());
        return true;
    }

    public void invalidateGlModel(int i) {
        if (model == null) return;
        if (i < glModels.size()) {
            GLModel glModel = glModels.get(i);
            glModel.reset();
            glModel.initFrom(model, i);
        }
        invalidateCommittedOverlays(i);
    }

    public void invalidateSelectionObject() {
        if (selectionModel != null) {
            selectionModel.reset();
        }
    }

    public void resetGlModels() {
        if (model == null) return;
        clearAllCommittedOverlays();
        for (int i = 0; i < model.getObjectsCount(); i++) {
            if (i >= glModels.size()) continue;

            GLModel glModel = glModels.get(i);
            glModel.reset();
            glModel.initFrom(model, i);
        }
    }

    public boolean invalidateFlattenMode() {
        if (isInFlattenMode) {
            setInFlattenMode(true);
            return true;
        }
        return false;
    }

    public boolean resetFlattenMode() {
        if (isInFlattenMode) {
            setInFlattenMode(false);
            return true;
        }
        return false;
    }

    public void setInFlattenMode(boolean inFlattenMode) {
        isInFlattenMode = inFlattenMode;

        for (int i = 0, c = flattenPlanes.size(); i < c; i++) {
            flattenPlanes.get(i).release();
        }
        flattenPlanes.clear();

        if (isInFlattenMode) {
            List<GLModel> planes = model.createFlattenPlanes(selectedObject);
            flattenPlanes.addAll(planes);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (backgroundModel == null) return; // Not initialized yet
        long dt = Math.min(System.currentTimeMillis() - lastDraw, 16);
        lastDraw = System.currentTimeMillis();

        // Fetch view matrix once per frame — getViewModelMatrix() is lazy/cached in Camera
        double[] viewMatrix = camera.getViewModelMatrix();

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glDisable(GL_DEPTH_TEST);
        GLShaderProgram shader = shadersManager.get(GLShadersManager.SHADER_BACKGROUND);
        shader.startUsing();
        shader.setUniformColor("top_color", cachedBgTop);
        shader.setUniformColor("bottom_color", cachedBgBottom);
        backgroundModel.render();
        shader.stopUsing();
        glEnable(GL_DEPTH_TEST);

        boolean bottom = Prefs.isOrthoProjectionEnabled() ? camera.getDirForward().z > 0 : camera.getDirToBed().z > 0;
        if (lastConfigUid != SliceBeam.CONFIG_UID) {
            configureBed();
        }
        if (bed.isValid()) {
            bed.render(shadersManager, bottom, viewMatrix, projectionMatrix, 1f / camera.getZoom());
        }

        if (isViewerEnabled) {
            if (viewer == null) {
                viewer = new GCodeViewer();
                viewer.initGL();
                viewer.setThemeColors();
                viewer.load(gcodeResult);
            }

            viewer.render(viewMatrix, projectionMatrix);
        }
        if (viewer == null && model != null) {
            shader = shadersManager.get(GLShadersManager.SHADER_GOURAUD_LIGHT);
            shader.startUsing();
            int color = cachedAccentColor;
            int hoverColor = cachedHoverColor;
            if (!paintMode) paintPalette = Prefs.getFilamentPalette(); // keep persisted paint colors current

            for (int i = 0; i < model.getObjectsCount(); i++) {
                boolean left = model.isLeftHanded(i);
                if (left) {
                    glFrontFace(GL_CW);
                }

                boolean selected = selectedObjects.contains(i);
                boolean primary = i == selectedObject;

                shader.setUniform("emission_factor", 0.05f);
                DoubleMatrix.setIdentityM(modelMatrix, 0);
                if (selected) {
                    // Live move delta applies to the whole selection (group move).
                    DoubleMatrix.translateM(modelMatrix, 0, selX, selY, selZ);

                    // Live rotation/scale only previews on the primary object (the gizmo target).
                    if (primary) {
                        model.getTranslation(i, translate);
                        model.getRotation(i, rotate);
                        DoubleMatrix.translateM(modelMatrix, 0, translate.x, translate.y, translate.z);
                        DoubleMatrix.rotateM(modelMatrix, 0, selRotX, 1, 0, 0);
                        DoubleMatrix.rotateM(modelMatrix, 0, selRotY, 0, 1, 0);
                        DoubleMatrix.rotateM(modelMatrix, 0, selRotZ, 0, 0, 1);
                        DoubleMatrix.scaleM(modelMatrix, 0, selScaleX, selScaleY, selScaleZ);
                        DoubleMatrix.translateM(modelMatrix, 0, -translate.x, -translate.y, -translate.z);
                    }
                }
                DoubleMatrix.multiplyMM(outModelMatrix, 0, viewMatrix, 0, modelMatrix, 0);

                shader.setUniformMatrix4fv("view_model_matrix", outModelMatrix);
                shader.setUniformMatrix4fv("projection_matrix", projectionMatrix);

                Slic3rUtils.calcViewNormalMatrix(viewMatrix, modelMatrix, normalMatrix);
                shader.setUniformMatrix3fv("view_normal_matrix", normalMatrix);

                shader.setUniform("volume_mirrored", left);

                if (glModels.size() < i + 1) {
                    GLModel glModel = new GLModel();
                    glModel.initFrom(model, i);
                    glModels.add(glModel);
                }
                GLModel glModel = glModels.get(i);
                boolean hovering = glModel.isHovering || selected;
                // FIXME: Render is lagging out with hover progress
//                if (hovering && glModel.hoverProgress < 1) {
//                    glModel.hoverProgress = Math.min(glModel.hoverProgress + dt / 50f, 1);
//                    glView.queueEvent(() -> glView.requestRender());
//                } else if (!hovering && glModel.hoverProgress > 0) {
//                    glModel.hoverProgress = Math.max(glModel.hoverProgress - dt / 50f, 0);
//                    glView.queueEvent(() -> glView.requestRender());
//                }
                boolean paintingThis = paintMode && i == paintObject;
                // While painting, show the live session overlays; otherwise the persisted committed ones.
                List<PaintOverlay> overlays = paintingThis ? paintOverlays : getCommittedOverlays(i);
                // The unpainted model always shows the T0 (filament 1) color; painted areas overlay on top.
                int baseColor = paintPalette.length > 0 ? paintPalette[0] : color;
                glModel.setColor(ColorUtils.blendARGB(baseColor, hoverColor, hovering ? 1 : 0));
                glModel.render();

                if (!overlays.isEmpty()) {
                    // Painted facets share the object's surface; offset to avoid z-fighting.
                    glEnable(GL_POLYGON_OFFSET_FILL);
                    glPolygonOffset(-1f, -1f);
                    for (PaintOverlay o : overlays) {
                        o.model.setColor(o.color);
                        o.model.render();
                    }
                    glPolygonOffset(0f, 0f);
                    glDisable(GL_POLYGON_OFFSET_FILL);
                }

                if (left) {
                    glFrontFace(GL_CCW);
                }

                if (selected) {
                    shader.stopUsing();

                    GLShaderProgram flat = shadersManager.get(GLShadersManager.SHADER_FLAT);
                    glLineWidth(ViewUtils.dp(1.5f));

                    flat.startUsing();
                    flat.setUniformMatrix4fv("view_model_matrix", outModelMatrix);
                    flat.setUniformMatrix4fv("projection_matrix", projectionMatrix);

                    if (selectionModel == null) {
                        selectionModel = new GLModel();
                    }
                    selectionModel.initBoundingBox(model, i);
                    selectionModel.setColor(hoverColor);
                    selectionModel.render();

                    flat.stopUsing();

                    shader.startUsing();
                }

                if (isInFlattenMode) {
                    shader.stopUsing();

                    GLShaderProgram flat = shadersManager.get(GLShadersManager.SHADER_FLAT);

                    flat.startUsing();
                    glEnable(GL_BLEND);
                    flat.setUniformMatrix4fv("view_model_matrix", outModelMatrix);
                    flat.setUniformMatrix4fv("projection_matrix", projectionMatrix);

                    for (GLModel plane : flattenPlanes) {
                        boolean hoveringPlane = plane.isHovering;
                        int clr = ColorUtils.blendARGB(hoverColor, color, hoveringPlane ? 1 : 0);
                        plane.setColor(ColorUtils.setAlphaComponent(clr, (int) (Color.alpha(clr) * 0.75f)));
                        plane.render();
                    }

                    glDisable(GL_BLEND);
                    flat.stopUsing();

                    shader.startUsing();
                }
            }
            shader.stopUsing();
        }

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
    }

    public boolean deleteObject(int i) {
        if (model == null) return false;
        assertTrue(i >= 0 && i < model.getObjectsCount());

        model.deleteObject(i);
        if (glModels.size() > i) {
            glModels.remove(i).release();
        }

        // Keep the multi-selection consistent: drop the deleted index and shift higher ones down.
        Set<Integer> shifted = new LinkedHashSet<>();
        for (int idx : selectedObjects) {
            if (idx == i) continue;
            shifted.add(idx > i ? idx - 1 : idx);
        }
        selectedObjects.clear();
        selectedObjects.addAll(shifted);

        if (i == selectedObject) {
            selectedObject = -1;
            selX = selY = selZ = 0;
            selRotX = selRotY = selRotZ = 0;
            selScaleX = selScaleY = selScaleZ = 1;
            SliceBeam.EVENT_BUS.fireEvent(new SelectedObjectChangedEvent());
        } else if (selectedObject > i) {
            selectedObject--;
        }

        if (model.getObjectsCount() == 0) {
            model.release();
            model = null;
            selectedObjects.clear();
        }
        SliceBeam.EVENT_BUS.fireEvent(new ObjectsListChangedEvent());
        return true;
    }

    /**
     * Batch-fill the bed with copies of the selected object.
     * Uses bounding box math to estimate capacity, adds all copies at once,
     * then arranges a single time — much faster than the old one-at-a-time approach.
     */
    public int fillBedWithSelectedObject() {
        if (model == null || bed == null || !bed.isValid() || isViewerEnabled || selectedObject == -1) return 0;

        int maxAttempts = FillBedPlanner.copyAttemptsForLimit(model.getObjectsCount(), MAX_FILL_BED_OBJECTS);
        if (maxAttempts == 0) return 0;

        // Calculate how many copies can fit using bounding box math
        int estimatedCapacity = estimateBedCapacity(selectedObject);
        int copiesToAdd = Math.min(Math.max(estimatedCapacity - model.getObjectsCount(), 0), maxAttempts);
        if (copiesToAdd <= 0) copiesToAdd = maxAttempts; // Fallback if estimate fails

        int sourceObject = selectedObject;
        Model sourceModel = new Model();
        int added = 0;
        try {
            sourceModel.addObject(model, sourceObject);

            // Batch-add all copies first
            for (int i = 0; i < copiesToAdd; i++) {
                model.addObject(sourceModel, 0);
                added++;
            }

            model.resetBoundingBox();

            // Single arrange pass for all copies
            if (!bed.arrange(model)) {
                // Arrange failed — remove copies that couldn't be placed
                // by trimming from the end until arrange succeeds
                while (added > 0) {
                    model.deleteObject(model.getObjectsCount() - 1);
                    added--;
                    model.resetBoundingBox();
                    if (bed.arrange(model)) break;
                }
            }
        } finally {
            sourceModel.release();
        }

        if (added > 0) {
            model.resetBoundingBox();
            resetGlModels();
            SliceBeam.EVENT_BUS.fireEvent(new ObjectsListChangedEvent());
        }
        return added;
    }

    /**
     * Batch-fill version that replaces the old step-by-step approach.
     * Adds all copies at once then arranges once, rather than arrange per copy.
     */
    public int fillBedWithSelectedObjectStep() {
        // Delegate to the batch method — no more step-by-step needed
        return fillBedWithSelectedObject();
    }

    /**
     * Estimate how many copies of an object can fit on the bed
     * based on bounding box dimensions.
     */
    private int estimateBedCapacity(int objectIndex) {
        Vec3d bedMin = bed.getVolumeMin();
        Vec3d bedMax = bed.getVolumeMax();
        double bedWidth = bedMax.x - bedMin.x;
        double bedDepth = bedMax.y - bedMin.y;

        Vec3d objMin = new Vec3d();
        Vec3d objMax = new Vec3d();
        model.getBoundingBoxApprox(objectIndex, objMin, objMax);
        double objWidth = objMax.x - objMin.x;
        double objDepth = objMax.y - objMin.y;

        if (objWidth <= 0 || objDepth <= 0) return MAX_FILL_BED_OBJECTS;

        // Add spacing between objects — must match the arrange min_obj_distance (6mm gap)
        double spacing = 6.0;
        int cols = Math.max(1, (int) ((bedWidth + spacing) / (objWidth + spacing)));
        int rows = Math.max(1, (int) ((bedDepth + spacing) / (objDepth + spacing)));

        return cols * rows;
    }

    /**
     * Duplicate the currently selected object (single copy).
     */
    public int duplicateSelectedObject() {
        if (model == null || bed == null || !bed.isValid() || isViewerEnabled || selectedObject == -1) return 0;

        Model sourceModel = new Model();
        try {
            sourceModel.addObject(model, selectedObject);
            model.addObject(sourceModel, 0);
            model.resetBoundingBox();
            bed.arrange(model);
            model.resetBoundingBox();
            resetGlModels();
            SliceBeam.EVENT_BUS.fireEvent(new ObjectsListChangedEvent());
            return 1;
        } finally {
            sourceModel.release();
        }
    }

    // ---- Multi-color painting (all methods run on the GL thread) ----

    public boolean isPaintMode() { return paintMode; }
    public int getPaintObject() { return paintObject; }
    public int getPaintFilament() { return paintFilament; }
    public void setPaintFilament(int f) { paintFilament = f; }
    public int getPaintTool() { return paintTool; }
    public void setPaintTool(int t) { paintTool = t; }
    public double getPaintBrushRadius() { return paintBrushRadius; }
    public void setPaintBrushRadius(double r) { paintBrushRadius = r; }

    public void beginPaint(int objIdx) {
        if (model == null || objIdx < 0 || objIdx >= model.getObjectsCount()) return;
        endPaintInternal(false);
        // Ensure the object's GLModel + raycast data exist (built lazily in onDrawFrame otherwise).
        while (glModels.size() <= objIdx) {
            GLModel gm = new GLModel();
            gm.initFrom(model, glModels.size());
            glModels.add(gm);
        }
        paintSession = new PaintSession(model, objIdx);
        if (!paintSession.isValid()) { paintSession = null; return; }
        paintObject = objIdx;
        paintMode = true;
        paintPalette = Prefs.getFilamentPalette();
        rebuildPaintOverlays();
    }

    public void endPaint(boolean commit) {
        endPaintInternal(commit);
    }

    private void endPaintInternal(boolean commit) {
        if (paintSession != null) {
            if (commit) paintSession.commit();
            paintSession.end();
            paintSession = null;
        }
        for (PaintOverlay o : paintOverlays) o.model.release();
        paintOverlays.clear();
        if (paintObject != -1) invalidateCommittedOverlays(paintObject); // rebuild persisted view from committed data
        paintObject = -1;
        paintMode = false;
    }

    /** Set object i's base filament (1-based) and refresh its persisted colors. */
    public void setObjectBaseFilament(int i, int filament) {
        if (model == null) return;
        model.setExtruder(i, filament);
        invalidateCommittedOverlays(i);
    }

    public void paintAt(float x, float y) {
        if (paintSession == null || paintObject < 0 || paintObject >= glModels.size()) return;
        GLModel gl = glModels.get(paintObject);
        gl.getRaycaster().raycast(this, raycastHits, x, y);
        if (raycastHits.isEmpty()) return;
        Vec3d hit = raycastHits.get(0).position;
        double[] h = new double[] { hit.x, hit.y, hit.z };
        if (paintTool == PAINT_TOOL_BUCKET) {
            paintSession.bucket(h, -1, paintFilament, true, 30.0);
        } else if (paintTool == PAINT_TOOL_HEIGHT) {
            // Paint everything up to the tapped height.
            paintSession.height(-1e9, hit.z, paintFilament);
        } else {
            paintSession.brush(h, -1, paintBrushRadius, paintFilament, camera.position.asDoubleArray());
        }
        // Don't commit on every drag event (expensive) — the live session overlays show the paint,
        // and endPaint() commits to the model. This keeps fast drags from painting sparsely.
        rebuildPaintOverlays();
    }

    public void heightPaint(double zMin, double zMax) {
        if (paintSession == null) return;
        paintSession.height(zMin, zMax, paintFilament);
        paintSession.commit();
        rebuildPaintOverlays();
    }

    public void clearPaint() {
        if (paintSession == null) return;
        paintSession.clear();
        paintSession.commit();
        rebuildPaintOverlays();
    }

    private void rebuildPaintOverlays() {
        for (PaintOverlay o : paintOverlays) o.model.release();
        paintOverlays.clear();
        if (paintSession == null) return;
        int n = Math.max(paintPalette.length, 1);
        for (int f = 1; f <= n; f++) {
            GLModel gl = new GLModel();
            int tris = paintSession.buildOverlay(gl, f);
            if (tris > 0) {
                PaintOverlay o = new PaintOverlay();
                o.model = gl;
                o.color = (f - 1) < paintPalette.length ? paintPalette[f - 1] : cachedAccentColor;
                paintOverlays.add(o);
            } else {
                gl.release();
            }
        }
    }

    private List<PaintOverlay> getCommittedOverlays(int i) {
        List<PaintOverlay> list = committedOverlays.get(i);
        if (list == null) {
            list = new ArrayList<>();
            if (model != null && model.hasPaint(i)) {
                int[] pal = paintPalette;
                int n = Math.max(pal.length, 1);
                for (int f = 1; f <= n; f++) {
                    GLModel gl = new GLModel();
                    int tris = model.buildPaintOverlay(i, gl, f);
                    if (tris > 0) {
                        PaintOverlay o = new PaintOverlay();
                        o.model = gl;
                        o.color = (f - 1) < pal.length ? pal[f - 1] : cachedAccentColor;
                        list.add(o);
                    } else {
                        gl.release();
                    }
                }
            }
            committedOverlays.put(i, list);
        }
        return list;
    }

    private void invalidateCommittedOverlays(int i) {
        List<PaintOverlay> list = committedOverlays.remove(i);
        if (list != null) for (PaintOverlay o : list) o.model.release();
    }

    private void clearAllCommittedOverlays() {
        for (List<PaintOverlay> l : committedOverlays.values())
            for (PaintOverlay o : l) o.model.release();
        committedOverlays.clear();
    }

    public int raycastObjectIndex(float x, float y) {
        if (model == null) return -1;
        double minDistance = Double.MAX_VALUE;
        int j = -1;
        for (int i = 0, c = model.getObjectsCount(); i < c; i++) {
            if (i >= glModels.size()) continue;

            GLModel glModel = glModels.get(i);
            glModel.getRaycaster().raycast(this, raycastHits, x, y);

            boolean hovered = !raycastHits.isEmpty();
            if (hovered) {
                double distance = raycastHits.get(0).position.distance(camera.position);
                if (distance < minDistance) {
                    minDistance = distance;
                    j = i;
                }
            }
        }
        return j;
    }

    public boolean onClick(float x, float y) {
        if (model == null || isViewerEnabled) return false;

        int j = raycastObjectIndex(x, y);

        if (isInFlattenMode && (j == selectedObject || j == -1)) {
            int minPlane = -1;
            double minDistancePlane = Double.MAX_VALUE;

            for (int i = 0, c = flattenPlanes.size(); i < c; i++) {
                GLModel glModel = flattenPlanes.get(i);
                glModel.getRaycaster().raycast(this, raycastHits, x, y);

                double minDistanceRay = Double.MAX_VALUE;
                if (!raycastHits.isEmpty()) {
                    for (GLModel.HitResult res : raycastHits) {
                        double distance = res.position.distance(camera.position);
                        if (distance < minDistanceRay) {
                            minDistanceRay = distance;
                        }
                    }
                }
                if (minDistanceRay < minDistancePlane) {
                    minDistancePlane = minDistanceRay;
                    minPlane = i;
                }
            }

            if (minPlane != -1) {
                GLModel glModel = flattenPlanes.get(minPlane);
                model.flattenRotate(selectedObject, glModel);
                model.ensureOnBed(selectedObject);

                invalidateGlModel(selectedObject);
                for (int k = 0, l = flattenPlanes.size(); k < l; k++) {
                    flattenPlanes.get(k).release();
                }
                flattenPlanes.clear();

                selectedObject = -1;
                selectedObjects.clear();
                SliceBeam.EVENT_BUS.fireEvent(new SelectedObjectChangedEvent());
                return true;
            }

            return false;
        }

        boolean render = j != selectedObject || j != -1;
        selectedObject = j == selectedObject ? -1 : j;
        // A tap replaces any multi-selection with the single tapped object (or clears it).
        selectedObjects.clear();
        if (selectedObject != -1) selectedObjects.add(selectedObject);
        if (render) {
            if (isInFlattenMode) {
                setInFlattenMode(false);
            }
            if (selectedObject == -1) {
                selX = selY = selZ = 0;
                selRotX = selRotY = selRotZ = 0;
                selScaleX = selScaleY = selScaleZ = 1;
            }
            SliceBeam.EVENT_BUS.fireEvent(new SelectedObjectChangedEvent());
        }
        return render;
    }

    public boolean hover(float x, float y) {
        if (model == null || isViewerEnabled) return false;

        boolean render = false;
        double minDistance = Double.MAX_VALUE;
        GLModel minModel = null;
        for (int i = 0, c = model.getObjectsCount(); i < c; i++) {
            if (i >= glModels.size()) continue;

            GLModel glModel = glModels.get(i);
            glModel.getRaycaster().raycast(this, raycastHits, x, y);

            boolean hovered = !raycastHits.isEmpty();
            if (hovered) {
                double distance = raycastHits.get(0).position.distance(camera.position);
                if (distance < minDistance) {
                    minDistance = distance;
                    minModel = glModel;
                }
            }
        }
        for (int i = 0, c = model.getObjectsCount(); i < c; i++) {
            if (i >= glModels.size()) continue;

            GLModel glModel = glModels.get(i);

            boolean hovered = minModel == glModel;
            if (glModel.isHovering && !hovered) {
                glModel.isHovering = false;
                render = true;
            } else if (!glModel.isHovering && hovered) {
                glModel.isHovering = true;
                render = true;
            }
        }

        if (isInFlattenMode) {
            int minPlane = -1;
            double minDistancePlane = Double.MAX_VALUE;

            for (int i = 0, c = flattenPlanes.size(); i < c; i++) {
                GLModel glModel = flattenPlanes.get(i);
                glModel.getRaycaster().raycast(this, raycastHits, x, y);

                double minDistanceRay = Double.MAX_VALUE;
                if (!raycastHits.isEmpty()) {
                    for (GLModel.HitResult res : raycastHits) {
                        double distance = res.position.distance(camera.position);
                        if (distance < minDistanceRay) {
                            minDistanceRay = distance;
                        }
                    }
                }
                if (minDistanceRay < minDistancePlane) {
                    minDistancePlane = minDistanceRay;
                    minPlane = i;
                }
            }

            if (minPlane != -1) {
                for (int i = 0; i < flattenPlanes.size(); i++) {
                    flattenPlanes.get(i).isHovering = i == minPlane;
                }
                render = true;
            } else {
                for (int i = 0; i < flattenPlanes.size(); i++) {
                    if (flattenPlanes.get(i).isHovering) {
                        flattenPlanes.get(i).isHovering = false;
                        render = true;
                    }
                }
            }
        }

        return render;
    }

    public boolean stopHover() {
        if (model == null) return false;

        boolean render = false;
        for (int i = 0, c = model.getObjectsCount(); i < c; i++) {
            if (i >= glModels.size()) continue;

            GLModel glModel = glModels.get(i);
            if (glModel.isHovering) {
                glModel.isHovering = false;
                render = true;
            }
        }
        return render;
    }

    public void setSelectionRotation(double x, double y, double z) {
        selRotX = x;
        selRotY = y;
        selRotZ = z;
    }

    public void setSelectionScale(double x, double y, double z) {
        selScaleX = x;
        selScaleY = y;
        selScaleZ = z;
    }

    public void setSelectionTranslation(double x, double y, double z) {
        selX = x;
        selY = y;
        selZ = z;
    }

    /** Commit a translation to every selected object (used for group drag-move). */
    public void translateSelectedObjects(double dx, double dy, double dz) {
        if (model == null) return;
        for (int idx : selectedObjects) {
            if (idx >= 0 && idx < model.getObjectsCount()) {
                model.translate(idx, dx, dy, dz);
                invalidateGlModel(idx);
            }
        }
        selX = selY = selZ = 0;
    }

    /** Move the current selection so its combined footprint is centered on the bed. */
    public void centerSelectionOnBed() {
        if (model == null || bed == null || !bed.isValid() || selectedObjects.isEmpty()) return;

        Vec3d bedMin = bed.getVolumeMin(), bedMax = bed.getVolumeMax();
        double bedCx = (bedMin.x + bedMax.x) / 2.0;
        double bedCy = (bedMin.y + bedMax.y) / 2.0;

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int idx : selectedObjects) {
            if (idx < 0 || idx >= model.getObjectsCount()) continue;
            model.getBoundingBoxExact(idx, bbMin, bbMax);
            minX = Math.min(minX, bbMin.x);
            minY = Math.min(minY, bbMin.y);
            maxX = Math.max(maxX, bbMax.x);
            maxY = Math.max(maxY, bbMax.y);
        }
        if (minX > maxX) return;

        double dx = bedCx - (minX + maxX) / 2.0;
        double dy = bedCy - (minY + maxY) / 2.0;
        for (int idx : selectedObjects) {
            if (idx >= 0 && idx < model.getObjectsCount()) {
                model.translate(idx, dx, dy, 0);
                invalidateGlModel(idx);
            }
        }
        selX = selY = selZ = 0;
    }

    /** Delete every selected object. Returns how many were removed. */
    public int deleteSelectedObjects() {
        if (model == null || selectedObjects.isEmpty()) return 0;

        List<Integer> indices = new ArrayList<>(selectedObjects);
        Collections.sort(indices, Collections.reverseOrder());

        int removed = 0;
        for (int idx : indices) {
            if (model == null) break;
            if (idx >= 0 && idx < model.getObjectsCount()) {
                model.deleteObject(idx);
                if (glModels.size() > idx) glModels.remove(idx).release();
                removed++;
            }
        }

        selectedObjects.clear();
        selectedObject = -1;
        selX = selY = selZ = 0;
        selRotX = selRotY = selRotZ = 0;
        selScaleX = selScaleY = selScaleZ = 1;

        if (model != null && model.getObjectsCount() == 0) {
            model.release();
            model = null;
        }

        SliceBeam.EVENT_BUS.fireEvent(new SelectedObjectChangedEvent());
        SliceBeam.EVENT_BUS.fireEvent(new ObjectsListChangedEvent());
        return removed;
    }

    /** Remove every object from the bed. Returns how many were removed. */
    public int deleteAllObjects() {
        if (model == null) return 0;
        int removed = model.getObjectsCount();

        for (int i = 0; i < glModels.size(); i++) glModels.get(i).release();
        glModels.clear();

        model.release();
        model = null;
        selectedObjects.clear();
        selectedObject = -1;
        selX = selY = selZ = 0;
        selRotX = selRotY = selRotZ = 0;
        selScaleX = selScaleY = selScaleZ = 1;
        if (isInFlattenMode) setInFlattenMode(false);

        SliceBeam.EVENT_BUS.fireEvent(new SelectedObjectChangedEvent());
        SliceBeam.EVENT_BUS.fireEvent(new ObjectsListChangedEvent());
        return removed;
    }

    public void setModel(Model model) {
        this.model = model;
        resetGlModels();
    }

    public Model getModel() {
        return model;
    }

    public GCodeProcessorResult getGcodeResult() {
        return gcodeResult;
    }

    public GCodeViewer getViewer() {
        return viewer;
    }

    private void configureBed() {
        try {
            lastConfigUid = SliceBeam.CONFIG_UID;
            SliceBeam.genCurrentConfig();
            bed.configure(SliceBeam.getCurrentConfigFile());
        } catch (Exception e) {
            Log.e("GLRenderer", "Failed to update config", e);
        }
    }

    public void refreshThemeColors() {
        cachedAccentColor = ThemesRepo.getColor(android.R.attr.colorAccent);
        cachedHoverColor = ThemesRepo.getColor(R.attr.modelHoverColor);
        cachedBgTop = ThemesRepo.getColor(R.attr.backgroundColorTop);
        cachedBgBottom = ThemesRepo.getColor(R.attr.backgroundColorBottom);
    }

    private void onCreate() {
        bed = new Bed3D();
        configureBed();
        refreshThemeColors();

        backgroundModel = new GLModel();
        backgroundModel.initBackgroundTriangles();
        shadersManager = new GLShadersManager();
        if (!bed.isValid()) return;

        if (cameraIsDirty) {
            Vec3d min = bed.getVolumeMin(), max = bed.getVolumeMax();
            Vec3d center = min.center(max);
            camera.origin.set(center);
            camera.origin.z = 0;

            camera.position.x = center.x - center.z * 2;
            camera.position.y = center.y - center.z * 2;
            camera.position.z = min.z + Math.sqrt(center.z * center.z * 8);
            cameraIsDirty = false;
        }
        if (isViewerEnabled) {
            viewer = new GCodeViewer();
            viewer.initGL();
            viewer.setThemeColors();
            viewer.load(gcodeResult);
        }
    }

    public void onDestroy() {
        endPaintInternal(false);
        clearAllCommittedOverlays();
        if (shadersManager != null) {
            shadersManager.clearShaders();
            shadersManager = null;
        }
        if (backgroundModel != null) {
            backgroundModel.release();
            backgroundModel = null;
        }
        if (selectionModel != null) {
            selectionModel.release();
            selectionModel = null;
        }
        if (bed != null) {
            bed.release();
            bed = null;
        }
        if (viewer != null) {
            viewer.release();
            viewer = null;
        }
        for (int i = 0; i < glModels.size(); i++) {
            glModels.get(i).release();
        }
        glModels.clear();

        isInFlattenMode = false;
        for (int i = 0; i < flattenPlanes.size(); i++) {
            flattenPlanes.get(i).release();
        }
        flattenPlanes.clear();
    }
}
