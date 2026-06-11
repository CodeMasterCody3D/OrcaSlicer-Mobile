package ru.ytkab0bp.slicebeam.slic3r;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import ru.ytkab0bp.slicebeam.BuildConfig;
import ru.ytkab0bp.slicebeam.config.ConfigObject;

public class Slic3rConfigWrapper {
    public final static String BLACKLISTED_SYMBOLS = "<>[]:/\\|?*\"";

    public final static List<String> PRINT_CONFIG_KEYS = Arrays.asList(
            "layer_height", "first_layer_height", "perimeters", "spiral_vase", "slice_closing_radius", "slicing_mode",
            "top_solid_layers", "top_solid_min_thickness", "bottom_solid_layers", "bottom_solid_min_thickness",
            "extra_perimeters", "extra_perimeters_on_overhangs", "avoid_crossing_curled_overhangs", "avoid_crossing_perimeters", "thin_walls", "overhangs",
            "seam_position","staggered_inner_seams", "external_perimeters_first", "fill_density", "fill_pattern", "top_fill_pattern", "bottom_fill_pattern",
            "infill_every_layers", /*"infill_only_where_needed",*/ "solid_infill_every_layers", "fill_angle", "bridge_angle",
            "solid_infill_below_area", "only_retract_when_crossing_perimeters", "infill_first",
            "ironing", "ironing_type", "ironing_flowrate", "ironing_speed", "ironing_spacing",
            "max_print_speed", "max_volumetric_speed", "avoid_crossing_perimeters_max_detour",
            "fuzzy_skin", "fuzzy_skin_thickness", "fuzzy_skin_point_dist",
            "max_volumetric_extrusion_rate_slope_positive", "max_volumetric_extrusion_rate_slope_negative",
            "perimeter_speed", "small_perimeter_speed", "external_perimeter_speed", "infill_speed", "solid_infill_speed",
            "enable_dynamic_overhang_speeds", "overhang_speed_0", "overhang_speed_1", "overhang_speed_2", "overhang_speed_3",
            "top_solid_infill_speed", "support_material_speed", "support_material_xy_spacing", "support_material_interface_speed",
            "bridge_speed", "gap_fill_speed", "gap_fill_enabled", "travel_speed", "travel_speed_z", "first_layer_speed", "first_layer_speed_over_raft", "perimeter_acceleration", "infill_acceleration",
            "external_perimeter_acceleration", "top_solid_infill_acceleration", "solid_infill_acceleration", "travel_acceleration", "wipe_tower_acceleration",
            "bridge_acceleration", "first_layer_acceleration", "first_layer_acceleration_over_raft", "default_acceleration", "skirts", "skirt_distance", "skirt_height", "draft_shield",
            "min_skirt_length", "brim_width", "brim_separation", "brim_type", "support_material", "support_material_auto", "support_material_threshold", "support_material_enforce_layers",
            "raft_layers", "raft_first_layer_density", "raft_first_layer_expansion", "raft_contact_distance", "raft_expansion",
            "support_material_pattern", "support_material_with_sheath", "support_material_spacing", "support_material_closing_radius", "support_material_style",
            "support_material_synchronize_layers", "support_material_angle", "support_material_interface_layers", "support_material_bottom_interface_layers",
            "support_material_interface_pattern", "support_material_interface_spacing", "support_material_interface_contact_loops",
            "support_material_contact_distance", "support_material_bottom_contact_distance",
            "support_material_buildplate_only",
            "support_tree_angle", "support_tree_angle_slow", "support_tree_branch_diameter", "support_tree_branch_diameter_angle", "support_tree_branch_diameter_double_wall",
            "support_tree_top_rate", "support_tree_branch_distance", "support_tree_tip_diameter",
            "dont_support_bridges", "thick_bridges", "notes", "complete_objects", "extruder_clearance_radius",
            "extruder_clearance_height", "gcode_comments", "gcode_label_objects", "output_filename_format", "post_process", "gcode_substitutions", "perimeter_extruder",
            "infill_extruder", "solid_infill_extruder", "support_material_extruder", "support_material_interface_extruder",
            "ooze_prevention", "standby_temperature_delta", "interface_shells", "extrusion_width", "first_layer_extrusion_width",
            "perimeter_extrusion_width", "external_perimeter_extrusion_width", "infill_extrusion_width", "solid_infill_extrusion_width",
            "top_infill_extrusion_width", "support_material_extrusion_width", "infill_overlap", "infill_anchor", "infill_anchor_max", "bridge_flow_ratio",
            "elefant_foot_compensation", "xy_size_compensation", "resolution", "gcode_resolution",
            "wipe_tower", "wipe_tower_x", "wipe_tower_y",
            "wipe_tower_width", "wipe_tower_cone_angle", "wipe_tower_rotation_angle", "wipe_tower_brim_width", "wipe_tower_bridging", "single_extruder_multi_material_priming", "mmu_segmented_region_max_width",
            "mmu_segmented_region_interlocking_depth", "wipe_tower_extruder", "wipe_tower_no_sparse_layers", "wipe_tower_extra_flow", "wipe_tower_extra_spacing", "compatible_printers", "compatible_printers_condition", "inherits",
            "perimeter_generator", "wall_transition_length", "wall_transition_filter_deviation", "wall_transition_angle",
            "wall_distribution_count", "min_feature_size", "min_bead_width",
            "top_one_perimeter_type", "only_one_perimeter_first_layer",
            "only_one_wall_top",
            // OrcaSlicer-native process keys with no legacy equivalent
            "initial_layer_infill_speed", "elefant_foot_compensation_layers", "enable_arc_fitting",
            "wall_direction", "slowdown_for_curled_perimeters", "xy_hole_compensation",
            "default_jerk", "outer_wall_jerk", "inner_wall_jerk", "infill_jerk",
            "top_surface_jerk", "initial_layer_jerk", "travel_jerk"
    );
    public final static List<String> FILAMENT_CONFIG_KEYS = Arrays.asList(
            "filament_colour", "filament_diameter", "filament_type", "filament_soluble", "filament_notes", "filament_max_volumetric_speed", "filament_infill_max_speed", "filament_infill_max_crossing_speed",
            "extrusion_multiplier", "filament_density", "filament_cost", "filament_spool_weight", "filament_loading_speed", "filament_loading_speed_start", "filament_load_time",
            "filament_unloading_speed", "filament_unloading_speed_start", "filament_unload_time", "filament_toolchange_delay", "filament_cooling_moves", "filament_stamping_loading_speed", "filament_stamping_distance",
            "filament_cooling_initial_speed", "filament_purge_multiplier", "filament_cooling_final_speed", "filament_ramming_parameters", "filament_minimal_purge_on_wipe_tower",
            "filament_multitool_ramming", "filament_multitool_ramming_volume", "filament_multitool_ramming_flow",
            "temperature", "idle_temperature", "first_layer_temperature", "bed_temperature", "first_layer_bed_temperature", "fan_always_on", "cooling", "min_fan_speed",
            "max_fan_speed", "bridge_fan_speed", "disable_fan_first_layers", "full_fan_speed_layer", "fan_below_layer_time", "slowdown_below_layer_time", "min_print_speed",
            "start_filament_gcode", "end_filament_gcode", "enable_dynamic_fan_speeds", "chamber_temperature", "chamber_minimal_temperature",
            "overhang_fan_speed_0", "overhang_fan_speed_1", "overhang_fan_speed_2", "overhang_fan_speed_3",
            // Retract overrides
            "filament_retract_length", "filament_retract_lift", "filament_retract_lift_above", "filament_retract_lift_below", "filament_retract_speed", "filament_deretract_speed", "filament_retract_restart_extra", "filament_retract_before_travel",
            "filament_retract_layer_change", "filament_wipe", "filament_retract_before_wipe", "filament_retract_length_toolchange", "filament_retract_restart_extra_toolchange", "filament_travel_ramping_lift",
            "filament_travel_slope", "filament_travel_max_lift", "filament_travel_lift_before_obstacle",
            // Profile compatibility
            "filament_vendor", "compatible_prints", "compatible_prints_condition", "compatible_printers", "compatible_printers_condition", "inherits",
            // Shrinkage compensation
            "filament_shrinkage_compensation_xy", "filament_shrinkage_compensation_z",
            // Per-plate bed temperatures (the hot-plate pair imports via legacy bed_temperature keys)
            "cool_plate_temp", "cool_plate_temp_initial_layer", "eng_plate_temp", "eng_plate_temp_initial_layer",
            "textured_plate_temp", "textured_plate_temp_initial_layer", "textured_cool_plate_temp", "textured_cool_plate_temp_initial_layer",
            "supertack_plate_temp", "supertack_plate_temp_initial_layer",
            "additional_cooling_fan_speed", "overhang_fan_threshold"
    );
    public final static List<String> PRINTER_CONFIG_KEYS = Arrays.asList(
            "printer_technology", "autoemit_temperature_commands",
            "bed_shape", "bed_custom_texture", "bed_custom_model", "binary_gcode", "z_offset", "gcode_flavor", "use_relative_e_distances",
            "use_firmware_retraction", "use_volumetric_e", "variable_layer_height", "prefer_clockwise_movements",
            //FIXME the print host keys are left here just for conversion from the Printer preset to Physical Printer preset.
            "host_type", "print_host", "printhost_apikey", "printhost_cafile",
            "single_extruder_multi_material", "start_gcode", "end_gcode", "before_layer_gcode", "layer_gcode", "toolchange_gcode",
            "color_change_gcode", "pause_print_gcode", "template_custom_gcode",
            "between_objects_gcode", "printer_vendor", "printer_model", "printer_variant", "printer_notes", "cooling_tube_retraction",
            "cooling_tube_length", "high_current_on_filament_swap", "parking_pos_retraction", "extra_loading_move", "multimaterial_purging",
            "max_print_height", "default_print_profile", "inherits",
            "remaining_times", "silent_mode",
            "machine_limits_usage", "thumbnails", "thumbnails_format",
            "machine_max_acceleration_extruding", "machine_max_acceleration_retracting", "machine_max_acceleration_travel",
            "machine_max_acceleration_x", "machine_max_acceleration_y", "machine_max_acceleration_z", "machine_max_acceleration_e",
            "machine_max_feedrate_x", "machine_max_feedrate_y", "machine_max_feedrate_z", "machine_max_feedrate_e",
            "machine_min_extruding_rate", "machine_min_travel_rate",
            "machine_max_jerk_x", "machine_max_jerk_y", "machine_max_jerk_z", "machine_max_jerk_e",
            // Extruder block (per-extruder vectors). Legacy names where one exists (KEY_MIGRATION
            // renames them on storage), OrcaSlicer names for options with no legacy equivalent.
            "nozzle_diameter", "min_layer_height", "max_layer_height", "extruder_offset", "extruder_colour",
            "retract_length", "retract_lift", "retract_lift_above", "retract_lift_below", "retract_lift_enforce",
            "z_hop_types", "travel_slope", "retract_speed", "deretract_speed", "retract_before_travel",
            "retract_layer_change", "wipe", "wipe_distance", "retract_before_wipe", "retract_restart_extra",
            "retract_length_toolchange", "retract_restart_extra_toolchange", "default_filament_profile",
            "enable_filament_ramming", "curr_bed_type"
    );
    public final static List<String> PHYSICAL_PRINTER_CONFIG_KEYS = Arrays.asList(
            "preset_name", // temporary option to compatibility with older Slicer
            "preset_names",
            "printer_technology",
            "host_type",
            "print_host",
            "printhost_apikey",
            "printhost_cafile",
            "printhost_port",
            "printhost_authorization_type",
            // HTTP digest authentization (RFC 2617)
            "printhost_user",
            "printhost_password",
            "printhost_ssl_ignore_revoke"
    );

