#!/usr/bin/env python3
"""Mirror verified public AnsIt releases into this module repository."""

from hashlib import sha256
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
from urllib.error import HTTPError
from urllib.parse import quote, urlparse
from urllib.request import Request, urlopen

SOURCE = "AntiOblivionis/AnsIt"
TARGET = "Xposed-Modules-Repo/ltd.tgwgroup.ansit"
PACKAGE = "ltd.tgwgroup.ansit"
FIRST_RELEASE = "2026-11-14T15:46:47Z"  # 0.9.1; earlier releases use another package.
SIGNER_SHA256 = "ae5e3c2a76c6f8e8c327124c96c15b9ec92f964aecec59eb5654e4918a87a24b"
API = "https://api.github.com"
UPLOADS = "https://uploads.github.com"
MAX_APK_BYTES = 256 * 1024 * 1024


def request(url, *, method="GET", payload=None, token=None, content_type=None):
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "AnsIt-release-sync",
               "X-GitHub-Api-Version": "2022-11-28"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if content_type:
        headers["Content-Type"] = content_type
    try:
        with urlopen(Request(url, data=payload, headers=headers, method=method), timeout=90) as response:
            return response.read()
    except HTTPError as error:
        raise RuntimeError(f"GitHub request failed with HTTP {error.code}") from None


def api(path, *, method="GET", data=None, token=None):
    body = None if data is None else json.dumps(data, ensure_ascii=False).encode("utf-8")
    raw = request(API + path, method=method, payload=body, token=token,
                  content_type="application/json" if body is not None else None)
    return json.loads(raw)


def all_releases(repo, token=None):
    result = []
    page = 1
    while True:
        releases = api(f"/repos/{repo}/releases?per_page=100&page={page}", token=token)
        if not isinstance(releases, list):
            raise RuntimeError("Unexpected release list from GitHub")
        result.extend(releases)
        if len(releases) < 100:
            return result
        page += 1


def apk_asset(release):
    assets = [item for item in release.get("assets", []) if item["name"].lower().endswith(".apk")]
    if len(assets) != 1:
        raise RuntimeError(f"Release {release['tag_name']} must contain exactly one APK")
    asset = assets[0]
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,160}\.apk", asset["name"]):
        raise RuntimeError("Unexpected APK filename")
    if not 0 < asset.get("size", 0) <= MAX_APK_BYTES:
        raise RuntimeError("Unexpected APK size")
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", asset.get("digest") or ""):
        raise RuntimeError("GitHub has not provided a usable APK digest yet")
    return asset


def download_apk(asset, destination):
    parsed = urlparse(asset["browser_download_url"])
    if parsed.scheme != "https" or parsed.netloc.lower() != "github.com" or not parsed.path.startswith("/" + SOURCE + "/releases/download/"):
        raise RuntimeError("Unexpected upstream APK download URL")
    data = request(asset["browser_download_url"])
    if len(data) != asset["size"] or "sha256:" + sha256(data).hexdigest() != asset["digest"]:
        raise RuntimeError("Upstream APK differs from its published size or digest")
    destination.write_bytes(data)


def find_build_tool(name):
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    directory = sdk / "build-tools"
    if not directory.is_dir():
        raise RuntimeError("Android SDK build tools are unavailable")
    versions = []
    for child in directory.iterdir():
        if child.is_dir() and re.fullmatch(r"\d+(?:\.\d+)+", child.name):
            versions.append((tuple(int(part) for part in child.name.split(".")), child))
    suffix = (".bat" if name == "apksigner" else ".exe") if os.name == "nt" else ""
    for _, version in sorted(versions, reverse=True):
        path = version / (name + suffix)
        if path.is_file():
            return str(path)
    raise RuntimeError(f"Android build tool not found: {name}")


def inspect_apk(path):
    def run(arguments):
        try:
            return subprocess.run(arguments, check=True, capture_output=True, text=True, timeout=90).stdout
        except (OSError, subprocess.SubprocessError):
            raise RuntimeError("APK inspection or signature verification failed") from None

    badging = run([find_build_tool("aapt"), "dump", "badging", str(path)])
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not match or match.group(1) != PACKAGE or not re.fullmatch(r"[A-Za-z0-9._+-]{1,80}", match.group(3)):
        raise RuntimeError("APK package or version is not valid for this module")
    verification = run([find_build_tool("apksigner"), "verify", "--verbose", "--print-certs", str(path)])
    signer = re.search(r"^Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})\s*$", verification, re.M | re.I)
    if "Number of signers: 1" not in verification or not signer or signer.group(1).lower() != SIGNER_SHA256:
        details = [line for line in verification.splitlines()
                   if line.startswith("Number of signers:") or "sha-256" in line.lower() or "sha256" in line.lower()]
        print("APK verifier summary:", details)
        raise RuntimeError("APK signer count or certificate does not match the published AnsIt certificate")
    return match.group(2), match.group(3)


