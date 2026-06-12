#include <android/log.h>
#include <typeinfo>
#include <set>
#include <algorithm>

#include <thread>

#include <jni.h>
#include "libslic3r/libslic3r.h"
#include "libslic3r/Config.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Slicing.hpp"
#include "libslic3r/Emboss.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/ModelArrange.hpp"
#include <libslic3r/SVG.hpp>
#include <libslic3r/GCode.hpp>
#include <libslic3r/Print.hpp>
#include <libslic3r/Utils.hpp>
#include <libslic3r/AppConfig.hpp>
#include <libslic3r/CutUtils.hpp>
#include <android/log.h>
#define LOG_TAG "NativeCut"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#include "libslic3r/Geometry.hpp"
#include "libslic3r/Arrange.hpp"
#include "libslic3r/AABBMesh.hpp"
#include "libslic3r/TriangleSelector.hpp"
#include "libslic3r/calib.hpp"
#include "libslic3r/Geometry/ConvexHull.hpp"
#include "libslic3r/Format/3mf.hpp"
#include "bbl/Orient.hpp"
#include "Viewer.hpp"

#include "GLModel.hpp"
#include "GLShader.hpp"
#include "bed_utils.hpp"
#include "libvgcode_utils.hpp"

#include <igl/unproject.h>
#include <GLES3/gl3.h>

using namespace Slic3r;
using namespace Slic3r::GUI;

#define TAG "SB_Native"

struct PlaneData {
    std::vector<Vec3d> vertices;
    Vec3d normal;
    float area;
};
struct ModelRef {
    Model model;
    std::string base_name;
};
struct GLModelRef {
    GLModel model;
    TriangleMesh mesh;
    AABBMesh* emesh;
    std::vector<stl_normal> normals;
    Vec3d flatten_normal;
};
// Multi-color painting session, bound to one ModelObject's first volume. Holds a TriangleSelector
// over the volume mesh plus an AABBMesh for ray-casting touch points to a facet.
struct PaintSessionRef {
    ModelRef* model = nullptr;
    int objIdx = -1;
    // 0 = color (mmu segmentation), 1 = support, 2 = seam, 3 = fuzzy skin.
    int mode = 0;
    TriangleMesh mesh;
    AABBMesh* emesh = nullptr;
    std::unique_ptr<Slic3r::TriangleSelector> selector;
};
struct ShaderRef {
    GLShaderProgram program;
};
struct BedRef {
    DynamicPrintConfig config;
    ExPolygon contour;
    GLModel* triangles;
    GLModel* gridlines;
    GLModel* contourlines;
    BuildVolume build_volume;
};
struct GCodeViewerRef {
    libvgcode::Viewer viewer;
    libvgcode::GCodeInputData data;
    bool initialized;
};
struct GCodeResultRef {
    GCodeProcessorResult result;
    std::string name;
};
struct ConfigRef {
    DynamicPrintConfig config;
};

jclass sliceListenerClass;
jmethodID sliceListenerOnProgress;

jclass shadersManagerClass = nullptr;
jmethodID shadersManagerGetCurrent = nullptr;

static JavaVM* staticVM;

GLShaderProgram* get_current_shader() {
    JNIEnv* env;
    if (staticVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return nullptr;
    }

    jlong ptr = env->CallStaticLongMethod(shadersManagerClass, shadersManagerGetCurrent);
    if (ptr == 0) {
        return nullptr;
    }
    ShaderRef* ref = (ShaderRef*) (intptr_t) ptr;
    GLShaderProgram* program = &ref->program;

    return program;
}

