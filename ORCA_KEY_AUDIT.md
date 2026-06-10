# Orca key whitelist audit

Generated 2026-06-10 against `app/src/main/jni/libslic3r` (ported Orca engine) vs the Java import whitelists.


## How keys flow
Orca JSON key → `IOUtils.mapOrcaConfigKey()` (Orca→legacy rename) → must be in
`Slic3rConfigWrapper.PRINT/FILAMENT/PRINTER_CONFIG_KEYS` or it is **silently dropped** →
stored in `ConfigObject` (`KEY_MIGRATION` legacy→Orca rename) → serialized INI →
engine `config.load()` (applies `PrintConfigDef::handle_legacy`).

Engine option universe (from `Preset.cpp` + machine-limit/extruder/filament-override
key lists in `PrintConfig.cpp`): **356 print / 128 filament / 159 printer** options.


## 1. CRITICAL: printer whitelist lacks the entire extruder block

Importing an `.orca_printer` machine profile silently drops all of these (engine then uses defaults, e.g. 0.4 nozzle):

- `nozzle_diameter`
- `min_layer_height`
- `max_layer_height`
- `extruder_offset`
- `extruder_colour`
- `retraction_length`
- `z_hop`
- `z_hop_types`
- `travel_slope`
- `retract_lift_above`
- `retract_lift_below`
- `retract_lift_enforce`
- `retraction_speed`
- `deretraction_speed`
- `retraction_minimum_travel`
- `retract_when_changing_layer`
- `wipe`
- `wipe_distance`
- `retract_before_wipe`
- `retract_restart_extra`
- `retract_length_toolchange`
- `retract_restart_extra_toolchange`
- `machine_max_speed_x`
- `machine_max_speed_y`
- `machine_max_speed_z`
- `machine_max_speed_e`
- `default_filament_profile`

Note: the whitelist has `machine_max_feedrate_*`, but the Orca engine renamed these to `machine_max_speed_*` and has **no** legacy alias for them — feedrate limits are currently lost end-to-end.


## 2. Renames known to KEY_MIGRATION but not to mapOrcaConfigKey

These engine keys are importable in principle (their legacy name is whitelisted) but `mapOrcaConfigKey()` doesn't know the rename, so the Orca-named JSON key is dropped. Replacing `mapOrcaConfigKey()` with `ConfigObject.legacyKey()` rescues all of them in one change:


