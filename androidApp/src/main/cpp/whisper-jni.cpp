#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_heyduk_relay_voice_WhisperJni_initContext(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Initializing whisper context from: %s", path);

    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (ctx == nullptr) {
        LOGE("Failed to init whisper context");
        return 0;
    }

    LOGI("Whisper context initialized successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_dev_heyduk_relay_voice_WhisperJni_transcribeAudio(
        JNIEnv *env, jobject, jlong contextPtr, jfloatArray audioData) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    if (ctx == nullptr) {
        LOGE("transcribeAudio called with null context");
        return env->NewStringUTF("");
    }

    jsize n_samples = env->GetArrayLength(audioData);
    jfloat *samples = env->GetFloatArrayElements(audioData, nullptr);

    LOGI("Transcribing %d samples", n_samples);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress   = false;
    params.print_timestamps = false;
    params.no_context       = true;
    params.single_segment   = false;
    params.language         = "en";
    params.n_threads        = 4;

    int ret = whisper_full(ctx, params, samples, n_samples);
    env->ReleaseFloatArrayElements(audioData, samples, 0);

    if (ret != 0) {
        LOGE("whisper_full failed with code %d", ret);
        return env->NewStringUTF("");
    }

    std::string result;
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; i++) {
        result += whisper_full_get_segment_text(ctx, i);
    }

    LOGI("Transcription complete: %zu chars, %d segments", result.size(), n_segments);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_dev_heyduk_relay_voice_WhisperJni_freeContext(JNIEnv *, jobject, jlong contextPtr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    if (ctx != nullptr) {
        whisper_free(ctx);
        LOGI("Whisper context freed");
    }
}

} // extern "C"
