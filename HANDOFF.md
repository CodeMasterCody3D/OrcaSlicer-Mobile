# Handoff Report: OrcaSlicerMobile Multi-Color Fix & Pressure Advance Next Steps

## 1. Project Context
This is an Android port of OrcaSlicer. The core engine is C++ (libslic3r) bridged via JNI in `app/src/main/jni/slicebeam/beam_native.cpp`.

## 2. Recent Accomplishments (Multi-Color Slicing)
We have successfully fixed multi-color painting and slicing. The following issues were resolved:
- **Missing Tool Changes**: Fixed by ensuring every filament has a unique `filament_self_index` (1..N) and `filament_settings_id`.
- **Wall Color Bug**: Fixed unpainted walls by:
    1.  Updating `paint_height` to test all three vertices of a triangle (preventing large hull triangles from being skipped).
    2.  Implementing `paint_commit` to force volumes with paint to `extruder = 0` (Auto), which tells the engine to respect facet-based painting for perimeters.
- **Stability**: Fixed a `SIGSEGV` by ensuring printer-level configuration vectors (like `nozzle_diameter`) are NOT resized to the filament count (they must match physical extruder count).
- **G-Code Flavor**: Forced `is_BBL_printer = false` to ensure standard `T0`, `T1` commands are emitted instead of Bambu-specific codes.

## 3. Current State
- **Painting**: Brush, Bucket, and Height Range tools are functional.
- **Slicing**: Produces G-code with `T` commands and a Prime Tower.
- **Preview**: Model walls now correctly reflect the painted colors.

## 4. Next Task: Pressure Advance (PA) Calibration/Settings
The user needs to be able to configure the **Pressure Advance (PA)** range. 
- Currently, the G-code shows `SET_PRESSURE_ADVANCE ADVANCE=0`.
- The next step is to allow the user to change the PA value or define a range (likely for a calibration test).

### Files of Interest:
- `app/src/main/jni/slicebeam/beam_native.cpp`: Contains the `model_slice` JNI method where configuration is manipulated before slicing.
- `app/src/main/java/ru/ytkab0bp/slicebeam/slic3r/Native.java`: JNI declarations.
- `app/src/main/assets/orca_profiles/`: Base configurations.

## 5. Implementation Notes for Next AI
The `Pressure Advance` setting in Slic3r is typically `pressure_advance` (a vector for filaments). 
You may need to:
1.  Locate where the calibration logic (if any) is triggered.
2.  Ensure `enable_pressure_advance` is set to `true`.
3.  Inject the desired `pressure_advance` values into the `DynamicPrintConfig` in `model_slice` or a dedicated calibration JNI method.
4.  The user specifically mentioned "pa range", so this might involve the **Pressure Advance Tower** calibration feature.

## 6. Key Code Snippet (Current `model_slice` multi-color block)
```cpp
// ... inside model_slice ...
                // filament_map must map all virtual filaments back to the physical extruder (1).
                {
                    std::vector<int> filamentMap(numFilaments, 1);
                    config.set_key_value("filament_map", new ConfigOptionInts(filamentMap));
                }

                const ConfigOption* nd = config.option("nozzle_diameter");
                if (nd && static_cast<const ConfigOptionFloats*>(nd)->values.size() <= 1) {
                    config.set_key_value("single_extruder_multi_material", new ConfigOptionBool(true));
                    config.set_key_value("single_extruder_multi_material_priming", new ConfigOptionBool(true));
                }

                // Enable a prime/wipe tower for reliable filament changes.
                config.set_key_value("enable_prime_tower", new ConfigOptionBool(true));
```