### print (61)
- `bottom_surface_pattern` (legacy `bottom_fill_pattern`)
- `bridge_flow` (legacy `bridge_flow_ratio`)
- `bridge_no_support` (legacy `dont_support_bridges`)
- `brim_object_gap` (legacy `brim_separation`)
- `enable_prime_tower` (legacy `wipe_tower`)
- `enforce_support_layers` (legacy `support_material_enforce_layers`)
- `filename_format` (legacy `output_filename_format`)
- `fuzzy_skin_point_distance` (legacy `fuzzy_skin_point_dist`)
- `gap_fill_target` (legacy `gap_fill_enabled`)
- `infill_combination` (legacy `infill_every_layers`)
- `infill_direction` (legacy `fill_angle`)
- `infill_wall_overlap` (legacy `infill_overlap`)
- `initial_layer_acceleration` (legacy `first_layer_acceleration`)
- `internal_solid_infill_acceleration` (legacy `solid_infill_acceleration`)
- `ironing_flow` (legacy `ironing_flowrate`)
- `is_infill_first` (legacy `infill_first`)
- `max_travel_detour_distance` (legacy `avoid_crossing_perimeters_max_detour`)
- `max_volumetric_extrusion_rate_slope` (legacy `max_volumetric_extrusion_rate_slope_positive`)
- `minimum_sparse_infill_area` (legacy `solid_infill_below_area`)
- `only_one_wall_first_layer` (legacy `only_one_perimeter_first_layer`)
- `outer_wall_acceleration` (legacy `external_perimeter_acceleration`)
- `overhang_1_4_speed` (legacy `overhang_speed_0`)
- `overhang_2_4_speed` (legacy `overhang_speed_1`)
- `overhang_3_4_speed` (legacy `overhang_speed_2`)
- `overhang_4_4_speed` (legacy `overhang_speed_3`)
- `prime_tower_brim_width` (legacy `wipe_tower_brim_width`)
- `prime_tower_width` (legacy `wipe_tower_width`)
- `reduce_crossing_wall` (legacy `avoid_crossing_perimeters`)
- `reduce_infill_retraction` (legacy `only_retract_when_crossing_perimeters`)
- `skirt_loops` (legacy `skirts`)
- `sparse_infill_acceleration` (legacy `infill_acceleration`)
- `spiral_mode` (legacy `spiral_vase`)
- `support_angle` (legacy `support_material_angle`)
- `support_base_pattern` (legacy `support_material_pattern`)
- `support_base_pattern_spacing` (legacy `support_material_spacing`)
- `support_bottom_z_distance` (legacy `support_material_bottom_contact_distance`)
- `support_filament` (legacy `support_material_extruder`)
- `support_interface_bottom_layers` (legacy `support_material_bottom_interface_layers`)
- `support_interface_filament` (legacy `support_material_interface_extruder`)
- `support_interface_loop_pattern` (legacy `support_material_interface_contact_loops`)
- `support_interface_pattern` (legacy `support_material_interface_pattern`)
- `support_interface_spacing` (legacy `support_material_interface_spacing`)
- `support_interface_speed` (legacy `support_material_interface_speed`)
- `support_interface_top_layers` (legacy `support_material_interface_layers`)
- `support_object_xy_distance` (legacy `support_material_xy_spacing`)
- `support_on_build_plate_only` (legacy `support_material_buildplate_only`)
- `support_speed` (legacy `support_material_speed`)
- `support_style` (legacy `support_material_style`)
- `support_threshold_angle` (legacy `support_material_threshold`)
- `support_top_z_distance` (legacy `support_material_contact_distance`)
- `top_surface_acceleration` (legacy `top_solid_infill_acceleration`)
- `top_surface_pattern` (legacy `top_fill_pattern`)
- `tree_support_angle_slow` (legacy `support_tree_angle_slow`)
- `tree_support_branch_angle` (legacy `support_tree_angle`)
- `tree_support_branch_diameter` (legacy `support_tree_branch_diameter`)
- `tree_support_branch_diameter_angle` (legacy `support_tree_branch_diameter_angle`)
- `tree_support_branch_distance` (legacy `support_tree_branch_distance`)
- `tree_support_tip_diameter` (legacy `support_tree_tip_diameter`)
- `tree_support_top_rate` (legacy `support_tree_top_rate`)
- `wall_generator` (legacy `perimeter_generator`)
- `wipe_tower_filament` (legacy `wipe_tower_extruder`)

### filament (12)
- `close_fan_the_first_x_layers` (legacy `disable_fan_first_layers`)
- `fan_cooling_layer_time` (legacy `fan_below_layer_time`)
- `filament_deretraction_speed` (legacy `filament_deretract_speed`)
- `filament_retract_when_changing_layer` (legacy `filament_retract_layer_change`)
- `filament_retraction_length` (legacy `filament_retract_length`)
- `filament_retraction_minimum_travel` (legacy `filament_retract_before_travel`)
- `filament_retraction_speed` (legacy `filament_retract_speed`)
- `filament_z_hop` (legacy `filament_retract_lift`)
- `hot_plate_temp` (legacy `bed_temperature`)
- `hot_plate_temp_initial_layer` (legacy `first_layer_bed_temperature`)
- `reduce_fan_stop_start_freq` (legacy `fan_always_on`)
- `slow_down_for_layer_cooling` (legacy `cooling`)

### printer (1)
- `machine_pause_gcode` (legacy `pause_print_gcode`)

