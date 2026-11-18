#!/usr/bin/env bash
# Vendor the Java sources of material-color-utilities (Apache-2.0) into yili-design under
# the com.yiligram.design.mcu package. Usage: tools/vendor/update_mcu.sh [git-ref]
set -euo pipefail
REF="${1:-main}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEST="$ROOT/yili-design/src/main/java/com/yiligram/design/mcu"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git clone -q --depth 1 --branch "$REF" https://github.com/material-foundation/material-color-utilities.git "$TMP/mcu"
COMMIT="$(git -C "$TMP/mcu" rev-parse HEAD)"
rm -rf "$DEST"
mkdir -p "$DEST"
cp -r "$TMP/mcu/java/"* "$DEST/"
cp "$TMP/mcu/LICENSE" "$DEST/LICENSE"

PKGS="blend contrast dislike dynamiccolor hct palettes quantize scheme score temperature utils"
ALT="$(echo $PKGS | tr ' ' '|')"
find "$DEST" -name '*.java' -print0 | xargs -0 sed -i -E \
  -e "s/^package ($ALT)\b/package com.yiligram.design.mcu.\1/" \
  -e "s/^import (static )?($ALT)\./import \1com.yiligram.design.mcu.\2./"

cat > "$DEST/README.md" <<MD
Vendored from https://github.com/material-foundation/material-color-utilities (java/),
commit $COMMIT, Apache License 2.0 (see LICENSE). Packages were moved under
com.yiligram.design.mcu by tools/vendor/update_mcu.sh; the code is otherwise unchanged.
MD
echo "material-color-utilities $COMMIT vendored into $DEST"