def release_body(source):
    marker = f"<!-- ansit-upstream-release-id:{source['id']} -->"
    original = (source.get("body") or "").strip()
    return (original + "\n\n" if original else "") + f"[原始 Release]({source['html_url']})\n\n{marker}\n"


def find_mirrored(source_id, releases):
    marker = f"<!-- ansit-upstream-release-id:{source_id} -->"
    found = [item for item in releases if marker in (item.get("body") or "")]
    if len(found) > 1:
        raise RuntimeError("Multiple module releases refer to the same source release")
    return found[0] if found else None


def confirm_asset(release, source_asset, *, allow_empty=False):
    assets = release.get("assets", [])
    if not assets and allow_empty:
        return False
    matching = [item for item in assets if item["name"] == source_asset["name"]]
    if len(assets) != 1 or len(matching) != 1 or matching[0].get("digest") != source_asset["digest"]:
        raise RuntimeError("Existing module release has an unexpected or incomplete asset; manual review is required")
    return True


def sync_one(source, target_releases, token, temporary):
    source_asset = apk_asset(source)
    body = release_body(source)
    existing = find_mirrored(source["id"], target_releases)
    if existing and not existing["draft"]:
        confirm_asset(existing, source_asset)
        if existing.get("body") != body:
            api(f"/repos/{TARGET}/releases/{existing['id']}", method="PATCH", data={"body": body}, token=token)
            print(f"Updated release notes: {existing['tag_name']}")
        else:
            print(f"Already synced: {existing['tag_name']}")
        return

    path = temporary / source_asset["name"]
    download_apk(source_asset, path)
    code, version = inspect_apk(path)
    tag = f"{code}-{version}"
    if existing:
        if existing["tag_name"] != tag:
            raise RuntimeError("Existing draft has an unexpected tag")
        has_asset = confirm_asset(existing, source_asset, allow_empty=True)
    else:
        if any(item["tag_name"] == tag for item in target_releases):
            raise RuntimeError(f"Release {tag} already exists without a matching sync marker")
        existing = api(f"/repos/{TARGET}/releases", method="POST", token=token, data={
            "tag_name": tag, "target_commitish": "main", "name": version, "body": body,
            "draft": True, "prerelease": False,
        })
        target_releases.append(existing)
        has_asset = False

    if not has_asset:
        url = f"{UPLOADS}/repos/{TARGET}/releases/{existing['id']}/assets?name={quote(source_asset['name'])}"
        uploaded = json.loads(request(url, method="POST", payload=path.read_bytes(), token=token,
                                      content_type="application/vnd.android.package-archive"))
        if uploaded.get("digest") not in (None, source_asset["digest"]):
            raise RuntimeError("Uploaded APK digest differs from the source")

    for attempt in range(5):
        current = api(f"/repos/{TARGET}/releases/{existing['id']}", token=token)
        try:
            confirm_asset(current, source_asset)
            break
        except RuntimeError:
            if attempt == 4:
                raise
            time.sleep(2)
    api(f"/repos/{TARGET}/releases/{existing['id']}", method="PATCH", token=token,
        data={"draft": False, "body": body})
    print(f"Published verified module release: {tag}")


def main():
    if os.environ.get("GITHUB_REPOSITORY") != SOURCE:
        raise RuntimeError("This workflow must run in the official AnsIt repository")
    source_token = os.environ.get("GITHUB_TOKEN")
    verify_only = os.environ.get("VERIFY_ONLY", "").lower() == "true"
    target_token = os.environ.get("XPOSED_SYNC_TOKEN")
    if not verify_only and not target_token:
        raise RuntimeError("Configure the XPOSED_SYNC_TOKEN Actions secret before syncing releases")
    source_releases = [release for release in all_releases(SOURCE, source_token)
                       if not release["draft"] and not release["prerelease"]
                       and release.get("published_at") and release["published_at"] >= FIRST_RELEASE]
    source_releases.sort(key=lambda release: release["published_at"])
    target_releases = all_releases(TARGET, source_token if verify_only else target_token)
    with tempfile.TemporaryDirectory(prefix="ansit-release-") as directory:
        for source in source_releases:
            if verify_only:
                asset = apk_asset(source)
                path = Path(directory) / asset["name"]
                download_apk(asset, path)
                code, version = inspect_apk(path)
                existing = find_mirrored(source["id"], target_releases)
                if existing:
                    if existing["tag_name"] != f"{code}-{version}":
                        raise RuntimeError("Published module release has an unexpected tag")
                    confirm_asset(existing, asset)
                    print(f"Verified existing module release: {existing['tag_name']}")
                else:
                    print(f"Would sync verified module release: {code}-{version}")
            else:
                sync_one(source, target_releases, target_token, Path(directory))
    if verify_only:
        print("Read-only verification complete")
        return
    print("Release sync complete")


if __name__ == "__main__":
    main()