## 3. Engine options with no whitelist path at all

Need whitelist additions (and `KEY_MIGRATION`/category placement only if exposing in UI). Full lists; many are internal/BBL-machine plumbing — user-facing highlights marked below in section 5.


### print (208)
- `alternate_extra_wall`
- `spiral_mode_smooth`
- `spiral_mode_max_xy_smoothing`
- `spiral_starting_flow_ratio`
- `spiral_finishing_flow_ratio`
- `top_surface_density`
- `bottom_surface_density`
- `ensure_vertical_shell_thickness`
- `overhang_reverse`
- `overhang_reverse_threshold`
- `overhang_reverse_internal_only`
- `wall_direction`
- `wall_sequence`
- `fill_multiline`
- `gyroid_optimized`
- `lateral_lattice_angle_1`
- `lateral_lattice_angle_2`
- `infill_overhang_angle`
- `lightning_overhang_angle`
- `lightning_prune_angle`
- `lightning_straightening_angle`
- `solid_infill_direction`
- `counterbore_hole_bridging`
- `infill_shift_step`
- `sparse_infill_rotate_template`
- `solid_infill_rotate_template`
- `symmetric_infill_y_axis`
- `skeleton_infill_density`
- `infill_lock_depth`
- `skin_infill_depth`
- `skin_infill_density`
- `align_infill_direction_to_model`
- `extra_solid_infills`
- `internal_solid_infill_pattern`
- `ironing_pattern`
- `ironing_angle`
- `ironing_angle_fixed`
- `ironing_inset`
- `support_ironing`
- `support_ironing_pattern`
- `support_ironing_flow`
- `support_ironing_spacing`
- `fuzzy_skin_first_layer`
- `fuzzy_skin_noise_type`
- `fuzzy_skin_mode`
- `fuzzy_skin_scale`
- `fuzzy_skin_octaves`
- `fuzzy_skin_persistence`
- `fuzzy_skin_ripples_per_layer`
- `fuzzy_skin_ripple_offset`
- `fuzzy_skin_layers_between_ripple_offset`
- `max_volumetric_extrusion_rate_slope_segment_length`
- `extrusion_rate_smoothing_external_perimeter_only`
- `support_object_first_layer_gap`
- `internal_bridge_speed`
- `skirt_type`
- `skirt_speed`
- `skirt_start_angle`
- `single_loop_draft_shield`
- `brim_flow_ratio`
- `brim_use_efc_outline`
- `combine_brims`
- `brim_ears_max_angle`
- `brim_ears_detection_length`
- `support_type`
- `support_threshold_overlap`
- `support_expansion`
- `print_extruder_id`
- `print_extruder_variant`
- `independent_support_layer_height`
- `support_critical_regions_only`
- `thick_internal_bridges`
- `dont_filter_internal_bridges`
- `enable_extra_bridge_layer`
- `max_bridge_length`
- `print_sequence`
- `print_order`
- `support_remove_small_overhang`
- `outer_wall_filament_id`
- `inner_wall_filament_id`
- `sparse_infill_filament_id`
- `internal_solid_filament_id`
- `top_surface_filament_id`
- `bottom_surface_filament_id`
- `support_interface_not_for_body`
- `preheat_time`
- `preheat_steps`
- `skin_infill_line_width`
- `skeleton_infill_line_width`
- `top_bottom_infill_wall_overlap`
- `bridge_line_width`
- `internal_bridge_flow`
- `elefant_foot_compensation_layers`
- `elefant_foot_layers_density`
- `xy_contour_compensation`
- `xy_hole_compensation`
- `prime_tower_enable_framework`
- `prime_tower_skip_points`
- `prime_volume`
- `prime_tower_infill_gap`
- `prime_tower_flat_ironing`
- `enable_tower_interface_features`
- `enable_tower_interface_cooldown_during_tower`
- `flush_into_infill`
- `flush_into_objects`
- `flush_into_support`
- `tree_support_wall_count`
- `detect_narrow_internal_solid_infill`
- `gcode_add_line_number`
- `enable_arc_fitting`
- `precise_z_height`
- `infill_combination_max_layer_height`
- `adaptive_layer_height`
- `support_bottom_interface_spacing`
- `enable_overhang_speed`
- `slowdown_for_curled_perimeters`
- `only_one_wall_top`
- `timelapse_type`
- `process_change_extrusion_role_gcode`
- `min_length_factor`
- `wall_maximum_resolution`
- `wall_maximum_deviation`
- `small_perimeter_threshold`
- `internal_bridge_angle`
- `relative_bridge_angle`
- `filter_out_gap_fill`
- `inner_wall_acceleration`
- `min_width_top_surface`
- `default_jerk`
- `outer_wall_jerk`
- `inner_wall_jerk`
- `infill_jerk`
- `top_surface_jerk`
- `initial_layer_jerk`
- `travel_jerk`
- `default_junction_deviation`
- `top_solid_infill_flow_ratio`
- `bottom_solid_infill_flow_ratio`
- `print_flow_ratio`
- `seam_gap`
- `set_other_flow_ratios`
- `first_layer_flow_ratio`
- `outer_wall_flow_ratio`
- `inner_wall_flow_ratio`
- `overhang_flow_ratio`
- `sparse_infill_flow_ratio`
- `internal_solid_infill_flow_ratio`
- `gap_fill_flow_ratio`
- `support_flow_ratio`
- `support_interface_flow_ratio`
- `role_based_wipe_speed`
- `wipe_speed`
- `accel_to_decel_enable`
- `accel_to_decel_factor`
- `wipe_on_loops`
- `wipe_before_external_loop`
- `bridge_density`
- `internal_bridge_density`
- `precise_outer_wall`
- `tree_support_auto_brim`
- `tree_support_brim_width`
- `initial_layer_travel_speed`
- `initial_layer_travel_acceleration`
- `initial_layer_travel_jerk`
- `exclude_object`
- `slow_down_layers`
- `initial_layer_min_bead_width`
- `make_overhang_printable`
- `make_overhang_printable_angle`
- `make_overhang_printable_hole_size`
- `wipe_tower_max_purge_speed`
- `wipe_tower_wall_type`
- `wipe_tower_extra_rib_length`
- `wipe_tower_rib_width`
- `wipe_tower_fillet_wall`
- `wiping_volumes_extruders`
- `tree_support_branch_distance_organic`
- `tree_support_branch_diameter_organic`
- `tree_support_branch_angle_organic`
- `hole_to_polyhole`
- `hole_to_polyhole_threshold`
- `hole_to_polyhole_twisted`
- `small_area_infill_flow_compensation`
- `small_area_infill_flow_compensation_model`
- `enable_wrapping_detection`
- `seam_slope_type`
- `seam_slope_conditional`
- `scarf_angle_threshold`
- `scarf_joint_speed`
- `scarf_joint_flow_ratio`
- `seam_slope_start_height`
- `seam_slope_entire_loop`
- `seam_slope_min_length`
- `seam_slope_steps`
- `seam_slope_inner_walls`
- `scarf_overhang_threshold`
- `interlocking_beam`
- `interlocking_orientation`
- `interlocking_beam_layer_count`
- `interlocking_depth`
- `interlocking_boundary_avoidance`
- `interlocking_beam_width`
- `calib_flowrate_topinfill_special_order`
- `zaa_enabled`
- `zaa_minimize_perimeter_height`
- `zaa_dont_alternate_fill_direction`
- `zaa_min_z`
- `ironing_expansion`

