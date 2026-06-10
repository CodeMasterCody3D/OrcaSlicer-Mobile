# E2E Test Suite Ready

The headless end-to-end (E2E) test suite for OrcaSlicerMobile is ready. It executes headlessly on standard JVM platforms by employing Class Path Shadowing (JNI Bypass) and Android API Stubbing.

## 1. Test Execution Commands

To compile the mock stubs, shadow classes, test suite, and run all 93 tests, run:
```bash
./scripts/tests/run_e2e_tests.sh
```

Alternatively, you can run the steps manually:
```bash
# 1. Create build directory
mkdir -p scripts/tests/bin

# 2. Compile mock stubs and shadow JNI classes
find scripts/tests/mocks -name "*.java" > compile_mocks.txt
javac -d scripts/tests/bin @compile_mocks.txt
rm compile_mocks.txt

# 3. Resolve Classpath & compile test suite
SDK_DIR=$(grep 'sdk.dir' local.properties | cut -d'=' -f2 | xargs)
ANDROID_JAR=$(ls -1d "$SDK_DIR"/platforms/android-* | sort -V | tail -n1)/android.jar
GRADLE_CLASSPATH=$(./gradlew -q -I scripts/tests/init.gradle :app:printClasspath | grep "CLASSPATH_ENTRY:" | cut -d':' -f2- | tr '\n' ':')

javac -d scripts/tests/bin \
  -cp "scripts/tests/bin:app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes/:$GRADLE_CLASSPATH:$ANDROID_JAR" \
  scripts/tests/E2ETestSuite.java scripts/tests/E2ETestRunner.java

# 4. Run tests
java -cp "scripts/tests/bin:app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes/:$GRADLE_CLASSPATH" \
  scripts.tests.E2ETestRunner
```

## 2. Coverage Summary

A total of **93 tests** are implemented and passing:

* **Tier 1: Feature Coverage (40 tests)**:
  * Color Painting: 5 tests
  * Support Painting: 5 tests
  * 3D Measuring: 5 tests
  * Long-Press Context Menu: 5 tests
  * Fill Bed pre-calculation: 5 tests
  * Mandatory Setup Screen: 5 tests
  * Auto Brim config loading: 5 tests
  * Nomenclature fixes: 5 tests
* **Tier 2: Boundary & Corner Cases (40 tests)**:
  * Color Painting: 5 tests
  * Support Painting: 5 tests
  * 3D Measuring: 5 tests
  * Long-Press Context Menu: 5 tests
  * Fill Bed pre-calculation: 5 tests
  * Mandatory Setup Screen: 5 tests
  * Auto Brim config loading: 5 tests
  * Nomenclature fixes: 5 tests
* **Tier 3: Cross-Feature Integration (8 tests)**:
  * Combination scenarios mixing painting, measuring, setup, and bed filling.
* **Tier 4: Real-World Application Scenarios (5 tests)**:
  * End-to-end slicing workflows, multi-model print jobs, support enforcement, profile migrations, and crash resets.

## 3. Feature Checklist

- [x] **JNI Bypass (Class Path Shadowing)**: Shadowed `ru.ytkab0bp.slicebeam.slic3r.Native` class resolves natively compiled logic without loading JNI shared libraries.
- [x] **Android Stubbing**: Standard Android SDK APIs (`TextUtils`, `Log`, `Context`, `SharedPreferences`, `Handler`, `Looper`, `PreferenceManager`, `GLSurfaceView`) stubbed to run synchronously on host JVMs.
- [x] **Color Painting**: State tracking and facet segmentation validation.
- [x] **Support Painting**: Blocking/enforcing brush and boundary constraints checked.
- [x] **3D Measuring**: Distance calculations and axis projections validated.
- [x] **Long-Press Context Menu**: Touch limits, listener invocation, and menu selection tested.
- [x] **Fill Bed pre-calculation**: Limit checks (at/above/below 256) and grid math verified.
- [x] **Mandatory Setup Screen**: Redirection logic on null/corrupt configuration verified.
- [x] **Auto Brim config loading**: Automatic sanitation of `auto_brim` values during slicing/3mf exports verified.
- [x] **Nomenclature fixes**: Elimination of "PrusaSlicer" strings in En/Ru localization checked.
