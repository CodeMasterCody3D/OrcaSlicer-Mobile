# Original User Request

## Initial Request — 2026-06-09T04:22:45-05:00

Overhaul the OrcaSlicerMobile Android application to include advanced desktop-level features such as 3D measuring, color/support painting, long-press context menus, an optimized "fill bed" algorithm, a first-time setup screen, and resolve remaining PrusaSlicer to OrcaSlicer nomenclature bugs.

Working directory: /home/cody/workspace/OrcaSlicerMobile
Integrity mode: development

## Requirements

### R1. 3D Renderer Features & UI
Implement color painting, support painting, and 3D measuring tools. Add a long-press context menu to the 3D model view mimicking OrcaSlicer desktop's right-click menu.

### R2. Fill Bed Optimization
Rewrite the `fillBedWithSelectedModel` logic to mathematically calculate how many models can fit within the bed bounding box before running the native arrangement algorithm, rather than arranging sequentially after each clone.

### R3. Onboarding & Bug Fixes
Add a mandatory, blocking first-time setup screen prompting users to choose between custom or built-in presets before they can access the rest of the app. Fix the "auto brim" issue and any remaining PrusaSlicer name conversion bugs.

## Acceptance Criteria

### Verification
- [ ] The app must compile successfully without errors using `./scripts/build-debug.sh`.
- [ ] An independent subagent must review the OpenGL and UI code changes and verify they implement the correct raycasting mechanics for measuring and painting.
- [ ] An independent subagent must confirm the first-time setup screen is registered as the initial launching point in the Android manifest and properly blocks access.
- [ ] An independent subagent must confirm the `fillBedWithSelectedModel` algorithm calculates bounding box capacity before calling `arrange()`.