    private File file;

    public List<ConfigObject> printConfigs = new ArrayList<>();
    public List<ConfigObject> printerConfigs = new ArrayList<>();
    public List<ConfigObject> filamentConfigs = new ArrayList<>();
    public List<ConfigObject> physicalPrintersConfigs = new ArrayList<>();

    public List<ConfigObject> printerModels = new ArrayList<>();
    public ConfigObject presets;
    public ConfigObject vendor;

    public Slic3rConfigWrapper() {}

    public Slic3rConfigWrapper(File f) throws IOException {
        file = f;
        readFromStream(new FileInputStream(file));
    }

    public Slic3rConfigWrapper(InputStream in) throws IOException {
        readFromStream(in);
    }

    public void importPrint(ConfigObject obj) {
        importInto(printConfigs, obj);
    }

    public void importPrinter(ConfigObject obj) {
        importInto(printerConfigs, obj);
    }

    public void importFilament(ConfigObject obj) {
        importInto(filamentConfigs, obj);
    }

    public void importInto(List<ConfigObject> list, ConfigObject obj) {
        for (ConfigObject o : list) {
            if (Objects.equals(o.getTitle(), obj.getTitle())) {
                o.values.clear();
                o.values.putAll(obj.values);
                return;
            }
        }
        list.add(obj);
    }

