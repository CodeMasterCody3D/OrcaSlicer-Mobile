package ru.ytkab0bp.slicebeam.slic3r;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.util.*;

public class Native {
    static {
        // Shadow class: No System.loadLibrary call!
    }

    // Mock State Management
    private static long nextPtr = 1;
    
    public static class MockModelObject {
        public double[] translation = {0.0, 0.0, 0.0};
        public double[] rotation = {0.0, 0.0, 0.0};
        public double[] scale = {1.0, 1.0, 1.0};
        public double[] mirror = {1.0, 1.0, 1.0};
        public double[] boundingBox = {-50.0, -50.0, 0.0, 50.0, 50.0, 100.0}; // xmin, ymin, zmin, xmax, ymax, zmax
        public int extruder = 0;
        public int colorIndex = 0;
        public boolean hasSupport = false;

        public MockModelObject clone() {
            MockModelObject o = new MockModelObject();
            o.translation = translation.clone();
            o.rotation = rotation.clone();
            o.scale = scale.clone();
            o.mirror = mirror.clone();
            o.boundingBox = boundingBox.clone();
            o.extruder = extruder;
            o.colorIndex = colorIndex;
            o.hasSupport = hasSupport;
            return o;
        }
    }

    public static class MockModel {
        public List<MockModelObject> objects = new ArrayList<>();
        public MockModel() {
            // Start with one object by default
            objects.add(new MockModelObject());
        }
    }

    public static class MockBed {
        public long[] data;
        public String configPath;
        public boolean isValid = true;
    }

    private static Map<Long, MockModel> models = new HashMap<>();
    private static Map<Long, MockBed> beds = new HashMap<>();

    public static Map<Long, MockModel> getMockModels() { return models; }
    public static Map<Long, MockBed> getMockBeds() { return beds; }

    public static void set_svg_path_prefix(String prefix) {}

    public static void get_print_config_def(PrintConfigDef def) {
        // Mock print config definition
        ConfigOptionDef brimTypeOpt = new ConfigOptionDef();
        brimTypeOpt.key = "brim_type";
        brimTypeOpt.defaultValue = "auto_brim";
        brimTypeOpt.enumValues = new String[]{"auto_brim", "outer_only", "no_brim"};
        def.options.put("brim_type", brimTypeOpt);
    }

    public static long bed_create(long[] data) {
        long ptr = nextPtr++;
        MockBed bed = new MockBed();
        bed.data = data;
        beds.put(ptr, bed);
        return ptr;
    }

    public static int bed_get_bounding_volume_max_size(long ptr) { return 250; }
    public static double[] bed_get_bounding_volume(long ptr) { return new double[]{-125, -125, 0, 125, 125, 200}; }
    public static void bed_configure(long ptr, String configPath) {
        MockBed bed = beds.get(ptr);
        if (bed != null) {
            bed.configPath = configPath;
        }
    }
    public static void bed_init_triangles_mesh(long ptr, long triangles) {}
    public static boolean bed_arrange(long ptr, long modelPtr) {
        MockModel model = models.get(modelPtr);
        if (model != null) {
            // Simulate placing them nicely
            for (int i = 0; i < model.objects.size(); i++) {
                model.objects.get(i).translation[0] = i * 20.0;
                model.objects.get(i).translation[1] = i * 20.0;
            }
            return true;
        }
        return false;
    }
    public static void bed_release(long ptr) { beds.remove(ptr); }

    public static long model_create() {
        long ptr = nextPtr++;
        models.put(ptr, new MockModel());
        return ptr;
    }

    public static long models_merge(long... ptrs) {
        long ptr = nextPtr++;
        MockModel merged = new MockModel();
        merged.objects.clear();
        for (long p : ptrs) {
            MockModel m = models.get(p);
            if (m != null) {
                for (MockModelObject o : m.objects) {
                    merged.objects.add(o.clone());
                }
            }
        }
        models.put(ptr, merged);
        return ptr;
    }

    public static long model_read_from_file(String path, String baseName) throws Slic3rRuntimeError {
        long ptr = nextPtr++;
        MockModel model = new MockModel();
        models.put(ptr, model);
        return ptr;
    }

    public static int model_get_objects_count(long ptr) {
        MockModel model = models.get(ptr);
        return model != null ? model.objects.size() : 0;
    }

    public static void model_add_object_from_another(long ptr, long from, int i) {
        MockModel toModel = models.get(ptr);
        MockModel fromModel = models.get(from);
        if (toModel != null && fromModel != null && i >= 0 && i < fromModel.objects.size()) {
            toModel.objects.add(fromModel.objects.get(i).clone());
        }
    }

    public static void model_delete_object(long ptr, int i) {
        MockModel model = models.get(ptr);
        if (model != null && i >= 0 && i < model.objects.size()) {
            model.objects.remove(i);
        }
    }

