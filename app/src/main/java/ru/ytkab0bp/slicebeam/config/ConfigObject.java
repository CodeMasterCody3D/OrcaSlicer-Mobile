package ru.ytkab0bp.slicebeam.config;

import java.util.HashMap;
import java.util.Map;

import ru.ytkab0bp.slicebeam.BuildConfig;
import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.fragment.ProfileListFragment;

/** @noinspection CopyConstructorMissesField*/
public class ConfigObject implements ProfileListFragment.ProfileListItem {
    public final static int PROFILE_LIST_PRINT = 0, PROFILE_LIST_FILAMENT = 1, PROFILE_LIST_PRINTER = 2;

    // PrusaSlicer/SliceBeam-era option keys -> OrcaSlicer engine keys. Applied to every key entering
    // or leaving a ConfigObject so the UI (which looks up options by their OrcaSlicer key), the stored
    // profile values, and the config handed to the native engine all agree on one naming scheme.
    // Every target here is verified to exist in the engine's PrintConfigDef.
    public static final Map<String, String> KEY_MIGRATION = buildKeyMigration();

    private static Map<String, String> buildKeyMigration() {
        Map<String, String> m = new HashMap<>();
        m.put("bed_shape", "printable_area");
        m.put("max_print_height", "printable_height");
        m.put("bed_temperature", "hot_plate_temp");
        m.put("first_layer_bed_temperature", "hot_plate_temp_initial_layer");
        m.put("temperature", "nozzle_temperature");
        m.put("first_layer_temperature", "nozzle_temperature_initial_layer");
        m.put("extrusion_multiplier", "filament_flow_ratio");
        m.put("cooling", "slow_down_for_layer_cooling");
        m.put("fan_always_on", "reduce_fan_stop_start_freq");
        m.put("fan_below_layer_time", "fan_cooling_layer_time");
        m.put("slowdown_below_layer_time", "slow_down_layer_time");
        m.put("min_print_speed", "slow_down_min_speed");
        m.put("max_fan_speed", "fan_max_speed");
        m.put("min_fan_speed", "fan_min_speed");
        m.put("disable_fan_first_layers", "close_fan_the_first_x_layers");
        m.put("bridge_fan_speed", "overhang_fan_speed");
        m.put("start_gcode", "machine_start_gcode");
        m.put("end_gcode", "machine_end_gcode");
        m.put("before_layer_gcode", "before_layer_change_gcode");
        m.put("layer_gcode", "layer_change_gcode");
        m.put("toolchange_gcode", "change_filament_gcode");
        m.put("pause_print_gcode", "machine_pause_gcode");
        m.put("start_filament_gcode", "filament_start_gcode");
        m.put("end_filament_gcode", "filament_end_gcode");
        m.put("retract_length", "retraction_length");
        m.put("retract_speed", "retraction_speed");
        m.put("deretract_speed", "deretraction_speed");
        m.put("retract_lift", "z_hop");
        m.put("retract_before_travel", "retraction_minimum_travel");
        m.put("retract_layer_change", "retract_when_changing_layer");
        m.put("filament_retract_length", "filament_retraction_length");
        m.put("filament_retract_speed", "filament_retraction_speed");
        m.put("filament_deretract_speed", "filament_deretraction_speed");
        m.put("filament_retract_before_travel", "filament_retraction_minimum_travel");
        m.put("filament_retract_layer_change", "filament_retract_when_changing_layer");
        m.put("filament_retract_lift", "filament_z_hop");
        m.put("perimeters", "wall_loops");
        m.put("perimeter_speed", "inner_wall_speed");
        m.put("external_perimeter_speed", "outer_wall_speed");
        m.put("perimeter_extruder", "wall_filament");
        m.put("perimeter_extrusion_width", "inner_wall_line_width");
        m.put("external_perimeter_extrusion_width", "outer_wall_line_width");
        m.put("perimeter_generator", "wall_generator");
        m.put("external_perimeter_acceleration", "outer_wall_acceleration");
        m.put("thin_walls", "detect_thin_wall");
        m.put("top_one_perimeter_type", "top_one_wall_type");
        m.put("only_one_perimeter_first_layer", "only_one_wall_first_layer");
        m.put("extra_perimeters", "extra_perimeters_on_overhangs");
        m.put("avoid_crossing_perimeters", "reduce_crossing_wall");
        m.put("avoid_crossing_perimeters_max_detour", "max_travel_detour_distance");
        m.put("only_retract_when_crossing_perimeters", "reduce_infill_retraction");
        m.put("fill_density", "sparse_infill_density");
        m.put("fill_pattern", "sparse_infill_pattern");
        m.put("fill_angle", "infill_direction");
        m.put("infill_speed", "sparse_infill_speed");
        m.put("infill_extruder", "sparse_infill_filament");
        m.put("infill_extrusion_width", "sparse_infill_line_width");
        m.put("infill_acceleration", "sparse_infill_acceleration");
        m.put("infill_overlap", "infill_wall_overlap");
        m.put("infill_every_layers", "infill_combination");
        m.put("infill_first", "is_infill_first");
        m.put("solid_infill_speed", "internal_solid_infill_speed");
        m.put("solid_infill_extruder", "solid_infill_filament");
        m.put("solid_infill_extrusion_width", "internal_solid_infill_line_width");
        m.put("solid_infill_acceleration", "internal_solid_infill_acceleration");
        m.put("solid_infill_below_area", "minimum_sparse_infill_area");
        m.put("top_solid_infill_speed", "top_surface_speed");
        m.put("top_solid_infill_acceleration", "top_surface_acceleration");
        m.put("top_infill_extrusion_width", "top_surface_line_width");
        m.put("bridge_flow_ratio", "bridge_flow");
        m.put("gap_fill_speed", "gap_infill_speed");
        m.put("gap_fill_enabled", "gap_fill_target");
        m.put("top_solid_layers", "top_shell_layers");
        m.put("bottom_solid_layers", "bottom_shell_layers");
        m.put("top_solid_min_thickness", "top_shell_thickness");
        m.put("bottom_solid_min_thickness", "bottom_shell_thickness");
        m.put("top_fill_pattern", "top_surface_pattern");
        m.put("bottom_fill_pattern", "bottom_surface_pattern");
        m.put("extrusion_width", "line_width");
        m.put("first_layer_extrusion_width", "initial_layer_line_width");
        m.put("first_layer_height", "initial_layer_print_height");
        m.put("arc_fitting", "enable_arc_fitting");
        m.put("overhangs", "detect_overhang_wall");
        m.put("ironing_flowrate", "ironing_flow");
        m.put("spiral_vase", "spiral_mode");
        m.put("support_material_speed", "support_speed");
        m.put("support_material_interface_speed", "support_interface_speed");
        m.put("first_layer_speed", "initial_layer_speed");
        m.put("first_layer_acceleration", "initial_layer_acceleration");
        m.put("overhang_speed_0", "overhang_1_4_speed");
        m.put("overhang_speed_1", "overhang_2_4_speed");
        m.put("overhang_speed_2", "overhang_3_4_speed");
        m.put("overhang_speed_3", "overhang_4_4_speed");
        m.put("max_volumetric_extrusion_rate_slope_positive", "max_volumetric_extrusion_rate_slope");
        m.put("support_material", "enable_support");
        m.put("support_material_threshold", "support_threshold_angle");
        m.put("support_material_enforce_layers", "enforce_support_layers");
        m.put("support_material_style", "support_style");
        m.put("support_material_pattern", "support_base_pattern");
        m.put("support_material_spacing", "support_base_pattern_spacing");
        m.put("support_material_angle", "support_angle");
        m.put("support_material_contact_distance", "support_top_z_distance");
        m.put("support_material_bottom_contact_distance", "support_bottom_z_distance");
        m.put("support_material_buildplate_only", "support_on_build_plate_only");
        m.put("support_material_xy_spacing", "support_object_xy_distance");
        m.put("support_material_extruder", "support_filament");
        m.put("support_material_interface_extruder", "support_interface_filament");
        m.put("support_material_interface_layers", "support_interface_top_layers");
        m.put("support_material_bottom_interface_layers", "support_interface_bottom_layers");
        m.put("support_material_interface_pattern", "support_interface_pattern");
        m.put("support_material_interface_spacing", "support_interface_spacing");
        m.put("support_material_interface_contact_loops", "support_interface_loop_pattern");
        m.put("support_material_extrusion_width", "support_line_width");
        m.put("dont_support_bridges", "bridge_no_support");
        m.put("support_tree_angle", "tree_support_branch_angle");
        m.put("support_tree_angle_slow", "tree_support_angle_slow");
        m.put("support_tree_branch_diameter", "tree_support_branch_diameter");
        m.put("support_tree_branch_diameter_angle", "tree_support_branch_diameter_angle");
        m.put("support_tree_branch_distance", "tree_support_branch_distance");
        m.put("support_tree_tip_diameter", "tree_support_tip_diameter");
        m.put("support_tree_top_rate", "tree_support_top_rate");
        m.put("skirts", "skirt_loops");
        m.put("brim_separation", "brim_object_gap");
        m.put("wipe_tower", "enable_prime_tower");
        m.put("wipe_tower_width", "prime_tower_width");
        m.put("wipe_tower_brim_width", "prime_tower_brim_width");
        m.put("wipe_tower_extruder", "wipe_tower_filament");
        m.put("extruder_clearance_height", "extruder_clearance_height_to_rod");
        m.put("fuzzy_skin_point_dist", "fuzzy_skin_point_distance");
        m.put("output_filename_format", "filename_format");
        m.put("xy_size_compensation", "xy_contour_compensation");
        m.put("machine_max_feedrate_x", "machine_max_speed_x");
        m.put("machine_max_feedrate_y", "machine_max_speed_y");
        m.put("machine_max_feedrate_z", "machine_max_speed_z");
        m.put("machine_max_feedrate_e", "machine_max_speed_e");
        return m;
    }

