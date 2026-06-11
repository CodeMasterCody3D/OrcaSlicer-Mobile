package ru.ytkab0bp.slicebeam.slic3r;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.utils.Vec3d;

public class Model {
    public final String key = UUID.randomUUID().toString();
    long pointer;

    private double[] boundingExact;
    private double[] boundingApprox;

    public Model() {
        this(Native.model_create());
    }

    public Model(File f) throws Slic3rRuntimeError {
        this(f.getAbsolutePath(), 0);
    }

    public Model(File f, int plateId) throws Slic3rRuntimeError {
        this(f.getAbsolutePath(), plateId);
    }

    public Model(String path) throws Slic3rRuntimeError {
        this(Native.model_read_from_file(path, getBaseName(path), 0));
    }

    public Model(String path, int plateId) throws Slic3rRuntimeError {
        this(Native.model_read_from_file(path, getBaseName(path), plateId));
    }

    private Model(long ptr) {
        this.pointer = ptr;
    }

    private static String getBaseName(String path) {
        if (path.contains("/")) {
            path = path.substring(path.lastIndexOf('/') + 1);
        }
        if (path.contains(".")) {
            path = path.substring(0, path.lastIndexOf('.'));
        }
        return path;
    }

    public void getBoundingBoxExact(int i, Vec3d min, Vec3d max) {
        double[] data = Native.model_get_bounding_box_exact(pointer, i);
        min.set(data[0], data[1], data[2]);
        max.set(data[3], data[4], data[5]);
    }

    public void getBoundingBoxApprox(int i, Vec3d min, Vec3d max) {
        double[] data = Native.model_get_bounding_box_approx(pointer, i);
        min.set(data[0], data[1], data[2]);
        max.set(data[3], data[4], data[5]);
    }

    public Vec3d getBoundingBoxExactMin() {
        if (boundingExact == null) boundingExact = Native.model_get_bounding_box_exact_global(pointer);
        return new Vec3d(boundingExact[0], boundingExact[1], boundingExact[2]);
    }

    public Vec3d getBoundingBoxExactMax() {
        if (boundingExact == null) boundingExact = Native.model_get_bounding_box_exact_global(pointer);
        return new Vec3d(boundingExact[3], boundingExact[4], boundingExact[5]);
    }

    public Vec3d getBoundingBoxApproxMin() {
        if (boundingApprox == null) boundingApprox = Native.model_get_bounding_box_approx_global(pointer);
        return new Vec3d(boundingApprox[0], boundingApprox[1], boundingApprox[2]);
    }

    public Vec3d getBoundingBoxApproxMax() {
        if (boundingApprox == null) boundingApprox = Native.model_get_bounding_box_approx_global(pointer);
        return new Vec3d(boundingApprox[3], boundingApprox[4], boundingApprox[5]);
    }

    public void resetBoundingBox() {
        boundingExact = null;
        boundingApprox = null;
    }

    public void translate(int i, double x, double y, double z) {
        Native.model_translate(pointer, i, x, y, z);
    }

    public void translate(double x, double y, double z) {
        Native.model_translate_global(pointer, x, y, z);
        resetBoundingBox();
    }

    public void ensureOnBed(int i) {
        Native.model_ensure_on_bed(pointer, i);
    }

    public void scale(int i, double x, double y, double z) {
        Native.model_scale(pointer, i, x, y, z);
    }

    public void rotate(int i, double x, double y, double z) {
        Native.model_rotate(pointer, i, x, y, z);
    }

    public void flattenRotate(int i, GLModel surface) {
        Native.model_flatten_rotate(pointer, i, surface.pointer);
    }

    public int getObjectsCount() {
        return Native.model_get_objects_count(pointer);
    }

    public void addObject(Model from, int i) {
        Native.model_add_object_from_another(pointer, from.pointer, i);
    }

    public void deleteObject(int i) {
        Native.model_delete_object(pointer, i);
    }

    public boolean cut(int objectIndex, double zHeight, double rotX, double rotY, boolean keepUpper, boolean keepLower) {
        return Native.model_cut(pointer, objectIndex, zHeight, rotX, rotY, keepUpper, keepLower);
    }

    public void addConnector(int objectIndex, Vec3d position, float radius, float height, int type, double rotX, double rotY) {
        Native.model_add_connector_on_plane(pointer, objectIndex, position.x, position.y, position.z, radius, height, type, rotX, rotY);
    }

    public void addConnector(int objectIndex, Vec3d position, float radius, float height, int type) {
        Native.model_add_connector(pointer, objectIndex, position.x, position.y, position.z, radius, height, type);
    }

    public void removeConnector(int objectIndex, int connectorIndex) {
        Native.model_remove_connector(pointer, objectIndex, connectorIndex);
    }

    public void clearConnectors(int objectIndex) {
        Native.model_clear_connectors(pointer, objectIndex);
    }

    public void getTranslation(int i, Vec3d vec) {
        double[] tr = Native.model_get_translation(pointer, i);
        vec.set(tr[0], tr[1], tr[2]);
    }