### filament (68)
- `default_filament_colour`
- `required_nozzle_HRC`
- `pellet_flow_coefficient`
- `volumetric_speed_coefficients`
- `filament_is_support`
- `filament_printable`
- `filament_adaptive_volumetric_speed`
- `filament_adhesiveness_category`
- `filament_tower_interface_pre_extrusion_dist`
- `filament_tower_interface_pre_extrusion_length`
- `filament_tower_ironing_area`
- `filament_tower_interface_purge_volume`
- `filament_tower_interface_print_temp`
- `cool_plate_temp`
- `textured_cool_plate_temp`
- `eng_plate_temp`
- `textured_plate_temp`
- `cool_plate_temp_initial_layer`
- `textured_cool_plate_temp_initial_layer`
- `eng_plate_temp_initial_layer`
- `textured_plate_temp_initial_layer`
- `supertack_plate_temp_initial_layer`
- `supertack_plate_temp`
- `bed_type`
- `temperature_vitrification`
- `dont_slow_down_outer_wall`
- `enable_overhang_bridge_fan`
- `overhang_fan_threshold`
- `close_additional_fan_first_x_layers`
- `first_x_layer_fan_speed`
- `additional_fan_full_speed_layer`
- `filament_change_extrusion_role_gcode`
- `activate_air_filtration`
- `activate_air_filtration_during_print`
- `activate_air_filtration_on_completion`
- `during_print_exhaust_fan_speed`
- `complete_print_exhaust_fan_speed`
- `filament_z_hop_types`
- `filament_retract_lift_enforce`
- `filament_wipe_distance`
- `additional_cooling_fan_speed`
- `nozzle_temperature_range_low`
- `nozzle_temperature_range_high`
- `filament_extruder_variant`
- `enable_pressure_advance`
- `pressure_advance`
- `adaptive_pressure_advance`
- `adaptive_pressure_advance_model`
- `adaptive_pressure_advance_overhangs`
- `adaptive_pressure_advance_bridges`
- `filament_shrink`
- `support_material_interface_fan_speed`
- `internal_bridge_fan_speed`
- `filament_seam_gap`
- `ironing_fan_speed`
- `filament_ironing_flow`
- `filament_ironing_spacing`
- `filament_ironing_inset`
- `filament_ironing_speed`
- `activate_chamber_temp_control`
- `filament_long_retractions_when_cut`
- `filament_retraction_distances_when_cut`
- `filament_change_length`
- `filament_flush_volumetric_speed`
- `filament_flush_temp`
- `filament_cooling_before_tower`
- `long_retractions_when_ec`
- `retraction_distances_when_ec`

