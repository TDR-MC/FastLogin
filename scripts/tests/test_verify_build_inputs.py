#!/usr/bin/env python3
#
# SPDX-License-Identifier: MIT
#
# The MIT License (MIT)
#
# Copyright (c) 2015-2024 games647 and contributors
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

"""Regression coverage for resolving distinct locked versions without Maven mediation.

Resolver regression additions Copyright (c) 2026 TDR-MC contributors.
"""

import importlib.util
import json
from collections import Counter
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("verify_build_inputs", ROOT / "scripts/verify-build-inputs.py")
RESOLVER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RESOLVER)


class ResolveLockedTests(unittest.TestCase):
    def test_real_lock_requests_every_version_once_in_unique_coordinate_models(self):
        lock = json.loads((ROOT / "docs/BUILD_INPUTS.json").read_text())
        requested = []
        models = []

        def strict_maven(command, *, check):
            self.assertFalse(check)
            self.assertIn("org.apache.maven.plugins:maven-dependency-plugin:3.7.0:resolve", command)
            self.assertIn("-DexcludeTransitive=true", command)
            self.assertIn("--strict-checksums", command)
            self.assertIn("--no-snapshot-updates", command)
            self.assertIn("-Dmaven.repo.local=/fixture/repository", command)
            model = ElementTree.parse(command[command.index("-f") + 1]).getroot()
            self.assertEqual(
                [(repo.findtext("id"), repo.findtext("url")) for repo in model.findall("repositories/repository")],
                [(repo["id"], repo["url"]) for repo in lock["repositories"]],
            )
            dependencies = model.findall("dependencies/dependency")
            coordinates = [(dep.findtext("groupId"), dep.findtext("artifactId")) for dep in dependencies]
            # Maven either rejects a duplicate model or mediates away a version.
            if len(coordinates) != len(set(coordinates)):
                return SimpleNamespace(returncode=1)
            models.append(coordinates)
            for dep in dependencies:
                self.assertEqual(dep.findtext("exclusions/exclusion/groupId"), "*")
                self.assertEqual(dep.findtext("exclusions/exclusion/artifactId"), "*")
                requested.append((dep.findtext("groupId") + ":" + dep.findtext("artifactId"), dep.findtext("version")))
            return SimpleNamespace(returncode=0)

        with patch.object(RESOLVER.subprocess, "run", side_effect=strict_maven):
            self.assertEqual(RESOLVER.resolve_locked(lock, Path("/fixture/repository"), "mvn"), 0)
        self.assertEqual(Counter(requested), Counter((a["coordinate"], a["version"]) for a in lock["maven"]))
        self.assertGreater(len(models), 1)  # The real lock includes distinct versions of one coordinate.

    def test_middle_batch_failure_stops_without_resolving_later_versions(self):
        lock = {
            "repositories": [],
            "maven": [{"coordinate": "fixture:library", "version": str(v)} for v in (1, 2, 3)],
        }
        attempted = []

        def failing_maven(command, *, check):
            model = ElementTree.parse(command[command.index("-f") + 1]).getroot()
            versions = [dep.findtext("version") for dep in model.findall("dependencies/dependency")]
            self.assertEqual(len(versions), 1)
            attempted.extend(versions)
            return SimpleNamespace(returncode=17 if versions == ["2"] else 0)

        with patch.object(RESOLVER.subprocess, "run", side_effect=failing_maven):
            self.assertEqual(RESOLVER.resolve_locked(lock, Path("/fixture/repository"), "mvn"), 17)
        self.assertEqual(attempted, ["1", "2"])


if __name__ == "__main__":
    unittest.main()
