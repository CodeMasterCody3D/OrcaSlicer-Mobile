# Handoff: Migrate Print-Settings option keys (PrusaSlicer → OrcaSlicer)

## Context
This app (`OrcaSlicerMobile`, branch `engine-swap`) swapped its native slicing engine from
PrusaSlicer-era `libslic3r` to the OrcaSlicer/BBS engine. The Java UI and the bundled profiles
still use **PrusaSlicer option key names**, but the engine now uses **OrcaSlicer names**. A previous
pass already migrated the Filament/Printer/G-code/Retraction keys. This task is the remaining big
one: the **Print (process) settings**.

## Root cause (confirmed)
`app/src/main/java/ru/ytkab0bp/slicebeam/fragment/PrintConfigFragment.java` builds its UI via
`OrcaPrintSettingsBuilder.build(...)`. That builder
(`app/src/main/java/ru/ytkab0bp/slicebeam/fragment/OrcaPrintSettingsBuilder.java`) lists ~195 option
keys, but **114 of them use PrusaSlicer names that don't exist in the OrcaSlicer engine's
`PrintConfigDef`**. The builder's `addOption()` silently skips any key whose def is null:

```java
private void addOption(List<...> items, String key) {
    ConfigOptionDef opt = def.options.get(key);
    if (opt != null) { items.add(fragment.new OptionElement(opt)); }   // null -> row dropped
}
```

So e.g. wall count (`perimeters`), infill (`fill_density`), supports (`support_material`), most
speeds, etc. never appear in the UI. It is **NOT** an "advanced mode" issue — there is no mode
filter in this app.

Additionally, the bundled print profiles in `app/src/main/assets/orca_profiles/*.ini` store **values
under the old names** (e.g. 924 files have `perimeters = ...`). So renaming only the UI is not
enough — the values must line up too (see "Two places to change").

## Data flow (why two places must change)
- The UI reads/writes a setting's value by `def.key` (the def's OrcaSlicer key) against the in-memory
  `ConfigObject` (`opt()` → `getCurrentConfig().get(def.key)`).
- `ConfigObject` is built by parsing the profiles (old keys). To make UI key == stored key, the app
  normalizes keys at the `ConfigObject` boundary via a central map `ConfigObject.KEY_MIGRATION`
  (legacy → OrcaSlicer), applied in `get/has/put/remove`. There is also a reverse map used by
  `Slic3rConfigWrapper` to keep profile categorization working.

So **every key you migrate must be changed in BOTH:**
1. `OrcaPrintSettingsBuilder.java` — rename the string literal to the OrcaSlicer key.
2. `ConfigObject.java` → `buildKeyMigration()` — add `m.put("<oldKey>", "<orcaKey>");` so stored
   values (old key) resolve to the new key. (The reverse map is auto-derived; do not edit it.)

## The verification method (CRITICAL — do not trust a mapping blind)
The source of truth is the engine's `PrintConfigDef`. For each candidate OrcaSlicer target key,
confirm it is actually a registered option (not a comment, a legacy alias source, or a g-code
placeholder var):

```bash
cd app/src/main/jni
# A real option is registered with this->add("KEY", ...) inside the PrintConfigDef init functions
grep -n 'this->add("KEY"' libslic3r/PrintConfig.cpp
```

Notes/gotchas when verifying:
- Options in classes named `*SlicingStatesConfigDef`, `TemperaturesConfigDef`,
  `PrintStatisticsConfigDef`, etc. (roughly lines 10572+ of PrintConfig.cpp) are **g-code placeholder
  variables, NOT user options** — they look valid but are not in the editable `PrintConfigDef`. Ignore
  them. The real options are registered in `init_common_params()` / `init_fff_params()` (~lines
  663–7372) and a few axis loops / override-key lists.
- Some families are registered via loops/lists, not literal `add("...")` — e.g.
  `machine_max_acceleration_{x,y,z,e}` (axis loop) and the filament override list. Grep broadly if a
  literal grep fails.
