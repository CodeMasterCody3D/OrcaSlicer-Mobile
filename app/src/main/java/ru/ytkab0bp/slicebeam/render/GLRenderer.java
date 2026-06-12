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
    private double[] cutPlaneModelMatrix = new double[16];
    private double[] cutPlaneOutModelMatrix = new double[16];

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

    // Draggable prime tower preview. Drawn in bed coordinates and committed to wipe_tower_x/y.
    private GLModel primeTowerModel;
    private int currentPlateIndex = 0;
    private double primeTowerPreviewX = Double.NaN, primeTowerPreviewY = Double.NaN;
    private double primeTowerWidth = -1, primeTowerDepth = -1, primeTowerHeight = 14;
    private final ArrayList<GLModel.HitResult> primeTowerBedHits = new ArrayList<>();

    // Inactive plates of a multi-plate project. Each Model is bed-local; offsets are the plate's
    // grid position relative to the active plate (which always renders at the origin). Models are
    // owned by BedFragment; only the GLModels here are owned by the renderer.
    private final List<Model> inactivePlateModels = new ArrayList<>();
    private final List<List<GLModel>> inactivePlateGlModels = new ArrayList<>();
    private final List<double[]> inactivePlateOffsets = new ArrayList<>();
    private final double[] plateViewMatrix = new double[16];

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

    private boolean cutPlaneVisible = false;
    private float cutPlaneZ, cutPlaneRotX, cutPlaneRotY;
    private Vec3d cutPlaneMin = new Vec3d();
    private Vec3d cutPlaneMax = new Vec3d();
    private boolean cutPlaneDirty = false;
    private int cutPlaneVBO = -1;
    private int cutPlaneVAO = -1;
    private java.nio.FloatBuffer cutPlaneVertexBuffer;

    private static class CutConnectorPreview {
        Vec3d position;
        float radius, height;
        int type;
        double rotX, rotY;
    }

    private final java.util.Map<Integer, List<CutConnectorPreview>> cutConnectorPreviews = new java.util.HashMap<>();
    private boolean cutConnectorPlacement = false;
    private float cutConnectorRadius = 5.0f;
    private float cutConnectorHeight = 10.0f;
    private int cutConnectorType = 0;
    private boolean cutConnectorsDirty = true;
    private int cutConnectorsVBO = -1;
    private int cutConnectorsVAO = -1;
    private int cutConnectorsVertexCount = 0;
    private java.nio.FloatBuffer cutConnectorsVertexBuffer;

    public synchronized void setCutConnectorPlacement(boolean enabled) {
        cutConnectorPlacement = enabled;
    }

    public synchronized void setCutConnectorDefaults(float radius, float height, int type) {
        cutConnectorRadius = Math.max(0.1f, radius);
        cutConnectorHeight = Math.max(0.1f, height);
        cutConnectorType = Math.max(0, Math.min(2, type));
    }

    public synchronized void clearCutConnectorsForObject(int objIdx) {
        List<CutConnectorPreview> list = cutConnectorPreviews.remove(objIdx);
        if (list != null) cutConnectorsDirty = true;
        if (model != null && objIdx >= 0 && objIdx < model.getObjectsCount()) {
            model.clearConnectors(objIdx);
        }
    }

    public synchronized void removeLastCutConnectorForObject(int objIdx) {
        List<CutConnectorPreview> list = cutConnectorPreviews.get(objIdx);
        if (list == null || list.isEmpty()) return;
        int idx = list.size() - 1;
        list.remove(idx);
        if (model != null && objIdx >= 0 && objIdx < model.getObjectsCount()) {
            model.removeConnector(objIdx, idx);
        }
        cutConnectorsDirty = true;
    }

    public synchronized int getCutConnectorCount(int objIdx) {
        List<CutConnectorPreview> list = cutConnectorPreviews.get(objIdx);
        return list == null ? 0 : list.size();
    }

    public synchronized void clearAllCutConnectorPreviews() {
        cutConnectorPreviews.clear();
        cutConnectorsDirty = true;
    }

    public synchronized void setCutPlane(float z, float rotX, float rotY, Vec3d min, Vec3d max) {
        cutPlaneZ = z;
        cutPlaneRotX = rotX;
        cutPlaneRotY = rotY;
        cutPlaneMin.set(min.x, min.y, min.z);
        cutPlaneMax.set(max.x, max.y, max.z);
        cutPlaneDirty = true;
    }

    public synchronized void showCutPlane(boolean show) {
        cutPlaneVisible = show;
        if (!show) cutConnectorPlacement = false;
    }

    // --- Multi-color painting ---
    public static final int PAINT_TOOL_BRUSH = 0, PAINT_TOOL_BUCKET = 1, PAINT_TOOL_HEIGHT = 2;
    private static class PaintOverlay { GLModel model; int color; }
    private PaintSession paintSession;
    private int paintObject = -1;
    private boolean paintMode;
    private int paintFilament = 2;          // 1-based palette index; 1 = base, so default to first painted color
    private int paintTool = PAINT_TOOL_BRUSH;
    private double paintBrushRadius = 4.0;   // mm
    private double heightBandWidth = 10.0; // mm
    private double bucketAngleThreshold = 30.0; // degrees
    private boolean brushSphere = false;
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

    public void setCurrentPlateIndex(int currentPlateIndex) {
        this.currentPlateIndex = Math.max(0, currentPlateIndex);
        primeTowerPreviewX = Double.NaN;
        primeTowerPreviewY = Double.NaN;
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String formatDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) return String.valueOf((int)Math.rint(value));
        return String.format(java.util.Locale.US, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String vectorValue(String value, int index, double fallback) {
        if (value == null || value.trim().isEmpty()) return formatDouble(fallback);
        String[] parts = value.split("[,;]");
        int i = Math.max(0, Math.min(index, parts.length - 1));
        return parts[i].trim();
    }

    private static String vectorWithValue(String value, int index, double replacement) {
        int n = Math.max(index + 1, value == null || value.trim().isEmpty() ? 1 : value.split("[,;]").length);
        String[] out = new String[n];
        String[] parts = value == null || value.trim().isEmpty() ? new String[0] : value.split("[,;]");
        for (int i = 0; i < n; i++) {
            out[i] = i < parts.length && !parts[i].trim().isEmpty() ? parts[i].trim() : (parts.length > 0 ? parts[Math.max(0, parts.length - 1)].trim() : "15");
        }
        out[index] = formatDouble(replacement);
        return android.text.TextUtils.join(",", out);
    }

    private String configValue(ru.ytkab0bp.slicebeam.config.ConfigObject cfg, String key, String fallback) {
        String v = cfg != null ? cfg.get(key) : null;
        return v != null ? v : fallback;
    }

    private boolean boolConfig(ru.ytkab0bp.slicebeam.config.ConfigObject cfg, String key, boolean fallback) {
        String v = configValue(cfg, key, fallback ? "1" : "0");
        if (v == null) return fallback;
        v = v.trim().toLowerCase(java.util.Locale.US);
        return "1".equals(v) || "true".equals(v) || "yes".equals(v) || "on".equals(v);
    }

    public boolean isModelMultiColor() {
        if (model == null || model.getPointer() == 0 || model.getObjectsCount() == 0) return false;
        boolean hasPaint = false;
        java.util.Set<Integer> activeExtruders = new java.util.HashSet<>();
        for (int i = 0; i < model.getObjectsCount(); i++) {
            if (model.hasPaint(i)) hasPaint = true;
            activeExtruders.add(model.getExtruder(i));
        }
        return hasPaint || activeExtruders.size() > 1;
    }

    private boolean isPrimeTowerEnabled(ru.ytkab0bp.slicebeam.config.ConfigObject cfg) {
        if (cfg == null) return false;
        if (!isModelMultiColor()) return false;
        if (cfg.has("enable_prime_tower")) return boolConfig(cfg, "enable_prime_tower", false);
        return boolConfig(cfg, "wipe_tower", false);
    }

    private double towerX(ru.ytkab0bp.slicebeam.config.ConfigObject cfg) {
        if (!Double.isNaN(primeTowerPreviewX)) return primeTowerPreviewX;
        return parseDouble(vectorValue(configValue(cfg, "wipe_tower_x", "15"), currentPlateIndex, 15), 15);
    }

    private double towerY(ru.ytkab0bp.slicebeam.config.ConfigObject cfg) {
        if (!Double.isNaN(primeTowerPreviewY)) return primeTowerPreviewY;
        return parseDouble(vectorValue(configValue(cfg, "wipe_tower_y", "15"), currentPlateIndex, 15), 15);
    }

    private double towerWidth(ru.ytkab0bp.slicebeam.config.ConfigObject cfg) {
        double width = parseDouble(configValue(cfg, "prime_tower_width", null), Double.NaN);
        if (Double.isNaN(width)) width = parseDouble(configValue(cfg, "wipe_tower_width", null), 35);
        return Math.max(8, Math.min(120, width));
    }

    private double towerDepth(ru.ytkab0bp.slicebeam.config.ConfigObject cfg, double width) {
        String matrix = configValue(cfg, "flush_volumes_matrix", "");
        double max = 0;
        if (matrix != null) {
            for (String part : matrix.split("[,;]")) max = Math.max(max, parseDouble(part, 0));
        }
        if (max <= 0) max = 280;
        // Approximate the printed footprint length from purge volume so the visual marker responds
        // when the desktop-style flush-volume matrix is edited.
        return Math.max(width, Math.min(180, max / Math.max(1, width) * 6.0));
    }

    private Vec3d raycastBed(float screenX, float screenY) {
        if (bed == null || !bed.isValid()) return null;
        bed.getRaycaster().raycast(this, primeTowerBedHits, screenX, screenY);
        if (primeTowerBedHits.isEmpty()) return null;
        return primeTowerBedHits.get(0).position;
    }

    public boolean tryStartPrimeTowerDrag(float screenX, float screenY) {
        if (isViewerEnabled || bed == null || !bed.isValid()) return false;
        ru.ytkab0bp.slicebeam.config.ConfigObject cfg = SliceBeam.buildCurrentConfigObject();
        if (!isPrimeTowerEnabled(cfg)) return false;
        Vec3d p = raycastBed(screenX, screenY);
        if (p == null) return false;
        double w = towerWidth(cfg), d = towerDepth(cfg, w);
        double x = towerX(cfg), y = towerY(cfg);
        double pad = Math.max(8, Math.min(w, d) * 0.35);
        if (p.x < x - pad || p.x > x + w + pad || p.y < y - pad || p.y > y + d + pad) return false;
        primeTowerPreviewX = x;
        primeTowerPreviewY = y;
        return true;
    }

    public boolean dragPrimeTower(float screenX, float screenY) {
        if (Double.isNaN(primeTowerPreviewX) || Double.isNaN(primeTowerPreviewY)) return false;
        Vec3d p = raycastBed(screenX, screenY);
        if (p == null) return false;
        ru.ytkab0bp.slicebeam.config.ConfigObject cfg = SliceBeam.buildCurrentConfigObject();
        double w = towerWidth(cfg), d = towerDepth(cfg, w);
        Vec3d min = bed.getVolumeMin(), max = bed.getVolumeMax();
        primeTowerPreviewX = Math.max(min.x, Math.min(max.x - w, p.x - w / 2.0));
        primeTowerPreviewY = Math.max(min.y, Math.min(max.y - d, p.y - d / 2.0));
        return true;
    }

    public boolean finishPrimeTowerDrag(boolean commit) {
        if (Double.isNaN(primeTowerPreviewX) || Double.isNaN(primeTowerPreviewY)) return false;
        if (commit) {
            ru.ytkab0bp.slicebeam.config.ConfigObject cfg = SliceBeam.buildCurrentConfigObject();
            String xs = vectorWithValue(configValue(cfg, "wipe_tower_x", "15"), currentPlateIndex, primeTowerPreviewX);
            String ys = vectorWithValue(configValue(cfg, "wipe_tower_y", "15"), currentPlateIndex, primeTowerPreviewY);
            SliceBeam.LIVE_DIFF_PRINT.put("wipe_tower_x", xs);
            SliceBeam.LIVE_DIFF_PRINT.put("wipe_tower_y", ys);
        }
        primeTowerPreviewX = Double.NaN;
        primeTowerPreviewY = Double.NaN;
        return true;
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

    /**
     * Replace the set of inactive plates shown around the active one. Must be called on the
     * GL thread. Pass empty lists (or null) to clear. GLModels are built lazily during draw.
     */
    public void setInactivePlates(List<Model> models, List<double[]> offsets) {
        for (List<GLModel> list : inactivePlateGlModels) {
            for (GLModel gm : list) gm.release();
        }
        inactivePlateGlModels.clear();
        inactivePlateModels.clear();
        inactivePlateOffsets.clear();
        if (models == null || offsets == null) return;
        for (int p = 0; p < models.size() && p < offsets.size(); p++) {
            if (models.get(p) == null) continue;
            inactivePlateModels.add(models.get(p));
            inactivePlateOffsets.add(offsets.get(p));
            inactivePlateGlModels.add(new ArrayList<>());
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
            // Multi-plate grid: draw the other plates' beds at their offsets (edit mode only).
            if (viewer == null && !isViewerEnabled) {
                for (int p = 0; p < inactivePlateOffsets.size(); p++) {
                    double[] off = inactivePlateOffsets.get(p);
                    System.arraycopy(viewMatrix, 0, plateViewMatrix, 0, 16);
                    DoubleMatrix.translateM(plateViewMatrix, 0, off[0], off[1], 0);
                    bed.render(shadersManager, bottom, plateViewMatrix, projectionMatrix, 1f / camera.getZoom(), false);
                }
            }
        }

        drawPrimeTowerPreview(viewMatrix);

        if (isViewerEnabled) {
            if (viewer == null) {
                viewer = new GCodeViewer();
                viewer.initGL();
                viewer.setThemeColors();
                if (isModelMultiColor()) {
                    int[] pal = ru.ytkab0bp.slicebeam.utils.Prefs.getFilamentPalette();
                    int[] rgb = new int[pal.length];
                    for (int i = 0; i < pal.length; i++) rgb[i] = pal[i] & 0xFFFFFF;
                    viewer.setToolColors(rgb);
                    viewer.setViewType(GCodeViewer.VIEW_TYPE_TOOL);
                } else {
                    viewer.setViewType(GCodeViewer.VIEW_TYPE_FEATURE);
                }
                viewer.load(gcodeResult);
            }

            viewer.render(viewMatrix, projectionMatrix);
        }
        // Models on inactive plates: plain dimmed render, no selection/paint/gizmo handling.
        if (viewer == null && !isViewerEnabled && !inactivePlateModels.isEmpty()) {
            shader = shadersManager.get(GLShadersManager.SHADER_GOURAUD_LIGHT);
            shader.startUsing();
            shader.setUniform("emission_factor", 0.05f);
            shader.setUniformMatrix4fv("projection_matrix", projectionMatrix);
            int[] palette = paintMode ? paintPalette : Prefs.getFilamentPalette();
            int inactiveColor = ColorUtils.blendARGB(palette.length > 0 ? palette[0] : cachedAccentColor, Color.GRAY, 0.45f);
            for (int p = 0; p < inactivePlateModels.size(); p++) {
                Model m = inactivePlateModels.get(p);
                double[] off = inactivePlateOffsets.get(p);
                List<GLModel> list = inactivePlateGlModels.get(p);
                System.arraycopy(viewMatrix, 0, plateViewMatrix, 0, 16);
                DoubleMatrix.translateM(plateViewMatrix, 0, off[0], off[1], 0);
                shader.setUniformMatrix4fv("view_model_matrix", plateViewMatrix);
                DoubleMatrix.setIdentityM(modelMatrix, 0);
                Slic3rUtils.calcViewNormalMatrix(plateViewMatrix, modelMatrix, normalMatrix);
                shader.setUniformMatrix3fv("view_normal_matrix", normalMatrix);
                for (int i = 0; i < m.getObjectsCount(); i++) {
                    boolean left = m.isLeftHanded(i);
                    if (left) glFrontFace(GL_CW);
                    shader.setUniform("volume_mirrored", left);
                    while (list.size() <= i) {
                        GLModel gm = new GLModel();
                        gm.initFrom(m, list.size());
                        list.add(gm);
                    }
                    GLModel gm = list.get(i);
                    gm.setColor(inactiveColor);
                    gm.render();
                    if (left) glFrontFace(GL_CCW);
                }
            }
            shader.stopUsing();
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
                int baseColor = paintPalette.length > 0 ? paintPalette[0] : color;
                glModel.setColor(ColorUtils.blendARGB(baseColor, Color.WHITE, hovering ? 0.4f : 0f));
                glModel.render();

                if (!overlays.isEmpty()) {
                    // Painted facets share the object's surface; offset to avoid z-fighting.
                    glEnable(GL_POLYGON_OFFSET_FILL);
                    glPolygonOffset(-1f, -1f);
                    for (PaintOverlay o : overlays) {
                        o.model.setColor(ColorUtils.blendARGB(o.color, Color.WHITE, hovering ? 0.4f : 0f));
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

                    if (i == selectedObject && cutPlaneVisible) {
                        drawCutPlane();
                        drawCutConnectors();
                    }

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

    private Vec3d rotatedCutPlaneVector(double x, double y, double z, double rotX, double rotY) {
        double[] m = new double[16];
        double[] in = new double[] {x, y, z, 0.0};
        double[] out = new double[4];
        DoubleMatrix.setIdentityM(m, 0);
        DoubleMatrix.rotateM(m, 0, Math.toDegrees(rotY), 0.0, 1.0, 0.0);
        DoubleMatrix.rotateM(m, 0, Math.toDegrees(rotX), 1.0, 0.0, 0.0);
        DoubleMatrix.multiplyMV(out, 0, m, 0, in, 0);
        return new Vec3d(out[0], out[1], out[2]).normalize();
    }

    private Vec3d raycastCutPlane(float x, float y) {
        float z, rotX, rotY;
        Vec3d min = new Vec3d();
        Vec3d max = new Vec3d();
        synchronized (this) {
            if (!cutPlaneVisible) return null;
            z = cutPlaneZ;
            rotX = cutPlaneRotX;
            rotY = cutPlaneRotY;
            min.set(cutPlaneMin.x, cutPlaneMin.y, cutPlaneMin.z);
            max.set(cutPlaneMax.x, cutPlaneMax.y, cutPlaneMax.z);
        }

        Vec3d point = Slic3rUtils.unproject(camera.getViewModelMatrix(), projectionMatrix, viewportWidth, viewportHeight, x, y);
        Vec3d direction = camera.getDirForward().clone();
        if (!Prefs.isOrthoProjectionEnabled()) {
            direction = point.clone().add(camera.position.clone().negate()).normalize();
        }

        Vec3d planePoint = new Vec3d(0.0, 0.0, z);
        Vec3d normal = rotatedCutPlaneVector(0.0, 0.0, 1.0, rotX, rotY);
        double denom = normal.x * direction.x + normal.y * direction.y + normal.z * direction.z;
        if (Math.abs(denom) < 1e-8) return null;
        double t = ((planePoint.x - point.x) * normal.x + (planePoint.y - point.y) * normal.y + (planePoint.z - point.z) * normal.z) / denom;
        if (t < 0) return null;

        Vec3d hit = point.clone().add(direction.clone().multiply(t));
        double[] cutMatrix = new double[16];
        double[] inverse = new double[16];
        double[] local = new double[4];
        DoubleMatrix.setIdentityM(cutMatrix, 0);
        DoubleMatrix.translateM(cutMatrix, 0, 0.0, 0.0, z);
        DoubleMatrix.rotateM(cutMatrix, 0, Math.toDegrees(rotY), 0.0, 1.0, 0.0);
        DoubleMatrix.rotateM(cutMatrix, 0, Math.toDegrees(rotX), 1.0, 0.0, 0.0);
        if (!DoubleMatrix.invertM(inverse, 0, cutMatrix, 0)) return null;
        DoubleMatrix.multiplyMV(local, 0, inverse, 0, new double[] {hit.x, hit.y, hit.z, 1.0}, 0);

        double padding = 20.0;
        if (local[0] < min.x - padding || local[0] > max.x + padding || local[1] < min.y - padding || local[1] > max.y + padding) {
            return null;
        }
        return hit;
    }

    private boolean tryAddCutConnector(float x, float y) {
        boolean enabled;
        float radius, height;
        int type;
        synchronized (this) {
            enabled = cutConnectorPlacement;
            radius = cutConnectorRadius;
            height = cutConnectorHeight;
            type = cutConnectorType;
        }
        if (!enabled || model == null || selectedObject < 0 || selectedObject >= model.getObjectsCount()) return false;
        Vec3d hit = raycastCutPlane(x, y);
        if (hit == null) return false;

        model.addConnector(selectedObject, hit, radius, height, type, cutPlaneRotX, cutPlaneRotY);
        CutConnectorPreview preview = new CutConnectorPreview();
        preview.position = hit;
        preview.radius = radius;
        preview.height = height;
        preview.type = type;
        preview.rotX = cutPlaneRotX;
        preview.rotY = cutPlaneRotY;
        synchronized (this) {
            List<CutConnectorPreview> list = cutConnectorPreviews.get(selectedObject);
            if (list == null) {
                list = new ArrayList<>();
                cutConnectorPreviews.put(selectedObject, list);
            }
            list.add(preview);
            cutConnectorsDirty = true;
        }
        return true;
    }

    private void addLine(List<Float> vertices, Vec3d a, Vec3d b) {
        vertices.add((float) a.x); vertices.add((float) a.y); vertices.add((float) a.z);
        vertices.add((float) b.x); vertices.add((float) b.y); vertices.add((float) b.z);
    }

    private void drawCutConnectors() {
        List<CutConnectorPreview> previews;
        boolean dirty;
        synchronized (this) {
            previews = cutConnectorPreviews.get(selectedObject);
            if (previews == null || previews.isEmpty()) return;
            previews = new ArrayList<>(previews);
            dirty = cutConnectorsDirty;
            cutConnectorsDirty = false;
        }

        if (dirty || cutConnectorsVBO == -1 || cutConnectorsVAO == -1) {
            ArrayList<Float> vertices = new ArrayList<>();
            final int segments = 32;
            for (CutConnectorPreview c : previews) {
                Vec3d bx = rotatedCutPlaneVector(1.0, 0.0, 0.0, c.rotX, c.rotY);
                Vec3d by = rotatedCutPlaneVector(0.0, 1.0, 0.0, c.rotX, c.rotY);
                Vec3d bn = rotatedCutPlaneVector(0.0, 0.0, 1.0, c.rotX, c.rotY);
                Vec3d half = bn.clone().multiply(c.height * 0.5);
                Vec3d bottomCenter = c.position.clone().add(half.clone().negate());
                Vec3d topCenter = c.position.clone().add(half);
                Vec3d firstBottom = null, firstTop = null, prevBottom = null, prevTop = null;
                for (int s = 0; s <= segments; s++) {
                    double a = 2.0 * Math.PI * s / segments;
                    Vec3d radial = bx.clone().multiply(Math.cos(a) * c.radius).add(by.clone().multiply(Math.sin(a) * c.radius));
                    Vec3d bottom = bottomCenter.clone().add(radial);
                    Vec3d top = topCenter.clone().add(radial.clone());
                    if (s == 0) {
                        firstBottom = bottom;
                        firstTop = top;
                    } else {
                        addLine(vertices, prevBottom, bottom);
                        addLine(vertices, prevTop, top);
                        if (s % 8 == 0) addLine(vertices, bottom, top);
                    }
                    prevBottom = bottom;
                    prevTop = top;
                }
                if (firstBottom != null && firstTop != null) {
                    addLine(vertices, firstBottom, firstTop);
                    addLine(vertices, c.position.clone().add(bx.clone().multiply(-c.radius)), c.position.clone().add(bx.clone().multiply(c.radius)));
                    addLine(vertices, c.position.clone().add(by.clone().multiply(-c.radius)), c.position.clone().add(by.clone().multiply(c.radius)));
                }
            }

            cutConnectorsVertexCount = vertices.size() / 3;
            if (cutConnectorsVertexCount == 0) return;
            if (cutConnectorsVertexBuffer == null || cutConnectorsVertexBuffer.capacity() < vertices.size()) {
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(vertices.size() * 4);
                bb.order(java.nio.ByteOrder.nativeOrder());
                cutConnectorsVertexBuffer = bb.asFloatBuffer();
            }
            cutConnectorsVertexBuffer.clear();
            for (Float f : vertices) cutConnectorsVertexBuffer.put(f);
            cutConnectorsVertexBuffer.position(0);

            if (cutConnectorsVBO == -1) {
                int[] buffers = new int[1];
                glGenBuffers(1, buffers, 0);
                cutConnectorsVBO = buffers[0];
            }
            glBindBuffer(GL_ARRAY_BUFFER, cutConnectorsVBO);
            glBufferData(GL_ARRAY_BUFFER, vertices.size() * 4, cutConnectorsVertexBuffer, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);

            if (cutConnectorsVAO == -1) {
                int[] vaos = new int[1];
                glGenVertexArrays(1, vaos, 0);
                cutConnectorsVAO = vaos[0];
            }

            GLShaderProgram flat = shadersManager.get(GLShadersManager.SHADER_FLAT);
            int posLoc = flat.getAttribLocation("v_position");
            glBindVertexArray(cutConnectorsVAO);
            glBindBuffer(GL_ARRAY_BUFFER, cutConnectorsVBO);
            glEnableVertexAttribArray(posLoc);
            glVertexAttribPointer(posLoc, 3, GL_FLOAT, false, 3 * 4, 0);
            glBindVertexArray(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }

        GLShaderProgram flat = shadersManager.get(GLShadersManager.SHADER_FLAT);
        flat.startUsing();
        flat.setUniformMatrix4fv("view_model_matrix", camera.getViewModelMatrix());
        flat.setUniformMatrix4fv("projection_matrix", projectionMatrix);
        flat.setUniform4f("uniform_color", 1.0f, 0.65f, 0.10f, 0.95f);
        glLineWidth(ViewUtils.dp(2.0f));
        glBindVertexArray(cutConnectorsVAO);
        glDrawArrays(GL_LINES, 0, cutConnectorsVertexCount);
        glBindVertexArray(0);
        flat.stopUsing();
    }

    private void drawCutPlane() {
        float z;
        float rotX;
        float rotY;
        Vec3d min = new Vec3d();
        Vec3d max = new Vec3d();
        boolean dirty;

        synchronized (this) {
            z = cutPlaneZ;
            rotX = cutPlaneRotX;
            rotY = cutPlaneRotY;
            min.set(cutPlaneMin.x, cutPlaneMin.y, cutPlaneMin.z);
            max.set(cutPlaneMax.x, cutPlaneMax.y, cutPlaneMax.z);
            dirty = cutPlaneDirty;
            cutPlaneDirty = false;
        }

        if (dirty || cutPlaneVBO == -1 || cutPlaneVAO == -1) {
            float padding = 20.0f; // mm
            float minX = (float) min.x - padding;
            float maxX = (float) max.x + padding;
            float minY = (float) min.y - padding;
            float maxY = (float) max.y + padding;

            float[] vertices = {
                minX, minY, 0.0f,
                maxX, minY, 0.0f,
                maxX, maxY, 0.0f,
                minX, maxY, 0.0f
            };

            if (cutPlaneVertexBuffer == null) {
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(vertices.length * 4);
                bb.order(java.nio.ByteOrder.nativeOrder());
                cutPlaneVertexBuffer = bb.asFloatBuffer();
            }
            cutPlaneVertexBuffer.clear();
            cutPlaneVertexBuffer.put(vertices);
            cutPlaneVertexBuffer.position(0);

            if (cutPlaneVBO == -1) {
                int[] buffers = new int[1];
                glGenBuffers(1, buffers, 0);
                cutPlaneVBO = buffers[0];
            }
            glBindBuffer(GL_ARRAY_BUFFER, cutPlaneVBO);
            glBufferData(GL_ARRAY_BUFFER, vertices.length * 4, cutPlaneVertexBuffer, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);

            if (cutPlaneVAO == -1) {
                int[] vaos = new int[1];
                glGenVertexArrays(1, vaos, 0);
                cutPlaneVAO = vaos[0];
            }
            
            GLShaderProgram flat = shadersManager.get(GLShadersManager.SHADER_FLAT);
            int posLoc = flat.getAttribLocation("v_position");
            
            glBindVertexArray(cutPlaneVAO);
            glBindBuffer(GL_ARRAY_BUFFER, cutPlaneVBO);
            glEnableVertexAttribArray(posLoc);
            glVertexAttribPointer(posLoc, 3, GL_FLOAT, false, 3 * 4, 0);
            glBindVertexArray(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }

        GLShaderProgram flat = shadersManager.get(GLShadersManager.SHADER_FLAT);
        flat.startUsing();

        System.arraycopy(modelMatrix, 0, cutPlaneModelMatrix, 0, 16);

        DoubleMatrix.translateM(cutPlaneModelMatrix, 0, 0.0, 0.0, z);
        DoubleMatrix.rotateM(cutPlaneModelMatrix, 0, Math.toDegrees(rotY), 0.0, 1.0, 0.0);
        DoubleMatrix.rotateM(cutPlaneModelMatrix, 0, Math.toDegrees(rotX), 1.0, 0.0, 0.0);

        double[] viewMatrix = camera.getViewModelMatrix();
        DoubleMatrix.multiplyMM(cutPlaneOutModelMatrix, 0, viewMatrix, 0, cutPlaneModelMatrix, 0);

        flat.setUniformMatrix4fv("view_model_matrix", cutPlaneOutModelMatrix);
        flat.setUniformMatrix4fv("projection_matrix", projectionMatrix);
        flat.setUniform4f("uniform_color", 1.0f, 1.0f, 1.0f, 0.3f);

        boolean blendEnabled = glIsEnabled(GL_BLEND);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glDepthMask(false);

        boolean cullEnabled = glIsEnabled(GL_CULL_FACE);
        glDisable(GL_CULL_FACE);

        glBindVertexArray(cutPlaneVAO);
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
        glBindVertexArray(0);

        glDepthMask(true);
        if (!blendEnabled) {
            glDisable(GL_BLEND);
        }
        if (cullEnabled) {
            glEnable(GL_CULL_FACE);
        }

        flat.stopUsing();
    }

    private void drawPrimeTowerPreview(double[] viewMatrix) {
        if (isViewerEnabled || bed == null || !bed.isValid()) return;
        ru.ytkab0bp.slicebeam.config.ConfigObject cfg = SliceBeam.buildCurrentConfigObject();
        if (!isPrimeTowerEnabled(cfg)) return;
        double x = towerX(cfg), y = towerY(cfg);
        double w = towerWidth(cfg), d = towerDepth(cfg, w), h = primeTowerHeight;
        if (primeTowerModel == null) {
            primeTowerModel = new GLModel();
        }
        if (!primeTowerModel.isInitialized() || Math.abs(primeTowerWidth - w) > 0.01 || Math.abs(primeTowerDepth - d) > 0.01) {
            primeTowerModel.reset();
            primeTowerModel.initBox((float)w, (float)d, (float)h);
            primeTowerWidth = w;
            primeTowerDepth = d;
        }

        GLShaderProgram shader = shadersManager.get(GLShadersManager.SHADER_GOURAUD_LIGHT);
        shader.startUsing();
        shader.setUniform("emission_factor", 0.45f);
        shader.setUniformMatrix4fv("projection_matrix", projectionMatrix);
        DoubleMatrix.setIdentityM(modelMatrix, 0);
        DoubleMatrix.translateM(modelMatrix, 0, x, y, 0);
        DoubleMatrix.multiplyMM(outModelMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        shader.setUniformMatrix4fv("view_model_matrix", outModelMatrix);
        Slic3rUtils.calcViewNormalMatrix(viewMatrix, modelMatrix, normalMatrix);
        shader.setUniformMatrix3fv("view_normal_matrix", normalMatrix);
        shader.setUniform("volume_mirrored", false);
        boolean blend = glIsEnabled(GL_BLEND);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        primeTowerModel.setColor(ColorUtils.setAlphaComponent(Color.YELLOW, Double.isNaN(primeTowerPreviewX) ? 150 : 225));
        primeTowerModel.render();
        if (!blend) glDisable(GL_BLEND);
        shader.stopUsing();
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
    public double getHeightBandWidth() { return heightBandWidth; }
    public void setHeightBandWidth(double w) { heightBandWidth = w; }
    public double getBucketAngleThreshold() { return bucketAngleThreshold; }
    public void setBucketAngleThreshold(double a) { bucketAngleThreshold = a; }
    public boolean isBrushSphere() { return brushSphere; }
    public void setBrushSphere(boolean s) { brushSphere = s; }

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
            paintSession.bucket(h, -1, paintFilament, true, bucketAngleThreshold);
        } else if (paintTool == PAINT_TOOL_HEIGHT) {
            double halfW = heightBandWidth / 2.0;
            paintSession.height(hit.z - halfW, hit.z + halfW, paintFilament);
        } else {
            paintSession.brush(h, -1, paintBrushRadius, paintFilament, camera.position.asDoubleArray(), brushSphere);
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
                // A model painted on desktop can use more filaments than the local palette has
                // slots for; cover every painted filament so nothing renders as unpainted.
                int maxPaint = model.paintMaxFilament(i);
                int n = Math.min(Math.max(Math.max(pal.length, maxPaint), 1), Prefs.MAX_FILAMENT_COLORS);
                for (int f = 1; f <= n; f++) {
                    GLModel gl = new GLModel();
                    int tris = model.buildPaintOverlay(i, gl, f);
                    if (tris > 0) {
                        PaintOverlay o = new PaintOverlay();
                        o.model = gl;
                        o.color = (f - 1) < pal.length ? pal[f - 1] : fallbackFilamentColor(f - 1);
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

    // Distinct color for a painted filament the local palette has no slot for. Spreads hues by
    // the golden angle so adjacent filament indices stay visually separable.
    private static int fallbackFilamentColor(int index) {
        float hue = (index * 137.508f) % 360f;
        return Color.HSVToColor(new float[]{hue, 0.65f, 0.95f});
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

        if (tryAddCutConnector(x, y)) return true;

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
        clearAllCutConnectorPreviews();
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
        if (primeTowerModel != null) {
            primeTowerModel.release();
            primeTowerModel = null;
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

        if (cutPlaneVBO != -1) {
            glDeleteBuffers(1, new int[]{cutPlaneVBO}, 0);
            cutPlaneVBO = -1;
        }
        if (cutPlaneVAO != -1) {
            glDeleteVertexArrays(1, new int[]{cutPlaneVAO}, 0);
            cutPlaneVAO = -1;
        }
        if (cutConnectorsVBO != -1) {
            glDeleteBuffers(1, new int[]{cutConnectorsVBO}, 0);
            cutConnectorsVBO = -1;
        }
        if (cutConnectorsVAO != -1) {
            glDeleteVertexArrays(1, new int[]{cutConnectorsVAO}, 0);
            cutConnectorsVAO = -1;
        }
    }
}
