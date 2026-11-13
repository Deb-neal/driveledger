#!/usr/bin/env python3
"""Compare four pinned synthetic clips. Default validates locally; --execute sends to OpenAI."""
import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request
import uuid
import wave

ROOT = Path(__file__).resolve().parents[1]
MODEL = "gpt-transcribe"
ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
VEHICLE_KEYWORDS = ("주유", "충전", "주차", "통행료", "정비", "보험료", "자동차세")


def load_key():
    key = os.environ.get("OPENAI_API_KEY", "").strip()
    local = ROOT / ".env.local"
    if not key and local.exists():
        for line in local.read_text().splitlines():
            if line.startswith("OPENAI_API_KEY="):
                key = line.partition("=")[2].strip().strip("\"'")
                break
    if not key or any(c.isspace() for c in key):
        raise ValueError("Set OPENAI_API_KEY in .env.local or the process environment; never paste it in chat.")
    return key


def validate_clip(item):
    path = (ROOT / item["path"]).resolve()
    if not path.is_relative_to((ROOT / "scripts/fixtures").resolve()):
        raise ValueError("Fixture is outside the synthetic comparison directory")
    if path.stat().st_size > 300_000:
        raise ValueError("Fixture exceeds the eight-second WAV limit")
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != item["sha256"]:
        raise ValueError("Fixture changed; do not upload an unreviewed recording")
    with wave.open(io.BytesIO(data)) as audio:
        if (audio.getframerate(), audio.getnchannels(), audio.getsampwidth(), audio.getcomptype()) != (16000, 1, 2, "NONE"):
            raise ValueError("Expected 16 kHz mono PCM16 WAV")
        frames = audio.getnframes()
        if not 4000 <= frames <= 128000 or len(audio.readframes(frames)) != frames * 2:
            raise ValueError("Invalid or truncated WAV")
    return data, frames / 16000


def make_request(data, key, vehicle_keywords=False):
    boundary = "driveledger-" + uuid.uuid4().hex
    chunks = []
    fields = [("model", MODEL), ("languages[]", "ko")]
    if vehicle_keywords:
        fields.extend(("keywords[]", word) for word in VEHICLE_KEYWORDS)
    for name, value in fields:
        chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    # Neutral filename; expected category/amount must never become an input hint.
    chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="audio.wav"\r\nContent-Type: audio/wav\r\n\r\n'.encode())
    chunks.extend((data, f"\r\n--{boundary}--\r\n".encode()))
    return urllib.request.Request(ENDPOINT, data=b"".join(chunks), headers={
        "Authorization": "Bearer " + key,
        "Content-Type": "multipart/form-data; boundary=" + boundary,
    }, method="POST")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def transcribe(data, key, opener, vehicle_keywords=False, timeout=30):
    with opener.open(make_request(data, key, vehicle_keywords), timeout=timeout) as response:
        payload = response.read(65537)
    if len(payload) > 65536:
        raise ValueError("Response too large")
    text = json.loads(payload).get("text")
    if not isinstance(text, str) or len(text) > 2000:
        raise ValueError("Invalid transcript response")
    return text.replace(key, "[REDACTED]")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true", help="Send the four synthetic clips; incurs API usage")
    parser.add_argument("--vehicle-keywords", action="store_true", help="Use the same seven category hints for every clip; no amount hints")
    args = parser.parse_args()
    items = json.loads((ROOT / "docs/online-stt-fixtures.json").read_text())
    if len(items) != 4:
        raise ValueError("This comparison is limited to four synthetic clips")
    clips = [(item, *validate_clip(item)) for item in items]
    seconds = sum(duration for _, _, duration in clips)
    print(f"Validated {len(clips)} synthetic clips, {seconds:.3f}s; model={MODEL}")
    if not args.execute:
        print("Dry run: no key read, no network request.")
        return
    key = load_key()
    opener = urllib.request.build_opener(NoRedirect())
    output = ROOT / "artifacts/phase3/asr-diagnostic" / ("online-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + ".json")
    output.parent.mkdir(parents=True, exist_ok=True)
    results = []
    for item, data, duration in clips:
        started = time.monotonic()
        row = {**item, "model": MODEL, "audio_seconds": duration,
               "keywords": list(VEHICLE_KEYWORDS) if args.vehicle_keywords else []}
        try:
            row["text"] = transcribe(data, key, opener, args.vehicle_keywords)
            row["status"] = "ok"
        except urllib.error.HTTPError as error:
            row.update(status="error", error=f"HTTP {error.code}")
        except (OSError, ValueError, urllib.error.URLError):
            row.update(status="error", error="Network or response error")
        row["elapsed_seconds"] = round(time.monotonic() - started, 3)
        results.append(row)
        output.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n")
        print(item["id"] + ": " + row["status"])
        if row["status"] == "error":
            print("Stopped after error; no automatic retries. " + str(output))
            return 1
    print("Raw transcripts saved; app parser and human-speech checks still required: " + str(output))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as error:
        # Never print raw exception bodies that could include credentials or upstream content.
        print("Local setup/fixture validation failed (" + type(error).__name__ + "). Check key and fixture configuration.")
        raise SystemExit(1)
