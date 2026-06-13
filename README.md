# OrcaSlicer Mobile

please consider donating.

[Donate to OrcaSlicer Mobile for Android Via PayPal](https://www.paypal.com/donate/?business=E37Z2QEXT7AQW&no_recurring=0&item_name=I+have+built+orcaslicer+mobile+for+Android.+I+need+your+help+to+keep+paying+for+my+ai+tools+to+keep+providing+bug+fixes.+Thanks&currency_code=USD)


[Donate to OrcaSlicer Mobile for Android Via Patreon](https://www.patreon.com/cw/CodeMasterCody3D)

[Donate to OrcaSlicer Mobile for Android Via Cash app](https://cash.app/$codyddixon)



A 3D printing slicer for Android, powered by the real **OrcaSlicer** engine.

OrcaSlicer Mobile takes the excellent Android slicer shell from [Slice Beam](https://github.com/utkabobr/SliceBeam) and replaces its slicing core with a full port of [OrcaSlicer](https://github.com/SoftFever/OrcaSlicer)'s `libslic3r` — so the G-code you slice on your phone comes from the same engine you use on the desktop.

## Features

- **Full OrcaSlicer slicing engine** running natively on-device: Arachne wall generator, tree supports, seam control, adaptive layers, and the rest of the Orca feature set
- **OrcaSlicer profile import** — load `.orca_printer` / `.orca_filament` config bundles, including profile inheritance resolution and automatic download of vendor base profiles
- **Multi-color painting** — paint models with brush, bucket-fill, and height-range tools from a palette of up to 16 filaments; sliced output gets proper tool changes, a prime tower, and per-filament flush volumes
- **G-code preview** with feature-type and filament/tool color views
- **Profile editor** with OrcaSlicer setting names and categories
- **3MF / STL / STEP / OBJ model import**, transform tools, auto-arrange, cut tool, auto-orient
- **Fully offline** — no account, no cloud, no telemetry

## Install

Download the latest APK from the [Releases](../../releases) page and sideload it (you may need to allow "install from unknown sources").

Requirements: Android 5.0+, 64-bit ARM device (arm64-v8a).

## Build from source

```bash
git clone https://github.com/CodeMasterCody3D/OrcaSlicer-Mobile.git
cd OrcaSlicer-Mobile
./gradlew assembleDebug
```

- Android SDK 35, NDK `23.1.7779620`
- The native engine (libslic3r + dependencies) builds via CMake on the first build — expect it to take a while
- Prebuilt native dependencies not stored in git (Boost, oneTBB, OCCT, GMP/MPFR) are expected under `app/src/main/jniImports/` and `app/src/main/occt/`
- The output APK lands in `app/build/outputs/apk/debug/`

Note: the Java package path remains `ru.ytkab0bp.slicebeam` for now because native JNI method names are tied to it; the `applicationId` is `com.codemastercody3d.orcaslicermobile`.

## Credits

This project stands on the shoulders of:

- [Slice Beam](https://github.com/utkabobr/SliceBeam) by utkabobr — the Android shell, JNI bridge, and OpenGL preview this project is built on
- [OrcaSlicer](https://github.com/SoftFever/OrcaSlicer) by SoftFever — the slicing engine
- [PrusaSlicer](https://github.com/prusa3d/PrusaSlicer) / Slic3r and [Bambu Studio](https://github.com/bambulab/BambuStudio), which OrcaSlicer is built upon

## Status

Experimental / alpha. Sliced output should always be sanity-checked in the G-code preview before printing. Issues and feedback are welcome.

## License

[AGPL-3.0](LICENSE), same as the projects it derives from. Source availability and attribution must be maintained for distributed APKs.
