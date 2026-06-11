# AI Handoff: OrcaSlicer Mobile Paint & Cut Tool Improvements

This workspace has been updated with several major features requested by the user. Below is the progress status and implementation details for the remaining features.

## Project Location
```
/home/cody/workspace/OrcaSlicerMobileTestHandOffToGemini
```

## Build & Deploy Commands
```bash
./gradlew installDebug   # Compiles Java/C++ and installs via ADB to connected phone
```

---

## 📊 Feature Progress Status

| Feature | Status | Description |
| :--- | :--- | :--- |
| **1. ✂️ Cut Plane Preview** | **DONE** | Translucent preview plane rendered in real-time matching sliders. |
| **2. 🖌️ Round Brush Fix** | **DONE** | Circular/Sphere brush stroke with triangle subdivision boundary clipping. |
| **3. 📏 Height Band Width** | **DONE** | SeekBar added in UI to paint a horizontal band of adjustable width. |
| **4. 🪣 Bucket Angle limit** | **DONE** | SeekBar added in UI to limit bucket fill angle and prevent boundary bleeding. |
| **6. 🎛️ Brush Type Toggle** | **DONE** | Switch between Circle (projected) and Sphere (3D) brush types. |
| **5. 🔗 Cut Connectors** | **TO DO** | Place dowels/snaps/plugs on the cut plane and perform boolean cuts. |

---

## 🛠️ Details of Completed Work

### 1. Cut Plane Visualization
- Renders a translucent white quad on the OpenGL thread using `flat` shader.
- Transformation matrix computed in [GLRenderer.java](file:///home/cody/workspace/OrcaSlicerMobileTestHandOffToGemini/app/src/main/java/ru/ytkab0bp/slicebeam/render/GLRenderer.java#L465) matching the exact translation and rotation applied by the native cut engine.
- Slider listeners in [TransformMenu.java](file:///home/cody/workspace/OrcaSlicerMobileTestHandOffToGemini/app/src/main/java/ru/ytkab0bp/slicebeam/components/bed_menu/TransformMenu.java#L600) synchronize preview plane attributes in real time.

### 2. Round Brush & Brush Type Toggle
- Bypassed brute-force centroid distance checks in `paint_brush` JNI.
- Replaced with the native Slic3r `TriangleSelector::select_patch(...)` using `SinglePointCursor::cursor_factory(...)`.
- Passes the model's instance matrix (`inst_matrix`) and converts the camera position to local mesh coordinates.
- Supports toggling between Circle (camera-projected) and Sphere (3D) brushes via JNI.

### 3. Height Band Width & Bucket Angle limit
- Tapping with the height paint tool now paints a Z-band using `paintSession.height(zMin, zMax)`.
- UI SeekBars added to [PaintModeView.java](file:///home/cody/workspace/OrcaSlicerMobileTestHandOffToGemini/app/src/main/java/ru/ytkab0bp/slicebeam/view/PaintModeView.java#L150) to dynamically adjust height band size and angle limit thresholds.

---

## 🚀 TASK FOR NEXT AI: Feature 5 — Cut Connectors

The C++ Slic3r engine already has full support for placing cut connectors (Plugs, Snaps, and Dowels) on the cut plane. The goal of this task is to expose this functionality to the Android UI.

### Step 1: JNI Declarations
Expose native connector methods in JNI. Add the following to [Native.java](file:///home/cody/workspace/OrcaSlicerMobileTestHandOffToGemini/app/src/main/java/ru/ytkab0bp/slicebeam/slic3r/Native.java):
```java
static native void model_add_connector(long ptr, int objIdx, double x, double y, double z, float radius, float height, int type);
static native void model_remove_connector(long ptr, int objIdx, int connIdx);
static native void model_clear_connectors(long ptr, int objIdx);
```
Where `type` maps to `CutConnectorType` enum:
- `0` = Plug
- `1` = Dowel
- `2` = Snap

### Step 2: C++ JNI Implementation
Implement these methods in [beam_native.cpp](file:///home/cody/workspace/OrcaSlicerMobileTestHandOffToGemini/app/src/main/jni/slicebeam/beam_native.cpp). 
You need to instantiate and insert a `CutConnector` object into the `ModelObject::cut_connectors` vector. Slic3r's native `Cut` class in `CutUtils.cpp` automatically reads these connectors when `perform_with_plane()` is executed.

Refer to `libslic3r/Model.hpp` for the struct definition:
```cpp
struct CutConnector {
    Vec3d pos;
    Transform3d rotation_m; // Align with the cut plane normal
    float radius;
    float height;
    float radius_tolerance;
    float height_tolerance;
    CutConnectorAttributes attribs; // type, style, shape
};
```

### Step 3: UI Placement in CutMenu
1. Add an "Add Connector" mode toggle button in `CutMenu` ([TransformMenu.java](file:///home/cody/workspace/OrcaSlicerMobileTestHandOffToGemini/app/src/main/java/ru/ytkab0bp/slicebeam/components/bed_menu/TransformMenu.java)).
2. When the user taps on the translucent cut plane in the viewport:
   - Compute the 3D position of the click on the cut plane.
   - Dispatch `Native.model_add_connector`.
   - Update the UI to show sliders adjusting connector dimensions (`radius`, `height`, and `type`).

### Step 4: Render Connector Previews
In [GLRenderer.java](file:///home/cody/workspace/OrcaSlicerMobileTestHandOffToGemini/app/src/main/java/ru/ytkab0bp/slicebeam/render/GLRenderer.java), loop over the object's `cut_connectors` and render cylinder wireframes or solid shapes at their 3D positions on the cut plane so the user can see them before tapping "Apply Cut".
