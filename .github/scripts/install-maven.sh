#!/usr/bin/env bash
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
# CI installer additions Copyright (c) 2026 TDR-MC contributors
# CI-owned ephemeral directory; do not replace a system Maven installation.
set -euo pipefail
runtime_root=$(mktemp -d "${RUNNER_TEMP:?}/tdr-maven-3.9.11.XXXXXXXX")
archive="$runtime_root/maven.tar.gz"
curl --fail --location --proto '=https' --tlsv1.2 --max-time 90 \
    --max-filesize 12582912 \
    --output "$archive" \
    https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.tar.gz
printf '%s  %s\n' \
    bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978 \
    "$archive" | sha512sum --check --status
tar --extract --gzip --no-same-owner --file "$archive" --directory "$runtime_root"
printf '%s\n' "$runtime_root/apache-maven-3.9.11/bin" >> "${GITHUB_PATH:?}"
