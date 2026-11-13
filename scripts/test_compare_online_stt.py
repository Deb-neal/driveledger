"""Local checks only; never contacts the API."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock
import urllib.error
import wave
from email.parser import BytesParser
from email.policy import default

spec = importlib.util.spec_from_file_location("compare", Path(__file__).with_name("compare-online-stt.py"))
compare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compare)


class ComparisonTests(unittest.TestCase):
    def test_multipart_has_audio_and_language_without_answer_hints(self):
        request = compare.make_request(b"RIFF-test", "test-secret")
        mime = BytesParser(policy=default).parsebytes(
            ("Content-Type: " + request.get_header("Content-type") + "\r\n\r\n").encode() + request.data)
        parts = list(mime.iter_parts())
        self.assertEqual([p.get_param("name", header="content-disposition") for p in parts], ["model", "languages[]", "file"])
        self.assertEqual(parts[-1].get_filename(), "audio.wav")
        self.assertEqual(parts[-1].get_payload(decode=True), b"RIFF-test")
        self.assertEqual(parts[1].get_payload(decode=True), b"ko")
        self.assertNotIn(b"test-secret", request.data)
        self.assertEqual(request.full_url, compare.ENDPOINT)

    def test_fixture_rejects_changed_audio_and_wrong_format(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(compare, "ROOT", Path(directory)):
            path = Path(directory) / "scripts/fixtures/fixture.wav"
            path.parent.mkdir(parents=True)
            with wave.open(str(path), "wb") as audio:
                audio.setparams((1, 2, 16000, 0, "NONE", "not compressed"))
                audio.writeframes(b"\x00\x00" * 16000)
            item = {"path": str(path.relative_to(directory)), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
            self.assertEqual(compare.validate_clip(item)[1], 1)
            path.write_bytes(path.read_bytes() + b"changed")
            with self.assertRaises(ValueError):
                compare.validate_clip(item)

            with wave.open(str(path), "wb") as audio:
                audio.setparams((2, 2, 16000, 0, "NONE", "not compressed"))
                audio.writeframes(b"\x00" * 64000)
            item["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
            with self.assertRaises(ValueError):
                compare.validate_clip(item)

    def test_keyword_variant_has_all_categories_and_no_expected_amount(self):
        request = compare.make_request(b"RIFF-test", "test-secret", True)
        mime = BytesParser(policy=default).parsebytes(
            ("Content-Type: " + request.get_header("Content-type") + "\r\n\r\n").encode() + request.data)
        values = [p.get_payload(decode=True).decode() for p in mime.iter_parts()
                  if p.get_param("name", header="content-disposition") == "keywords[]"]
        self.assertEqual(values, list(compare.VEHICLE_KEYWORDS))
        self.assertFalse(any(c.isdigit() for value in values for c in value))

    def test_response_validation_and_no_retry(self):
        opener = Mock()
        opener.open.return_value = io.BytesIO(json.dumps({"text": "주유 3만원"}).encode())
        self.assertEqual(compare.transcribe(b"audio", "test-secret", opener), "주유 3만원")
        opener.open.return_value = io.BytesIO(b'{"text":null}')
        with self.assertRaises(ValueError):
            compare.transcribe(b"audio", "test-secret", opener)
        opener.reset_mock()
        opener.open.side_effect = urllib.error.HTTPError(compare.ENDPOINT, 429, "rate limit", {}, None)
        with self.assertRaises(urllib.error.HTTPError):
            compare.transcribe(b"audio", "test-secret", opener)
        self.assertEqual(opener.open.call_count, 1)

    def test_dry_run_does_not_read_key_or_build_network_client(self):
        with patch("sys.argv", ["compare"]), patch.object(compare, "load_key") as key, patch.object(compare.urllib.request, "build_opener") as network:
            compare.main()
            key.assert_not_called()
            network.assert_not_called()

    def test_redirect_refused(self):
        self.assertIsNone(compare.NoRedirect().redirect_request(None, None, 302, "", {}, "https://example.com"))


if __name__ == "__main__":
    unittest.main()
