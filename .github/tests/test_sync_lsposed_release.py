import copy
from hashlib import sha256
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "sync_lsposed_release.py"
spec = importlib.util.spec_from_file_location("sync_lsposed_release", SCRIPT)
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)
APK = b"fixture APK bytes"
DIGEST = "sha256:" + sha256(APK).hexdigest()


def source_release():
    return {
        "id": 12345, "tag_name": "0.9.1", "html_url": "https://github.com/AntiOblivionis/AnsIt/releases/tag/0.9.1",
        "body": "Release notes", "assets": [{
            "name": "AnsIt-0.9.1.apk", "size": len(APK), "digest": DIGEST,
            "browser_download_url": "https://github.com/AntiOblivionis/AnsIt/releases/download/0.9.1/AnsIt-0.9.1.apk",
        }],
    }


class FakeGitHub:
    def __init__(self):
        self.releases = {}
        self.writes = []
        self.fail_upload = False

    def api(self, path, *, method="GET", data=None, token=None):
        if method == "POST" and path == f"/repos/{sync.TARGET}/releases":
            self.releases[100] = dict(data, id=100, assets=[])
            self.writes.append("create")
        release = self.releases[100]
        if method == "PATCH":
            release.update(data)
            self.writes.append("patch")
        return copy.deepcopy(release)

    def request(self, url, *, method="GET", payload=None, token=None, content_type=None):
        if method == "GET":
            return APK
        if self.fail_upload:
            raise RuntimeError("simulated upload interruption")
        asset = {"name": "AnsIt-0.9.1.apk", "digest": "sha256:" + sha256(payload).hexdigest()}
        self.releases[100]["assets"] = [asset]
        self.writes.append("upload")
        return json.dumps(asset).encode()


class SyncTest(unittest.TestCase):
    def setUp(self):
        self.github = FakeGitHub()
        self.patches = [
            patch.object(sync, "api", side_effect=self.github.api),
            patch.object(sync, "request", side_effect=self.github.request),
            patch.object(sync, "inspect_apk", return_value=("17", "0.9.1")),
        ]
        for item in self.patches:
            item.start()
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()
        for item in reversed(self.patches):
            item.stop()

    def test_publish_then_repeat_without_writes(self):
        source = source_release()
        sync.sync_one(source, [], "fake", self.path)
        release = self.github.releases[100]
        self.assertEqual("17-0.9.1", release["tag_name"])
        self.assertFalse(release["draft"])
        self.assertEqual(DIGEST, release["assets"][0]["digest"])
        writes = list(self.github.writes)
        sync.sync_one(source, [copy.deepcopy(release)], "fake", self.path)
        self.assertEqual(writes, self.github.writes)

    def test_retry_completes_an_interrupted_draft(self):
        self.github.fail_upload = True
        with self.assertRaisesRegex(RuntimeError, "interruption"):
            sync.sync_one(source_release(), [], "fake", self.path)
        self.assertTrue(self.github.releases[100]["draft"])
        self.github.fail_upload = False
        sync.sync_one(source_release(), [copy.deepcopy(self.github.releases[100])], "fake", self.path)
        self.assertFalse(self.github.releases[100]["draft"])

    def test_changed_existing_asset_is_never_overwritten(self):
        sync.sync_one(source_release(), [], "fake", self.path)
        self.github.releases[100]["assets"][0]["digest"] = "sha256:" + "0" * 64
        writes = list(self.github.writes)
        with self.assertRaisesRegex(RuntimeError, "manual review"):
            sync.sync_one(source_release(), [copy.deepcopy(self.github.releases[100])], "fake", self.path)
        self.assertEqual(writes, self.github.writes)

    def test_source_note_edits_are_mirrored(self):
        source = source_release()
        sync.sync_one(source, [], "fake", self.path)
        source["body"] = "Updated release notes"
        sync.sync_one(source, [copy.deepcopy(self.github.releases[100])], "fake", self.path)
        self.assertIn("Updated release notes", self.github.releases[100]["body"])

    def test_bad_download_cannot_create_a_release(self):
        source = source_release()
        source["assets"][0]["digest"] = "sha256:" + "0" * 64
        with self.assertRaisesRegex(RuntimeError, "differs"):
            sync.sync_one(source, [], "fake", self.path)
        self.assertEqual([], self.github.writes)

    def test_read_only_mode_checks_without_writes_or_target_token(self):
        source = source_release()
        source.update({"draft": False, "prerelease": False, "published_at": sync.FIRST_RELEASE})
        target = {"id": 100, "tag_name": "17-0.9.1", "draft": False,
                  "body": sync.release_body(source), "assets": [{"name": "AnsIt-0.9.1.apk", "digest": DIGEST}]}
        with patch.dict(os.environ, {"GITHUB_REPOSITORY": sync.SOURCE, "VERIFY_ONLY": "true"}, clear=True), \
             patch.object(sync, "all_releases", side_effect=[[source], [target]]):
            sync.main()
        self.assertEqual([], self.github.writes)

    def test_missing_token_never_attempts_a_write(self):
        with patch.dict(os.environ, {"GITHUB_REPOSITORY": sync.SOURCE}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "XPOSED_SYNC_TOKEN"):
                sync.main()
        self.assertEqual([], self.github.writes)

    def test_certificate_verifier_accepts_old_and_new_build_tools_output(self):
        for prefix in ("Signer #1", "V3.0 Signer:"):
            output = f"Number of signers: 1\n{prefix} certificate SHA-256 digest: {sync.SIGNER_SHA256}\n"
            sync.verify_signer(output)

    def test_certificate_verifier_rejects_changed_or_multiple_signers(self):
        unknown = "0" * 64
        for output in (
            f"Number of signers: 1\nV3.0 Signer: certificate SHA-256 digest: {unknown}\n",
            f"Number of signers: 2\nSigner #1 certificate SHA-256 digest: {sync.SIGNER_SHA256}\n",
            f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {sync.SIGNER_SHA256}\nV3.0 Signer: certificate SHA-256 digest: {unknown}\n",
        ):
            with self.assertRaisesRegex(RuntimeError, "certificate"):
                sync.verify_signer(output)


if __name__ == "__main__":
    unittest.main()
