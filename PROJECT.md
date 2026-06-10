# Project: OrcaSlicerMobile Overhaul

## Architecture
- **GLView & GLRenderer (UI & Renderer)**: Handles touch events (gestures, clicks), displays the 3D model, manages context menu triggering, and draws measurement/painting overlays.
- **Slic3r Native / JNI Interface**: Provides a bridge between Java and native Slic3r functions (`libslic3r`), specifically for raycasting, object manipulation, configuration loading, and arrangement.
- **Preset/Onboarding Module**: Manages the first-time startup configuration state, blocks main interface access until setup is completed, and initializes custom/built-in presets.
- **Auto Brim & Config Sanitizer**: Sanitizes configuration values (e.g., `brim_type`) before native bed configure calls load the configuration.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | E2E Test Suite | Create opaque-box E2E test cases (Tiers 1-4) and publish `TEST_READY.md`. | None | IN_PROGRESS (79c76a26-e2c4-4c89-a085-ee0b211bdf50) |
| 2 | Setup Screen & Nomenclature | Block app, prompt for presets, fix "auto brim" loading and PrusaSlicer references. | None | IN_PROGRESS (e68fe3f3-a3ab-4fc2-9d58-7f382d08b808) |
| 3 | Fill Bed Optimization | Pre-calculate capacity mathematically based on bounding boxes before calling native arrange. | M2 | PLANNED |
| 4 | 3D Renderer Features | Implement measuring, color/support painting tools, and long-press context menu in OpenGL view. | M2, M3 | PLANNED |
| 5 | Integration & Verification | Pass 100% of E2E tests (Tier 1-4). | M1, M4 | PLANNED |
| 6 | Adversarial Hardening | Implement Tier 5 (adversarial tests) to cover gaps. | M5 | PLANNED |

## Interface Contracts
### Java (GLView) ↔ Native (beam_native.cpp)
- **Model Raycasting / Hit Detection**: `Native.glmodel_raycast_closest_hit(int objectIndex, float x, float y)` -> `NativeHitResult` containing hit coordinates, facet/triangle index, and distance.
- **Paint Apply**: `Native.apply_paint(int objectIndex, int facetIndex, int paintType, int paintValue)` -> boolean success.
- **Bed Arrange**: `Native.bed_arrange(long ptr, long model)` -> boolean success.

### Config Loading ↔ JNI Bed Configure
- **Config Sanitizer**: `SliceBeam.sanitizeConfig(String configPath)` -> ensures `brim_type` value is compatible with native Slic3r validation (e.g. mapping `"auto_brim"` to valid native format or preventing configuration load abort).

## Code Layout
- Java UI: `app/src/main/java/ru/ytkab0bp/slicebeam/`
- GL Rendering: `app/src/main/java/ru/ytkab0bp/slicebeam/render/`
- JNI/Native: `app/src/main/jni/slicebeam/`
- Resources/Assets: `app/src/main/res/` and `app/src/main/assets/`