    public static double[] model_get_translation(long ptr, int objectIndex) {
        MockModel model = models.get(ptr);
        if (model != null && objectIndex >= 0 && objectIndex < model.objects.size()) {
            return model.objects.get(objectIndex).translation;
        }
        return new double[]{0, 0, 0};
    }

    public static double[] model_get_scale(long ptr, int objectIndex) {
        MockModel model = models.get(ptr);
        if (model != null && objectIndex >= 0 && objectIndex < model.objects.size()) {
            return model.objects.get(objectIndex).scale;
        }
        return new double[]{1, 1, 1};
    }

    public static double[] model_get_mirror(long ptr, int objectIndex) {
        MockModel model = models.get(ptr);
        if (model != null && objectIndex >= 0 && objectIndex < model.objects.size()) {
            return model.objects.get(objectIndex).mirror;
        }
        return new double[]{1, 1, 1};
    }

    public static double[] model_get_rotation(long ptr, int objectIndex) {
        MockModel model = models.get(ptr);
        if (model != null && objectIndex >= 0 && objectIndex < model.objects.size()) {
            return model.objects.get(objectIndex).rotation;
        }
        return new double[]{0, 0, 0};
    }

    public static double[] model_get_bounding_box_exact(long ptr, int i) {
        MockModel model = models.get(ptr);
        if (model != null && i >= 0 && i < model.objects.size()) {
            return model.objects.get(i).boundingBox;
        }
        return new double[]{-50, -50, 0, 50, 50, 100};
    }

    public static double[] model_get_bounding_box_approx(long ptr, int i) {
        return model_get_bounding_box_exact(ptr, i);
    }

    public static double[] model_get_bounding_box_exact_global(long ptr) {
        MockModel model = models.get(ptr);
        if (model == null || model.objects.isEmpty()) return new double[]{0, 0, 0, 0, 0, 0};
        double xmin = Double.MAX_VALUE, ymin = Double.MAX_VALUE, zmin = Double.MAX_VALUE;
        double xmax = -Double.MAX_VALUE, ymax = -Double.MAX_VALUE, zmax = -Double.MAX_VALUE;
        for (MockModelObject o : model.objects) {
            xmin = Math.min(xmin, o.boundingBox[0] + o.translation[0]);
            ymin = Math.min(ymin, o.boundingBox[1] + o.translation[1]);
            zmin = Math.min(zmin, o.boundingBox[2] + o.translation[2]);
            xmax = Math.max(xmax, o.boundingBox[3] + o.translation[0]);
            ymax = Math.max(ymax, o.boundingBox[4] + o.translation[1]);
            zmax = Math.max(zmax, o.boundingBox[5] + o.translation[2]);
        }
        return new double[]{xmin, ymin, zmin, xmax, ymax, zmax};
    }

    public static double[] model_get_bounding_box_approx_global(long ptr) {
        return model_get_bounding_box_exact_global(ptr);
    }

    public static boolean model_is_left_handed(long ptr, int i) { return false; }

    public static void model_translate(long ptr, int i, double x, double y, double z) {
        MockModel model = models.get(ptr);
        if (model != null && i >= 0 && i < model.objects.size()) {
            model.objects.get(i).translation[0] += x;
            model.objects.get(i).translation[1] += y;
            model.objects.get(i).translation[2] += z;
        }
    }

    public static void model_translate_global(long ptr, double x, double y, double z) {
        MockModel model = models.get(ptr);
        if (model != null) {
            for (MockModelObject o : model.objects) {
                o.translation[0] += x;
                o.translation[1] += y;
                o.translation[2] += z;
            }
        }
    }

    public static void model_ensure_on_bed(long ptr, int i) {
        MockModel model = models.get(ptr);
        if (model != null && i >= 0 && i < model.objects.size()) {
            // align z_min to 0
            model.objects.get(i).translation[2] = -model.objects.get(i).boundingBox[2];
        }
    }

    public static void model_scale(long ptr, int i, double x, double y, double z) {
        MockModel model = models.get(ptr);
        if (model != null && i >= 0 && i < model.objects.size()) {
            model.objects.get(i).scale[0] *= x;
            model.objects.get(i).scale[1] *= y;
            model.objects.get(i).scale[2] *= z;
        }
    }

    public static void model_rotate(long ptr, int i, double x, double y, double z) {
        MockModel model = models.get(ptr);
        if (model != null && i >= 0 && i < model.objects.size()) {
            model.objects.get(i).rotation[0] += x;
            model.objects.get(i).rotation[1] += y;
            model.objects.get(i).rotation[2] += z;
        }
    }

    public static void model_flatten_rotate(long ptr, int i, long surfacePtr) {}
    public static long[] model_create_flatten_planes(long ptr, int i) { return new long[0]; }
    public static void model_auto_orient(long ptr, int i) {}
    public static boolean model_is_big_object(long ptr, int i) { return false; }
    public static int model_get_extruder(long ptr, int i) {
        MockModel model = models.get(ptr);
        if (model != null && i >= 0 && i < model.objects.size()) {
            return model.objects.get(i).extruder;
        }
        return 0;
    }
    public static void model_set_extruder(long ptr, int i, int extruder) {
        MockModel model = models.get(ptr);
        if (model != null && i >= 0 && i < model.objects.size()) {
            model.objects.get(i).extruder = extruder;
        }
    }

