// JNI side of com.yiligram.hook.YiliHook: initializes LSPlant and forwards hook requests.
#include <android/log.h>
#include <jni.h>

#include <mutex>

#include <dobby.h>
#include <lsplant.hpp>

#include "art_symbols.h"

#define LOG_TAG "YiliHook"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

yili::ArtSymbols g_symbols;
std::mutex g_inline_lock;

void *InlineHook(void *target, void *hooker) {
    std::lock_guard lock(g_inline_lock);
    void *origin = nullptr;
    if (DobbyHook(target, hooker, &origin) != 0) {
        LOGE("inline hook of %p failed", target);
        return nullptr;
    }
    return origin;
}

bool InlineUnhook(void *target) {
    std::lock_guard lock(g_inline_lock);
    return DobbyDestroy(target) == 0;
}

jboolean Init(JNIEnv *env, jclass) {
    if (!g_symbols.Load()) return JNI_FALSE;
    lsplant::InitInfo info{
            .inline_hooker = InlineHook,
            .inline_unhooker = InlineUnhook,
            .art_symbol_resolver = [](std::string_view name) { return g_symbols.Find(name); },
            .art_symbol_prefix_resolver =
                    [](std::string_view prefix) { return g_symbols.FindPrefix(prefix); },
            .generated_class_name = "YiliHooker_",
            .generated_source_name = "Yili",
    };
    bool ok = lsplant::Init(env, info);
    // Symbols are only needed while LSPlant initializes; the table takes a few MB.
    g_symbols.Clear();
    LOGI("LSPlant init: %s", ok ? "ok" : "failed");
    return ok ? JNI_TRUE : JNI_FALSE;
}

jobject Hook(JNIEnv *env, jclass, jobject target, jobject hooker, jobject callback) {
    return lsplant::Hook(env, target, hooker, callback);
}

jboolean Unhook(JNIEnv *env, jclass, jobject target) {
    return lsplant::UnHook(env, target) ? JNI_TRUE : JNI_FALSE;
}

jboolean IsHooked(JNIEnv *env, jclass, jobject target) {
    return lsplant::IsHooked(env, target) ? JNI_TRUE : JNI_FALSE;
}

jboolean Deoptimize(JNIEnv *env, jclass, jobject target) {
    return lsplant::Deoptimize(env, target) ? JNI_TRUE : JNI_FALSE;
}

const JNINativeMethod kMethods[] = {
        {"nativeInit", "()Z", reinterpret_cast<void *>(Init)},
        {"nativeHook",
         "(Ljava/lang/reflect/Member;Ljava/lang/Object;Ljava/lang/reflect/Method;)"
         "Ljava/lang/reflect/Method;",
         reinterpret_cast<void *>(Hook)},
        {"nativeUnhook", "(Ljava/lang/reflect/Member;)Z", reinterpret_cast<void *>(Unhook)},
        {"nativeIsHooked", "(Ljava/lang/reflect/Member;)Z", reinterpret_cast<void *>(IsHooked)},
        {"nativeDeoptimize", "(Ljava/lang/reflect/Member;)Z",
         reinterpret_cast<void *>(Deoptimize)},
};

}  // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass cls = env->FindClass("com/yiligram/hook/YiliHook");
    if (cls == nullptr) return JNI_ERR;
    if (env->RegisterNatives(cls, kMethods, sizeof(kMethods) / sizeof(kMethods[0])) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