- The engine's `PrintConfigDef::handle_legacy()` in `libslic3r/PrintConfig.cpp` is a partial
  PrusaSlicer→Orca rename table and a good cross-check for several of these.

Optional but most reliable: dump the live key set. Temporarily add, inside
`Java_..._get_1print_1config_1def` (in `slicebeam/beam_native.cpp`), a loop logging `nDef.keys()` to
a **file** (NOT logcat — logcat drops messages under load), launch the app, pull the file, diff. Then
remove the temp code.

## Beware value-semantics mismatches (these are NOT plain renames)
Some PrusaSlicer keys map to an Orca key whose **value format/enum differs**. Renaming the key alone
will make the value fail to deserialize (silently empty / wrong). For these, either add a value
transform in `ConfigObject.normalizeSerializedValue()` / handle in the engine's `handle_legacy()`, or
drop them for now. Known tricky ones:
- `support_material_auto` → support is an enum `support_type` in Orca (`normal(auto)`/`tree(auto)`).
- `complete_objects` (bool) → `print_sequence` (enum `by layer`/`by object`).
- `ironing` (bool enable) → `ironing_type` (enum).
- `external_perimeters_first` (bool) → `wall_sequence` (enum) — handle_legacy already covers
  `wall_infill_order`; check.
- `spiral_vase` → `spiral_mode` (bool→bool, this one is a clean rename).
- `xy_size_compensation` → splits into `xy_contour_compensation` + `xy_hole_compensation`.

Keys with **no OrcaSlicer equivalent** → just remove them from the builder list (don't add to the
migration map). Candidates: `x_size_compensation`, `y_size_compensation`, `gcode_resolution`,
`max_print_speed`, `solid_infill_every_layers`, `first_layer_speed_over_raft`,
`first_layer_acceleration_over_raft`, `support_material_synchronize_layers`,
`support_material_with_sheath`, `support_material_closing_radius`, `output_filename_format`
(verify each — some may exist).

## Proposed mapping table (VERIFY every target before applying)
Format: `prusaslicer_key -> orcaslicer_key`. Grouped. Confidence is "high" unless noted. Verify each.

### Walls (perimeter → wall)
```
perimeters                         -> wall_loops
perimeter_speed                    -> inner_wall_speed
external_perimeter_speed           -> outer_wall_speed
perimeter_extruder                 -> wall_filament
perimeter_extrusion_width          -> inner_wall_line_width
external_perimeter_extrusion_width -> outer_wall_line_width
perimeter_generator                -> wall_generator
external_perimeter_acceleration    -> outer_wall_acceleration
thin_walls                         -> detect_thin_wall
top_one_perimeter_type             -> top_one_wall_type
only_one_perimeter_first_layer     -> only_one_wall_first_layer
extra_perimeters                   -> extra_perimeters_on_overhangs   (verify; may differ)
avoid_crossing_perimeters          -> reduce_crossing_wall
avoid_crossing_perimeters_max_detour -> max_travel_detour_distance
only_retract_when_crossing_perimeters -> reduce_infill_retraction      (verify semantics)
external_perimeters_first          -> wall_sequence                    (ENUM — see gotchas)
```

### Infill (fill_/infill_/solid_infill_ → sparse_infill_/internal_solid_infill_)
```
fill_density                  -> sparse_infill_density
fill_pattern                  -> sparse_infill_pattern
fill_angle                    -> infill_direction
infill_speed                  -> sparse_infill_speed
infill_extruder               -> sparse_infill_filament
infill_extrusion_width        -> sparse_infill_line_width
infill_acceleration           -> sparse_infill_acceleration
infill_overlap                -> infill_wall_overlap
infill_every_layers           -> infill_combination
infill_first                  -> is_infill_first
solid_infill_speed            -> internal_solid_infill_speed
solid_infill_extruder         -> solid_infill_filament
solid_infill_extrusion_width  -> internal_solid_infill_line_width
solid_infill_acceleration     -> internal_solid_infill_acceleration
solid_infill_below_area       -> minimum_sparse_infill_area            (verify; may differ)
top_solid_infill_speed        -> top_surface_speed
top_solid_infill_acceleration -> top_surface_acceleration
top_infill_extrusion_width    -> top_surface_line_width
bridge_flow_ratio             -> bridge_flow
gap_fill_speed                -> gap_infill_speed
gap_fill_enabled              -> gap_fill_target                       (verify; enum/bool)
```

