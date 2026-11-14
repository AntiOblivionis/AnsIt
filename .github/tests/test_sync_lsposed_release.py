import copy
import base64
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
             patch.object(sync, "all_releases", side_effect=[[source], [target]]), \
             patch.object(sync, "sync_readme_files") as sync_readme:
            sync.main()
        self.assertEqual([], self.github.writes)
        sync_readme.assert_called_once_with(None, None, verify_only=True)

    def test_missing_token_never_attempts_a_write(self):
        with patch.dict(os.environ, {"GITHUB_REPOSITORY": sync.SOURCE}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "XPOSED_SYNC_TOKEN"):
                sync.main()
        self.assertEqual([], self.github.writes)

    def test_write_verification_preserves_existing_release_body(self):
        source = source_release()
        source.update({"draft": False, "prerelease": False, "published_at": sync.FIRST_RELEASE})
        body = sync.release_body(source)
        target = {"id": 100, "tag_name": "17-0.9.1", "draft": False, "body": body,
                  "assets": [{"name": "AnsIt-0.9.1.apk", "digest": DIGEST}]}
        self.github.releases[100] = copy.deepcopy(target)
        env = {"GITHUB_REPOSITORY": sync.SOURCE, "XPOSED_SYNC_TOKEN": "fake", "VERIFY_WRITE": "true"}
        with patch.dict(os.environ, env, clear=True), \
             patch.object(sync, "all_releases", side_effect=[[source], [target], [target]]), \
             patch.object(sync, "sync_readme_files"):
            sync.main()
        self.assertEqual(["patch"], self.github.writes)
        self.assertEqual(body, self.github.releases[100]["body"])

    def test_read_only_and_write_verification_are_mutually_exclusive(self):
        env = {"GITHUB_REPOSITORY": sync.SOURCE, "VERIFY_ONLY": "true", "VERIFY_WRITE": "true"}
        with patch.dict(os.environ, env, clear=True):
            with self.assertRaisesRegex(RuntimeError, "cannot run together"):
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


class ReadmeSyncTest(unittest.TestCase):
    def setUp(self):
        self.files = {
            (sync.SOURCE, "README.md"): "# AnsIt\r\n中文说明\r\n".encode("utf-8"),
            (sync.SOURCE, "LICENSE"): b"MIT license\n",
            (sync.SOURCE, "design/ansit-icon.svg"): b"<svg></svg>\n",
            (sync.TARGET, "README.md"): b"previous README\n",
        }
        self.writes = []
        self.source_refs = []
        self.deny_target = False
        self.corrupt_source = False

    @staticmethod
    def blob(content):
        return sync.sha1(b"blob " + str(len(content)).encode("ascii") + b"\0" + content).hexdigest()

    def api(self, path, *, method="GET", data=None, token=None):
        if path == f"/repos/{sync.SOURCE}/commits/main":
            return {"sha": "a" * 40}
        for repo in (sync.SOURCE, sync.TARGET):
            prefix = f"/repos/{repo}/contents/"
            if not path.startswith(prefix):
                continue
            name, _, query = path[len(prefix):].partition("?")
            key = (repo, name)
            if method == "PUT":
                self.assertEqual("target-token", token)
                previous = self.files.get(key)
                self.assertEqual(self.blob(previous) if previous is not None else None, data.get("sha"))
                self.files[key] = base64.b64decode(data["content"], validate=True)
                self.writes.append(name)
                return {}
            if repo == sync.SOURCE:
                self.source_refs.append(query)
            if repo == sync.TARGET and self.deny_target:
                raise sync.GitHubError(403)
            if key not in self.files:
                raise sync.GitHubError(404)
            content = self.files[key]
            sha = "0" * 40 if repo == sync.SOURCE and self.corrupt_source else self.blob(content)
            return {"type": "file", "encoding": "base64", "size": len(content),
                    "sha": sha, "content": base64.b64encode(content).decode("ascii")}
        raise AssertionError(path)

    def test_exact_bytes_assets_first_and_no_redundant_writes(self):
        with patch.object(sync, "api", side_effect=self.api):
            sync.sync_readme_files("source-token", "target-token")
            self.assertEqual(list(sync.README_FILES), self.writes)
            for name in sync.README_FILES:
                self.assertEqual(self.files[(sync.SOURCE, name)], self.files[(sync.TARGET, name)])
            self.assertTrue(all(ref == "ref=" + "a" * 40 for ref in self.source_refs))
            sync.sync_readme_files("source-token", "target-token")
        self.assertEqual(list(sync.README_FILES), self.writes)

    def test_read_only_does_not_write_files(self):
        with patch.object(sync, "api", side_effect=self.api):
            sync.sync_readme_files("source-token", "target-token", verify_only=True)
        self.assertEqual([], self.writes)
        self.assertEqual(b"previous README\n", self.files[(sync.TARGET, "README.md")])

    def test_permission_error_is_not_treated_as_a_missing_file(self):
        self.deny_target = True
        with patch.object(sync, "api", side_effect=self.api):
            with self.assertRaises(sync.GitHubError):
                sync.sync_readme_files("source-token", "target-token")
        self.assertEqual([], self.writes)

    def test_invalid_upstream_blob_is_rejected_before_writes(self):
        self.corrupt_source = True
        with patch.object(sync, "api", side_effect=self.api):
            with self.assertRaisesRegex(RuntimeError, "blob hash mismatch"):
                sync.sync_readme_files("source-token", "target-token")
        self.assertEqual([], self.writes)


if __name__ == "__main__":
    unittest.main()