    // Reverse map (OrcaSlicer -> legacy) so code that still categorizes by legacy key names can
    // recognize an already-migrated key (e.g. when re-reading a config we ourselves serialized).
    private static final Map<String, String> KEY_MIGRATION_REVERSE = buildReverse();

    private static Map<String, String> buildReverse() {
        Map<String, String> m = new HashMap<>();
        for (Map.Entry<String, String> e : KEY_MIGRATION.entrySet()) m.put(e.getValue(), e.getKey());
        return m;
    }

    /** Resolve a config key to its OrcaSlicer engine name (identity for keys already current/unknown). */
    public static String migrateKey(String key) {
        String mapped = KEY_MIGRATION.get(key);
        return mapped != null ? mapped : key;
    }

    /** Resolve an OrcaSlicer key back to its legacy name (identity if it has none). */
    public static String legacyKey(String key) {
        String mapped = KEY_MIGRATION_REVERSE.get(key);
        return mapped != null ? mapped : key;
    }

    private String title;
    public Map<String, String> values = new HashMap<>();

    // Used only in setup
    public String thumbnailUrl;

    // Type for isSelected()
    public int profileListType;

    public ConfigObject() {
        title = null;
    }

    public ConfigObject(String title) {
        this.title = title;
    }

    public ConfigObject(ConfigObject from) {
        this.title = from.title;
        this.values.putAll(from.values);
    }

