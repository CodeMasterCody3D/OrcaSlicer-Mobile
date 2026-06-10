# Test Infrastructure: Headless E2E Test Suite for OrcaSlicerMobile

This document outlines the testing philosophy, feature coverage inventory, test runner architecture, and execution details for the headless end-to-end (E2E) test suite of OrcaSlicerMobile.

## 1. Test Philosophy
The test suite follows an **opaque-box, requirement-driven** model:
* **Requirement-Driven**: Every test case directly traces to a specific feature requirement or bug fix.
* **Opaque-Box**: Tests verify correct behavior by invoking public UI controllers, simulating input touch coordinates, and asserting changes in application state or configuration output, treating the underlying graphics rendering and native C++ processing as a black box.

## 2. Feature Inventory
The E2E test suite covers the following 8 core features:
1. **Color Painting**: Applying filament colors to mesh facets.
2. **Support Painting**: Marking facets for support enforcement or blocking.
3. **3D Measuring**: Calculating coordinates and distance between two selected points.
4. **Long-Press Context Menu**: Opening context options via sustained hold.
5. **Fill Bed pre-calculation**: Optimizing the grid count of models fitting on the bed.
6. **Mandatory Setup Screen**: Forcing printer configuration wizard on first-start.
7. **Auto Brim config loading**: Sanitizing auto-brim configuration values to prevent crashes.
8. **Nomenclature fixes**: Replacing legacy "PrusaSlicer" strings with "OrcaSlicer Mobile" / "Slice Beam".

## 3. Test Architecture
The test infrastructure runs headlessly on a standard JVM without an emulator or device:
* **Shadow Native Class**: A test-only `ru.ytkab0bp.slicebeam.slic3r.Native` class is placed first on the classpath, replacing JNI library loading with pure Java mock responses.
* **Android Stubs**: Lightweight mock implementations of Android SDK classes are compiled as part of the test setup.
* **Simulation Layer**: Motion events are dispatched programmatically to `GLView` components to simulate user input.

```
scripts/tests/
├── E2ETestRunner.java       # Runs all test tiers and generates a summary report
├── E2ETestSuite.java        # Contains test case definitions and assertions
└── mocks/                   # Contains Android stubs and shadow Native.java
```

## 4. Coverage Thresholds
The test suite guarantees coverage across 4 distinct testing tiers:
* **Tier 1 (Feature Coverage)**: 5 tests per feature = **40 tests**
* **Tier 2 (Boundary & Corner)**: 5 tests per feature = **40 tests**
* **Tier 3 (Cross-Feature)**: Integration of multiple features = **8 tests**
* **Tier 4 (Real-World)**: End-to-end user workflows = **5 tests**
* **Minimum Total Coverage**: **93 tests**

## 5. Verification Method
To compile and execute the E2E test suite:
```bash
./scripts/tests/run_e2e_tests.sh
```
This script automates:
1. Compilation of Android stubs and JVM mock classes.
2. Compilation of application code combined with E2ETestSuite and E2ETestRunner.
3. Classpath layering (mock classes ahead of actual classes) and JVM execution.