    public ConfigObject findFilament(String key) {
        for (ConfigObject obj : filamentConfigs) {
            if (Objects.equals(key, obj.getTitle())) {
                return obj;
            }
        }
        return null;
    }

    public ConfigObject findPrinterVariant(String model, String variant) {
        for (ConfigObject obj : printerConfigs) {
            if (Objects.equals(model, obj.get("printer_model")) && Objects.equals(variant, obj.get("printer_variant"))) {
                return obj;
            }
        }
        return null;
    }

    public ConfigObject findPrint(String key) {
        for (ConfigObject obj : printConfigs) {
            if (Objects.equals(key, obj.getTitle())) {
                return obj;
            }
        }
        return null;
    }

    public ConfigObject findPrinter(String key) {
        for (ConfigObject obj : printerConfigs) {
            if (Objects.equals(key, obj.getTitle())) {
                return obj;
            }
        }
        return null;
    }

    private void serializeList(StringBuilder sb, String key, List<ConfigObject> list) {
        for (ConfigObject cfg : list) {
            sb.append("[").append(key).append(":").append(cfg.getTitle()).append("]\n");

            for (Map.Entry<String, String> en : cfg.values.entrySet()) {
                String value = ConfigObject.normalizeSerializedValue(en.getKey(), en.getValue());
                sb.append(en.getKey()).append(" = ").append(value.replace("\n", "\\n")).append("\n");
            }
            sb.append("\n");
        }
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("# generated by Slice Beam ").append(BuildConfig.VERSION_NAME).append("\n\n");
        serializeList(sb, "printer", printerConfigs);
        serializeList(sb, "print", printConfigs);
        serializeList(sb, "filament", filamentConfigs);

        if (presets != null) {
            sb.append("[presets]\n");
            for (Map.Entry<String, String> en : presets.values.entrySet()) {
                sb.append(en.getKey()).append(" = ").append(en.getValue().replace("\n", "\\n")).append("\n");
            }
        }

        return sb.toString();
    }

