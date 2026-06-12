# Gemini Handoff — OrcaSlicer Mobile: Toolbar tools (2026-06-12)

Repo: `/home/cody/workspace/OrcaSlicerMobileTestHandOffToGemini` (branch `master`, plain `git push` works)
Phone: adb `R5CR60DKJGB`. User runs on-device tests himself; he's new to git — run git for him, plain language.

Env + build:
```bash
export JAVA_HOME=/home/cody/.sdkman/candidates/java/17.0.11-tem
export ANDROID_HOME=/home/cody/android-sdk; export ANDROID_SDK_ROOT=/home/cody/android-sdk
export PATH=/home/cody/android-sdk/platform-tools:$PATH
./gradlew assembleDebug   # native (jni/**) changes recompile via :app:buildCMakeRelease[arm64-v8a] ~35s
adb -s R5CR60DKJGB install -r "$(ls -t app/build/outputs/apk/debug/*.apk | head -1)"
```
APK filename embeds the LAST COMMIT hash — it does not change until you commit. Native lib = `libslic3r.so` (beam_native.cpp compiles into it). Crash logs: `adb logcat -b crash -d`. The slice config INI the app feeds the engine: `adb exec-out run-as com.codemastercody3d.orcaslicermobile cat files/slic3r_current.ini` — pulling this is how the last crash was solved; prefer it over guessing.

## Where things stand