### Top/bottom shells
```
top_solid_layers         -> top_shell_layers
bottom_solid_layers      -> bottom_shell_layers
top_solid_min_thickness  -> top_shell_thickness
bottom_solid_min_thickness -> bottom_shell_thickness
top_fill_pattern         -> top_surface_pattern
bottom_fill_pattern      -> bottom_surface_pattern
```

### Quality / precision / line width
```
extrusion_width             -> line_width
first_layer_extrusion_width -> initial_layer_line_width
first_layer_height          -> initial_layer_print_height
arc_fitting                 -> enable_arc_fitting
overhangs                   -> detect_overhang_wall
ironing                     -> ironing_type            (ENUM — see gotchas)
ironing_flowrate            -> ironing_flow
spiral_vase                 -> spiral_mode
xy_size_compensation        -> xy_contour_compensation (also add xy_hole_compensation; see gotchas)
```

### Speed / acceleration
```
support_material_speed           -> support_speed
support_material_interface_speed -> support_interface_speed
first_layer_speed                -> initial_layer_speed
first_layer_acceleration         -> initial_layer_acceleration
overhang_speed_0                 -> overhang_1_4_speed
overhang_speed_1                 -> overhang_2_4_speed
overhang_speed_2                 -> overhang_3_4_speed
overhang_speed_3                 -> overhang_4_4_speed
max_volumetric_extrusion_rate_slope_positive -> max_volumetric_extrusion_rate_slope  (verify; Orca has single slope + segment_length)
max_volumetric_extrusion_rate_slope_negative -> (likely no equivalent; drop)
```

### Supports (support_material* → support_* / enable_support)
```
support_material                          -> enable_support
support_material_threshold                -> support_threshold_angle
support_material_enforce_layers           -> enforce_support_layers
support_material_style                    -> support_style
support_material_pattern                  -> support_base_pattern
support_material_spacing                  -> support_base_pattern_spacing
support_material_angle                    -> support_angle
support_material_contact_distance         -> support_top_z_distance
support_material_bottom_contact_distance  -> support_bottom_z_distance
support_material_buildplate_only          -> support_on_build_plate_only
support_material_xy_spacing               -> support_object_xy_distance
support_material_extruder                 -> support_filament
support_material_interface_extruder       -> support_interface_filament
support_material_interface_layers         -> support_interface_top_layers
support_material_bottom_interface_layers  -> support_interface_bottom_layers
support_material_interface_pattern        -> support_interface_pattern
support_material_interface_spacing        -> support_interface_spacing
support_material_interface_contact_loops  -> support_interface_loop_pattern
support_material_extrusion_width          -> support_line_width
dont_support_bridges                      -> bridge_no_support
support_material_auto                     -> support_type   (ENUM — see gotchas)
support_material_with_sheath              -> (verify / likely drop)
support_material_closing_radius           -> (verify / likely drop)
support_material_synchronize_layers       -> (verify / likely drop)
```

### Tree supports (support_tree_* → tree_support_*)
```
support_tree_angle                      -> tree_support_branch_angle
support_tree_angle_slow                  -> tree_support_angle_slow            (verify)
support_tree_branch_diameter             -> tree_support_branch_diameter
support_tree_branch_diameter_angle       -> tree_support_branch_diameter_angle (verify)
support_tree_branch_diameter_double_wall -> tree_support_branch_diameter_double_wall (verify)
support_tree_branch_distance             -> tree_support_branch_distance
support_tree_tip_diameter                -> tree_support_tip_diameter
support_tree_top_rate                    -> tree_support_top_rate
```