### printer (112)
- `extruder_printable_area`
- `support_parallel_printheads`
- `parallel_printheads_count`
- `parallel_printheads_bed_exclude_areas`
- `bed_exclude_area`
- `fan_kickstart`
- `part_cooling_fan_min_pwm`
- `fan_speedup_time`
- `fan_speedup_overhangs`
- `manual_filament_change`
- `file_start_gcode`
- `printing_by_object_gcode`
- `time_lapse_gcode`
- `wrapping_detection_gcode`
- `change_extrusion_role_gcode`
- `printer_extruder_id`
- `printer_extruder_variant`
- `extruder_variant_list`
- `default_nozzle_volume_type`
- `extruder_printable_height`
- `extruder_clearance_radius`
- `extruder_clearance_height_to_lid`
- `extruder_clearance_height_to_rod`
- `nozzle_height`
- `master_extruder_id`
- `scan_first_layer`
- `enable_power_loss_recovery`
- `wrapping_detection_layers`
- `wrapping_exclude_area`
- `machine_load_filament_time`
- `machine_unload_filament_time`
- `machine_tool_change_time`
- `time_cost`
- `nozzle_type`
- `nozzle_hrc`
- `auxiliary_fan`
- `nozzle_volume`
- `upward_compatible_machine`
- `z_hop_types`
- `travel_slope`
- `retract_lift_enforce`
- `support_chamber_temp_control`
- `support_air_filtration`
- `printer_structure`
- `best_object_pos`
- `head_wrap_detect_zone`
- `flashforge_serial_number`
- `bbl_use_printhost`
- `printer_agent`
- `print_host_webui`
- `printhost_port`
- `printhost_authorization_type`
- `printhost_user`
- `printhost_password`
- `printhost_ssl_ignore_revoke`
- `extruder_type`
- `grab_length`
- `support_object_skip_flush`
- `physical_extruder_map`
- `wipe_tower_type`
- `purge_in_prime_tower`
- `enable_filament_ramming`
- `tool_change_on_wipe_tower`
- `disable_m73`
- `preferred_orientation`
- `emit_machine_limits_to_gcode`
- `pellet_modded_printer`
- `support_multi_bed_types`
- `default_bed_type`
- `bed_mesh_min`
- `bed_mesh_max`
- `bed_mesh_probe_distance`
- `adaptive_bed_mesh_margin`
- `enable_long_retraction_when_cut`
- `long_retractions_when_cut`
- `retraction_distances_when_cut`
- `bed_temperature_formula`
- `nozzle_flush_dataset`
- `machine_max_speed_x`
- `machine_max_speed_y`
- `machine_max_speed_z`
- `machine_max_speed_e`
- `machine_max_junction_deviation`
- `resonance_avoidance`
- `min_resonance_avoidance_speed`
- `max_resonance_avoidance_speed`
- `input_shaping_emit`
- `input_shaping_type`
- `input_shaping_freq_x`
- `input_shaping_freq_y`
- `input_shaping_damp_x`
- `input_shaping_damp_y`
- `nozzle_diameter`
- `min_layer_height`
- `max_layer_height`
- `extruder_offset`
- `retraction_length`
- `z_hop`
- `retract_lift_above`
- `retract_lift_below`
- `retraction_speed`
- `deretraction_speed`
- `retract_before_wipe`
- `retract_restart_extra`
- `retraction_minimum_travel`
- `wipe`
- `wipe_distance`
- `retract_when_changing_layer`
- `retract_length_toolchange`
- `retract_restart_extra_toolchange`
- `extruder_colour`
- `default_filament_profile`

