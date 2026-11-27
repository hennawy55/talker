#include <jni.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <vector>
#include "audiocpp.h"

static audiocpp_registry* g_registry = nullptr;
static audiocpp_model* g_model = nullptr;
static audiocpp_session* g_session = nullptr;
static std::mutex g_mutex;
static std::string g_error;
static int g_sample_rate = 24000;

static void set_error(const char* prefix) {
    const char* detail = audiocpp_last_error();
    g_error = prefix ? prefix : "Audio engine error";
    if (detail && *detail) { g_error += ": "; g_error += detail; }
}
static void release_engine() {
    audiocpp_session_free(g_session); g_session = nullptr;
    audiocpp_model_free(g_model); g_model = nullptr;
    audiocpp_registry_free(g_registry); g_registry = nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_talker_offline_MainActivity_nativeLoad(JNIEnv* env, jobject, jstring modelPath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_engine();
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    audiocpp_status st = audiocpp_registry_create(nullptr, &g_registry);
    if (st == AUDIOCPP_OK) {
        audiocpp_model_config config = {"omnivoice", nullptr, nullptr, nullptr};
        st = audiocpp_model_load(g_registry, path, &config, nullptr, &g_model);
    }
    env->ReleaseStringUTFChars(modelPath, path);
    if (st != AUDIOCPP_OK) { set_error("Model load failed"); release_engine(); return env->NewStringUTF(g_error.c_str()); }
    audiocpp_backend_config backend = {"cpu", 0, 4};
    st = audiocpp_session_create(g_model, "tts", "offline", &backend, nullptr, &g_session);
    if (st != AUDIOCPP_OK) { set_error("Session create failed"); release_engine(); return env->NewStringUTF(g_error.c_str()); }
    g_error.clear();
    return nullptr;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_talker_offline_MainActivity_nativeGenerate(JNIEnv* env, jobject, jstring text,
        jstring language, jfloatArray voice, jint voiceRate, jint voiceChannels, jstring referenceText,
        jint numSteps, jfloat guidanceScale, jfloat speed, jboolean denoise,
        jboolean preprocessPrompt, jboolean postprocessOutput) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_session) { g_error = "Engine is not loaded"; return nullptr; }
    const char* textChars = env->GetStringUTFChars(text, nullptr);
    const char* langChars = env->GetStringUTFChars(language, nullptr);
    const char* refChars = referenceText ? env->GetStringUTFChars(referenceText, nullptr) : nullptr;
    audiocpp_request* req = audiocpp_request_create();
    audiocpp_result* result = nullptr;
    audiocpp_status st = audiocpp_request_set_text(req, textChars, langChars);
    if (st == AUDIOCPP_OK && voice && env->GetArrayLength(voice) > 0) {
        jsize n = env->GetArrayLength(voice);
        std::vector<float> samples(static_cast<size_t>(n));
        env->GetFloatArrayRegion(voice, 0, n, samples.data());
        st = audiocpp_request_set_voice_audio(req, samples.data(),
            static_cast<size_t>(n / std::max(1, voiceChannels)), voiceRate, voiceChannels);
        if (st == AUDIOCPP_OK && refChars && *refChars)
            st = audiocpp_request_set_option(req, "reference_text", refChars);
    }
    if (st == AUDIOCPP_OK) {
        const std::string steps_value = std::to_string(std::max(1, static_cast<int>(numSteps)));
        const std::string guidance_value = std::to_string(std::max(0.0f, static_cast<float>(guidanceScale)));
        const std::string speed_value = std::to_string(std::max(0.1f, static_cast<float>(speed)));
        const char* denoise_value = denoise == JNI_TRUE ? "true" : "false";
        const char* preprocess_value = preprocessPrompt == JNI_TRUE ? "true" : "false";
        const char* postprocess_value = postprocessOutput == JNI_TRUE ? "true" : "false";
        st = audiocpp_request_set_option(req, "num_inference_steps", steps_value.c_str());
        if (st == AUDIOCPP_OK) st = audiocpp_request_set_option(req, "guidance_scale", guidance_value.c_str());
        if (st == AUDIOCPP_OK) st = audiocpp_request_set_option(req, "speed", speed_value.c_str());
        if (st == AUDIOCPP_OK) st = audiocpp_request_set_option(req, "denoise", denoise_value);
        if (st == AUDIOCPP_OK) st = audiocpp_request_set_option(req, "preprocess_prompt", preprocess_value);
        if (st == AUDIOCPP_OK) st = audiocpp_request_set_option(req, "postprocess_output", postprocess_value);
    }
    if (st == AUDIOCPP_OK) st = audiocpp_session_run(g_session, req, &result);
    env->ReleaseStringUTFChars(text, textChars);
    env->ReleaseStringUTFChars(language, langChars);
    if (refChars) env->ReleaseStringUTFChars(referenceText, refChars);
    audiocpp_request_free(req);
    if (st != AUDIOCPP_OK) {
        set_error("Speech generation failed");
        audiocpp_result_free(result);
        return nullptr;
    }
    const float* samples = nullptr; size_t frames = 0; int rate = 0, channels = 0;
    st = audiocpp_result_audio(result, &samples, &frames, &rate, &channels);
    if (st != AUDIOCPP_OK || !samples || frames == 0 || frames * static_cast<size_t>(channels) > 100000000) {
        set_error("No valid audio returned");
        audiocpp_result_free(result);
        return nullptr;
    }
    const size_t count = frames * static_cast<size_t>(channels);
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(count));
    if (out) env->SetFloatArrayRegion(out, 0, static_cast<jsize>(count), samples);
    g_sample_rate = rate;
    audiocpp_result_free(result);
    if (!out) g_error = "Unable to allocate output audio";
    else g_error.clear();
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_talker_offline_MainActivity_nativeLastError(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return env->NewStringUTF(g_error.c_str());
}
extern "C" JNIEXPORT jint JNICALL
Java_com_talker_offline_MainActivity_nativeSampleRate(JNIEnv*, jobject) { return g_sample_rate; }
extern "C" JNIEXPORT void JNICALL
Java_com_talker_offline_MainActivity_nativeClose(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex); release_engine();
}