### Skirt / brim / multimaterial / others
```
skirts                  -> skirt_loops
brim_separation         -> brim_object_gap
wipe_tower              -> enable_prime_tower
wipe_tower_width        -> prime_tower_width
wipe_tower_brim_width   -> prime_tower_brim_width
wipe_tower_extruder     -> wipe_tower_filament
wipe_tower_acceleration -> (verify / likely drop)
complete_objects        -> print_sequence            (ENUM — see gotchas)
extruder_clearance_height -> extruder_clearance_height_to_rod  (verify; Orca also has _to_lid)
fuzzy_skin_point_dist   -> fuzzy_skin_point_distance
output_filename_format  -> filename_format            (verify)
gcode_resolution        -> (verify / likely drop)
max_print_speed         -> (verify / likely drop)
x_size_compensation     -> (likely drop)
y_size_compensation     -> (likely drop)
solid_infill_every_layers -> (verify / likely drop)
first_layer_speed_over_raft -> (verify / likely drop)
first_layer_acceleration_over_raft -> (verify / likely drop)
avoid_crossing_curled_overhangs -> (verify)
```

## Step-by-step recipe
1. For each pair above, run the verification grep. Keep pairs whose target is a real `add(...)`
   option; move unverified ones to "drop".
2. In `OrcaPrintSettingsBuilder.java`, replace each old key string literal with its OrcaSlicer key.
   Remove keys with no equivalent from the section lists. (Tip: do it per `addSection(...)` line.)
3. In `ConfigObject.java` `buildKeyMigration()`, add `m.put("<old>", "<new>");` for each verified
   rename (so stored profile values resolve). Do NOT add the "drop" keys.
4. For the ENUM/value-mismatch keys (gotchas), either:
   - add a value transform in `ConfigObject.normalizeSerializedValue(key, value)`, or
   - add the rename+value handling in the engine `PrintConfigDef::handle_legacy()`
     (`app/src/main/jni/libslic3r/PrintConfig.cpp`), or
   - leave them dropped for v1.
5. Build, install, verify:
   ```bash
   ./scripts/build-debug.sh
   # APK: app/build/outputs/apk/debug/OrcaSlicerMobile_*.apk
   adb install -r <apk>
   ```
   Open the **Print** tab → expand **Strength → Walls**: "Wall loops" should appear with a value;
   check Infill, Top/bottom shells, Supports, Speed sections populate with values.

## Reference: example of the existing pattern to copy
The Filament/Printer migration already in the tree is the template:
- `ConfigObject.java` → `KEY_MIGRATION` map + `migrateKey()`/`legacyKey()` and `get/has/put/remove`
  normalization.
- `Slic3rConfigWrapper.java` → categorization uses `ConfigObject.legacyKey(key)` so both old and new
  keys are bucketed.
- The fragments use `def.options.get("<orcaKey>")`.

Mirror that exactly for the print keys.

## Other engine fixes already applied this session (context, do not redo)
- `Config.cpp` `ConfigBase::load()`: re-enabled `load_from_ini()` for non-JSON files (OrcaSlicer had
  it commented out) — required for bed config + slicing to read SliceBeam's flat .ini.
- `GCodeProcessor.cpp` `update_slice_warnings()`: bounds-guarded empty `m_filament_maps` (was a
  null-deref crash on slice for single-extruder configs).
- `Config.hpp`: null-guarded `ConfigOptionEnum(s)Generic::serialize` for null `keys_map`.
- `PrintConfig.cpp` `handle_legacy()`: added `bed_shape`→`printable_area`,
  `max_print_height`→`printable_height`.
- Bed config option names migrated in `beam_native.cpp` (`bed_configure`).
```
