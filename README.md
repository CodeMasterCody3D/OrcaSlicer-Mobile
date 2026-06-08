# OrcaSlicer Mobile

OrcaSlicer Mobile is an Android slicer project bootstrapped from [Slice Beam](https://github.com/utkabobr/SliceBeam), which itself is based on PrusaSlicer/Slic3r technology.

## Current status

This repository is in the **bootstrap** phase. The immediate goal is to create a working Android slicer shell with OrcaSlicer-compatible profile handling, then iteratively replace inherited Slice Beam/Beam-specific services and validate current OrcaSlicer preset compatibility.

## Why this base

Slice Beam already provides the hard Android-specific pieces that a direct desktop OrcaSlicer port would need:

- Android Java UI for model loading, printer/profile setup, slicing, and G-code export
- JNI bridge into a native slicer core
- OpenGL preview/G-code visualization integration
- Existing `.orca_printer` config-bundle import and inheritance flattening
- Android Gradle/CMake project layout

## Bootstrap rules

For the first milestone, the Java package path remains `ru.ytkab0bp.slicebeam` because native JNI method names are tied to that package. Renaming Java packages comes later after the app builds and native bridge tests are in place.

The Android `applicationId` is already changed to:

```text
com.codemastercody3d.orcaslicermobile
```

## Known inherited areas to replace or audit

- Beam/SliceBeam cloud endpoints
- Beam server data/update feed
- Boosty/Telegram/K3D support links
- Prusa preset repository setup flow
- Slice Beam-specific copy and UI labels
- Native deps not stored in git: Boost, oneTBB, OCCT, GMP/MPFR binaries

## License and attribution

This project preserves the upstream AGPLv3 licensing requirements inherited from Slice Beam, PrusaSlicer, and Slic3r. Source availability and attribution must be maintained for distributed APKs.