    public static long model_slice(long ptr, String configPath, String path, SliceListener listener) throws Slic3rRuntimeError {
        // Notify listener to simulate real slicing progress
        if (listener != null) {
            listener.onSliceProgress(10);
            listener.onSliceProgress(50);
            listener.onSliceProgress(100);
        }
        return 5000L; // Mock gcoderesult pointer
    }

    public static void model_export_3mf(long ptr, String configPath, String path) throws Slic3rRuntimeError {}
    public static void model_release(long ptr) { models.remove(ptr); }

    public static long gcoderesult_load_file(String path, String name) { return 5000L; }
    public static String gcoderesult_get_recommended_name(long ptr) { return "mock_slice.gcode"; }
    public static double gcoderesult_get_used_filament_mm(long ptr, int role) { return 1500.0; }
    public static double gcoderesult_get_used_filament_g(long ptr, int role) { return 4.5; }
    public static void gcoderesult_release(long ptr) {}

    // Shader mocks
    public static long shader_init_from_texts(String name, String fragment, String vertex) { return 100L; }
    public static int shader_get_id(long ptr) { return 1; }
    public static int shader_get_uniform_location(long ptr, String name) { return 0; }
    public static int shader_get_attrib_location(long ptr, String name) { return 0; }
    public static void shader_start_using(long ptr) {}
    public static void shader_stop_using(long ptr) {}
    public static void shader_release(long ptr) {}

    // GLModel mocks
    public static long glmodel_create() { return nextPtr++; }
    public static void glmodel_init_from_model(long ptr, long model) {}
    public static void glmodel_init_from_model_object(long ptr, long model, int i) {}
    public static void glmodel_init_raycast_data(long ptr) {}
    public static void glmodel_set_color(long ptr, float red, float green, float blue, float alpha) {}
    public static void glmodel_render(long ptr) {}
    public static void glmodel_stilized_arrow(long ptr, float tipRadius, float tipLength, float stemRadius, float stemLength) {}
    public static void glmodel_init_background_triangles(long ptr) {}
    public static void glmodel_init_bounding_box(long ptr, long modelPtr, int i) {}
    public static boolean glmodel_is_initialized(long ptr) { return true; }
    public static boolean glmodel_is_empty(long ptr) { return false; }
    public static double[] glmodel_raycast_closest_hit(long ptr, double[] point, double[] direction) {
        // Return hit at (0,0,0) with normal (0,0,1)
        return new double[]{0, 0, 0, 0, 0, 1};
    }
    public static void glmodel_reset(long ptr) {}
    public static void glmodel_release(long ptr) {}

    // VGCode mocks
    public static long vgcode_create() { return 6000L; }
    public static void vgcode_init(long ptr) {}
    public static boolean vgcode_is_initialized(long ptr) { return true; }
    public static void vgcode_set_colors(long ptr, int[] colors) {}
    public static long vgcode_get_layers_count(long ptr) { return 100L; }
    public static void vgcode_load(long ptr, long resultPtr) {}
    public static void vgcode_render(long ptr, float[] viewMatrix, float[] projectionMatrix) {}
    public static void vgcode_set_layers_view_range(long ptr, long min, long max) {}
    public static long[] vgcode_get_layers_view_range(long ptr) { return new long[]{0, 100}; }
    public static float vgcode_get_estimated_time(long ptr) { return 3600f; }
    public static float vgcode_get_estimated_time_role(long ptr, int role) { return 600f; }
    public static boolean vgcode_is_extrusion_role_visible(long ptr, int role) { return true; }
    public static void vgcode_toggle_extrusion_role_visibility(long ptr, int role) {}
    public static void vgcode_reset(long ptr) {}
    public static void vgcode_release(long ptr) {}

    // Utils config mocks
    public static long utils_config_create(String config) { return 7000L; }
    public static boolean utils_config_check_compatibility(long ptr, String condition) { return true; }
    public static String utils_config_eval(long ptr, String condition) throws Slic3rRuntimeError { return "eval_result"; }
    public static void utils_config_release(long ptr) {}

    public static void utils_calc_view_normal_matrix(double[] viewMatrix, double[] worldMatrix, double[] normalMatrix) {}
    public static double[] utils_unproject(double[] viewMatrix, double[] projectionMatrix, int screenWidth, int screenHeight, double x, double y) {
        return new double[]{0.0, 0.0, 0.0};
    }

    // Zip bundle extraction mock helper
    public static String orca_bundle_read(String archivePath, String extractDir) throws IOException, JSONException {
        // Return a mock JSON bundle
        JSONObject result = new JSONObject();
        result.put("bundle_structure_json", "{}");
        result.put("files", new JSONArray());
        return result.toString();
    }
}
