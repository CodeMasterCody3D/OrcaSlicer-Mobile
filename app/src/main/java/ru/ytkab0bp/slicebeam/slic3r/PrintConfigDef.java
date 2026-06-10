package ru.ytkab0bp.slicebeam.slic3r;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrintConfigDef {
    public static List<String> SKIP_DEFAULT_OPTIONS = Arrays.asList(
            "tilt_up_initial_speed",
            "tilt_up_finish_speed",
            "tilt_down_initial_speed",
            "tilt_down_finish_speed",
            "tower_speed"
    );

    private static PrintConfigDef instance;

    private final static Map<String, Class<?>> clzMap = new HashMap<String, Class<?>>() {
        @Nullable
        @Override
        public Class<?> get(@Nullable Object key) {
            Class<?> clz = super.get(key);
            if (clz == null) {
                try {
                    put((String) key, clz = Class.forName((String) key));
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            return clz;
        }
    };
    private final static Map<Pair<Class<?>, String>, Field> fieldMap = new HashMap<Pair<Class<?>, String>, Field>() {
        @Nullable
        @Override
        public Field get(@Nullable Object key) {
            Field f = super.get(key);
            if (f == null) {
                Pair<Class<?>, String> k = (Pair<Class<?>, String>) key;
                try {
                    f = k.first.getDeclaredField(k.second);
                    f.setAccessible(true);
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }
            }
            return f;
        }
    };
    private final static Map<Pair<Class<?>, String>, Object> valueMap = new HashMap<>();

    public Map<String, ConfigOptionDef> options = new HashMap<>();

    @Keep
    PrintConfigDef() {}

    @Keep
    static Object resolveEnum(String className, String value) {
        className = className.replace("/", ".");
        Class<?> clz = clzMap.get(className);
        Pair<Class<?>, String> key = new Pair<>(clz, value);
        Object val = valueMap.get(key);
        if (val != null) return val;

        Field f = fieldMap.get(key);
        try {
            valueMap.put(key, val = f.get(null));
            return val;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static final java.util.Map<String, String> ORCA_LABELS = new java.util.HashMap<String, String>() {{
        // Wall / perimeter renames
        put("perimeters",                          "Wall loops");
        put("extra_perimeters",                    "Extra walls");
        put("perimeter_generator",                 "Wall generator");
        put("avoid_crossing_perimeters",           "Avoid crossing walls");
        put("overhangs",                           "Detect overhang walls");
        put("thin_walls",                          "Detect thin walls");
        // Layer height
        put("first_layer_height",                  "Initial layer height");
        // Top/bottom shells
        put("top_solid_layers",                    "Top shell layers");
        put("bottom_solid_layers",                 "Bottom shell layers");
        put("top_solid_min_thickness",             "Top shell thickness");
        put("bottom_solid_min_thickness",          "Bottom shell thickness");
        put("top_fill_pattern",                    "Top surface pattern");
        put("bottom_fill_pattern",                 "Bottom surface pattern");
        // Infill
        put("fill_density",                        "Sparse infill density");
        put("fill_pattern",                        "Sparse infill pattern");
        put("fill_angle",                          "Sparse infill direction");
        put("infill_overlap",                      "Infill/wall overlap");
        put("bridge_flow_ratio",                   "Bridge flow");
        // Line widths
        put("extrusion_width",                     "Line width");
        put("first_layer_extrusion_width",         "Initial layer line width");
        put("perimeter_extrusion_width",           "Inner wall line width");
        put("external_perimeter_extrusion_width",  "Outer wall line width");
        put("infill_extrusion_width",              "Sparse infill line width");
        put("solid_infill_extrusion_width",        "Inner solid infill line width");
        put("top_infill_extrusion_width",          "Top surface line width");
        put("support_material_extrusion_width",    "Support line width");
        // Speed
        put("perimeter_speed",                     "Inner wall speed");
        put("small_perimeter_speed",               "Small perimeter speed");
        put("external_perimeter_speed",            "Outer wall speed");
        put("infill_speed",                        "Sparse infill speed");
        put("solid_infill_speed",                  "Internal solid infill speed");
        put("top_solid_infill_speed",              "Top surface speed");
        put("gap_fill_speed",                      "Gap infill speed");
        put("first_layer_speed",                   "Initial layer speed");
        put("first_layer_speed_over_raft",         "Initial layer speed over raft");
        put("ironing_speed",                       "Ironing speed");
        // Ironing
        put("ironing_flowrate",                    "Ironing flow");
        // Support
        put("support_material",                    "Enable support");
        put("support_material_threshold",          "Threshold angle");
        put("support_material_buildplate_only",    "On build plate only");
        put("support_material_contact_distance",   "Top Z distance");
        put("support_material_bottom_contact_distance", "Bottom Z distance");
        put("support_material_xy_spacing",         "XY separation between object and support");
        put("support_material_pattern",            "Support base pattern");
        put("support_material_spacing",            "Support base pattern spacing");
        put("support_material_interface_layers",   "Top interface layers");
        put("support_material_bottom_interface_layers", "Bottom interface layers");
        put("support_material_interface_spacing",  "Interface pattern spacing");
        // Skirt / brim
        put("skirts",                              "Skirt loops");
        put("brim_separation",                     "Brim-object gap");
        // Special modes
        put("spiral_vase",                         "Spiral vase");
        put("xy_size_compensation",                "XY size compensation");
        put("elefant_foot_compensation",           "Elephant foot compensation");
        // Prime tower
        put("wipe_tower",                          "Enable prime tower");
        put("wipe_tower_width",                    "Prime tower width");
    }};

    public static PrintConfigDef getInstance() {
        if (instance == null) {
            Native.get_print_config_def(instance = new PrintConfigDef());
            applyOrcaLabels(instance);
        }
        return instance;
    }

    private static void applyOrcaLabels(PrintConfigDef def) {
        for (java.util.Map.Entry<String, String> entry : ORCA_LABELS.entrySet()) {
            ConfigOptionDef opt = def.options.get(entry.getKey());
            if (opt != null) {
                opt.label = entry.getValue();
            }
        }

        ConfigOptionDef brimTypeOpt = def.options.get("brim_type");
        if (brimTypeOpt != null) {
            String[] origValues = brimTypeOpt.enumValues;
            String[] origLabels = brimTypeOpt.enumLabels;
            if (origValues != null && origLabels != null) {
                boolean hasAutoBrim = false;
                for (String val : origValues) {
                    if ("auto_brim".equals(val)) {
                        hasAutoBrim = true;
                        break;
                    }
                }
                if (!hasAutoBrim) {
                    String[] newValues = new String[origValues.length + 1];
                    String[] newLabels = new String[origLabels.length + 1];
                    newValues[0] = "auto_brim";
                    newLabels[0] = "Auto";
                    System.arraycopy(origValues, 0, newValues, 1, origValues.length);
                    System.arraycopy(origLabels, 0, newLabels, 1, origLabels.length);
                    brimTypeOpt.enumValues = newValues;
                    brimTypeOpt.enumLabels = newLabels;
                    brimTypeOpt.defaultValue = "auto_brim";
                }
            }
        }
    }

    @Keep
    void addOption(String key, ConfigOptionDef def) {
        options.put(key, def);
    }
}
