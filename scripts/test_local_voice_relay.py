import importlib.util
import io
import json
from pathlib import Path
import threading
import unittest
import urllib.request
import urllib.error
from unittest.mock import Mock
import wave

spec = importlib.util.spec_from_file_location("relay", Path(__file__).with_name("local-voice-relay.py"))
relay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(relay)


def audio(signal=True, frames=16000):
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setparams((1, 2, 16000, 0, "NONE", "not compressed"))
        wav.writeframes((b"\x00\x10" if signal else b"\x00\x00") * frames)
    return buffer.getvalue()


class RelayTests(unittest.TestCase):
    def setUp(self):
        self.upstream = Mock(return_value="주유 3만원")
        self.server = relay.Relay(("127.0.0.1", 0), self.upstream)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def request(self, data, extra=None):
        headers = {"Content-Type": "audio/wav", "X-DriveLedger-Local": "1"}
        headers.update(extra or {})
        req = urllib.request.Request(f"http://127.0.0.1:{self.server.server_port}/transcribe", data=data, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=2) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            with error:
                return error.code, json.load(error)

    def test_valid_request_returns_text_once(self):
        self.assertEqual(self.request(audio()), (200, {"text": "주유 3만원"}))
        self.upstream.assert_called_once()

    def test_silence_and_bad_audio_never_reach_provider(self):
        self.assertEqual(self.request(audio(False)), (200, {"text": ""}))
        self.assertEqual(self.request(b"x" * 100)[0], 400)
        self.assertEqual(self.request(audio(frames=128001))[0], 400)
        self.assertEqual(self.request(b"x" * 300001)[0], 413)
        self.upstream.assert_not_called()

    def test_browser_origin_and_wrong_content_type_rejected(self):
        self.assertEqual(self.request(audio(), {"Origin": "https://example.com"})[0], 403)
        self.assertEqual(self.request(audio(), {"Content-Type": "text/plain"})[0], 415)
        self.upstream.assert_not_called()

    def test_session_limit_and_busy_do_not_call_provider(self):
        self.server.requests_sent = 40
        self.assertEqual(self.request(audio())[0], 429)
        with self.server.slot:
            self.assertEqual(self.request(audio())[0], 429)
        self.upstream.assert_not_called()

    def test_provider_error_is_redacted_without_retry(self):
        self.upstream.side_effect = RuntimeError("secret-provider-body")
        status, body = self.request(audio())
        self.assertEqual(status, 502)
        self.assertNotIn("secret", json.dumps(body))
        self.upstream.assert_called_once()


if __name__ == "__main__":
    unittest.main()
