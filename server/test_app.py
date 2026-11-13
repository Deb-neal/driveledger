from contextlib import closing
import io
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest.mock import Mock
import wave
from concurrent.futures import ThreadPoolExecutor

from server.app import App, Quota

TOKEN = "a" * 64
FIXTURE = Path(__file__).resolve().parents[1] / "scripts/fixtures/fuel30k.wav"


def request(app, body=None, **headers):
    body = FIXTURE.read_bytes() if body is None else body
    env = {"REQUEST_METHOD": "POST", "PATH_INFO": "/transcribe", "QUERY_STRING": "",
           "HTTP_AUTHORIZATION": "Bearer " + TOKEN, "CONTENT_TYPE": "audio/wav",
           "CONTENT_LENGTH": str(len(body)), "wsgi.input": io.BytesIO(body)}
    env.update(headers)
    response = []
    data = b"".join(app(env, lambda status, fields: response.append((int(status.split()[0]), dict(fields)))))
    return response[0][0], json.loads(data)


class ServerTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "usage.sqlite3"
        self.clock = [172800]
        self.provider = Mock(return_value="주유 3만원")
        self.app = App(TOKEN, Quota(self.path, total=5, daily=3, minute=2, clock=lambda: self.clock[0]), self.provider)

    def test_success_and_no_audio_or_transcript_in_database(self):
        self.assertEqual((200, {"text": "주유 3만원"}), request(self.app))
        self.provider.assert_called_once()
        with closing(sqlite3.connect(self.path)) as db:
            self.assertEqual([("day", 2, 1), ("minute", 2880, 1), ("total", 0, 1)],
                             db.execute("SELECT * FROM usage ORDER BY scope").fetchall())

    def test_auth_and_invalid_transport_never_call_provider(self):
        for headers, code in [({"HTTP_AUTHORIZATION": ""}, 401),
                              ({"HTTP_AUTHORIZATION": "Bearer wrong"}, 401),
                              ({"HTTP_ORIGIN": "https://example.org"}, 401),
                              ({"CONTENT_TYPE": "application/json"}, 415),
                              ({"HTTP_TRANSFER_ENCODING": "chunked"}, 415),
                              ({"CONTENT_LENGTH": "300001"}, 413),
                              ({"CONTENT_LENGTH": "invalid"}, 413),
                              ({"QUERY_STRING": "token=secret"}, 404)]:
            self.assertEqual(code, request(self.app, **headers)[0])
        self.provider.assert_not_called()

    def test_invalid_audio_and_silence(self):
        self.assertEqual(400, request(self.app, b"x" * 100)[0])
        self.assertEqual(400, request(self.app, FIXTURE.read_bytes()[:-100])[0])
        for rate, sample, expected in [(16000, 0, 200), (8000, 1000, 400)]:
            stream = io.BytesIO()
            with wave.open(stream, "wb") as wav:
                wav.setparams((1, 2, rate, 0, "NONE", "not compressed"))
                wav.writeframes(sample.to_bytes(2, "little", signed=True) * rate)
            self.assertEqual(expected, request(self.app, stream.getvalue())[0])
        self.provider.assert_not_called()

    def test_quota_survives_restart_and_rolls_periods_not_total(self):
        for _ in range(2):
            self.assertEqual(200, request(self.app)[0])
        self.assertEqual(429, request(self.app)[0])
        restarted = App(TOKEN, Quota(self.path, 5, 3, 2, lambda: self.clock[0]), self.provider)
        self.assertEqual(429, request(restarted)[0])
        self.clock[0] += 60
        self.assertEqual(200, request(restarted)[0])
        self.assertEqual(429, request(restarted)[0])  # daily cap
        self.clock[0] += 86400
        for _ in range(2):
            self.assertEqual(200, request(restarted)[0])
        self.clock[0] += 86400
        self.assertEqual(429, request(restarted)[0])  # lifetime cap
        self.assertEqual(5, self.provider.call_count)

    def test_upstream_failure_redacted_and_counts_without_retry(self):
        self.provider.side_effect = RuntimeError("private-audio secret-key " + TOKEN)
        self.assertEqual((502, {"error": "transcription_unavailable"}), request(self.app))
        self.assertEqual(1, self.provider.call_count)
        with closing(sqlite3.connect(self.path)) as db:
            self.assertEqual(1, db.execute("SELECT used FROM usage WHERE scope='total'").fetchone()[0])

    def test_busy_rejected_and_lock_released_after_failure(self):
        with self.app.slot:
            self.assertEqual((429, {"error": "busy"}), request(self.app))
        self.provider.return_value = "x" * 201
        self.assertEqual(502, request(self.app)[0])
        self.provider.return_value = "주차 삼천 원"
        self.assertEqual(200, request(self.app)[0])

    def test_storage_failure_fails_closed(self):
        self.app.quota.reserve = Mock(side_effect=sqlite3.OperationalError("private-path"))
        self.assertEqual((503, {"error": "service_unavailable"}), request(self.app))
        self.provider.assert_not_called()

    def test_atomic_reservations_across_connections(self):
        quota = Quota(self.path, 5, 5, 5)
        with ThreadPoolExecutor(max_workers=10) as pool:
            outcomes = list(pool.map(lambda _: quota.reserve(), range(20)))
        self.assertEqual(5, sum(outcomes))

    def test_health_is_public_but_has_no_usage_or_credentials(self):
        self.assertEqual((200, {"service": "driveledger-voice", "status": "ok"}),
                         request(self.app, REQUEST_METHOD="GET", PATH_INFO="/health", HTTP_AUTHORIZATION=""))
        self.provider.assert_not_called()

    def test_malformed_token_rejected_at_startup(self):
        with self.assertRaises(ValueError):
            App("weak-token", self.app.quota, self.provider)


if __name__ == "__main__":
    unittest.main()
