# OrcaSlicer Mobile — Feature Parity Audit

Audit of what the OrcaSlicer engine (now bundled via `app/src/main/jni/libslic3r`)
exposes vs. what the mobile UI actually surfaces. Goal: find features lost when the
project was ported from SliceBeam (Prusa engine) to the Orca engine.

## Method

- Engine options: `grep this->add("…")` in `app/src/main/jni/libslic3r/PrintConfig.cpp` → **862** config keys.
- UI-exposed options: every `def.options.get("…")` in the config fragments plus the
  section keys in `OrcaPrintSettingsBuilder` → **253** real engine keys wired into the UI.
- Model tools: enumerated from `components/bed_menu/*` (File / Camera / Orientation / Transform / Slice).

**Result: ~253 / 862 engine options are exposed.** After removing SLA/resin, CLI verbs,
runtime state, and preset-metadata keys, roughly **180–200 genuine user-facing FDM settings
are defined in the engine but not shown in the app**, plus several whole model-editing tools.

> The lists below are curated to the settings OrcaSlicer **desktop actually shows in its tabs**.
> Pure-internal keys (`e_position`, `layer_z`, `print_time`, `inherits`, `*_settings_id`, CLI
> verbs like `cut`/`arrange`/`slice`) are intentionally excluded.

---

## 1. Missing Print settings (Process tabs)

### Quality
- **Adaptive layer height** — `adaptive_layer_height` (big one; desktop has the height-range gizmo too)
- Precise wall / Z: `precise_outer_wall`, `precise_z_height`, `ensure_vertical_shell_thickness`
- Elephant-foot advanced: `elefant_foot_compensation_layers`, `elefant_foot_layers_density`, `elefant_foot_min_width`
- Hole/contour compensation: `xy_hole_compensation`, `xy_contour_compensation`, `hole_to_polyhole`, `hole_to_polyhole_threshold`, `hole_to_polyhole_twisted`
- Ironing advanced: `ironing_pattern`, `ironing_angle`, `ironing_inset`, `ironing_expansion`, `ironing_fan_speed`
- Fuzzy skin advanced: `fuzzy_skin_mode`, `fuzzy_skin_first_layer`, `fuzzy_skin_noise_type`, `fuzzy_skin_scale`, `fuzzy_skin_octaves`, `fuzzy_skin_persistence`, ripple family

### Seam (Orca scarf-joint seam is a headline feature — entirely missing)
- `seam_gap`, `wipe_speed`, `role_based_wipe_speed`
- Scarf joint: `has_scarf_joint_seam`, `seam_slope_type`, `seam_slope_start_height`, `seam_slope_entire_loop`,
  `seam_slope_min_length`, `seam_slope_steps`, `seam_slope_inner_walls`, `seam_slope_conditional`,
  `scarf_angle_threshold`, `scarf_overhang_threshold`, `scarf_joint_speed`, `scarf_joint_flow_ratio`

### Strength / Infill
- Per-feature flow ratios: `outer_wall_flow_ratio`, `inner_wall_flow_ratio`, `top_solid_infill_flow_ratio`,
  `bottom_solid_infill_flow_ratio`, `internal_solid_infill_flow_ratio`, `sparse_infill_flow_ratio`,
  `gap_fill_flow_ratio`, `first_layer_flow_ratio`, `bridge_density`
- Internal bridges: `internal_bridge_density`, `internal_bridge_angle`, `internal_bridge_speed`,
  `internal_bridge_flow`, `internal_bridge_fan_speed`, `thick_internal_bridges`, `dont_filter_internal_bridges`, `enable_extra_bridge_layer`, `counterbore_hole_bridging`
- Infill advanced: `internal_solid_infill_pattern`, `solid_infill_direction`, `infill_combination_max_layer_height`,
  `detect_narrow_internal_solid_infill`, `min_width_top_surface`, `top_surface_density`, `bottom_surface_density`,
  `gyroid_optimized`, `lightning_overhang_angle`, `infill_lock_depth`, locked/skin/skeleton infill family
- Walls: `wall_sequence`, `wall_direction`, `overhang_reverse` (+`_threshold`,`_internal_only`), `only_one_wall_top`

### Speed / Motion (large gap — mostly missing)
- Jerk: `default_jerk`, `outer_wall_jerk`, `inner_wall_jerk`, `infill_jerk`, `top_surface_jerk`,
  `travel_jerk`, `initial_layer_jerk`, `initial_layer_travel_jerk`
- Junction deviation: `default_junction_deviation`
- Accel: `accel_to_decel_enable`, `accel_to_decel_factor`
- First layer: `initial_layer_travel_speed`, `initial_layer_infill_speed`
- Overhang speed system: `enable_overhang_speed`, `slowdown_for_curled_perimeters`
- Resonance avoidance (Orca): `resonance_avoidance`, `max_resonance_avoidance_speed`, `min_resonance_avoidance_speed`
- Pressure advance: `enable_pressure_advance`, `pressure_advance`,
  `adaptive_pressure_advance`(+`_model`,`_overhangs`,`_bridges`)

### Support
- Tree (organic): `tree_support_branch_angle_organic`, `tree_support_branch_diameter_organic`,
  `tree_support_branch_distance_organic`, `tree_support_wall_count`, `tree_support_with_infill`, `tree_support_auto_brim`
- Support ironing: `support_ironing`, `support_ironing_flow`, `support_ironing_pattern`, `support_ironing_spacing`
- Misc: `support_critical_regions_only`, `support_remove_small_overhang`, `support_interface_not_for_body`,
  `independent_support_layer_height`, `support_object_first_layer_gap`

