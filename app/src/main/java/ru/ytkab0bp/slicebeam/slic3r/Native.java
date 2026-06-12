package ru.ytkab0bp.slicebeam.slic3r;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import ru.ytkab0bp.slicebeam.SliceBeam;

public class Native {
    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("gmp");
        System.loadLibrary("gmpxx");
        System.loadLibrary("mpfr");
        OCCTLoader.load();

        System.loadLibrary("slic3r");

        set_svg_path_prefix(SliceBeam.INSTANCE.getCacheDir().getAbsolutePath());
    }

    static native void get_print_config_def(PrintConfigDef def);

    static native void set_svg_path_prefix(String prefix);
    public static String orca_bundle_read(String archivePath, String extractDir) throws IOException, JSONException {
        File extractRoot = new File(extractDir);
        if (!extractRoot.exists() && !extractRoot.mkdirs()) {
            throw new IOException("Failed to create extraction directory: " + extractRoot);
        }

        try {
            unzipArchive(new File(archivePath), extractRoot);

            JSONArray files = new JSONArray();
            String bundleStructureJson = collectJsonFiles(extractRoot, extractRoot, files);
            if (bundleStructureJson == null) {
                throw new IOException("bundle_structure.json not found in Orca bundle");
            }

            JSONObject result = new JSONObject();
            result.put("bundle_structure_json", bundleStructureJson);
            result.put("files", files);
            return result.toString();
        } finally {
            deleteRecursively(extractRoot);
        }
    }

    private static void unzipArchive(File archive, File extractRoot) throws IOException {
        String rootPath = extractRoot.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(extractRoot, entry.getName());
                String outPath = outFile.getCanonicalPath();
                if (!outPath.equals(extractRoot.getCanonicalPath()) && !outPath.startsWith(rootPath)) {
                    throw new IOException("Zip entry escapes extraction directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw new IOException("Failed to create directory: " + outFile);
                    }
                    continue;
                }

                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory: " + parent);
                }

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static String collectJsonFiles(File root, File current, JSONArray files) throws IOException, JSONException {
        String bundleStructureJson = null;
        File[] children = current.listFiles();
        if (children == null) {
            return null;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                String nested = collectJsonFiles(root, child, files);
                if (bundleStructureJson == null && nested != null) {
                    bundleStructureJson = nested;
                }
                continue;
            }

            if (!child.getName().endsWith(".json")) {
                continue;
            }

            String content = readTextFile(child);
            if ("bundle_structure.json".equals(child.getName())) {
                bundleStructureJson = content;
            }

            JSONObject item = new JSONObject();
            item.put("path", root.toPath().relativize(child.toPath()).toString().replace('\\', '/'));
            item.put("content", content);
            files.put(item);
        }

        return bundleStructureJson;
    }

    private static String readTextFile(File file) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (InputStream in = new FileInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }


    static native long shader_init_from_texts(String name, String fragment, String vertex);
    static native int shader_get_id(long ptr);
    static native int shader_get_uniform_location(long ptr, String name);
    static native int shader_get_attrib_location(long ptr, String name);
    static native void shader_start_using(long ptr);
    static native void shader_stop_using(long ptr);
    static native void shader_release(long ptr);

    static native long glmodel_create();
    static native void glmodel_init_from_model(long ptr, long model);
    static native void glmodel_init_from_model_object(long ptr, long model, int i);
    static native void glmodel_init_raycast_data(long ptr);
    static native void glmodel_set_color(long ptr, float red, float green, float blue, float alpha);
    static native void glmodel_render(long ptr);
    static native void glmodel_stilized_arrow(long ptr, float tipRadius, float tipLength, float stemRadius, float stemLength);
    static native void glmodel_init_background_triangles(long ptr);
    static native void glmodel_init_bounding_box(long ptr, long modelPtr, int i);
    static native boolean glmodel_is_initialized(long ptr);
    static native boolean glmodel_is_empty(long ptr);
    static native double[] glmodel_raycast_closest_hit(long ptr, double[] point, double[] direction);
    static native void glmodel_reset(long ptr);
    static native void glmodel_release(long ptr);

    static native long bed_create(long[] data);
    static native int bed_get_bounding_volume_max_size(long ptr);
    static native double[] bed_get_bounding_volume(long ptr);
    static native void bed_configure(long ptr, String configPath);
    static native void bed_init_triangles_mesh(long ptr, long triangles);
    static native boolean bed_arrange(long ptr, long modelPtr);
    static native void bed_release(long ptr);

    static native long models_merge(long... ptrs);
    static native long model_create();
    public static native long model_read_from_file(String path, String baseName, int plateId) throws Slic3rRuntimeError;
    static native int model_get_objects_count(long ptr);
    static native void model_add_object_from_another(long ptr, long from, int i);
    static native void model_delete_object(long ptr, int i);
    static native double[] model_get_translation(long ptr, int objectIndex);
    static native double[] model_get_scale(long ptr, int objectIndex);
    static native double[] model_get_mirror(long ptr, int objectIndex);
    static native double[] model_get_rotation(long ptr, int objectIndex);
    static native double[] model_get_bounding_box_exact(long ptr, int i);
    static native double[] model_get_bounding_box_approx(long ptr, int i);
    static native double[] model_get_bounding_box_exact_global(long ptr);
    static native double[] model_get_bounding_box_approx_global(long ptr);
    static native boolean model_is_left_handed(long ptr, int i);
    static native void model_translate(long ptr, int i, double x, double y, double z);
    static native void model_translate_global(long ptr, double x, double y, double z);
    static native void model_ensure_on_bed(long ptr, int i);
    static native void model_scale(long ptr, int i, double x, double y, double z);
    static native void model_rotate(long ptr, int i, double x, double y, double z);
    static native void model_flatten_rotate(long ptr, int i, long surfacePtr);
    static native long[] model_create_flatten_planes(long ptr, int i);
    static native boolean model_cut(long ptr, int i, double zHeight, double rotX, double rotY, boolean keepUpper, boolean keepLower);
    static native void model_add_connector(long ptr, int objIdx, double x, double y, double z, float radius, float height, int type);
    static native void model_add_connector_on_plane(long ptr, int objIdx, double x, double y, double z, float radius, float height, int type, double rotX, double rotY);
    static native void model_remove_connector(long ptr, int objIdx, int connIdx);
    static native void model_clear_connectors(long ptr, int objIdx);
    static native void model_auto_orient(long ptr, int i);
    static native boolean model_is_big_object(long ptr, int i);
    static native int model_get_extruder(long ptr, int i);
    static native void model_set_extruder(long ptr, int i, int extruder);
    // ---- Multi-color painting (mmu segmentation) ----
    static native long paint_begin(long modelPtr, int objIdx);
    static native double[] paint_raycast(long sessionPtr, double[] origin, double[] dir);
    static native void paint_brush(long sessionPtr, double[] hit, int facetStart, double radius, int filamentIdx, double[] cameraPos, boolean sphere);
    static native void paint_bucket(long sessionPtr, double[] hit, int facetStart, int filamentIdx, boolean propagate, double angle);
    static native void paint_height(long sessionPtr, double zMin, double zMax, int filamentIdx);
    static native void paint_clear(long sessionPtr);
    static native void paint_commit(long sessionPtr);
    static native float[] paint_get_mesh(long sessionPtr, int filamentIdx);
    static native int glmodel_init_from_paint(long glPtr, long sessionPtr, int filamentIdx);
    static native void paint_end(long sessionPtr);
    static native int model_build_paint_overlay(long modelPtr, int objIdx, long glPtr, int filamentIdx);
    static native boolean model_has_paint(long modelPtr, int objIdx);
    static native int model_paint_max_filament(long modelPtr, int objIdx);

    static native long model_slice(long ptr, String configPath, String path, SliceListener listener, int numFilaments, int[] filamentColors, int calibMode, double calibStart, double calibEnd, double calibStep) throws Slic3rRuntimeError;
    static native void model_export_3mf(long ptr, String configPath, String path) throws Slic3rRuntimeError;
    static native void model_release(long ptr);

    static native long gcoderesult_load_file(String path, String name);
    static native String gcoderesult_get_recommended_name(long ptr);
    static native double gcoderesult_get_used_filament_mm(long ptr, int role);
    static native double gcoderesult_get_used_filament_g(long ptr, int role);
    static native void gcoderesult_release(long ptr);

    static native long vgcode_create();
    static native void vgcode_init(long ptr);
    static native boolean vgcode_is_initialized(long ptr);
    static native void vgcode_set_colors(long ptr, int[] colors);
    static native long vgcode_get_layers_count(long ptr);
    static native void vgcode_load(long ptr, long resultPtr);
    static native void vgcode_render(long ptr, float[] viewMatrix, float[] projectionMatrix);
    static native void vgcode_set_layers_view_range(long ptr, long min, long max);
    static native void vgcode_set_infill_visibility_depth(long ptr, int depth);
    static native void vgcode_set_fast_mode(long ptr, boolean fastMode);
    static native void vgcode_set_selected_object_id(long ptr, int id);
    static native long[] vgcode_get_layers_view_range(long ptr);
    static native float vgcode_get_estimated_time(long ptr);
    static native float vgcode_get_estimated_time_role(long ptr, int role);
    static native boolean vgcode_is_extrusion_role_visible(long ptr, int role);
    static native void vgcode_toggle_extrusion_role_visibility(long ptr, int role);
    static native void vgcode_set_view_type(long ptr, int type);
    static native int vgcode_get_view_type(long ptr);
    static native void vgcode_set_tool_colors(long ptr, int[] colors);
    static native void vgcode_reset(long ptr);
    static native void vgcode_release(long ptr);

    static native long utils_config_create(String config);
    static native boolean utils_config_check_compatibility(long ptr, String condition);
    static native String utils_config_eval(long ptr, String condition) throws Slic3rRuntimeError;
    static native void utils_config_release(long ptr);

    static native void utils_calc_view_normal_matrix(double[] viewMatrix, double[] worldMatrix, double[] normalMatrix);
    static native double[] utils_unproject(double[] viewMatrix, double[] projectionMatrix, int screenWidth, int screenHeight, double x, double y);
}
