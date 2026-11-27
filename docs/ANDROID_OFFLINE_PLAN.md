# Talker Android Offline Port — Engineering Plan

## Objective

Port the existing VoiceTut-TTS experience to a native Android application. After the model assets are downloaded once, text-to-speech, zero-shot voice cloning, playback, export/share, and history must work without a server or internet connection.

## Existing implementation findings

The current Python engine in `voicetut_tts/engine.py` wraps OmniVoice and calls `OmniVoice.from_pretrained`. It expects PyTorch, NumPy, the OmniVoice package, model checkpoint files, and speaker reference assets. The current React UI in `web/` calls the Gradio API exposed by `voicetut_tts/server.py`; wrapping that UI in a WebView would therefore not create an offline app.

## Proposed Android architecture

- Native Kotlin application and local UI.
- On-device inference runtime, with a tested export of the *fine-tuned VoiceTut-TTS weights*, not an unrelated base checkpoint.
- App-private model store with download/resume, SHA-256 verification, version metadata, and disk-space checks.
- Local audio pipeline for reference recording/import, preprocessing, and playback.
- Room/SQLite history containing text, voice mode, timestamps, and generated-file URI.
- Android Sharesheet and MediaStore/document export.
- No inference server, remote API, telemetry dependency, or network permission during ordinary inference.

## Critical model-porting gate

An existing community OmniVoice-to-LiteRT export is evidence that parts of the base OmniVoice architecture may be exportable. It does not prove that VoiceTut-TTS's fine-tuned checkpoint, tokenizer/configuration, audio encoder/reference-audio path, or Egyptian voice conditioning can be used by that export.

Before implementing production inference, validate all of the following:

1. Reconstruct the exact VoiceTut-TTS model architecture and checkpoint mapping.
2. Export backbone and decoder with numerically valid dynamic input shapes.
3. Verify the exported checkpoint reproduces reference outputs on desktop.
4. Port the full reference-audio encoder/conditioning path required by zero-shot cloning.
5. Run end-to-end inference on a physical Android device.
6. Compare Egyptian Arabic pronunciation, speaker similarity, audio quality, latency, peak RAM, thermal behavior, and storage.
7. Only then enable the generation and cloning controls in the app.

## Delivery milestones

- M0: isolated Android branch and native buildable shell.
- M1: reproducible model export and desktop parity tests.
- M2: Android inference proof of concept using VoiceTut-TTS weights.
- M3: model download manager and offline verification.
- M4: local cloning, playback, WAV export/share, and history.
- M5: airplane-mode test, performance profiling, release APK.

## Model size and device constraints

Model size, peak RAM, and speed must be measured from the actual exported VoiceTut-TTS artifacts. Do not treat estimates from a different OmniVoice checkpoint or a different device as a guarantee. Do not silently replace the Egyptian fine-tune with another TTS model if export fails.
