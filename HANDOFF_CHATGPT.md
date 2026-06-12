# OrcaSlicer Mobile — Session Handoff (2026-06-11, evening)

For the next AI. Read fully before touching anything.

## Project context
- **Repo**: `/home/cody/workspace/OrcaSlicerMobileTestHandOffToGemini` (CORRECT folder; ignore `OrcaSlicerMobileTest`)
- Android slicer (SliceBeam shell, pkg `ru.ytkab0bp.slicebeam`, appId `com.codemastercody3d.orcaslicermobile`) + real OrcaSlicer libslic3r via JNI.
- **GitHub**: https://github.com/CodeMasterCody3D/OrcaSlicer-Mobile — branches cleaned up: **single `master` everywhere now**, plain `git push` works (no more `engine-swap:master`). Latest release **v0.4.0**.
- **Build**: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/OrcaSlicerMobile_<hash>.apk`. APK name embeds git hash, so it does NOT change until you commit.
- **Native builds from source by default** (`usePrebuiltNative=false`). Changing `app/src/main/jni/**` triggers `:app:buildCMakeRelease[arm64-v8a]` (~35–42s). Java-only changes are fast (native cached). The lib is `libslic3r.so` (beam_native.cpp compiles INTO the `slic3r` CMake target, NOT a separate libbeam_native.so).
- Verify a native symbol made it in: `unzip -o -q <apk> lib/arm64-v8a/libslic3r.so -d /tmp/x && tr -c '[:print:]' '\n' </tmp/x/lib/arm64-v8a/libslic3r.so | grep model_1paint_1max_1filament`
- **Phone**: adb `R5CR60DKJGB`. Install `adb -s R5CR60DKJGB install -r <apk>`. User runs on-device tests himself — give steps. New to git — run it for him, explain plainly. Screenshot: `adb -s R5CR60DKJGB exec-out screencap -p > /tmp/x.png` then Read it.
- Test file (the painted assembly): `/home/cody/Downloads/RunescapeRunearmorPainted+BambuLab+painted+file.3mf`. Unzipped copy at `/tmp/runescape/`.

## DONE & COMMITTED+PUSHED this session
1. Multi-plate 3MF: commit `29e89f9` (load + switch). Then **multi-plate GRID rendering**, commit `6f2d363` (pushed, in v0.4.0):
   - `GLRenderer.java`: renders ALL plates at once. Active plate = full color/editable; inactive = dimmed, no gizmos. New `setInactivePlates(models, offsets)`; draws extra beds + their models at grid offsets.
   - `BedFragment.java`: eager-loads every plate; lays them in desktop's grid. **Plate grid math (from upstream OrcaSlicer PartPlate.cpp): stride = bedSize × 1.2** (LOGICAL_PART_PLATE_GAP=1/5); cols = `round(sqrt(count))` bumped up if `sqrt>round`; col→+X, row→−Y. CRITICAL: a 3MF's plate offsets use the **bed size the FILE was saved for** (read from `Metadata/project_settings.config` → `printable_area`), NOT the app's bed. `readProjectBedSize()` does this; each plate's objects are translated back to bed-local on load. (Earlier bug: used app bed 300 vs file bed 218 → models off-plate.)
2. Branch cleanup + **v0.4.0** release (APK attached) via `gh release create`.

## ⚠️ UNCOMMITTED — the paint fix (just installed, awaiting user's on-device confirmation)
**Problem**: Bambu/Orca painted 3MF loaded but showed mostly UNpainted. Two real bugs, both fixed:

### Bug 1 — palette too short (FIXED)
Default filament palette = **1 slot** (`Prefs.getFilamentSlots`). `GLRenderer.getCommittedOverlays` only built one color overlay per palette slot → filaments 2..8 never drawn.
- `MainActivity.java`: new `applyProjectFilamentColors(projectSettings)` reads `filament_colour`+`filament_type` from project_settings.config and calls `Prefs.setFilamentSlots(...)`. Called in `importEmbedded3mfProfiles` BEFORE `loadModel`, so overlays build with the file's colors (matches desktop). Added `import ...utils.FilamentSlot;`.
- `GLRenderer.getCommittedOverlays`: loop bound now `min(max(palette.len, model.paintMaxFilament(i)), MAX_FILAMENT_COLORS=16)`; colors past palette use new `fallbackFilamentColor()` (golden-angle HSV). New native `model_paint_max_filament` (Native.java + Model.java `paintMaxFilament`).

### Bug 2 — multi-volume assembly (THE BIG ONE, FIXED)
The Runescape file is an **assembly = 1 ModelObject with 3 volumes** (figure 7950 tris painted, accessories 1838 painted, support_blocker cube 12 unpainted). The rendered GLModel is `obj->mesh()` = ALL is_model_part volumes merged (each transformed by `inst->get_matrix() * v->get_matrix()`, in order; support_blocker excluded). But paint code read only `obj->volumes[0]` and deserialized it against the merged mesh → only one piece showed, misaligned.
- `beam_native.cpp`: new static `accumulate_paint_facets(obj, state)` iterates ALL instances × model-part volumes, deserializes EACH volume's `mmu_segmentation_facets` against `v->mesh()` (volume-local), `get_facets(state)`, transforms verts by `inst_m * v->get_matrix()`, merges with index offset. `model_build_paint_overlay`, `model_has_paint`, `model_paint_max_filament` all rewritten to use all volumes.
- Why it stays aligned: transform is affine so split-midpoints commute; single-volume in-app paint still works because volume[0] triangle order == obj->mesh() order and `inst_m*vol_m*local == obj->mesh()` coords. In-app paint_commit still dumps to volumes[0] (pre-existing multi-volume in-app limitation, NOT made worse).

**Files touched (uncommitted)**: `app/src/main/jni/slicebeam/beam_native.cpp`, `app/src/main/java/.../slic3r/Native.java`, `.../slic3r/Model.java`, `.../render/GLRenderer.java`, `.../MainActivity.java`.

## IMMEDIATE NEXT STEP
User is re-opening the Runescape 3MF on the phone (APK built 22:59 installed). **Overlays build once per model load → file must be fully reopened.** Expect figure AND base painted in the 8 file colors. If good:
```
git add -A && git commit -m "Load multi-volume painted 3MF assemblies; adopt project filament colors" && git push
```
(Consider bumping a v0.4.1 release.) Note: opening a painted project REPLACES the user's filament palette with the file's colors (same as desktop) — intended.

## Gotchas / scratch
- Leftover untracked debris (safe to delete): `BedFragment.java.orig/.rej`, `patch.diff`, `logcat_filtered*.txt`.
- `EnforcerBlockerType`: NONE=0, Extruder1=1 … Extruder16=ExtruderMax=16. `paint_state(filamentIdx)` maps f→Extruder f; base/unpainted = NONE (rendered by normal model render with palette[0]).
- Architecture cheat-sheet from prior session (key migration, import whitelists, curr_bed_type=btPEI bed-temp fix, wizard filament page, regenerated 64-vendor INIs) is in git history; profile-import fidelity work already shipped earlier.
