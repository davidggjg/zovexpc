#include "probes.h"
#include "log.h"

#include <jni.h>
#include <string>

namespace {

jstring toJava(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

void setStr(JNIEnv* env, jclass cls, jobject obj, const char* field, const std::string& value) {
    jfieldID f = env->GetFieldID(cls, field, "Ljava/lang/String;");
    if (!f) return;
    jstring js = toJava(env, value);
    env->SetObjectField(obj, f, js);
    env->DeleteLocalRef(js);
}

void setBool(JNIEnv* env, jclass cls, jobject obj, const char* field, bool value) {
    jfieldID f = env->GetFieldID(cls, field, "Z");
    if (f) env->SetBooleanField(obj, f, value ? JNI_TRUE : JNI_FALSE);
}

void setInt(JNIEnv* env, jclass cls, jobject obj, const char* field, int value) {
    jfieldID f = env->GetFieldID(cls, field, "I");
    if (f) env->SetIntField(obj, f, value);
}

void setLong(JNIEnv* env, jclass cls, jobject obj, const char* field, jlong value) {
    jfieldID f = env->GetFieldID(cls, field, "J");
    if (f) env->SetLongField(obj, f, value);
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_zovex_pc_core_NativeBridge_nativeVersion(JNIEnv* env, jobject) {
    return toJava(env, "zovex-native 0.1.0-stage1");
}

JNIEXPORT jobject JNICALL
Java_com_zovex_pc_core_NativeBridge_nativeProbeVulkan(JNIEnv* env, jobject) {
    const zovex::VulkanInfo info = zovex::probeVulkan();

    jclass cls = env->FindClass("com/zovex/pc/core/VulkanReport");
    if (!cls) {
        ZLOGE("VulkanReport class not found");
        return nullptr;
    }
    jmethodID ctor = env->GetMethodID(cls, "<init>", "()V");
    if (!ctor) {
        ZLOGE("VulkanReport has no no-arg constructor");
        return nullptr;
    }
    jobject out = env->NewObject(cls, ctor);

    setBool(env, cls, out, "ok", info.ok);
    setStr(env, cls, out, "detail", info.detail);
    setStr(env, cls, out, "deviceName", info.deviceName);
    setStr(env, cls, out, "driverName", info.driverName);
    setInt(env, cls, out, "apiMajor", static_cast<int>(info.apiMajor));
    setInt(env, cls, out, "apiMinor", static_cast<int>(info.apiMinor));
    setInt(env, cls, out, "apiPatch", static_cast<int>(info.apiPatch));
    setBool(env, cls, out, "descriptorIndexing", info.descriptorIndexing);
    setBool(env, cls, out, "timelineSemaphore", info.timelineSemaphore);
    setLong(env, cls, out, "deviceLocalBytes", static_cast<jlong>(info.deviceLocalBytes));

    return out;
}

JNIEXPORT jobject JNICALL
Java_com_zovex_pc_core_NativeBridge_nativeProbeExec(JNIEnv* env, jobject, jstring jdir) {
    const char* raw = env->GetStringUTFChars(jdir, nullptr);
    const std::string dir = raw ? raw : "";
    if (raw) env->ReleaseStringUTFChars(jdir, raw);

    const zovex::ExecInfo info = zovex::probeExecMapping(dir);

    jclass cls = env->FindClass("com/zovex/pc/core/ExecReport");
    if (!cls) {
        ZLOGE("ExecReport class not found");
        return nullptr;
    }
    jmethodID ctor = env->GetMethodID(cls, "<init>", "()V");
    if (!ctor) {
        ZLOGE("ExecReport has no no-arg constructor");
        return nullptr;
    }
    jobject out = env->NewObject(cls, ctor);

    setBool(env, cls, out, "ok", info.ok);
    setBool(env, cls, out, "mmapAllowed", info.mmapAllowed);
    setBool(env, cls, out, "execveAllowed", info.execveAllowed);
    setStr(env, cls, out, "detail", info.detail);

    return out;
}

} // extern "C"