    /**
     * Note: suitable only from "printer" config
     */
    public int getExtruderCount() {
        return get("nozzle_diameter") != null ? get("nozzle_diameter").replaceAll("[^.]+", "").length() : 1;
    }

    public boolean has(String key) {
        return values.containsKey(migrateKey(key));
    }

    public String get(String key) {
        return values.get(migrateKey(key));
    }

    public void remove(String key) {
        values.remove(migrateKey(key));
    }

    public void put(String key, String value) {
        values.put(migrateKey(key), value);
    }

    @Override
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public boolean isSelected() {
        switch (profileListType) {
            case PROFILE_LIST_PRINT:
                return getTitle().equals(SliceBeam.CONFIG.presets.get("print"));
            case PROFILE_LIST_FILAMENT:
                return getTitle().equals(SliceBeam.CONFIG.presets.get("filament"));
            case PROFILE_LIST_PRINTER:
                return getTitle().equals(SliceBeam.CONFIG.presets.get("printer"));
        }
        return false;
    }

    public static String normalizeSerializedValue(String key, String value) {
        if (value == null) {
            return null;
        }
        if ("before_layer_change_gcode".equals(key) && value.contains("G92 E0")) {
            StringBuilder cleaned = new StringBuilder();
            String[] lines = value.split("\n", -1);
            for (String line : lines) {
                if (!line.contains("G92 E0")) {
                    if (cleaned.length() > 0) cleaned.append('\n');
                    cleaned.append(line);
                }
            }
            return cleaned.toString();
        }
        if (key.endsWith("_gcode")) {
            value = value.replace("[bed_temperature_initial_layer_single]", "{first_layer_bed_temperature[0]}");
            value = value.replace("[bed_temperature_initial_layer]", "{first_layer_bed_temperature[0]}");
            value = value.replace("[nozzle_temperature_initial_layer]", "{first_layer_temperature[0]}");
        }
        return value;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("# generated by Slice Beam ").append(BuildConfig.VERSION_NAME).append("\n\n");
        for (Map.Entry<String, String> en : values.entrySet()) {
            String value = normalizeSerializedValue(en.getKey(), en.getValue());
            sb.append(en.getKey()).append(" = ").append(value.replace("\n", "\\n")).append("\n");
        }
        return sb.toString();
    }

