#!/usr/bin/env python3
"""Static regression checks for Android cut connector plumbing.

This intentionally checks the Java/JNI/native bridge points that make Feature 5 usable:
- Java Native declarations and Model wrapper methods exist.
- Native C++ uses the correct ModelRef pointer wrapper.
- Connector add/remove/clear JNI functions create unprocessed cut connector volumes.
- Cut execution enables CreateDowels so dowel connectors survive the cut.
- Renderer exposes cut-plane connector placement and preview state.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")

def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"missing {label}: {needle}")

native_java = read("app/src/main/java/ru/ytkab0bp/slicebeam/slic3r/Native.java")
model_java = read("app/src/main/java/ru/ytkab0bp/slicebeam/slic3r/Model.java")
renderer_java = read("app/src/main/java/ru/ytkab0bp/slicebeam/render/GLRenderer.java")
transform_java = read("app/src/main/java/ru/ytkab0bp/slicebeam/components/bed_menu/TransformMenu.java")
beam_native = read("app/src/main/jni/slicebeam/beam_native.cpp")

for needle, label in [
    ("static native void model_add_connector(long ptr, int objIdx, double x, double y, double z, float radius, float height, int type);", "specified add connector JNI declaration"),
    ("static native void model_remove_connector(long ptr, int objIdx, int connIdx);", "remove connector JNI declaration"),
    ("static native void model_clear_connectors(long ptr, int objIdx);", "clear connector JNI declaration"),
    ("static native void model_add_connector_on_plane", "rotated-plane connector JNI declaration"),
]:
    require(native_java, needle, label)

for needle, label in [
    ("public void addConnector(int objectIndex", "Model.addConnector wrapper"),
    ("public void removeConnector(int objectIndex", "Model.removeConnector wrapper"),
    ("public void clearConnectors(int objectIndex", "Model.clearConnectors wrapper"),
]:
    require(model_java, needle, label)

for needle, label in [
    ("Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1add_1connector", "native add connector implementation"),
    ("Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1remove_1connector", "native remove connector implementation"),
    ("Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1clear_1connectors", "native clear connector implementation"),
    ("ModelRef* model = (ModelRef *) (intptr_t) ptr;", "native functions use ModelRef wrapper"),
    ("ModelVolume::CutInfo(connector_type", "connector volume cut info"),
    ("ModelObjectCutAttribute::CreateDowels", "cut enables dowel creation"),
    ("!volume->cut_info.is_processed", "remove/clear only unprocessed connector volumes"),
]:
    require(beam_native, needle, label)

for needle, label in [
    ("setCutConnectorPlacement", "renderer placement toggle"),
    ("tryAddCutConnector", "renderer click-to-place implementation"),
    ("drawCutConnectors", "renderer connector preview drawing"),
    ("cutConnectorsDirty", "renderer preview VBO invalidation"),
]:
    require(renderer_java, needle, label)

for needle, label in [
    ("Add Connector", "CutMenu add connector toggle"),
    ("Radius", "CutMenu radius control"),
    ("Height", "CutMenu height control"),
    ("Clear Connectors", "CutMenu clear connector action"),
]:
    require(transform_java, needle, label)

print("cut connector bridge checks passed")
