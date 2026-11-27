#!/usr/bin/env python3
"""Bundle the official VoiceTut-TTS reference-speaker WAV files into the APK."""

from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "android/app/src/main/assets/reference_speakers"
BASE_URL = (
    "https://raw.githubusercontent.com/MohammedAly22/VoiceTuT-TTS/"
    "44cba00cd3ddb04b5213353e19e960045e332da7/reference_speakers/"
)
FILES = [
    "ahmed.wav",
    "ali.wav",
    "hadeer.wav",
    "hanan.wav",
    "hassan.wav",
    "karim.wav",
    "mahmoud.wav",
    "mariam.wav",
    "mohammed.wav",
    "mostafa.wav",
    "ramy.wav",
    "rana.wav",
    "sarah.wav",
    "shaker.wav",
    "tarek.wav",
    "yara.wav",
    "yasmin.wav",
    "yehia.wav",
    "youssef.wav",
    "references.json",
]


def download(name: str) -> None:
    request = Request(
        BASE_URL + name,
        headers={"User-Agent": "Talker-Android-Build"},
    )
    with urlopen(request, timeout=45) as response:
        if response.status != 200:
            raise RuntimeError(f"Failed to fetch {name}: HTTP {response.status}")
        data = response.read()

    if name.endswith(".wav"):
        if len(data) < 44 or data[:4] != b"RIFF" or data[8:12] != b"WAVE":
            raise RuntimeError(f"{name} is not a valid RIFF/WAVE file")

    target = ASSET_DIR / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    print(f"Bundled {name}: {len(data):,} bytes")


def main() -> None:
    for filename in FILES:
        download(filename)

    actual = sorted(path.name for path in ASSET_DIR.glob("*.wav"))
    if len(actual) != 19:
        raise RuntimeError(f"Expected 19 WAV files, found {len(actual)}")
    print(f"Verified {len(actual)} VoiceTut reference voices")


if __name__ == "__main__":
    main()