## 4. Dead whitelist entries (engine never defines them, handle_legacy can't save them)

Currently stored/serialized but ignored by the engine at load (EnableSilent). Any UI bound to these is a no-op. Remove, or remap where a value-compatible Orca equivalent exists:


### print (23)
- `avoid_crossing_curled_overhangs`
- `complete_objects`
- `enable_dynamic_overhang_speeds`
- `external_perimeters_first`
- `first_layer_acceleration_over_raft`
- `first_layer_speed_over_raft`
- `gcode_resolution`
- `gcode_substitutions`
- `infill_only_where_needed`
- `ironing`
- `max_print_speed`
- `max_volumetric_extrusion_rate_slope_negative`
- `max_volumetric_speed`
- `perimeter_acceleration`
- `solid_infill_every_layers`
- `support_material_auto`
- `support_material_closing_radius`
- `support_material_synchronize_layers`
- `support_material_with_sheath`
- `support_tree_branch_diameter_double_wall`
- `top_one_perimeter_type` (stored as `top_one_wall_type`)
- `wipe_tower_acceleration`
- `xy_size_compensation`

### filament (19)
- `chamber_minimal_temperature`
- `enable_dynamic_fan_speeds`
- `filament_infill_max_crossing_speed`
- `filament_infill_max_speed`
- `filament_load_time`
- `filament_purge_multiplier`
- `filament_retract_length_toolchange`
- `filament_retract_restart_extra_toolchange`
- `filament_shrinkage_compensation_xy`
- `filament_spool_weight`
- `filament_travel_lift_before_obstacle`
- `filament_travel_max_lift`
- `filament_travel_ramping_lift`
- `filament_travel_slope`
- `filament_unload_time`
- `overhang_fan_speed_0`
- `overhang_fan_speed_1`
- `overhang_fan_speed_2`
- `overhang_fan_speed_3`