Committed + released through **v0.4.3** (`e9c0a20`): Toolbar tab (renamed from Transform) hosting all 16 desktop-style tools; Plates moved in; Orientation tab removed; Move/Rotate wired via `new OrientationMenu().new PositionMenu()/.new RotationMenu()` (they're self-contained UnfoldMenus).

**UNCOMMITTED working tree** (this session's tool work — commit once user confirms seam/fuzzy verification):
1. **Split to objects** — native `model_split` (beam_native.cpp, uses `ModelObject::split(&new_objects, true)`, deletes original, returns piece count; 0 = nothing to split with appended-object rollback). Java: `Model.split()`, `BedFragment.splitSelectedObject()` (GL thread: split → deselect → `bed.arrange(model)` → `resetGlModels`), Toolbar button. **User-verified working.**
2. **Support / Seam / Fuzzy-skin painting** — the color-paint pipeline made mode-aware:
   - `PaintSessionRef.mode` (0 color, 1 support, 2 seam, 3 fuzzy); `facets_for_mode()` picks `mmu_segmentation_facets` / `supported_facets` / `seam_facets` / `fuzzy_skin_facets`; `paint_begin(model, obj, mode)` deserializes that channel; `paint_commit` writes it back (extruder=0 only in color mode). Brush/bucket/height functions untouched — EnforcerBlockerType ENFORCER=1/BLOCKER=2 are the same values as filament states 1/2, so UI just paints with "filament" 1/2/0.
   - Java: `PaintSession(model, obj, mode)`, `GLRenderer.PAINT_MODE_*` + `beginPaint(obj, mode)` + `paintStateColor()` (enforce=green, block=red, fuzzy=blue), `rebuildPaintOverlays` uses 2 states in non-color modes; `BedFragment.enterPaintMode(obj, mode)`; `PaintModeView(ctx, glView, onExit, mode)` shows Enforce/Block/Erase (Fuzzy/Erase for fuzzy) instead of palette; `TransformMenu.paintButton()`.
   - Known polish gap: outside paint mode the main view only re-displays COLOR paint; support/seam/fuzzy marks reappear when re-entering that paint tool (data is committed and used by the slicer regardless).
3. **Wipe-tower SIGSEGV fix (two prongs)** — crash was `Print::_make_wipe_tower → ToolOrdering::reorder_extruders_for_minimum_flush_volume → static calc_filament_change_info_by_toolorder` OOB. ROOT CAUSE: the imported Bambu project config declares 8 filaments (`filament_colour/diameter/type` ×8, 8×8 flush matrix, prime tower on) but carries 1-entry `filament_map`/`filament_self_index`/`filament_is_support` — and the app's multicolor fixups only ran when the APP requested multicolor (paint+palette), not when the CONFIG itself is multi-filament (fuzzy-only paint job → numFilaments=1 → no fixups → engine OOB).
   - Prong A (beam_native.cpp `model_slice`): before the `numFilaments > 1` block, `numFilaments = max(numFilaments, config filament_colour.size(), filament_diameter.size())` → existing consistency fixups (self_index 1..N, map all-1, cloneToN resize of every `filament_*` vector, flush sizing, SEMM) now run for config-declared multi-filament too. Logs `"config declares N filaments ... normalizing"`.
   - Prong B (libslic3r/GCode/ToolOrdering.cpp): bounds guards in `calc_filament_change_info_by_toolorder` (empty map/matrix bail + clamped indexing), `reorder_extruders_for_minimum_flush_volume` (N==0 return; matrix-size check before slicing rows else prime_volume fallback; bail if any used filament id ≥ N; `filament_maps.resize(N,1)` after `get_filament_maps()`), `get_recommended_filament_maps` (pad undersized matrix to N², 280 default).
   - Also still present from earlier attempts (harmless belt-and-braces): unconditional flush matrix/multiplier/vector normalization keyed on `filament_colour.size()>1` in model_slice.
   - Status: user sliced after the fix without reporting a crash (he moved on to inspecting seams) — treat as likely fixed, confirm once more.

## IMMEDIATE TASK: verify seam painting + make seams visible

User: "seams dont show in print info so i cant tell if paint on seams are actually working."
- The engine's SeamPlacer DOES consume `seam_facets` (enforcer pulls seam there, blocker repels). The G-code itself is the proof: seam = start point of each outer wall loop.
- Mobile's G-code preview is libvgcode, same as desktop. Desktop shows seams as dots via the **Seams option toggle — libvgcode `EOptionType::Seams`**. Mobile likely never exposes that toggle. TODO: add a "Show seams" toggle to the preview/Print info UI → `vgcode_*` JNI (see existing `vgcode_toggle_extrusion_role_visibility` etc. in beam_native.cpp; check libvgcode ViewerImpl for `toggle_option_visibility`/`is_option_visible` with `EOptionType::Seams`) and surface it next to the existing view-type/role toggles (GCodeViewer.java / Print info menu in SliceMenu.java or wherever role toggles live).
- Cheaper interim verification: pull the G-code (`adb exec-out run-as com.codemastercody3d.orcaslicermobile cat cache/temp.gcode > /tmp/t.gcode`) and check outer-wall loop start points cluster at the painted spot vs. scattered without paint. Or open the same G-code in desktop OrcaSlicer with its Seams toggle.

## After that
- Commit everything: suggested message "Add split tool, support/seam/fuzzy painting, fix multi-filament config wipe-tower crash". Then a release (v0.4.4) if user wants.
- Remaining Toolbar tools (3 of 16): **Measure** (smallest; AABBMesh raycast infra exists — see paint_raycast), **Variable layer height** (`ModelObject::layer_height_profile` exists in engine), **Emboss** (biggest; text→mesh/fonts).
- Untracked junk safe to delete: `BedFragment.java.orig/.rej`, `patch.diff`, `logcat_filtered*.txt`.

## Hard-won gotchas
- `Model.translate(i,...)` is a RELATIVE volume translate; `getTranslation` actually returns bbox center.
- Paint sessions/overlays are GL-thread only; overlays rebuild on model load (committed overlays cache keyed per object, `invalidateCommittedOverlays`).
- `paint_commit` writes volumes[0] only (multi-volume in-app painting limitation, pre-existing).
- Multi-volume LOADED paint (Bambu assemblies) is handled by `accumulate_paint_facets` in beam_native.cpp (all model-part volumes, `inst_m * vol_m` transforms).
- Project 3MF import adopts the file's filament palette (`MainActivity.applyProjectFilamentColors`) — that's why the user's palette is 8 colors.
- `print.is_BBL_printer() = false` is forced in model_slice (keeps T0/T1 tool changes for Klipper/Marlin).
