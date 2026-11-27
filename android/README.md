# Talker Android — Offline Edition

This is a separate native Android application track. It does not embed the existing Gradio server or call the Talker web backend.

## Current status

- Native Android project scaffold created.
- Dark Arabic-first application shell created.
- No server URL or backend API is present.
- Model inference is intentionally not represented as working yet.
- VoiceTut-TTS model export and on-device inference are the next engineering milestone.

## Build

Open this `android/` directory in Android Studio, or build with Gradle 8.9 and JDK 17:

```bash
gradle assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Offline requirement

The intended flow is: download model assets once from their published model repository, verify their checksums, store them in app-private storage, and then run synthesis with networking disabled. The Android app must not depend on a local computer, Gradio, Hugging Face Inference API, or any hosted inference endpoint.

The current scaffold does not yet download model assets or synthesize audio.