    public void getRotation(int i, Vec3d vec) {
        double[] tr = Native.model_get_rotation(pointer, i);
        vec.set(tr[0], tr[1], tr[2]);
    }

    public boolean isLeftHanded(int i) {
        return Native.model_is_left_handed(pointer, i);
    }

    public void getScale(int i, Vec3d vec) {
        double[] tr = Native.model_get_scale(pointer, i);
        vec.set(tr[0], tr[1], tr[2]);
    }

    public void getMirror(int i, Vec3d vec) {
        double[] tr = Native.model_get_mirror(pointer, i);
        vec.set(tr[0], tr[1], tr[2]);
    }

    public List<GLModel> createFlattenPlanes(int i) {
        long[] ptr = Native.model_create_flatten_planes(pointer, i);
        List<GLModel> list = new ArrayList<>(ptr.length);
        for (long l : ptr) {
            list.add(new GLModel(l));
        }
        return list;
    }

    public int getExtruder(int i) {
        return Native.model_get_extruder(pointer, i);
    }

    public void setExtruder(int i, int extruder) {
        Native.model_set_extruder(pointer, i, extruder);
    }

    /** True if object i has any committed multi-color painting. */
    public boolean hasPaint(int i) {
        return Native.model_has_paint(pointer, i);
    }

    /** Fill a GLModel with object i's committed painting for the given filament; returns triangle count. */
    public int buildPaintOverlay(int i, GLModel gl, int filamentIdx) {
        return Native.model_build_paint_overlay(pointer, i, gl.pointer, filamentIdx);
    }

    public void autoOrient(int i) {
        Native.model_auto_orient(pointer, i);
    }

    public boolean isBigObject(int i) {
        return Native.model_is_big_object(pointer, i);
    }

    public GCodeProcessorResult slice(String configPath, String gcodePath, SliceListener listener) throws Slic3rRuntimeError {
        return slice(configPath, gcodePath, listener, 0, 0, 0, 0);
    }

    /** Slice as an OrcaSlicer calibration (calibMode = CalibMode ordinal; 0 = normal print). */
    public GCodeProcessorResult slice(String configPath, String gcodePath, SliceListener listener, int calibMode, double calibStart, double calibEnd, double calibStep) throws Slic3rRuntimeError {
        // Restore auto_brim for the native engine if the user selected it (genCurrentConfig converts
        // it to outer_only for Bed3D compatibility since Bed3D doesn't understand the auto_brim enum).
        if (SliceBeam.AUTO_BRIM_SELECTED) {
            try {
                java.io.File cfgFile = new java.io.File(configPath);
                String cfgStr = new String(java.nio.file.Files.readAllBytes(cfgFile.toPath()));
                // Only replace outer_only → auto_brim to avoid mangling other brim_type values.
                if (cfgStr.contains("brim_type = outer_only")) {
                    cfgStr = cfgStr.replace("brim_type = outer_only", "brim_type = auto_brim");
                    java.nio.file.Files.write(cfgFile.toPath(), cfgStr.getBytes());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Multi-color: if any object is painted (or has a non-default base filament), declare the
        // filament palette so the engine runs MMU segmentation and emits tool changes.
        int numFilaments = 1;
        int[] colors = null;
        int[] pal = ru.ytkab0bp.slicebeam.utils.Prefs.getFilamentPalette();
        if (pal.length > 1) {
            boolean anyColor = false;
            for (int i = 0; i < getObjectsCount(); i++) {
                if (hasPaint(i) || getExtruder(i) > 1) { anyColor = true; break; }
            }
            if (anyColor) {
                numFilaments = pal.length;
                colors = new int[pal.length];
                for (int i = 0; i < pal.length; i++) colors[i] = pal[i] & 0xFFFFFF;
            }
        }
        return new GCodeProcessorResult(Native.model_slice(pointer, configPath, gcodePath, listener, numFilaments, colors, calibMode, calibStart, calibEnd, calibStep));
    }

    public void export3mf(String configPath, String _3mfPath) throws Slic3rRuntimeError {
        // Same auto_brim restoration as slice() — the native engine needs the real value.
        if (SliceBeam.AUTO_BRIM_SELECTED) {
            try {
                java.io.File cfgFile = new java.io.File(configPath);
                String cfgStr = new String(java.nio.file.Files.readAllBytes(cfgFile.toPath()));
                if (cfgStr.contains("brim_type = outer_only")) {
                    cfgStr = cfgStr.replace("brim_type = outer_only", "brim_type = auto_brim");
                    java.nio.file.Files.write(cfgFile.toPath(), cfgStr.getBytes());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Native.model_export_3mf(pointer, configPath, _3mfPath);
    }

    public void release() {
        if (pointer != 0) {
            Native.model_release(pointer);
            pointer = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        release();
    }

    public static Model merge(Model... models) {
        long[] ptrs = new long[models.length];
        for (int i = 0, modelsSize = models.length; i < modelsSize; i++) {
            Model m = models[i];
            ptrs[i] = m.pointer;
        }
        return new Model(Native.models_merge(ptrs));
    }
}
