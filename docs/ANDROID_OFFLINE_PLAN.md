# Talker Android Offline Port — Engineering Plan

## Objective

Port the existing VoiceTut-TTS experience to a native Android application. After the model assets are downloaded once, text-to-speech, zero-shot voice cloning, playback, export/share, and history must work without a server or internet connection.

## Existing implementation findings

The current Python engine in `voicetut_tts/engine.py` wraps OmniVoice and calls `OmniVoice.from_pretrained`. It expects PyTorch, NumPy, the OmniVoice package, model checkpoint files, and speaker reference assets. The current React UI in `web/` calls the Gradio API exposed by `voicetut_tts/server.py`; wrapping that UI in a WebView would therefore not create an offline app.

## Selected model/runtime investigation

A model-specific quantized package now exists: `mohammedaly22/VoiceTut-TTS-GGUF`, including `voicetut-tts-q8_0.gguf` (about 1.35 GB). Its model card says the package is self-contained and supports OmniVoice TTS and zero-shot voice cloning. This is a much stronger starting point than trying to export the PyTorch checkpoint from scratch.

Candidate native runtime: `0xShug0/audio.cpp`, which has an OmniVoice implementation and a C ABI for in-process embedding. The runtime documentation describes auto voice and reference-WAV + transcript cloning. The C ABI avoids running an HTTP server inside the app.

Sources:
- https://huggingface.co/mohammedaly22/VoiceTut-TTS-GGUF
- https://github.com/0xShug0/audio.cpp
- https://github.com/0xShug0/audio.cpp/blob/main/docs/c_api.md
- https://github.com/0xShug0/audio.cpp/blob/main/docs/models/omnivoice.md

**Not yet proven:** the audio.cpp C API has not yet been built with Android NDK, linked into this app, or tested on a physical Android device. Desktop/Linux support is not evidence of Android compatibility. The exact pinned runtime revision and Android ABI build must be validated before enabling inference.

## Proposed Android architecture

- Native Kotlin application and local UI.
- Android NDK C/C++ runtime embedding the OmniVoice implementation, preferably through audio.cpp's C ABI after a successful NDK build.
- VoiceTut-TTS Q8_0 GGUF downloaded once to app-private storage; verify file integrity before marking it installed.
- App-private model store with download/resume, version metadata, and disk-space checks.
- Local audio pipeline for reference recording/import, resampling to the model's expected input, and playback.
- Room/SQLite history containing text, voice mode, timestamps, and generated-file URI.
- Android Sharesheet and MediaStore/document export.
- No inference server, remote API, or network dependency during ordinary inference.

## Critical Android runtime gate

1. Pin a specific audio.cpp revision and build its C API for Android NDK `arm64-v8a`.
2. Verify that the OmniVoice model loader accepts the VoiceTut GGUF package as documented.
3. Add a minimal JNI bridge for model loading, synthesis, cancellation, and returned PCM audio.
4. Run a real synthesis with the VoiceTut fine-tune on a physical Android device.
5. Run zero-shot cloning with a reference WAV and transcript.
6. Compare Egyptian Arabic pronunciation and audio quality with the desktop VoiceTut output.
7. Measure peak RAM, cold/warm latency, thermal behavior, and battery impact on the target phone.
8. Only after these pass, complete the download manager, history, playback, WAV export/share, and airplane-mode validation.

## Model size and device constraints

The Q8_0 GGUF is approximately 1.35 GB, so the app must check available storage and show download progress. Model load and synthesis memory can exceed the model file size. Do not infer Android performance from desktop GPU benchmarks. A device may need substantial free RAM and storage; profile on the actual target phone.

## Delivery milestones

- M0: isolated Android branch and native buildable shell. Complete.
- M1: identify an existing VoiceTut GGUF package and a native OmniVoice runtime candidate. Complete.
- M2: Android NDK build + JNI inference proof of concept. Not complete.
- M3: on-device model download manager and offline verification. Not complete.
- M4: local cloning, playback, WAV export/share, and history. Not complete.
- M5: airplane-mode test, performance profiling, stable signed release APK. Not complete.

Do not enable controls that have no working engine behind them. Do not label the app offline-ready until it passes a real airplane-mode synthesis test.