    public void readFromStream(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in));

        ConfigObject currentPrintConfig = null;
        ConfigObject currentPrinterConfig = null;
        ConfigObject currentFilamentConfig = null;
        ConfigObject currentPhysicalPrinterConfig = null;

        ConfigObject explicitObject = null;

        Map<String, ConfigObject> parentMap = new HashMap<>();

        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("#")) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                if (line.equals("[obsolete_presets]")) {
                    explicitObject = new ConfigObject();
                    continue;
                }

                if (!line.contains(":") && !line.equals("[presets]") && !line.equals("[vendor]")) {
                    throw new UnsupportedEncodingException(String.format("Failed to decode config category: %s", line));
                }
                if (currentPrintConfig != null || currentPrinterConfig != null || currentFilamentConfig != null || currentPhysicalPrinterConfig != null) {
                    throw new UnsupportedEncodingException("Failed to decode config: explicit category in combined profile!");
                }

                if (line.equals("[presets]")) {
                    explicitObject = presets = new ConfigObject();
                    continue;
                }
                if (line.equals("[vendor]")) {
                    explicitObject = vendor = new ConfigObject();
                    continue;
                }

                line = line.substring(1, line.length() - 1);
                String[] spl = line.split(":");
                String key = spl[0];
                String name = spl[1];

                switch (key) {
                    case "printer_model": {
                        printerModels.add(explicitObject = new ConfigObject(name));
                        break;
                    }
                    case "print": {
                        printConfigs.add(explicitObject = new ConfigObject(name));
                        explicitObject.profileListType = ConfigObject.PROFILE_LIST_PRINT;
                        break;
                    }
                    case "printer": {
                        printerConfigs.add(explicitObject = new ConfigObject(name));
                        explicitObject.profileListType = ConfigObject.PROFILE_LIST_PRINTER;
                        break;
                    }
                    case "physical_printer": {
                        physicalPrintersConfigs.add(explicitObject = new ConfigObject(name));
                        break;
                    }
                    case "filament": {
                        filamentConfigs.add(explicitObject = new ConfigObject(name));
                        explicitObject.profileListType = ConfigObject.PROFILE_LIST_FILAMENT;
                        break;
                    }
                }

                parentMap.put(name, explicitObject);
            }

            int i = line.indexOf(" = ");
            if (i != -1) {
                String key = line.substring(0, i);
                String value = line.substring(i + 3).trim().replace("\\n", "\n");

                if (key.equals("arc_fitting")) {
                    continue;
                }
                if (key.equals("thumbnails")) {
                    value = value.replaceAll(", \\d+x\\d+/COLPIC", "");
                }

                if (explicitObject != null) {
                    explicitObject.put(key, value);
                } else {
                    if (key.equals("printer_settings_id")) {
                        if (currentPrinterConfig == null)
                            currentPrinterConfig = new ConfigObject();
                        currentPrinterConfig.setTitle(value);
                    }
                    if (key.equals("print_settings_id")) {
                        if (currentPrintConfig == null)
                            currentPrintConfig = new ConfigObject();
                        currentPrintConfig.setTitle(value);
                    }
                    if (key.equals("filament_settings_id")) {
                        if (currentFilamentConfig == null)
                            currentFilamentConfig = new ConfigObject();
                        currentFilamentConfig.setTitle(value);
                    }

                    // The *_CONFIG_KEYS sets are keyed by legacy names; legacyKey() lets an
                    // already-migrated key (from a config we serialized) still be categorized.
                    String legacyKey = ConfigObject.legacyKey(key);
                    if (PRINT_CONFIG_KEYS.contains(key) || PRINT_CONFIG_KEYS.contains(legacyKey)) {
                        if (currentPrintConfig == null)
                            currentPrintConfig = new ConfigObject();
                        currentPrintConfig.put(key, value);
                    }
                    if (FILAMENT_CONFIG_KEYS.contains(key) || FILAMENT_CONFIG_KEYS.contains(legacyKey)) {
                        if (currentFilamentConfig == null)
                            currentFilamentConfig = new ConfigObject();
                        currentFilamentConfig.put(key, value);
                    }
                    if (PRINTER_CONFIG_KEYS.contains(key) || PRINTER_CONFIG_KEYS.contains(legacyKey)) {
                        if (currentPrinterConfig == null)
                            currentPrinterConfig = new ConfigObject();
                        currentPrinterConfig.put(key, value);
                    }
                    if (PHYSICAL_PRINTER_CONFIG_KEYS.contains(key) || PHYSICAL_PRINTER_CONFIG_KEYS.contains(legacyKey)) {
                        if (currentPhysicalPrinterConfig == null)
                            currentPhysicalPrinterConfig = new ConfigObject();
                        currentPhysicalPrinterConfig.put(key, value);
                    }
                }
            }
        }

        for (ConfigObject obj : parentMap.values()) {
            while (obj.values.containsKey("inherits")) {
                String value = obj.values.remove("inherits");
                if (value.isEmpty()) continue;

                if (value.contains(";")) {
                    String[] spl = value.split(";");

                    for (String s : spl) {
                        String str = s.trim();
                        Map<String, String> newValues = new HashMap<>();
                        newValues.putAll(parentMap.get(str).values);
                        newValues.putAll(obj.values);
                        obj.values = newValues;
                    }
                } else {
                    if (parentMap.containsKey(value)) {
                        Map<String, String> newValues = new HashMap<>();
                        newValues.putAll(parentMap.get(value).values);
                        newValues.putAll(obj.values);
                        obj.values = newValues;
                    }
                }
            }
        }

        if (currentPrintConfig != null) {
            printConfigs.add(currentPrintConfig);
        }
        if (currentPrinterConfig != null) {
            printerConfigs.add(currentPrinterConfig);
        }
        if (currentFilamentConfig != null) {
            filamentConfigs.add(currentFilamentConfig);
        }
        if (currentPhysicalPrinterConfig != null) {
            physicalPrintersConfigs.add(currentPhysicalPrinterConfig);
        }

        if (presets == null) {
            presets = new ConfigObject();
            if (currentPrintConfig != null) {
                presets.put("print", currentPrintConfig.getTitle());
            }
            if (currentFilamentConfig != null) {
                presets.put("filament", currentFilamentConfig.getTitle());
            }
            if (currentPrinterConfig != null) {
                presets.put("printer", currentPrinterConfig.getTitle());
            }
            if (currentPhysicalPrinterConfig != null) {
                presets.put("physical_printer", currentPhysicalPrinterConfig.getTitle());
            }
        }

        r.close();
        in.close();
    }
}