    public static ConfigObject createCustomPrinterProfile() {
        ConfigObject custom = new ConfigObject(SliceBeam.INSTANCE.getString(R.string.IntroCustomProfileName));
        custom.put("printer_technology", "FFF");
        custom.put("bed_shape", "0x0,200x0,200x200,0x200");
        custom.put("binary_gcode", "0");
        custom.put("gcode_flavor", "marlin");
        custom.put("max_print_height", "200");
        custom.put("min_layer_height", "0.15");
        custom.put("max_layer_height", "0.30");
        custom.put("layer_height", "0.2");
        custom.put("nozzle_diameter", "0.4");
        custom.put("z_offset", "0");
        custom.put("retract_length", "0.5");
        custom.put("retract_speed", "30");
        custom.put("deretract_speed", "30");
        custom.put("retract_before_travel", "2");

        custom.put("machine_limits_usage", "time_estimate_only");
        custom.put("machine_max_acceleration_e", "5000");
        custom.put("machine_max_acceleration_extruding", "500");
        custom.put("machine_max_acceleration_retracting", "1000");
        custom.put("machine_max_acceleration_travel", "500");
        custom.put("machine_max_acceleration_x", "500");
        custom.put("machine_max_acceleration_y", "500");
        custom.put("machine_max_acceleration_z", "100");
        custom.put("machine_max_feedrate_e", "60");
        custom.put("machine_max_feedrate_x", "500");
        custom.put("machine_max_feedrate_y", "500");
        custom.put("machine_max_feedrate_z", "10");
        custom.put("machine_max_jerk_e", "5");
        custom.put("machine_max_jerk_x", "8");
        custom.put("machine_max_jerk_y", "8");
        custom.put("machine_max_jerk_z", "0.4");
        custom.put("machine_min_extruding_rate", "0");
        custom.put("machine_min_travel_rate", "0");

        custom.put("start_gcode", "G90 ; use absolute coordinates\\nM83 ; extruder relative mode\\nM104 S{is_nil(idle_temperature[0]) ? 150 : idle_temperature[0]} ; set temporary nozzle temp to prevent oozing during homing\\nM140 S{first_layer_bed_temperature[0]} ; set final bed temp\\nG4 S30 ; allow partial nozzle warmup\\nG28 ; home all axis\\nG1 Z50 F240\\nG1 X2.0 Y10 F3000\\nM104 S{first_layer_temperature[0]} ; set final nozzle temp\\nM190 S{first_layer_bed_temperature[0]} ; wait for bed temp to stabilize\\nM109 S{first_layer_temperature[0]} ; wait for nozzle temp to stabilize\\nG1 Z0.28 F240\\nG92 E0\\nG1 X2.0 Y140 E10 F1500 ; prime the nozzle\\nG1 X2.3 Y140 F5000\\nG92 E0\\nG1 X2.3 Y10 E10 F1200 ; prime the nozzle\\nG92 E0");
        custom.put("end_gcode", "{if max_layer_z < max_print_height}G1 Z{z_offset+min(max_layer_z+2, max_print_height)} F600 ; Move print head up{endif}\\nG1 X5 Y{print_bed_max[1]*0.85} F{travel_speed*60} ; present print\\n{if max_layer_z < max_print_height-10}G1 Z{z_offset+min(max_layer_z+70, max_print_height-10)} F600 ; Move print head further up{endif}\\n{if max_layer_z < max_print_height*0.6}G1 Z{max_print_height*0.6} F600 ; Move print head further up{endif}\\nM140 S0 ; turn off heatbed\\nM104 S0 ; turn off temperature\\nM107 ; turn off fan\\nM84 X Y E ; disable motors");

        return custom;
    }

    public static ConfigObject createCustomFilamentProfile() {
        ConfigObject genericFilament = new ConfigObject(SliceBeam.INSTANCE.getString(R.string.IntroCustomProfileFilamentName));
        genericFilament.profileListType = ConfigObject.PROFILE_LIST_FILAMENT;
        genericFilament.put("first_layer_bed_temperature", "60");
        genericFilament.put("bed_temperature", "60");
        genericFilament.put("first_layer_temperature", "210");
        genericFilament.put("temperature", "210");
        genericFilament.put("filament_type", "PLA");
        genericFilament.put("slowdown_below_layer_time", "8");
        genericFilament.put("cooling", "1");
        genericFilament.put("fan_always_on", "1");
        genericFilament.put("fan_below_layer_time", "20");
        genericFilament.put("idle_temperature", "150");
        return genericFilament;
    }
}
