#!/usr/bin/env python3
"""Apply Android-only build guards to the pinned audio.cpp checkout.

The upstream CLI/server/GGUF executable targets are desktop utilities. AGP
attempts to build every CMake executable target it discovers, and the CLI's
POSIX process-spawn code is unavailable in the Android NDK. Keep the engine,
model runtime and C API intact while omitting those executables on Android.
"""
from pathlib import Path

cmake = Path("third_party/audio.cpp/CMakeLists.txt")
if not cmake.is_file():
    raise SystemExit(f"Missing pinned engine CMake file: {cmake}")

source = cmake.read_text(encoding="utf-8")

def guard_block(start_marker: str, end_marker: str, label: str) -> None:
    global source
    start = source.find(start_marker)
    if start < 0:
        raise SystemExit(f"Could not find {label} start marker")
    end = source.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"Could not find {label} end marker")
    prefix = f"if(NOT ANDROID) # TALKER_ANDROID_EXCLUDE_{label}\n"
    suffix = f"\nendif() # TALKER_ANDROID_EXCLUDE_{label}\n\n"
    if source[max(0, start - len(prefix) - 12):start].find(f"TALKER_ANDROID_EXCLUDE_{label}") >= 0:
        return
    source = source[:start] + prefix + source[start:end] + suffix + source[end:]

# Desktop CLI includes workflow.cpp (posix_spawnp) and is not needed by JNI.
guard_block(
    "file(GLOB AUDIOCPP_RECIPE_PLUGIN_SOURCES",
    "# C ABI (opt-in)",
    "CLI",
)

# The demo voice assets, HTTP server and GGUF utility are also desktop-only.
# The C ABI block above remains outside this guard and is built for Android.
guard_block(
    'set(AUDIOCPP_DEMO_VOICE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/webui/native/demo_voices")',
    "# Shared test setup; declarations and category selection stay separate.",
    "SERVER_AND_TOOLS",
)

cmake.write_text(source, encoding="utf-8")
print("Applied Android-only guards: CLI, server, GGUF and model-manager tools excluded; C API retained.")
