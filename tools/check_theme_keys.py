#!/usr/bin/env python3
"""Fail if com.yiligram.bridge.RoleOverrides names a Theme key that upstream no longer has.

Run after merging a new Telegram release: python3 tools/check_theme_keys.py
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
theme = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java").read_text(encoding="utf-8")
keys = set(re.findall(r"public static final int key_([A-Za-z0-9_]+)", theme))
overrides = (ROOT / "TMessagesProj/src/main/java/com/yiligram/bridge/RoleOverrides.java").read_text(encoding="utf-8")
code = re.sub(r"//[^\n]*", "", overrides)  # ignore quoted words in comments
used = set(re.findall(r'"([A-Za-z][A-Za-z0-9_]*)"', code))
missing = sorted(used - keys)
if missing:
    print("RoleOverrides references unknown theme keys:", ", ".join(missing))
    sys.exit(1)
print(f"OK: {len(used)} overridden keys, all present among {len(keys)} theme keys")
