# Build Status

Last verified from this workspace during bootstrap.

## Environment used

```bash
JAVA_HOME=/home/cody/.sdkman/candidates/java/17.0.11-tem
ANDROID_HOME=/home/cody/android-sdk
ANDROID_SDK_ROOT=/home/cody/android-sdk
```

Gradle successfully configured the project and automatically installed required Android components:

- Android SDK Platform 35
- Android NDK `23.1.7779620`
- CMake `3.22.1`

## Commands

Configuration check:

```bash
./gradlew projects --no-daemon --stacktrace
```

Result: **successful**.

Debug build wrapper:

```bash
scripts/build-debug.sh
```

Result: **fails at native CMake/Ninja build because upstream prebuilt native libraries are intentionally not stored in git**.

Current first failure:

```text
ninja: error: '../../../../src/main/jniImports/oneTBB/lib/arm64-v8a/libtbb.a', needed by '../../../../build/intermediates/cxx/Release/.../obj/arm64-v8a/libslic3r.so', missing and no known rule to make it
```

## Native prebuilt check

Run:

```bash
scripts/check-native-prebuilts.py arm64-v8a
```

Current result:

- present prebuilt imports: 3
- missing prebuilt imports: 61

Present:

- `app/src/main/jniLibs/arm64-v8a/libgmp.so`
- `app/src/main/jniLibs/arm64-v8a/libgmpxx.so`
- `app/src/main/jniLibs/arm64-v8a/libmpfr.so`

Missing groups:

- oneTBB: `libtbb.a`, `libtbbmalloc.a`
- OCCT shared libs: 20 imported `libTK*.so` files
- Boost 1.85 static libs for `arm64-v8a`: 39 imported `libboost_*-clang-mt-a64-1_85.a` files

## Next build milestone

Build or recover the missing `arm64-v8a` native prebuilt bundle and place it in the exact paths expected by `app/CMakeLists.txt`, then rerun:

```bash
scripts/check-native-prebuilts.py arm64-v8a
scripts/build-debug.sh
```