### printer (15)
- `autoemit_temperature_commands`
- `between_objects_gcode`
- `binary_gcode`
- `color_change_gcode`
- `machine_limits_usage`
- `machine_max_feedrate_e`
- `machine_max_feedrate_x`
- `machine_max_feedrate_y`
- `machine_max_feedrate_z`
- `multimaterial_purging`
- `prefer_clockwise_movements`
- `printer_vendor`
- `remaining_times`
- `use_volumetric_e`
- `variable_layer_height`

## 5. Prioritized recommendations

1. **Fix import mapper duplication**: make `IOUtils.mapOrcaConfigKey()` delegate to
   `ConfigObject.legacyKey()` (KEY_MIGRATION reverse). Rescues 74 keys (61 print, 12 filament, 1 printer).
2. **Add extruder block to PRINTER_CONFIG_KEYS** (section 1) and migrate
   `machine_max_feedrate_*` → `machine_max_speed_*` via KEY_MIGRATION.
3. **High-value print keys to whitelist**: `wall_sequence`, `wall_direction`, `precise_outer_wall`,
   `alternate_extra_wall`, `seam_gap`, scarf-joint group (`seam_slope_*`, `scarf_*`),
   `support_type`, `support_expansion`, `support_critical_regions_only`, `support_remove_small_overhang`,
   `independent_support_layer_height`, organic-tree params (`tree_support_*_organic`),
   `make_overhang_printable*`, `hole_to_polyhole*`, per-feature flow ratios (`*_flow_ratio`),
   jerk group (`default_jerk`, `outer_wall_jerk`, ...), `slow_down_layers`, `skirt_type`, `skirt_speed`,
   `brim_ears_*`, `ironing_pattern`, `ironing_angle`, `ironing_inset`, fuzzy-skin extension group,
   `infill_shift_step`, `solid_infill_direction`, `internal_solid_infill_pattern`,
   `internal_bridge_speed`, `enable_extra_bridge_layer`, `precise_z_height`, `exclude_object`,
   `adaptive_layer_height`, `enable_overhang_speed`, `print_sequence`.
4. **High-value filament keys**: pressure advance group (`enable_pressure_advance`, `pressure_advance`,
   `adaptive_pressure_advance*`) — replaces the current start-gcode injection hack,
   `filament_shrink` + `filament_shrinkage_compensation_z`, bed-type temps
   (`cool_plate_temp*`, `textured_plate_temp*`, `eng_plate_temp*`, `bed_type`),
   `temperature_vitrification`, `overhang_fan_threshold`, `enable_overhang_bridge_fan`,
   `additional_cooling_fan_speed`, `internal_bridge_fan_speed`, `support_material_interface_fan_speed`,
   `ironing_fan_speed`, `nozzle_temperature_range_low/high`, `filament_wipe_distance`,
   `filament_z_hop_types`, `dont_slow_down_outer_wall`.
5. **High-value printer keys**: `machine_max_speed_*`, `bed_exclude_area`, `nozzle_type`,
   `nozzle_volume`, `nozzle_height`, `fan_kickstart`, `fan_speedup_time`, `fan_speedup_overhangs`,
   `manual_filament_change`, `time_lapse_gcode`, `change_extrusion_role_gcode`,
   `emit_machine_limits_to_gcode`, `disable_m73`, `preferred_orientation`, `best_object_pos`,
   `enable_long_retraction_when_cut`, `long_retractions_when_cut`, `retraction_distances_when_cut`,
   `printhost_port/user/password/authorization_type` (present in PHYSICAL list, absent from printer list).
6. **Clean dead entries** (section 4): notably `ironing` (Orca controls ironing purely via `ironing_type`;
   the import shim writes an `ironing` key the engine ignores), `external_perimeters_first`
   (superseded by `wall_sequence` enum), `max_volumetric_speed` (filament-scoped
   `filament_max_volumetric_speed` is already whitelisted), `complete_objects` (superseded by
   `print_sequence`), and the whole stale `filament_travel_*` / `enable_dynamic_*` Prusa groups.
