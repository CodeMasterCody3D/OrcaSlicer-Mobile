package ru.ytkab0bp.slicebeam.slic3r;

import static ru.ytkab0bp.slicebeam.utils.DebugUtils.assertTrue;

import android.graphics.Color;

import java.util.ArrayList;

import ru.ytkab0bp.slicebeam.render.Camera;
import ru.ytkab0bp.slicebeam.render.GLRenderer;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.Vec3d;

public class GLModel {
    long pointer;
    private MeshRaycaster raycaster;

    public float hoverProgress;
    public boolean isHovering;

    /* package */ GLModel(long ptr) {
        pointer = ptr;
    }

    public GLModel() {
        this(Native.glmodel_create());
    }

    public GLModel(Model model) {
        this(Native.glmodel_create());
        initFrom(model);
    }

    public void initFrom(Model model) {
        Native.glmodel_init_from_model(pointer, model.pointer);
    }

    public void initFrom(Model model, int i) {
        Native.glmodel_init_from_model_object(pointer, model.pointer, i);
    }

    /** Build this model from facets painted with the given filament in a paint session (mesh-local coords). */
    public int initFromPaint(long paintSessionPtr, int filamentIdx) {
        return Native.glmodel_init_from_paint(pointer, paintSessionPtr, filamentIdx);
    }

    public void setColor(int color) {
        Native.glmodel_set_color(pointer, Color.red(color) / (float) 0xFF, Color.green(color) / (float) 0xFF, Color.blue(color) / (float) 0xFF, Color.alpha(color) / (float) 0xFF);
    }

    public void stilizedArrow(float tipRadius, float tipLength, float stemRadius, float stemLength) {
        Native.glmodel_stilized_arrow(pointer, tipRadius, tipLength, stemRadius, stemLength);
    }

    public void initBackgroundTriangles() {
        Native.glmodel_init_background_triangles(pointer);
    }

    public void initBoundingBox(Model model, int i) {
        Native.glmodel_init_bounding_box(pointer, model.pointer, i);
    }

    public boolean isInitialized() {
        return Native.glmodel_is_initialized(pointer);
    }

    public boolean isEmpty() {
        return Native.glmodel_is_empty(pointer);
    }

    public void render() {
        Native.glmodel_render(pointer);
    }

    public void reset() {
        Native.glmodel_reset(pointer);
        raycaster = null;
    }

    public void release() {
        if (pointer != 0) {
            Native.glmodel_release(pointer);
            pointer = 0;
        }
    }

    public MeshRaycaster getRaycaster() {
        if (raycaster == null) {
            Native.glmodel_init_raycast_data(pointer);
            raycaster = new MeshRaycaster();
        }
        return raycaster;
    }

    public final class MeshRaycaster {
        public void raycast(GLRenderer renderer, ArrayList<HitResult> list, float x, float y) {
            assertTrue(renderer != null);
            list.clear();

            Camera camera = renderer.getCamera();
            Vec3d point = Slic3rUtils.unproject(camera.getViewModelMatrix(), renderer.getProjectionMatrix(), renderer.getViewportWidth(), renderer.getViewportHeight(), x, y);
            Vec3d direction = camera.getDirForward().clone();
            if (!Prefs.isOrthoProjectionEnabled()) {
                direction = point.clone().add(camera.position.clone().negate()).normalize();
            }
            double[] v = Native.glmodel_raycast_closest_hit(pointer, point.asDoubleArray(), direction.asDoubleArray());
            list.ensureCapacity(v.length / 6);
            for (int i = 0; i < v.length; i += 6) {
                list.add(new HitResult(
                        new Vec3d(v[i], v[i + 1], v[i + 2]),
                        new Vec3d(v[i + 3], v[i + 4], v[i + 5])
                ));
            }
        }
    }

    public static class HitResult {
        public final Vec3d position, normal;

        public HitResult(Vec3d position, Vec3d normal) {
            this.position = position;
            this.normal = normal;
        }
    }
}
