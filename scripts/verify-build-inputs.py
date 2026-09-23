#!/usr/bin/env python3
#
# SPDX-License-Identifier: MIT
#
# The MIT License (MIT)
#
# Copyright (c) 2026 TDR-MC contributors
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
"""Fail a FastLogin build when a locked Maven or vendored input has drifted."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "docs" / "BUILD_INPUTS.json"


def file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify(path: Path, expected: str, label: str, errors: list[str]) -> None:
    if not path.is_file():
        errors.append(f"{label}: missing {path}")
    elif file_hash(path) != expected:
        errors.append(f"{label}: SHA-256 mismatch for {path}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--maven-repo",
        type=Path,
        default=Path.home() / ".m2" / "repository",
        help="Maven local repository (default: ~/.m2/repository)",
    )
    args = parser.parse_args()
    lock = json.loads(LOCK.read_text(encoding="utf-8"))
    errors: list[str] = []
    count = 0

    for artifact in lock["maven"]:
        relative_dir = Path(artifact["repository_path"])
        if relative_dir.is_absolute() or ".." in relative_dir.parts:
            errors.append(f"invalid Maven path: {relative_dir}")
            continue
        artifact_dir = args.maven_repo / relative_dir
        versions = {artifact["version"], artifact["resolved_version"]}
        for version in versions:
            for extension in ("jar", "pom"):
                name = f'{artifact["artifact_id"]}-{version}.{extension}'
                verify(
                    artifact_dir / name,
                    artifact[f"{extension}_sha256"],
                    artifact["coordinate"],
                    errors,
                )
                count += 1

    for artifact in lock["vendored"]:
        relative_path = Path(artifact["path"])
        if relative_path.is_absolute() or ".." in relative_path.parts:
            errors.append(f"invalid vendored path: {relative_path}")
            continue
        verify(ROOT / relative_path, artifact["sha256"], str(relative_path), errors)
        count += 1

    for module in ("core", "bukkit", "bungee", "velocity"):
        actual = ROOT / module / "target" / "dependency-tree.txt"
        expected = ROOT / "docs" / "dependency-tree" / f"{module}.txt"
        if not actual.is_file():
            errors.append(f"{module}: generate dependency tree before verification")
        elif actual.read_bytes() != expected.read_bytes():
            errors.append(f"{module}: resolved dependency tree differs from lock")
        count += 1

    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        print(f"Build-input verification failed: {len(errors)} error(s)", file=sys.stderr)
        return 1
    print(f"Build-input verification passed: {count} files match SHA-256")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