### Brim / Skirt
- **Brim ears** (painted brim): `brim_ears`, `brim_ears_max_angle`, `brim_ears_detection_length`, `brim_use_efc_outline`
- `combine_brims`, `brim_flow_ratio`, `single_loop_draft_shield`, `skirt_speed`, `skirt_start_angle`, `skirt_type`

### Others / Special
- **Timelapse**: `enable_timelapse`, `timelapse_type`
- **Power-loss recovery**: `enable_power_loss_recovery`
- **Object skipping / labeling**: `exclude_object`, `gcode_add_line_number`, `disable_m73`, `scan_first_layer`, `spaghetti_detector`
- Sequential print first-layer sequencing: `first_layer_sequence_choice`, `other_layers_sequence_choice`

---

## 2. Missing Filament settings
- **Bed-type temps** (Orca per-plate temps): `cool_plate_temp`(+initial), `eng_plate_temp`(+initial),
  `textured_plate_temp`(+initial), `supertack_plate_temp`(+initial) — only `hot_plate_temp` is exposed today
- Pressure advance per filament: `enable_pressure_advance`, `pressure_advance`
- Cooling advanced: `additional_cooling_fan_speed`, `fan_kickstart`, `fan_speedup_time`, `fan_speedup_overhangs`,
  `close_additional_fan_first_x_layers`, `first_x_layer_fan_speed`, `internal_bridge_fan_speed`
- Air filtration: `activate_air_filtration`(+during/on-completion), `complete_print_exhaust_fan_speed`, `during_print_exhaust_fan_speed`
- Chamber control: `activate_chamber_temp_control`, `chamber_temperature` (exposed) vs `support_chamber_temp_control`
- Filament physical: `temperature_vitrification`, `nozzle_temperature_range_low`/`_high`, `filament_shrink`,
  `filament_change_length`, `high_current_on_filament_swap`, `filament_adaptive_volumetric_speed`
- Filament ironing overrides: `filament_ironing_flow/inset/spacing/speed`

---

## 3. Missing Printer settings
- **Machine motion limits** (whole group): `machine_max_speed_*`, `machine_max_acceleration_*`,
  `machine_max_jerk_*`, `machine_max_junction_deviation`, `machine_min_extruding_rate`, `machine_min_travel_rate`
- Multi-extruder / multi-tool: `extruder_type`, `nozzle_volume`, `nozzle_volume_type`, `extruder_printable_area`,
  `extruder_printable_height`, `extruder_ams_count`, `master_extruder_id`, `physical_extruder_map`
- Z-hop: `z_hop_types`, `retract_lift_enforce`, `long_retractions_when_cut`, `retraction_distances_when_cut`
- `nozzle_hrc`, `nozzle_type`, `printer_structure`, `bed_mesh_*`, `adaptive_bed_mesh_margin`, `preheat_steps`

---

## 4. Missing model-editing tools (gizmos)

Current tools: **Move, Rotate, Scale, Mirror, Auto-orient, Lay-on-face (Flatten), Arrange**, plus
bed/object context actions (duplicate, fill-bed, center, delete, select-all).

Missing vs OrcaSlicer desktop:

| Tool | Status | Notes |
|------|--------|-------|
| **Cut** (plane, dowels/connectors, keep parts) | ❌ | Task #6 |
| **Color / MMU painting** | ❌ | Task #7 — engine has the data path (`mmu_segmented_region_*`, filament-per-feature, flush volumes) |
| **Support painting** (enforcers/blockers) | ❌ | Orca FacetsAnnotation; pairs with support settings |
| **Seam painting** | ❌ | complements the missing scarf-seam settings |
| **Fuzzy-skin painting** | ❌ | Orca paints fuzzy regions |
| **Text / Emboss** | ❌ | desktop text gizmo |
| **SVG import / emboss** | ❌ | desktop SVG gizmo |
| **Measure** | ❌ | distance/angle measuring |
| **Negative / Modifier / Support-blocker volumes** | ❌ | per-part meshes with setting overrides |
| **Per-object / per-part setting overrides** | ❌ | right-click "Add settings" in desktop |
| **Variable / adaptive layer height** (height-range gizmo) | ❌ | ties to `adaptive_layer_height` |
| **Split to objects / parts** | ❌ | `split` exists in engine as a verb |
| **Simplify mesh / mesh boolean** | ❌ | desktop mesh ops |

---

## 5. Prioritized recommendations

1. **Speed/Motion + Jerk/Junction + Pressure Advance** — biggest single settings gap; pure UI work
   (engine already supports them). High user impact, low risk.
2. **Per-feature flow ratios + internal bridge settings** — UI work only.
3. **Bed-type temperatures (filament)** — Orca printers expect these; currently only `hot_plate_temp`.
4. **Scarf-joint seam + seam painting** — marquee Orca quality feature.
5. **Brim ears, ironing-advanced, fuzzy-skin-advanced** — UI work only.
6. **Cut tool** (Task #6) and **Color painting** (Task #7) — large, need native + gizmo work.
7. **Machine motion limits** in Printer tab — needs the array/matrix editor widget.

### Quick wins (engine-ready, UI-only)
Adding these keys to `OrcaPrintSettingsBuilder` / `FilamentConfigFragment` / `PrinterConfigFragment`
immediately surfaces them — they already slice correctly:
`default_jerk`, `outer_wall_jerk`, `inner_wall_jerk`, `infill_jerk`, `travel_jerk`,
`enable_pressure_advance`, `pressure_advance`, `accel_to_decel_enable`, `accel_to_decel_factor`,
`initial_layer_travel_speed`, all `*_flow_ratio`, `internal_bridge_*`, `seam_gap`, `brim_ears*`,
`cool_plate_temp`/`textured_plate_temp`/`eng_plate_temp` (+initial), `enable_timelapse`,
`exclude_object`, `adaptive_layer_height`.