extern "C" {
    int JNI_OnLoad(JavaVM *vm, void*) {
        JNIEnv *env;
        if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            return JNI_ERR;
        }

        staticVM = vm;

        sliceListenerClass = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/SliceListener");
        sliceListenerOnProgress = env->GetMethodID(sliceListenerClass, "onProgress", "(ILjava/lang/String;)V");

        shadersManagerClass = static_cast<jclass>(env->NewGlobalRef(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/GLShadersManager")));
        shadersManagerGetCurrent = env->GetStaticMethodID(shadersManagerClass, "getCurrentShaderPointer", "()J");

        return JNI_VERSION_1_6;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_set_1svg_1path_1prefix(JNIEnv *env, jclass, jstring path) {
        const char* chars = env->GetStringUTFChars(path, JNI_FALSE);
        Slic3r::svg_path_prefix = std::string(chars);
        env->ReleaseStringUTFChars(path, chars);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_get_1print_1config_1def(JNIEnv *env, jclass, jobject def) {
        jclass printConfigDefClass = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/PrintConfigDef");
        jmethodID printConfigAddOption = env->GetMethodID(printConfigDefClass, "addOption", "(Ljava/lang/String;Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef;)V");
        jmethodID printConfigResolveEnum = env->GetStaticMethodID(printConfigDefClass, "resolveEnum", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");
        jclass configOptionDefClass = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef");
        jmethodID configOptionDefCtr = env->GetMethodID(configOptionDefClass, "<init>", "()V");
        jfieldID keyField = env->GetFieldID(configOptionDefClass, "key", "Ljava/lang/String;");
        jfieldID typeField = env->GetFieldID(configOptionDefClass, "type", "Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$ConfigOptionType;");
        jfieldID guiTypeField = env->GetFieldID(configOptionDefClass, "guiType", "Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$GUIType;");
        jfieldID labelField = env->GetFieldID(configOptionDefClass, "label", "Ljava/lang/String;");
        jfieldID fullLabelField = env->GetFieldID(configOptionDefClass, "fullLabel", "Ljava/lang/String;");
        jfieldID printerTechField = env->GetFieldID(configOptionDefClass, "printerTechnology", "Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$PrinterTechnology;");
        jfieldID categoryField = env->GetFieldID(configOptionDefClass, "category", "Ljava/lang/String;");
        jfieldID tooltipField = env->GetFieldID(configOptionDefClass, "tooltip", "Ljava/lang/String;");
        jfieldID sidetextField = env->GetFieldID(configOptionDefClass, "sidetext", "Ljava/lang/String;");
        jfieldID multilineField = env->GetFieldID(configOptionDefClass, "multiline", "Z");
        jfieldID fullWidthField = env->GetFieldID(configOptionDefClass, "fullWidth", "Z");
        jfieldID readonlyField = env->GetFieldID(configOptionDefClass, "readonly", "Z");
        jfieldID heightField = env->GetFieldID(configOptionDefClass, "height", "I");
        jfieldID widthField = env->GetFieldID(configOptionDefClass, "width", "I");
        jfieldID minField = env->GetFieldID(configOptionDefClass, "min", "F");
        jfieldID maxField = env->GetFieldID(configOptionDefClass, "max", "F");
        jfieldID modeField = env->GetFieldID(configOptionDefClass, "mode", "Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$ConfigOptionMode;");
        jfieldID defaultValueField = env->GetFieldID(configOptionDefClass, "defaultValue", "Ljava/lang/String;");
        jfieldID enumLabelsField = env->GetFieldID(configOptionDefClass, "enumLabels", "[Ljava/lang/String;");
        jfieldID enumValuesField = env->GetFieldID(configOptionDefClass, "enumValues", "[Ljava/lang/String;");

        auto resolveEnum = [&env,&printConfigDefClass,&printConfigResolveEnum](char* className, char* enumValue) {
            jobject key = env->NewStringUTF(className);
            jobject val = env->NewStringUTF(enumValue);

            jobject v = env->CallStaticObjectMethod(printConfigDefClass, printConfigResolveEnum, key, val);

            env->DeleteLocalRef(key);
            env->DeleteLocalRef(val);
            return v;
        };

        PrintConfigDef nDef;
        for (std::string key : nDef.keys()) {
            const ConfigOptionDef* nCfgDef = nDef.get(key);
            bool isEnum = false;
            jobject cfgDef = env->NewObject(configOptionDefClass, configOptionDefCtr);
            const char* typeStr;
            switch (nCfgDef->type) {
                default:
                case Slic3r::coNone:
                    typeStr = "NONE";
                    break;
                case Slic3r::coFloat:
                    typeStr = "FLOAT";
                    break;
                case Slic3r::coFloats:
                    typeStr = "FLOATS";
                    break;
                case Slic3r::coInt:
                    typeStr = "INT";
                    break;
                case Slic3r::coInts:
                    typeStr = "INTS";
                    break;
                case Slic3r::coString:
                    typeStr = "STRING";
                    break;
                case Slic3r::coStrings:
                    typeStr = "STRINGS";
                    break;
                case Slic3r::coPercent:
                    typeStr = "PERCENT";
                    break;
                case Slic3r::coPercents:
                    typeStr = "PERCENTS";
                    break;
                case Slic3r::coFloatOrPercent:
                    typeStr = "FLOAT_OR_PERCENT";
                    break;
                case Slic3r::coFloatsOrPercents:
                    typeStr = "FLOATS_OR_PERCENTS";
                    break;
                case Slic3r::coPoint:
                    typeStr = "POINT";
                    break;
                case Slic3r::coPoints:
                    typeStr = "POINTS";
                    break;
                case Slic3r::coPoint3:
                    typeStr = "POINT3";
                    break;
                case Slic3r::coBool:
                    typeStr = "BOOL";
                    break;
                case Slic3r::coBools:
                    typeStr = "BOOLS";
                    break;
                case Slic3r::coEnum:
                    typeStr = "ENUM";
                    isEnum = true;
                    break;
                case Slic3r::coEnums:
                    typeStr = "ENUMS";
                    break;
            }

            const char* guiTypeStr;
            switch (nCfgDef->gui_type) {
                default:
                case Slic3r::ConfigOptionDef::GUIType::undefined:
                    guiTypeStr = "UNDEFINED";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::i_enum_open:
                    guiTypeStr = "I_ENUM_OPEN";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::f_enum_open:
                    guiTypeStr = "F_ENUM_OPEN";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::select_open:
                    guiTypeStr = "SELECT_OPEN";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::color:
                    guiTypeStr = "COLOR";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::slider:
                    guiTypeStr = "SLIDER";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::legend:
                    guiTypeStr = "LEGEND";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::one_string:
                    guiTypeStr = "ONE_STRING";
                    break;
            }

            const char* techStr;
            switch (nCfgDef->printer_technology) {
                case Slic3r::ptAny:
                    techStr = "ANY";
                    break;
                case Slic3r::ptFFF:
                    techStr = "FFF";
                    break;
                case Slic3r::ptSLA:
                    techStr = "SLA";
                    break;
                default:
                case Slic3r::ptUnknown:
                    techStr = "UNKNOWN";
                    break;
            }

            const char* modeStr;
            switch (nCfgDef->mode) {
                case Slic3r::comSimple:
                    modeStr = "SIMPLE";
                    break;
                case Slic3r::comAdvanced:
                    modeStr = "ADVANCED";
                    break;
                case Slic3r::comExpert:
                    modeStr = "EXPERT";
                    break;
                default:
                case Slic3r::comDevelop:
                    modeStr = "UNDEFINED";
                    break;
            }

            jobject keyValue = env->NewStringUTF(key.c_str());
            jobject typeValue = resolveEnum((char*) "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$ConfigOptionType", (char*) typeStr);
            jobject guiTypeValue = resolveEnum((char*) "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$GUIType", (char*) guiTypeStr);
            jobject labelValue = env->NewStringUTF(nCfgDef->label.c_str());
            jobject fullLabelValue = env->NewStringUTF(nCfgDef->full_label.c_str());
            jobject printerTechValue = resolveEnum((char*) "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$PrinterTechnology", (char*) techStr);
            jobject categoryValue = env->NewStringUTF(nCfgDef->category.c_str());
            jobject tooltipValue = env->NewStringUTF(nCfgDef->tooltip.c_str());
            jobject sidetextValue = env->NewStringUTF(nCfgDef->sidetext.c_str());
            jobject modeValue = resolveEnum((char*) "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$ConfigOptionMode", (char*) modeStr);

            env->SetObjectField(cfgDef, keyField, keyValue);
            env->SetObjectField(cfgDef, typeField, typeValue);
            env->SetObjectField(cfgDef, guiTypeField, guiTypeValue);
            env->SetObjectField(cfgDef, labelField, labelValue);
            env->SetObjectField(cfgDef, fullLabelField, fullLabelValue);
            env->SetObjectField(cfgDef, printerTechField, printerTechValue);
            env->SetObjectField(cfgDef, categoryField, categoryValue);
            env->SetObjectField(cfgDef, tooltipField, tooltipValue);
            env->SetObjectField(cfgDef, sidetextField, sidetextValue);
            env->SetBooleanField(cfgDef, multilineField, nCfgDef->multiline);
            env->SetBooleanField(cfgDef, fullWidthField, nCfgDef->full_width);
            env->SetBooleanField(cfgDef, readonlyField, nCfgDef->readonly);
            env->SetIntField(cfgDef, heightField, nCfgDef->height);
            env->SetIntField(cfgDef, widthField, nCfgDef->width);
            env->SetFloatField(cfgDef, minField, nCfgDef->min);
            env->SetFloatField(cfgDef, maxField, nCfgDef->max);
            env->SetObjectField(cfgDef, modeField, modeValue);
            if (isEnum) {
                // OrcaSlicer stores enum labels/values as plain vectors on ConfigOptionDef
                // (PrusaSlicer's enum_def/ConfigOptionEnumDef wrapper does not exist here).
                const std::vector<std::string>& labels = nCfgDef->enum_labels;
                jobjectArray labelsArr = env->NewObjectArray(labels.size(), env->FindClass("java/lang/String"), nullptr);
                for (int i = 0; i < labels.size(); i++) {
                    jobject str = env->NewStringUTF(labels[i].c_str());
                    env->SetObjectArrayElement(labelsArr, i, str);
                    env->DeleteLocalRef(str);
                }

                const std::vector<std::string>& values = nCfgDef->enum_values;
                jobjectArray valuesArr = env->NewObjectArray(values.size(), env->FindClass("java/lang/String"), nullptr);
                for (int i = 0; i < values.size(); i++) {
                    jobject str = env->NewStringUTF(values[i].c_str());
                    env->SetObjectArrayElement(valuesArr, i, str);
                    env->DeleteLocalRef(str);
                }

                env->SetObjectField(cfgDef, enumLabelsField, labelsArr);
                env->SetObjectField(cfgDef, enumValuesField, valuesArr);

                env->DeleteLocalRef(labelsArr);
                env->DeleteLocalRef(valuesArr);
            }

            const ConfigOption* defValue = nCfgDef->get_default_value<ConfigOption>();
            if (defValue != nullptr) {
                jobject defValueObj = env->NewStringUTF(defValue->serialize().c_str());
                env->SetObjectField(cfgDef, defaultValueField, defValueObj);
                env->DeleteLocalRef(defValueObj);
            }

            env->CallVoidMethod(def, printConfigAddOption, keyValue, cfgDef);

            env->DeleteLocalRef(cfgDef);
            env->DeleteLocalRef(keyValue);
            env->DeleteLocalRef(typeValue);
            env->DeleteLocalRef(guiTypeValue);
            env->DeleteLocalRef(labelValue);
            env->DeleteLocalRef(fullLabelValue);
            env->DeleteLocalRef(printerTechValue);
            env->DeleteLocalRef(categoryValue);
            env->DeleteLocalRef(tooltipValue);
            env->DeleteLocalRef(sidetextValue);
            env->DeleteLocalRef(modeValue);
        }
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1read_1from_1file(JNIEnv *env, jclass, jstring path, jstring base_name, jint plateId) {
        const char* chars = env->GetStringUTFChars(path, JNI_FALSE);
        const char* baseChars = env->GetStringUTFChars(base_name, JNI_FALSE);

        ModelRef* ref;
        try {
            ref = new ModelRef();
            LoadStrategy load_strategy = LoadStrategy::AddDefaultInstances;
            if (boost::algorithm::iends_with(std::string(chars), ".3mf")) {
                load_strategy = LoadStrategy::AddDefaultInstances | LoadStrategy::LoadModel;
            }
            ref->model = Model::read_from_file(std::string(chars), nullptr, nullptr, load_strategy, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, plateId, nullptr);
            ref->base_name = std::string(baseChars);
        } catch (const Slic3r::RuntimeError& e) {
            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), e.what());
            return 0;
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
            return 0;
        }

        env->ReleaseStringUTFChars(path, chars);
        env->ReleaseStringUTFChars(base_name, baseChars);

        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1create(JNIEnv *env, jclass) {
        ModelRef* ref = new ModelRef();
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_models_1merge(JNIEnv* env, jclass, jlongArray ptrsArr) {
        ModelRef* ref = new ModelRef();

        jlong* ptrs = env->GetLongArrayElements(ptrsArr, JNI_FALSE);
        int len = env->GetArrayLength(ptrsArr);
        for (int i = 0; i < len; i++) {
            ModelRef* sRef = (ModelRef*) (intptr_t) ptrs[i];
            for (ModelObject* obj : sRef->model.objects) {
                ref->model.add_object(*obj);
            }
        }
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1objects_1count(JNIEnv* env, jclass, jlong ptr) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        return model->model.objects.size();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1add_1object_1from_1another(JNIEnv* env, jclass, jlong ptr, jlong fromPtr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        ModelRef* from = (ModelRef *) (intptr_t) fromPtr;
        model->model.add_object(*from->model.objects[i]);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1delete_1object(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        model->model.delete_object(i);
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1rotation(JNIEnv* env, jclass, jlong ptr, jint object_index) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[object_index];
        jdoubleArray arr = env->NewDoubleArray(3);
        env->SetDoubleArrayRegion(arr, 0, 3, obj->volumes[0]->get_rotation().data());
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1mirror(JNIEnv* env, jclass, jlong ptr, jint object_index) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[object_index];
        jdoubleArray arr = env->NewDoubleArray(3);
        env->SetDoubleArrayRegion(arr, 0, 3, obj->volumes[0]->get_mirror().data());
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1scale(JNIEnv* env, jclass, jlong ptr, jint object_index) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[object_index];
        jdoubleArray arr = env->NewDoubleArray(3);
        env->SetDoubleArrayRegion(arr, 0, 3, obj->volumes[0]->get_scaling_factor().data());
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1translation(JNIEnv* env, jclass, jlong ptr, jint object_index) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[object_index];
        Vec3d offset = obj->bounding_box_exact().center();
        jdoubleArray arr = env->NewDoubleArray(3);
        env->SetDoubleArrayRegion(arr, 0, 3, offset.data());
        return arr;
    }

    static bool is_unprocessed_cut_connector(const ModelVolume* volume) {
        return volume != nullptr && volume->cut_info.is_connector && !volume->cut_info.is_processed;
    }

    static CutConnectorType connector_type_from_int(jint type) {
        switch (type) {
            case 1: return CutConnectorType::Dowel;
            case 2: return CutConnectorType::Snap;
            case 0:
            default: return CutConnectorType::Plug;
        }
    }

    static void add_cut_connector_volume(ModelObject* object, const Vec3d& pos, float radius, float height, jint type, double rotX, double rotY) {
        if (object == nullptr) return;
        radius = std::max(radius, 0.1f);
        height = std::max(height, 0.1f);

        CutConnectorType connector_type = connector_type_from_int(type);
        TriangleMesh mesh = make_cylinder(1.0, 1.0, PI / 18.0);
        ModelVolume* volume = object->add_volume(std::move(mesh), ModelVolumeType::NEGATIVE_VOLUME, false);
        if (volume == nullptr) return;

        Transform3d rotation_m = Transform3d::Identity();
        rotation_m.rotate(Eigen::AngleAxisd(rotY, Vec3d::UnitY()));
        rotation_m.rotate(Eigen::AngleAxisd(rotX, Vec3d::UnitX()));
        volume->set_transformation(rotation_m);
        volume->set_offset(pos);
        volume->set_scaling_factor(Vec3d(radius, radius, height));
        volume->cut_info = ModelVolume::CutInfo(connector_type, 0.05f, 0.10f, false);
        volume->name = connector_type == CutConnectorType::Dowel ? "Cut Dowel" : (connector_type == CutConnectorType::Snap ? "Cut Snap" : "Cut Plug");
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1add_1connector(JNIEnv* env, jclass, jlong ptr, jint objIdx, jdouble x, jdouble y, jdouble z, jfloat radius, jfloat height, jint type) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || objIdx < 0 || objIdx >= model->model.objects.size()) return;
        add_cut_connector_volume(model->model.objects[objIdx], Vec3d(x, y, z), radius, height, type, 0.0, 0.0);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1add_1connector_1on_1plane(JNIEnv* env, jclass, jlong ptr, jint objIdx, jdouble x, jdouble y, jdouble z, jfloat radius, jfloat height, jint type, jdouble rotX, jdouble rotY) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || objIdx < 0 || objIdx >= model->model.objects.size()) return;
        add_cut_connector_volume(model->model.objects[objIdx], Vec3d(x, y, z), radius, height, type, rotX, rotY);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1remove_1connector(JNIEnv* env, jclass, jlong ptr, jint objIdx, jint connIdx) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || objIdx < 0 || objIdx >= model->model.objects.size() || connIdx < 0) return;
        ModelObject* object = model->model.objects[objIdx];
        int connector_counter = 0;
        for (int volume_idx = 0; volume_idx < object->volumes.size(); ++volume_idx) {
            if (!is_unprocessed_cut_connector(object->volumes[volume_idx])) continue;
            if (connector_counter == connIdx) {
                object->delete_volume(volume_idx);
                return;
            }
            ++connector_counter;
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1clear_1connectors(JNIEnv* env, jclass, jlong ptr, jint objIdx) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || objIdx < 0 || objIdx >= model->model.objects.size()) return;
        ModelObject* object = model->model.objects[objIdx];
        for (int volume_idx = int(object->volumes.size()) - 1; volume_idx >= 0; --volume_idx) {
            if (is_unprocessed_cut_connector(object->volumes[volume_idx])) {
                object->delete_volume(volume_idx);
            }
        }
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1cut(JNIEnv* env, jclass, jlong ptr, jint i, jdouble zHeight, jdouble rotX, jdouble rotY, jboolean keepUpper, jboolean keepLower) {
        ModelRef* modelRef = (ModelRef *) (intptr_t) ptr;
        if (modelRef == nullptr) {
            LOGD("Cut failed: null model");
            return false;
        }
        Model& model = modelRef->model;
        if (i < 0 || i >= model.objects.size()) {
            LOGD("Cut failed: invalid model or index %d", i);
            return false;
        }
        
        auto object = model.objects[i];
        auto bbox = object->bounding_box_exact();
        LOGD("Cut invoked: index=%d zHeight=%f rotX=%f rotY=%f. Object bounds Z: min=%f, max=%f", i, zHeight, rotX, rotY, bbox.min.z(), bbox.max.z());
        
        Transform3d cut_matrix = Transform3d::Identity();
        cut_matrix.translate(Vec3d(0.0, 0.0, zHeight));
        cut_matrix.rotate(Eigen::AngleAxisd(rotY, Vec3d::UnitY()));
        cut_matrix.rotate(Eigen::AngleAxisd(rotX, Vec3d::UnitX()));
        
        ModelObjectCutAttributes attributes = ModelObjectCutAttribute::InvalidateCutInfo | ModelObjectCutAttribute::CreateDowels;
        if (keepUpper) attributes = attributes | ModelObjectCutAttribute::KeepUpper;
        if (keepLower) attributes = attributes | ModelObjectCutAttribute::KeepLower;
        
        Cut cut(object, 0, cut_matrix, attributes);
        const auto& cut_objects = cut.perform_with_plane();
        
        LOGD("Cut returned %zu objects", cut_objects.size());
        
        if (cut_objects.empty()) {
            return false;
        }
        
        bool isFirst = true;
        for (auto cut_obj : cut_objects) {
            if (isFirst && cut_objects.size() > 1) {
                cut_obj->translate(Vec3d(20.0, 20.0, 10.0));
                isFirst = false;
            }
            model.add_object(*cut_obj);
        }
        
        model.delete_object(i);
        
        return true;
    }

    // Split the selected object into separate objects (by connected mesh parts for a single-volume
    // object, or by volume for a multi-volume assembly). split() appends the new objects to the
    // model and preserves painting; we then drop the original. Returns the number of pieces, or 0
    // if there was nothing to split.
    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1split(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* modelRef = (ModelRef*) (intptr_t) ptr;
        if (modelRef == nullptr) return 0;
        Model& model = modelRef->model;
        if (i < 0 || i >= (int) model.objects.size()) return 0;
        size_t before = model.objects.size();
        ModelObjectPtrs new_objects;
        model.objects[i]->split(&new_objects, true);
        if (new_objects.size() <= 1) {
            // Nothing meaningful to split: remove anything split() appended, keep the original.
            while (model.objects.size() > before)
                model.delete_object(model.objects.size() - 1);
            return 0;
        }
        model.delete_object(i);
        return (jint) new_objects.size();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1translate(JNIEnv* env, jclass, jlong ptr, jint i, jdouble x, jdouble y, jdouble z) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        model->model.objects[i]->translate(x, y, z);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1ensure_1on_1bed(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        model->model.objects[i]->ensure_on_bed(false);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1scale(JNIEnv* env, jclass, jlong ptr, jint i, jdouble x, jdouble y, jdouble z) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        Vec3d factor(x, y, z);
        ModelVolumePtrs ptrs = model->model.objects[i]->volumes;
        for (int i = 0, c = ptrs.size(); i < c; i++) {
            ptrs[i]->set_scaling_factor(factor);
        }
        model->model.objects[i]->invalidate_bounding_box();
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1is_1left_1handed(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        return model->model.objects[i]->volumes[0]->is_left_handed();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1rotate(JNIEnv* env, jclass, jlong ptr, jint i, jdouble x, jdouble y, jdouble z) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        Vec3d vec(x, y, z);
        ModelVolumePtrs ptrs = model->model.objects[i]->volumes;
        for (int i = 0, c = ptrs.size(); i < c; i++) {
            Vec3d current_rotation = ptrs[i]->get_rotation();

            Eigen::Quaterniond q_current =
                    Eigen::AngleAxisd(current_rotation[2], Eigen::Vector3d::UnitZ()) *
                    Eigen::AngleAxisd(current_rotation[1], Eigen::Vector3d::UnitY()) *
                    Eigen::AngleAxisd(current_rotation[0], Eigen::Vector3d::UnitX());

            Eigen::Quaterniond q_delta =
                    Eigen::AngleAxisd(vec[0], Eigen::Vector3d::UnitX()) *
                    Eigen::AngleAxisd(vec[1], Eigen::Vector3d::UnitY()) *
                    Eigen::AngleAxisd(vec[2], Eigen::Vector3d::UnitZ());

            Eigen::Quaterniond q_result = q_delta * q_current;
            Eigen::Vector3d new_rotation = q_result.toRotationMatrix().eulerAngles(2, 1, 0);
            ptrs[i]->set_rotation(Vec3d(new_rotation[2], new_rotation[1], new_rotation[0]));
        }
        model->model.objects[i]->invalidate_bounding_box();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1flatten_1rotate(JNIEnv* env, jclass, jlong ptr, jint i, jlong surface_ptr) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        GLModelRef* surface = (GLModelRef*) (intptr_t) surface_ptr;

        const Vec3d& normal = surface->flatten_normal;
        ModelVolumePtrs ptrs = model->model.objects[i]->volumes;
        for (int i = 0, c = ptrs.size(); i < c; i++) {
            auto vol = ptrs[i];
            const Geometry::Transformation& old_transform = vol->get_transformation();
            const Vec3d tnormal = normal;
            const Transform3d rotation_matrix = Transform3d(Eigen::Quaterniond().setFromTwoVectors(tnormal, -Vec3d::UnitZ()));
            vol->set_transformation(old_transform.get_offset_matrix() * rotation_matrix * old_transform.get_matrix_no_offset());
        }
        model->model.objects[i]->invalidate_bounding_box();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1translate_1global(JNIEnv* env, jclass, jlong ptr, jdouble x, jdouble y, jdouble z) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        model->model.translate(x, y, z);
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1approx(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->model.objects[i]->bounding_box_approx().min.x();
        elements[1] = ref->model.objects[i]->bounding_box_approx().min.y();
        elements[2] = ref->model.objects[i]->bounding_box_approx().min.z();
        elements[3] = ref->model.objects[i]->bounding_box_approx().max.x();
        elements[4] = ref->model.objects[i]->bounding_box_approx().max.y();
        elements[5] = ref->model.objects[i]->bounding_box_approx().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1exact(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->model.objects[i]->bounding_box_exact().min.x();
        elements[1] = ref->model.objects[i]->bounding_box_exact().min.y();
        elements[2] = ref->model.objects[i]->bounding_box_exact().min.z();
        elements[3] = ref->model.objects[i]->bounding_box_exact().max.x();
        elements[4] = ref->model.objects[i]->bounding_box_exact().max.y();
        elements[5] = ref->model.objects[i]->bounding_box_exact().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1approx_1global(JNIEnv* env, jclass, jlong ptr) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->model.bounding_box_approx().min.x();
        elements[1] = ref->model.bounding_box_approx().min.y();
        elements[2] = ref->model.bounding_box_approx().min.z();
        elements[3] = ref->model.bounding_box_approx().max.x();
        elements[4] = ref->model.bounding_box_approx().max.y();
        elements[5] = ref->model.bounding_box_approx().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1exact_1global(JNIEnv* env, jclass, jlong ptr) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->model.bounding_box_exact().min.x();
        elements[1] = ref->model.bounding_box_exact().min.y();
        elements[2] = ref->model.bounding_box_exact().min.z();
        elements[3] = ref->model.bounding_box_exact().max.x();
        elements[4] = ref->model.bounding_box_exact().max.y();
        elements[5] = ref->model.bounding_box_exact().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jlongArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1create_1flatten_1planes(JNIEnv* env, jclass, jlong ptr, jint i) {
        auto model = (Model*)ptr;
        if (model == nullptr || i < 0 || i >= model->objects.size()) return nullptr;
        auto obj = model->objects[i];
        
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        const ModelObject* mo = ref->model.objects[i];
        TriangleMesh ch;
        Transform3d real_transform = Geometry::translation_transform(mo->bounding_box_exact().center());
        for (const ModelVolume* vol : mo->volumes) {
            if (vol->type() != ModelVolumeType::MODEL_PART)
                continue;
            TriangleMesh vol_ch = vol->get_convex_hull();
            vol_ch.transform(vol->get_matrix_no_offset());
            vol_ch.transform(real_transform);
            ch.merge(vol_ch);
        }
        ch = ch.convex_hull_3d();

        std::vector<PlaneData> m_planes;

        const Transform3d inst_matrix = mo->instances.front()->get_matrix_no_offset();

        // Following constants are used for discarding too small polygons.
        const float minimal_area = 5.f; // in square mm (world coordinates)
        const float minimal_side = 1.f; // mm

        const int                num_of_facets  = ch.facets_count();
        const std::vector<Vec3f> face_normals   = its_face_normals(ch.its);
        const std::vector<Vec3i32> face_neighbors = its_face_neighbors(ch.its);
        std::vector<int>         facet_queue(num_of_facets, 0);
        std::vector<bool>        facet_visited(num_of_facets, false);
        int                      facet_queue_cnt = 0;
        const stl_normal*        normal_ptr      = nullptr;
        int facet_idx = 0;
        while (true) {
            // Find next unvisited triangle:
            for (; facet_idx < num_of_facets; ++ facet_idx)
                if (!facet_visited[facet_idx]) {
                    facet_queue[facet_queue_cnt ++] = facet_idx;
                    facet_visited[facet_idx] = true;
                    normal_ptr = &face_normals[facet_idx];
                    m_planes.emplace_back();
                    break;
                }
            if (facet_idx == num_of_facets)
                break; // Everything was visited already

            while (facet_queue_cnt > 0) {
                int facet_idx = facet_queue[-- facet_queue_cnt];
                const stl_normal& this_normal = face_normals[facet_idx];
                if (std::abs(this_normal(0) - (*normal_ptr)(0)) < 0.001 && std::abs(this_normal(1) - (*normal_ptr)(1)) < 0.001 && std::abs(this_normal(2) - (*normal_ptr)(2)) < 0.001) {
                    const Vec3i32 face = ch.its.indices[facet_idx];
                    for (int j=0; j<3; ++j)
                        m_planes.back().vertices.emplace_back(ch.its.vertices[face[j]].cast<double>());

                    facet_visited[facet_idx] = true;
                    for (int j = 0; j < 3; ++ j)
                        if (int neighbor_idx = face_neighbors[facet_idx][j]; neighbor_idx >= 0 && ! facet_visited[neighbor_idx])
                            facet_queue[facet_queue_cnt ++] = neighbor_idx;
                }
            }
            m_planes.back().normal = normal_ptr->cast<double>();

            Pointf3s& verts = m_planes.back().vertices;
            // Now we'll transform all the points into world coordinates, so that the areas, angles and distances
            // make real sense.
            verts = transform(verts, inst_matrix);

            // if this is a just a very small triangle, remove it to speed up further calculations (it would be rejected later anyway):
            if (verts.size() == 3 &&
                ((verts[0] - verts[1]).norm() < minimal_side
                 || (verts[0] - verts[2]).norm() < minimal_side
                 || (verts[1] - verts[2]).norm() < minimal_side))
                m_planes.pop_back();
        }

        // Let's prepare transformation of the normal vector from mesh to instance coordinates.
        const Matrix3d normal_matrix = inst_matrix.matrix().block(0, 0, 3, 3).inverse().transpose();

        // Now we'll go through all the polygons, transform the points into xy plane to process them:
        for (unsigned int polygon_id=0; polygon_id < m_planes.size(); ++polygon_id) {
            Pointf3s& polygon = m_planes[polygon_id].vertices;
            const Vec3d& normal = m_planes[polygon_id].normal;

            // transform the normal according to the instance matrix:
            const Vec3d normal_transformed = normal_matrix * normal;

            // We are going to rotate about z and y to flatten the plane
            Eigen::Quaterniond q;
            Transform3d m = Transform3d::Identity();
            m.matrix().block(0, 0, 3, 3) = q.setFromTwoVectors(normal_transformed, Vec3d::UnitZ()).toRotationMatrix();
            polygon = transform(polygon, m);

            // Now to remove the inner points. We'll misuse Geometry::convex_hull for that, but since
            // it works in fixed point representation, we will rescale the polygon to avoid overflows.
            // And yes, it is a nasty thing to do. Whoever has time is free to refactor.
            Vec3d bb_size = BoundingBoxf3(polygon).size();
            float sf = std::min(1./bb_size(0), 1./bb_size(1));
            Transform3d tr = Geometry::scale_transform({ sf, sf, 1.f });
            polygon = transform(polygon, tr);
            polygon = Slic3r::Geometry::convex_hull(polygon);
            polygon = transform(polygon, tr.inverse());

            // Calculate area of the polygons and discard ones that are too small
            float& area = m_planes[polygon_id].area;
            area = 0.f;
            for (unsigned int i = 0; i < polygon.size(); i++) // Shoelace formula
                area += polygon[i](0)*polygon[i + 1 < polygon.size() ? i + 1 : 0](1) - polygon[i + 1 < polygon.size() ? i + 1 : 0](0)*polygon[i](1);
            area = 0.5f * std::abs(area);

            bool discard = false;
            if (area < minimal_area)
                discard = true;
            else {
                // We also check the inner angles and discard polygons with angles smaller than the following threshold
                const double angle_threshold = ::cos(10.0 * (double)PI / 180.0);

                for (unsigned int i = 0; i < polygon.size(); ++i) {
                    const Vec3d& prec = polygon[(i == 0) ? polygon.size() - 1 : i - 1];
                    const Vec3d& curr = polygon[i];
                    const Vec3d& next = polygon[(i == polygon.size() - 1) ? 0 : i + 1];

                    if ((prec - curr).normalized().dot((next - curr).normalized()) > angle_threshold) {
                        discard = true;
                        break;
                    }
                }
            }

            if (discard) {
                m_planes[polygon_id--] = std::move(m_planes.back());
                m_planes.pop_back();
                continue;
            }

            // We will shrink the polygon a little bit so it does not touch the object edges:
            Vec3d centroid = std::accumulate(polygon.begin(), polygon.end(), Vec3d(0.0, 0.0, 0.0));
            centroid /= (double)polygon.size();
            for (auto& vertex : polygon)
                vertex = 0.9f*vertex + 0.1f*centroid;

            // Polygon is now simple and convex, we'll round the corners to make them look nicer.
            // The algorithm takes a vertex, calculates middles of respective sides and moves the vertex
            // towards their average (controlled by 'aggressivity'). This is repeated k times.
            // In next iterations, the neighbours are not always taken at the middle (to increase the
            // rounding effect at the corners, where we need it most).
            const unsigned int k = 10; // number of iterations
            const float aggressivity = 0.2f;  // agressivity
            const unsigned int N = polygon.size();
            std::vector<std::pair<unsigned int, unsigned int>> neighbours;
            if (k != 0) {
                Pointf3s points_out(2*k*N); // vector long enough to store the future vertices
                for (unsigned int j=0; j<N; ++j) {
                    points_out[j*2*k] = polygon[j];
                    neighbours.push_back(std::make_pair((int)(j*2*k-k) < 0 ? (N-1)*2*k+k : j*2*k-k, j*2*k+k));
                }

                for (unsigned int i=0; i<k; ++i) {
                    // Calculate middle of each edge so that neighbours points to something useful:
                    for (unsigned int j=0; j<N; ++j)
                        if (i==0)
                            points_out[j*2*k+k] = 0.5f * (points_out[j*2*k] + points_out[j==N-1 ? 0 : (j+1)*2*k]);
                        else {
                            float r = 0.2+0.3/(k-1)*i; // the neighbours are not always taken in the middle
                            points_out[neighbours[j].first] = r*points_out[j*2*k] + (1-r) * points_out[neighbours[j].first-1];
                            points_out[neighbours[j].second] = r*points_out[j*2*k] + (1-r) * points_out[neighbours[j].second+1];
                        }
                    // Now we have a triangle and valid neighbours, we can do an iteration:
                    for (unsigned int j=0; j<N; ++j)
                        points_out[2*k*j] = (1-aggressivity) * points_out[2*k*j] +
                                            aggressivity*0.5f*(points_out[neighbours[j].first] + points_out[neighbours[j].second]);

                    for (auto& n : neighbours) {
                        ++n.first;
                        --n.second;
                    }
                }
                polygon = points_out; // replace the coarse polygon with the smooth one that we just created
            }


            // Raise a bit above the object surface to avoid flickering:
            for (auto& b : polygon)
                b(2) += 0.1f;

            // Transform back to 3D (and also back to mesh coordinates)
            polygon = transform(polygon, inst_matrix.inverse() * m.inverse());
        }

        // We'll sort the planes by area and only keep the 254 largest ones (because of the picking pass limitations):
        std::sort(m_planes.rbegin(), m_planes.rend(), [](const PlaneData& a, const PlaneData& b) { return a.area < b.area; });
        m_planes.resize(std::min((int)m_planes.size(), 254));

        jlongArray arr = env->NewLongArray(m_planes.size());

        // And finally create respective VBOs. The polygon is convex with
        // the vertices in order, so triangulation is trivial.
        for (int i = 0, s = m_planes.size(); i < s; i++) {
            auto& plane = m_planes[i];
            indexed_triangle_set its;
            its.vertices.reserve(plane.vertices.size());
            its.indices.reserve(plane.vertices.size() / 3);
            for (size_t i = 0; i < plane.vertices.size(); ++i) {
                its.vertices.emplace_back((Vec3f)plane.vertices[i].cast<float>());
            }
            for (size_t i = 1; i < plane.vertices.size() - 1; ++i) {
                its.indices.emplace_back(0, i, i + 1); // triangle fan
            }

            if (Geometry::Transformation(inst_matrix).is_left_handed()) {
                // we need to swap face normals in case the object is mirrored
                // for the raycaster to work properly
                for (stl_triangle_vertex_indices& face : its.indices) {
                    if (its_face_normal(its, face).cast<double>().dot(plane.normal) < 0.0)
                        std::swap(face[1], face[2]);
                }
            }
            GLModelRef* ref = new GLModelRef();
            ref->mesh = TriangleMesh(its);
            ref->model.init_from(its);
            ref->flatten_normal = plane.normal;
            ref->emesh = new AABBMesh(its, true);
            ref->normals = its_face_normals(its);

            jlong ptr = reinterpret_cast<jlong>(ref);
            env->SetLongArrayRegion(arr, i, 1, &ptr);

            // vertices are no more needed, clear memory
            plane.vertices = std::vector<Vec3d>();
        }
        m_planes.clear();
        return arr;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1auto_1orient(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[i];
        orientation::orient(obj);
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1is_1big_1object(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[i];
        return obj->volumes.size() == 1 && obj->volumes.front()->mesh().its.indices.size() >= 500000;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1extruder(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[i];
        return obj->config.has("extruder") ? obj->config.opt_int("extruder") : -1;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1set_1extruder(JNIEnv* env, jclass, jlong ptr, jint i, jint extruder) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[i];
        obj->config.set("extruder", extruder);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1apply_1adaptive_1layer_1height(JNIEnv* env, jclass, jlong ptr, jint i, jstring configPath, jfloat qualityFactor) {
        try {
            ModelRef* model = (ModelRef*) (intptr_t) ptr;
            if (i < 0 || i >= (int) model->model.objects.size()) return;
            ModelObject* obj = model->model.objects[i];

            DynamicPrintConfig config;
            const char *chars = env->GetStringUTFChars(configPath, JNI_FALSE);
            config.load(std::string(chars), ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(configPath, chars);
            config.normalize_fdm();

            SlicingParameters slicing_params = PrintObject::slicing_parameters(config, *obj, 0.0f, Vec3d::Zero());
            std::vector<double> profile = layer_height_profile_adaptive(slicing_params, *obj, qualityFactor);
            obj->layer_height_profile.set(profile);
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1clear_1adaptive_1layer_1height(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (i >= 0 && i < (int) model->model.objects.size()) {
            model->model.objects[i]->layer_height_profile.clear();
        }
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1has_1adaptive_1layer_1height(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (i >= 0 && i < (int) model->model.objects.size()) {
            return !model->model.objects[i]->layer_height_profile.empty();
        }
        return false;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1emboss_1text(
        JNIEnv* env, jclass, jlong ptr, jint i, jstring fontPath, jstring textStr, jfloat size, jfloat depth, jint type,
        jdouble px, jdouble py, jdouble pz, jdouble nx, jdouble ny, jdouble nz) {
        try {
            ModelRef* model = (ModelRef*) (intptr_t) ptr;
            if (i < 0 || i >= (int) model->model.objects.size()) return;
            ModelObject* obj = model->model.objects[i];

            const char *font_path_chars = env->GetStringUTFChars(fontPath, JNI_FALSE);
            std::string font_path(font_path_chars);
            env->ReleaseStringUTFChars(fontPath, font_path_chars);

            const char *text_chars = env->GetStringUTFChars(textStr, JNI_FALSE);
            std::string text(text_chars);
            env->ReleaseStringUTFChars(textStr, text_chars);

            // 1) Load font
            std::unique_ptr<Emboss::FontFile> font_file = Emboss::create_font_file(font_path.c_str());
            if (!font_file) {
                env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to load font file");
                return;
            }
            Emboss::FontFileWithCache font_with_cache(std::move(font_file));

            // 2) Generate 2D shapes
            FontProp font_prop(size);
            HealedExPolygons healed_shapes = Emboss::text2shapes(font_with_cache, text.c_str(), font_prop);
            if (healed_shapes.expolygons.empty()) {
                env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Generated text shape is empty");
                return;
            }

            // 3) Create transformation and projection onto surface
            Vec3d position(px, py, pz);
            Vec3d normal(nx, ny, nz);
            Transform3d matrix = Emboss::create_transformation_onto_surface(position, normal);
            Emboss::OrthoProject projection(matrix, normal * depth);

            // 4) Convert to 3D model (indexed_triangle_set)
            indexed_triangle_set its = Emboss::polygons2model(healed_shapes.expolygons, projection);

            // 5) Add volume to model object
            TriangleMesh mesh(std::move(its));
            ModelVolumeType vol_type = static_cast<ModelVolumeType>(type);
            obj->add_volume(std::move(mesh), vol_type, true);
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
        }
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1slice(JNIEnv* env, jclass, jlong ptr, jstring configPath, jstring path, jobject listener, jint numFilaments, jintArray colorsArr, jint calibMode, jdouble calibStart, jdouble calibEnd, jdouble calibStep) {
        try {
            ModelRef* model = (ModelRef*) (intptr_t) ptr;

            Print print;
            DynamicPrintConfig config;
            const char *chars = env->GetStringUTFChars(configPath, JNI_FALSE);
            config.load(std::string(chars), ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(configPath, chars);
            config.normalize_fdm();

            // The app's bed temperatures live in the hot-plate slots (legacy bed_temperature keys
            // migrate to hot_plate_temp); the engine reads the slot picked by curr_bed_type, whose
            // compiled default is Cool Plate — which would silently ignore those values.
            if (config.option("curr_bed_type", false) == nullptr)
                config.set_key_value("curr_bed_type", new ConfigOptionEnum<BedType>(btPEI));

            // An imported project config can itself declare multiple filaments (e.g. a Bambu 3MF's
            // project_settings carries 8-entry filament_colour/filament_diameter vectors) while other
            // per-filament vectors it carries stay single-entry (filament_map, filament_self_index,
            // filament_is_support, ...). Desktop's preset layer keeps these consistent; here we must
            // do it ourselves: adopt the config's filament count so the consistency fixups below run
            // even when the app didn't request multicolor painting. Otherwise the engine's wipe
            // tower / flush-volume ordering indexes the short vectors out of bounds → SIGSEGV.
            {
                int cfgFilaments = 0;
                if (auto* fc = dynamic_cast<const ConfigOptionStrings*>(config.option("filament_colour", false)))
                    cfgFilaments = std::max(cfgFilaments, (int) fc->values.size());
                if (auto* fd = dynamic_cast<const ConfigOptionFloats*>(config.option("filament_diameter", false)))
                    cfgFilaments = std::max(cfgFilaments, (int) fd->values.size());
                if (cfgFilaments > numFilaments) {
                    __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "config declares %d filaments (app requested %d); normalizing", cfgFilaments, (int) numFilaments);
                    numFilaments = cfgFilaments;
                }
            }

            // Multi-color: declare N filaments (resizing all filament vectors) + their colors so the
            // engine runs MMU segmentation on the painted facets and emits tool changes.
            if (numFilaments > 1) {
                // The painting may reference filament indices beyond the palette (e.g. leftover paint
                // from a larger palette). Cover the highest painted filament so export never indexes
                // a per-filament vector out of range.
                int maxPaintedState = 0;
                for (auto* mo : model->model.objects) {
                    __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "object: %s, layer_config_ranges.size=%zu", mo->name.c_str(), mo->layer_config_ranges.size());
                    for (auto* v : mo->volumes) {
                        const auto& us = v->mmu_segmentation_facets.get_data().used_states;
                        int volMax = 0;
                        for (int i = (int) us.size() - 1; i >= 1; --i) { if (us[i]) { if (i > volMax) volMax = i; break; } }
                        __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "volume: %s, maxPainted=%d", v->name.c_str(), volMax);
                        if (volMax > maxPaintedState) maxPaintedState = volMax;
                    }
                }
                if (maxPaintedState > numFilaments) numFilaments = maxPaintedState;

                config.set_num_filaments((unsigned int) numFilaments);

                // Resize ONLY per-filament vectors. We EXCLUDE printer-extruder vectors like 
                // nozzle_diameter to avoid SIGSEGV in DynamicPrintConfig::update_values_to_printer_extruders.
                // In SEMM mode, the engine knows to use nozzle_diameter[0] for all filaments.
                auto cloneToN = [&](ConfigOption* opt) {
                    if (auto* o = dynamic_cast<ConfigOptionFloats*>(opt))                { if (!o->values.empty()) o->values.resize(numFilaments, o->values[0]); }
                    else if (auto* o = dynamic_cast<ConfigOptionInts*>(opt))             { if (!o->values.empty()) o->values.resize(numFilaments, o->values[0]); }
                    else if (auto* o = dynamic_cast<ConfigOptionStrings*>(opt))          { if (!o->values.empty()) o->values.resize(numFilaments, o->values[0]); }
                    else if (auto* o = dynamic_cast<ConfigOptionBools*>(opt))            { if (!o->values.empty()) o->values.resize(numFilaments, o->values[0]); }
                    else if (auto* o = dynamic_cast<ConfigOptionPercents*>(opt))         { if (!o->values.empty()) o->values.resize(numFilaments, o->values[0]); }
                    else if (auto* o = dynamic_cast<ConfigOptionFloatsOrPercents*>(opt)) { if (!o->values.empty()) o->values.resize(numFilaments, o->values[0]); }
                };

                // Filament options that don't start with "filament_" prefix.
                static const std::set<std::string> extraFilamentOpts = {
                    "nozzle_temperature", "nozzle_temperature_initial_layer",
                    "nozzle_temperature_range_low", "nozzle_temperature_range_high",
                    "cool_plate_temp", "cool_plate_temp_initial_layer",
                    "eng_plate_temp", "eng_plate_temp_initial_layer",
                    "hot_plate_temp", "hot_plate_temp_initial_layer",
                    "textured_plate_temp", "textured_plate_temp_initial_layer",
                    "chamber_temperature", "chamber_minimal_temperature",
                    "full_fan_speed_layer", "bridge_fan_speed", "max_fan_speed", "min_fan_speed",
                    "slow_down_layer_time", "fan_below_layer_time"
                };

                for (const std::string& key : config.keys()) {
                    bool isFilament = key.rfind("filament_", 0) == 0 || extraFilamentOpts.count(key) > 0;
                    if (isFilament) {
                        // Skip identity/palette fields during cloning (set explicitly below).
                        if (key == "filament_settings_id" || key == "filament_self_index" || key == "filament_colour" || 
                            key == "filament_multi_colour" || key == "filament_map") continue;
                        ConfigOption* opt = config.option(key, false);
                        if (opt && opt->is_vector()) cloneToN(opt);
                    }
                }

                // Override the colors with the user's palette (padded to N by repeating the last color).
                if (colorsArr != nullptr) {
                    jsize n = env->GetArrayLength(colorsArr);
                    jint* c = env->GetIntArrayElements(colorsArr, JNI_FALSE);
                    std::vector<std::string> colorStrs;
                    std::vector<std::string> colorTypes;
                    for (int i = 0; i < numFilaments; ++i) {
                        int src = (i < n) ? i : (n > 0 ? n - 1 : 0);
                        char buf[8];
                        snprintf(buf, sizeof(buf), "#%06X", (n > 0 ? c[src] : 0xFFFFFF) & 0xFFFFFF);
                        colorStrs.emplace_back(buf);
                        colorTypes.emplace_back("1"); // default color type
                    }
                    env->ReleaseIntArrayElements(colorsArr, c, JNI_ABORT);
                    if (!colorStrs.empty()) {
                        config.set_key_value("filament_colour", new ConfigOptionStrings(colorStrs));
                        config.set_key_value("filament_multi_colour", new ConfigOptionStrings(colorStrs));
                        config.set_key_value("filament_colour_type", new ConfigOptionStrings(colorTypes));
                    }
                }

                // filament_self_index must be 1..N. If all filaments have the same index (e.g. 1),
                // the engine merges them and emits no tool changes.
                {
                    std::vector<int> selfIdx(numFilaments);
                    for (int i = 0; i < numFilaments; ++i) selfIdx[i] = i + 1;
                    config.set_key_value("filament_self_index", new ConfigOptionInts(selfIdx));
                }

                // filament_settings_id should also be unique to prevent merging.
                {
                    std::vector<std::string> ids;
                    for (int i = 0; i < numFilaments; ++i) {
                        char buf[32];
                        snprintf(buf, sizeof(buf), "Filament %d", i + 1);
                        ids.emplace_back(buf);
                    }
                    config.set_key_value("filament_settings_id", new ConfigOptionStrings(ids));
                }

                // filament_map must map all virtual filaments back to the physical extruder (1).
                // Without this, update_values_to_printer_extruders crashes if it tries to index 
                // beyond the physical extruder count.
                {
                    std::vector<int> filamentMap(numFilaments, 1);
                    config.set_key_value("filament_map", new ConfigOptionInts(filamentMap));
                }

                // Flush (purge) volumes are project-scoped options that preset INIs never carry,
                // and the engine's default flush_volumes_matrix is sized for exactly 4 filaments.
                // GCode::append_full_config requires matrix.size() == N^2 * flush_multiplier.size()
                // and throws otherwise, so rebuild the flush vectors whenever the sizes disagree.
                {
                    // The engine (ToolOrdering::reorder_extruders_for_minimum_flush_volume,
                    // Print::_make_wipe_tower) treats flush_volumes_matrix as `nozzle_diameter.size()`
                    // blocks, each a `filament_colour.size()` x `filament_colour.size()` matrix. If our
                    // matrix is sized differently the engine slices past the end → SIGSEGV. Size it to
                    // exactly that layout, keyed off filament_colour so the per-nozzle dimension matches
                    // the engine's `number_of_extruders`.
                    size_t n = (size_t) numFilaments;
                    if (auto* fc = dynamic_cast<const ConfigOptionStrings*>(config.option("filament_colour", false)))
                        n = std::max(n, fc->values.size());

                    size_t nozzles = 1;
                    if (auto* nd = dynamic_cast<const ConfigOptionFloats*>(config.option("nozzle_diameter", false)))
                        if (!nd->values.empty()) nozzles = nd->values.size();

                    // Engine default volumes: 280mm^3 between distinct filaments, 0 on the diagonal.
                    std::vector<double> matrix(n * n * nozzles, 280.);
                    for (size_t z = 0; z < nozzles; ++z)
                        for (size_t i = 0; i < n; ++i)
                            matrix[z * n * n + i * n + i] = 0.;
                    config.set_key_value("flush_volumes_matrix", new ConfigOptionFloats(matrix));

                    // flush_multiplier is indexed per nozzle; one entry per nozzle.
                    auto* fm = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_multiplier", false));
                    if (fm == nullptr || fm->values.size() != nozzles)
                        config.set_key_value("flush_multiplier", new ConfigOptionFloats(std::vector<double>(nozzles, 0.3)));

                    auto* fv = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_volumes_vector", false));
                    if (fv == nullptr || fv->values.size() != n * 2)
                        config.set_key_value("flush_volumes_vector", new ConfigOptionFloats(std::vector<double>(n * 2, 140.)));
                }

                const ConfigOption* nd = config.option("nozzle_diameter");
                if (nd && static_cast<const ConfigOptionFloats*>(nd)->values.size() <= 1) {
                    config.set_key_value("single_extruder_multi_material", new ConfigOptionBool(true));
                    if (config.option("single_extruder_multi_material_priming", false) == nullptr) {
                        config.set_key_value("single_extruder_multi_material_priming", new ConfigOptionBool(true));
                    }
                }

                // Enable a prime/wipe tower for reliable filament changes.
                if (config.option("enable_prime_tower", false) == nullptr) {
                    config.set_key_value("enable_prime_tower", new ConfigOptionBool(true));
                }

                int fdSize = 0;
                if (auto* fd = dynamic_cast<const ConfigOptionFloats*>(config.option("filament_diameter", false))) fdSize = (int) fd->values.size();
                int fcSize = 0, mxSize = 0, ndSize = 0, fmSize = 0;
                if (auto* o = dynamic_cast<const ConfigOptionStrings*>(config.option("filament_colour", false))) fcSize = (int) o->values.size();
                if (auto* o = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_volumes_matrix", false))) mxSize = (int) o->values.size();
                if (auto* o = dynamic_cast<const ConfigOptionFloats*>(config.option("nozzle_diameter", false))) ndSize = (int) o->values.size();
                if (auto* o = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_multiplier", false))) fmSize = (int) o->values.size();
                __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "multicolor slice: numFilaments=%d maxPainted=%d filament_diameter.size=%d filament_colour.size=%d flush_matrix.size=%d nozzle_diameter.size=%d flush_multiplier.size=%d", (int) numFilaments, maxPaintedState, fdSize, fcSize, mxSize, ndSize, fmSize);
            }

            // Always normalize the purge/flush matrix to the engine's expected layout:
            // nozzle_diameter.size() blocks, each filament_colour.size() x filament_colour.size().
            // Imported project configs (e.g. a Bambu 3MF) can carry a wipe tower with a mis-sized
            // matrix even when the app's multicolor path above didn't run, which makes
            // _make_wipe_tower / reorder_extruders_for_minimum_flush_volume index out of bounds.
            {
                size_t fc = 0;
                if (auto* o = dynamic_cast<const ConfigOptionStrings*>(config.option("filament_colour", false)))
                    fc = o->values.size();
                if (fc > 1) {
                    size_t nozzles = 1;
                    if (auto* nd = dynamic_cast<const ConfigOptionFloats*>(config.option("nozzle_diameter", false)))
                        if (!nd->values.empty()) nozzles = nd->values.size();

                    auto* mx = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_volumes_matrix", false));
                    if (mx == nullptr || mx->values.size() != fc * fc * nozzles) {
                        __android_log_print(ANDROID_LOG_WARN, "BeamPaint",
                            "normalizing flush matrix: filament_colour=%zu nozzles=%zu old_matrix=%d -> %zu",
                            fc, nozzles, mx ? (int) mx->values.size() : -1, fc * fc * nozzles);
                        std::vector<double> matrix(fc * fc * nozzles, 280.);
                        for (size_t z = 0; z < nozzles; ++z)
                            for (size_t i = 0; i < fc; ++i)
                                matrix[z * fc * fc + i * fc + i] = 0.;
                        config.set_key_value("flush_volumes_matrix", new ConfigOptionFloats(matrix));
                    }
                    auto* fm = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_multiplier", false));
                    if (fm == nullptr || fm->values.size() != nozzles)
                        config.set_key_value("flush_multiplier", new ConfigOptionFloats(std::vector<double>(nozzles, 0.3)));
                    auto* fv = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_volumes_vector", false));
                    if (fv == nullptr || fv->values.size() != fc * 2)
                        config.set_key_value("flush_volumes_vector", new ConfigOptionFloats(std::vector<double>(fc * 2, 140.)));
                }
            }

            for (auto* mo : model->model.objects) {
                print.auto_assign_extruders(mo);
            }

            // OrcaSlicer incorrectly suppresses standard tool changes (T0, T1, etc.) if it thinks
            // the printer is a Bambu Lab machine (is_BBL_printer == true). We force it to false
            // to ensure standard G-code emission for regular Klipper/Marlin printers.
            print.is_BBL_printer() = false;

            __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "step: assigned extruders, validating config");

            // OrcaSlicer's config.validate() returns a map of option-key -> error message
            // instead of a single string; flatten it into one message for the Java side.
            std::map<std::string, std::string> config_errors = config.validate();
            if (!config_errors.empty()) {
                std::string err;
                for (const auto& kv : config_errors) {
                    if (!err.empty()) err += "\n";
                    err += kv.second;
                }
                env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), err.c_str());
                return 0;
            }
            __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "step: config valid, applying");
            print.apply(model->model, config);

            // OrcaSlicer calibration: when a calib mode is requested, set the params on the print so
            // the engine generates the calibration test (e.g. PA line pattern) instead of a normal print.
            if (calibMode != 0) {
                Slic3r::Calib_Params cp;
                cp.mode = static_cast<Slic3r::CalibMode>(calibMode);
                cp.start = calibStart;
                cp.end = calibEnd;
                cp.step = calibStep;
                cp.print_numbers = true;
                print.set_calib_params(cp);
            }

            __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "step: applied, print.validate");

            // Print::validate() now returns a StringObjectException whose message is in .string.
            std::string err = print.validate().string;
            if (!err.empty()) {
                env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), err.c_str());
                return 0;
            }

            std::thread::id id = std::this_thread::get_id();

            print.set_status_callback([&id, &listener](const Slic3r::PrintBase::SlicingStatus &s) {
                bool needAttach = id != std::this_thread::get_id();

                JNIEnv* e;
                if (staticVM->GetEnv(reinterpret_cast<void **>(&e), JNI_VERSION_1_6) != JNI_OK) {
                    return;
                }
                if (needAttach) {
                    JavaVMAttachArgs args;
                    args.name = nullptr;
                    args.group = nullptr;
                    args.version = JNI_VERSION_1_6;
                    staticVM->AttachCurrentThread(&e, &args);
                }
                e->CallVoidMethod(listener, sliceListenerOnProgress, s.percent, e->NewStringUTF(s.text.c_str()));
                if (needAttach) {
                    staticVM->DetachCurrentThread();
                }
            });
            __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "step: print.process");
            print.process();
            __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "step: processed, export_gcode");

            chars = env->GetStringUTFChars(path, JNI_FALSE);
            GCodeResultRef* resultRef = new GCodeResultRef();
            print.export_gcode(std::string(chars), &resultRef->result, nullptr);
            __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "step: exported gcode");
            env->ReleaseStringUTFChars(path, chars);

            resultRef->name = print.output_filename(model->base_name);

            return (jlong) (intptr_t) resultRef;
        } catch (const std::exception& e) {
            __android_log_print(ANDROID_LOG_ERROR, "BeamPaint", "slice exception type=%s what=%s", typeid(e).name(), e.what());
            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), e.what());
            return 0;
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1export_13mf(JNIEnv* env, jclass, jlong ptr, jstring configPath, jstring path) {
        auto model = reinterpret_cast<ModelRef*>(ptr);

        try {
            DynamicPrintConfig config;
            const char *chars = env->GetStringUTFChars(configPath, JNI_FALSE);
            config.load(std::string(chars), ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(configPath, chars);
            config.normalize_fdm();

            const char *pathChars = env->GetStringUTFChars(path, JNI_FALSE);
            Slic3r::store_3mf(pathChars, &model->model, &config, false, nullptr, false);
            env->ReleaseStringUTFChars(path, pathChars);
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), e.what());
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1release(JNIEnv* env, jclass, jlong ptr) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        delete model;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1load_1file(JNIEnv* env, jclass, jstring path, jstring name) {
        GCodeResultRef* ref = new GCodeResultRef();

        GCodeProcessor processor;
        try {
            const char* chars = env->GetStringUTFChars(path, JNI_FALSE);
            const char* nameChars = env->GetStringUTFChars(name, JNI_FALSE);
            ref->name = std::string(nameChars);

            // process_file()'s second argument is now a cancel callback (std::function<void()>),
            // not a float progress callback.
            processor.process_file(chars, []() {
                // TODO: Support cancellation
            });
            ref->result = std::move(processor.extract_result());

            env->ReleaseStringUTFChars(path, chars);
            env->ReleaseStringUTFChars(name, nameChars);

            return (jlong) (intptr_t) ref;
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
            return 0;
        }
    }

    JNIEXPORT jstring JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1recommended_1name(JNIEnv* env, jclass, jlong ptr) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;
        return env->NewStringUTF(ref->name.c_str());
    }

    // Maps a Java-side role index (matching libvgcode::EGCodeExtrusionRole ordering) to the
    // engine's Slic3r::ExtrusionRole, which is the key type of used_filaments_per_role.
    ExtrusionRole mapGCodeRole(int index) {
        ExtrusionRole gRole;
        switch (index) {
            default:
            case 0:
                gRole = erNone;
                break;
            case 1:
                gRole = erPerimeter;
                break;
            case 2:
                gRole = erExternalPerimeter;
                break;
            case 3:
                gRole = erOverhangPerimeter;
                break;
            case 4:
                gRole = erInternalInfill;
                break;
            case 5:
                gRole = erSolidInfill;
                break;
            case 6:
                gRole = erTopSolidInfill;
                break;
            case 7:
                gRole = erIroning;
                break;
            case 8:
                gRole = erBridgeInfill;
                break;
            case 9:
                gRole = erGapFill;
                break;
            case 10:
                gRole = erSkirt;
                break;
            case 11:
                gRole = erSupportMaterial;
                break;
            case 12:
                gRole = erSupportMaterialInterface;
                break;
            case 13:
                gRole = erWipeTower;
                break;
            case 14:
                gRole = erCustom;
                break;
        }
        return gRole;
    }

    JNIEXPORT jdouble JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1used_1filament_1mm(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;

        std::pair<double, double> info = ref->result.print_statistics.used_filaments_per_role.find(mapGCodeRole(role))->second;
        return info.first * 1000.0 / 25.4;
    }

    JNIEXPORT jdouble JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1used_1filament_1g(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;

        std::pair<double, double> info = ref->result.print_statistics.used_filaments_per_role.find(mapGCodeRole(role))->second;
        return info.second;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1release(JNIEnv* env, jclass, jlong ptr) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;
        delete ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1init_1from_1texts(JNIEnv* env, jclass, jstring name, jstring fsText, jstring vsText) {
        const char* nameChars = env->GetStringUTFChars(name, JNI_FALSE);
        const char* fsChars = env->GetStringUTFChars(fsText, JNI_FALSE);
        const char* vsChars = env->GetStringUTFChars(vsText, JNI_FALSE);
        GLShaderProgram::ShaderSources sources = {};
        sources[static_cast<size_t>(GLShaderProgram::EShaderType::Vertex)] = std::string(vsChars);
        sources[static_cast<size_t>(GLShaderProgram::EShaderType::Fragment)] = std::string(fsChars);
        ShaderRef* ref = new ShaderRef();
        ref->program.init_from_texts(std::string(nameChars), sources);

        env->ReleaseStringUTFChars(name, nameChars);
        env->ReleaseStringUTFChars(fsText, fsChars);
        env->ReleaseStringUTFChars(vsText, vsChars);

        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1create(JNIEnv* env, jclass) {
        GLModelRef* ref = new GLModelRef();
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1raycast_1data(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->emesh = new AABBMesh(ref->mesh, true);
        ref->normals = its_face_normals(ref->mesh.its);
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1raycast_1closest_1hit(JNIEnv* env, jclass, jlong ptr, jdoubleArray pointArr, jdoubleArray directionArr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        jdouble* point = env->GetDoubleArrayElements(pointArr, JNI_FALSE);
        jdouble* direction = env->GetDoubleArrayElements(directionArr, JNI_FALSE);

        Vec3d point3d(point);
        Vec3d direction3d(direction);

        Vec3d point_positive = point3d - direction3d;
        Vec3d point_negative = point3d + direction3d;

        std::vector<AABBMesh::hit_result> hits = ref->emesh->query_ray_hits(point_positive, direction3d);
        jdoubleArray arr = env->NewDoubleArray(hits.size() * 6);
        for (int i = 0; i < hits.size(); ++i) {
            const AABBMesh::hit_result& hit = hits[i];
            env->SetDoubleArrayRegion(arr, i * 6, 6, hit.position().data());
        }

        env->ReleaseDoubleArrayElements(pointArr, point, JNI_ABORT);
        env->ReleaseDoubleArrayElements(directionArr, direction, JNI_ABORT);

        return arr;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1from_1model(JNIEnv* env, jclass, jlong ptr, jlong model) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ModelRef* mRef = (ModelRef*) (intptr_t) model;
        ref->mesh = mRef->model.mesh();
        ref->model.init_from(ref->mesh.its);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1from_1model_1object(JNIEnv* env, jclass, jlong ptr, jlong model, jint i) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ModelRef* mRef = (ModelRef*) (intptr_t) model;
        ref->mesh = mRef->model.objects[i]->mesh();
        ref->model.init_from(ref->mesh.its);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1set_1color(JNIEnv* env, jclass, jlong ptr, jfloat red, jfloat green, jfloat blue, jfloat alpha) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.set_color(ColorRGBA(red, green, blue, alpha));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1stilized_1arrow(JNIEnv* env, jclass, jlong ptr, jfloat tip_radius, jfloat tip_length, jfloat stem_radius, jfloat stem_length) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.init_from(stilized_arrow(16, tip_radius, tip_length, stem_radius, stem_length));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1background_1triangles(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.reset();

        GLModel::Geometry init_data;
        init_data.format = { GLModel::Geometry::EPrimitiveType::Triangles, GLModel::Geometry::EVertexLayout::P2T2 };
        init_data.reserve_vertices(4);
        init_data.reserve_indices(6);

        // vertices
        init_data.add_vertex(Vec2f(-1.0f, -1.0f), Vec2f(0.0f, 0.0f));
        init_data.add_vertex(Vec2f(1.0f, -1.0f),  Vec2f(1.0f, 0.0f));
        init_data.add_vertex(Vec2f(1.0f, 1.0f),   Vec2f(1.0f, 1.0f));
        init_data.add_vertex(Vec2f(-1.0f, 1.0f),  Vec2f(0.0f, 1.0f));

        // indices
        init_data.add_triangle(0, 1, 2);
        init_data.add_triangle(2, 3, 0);

        ref->model.init_from(std::move(init_data));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1box(JNIEnv* env, jclass, jlong ptr, jfloat width, jfloat depth, jfloat height) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.reset();
        ref->mesh.clear();
        ref->emesh = nullptr;
        ref->normals.clear();

        const float w = std::max(1.0f, (float)width);
        const float d = std::max(1.0f, (float)depth);
        const float h = std::max(1.0f, (float)height);

        GLModel::Geometry init_data;
        init_data.format = { GLModel::Geometry::EPrimitiveType::Triangles, GLModel::Geometry::EVertexLayout::P3N3 };
        init_data.reserve_vertices(24);
        init_data.reserve_indices(36);

        auto add_face = [&](const Vec3f& normal, const Vec3f& a, const Vec3f& b, const Vec3f& c, const Vec3f& e) {
            unsigned int base = (unsigned int)init_data.vertices_count();
            init_data.add_vertex(a, normal);
            init_data.add_vertex(b, normal);
            init_data.add_vertex(c, normal);
            init_data.add_vertex(e, normal);
            init_data.add_triangle(base + 0, base + 1, base + 2);
            init_data.add_triangle(base + 2, base + 3, base + 0);
        };

        Vec3f p000(0, 0, 0), p100(w, 0, 0), p110(w, d, 0), p010(0, d, 0);
        Vec3f p001(0, 0, h), p101(w, 0, h), p111(w, d, h), p011(0, d, h);
        add_face(Vec3f(0, 0, -1), p010, p110, p100, p000); // bottom
        add_face(Vec3f(0, 0,  1), p001, p101, p111, p011); // top
        add_face(Vec3f(0, -1, 0), p000, p100, p101, p001); // front
        add_face(Vec3f(1,  0, 0), p100, p110, p111, p101); // right
        add_face(Vec3f(0,  1, 0), p110, p010, p011, p111); // back
        add_face(Vec3f(-1, 0, 0), p010, p000, p001, p011); // left

        ref->model.init_from(std::move(init_data));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1bounding_1box(JNIEnv* env, jclass, jlong ptr, jlong modelPtr, jint i) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ModelRef* modelRef = (ModelRef*) (intptr_t) modelPtr;

        const BoundingBoxf3& box = modelRef->model.objects[i]->bounding_box_approx();
        const BoundingBoxf3& curr_box = ref->model.get_bounding_box();
        if (!ref->model.is_initialized() || !is_approx(box.min, curr_box.min) || !is_approx(box.max, curr_box.max)) {
            ref->model.reset();

            const Vec3f b_min = box.min.cast<float>();
            const Vec3f b_max = box.max.cast<float>();
            const Vec3f size = 0.2f * box.size().cast<float>();

            GLModel::Geometry init_data;
            init_data.format = { GLModel::Geometry::EPrimitiveType::Lines, GLModel::Geometry::EVertexLayout::P3 };
            init_data.reserve_vertices(48);
            init_data.reserve_indices(48);

            // vertices
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x() + size.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y() + size.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_min.z() + size.z()));

            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x() - size.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y() + size.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_min.z() + size.z()));

            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x() - size.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y() - size.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_min.z() + size.z()));

            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x() + size.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y() - size.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_min.z() + size.z()));

            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x() + size.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y() + size.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_max.z() - size.z()));

            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x() - size.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y() + size.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_max.z() - size.z()));

            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x() - size.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y() - size.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_max.z() - size.z()));

            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x() + size.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y() - size.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_max.z() - size.z()));

            // indices
            for (unsigned int i = 0; i < 48; ++i) {
                init_data.add_index(i);
            }

            ref->model.init_from(std::move(init_data));
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1render(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.render();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1reset(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.reset();
        ref->mesh.clear();
        ref->emesh = nullptr;
        ref->normals.clear();
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1is_1initialized(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        return ref->model.is_initialized();
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1is_1empty(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        return ref->model.is_empty();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1release(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.reset();
        delete ref;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1get_1id(JNIEnv* env, jclass, jlong ptr) {
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        return shader->program.get_id();
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1get_1uniform_1location(JNIEnv* env, jclass, jlong ptr, jstring name) {
        const char* chars = env->GetStringUTFChars(name, JNI_FALSE);
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        if (shader) {
            int location = shader->program.get_uniform_location(chars);
            env->ReleaseStringUTFChars(name, chars);
            return location;
        }
        return 0;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1get_1attrib_1location(JNIEnv* env, jclass, jlong ptr, jstring name) {
        const char *chars = env->GetStringUTFChars(name, JNI_FALSE);
        ShaderRef *shader = (ShaderRef *) (intptr_t) ptr;
        if (shader) {
            int location = shader->program.get_attrib_location(chars);
            env->ReleaseStringUTFChars(name, chars);
            return location;
        }
        return 0;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1start_1using(JNIEnv* env, jclass, jlong ptr) {
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        shader->program.start_using();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1stop_1using(JNIEnv* env, jclass, jlong ptr) {
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        shader->program.stop_using();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1release(JNIEnv* env, jclass, jlong ptr) {
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        delete shader;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1calc_1view_1normal_1matrix(JNIEnv* env, jclass, jdoubleArray view_matrix, jdoubleArray world_matrix, jdoubleArray normal_matrix) {
        jdouble* viewMatrix = env->GetDoubleArrayElements(view_matrix, JNI_FALSE);
        jdouble* worldMatrix = env->GetDoubleArrayElements(world_matrix, JNI_FALSE);

        Matrix4d mViewMatrix(viewMatrix);
        Matrix4d mWorldMatrix(worldMatrix);

        Matrix3d mNormalMatrix = mViewMatrix.block(0, 0, 3, 3) * mWorldMatrix.block(0, 0, 3, 3).inverse().transpose();
        env->SetDoubleArrayRegion(normal_matrix, 0, 12, mNormalMatrix.data());

        env->ReleaseDoubleArrayElements(view_matrix, viewMatrix, JNI_ABORT);
        env->ReleaseDoubleArrayElements(world_matrix, worldMatrix, JNI_ABORT);
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1config_1create(JNIEnv* env, jclass, jstring config) {
        ConfigRef* ref = new ConfigRef();
        const char* config_ini = env->GetStringUTFChars(config, JNI_FALSE);
        // Tolerate version drift in bundled profiles: substitute unknown enum values rather than
        // throwing, so the config/UI layer keeps working instead of failing to construct.
        ref->config.load_from_ini_string(config_ini, ForwardCompatibilitySubstitutionRule::EnableSilent);

        const ConfigOption *opt = ref->config.option("nozzle_diameter");
        if (opt)
            ref->config.set_key_value("num_extruders", new ConfigOptionInt((int)static_cast<const ConfigOptionFloats*>(opt)->values.size()));

        env->ReleaseStringUTFChars(config, config_ini);
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1config_1release(JNIEnv* env, jclass, jlong ptr) {
        ConfigRef* ref = (ConfigRef*) (intptr_t) ptr;
        delete ref;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1config_1check_1compatibility(JNIEnv* env, jclass, jlong ptr, jstring cond) {
        ConfigRef* ref = (ConfigRef*) (intptr_t) ptr;
        const char* condition = env->GetStringUTFChars(cond, JNI_FALSE);

        jboolean value;
        try {
            value = PlaceholderParser::evaluate_boolean_expression(condition, ref->config);
        } catch (const std::runtime_error &err) {
            //FIXME in case of an error, return "compatible with everything".
            __android_log_print(ANDROID_LOG_WARN, TAG, "Parsing error of compatible_printers_condition:\n%s\n", err.what());
            value = true;
        }
        env->ReleaseStringUTFChars(cond, condition);
        return value;
    }

    JNIEXPORT jstring JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1config_1eval(JNIEnv* env, jclass, jlong ptr, jstring cond) {
        ConfigRef *ref = (ConfigRef *) (intptr_t) ptr;
        const char *condition = env->GetStringUTFChars(cond, JNI_FALSE);

        try {
            PlaceholderParser parser(&ref->config);
            std::string val = parser.process(std::string(condition));
            env->ReleaseStringUTFChars(cond, condition);
            return env->NewStringUTF(val.c_str());
        } catch (const std::runtime_error &err) {
            env->ReleaseStringUTFChars(cond, condition);

            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), err.what());
            return nullptr;
        }
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1unproject(JNIEnv* env, jclass, jdoubleArray view_matrix, jdoubleArray projection_matrix, jint screen_width, jint screen_height, jdouble screen_x, jdouble screen_y) {
        jdouble* viewMatrix = env->GetDoubleArrayElements(view_matrix, JNI_FALSE);
        jdouble* projectionMatrix = env->GetDoubleArrayElements(projection_matrix, JNI_FALSE);

        Matrix4d modelview(viewMatrix);
        Matrix4d projection(projectionMatrix);
        Vec4i32 viewport(0, 0, screen_width, screen_height);
        Vec3d screenPoint(screen_x, screen_height - screen_y, 0);

        Vec3d point;
        igl::unproject(screenPoint, modelview, projection, viewport, point);

        jdoubleArray arr = env->NewDoubleArray(3);
        env->SetDoubleArrayRegion(arr, 0, 3, point.data());

        env->ReleaseDoubleArrayElements(view_matrix, viewMatrix, JNI_ABORT);
        env->ReleaseDoubleArrayElements(projection_matrix, projectionMatrix, JNI_ABORT);

        return arr;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1create(JNIEnv* env, jclass, jlongArray data) {
        BedRef* ref = new BedRef();
        GLModelRef* refs = new GLModelRef[3];
        refs[0] = GLModelRef();
        ref->triangles = &refs[0].model;
        refs[1] = GLModelRef();
        ref->gridlines = &refs[1].model;
        refs[2] = GLModelRef();
        ref->contourlines = &refs[2].model;

        for (int i = 0; i < 3; i++) {
            jlong ptrLong = (jlong) (intptr_t) &refs[i];
            env->SetLongArrayRegion(data, i, 1, &ptrLong);
        }
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1configure(JNIEnv* env, jclass, jlong ptr, jstring config_path) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;

        const char* chars = env->GetStringUTFChars(config_path, JNI_FALSE);
        std::string config_path_str(chars);
        env->ReleaseStringUTFChars(config_path, chars);

        // Convert any C++ exception (e.g. a missing/invalid config option) into a Java exception
        // instead of letting it escape the JNI boundary and abort the whole process.
        try {
            // The bed only needs printable_area / printable_height. Use silent substitution so a single
            // unrecognised enum value elsewhere in the profile (engine/profile version drift) falls back
            // to its default instead of throwing and leaving the bed blank.
            ref->config.load(config_path_str, ForwardCompatibilitySubstitutionRule::EnableSilent);

            // OrcaSlicer renamed bed_shape -> printable_area and max_print_height -> printable_height.
            const Pointfs bed_shape = ref->config.option_throw<ConfigOptionPoints>("printable_area")->values;
            float maxHeight = ref->config.option_throw<ConfigOptionFloat>("printable_height")->value;

            ref->contour = ExPolygon(Polygon::new_scale(bed_shape));
            const BoundingBox bbox = ref->contour.contour.bounding_box();
            if (!bbox.defined) {
                env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), "Invalid bed shape");
                return;
            }

            // BuildVolume now also takes per-extruder printable areas/heights; we have none, so pass empty.
            ref->build_volume = BuildVolume { bed_shape, maxHeight, {}, {} };
            ref->triangles->reset();
            ref->gridlines->reset();
            ref->contourlines->reset();

            bed_util_init_gridlines(ref->contour, ref->gridlines);
            bed_util_init_triangles(ref->contour, ref->triangles);
            bed_util_init_contourlines(ref->contour, ref->contourlines);
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), e.what());
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1init_1triangles_1mesh(JNIEnv* env, jclass, jlong ptr, jlong triangles_ptr) {
        auto ref = reinterpret_cast<BedRef*>(ptr);
        auto tRef = reinterpret_cast<GLModelRef*>(triangles_ptr);

        auto contour = ref->contour;
        BoundingBox bb = get_extents(contour);
        Point center = bb.center();
        float scaleFactor = 4;
        contour.scale(scaleFactor);
        contour.translate(-center.x() * scaleFactor * 0.5f, -center.y() * scaleFactor * 0.5f);
        bed_util_init_triangles_its(contour, &tRef->mesh.its);
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1arrange(JNIEnv* env, jclass, jlong ptr, jlong model) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        ModelRef* mRef = (ModelRef*) (intptr_t) model;

        try {
            // OrcaSlicer's arrange API takes the bed as Points (scaled bed shape) plus ArrangeParams,
            // rather than the arr2::ArrangeBed/ArrangeSettings types of newer PrusaSlicer.
            DynamicPrintConfig config = ref->config;
            Points bed = get_bed_shape(config);
            ArrangeParams arrange_cfg;
            arrange_cfg.min_obj_distance = scaled(6.0);

            // arrange_objects() / arrange() do NOT translate min_obj_distance into the per-item inflation
            // that the packer uses for spacing — _arrange() zeroes min_obj_distance and expects every item
            // to be pre-inflated (the desktop does this via update_selected_items_inflation()). Without it,
            // parts pack edge-to-edge. Inflate each real object by half the min distance so neighbouring
            // parts end up a full min_obj_distance (6mm) apart, which keeps them separable/printable.
            ModelInstancePtrs instances;
            ArrangePolygons input = get_arrange_polys(mRef->model, instances);
            const coord_t inflation = arrange_cfg.min_obj_distance / 2;
            for (ArrangePolygon& ap : input)
                if (!ap.is_virt_object)
                    ap.inflation = inflation;
            arrangement::arrange(input, bed, arrange_cfg);
            return apply_arrange_polys(input, instances, throw_if_out_of_bed);
        } catch (const std::exception& e) {
            // If the model cannot fit the bed, it throws "Objects could not fit on the bed".
            // Catch it here to avoid JNI crash.
            return false;
        }
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1get_1bounding_1volume(JNIEnv* env, jclass, jlong ptr) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->build_volume.bounding_volume().min.x();
        elements[1] = ref->build_volume.bounding_volume().min.y();
        elements[2] = ref->build_volume.bounding_volume().min.z();
        elements[3] = ref->build_volume.bounding_volume().max.x();
        elements[4] = ref->build_volume.bounding_volume().max.y();
        elements[5] = ref->build_volume.bounding_volume().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1get_1bounding_1volume_1max_1size(JNIEnv* env, jclass, jlong ptr) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        return ref->build_volume.bounding_volume().max_size();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1release(JNIEnv* env, jclass, jlong ptr) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        delete ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1create(JNIEnv* env, jclass) {
        GCodeViewerRef* ref = new GCodeViewerRef();
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1colors(JNIEnv* env, jclass, jlong ptr, jintArray colorsArr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;

        jint* colors = env->GetIntArrayElements(colorsArr, JNI_FALSE);
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::Skirt,                    { (unsigned char) colors[0],  (unsigned char) colors[1],  (unsigned char) colors[2] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::ExternalPerimeter,        { (unsigned char) colors[3],  (unsigned char) colors[4],  (unsigned char) colors[5] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::SupportMaterial,          { (unsigned char) colors[6],  (unsigned char) colors[7],  (unsigned char) colors[8] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::SupportMaterialInterface, { (unsigned char) colors[9],  (unsigned char) colors[10], (unsigned char) colors[11] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::InternalInfill,           { (unsigned char) colors[12], (unsigned char) colors[13], (unsigned char) colors[14] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::SolidInfill,              { (unsigned char) colors[15], (unsigned char) colors[16], (unsigned char) colors[17] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::WipeTower,                { (unsigned char) colors[18], (unsigned char) colors[19], (unsigned char) colors[20] });
        env->ReleaseIntArrayElements(colorsArr, colors, JNI_ABORT);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1render(JNIEnv* env, jclass, jlong ptr, jfloatArray viewMatrixArr, jfloatArray projectionMatrixArr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        jfloat* viewMatrix = env->GetFloatArrayElements(viewMatrixArr, JNI_FALSE);
        jfloat* projectionMatrix = env->GetFloatArrayElements(projectionMatrixArr, JNI_FALSE);

        libvgcode::Mat4x4 converted_view_matrix;
        std::memcpy(converted_view_matrix.data(), viewMatrix, 16 * sizeof(float));
        libvgcode::Mat4x4 converted_projection_matrix;
        std::memcpy(converted_projection_matrix.data(), projectionMatrix, 16 * sizeof(float));

        ref->viewer.render(converted_view_matrix, converted_projection_matrix);

        env->ReleaseFloatArrayElements(viewMatrixArr, viewMatrix, JNI_ABORT);
        env->ReleaseFloatArrayElements(projectionMatrixArr, projectionMatrix, JNI_ABORT);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1init(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        if (ref->initialized) return;
        ref->viewer.init(reinterpret_cast<const char*>(glGetString(GL_VERSION)));
        ref->initialized = true;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1is_1initialized(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->initialized;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1load(JNIEnv* env, jclass, jlong ptr, jlong resultPtr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        GCodeResultRef* resultRef = (GCodeResultRef*) (intptr_t) resultPtr;

        ref->data = libvgcode_convert_input_data(resultRef->result, resultRef->result.extruder_colors, resultRef->result.extruder_colors, ref->viewer);
        ref->viewer.load(std::move(ref->data));
        ref->viewer.set_time_mode(libvgcode::ETimeMode::Normal);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1reset(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.reset();
        ref->initialized = false;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1layers_1count(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->viewer.get_layers_count();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1layers_1view_1range(JNIEnv* env, jclass, jlong ptr, jlong min, jlong max) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.set_layers_view_range(static_cast<uint32_t>(min), static_cast<uint32_t>(max));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1infill_1visibility_1depth(JNIEnv* env, jclass, jlong ptr, jint depth) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.set_infill_visibility_depth(depth);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1fast_1mode(JNIEnv* env, jclass, jlong ptr, jboolean fastMode) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.set_fast_mode(fastMode);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1selected_1object_1id(JNIEnv* env, jclass, jlong ptr, jint id) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.set_selected_object_id(id);
    }

    JNIEXPORT jlongArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1layers_1view_1range(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        jlongArray arr = env->NewLongArray(2);
        auto range = ref->viewer.get_layers_view_range();
        jlong min = range[0], max = range[1];
        env->SetLongArrayRegion(arr, 0, 1, &min);
        env->SetLongArrayRegion(arr, 0, 2, &max);
        return arr;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1release(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.shutdown();
        delete ref;
    }

    JNIEXPORT jfloat JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1estimated_1time(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->viewer.get_estimated_time();
    }

    libvgcode::EGCodeExtrusionRole mapRole(int index) {
        libvgcode::EGCodeExtrusionRole crole;
        switch (index) {
            default:
            case 0:
                crole = libvgcode::EGCodeExtrusionRole::None;
                break;
            case 1:
                crole = libvgcode::EGCodeExtrusionRole::Perimeter;
                break;
            case 2:
                crole = libvgcode::EGCodeExtrusionRole::ExternalPerimeter;
                break;
            case 3:
                crole = libvgcode::EGCodeExtrusionRole::OverhangPerimeter;
                break;
            case 4:
                crole = libvgcode::EGCodeExtrusionRole::InternalInfill;
                break;
            case 5:
                crole = libvgcode::EGCodeExtrusionRole::SolidInfill;
                break;
            case 6:
                crole = libvgcode::EGCodeExtrusionRole::TopSolidInfill;
                break;
            case 7:
                crole = libvgcode::EGCodeExtrusionRole::Ironing;
                break;
            case 8:
                crole = libvgcode::EGCodeExtrusionRole::BridgeInfill;
                break;
            case 9:
                crole = libvgcode::EGCodeExtrusionRole::GapFill;
                break;
            case 10:
                crole = libvgcode::EGCodeExtrusionRole::Skirt;
                break;
            case 11:
                crole = libvgcode::EGCodeExtrusionRole::SupportMaterial;
                break;
            case 12:
                crole = libvgcode::EGCodeExtrusionRole::SupportMaterialInterface;
                break;
            case 13:
                crole = libvgcode::EGCodeExtrusionRole::WipeTower;
                break;
            case 14:
                crole = libvgcode::EGCodeExtrusionRole::Custom;
                break;
        }
        return crole;
    }

    JNIEXPORT jfloat JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1estimated_1time_1role(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->viewer.get_extrusion_role_estimated_time(mapRole(role));
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1is_1extrusion_1role_1visible(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->viewer.is_extrusion_role_visible(mapRole(role));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1toggle_1extrusion_1role_1visibility(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.toggle_extrusion_role_visibility(mapRole(role));
    }

    // Switch the gcode preview color mode (e.g. FeatureType=0, Tool/Filament=11). See libvgcode EViewType.
    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1view_1type(JNIEnv* env, jclass, jlong ptr, jint type) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.set_view_type(static_cast<libvgcode::EViewType>(type));
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1view_1type(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return (jint) ref->viewer.get_view_type();
    }

    // Set the per-filament/tool colors used by the Tool view. colors are packed 0xRRGGBB ints.
    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1tool_1colors(JNIEnv* env, jclass, jlong ptr, jintArray colorsArr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        jsize n = env->GetArrayLength(colorsArr);
        jint* c = env->GetIntArrayElements(colorsArr, JNI_FALSE);
        libvgcode::Palette palette;
        palette.reserve(n);
        for (jsize i = 0; i < n; ++i) {
            int v = c[i];
            palette.push_back({ (unsigned char) ((v >> 16) & 0xFF), (unsigned char) ((v >> 8) & 0xFF), (unsigned char) (v & 0xFF) });
        }
        env->ReleaseIntArrayElements(colorsArr, c, JNI_ABORT);
        if (!palette.empty())
            ref->viewer.set_tool_colors(palette);
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1is_1option_1visible(JNIEnv* env, jclass, jlong ptr, jint optionType) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return (jboolean) ref->viewer.is_option_visible(static_cast<libvgcode::EOptionType>(optionType));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1toggle_1option_1visibility(JNIEnv* env, jclass, jlong ptr, jint optionType) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.toggle_option_visibility(static_cast<libvgcode::EOptionType>(optionType));
    }

    // ---- Multi-color painting (mmu segmentation) ----

    static Slic3r::EnforcerBlockerType paint_state(jint filamentIdx) {
        if (filamentIdx <= 0) return Slic3r::EnforcerBlockerType::NONE;
        int v = filamentIdx;
        if (v > (int) Slic3r::EnforcerBlockerType::ExtruderMax) v = (int) Slic3r::EnforcerBlockerType::ExtruderMax;
        return static_cast<Slic3r::EnforcerBlockerType>(v);
    }

    // The facet channel a paint session reads/writes, by mode: 0 color, 1 support, 2 seam, 3 fuzzy.
    static Slic3r::FacetsAnnotation& facets_for_mode(Slic3r::ModelVolume* v, int mode) {
        switch (mode) {
            case 1:  return v->supported_facets;
            case 2:  return v->seam_facets;
            case 3:  return v->fuzzy_skin_facets;
            default: return v->mmu_segmentation_facets;
        }
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1begin(JNIEnv* env, jclass, jlong modelPtr, jint objIdx, jint mode) {
        ModelRef* mRef = (ModelRef*) (intptr_t) modelPtr;
        if (objIdx < 0 || objIdx >= (int) mRef->model.objects.size()) return 0;
        ModelObject* obj = mRef->model.objects[objIdx];
        if (obj->volumes.empty()) return 0;
        PaintSessionRef* s = new PaintSessionRef();
        s->model = mRef;
        s->objIdx = objIdx;
        s->mode = mode;
        // Use the same merged object mesh the GLModel/raycaster uses, so hit coords and triangle
        // indices line up exactly. Paint states are stored per triangle index, so they still map
        // back to volume[0]'s facet annotation (single-volume objects).
        s->mesh = obj->mesh();
        s->selector = std::make_unique<Slic3r::TriangleSelector>(s->mesh);
        const auto& data = facets_for_mode(obj->volumes[0], mode).get_data();
        if (!data.triangles_to_split.empty())
            s->selector->deserialize(data, true);
        s->emesh = new AABBMesh(s->mesh, true);
        return (jlong) (intptr_t) s;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1raycast(JNIEnv* env, jclass, jlong sessionPtr, jdoubleArray originArr, jdoubleArray dirArr) {
        PaintSessionRef* s = (PaintSessionRef*) (intptr_t) sessionPtr;
        jdouble* o = env->GetDoubleArrayElements(originArr, JNI_FALSE);
        jdouble* d = env->GetDoubleArrayElements(dirArr, JNI_FALSE);
        Vec3d origin(o[0], o[1], o[2]);
        Vec3d dir(d[0], d[1], d[2]);
        env->ReleaseDoubleArrayElements(originArr, o, JNI_ABORT);
        env->ReleaseDoubleArrayElements(dirArr, d, JNI_ABORT);
        std::vector<AABBMesh::hit_result> hits = s->emesh->query_ray_hits(origin, dir);
        if (hits.empty()) return env->NewDoubleArray(0);
        const AABBMesh::hit_result& hit = hits.front();
        jdoubleArray arr = env->NewDoubleArray(4);
        Vec3d pos = hit.position();
        double out[4] = { (double) hit.face(), pos.x(), pos.y(), pos.z() };
        env->SetDoubleArrayRegion(arr, 0, 4, out);
        return arr;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1brush(JNIEnv* env, jclass, jlong sessionPtr, jdoubleArray hitArr, jint facetStart, jdouble radius, jint filamentIdx, jdoubleArray cameraArr, jboolean sphere) {
        PaintSessionRef* s = (PaintSessionRef*) (intptr_t) sessionPtr;
        jdouble* h = env->GetDoubleArrayElements(hitArr, JNI_FALSE);
        Vec3f hit((float) h[0], (float) h[1], (float) h[2]);
        env->ReleaseDoubleArrayElements(hitArr, h, JNI_ABORT);

        jdouble* cam = env->GetDoubleArrayElements(cameraArr, JNI_FALSE);
        Vec3d camera_pos_world((double) cam[0], (double) cam[1], (double) cam[2]);
        env->ReleaseDoubleArrayElements(cameraArr, cam, JNI_ABORT);

        ModelObject* obj = s->model->model.objects[s->objIdx];
        Transform3d inst_matrix = Transform3d::Identity();
        if (!obj->instances.empty() && obj->instances.front() != nullptr) {
            inst_matrix = obj->instances.front()->get_matrix();
        }

        // Convert camera position to local mesh space
        Vec3d camera_pos_local = inst_matrix.inverse() * camera_pos_world;

        Transform3d trafo_no_translate = inst_matrix;
        trafo_no_translate.translation() = Vec3d::Zero();

        Slic3r::EnforcerBlockerType state = paint_state(filamentIdx);

        if (facetStart < 0) {
            const auto& its = s->mesh.its;
            float best = FLT_MAX; int bi = 0;
            for (int i = 0; i < (int) its.indices.size(); ++i) {
                const Vec3i32& t = its.indices[i];
                Vec3f c = (its.vertices[t[0]] + its.vertices[t[1]] + its.vertices[t[2]]) / 3.f;
                float d = (c - hit).squaredNorm();
                if (d < best) { best = d; bi = i; }
            }
            facetStart = bi;
        }

        auto cursor = TriangleSelector::SinglePointCursor::cursor_factory(
            hit, camera_pos_local.cast<float>(), (float)radius,
            sphere ? TriangleSelector::CursorType::SPHERE : TriangleSelector::CursorType::CIRCLE,
            inst_matrix, TriangleSelector::ClippingPlane());

        s->selector->select_patch(facetStart, std::move(cursor), state, trafo_no_translate, true);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1bucket(JNIEnv* env, jclass, jlong sessionPtr, jdoubleArray hitArr, jint facetStart, jint filamentIdx, jboolean propagate, jdouble angle) {
        PaintSessionRef* s = (PaintSessionRef*) (intptr_t) sessionPtr;
        jdouble* h = env->GetDoubleArrayElements(hitArr, JNI_FALSE);
        Vec3f hit((float) h[0], (float) h[1], (float) h[2]);
        env->ReleaseDoubleArrayElements(hitArr, h, JNI_ABORT);
        // Bucket fill needs a valid starting facet; the Java raycaster only gives the hit point,
        // so find the nearest original facet to it.
        if (facetStart < 0) {
            const auto& its = s->mesh.its;
            float best = FLT_MAX; int bi = 0;
            for (int i = 0; i < (int) its.indices.size(); ++i) {
                const Vec3i32& t = its.indices[i];
                Vec3f c = (its.vertices[t[0]] + its.vertices[t[1]] + its.vertices[t[2]]) / 3.f;
                float d = (c - hit).squaredNorm();
                if (d < best) { best = d; bi = i; }
            }
            facetStart = bi;
        }
        Slic3r::TriangleSelector::ClippingPlane clp;
        s->selector->bucket_fill_select_triangles(hit, facetStart, clp, (float) angle, propagate, true);
        s->selector->seed_fill_apply_on_triangles(paint_state(filamentIdx));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1height(JNIEnv* env, jclass, jlong sessionPtr, jdouble zMin, jdouble zMax, jint filamentIdx) {
        PaintSessionRef* s = (PaintSessionRef*) (intptr_t) sessionPtr;
        Slic3r::EnforcerBlockerType state = paint_state(filamentIdx);
        const auto& its = s->mesh.its;
        for (int i = 0; i < (int) its.indices.size(); ++i) {
            const Vec3i32& tri = its.indices[i];
            const Vec3f& a = its.vertices[tri[0]];
            const Vec3f& b = its.vertices[tri[1]];
            const Vec3f& c = its.vertices[tri[2]];
            float cz = (a.z() + b.z() + c.z()) / 3.f;
            // Paint if centroid OR any vertex is within range. This is critical for large
            // hull triangles that cross range boundaries.
            if ((cz >= zMin && cz <= zMax) ||
                (a.z() >= zMin && a.z() <= zMax) ||
                (b.z() >= zMin && b.z() <= zMax) ||
                (c.z() >= zMin && c.z() <= zMax)) {
                s->selector->set_facet(i, state);
            }
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1clear(JNIEnv* env, jclass, jlong sessionPtr) {
        PaintSessionRef* s = (PaintSessionRef*) (intptr_t) sessionPtr;
        s->selector->reset();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1commit(JNIEnv* env, jclass, jlong sessionPtr) {
        PaintSessionRef* s = (PaintSessionRef*) (intptr_t) sessionPtr;
        ModelObject* obj = s->model->model.objects[s->objIdx];

        // The merged session mesh matches obj->mesh(); commit to volumes[0] as the standard for
        // single-part models. Write to the facet channel for this session's mode.
        for (auto* v : obj->volumes) {
            if (v == obj->volumes[0]) {
                facets_for_mode(v, s->mode).set(*s->selector);
            }
            // Color painting needs the volume's extruder set to Auto so per-facet filaments apply.
            if (s->mode == 0 && !v->mmu_segmentation_facets.get_data().triangles_to_split.empty()) {
                v->config.set("extruder", 0);
            }
        }
    }

    // Returns flattened triangle vertices (9 floats per triangle) for the facets painted with the
    // given filament, for rendering a colored overlay.
    JNIEXPORT jfloatArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1get_1mesh(JNIEnv* env, jclass, jlong sessionPtr, jint filamentIdx) {
        PaintSessionRef* s = (PaintSessionRef*) (intptr_t) sessionPtr;
        indexed_triangle_set its = s->selector->get_facets(paint_state(filamentIdx));
        std::vector<float> buf;
        buf.reserve(its.indices.size() * 9);
        for (const Vec3i32& tri : its.indices) {
            for (int k = 0; k < 3; ++k) {
                const Vec3f& v = its.vertices[tri[k]];
                buf.push_back(v.x()); buf.push_back(v.y()); buf.push_back(v.z());
            }
        }
        jfloatArray arr = env->NewFloatArray((jsize) buf.size());
        if (!buf.empty()) env->SetFloatArrayRegion(arr, 0, (jsize) buf.size(), buf.data());
        return arr;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1end(JNIEnv* env, jclass, jlong sessionPtr) {
        PaintSessionRef* s = (PaintSessionRef*) (intptr_t) sessionPtr;
        delete s->emesh;
        delete s;
    }

    // Build a GLModel from the committed mmu painting on an object's volume[0] (no active session),
    // so painted colors persist on the model after exiting paint mode. Returns the triangle count.
    // Accumulate, in the object's merged-mesh coordinate space, the facets painted with the given
    // filament state across ALL model-part volumes. A Bambu/Orca "assembly" loads as one
    // ModelObject with several volumes (figure + accessories), each carrying its own paint data;
    // the rendered GLModel is obj->mesh() (all model-part volumes merged, each transformed by its
    // volume matrix then the instance matrix). The overlay must merge the same volumes with the
    // same transforms, or only the first volume's paint shows (and misaligned against the merge).
    static indexed_triangle_set accumulate_paint_facets(Slic3r::ModelObject* obj, Slic3r::EnforcerBlockerType state) {
        indexed_triangle_set out;
        for (const Slic3r::ModelInstance* inst : obj->instances) {
            const Slic3r::Transform3d inst_m = inst->get_matrix();
            for (const Slic3r::ModelVolume* v : obj->volumes) {
                if (!v->is_model_part()) continue;
                const auto& data = v->mmu_segmentation_facets.get_data();
                if (data.triangles_to_split.empty()) continue;
                TriangleMesh vmesh = v->mesh();
                Slic3r::TriangleSelector sel(vmesh);
                sel.deserialize(data, true);
                indexed_triangle_set part = sel.get_facets(state);
                if (part.indices.empty()) continue;
                const Slic3r::Transform3d m = inst_m * v->get_matrix();
                const int base = (int) out.vertices.size();
                out.vertices.reserve(out.vertices.size() + part.vertices.size());
                for (const stl_vertex& vert : part.vertices)
                    out.vertices.emplace_back((m * vert.cast<double>()).cast<float>());
                out.indices.reserve(out.indices.size() + part.indices.size());
                for (const stl_triangle_vertex_indices& tri : part.indices)
                    out.indices.emplace_back(tri + stl_triangle_vertex_indices(base, base, base));
            }
        }
        return out;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1build_1paint_1overlay(JNIEnv* env, jclass, jlong modelPtr, jint objIdx, jlong glPtr, jint filamentIdx) {
        ModelRef* m = (ModelRef*) (intptr_t) modelPtr;
        if (objIdx < 0 || objIdx >= (int) m->model.objects.size()) return 0;
        ModelObject* obj = m->model.objects[objIdx];
        if (obj->volumes.empty()) return 0;
        indexed_triangle_set its = accumulate_paint_facets(obj, paint_state(filamentIdx));
        GLModelRef* g = (GLModelRef*) (intptr_t) glPtr;
        g->model.reset();
        if (!its.indices.empty())
            g->model.init_from(its);
        return (jint) its.indices.size();
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1has_1paint(JNIEnv* env, jclass, jlong modelPtr, jint objIdx) {
        ModelRef* m = (ModelRef*) (intptr_t) modelPtr;
        if (objIdx < 0 || objIdx >= (int) m->model.objects.size()) return false;
        ModelObject* obj = m->model.objects[objIdx];
        for (const Slic3r::ModelVolume* v : obj->volumes) {
            if (v->is_model_part() && !v->mmu_segmentation_facets.get_data().triangles_to_split.empty())
                return true;
        }
        return false;
    }

    // Highest filament index (1-based) that has any painted facets across the object's model-part
    // volumes, or 0 if none. Lets the renderer build an overlay for every painted filament even
    // when the user's configured palette has fewer slots than the model was painted with.
    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1paint_1max_1filament(JNIEnv* env, jclass, jlong modelPtr, jint objIdx) {
        ModelRef* m = (ModelRef*) (intptr_t) modelPtr;
        if (objIdx < 0 || objIdx >= (int) m->model.objects.size()) return 0;
        ModelObject* obj = m->model.objects[objIdx];
        int maxF = 0;
        for (const Slic3r::ModelVolume* v : obj->volumes) {
            if (!v->is_model_part()) continue;
            const auto& data = v->mmu_segmentation_facets.get_data();
            if (data.triangles_to_split.empty()) continue;
            TriangleMesh vmesh = v->mesh();
            Slic3r::TriangleSelector sel(vmesh);
            sel.deserialize(data, true);
            for (int f = (int) Slic3r::EnforcerBlockerType::ExtruderMax; f > maxF; --f) {
                if (!sel.get_facets(static_cast<Slic3r::EnforcerBlockerType>(f)).indices.empty()) {
                    maxF = f;
                    break;
                }
            }
        }
        return (jint) maxF;
    }

    // Build a GLModel from the facets painted with the given filament (mesh-local coords), for
    // rendering a colored overlay over the base object. Returns the number of triangles.
    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1from_1paint(JNIEnv* env, jclass, jlong glPtr, jlong sessionPtr, jint filamentIdx) {
        GLModelRef* g = (GLModelRef*) (intptr_t) glPtr;
        PaintSessionRef* s = (PaintSessionRef*) (intptr_t) sessionPtr;
        indexed_triangle_set its = s->selector->get_facets(paint_state(filamentIdx));
        g->model.reset();
        if (!its.indices.empty())
            g->model.init_from(its);
        return (jint) its.indices.size();
    }
}