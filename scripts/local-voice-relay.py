#!/usr/bin/env python3
"""Mac-only development relay. Bind loopback; adb reverse tcp:18765 tcp:18765.

Not a production server. At most 40 upstream requests per process, one at a time.
Audio and transcripts stay in memory; no request bodies or API keys are logged.
"""
import importlib.util
import io
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import struct
import threading
import urllib.request
import wave

spec = importlib.util.spec_from_file_location("comparison", Path(__file__).with_name("compare-online-stt.py"))
comparison = importlib.util.module_from_spec(spec)
spec.loader.exec_module(comparison)


def check_audio(data):
    with wave.open(io.BytesIO(data)) as audio:
        if (audio.getframerate(), audio.getnchannels(), audio.getsampwidth(), audio.getcomptype()) != (16000, 1, 2, "NONE"):
            raise ValueError("Unsupported WAV")
        frames = audio.getnframes()
        if not 4000 <= frames <= 128000:
            raise ValueError("Audio duration outside limits")
        pcm = audio.readframes(frames)
        if len(pcm) != frames * 2:
            raise ValueError("Truncated WAV")
    return sum((sample / 32768) ** 2 for (sample,) in struct.iter_unpack("<h", pcm)) / frames > .00001


class Relay(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 4

    def __init__(self, address, transcribe):
        self.transcribe = transcribe
        self.slot = threading.Lock()
        self.requests_sent = 0
        super().__init__(address, Handler)

    def handle_error(self, request, client_address):
        pass  # No raw exception/request logging.


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(15)

    def log_message(self, *args):
        pass

    def reply(self, status, payload):
        data = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            self.reply(200, {"service": "driveledger-local-voice", "model": comparison.MODEL,
                             "requests_sent": self.server.requests_sent, "request_limit": 40})
        else:
            self.reply(404, {"error": "not_found"})

    def do_POST(self):
        if self.path != "/transcribe":
            return self.reply(404, {"error": "not_found"})
        if self.headers.get("Origin") or self.headers.get("X-DriveLedger-Local") != "1":
            return self.reply(403, {"error": "local_app_only"})
        if self.headers.get("Content-Type") != "audio/wav" or self.headers.get("Transfer-Encoding"):
            return self.reply(415, {"error": "wav_required"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if not 44 <= length <= 300000:
            return self.reply(413, {"error": "size_limit"})
        if not self.server.slot.acquire(blocking=False):
            return self.reply(429, {"error": "busy"})
        try:
            data = self.rfile.read(length)
            try:
                if len(data) != length:
                    raise ValueError("Incomplete body")
                has_signal = check_audio(data)
            except (ValueError, wave.Error, EOFError):
                return self.reply(400, {"error": "invalid_audio"})
            if not has_signal:
                return self.reply(200, {"text": ""})
            if self.server.requests_sent >= 40:
                return self.reply(429, {"error": "session_limit"})
            self.server.requests_sent += 1
            try:
                text = self.server.transcribe(data)
                if not isinstance(text, str) or len(text) > 200:
                    raise ValueError("Invalid text")
            except Exception:
                return self.reply(502, {"error": "transcription_unavailable"})
            self.reply(200, {"text": text})
        finally:
            self.server.slot.release()


def main():
    key = comparison.load_key()
    opener = urllib.request.build_opener(comparison.NoRedirect())
    with Relay(("127.0.0.1", 18765), lambda data: comparison.transcribe(data, key, opener, True, timeout=12)) as server:
        print("Local online voice ready at 127.0.0.1:18765; max 40 requests; no audio logging.", flush=True)
        server.serve_forever()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
    except Exception:
        print("Relay could not start. Check local key and port availability.")
        raise SystemExit(1)
