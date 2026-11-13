"""Submission voice relay. Run behind Render HTTPS with server/gunicorn.conf.py."""
from contextlib import closing
import hmac
import importlib.util
import json
import os
from pathlib import Path
import re
import sqlite3
import threading
import time
import urllib.request
from http import HTTPStatus

spec = importlib.util.spec_from_file_location("local_relay", Path(__file__).resolve().parents[1] / "scripts/local-voice-relay.py")
local = importlib.util.module_from_spec(spec)
spec.loader.exec_module(local)


class Quota:
    """Reserve paid attempts atomically; failed upstream requests also consume quota."""
    def __init__(self, path, total=1000, daily=200, minute=10, clock=time.time):
        self.path, self.limits, self.clock = str(path), (total, daily, minute), clock
        if any(type(n) is not int or n < 1 for n in self.limits):
            raise ValueError("Invalid request limits")
        with closing(sqlite3.connect(self.path)) as db, db:
            db.execute("CREATE TABLE IF NOT EXISTS usage (scope TEXT PRIMARY KEY, period INTEGER NOT NULL, used INTEGER NOT NULL)")

    def reserve(self):
        now = int(self.clock())
        with closing(sqlite3.connect(self.path, timeout=2)) as db, db:
            db.execute("BEGIN IMMEDIATE")
            updates = []
            for scope, period, limit in zip(("total", "day", "minute"), (0, now // 86400, now // 60), self.limits):
                row = db.execute("SELECT period, used FROM usage WHERE scope=?", (scope,)).fetchone()
                used = row[1] if row and row[0] == period else 0
                if used >= limit:
                    return False
                updates.append((scope, period, used + 1))
            db.executemany("INSERT OR REPLACE INTO usage VALUES (?, ?, ?)", updates)
        return True


class App:
    def __init__(self, token, quota, transcribe):
        if not re.fullmatch(r"[a-f0-9]{64}", token):
            raise ValueError("VOICE_RELAY_TOKEN must be 64 lowercase hexadecimal characters")
        self.authorization = ("Bearer " + token).encode()
        self.quota, self.transcribe = quota, transcribe
        self.slot = threading.Lock()

    def __call__(self, environ, start_response):
        try:
            status, payload = self.handle(environ)
        except Exception:
            # Never log provider exceptions, request content, credentials or transcripts.
            status, payload = 503, {"error": "service_unavailable"}
        body = json.dumps(payload, ensure_ascii=False).encode()
        start_response(f"{status} {HTTPStatus(status).phrase}", [
            ("Content-Type", "application/json; charset=utf-8"),
            ("Content-Length", str(len(body))), ("Cache-Control", "no-store"),
            ("X-Content-Type-Options", "nosniff"),
        ])
        return [body]

    def handle(self, env):
        route = (env.get("REQUEST_METHOD"), env.get("PATH_INFO"))
        if route == ("GET", "/health"):
            return 200, {"service": "driveledger-voice", "status": "ok"}
        if route != ("POST", "/transcribe") or env.get("QUERY_STRING"):
            return 404, {"error": "not_found"}
        supplied = env.get("HTTP_AUTHORIZATION", "").encode("utf-8")
        if env.get("HTTP_ORIGIN") or not hmac.compare_digest(supplied, self.authorization):
            return 401, {"error": "unauthorized"}
        if env.get("CONTENT_TYPE") != "audio/wav" or env.get("HTTP_TRANSFER_ENCODING"):
            return 415, {"error": "wav_required"}
        try:
            length = int(env.get("CONTENT_LENGTH", "0"))
        except ValueError:
            length = 0
        if not 44 <= length <= 300000:
            return 413, {"error": "size_limit"}
        if not self.slot.acquire(blocking=False):
            return 429, {"error": "busy"}
        try:
            try:
                data = env["wsgi.input"].read(length)
                if len(data) != length:
                    raise ValueError("Truncated body")
                signal = local.check_audio(data)
            except (ValueError, local.wave.Error, EOFError):
                return 400, {"error": "invalid_audio"}
            if not signal:
                return 200, {"text": ""}
            if not self.quota.reserve():
                return 429, {"error": "request_limit"}
            try:
                text = self.transcribe(data)
                if not isinstance(text, str) or len(text) > 200:
                    raise ValueError("Invalid text")
            except Exception:
                return 502, {"error": "transcription_unavailable"}
            return 200, {"text": text}
        finally:
            self.slot.release()


def create_app():
    # Production never reads the developer's .env.local.
    key = os.environ.get("OPENAI_API_KEY", "")
    token = os.environ.get("VOICE_RELAY_TOKEN", "")
    path = os.environ.get("VOICE_USAGE_DB", "")
    if not key or any(c.isspace() for c in key) or not Path(path).is_absolute():
        raise ValueError("Set OPENAI_API_KEY and absolute VOICE_USAGE_DB on a persistent disk")
    quota = Quota(path, *(int(os.environ.get(name, default)) for name, default in (
        ("VOICE_TOTAL_LIMIT", "1000"), ("VOICE_DAILY_LIMIT", "200"), ("VOICE_MINUTE_LIMIT", "10"))))
    opener = urllib.request.build_opener(local.comparison.NoRedirect())
    return App(token, quota, lambda data: local.comparison.transcribe(data, key, opener, True, timeout=12))
