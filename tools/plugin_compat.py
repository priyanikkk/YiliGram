#!/usr/bin/env python3
"""Static compatibility report for exteraGram plugins against YiliGram's plugin SDK.

Reads plugins and the SDK with ``ast`` only — no plugin code is executed.

    python3 tools/plugin_compat.py path/to/plugins/*.plugin

Per plugin: metadata validity, app/SDK version requirements, SDK modules and names it imports
that YiliGram does not provide, BasePlugin methods it calls that do not exist, and Telegram
classes it names (for hooks and reflection) that do not exist in this source tree.
"""
import ast
import os
import re
import sys
from typing import Dict, Set

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SDK = os.path.join(ROOT, "TMessagesProj_App", "src", "main", "python")
JAVA_ROOTS = [os.path.join(ROOT, "TMessagesProj", "src", "main", "java"),
              os.path.join(ROOT, "TMessagesProj_App", "src", "main", "java")]
CLASS_NAME = re.compile(r"^org\.telegram\.[a-z0-9_.]*[A-Z][A-Za-z0-9_$]*$")
sys.path.insert(0, SDK)

SDK_MODULES = {"base_plugin", "client_utils", "android_utils", "hook_utils", "file_utils", "log_utils",
               "ui", "ui.settings", "ui.alert", "ui.bulletin", "extera_utils", "extera_utils.text_formatting",
               "extera_utils.classes", "markdown_utils", "intents", "elyx", "yili_ui"}
HOOK_CALLS = {"hook_method", "hook_all_methods", "hook_all_constructors"}


def sdk_exports() -> Dict[str, Set[str]]:
    """Top-level names (and BasePlugin attributes) defined by YiliGram's SDK modules."""
    exports: Dict[str, Set[str]] = {}
    for dirpath, _, files in os.walk(SDK):
        for f in files:
            if not f.endswith(".py"):
                continue
            rel = os.path.relpath(os.path.join(dirpath, f), SDK)[:-3].replace(os.sep, ".")
            mod = rel[:-9] if rel.endswith(".__init__") else rel
            tree = ast.parse(open(os.path.join(dirpath, f), encoding="utf-8").read())
            names = set()
            for node in tree.body:
                if isinstance(node, (ast.FunctionDef, ast.ClassDef, ast.AsyncFunctionDef)):
                    names.add(node.name)
                    if node.name == "BasePlugin":
                        exports["BasePlugin"] = {n.name for n in node.body if isinstance(n, ast.FunctionDef)}
                elif isinstance(node, ast.Assign):
                    names |= {t.id for t in node.targets if isinstance(t, ast.Name)}
                elif isinstance(node, (ast.Import, ast.ImportFrom)):
                    names |= {(a.asname or a.name).split(".")[0] for a in node.names}
            exports[mod] = names
    return exports


def class_exists(name: str) -> bool:
    """True if the (possibly nested, ``Outer$Inner``) class is declared in the Java sources."""
    outer, _, inner = name.partition("$")
    if outer.rsplit(".", 1)[-1] in ("R", "BuildConfig"):
        return True  # generated at build time
    rel = outer.replace(".", os.sep) + ".java"
    for root in JAVA_ROOTS:
        path = os.path.join(root, rel)
        if os.path.isfile(path):
            if not inner:
                return True
            src = open(path, encoding="utf-8", errors="replace").read()
            return all(re.search(r"\b(class|interface|enum|record)\s+" + re.escape(part) + r"\b", src)
                       for part in inner.split("$") if not part.isdigit())
    # Top-level classes in another file of the same package are not found this way; treat
    # unknown files as missing only when the package directory exists.
    pkg = os.path.dirname(rel)
    return not any(os.path.isdir(os.path.join(root, pkg)) for root in JAVA_ROOTS)


def analyze(path: str, exports: Dict[str, Set[str]]) -> Dict:
    import yili_engine
    src = open(path, encoding="utf-8", errors="replace").read()
    report = {"file": os.path.basename(path), "missing_modules": set(), "missing_names": set(),
              "missing_methods": set(), "java_hooks": False, "missing_classes": set(), "notes": []}
    try:
        tree = ast.parse(src)
    except SyntaxError as e:
        report["notes"].append(f"syntax error: {e}")
        return report
    meta = yili_engine.parse_metadata(src)
    report["id"] = meta.get("id")
    report["meta_error"] = yili_engine.validate_metadata(meta)
    report["compat"] = None
    if not yili_engine.check_version(meta.get("app_version"), yili_engine.COMPAT_APP_VERSION):
        report["compat"] = f"app {meta.get('app_version')}"
    elif not yili_engine.check_version(meta.get("sdk_version"), yili_engine.SDK_VERSION):
        report["compat"] = f"sdk {meta.get('sdk_version')}"
    report["requirements"] = meta.get("requirements")
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom) and node.module:
            mod = node.module
            if mod.split(".")[0] in {m.split(".")[0] for m in SDK_MODULES}:
                if mod not in exports:
                    report["missing_modules"].add(mod)
                    continue
                for a in node.names:
                    if a.name != "*" and a.name not in exports[mod] and f"{mod}.{a.name}" not in exports:
                        report["missing_names"].add(f"{mod}.{a.name}")
        elif isinstance(node, ast.Import):
            for a in node.names:
                if a.name in SDK_MODULES and a.name not in exports:
                    report["missing_modules"].add(a.name)
        elif isinstance(node, ast.Constant) and isinstance(node.value, str) and CLASS_NAME.match(node.value):
            if not class_exists(node.value):
                report["missing_classes"].add(node.value)
        elif isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute):
            if isinstance(node.func.value, ast.Name) and node.func.value.id == "self":
                if node.func.attr in HOOK_CALLS:
                    report["java_hooks"] = True
    # self.<method>() calls that neither the plugin class nor BasePlugin define
    for cls in [n for n in tree.body if isinstance(n, ast.ClassDef)]:
        if not any(isinstance(b, ast.Name) and b.id == "BasePlugin" for b in cls.bases):
            continue
        own = {n.name for n in ast.walk(cls) if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef))}
        attrs = {t.attr for n in ast.walk(cls) if isinstance(n, ast.Assign)
                 for t in n.targets if isinstance(t, ast.Attribute)}
        for n in ast.walk(cls):
            if isinstance(n, ast.Call) and isinstance(n.func, ast.Attribute) \
                    and isinstance(n.func.value, ast.Name) and n.func.value.id == "self":
                name = n.func.attr
                if name not in own and name not in attrs and name not in exports.get("BasePlugin", set()) \
                        and not name.startswith("_"):
                    report["missing_methods"].add(name)
    shimmed = ("com.exteragram.messenger.plugins", "com.exteragram.messenger.plugins.ui")
    other = sorted({m for m in re.findall(r"com\.exteragram\.[A-Za-z0-9_.]+", src)
                    if not any(m == s or m.startswith(s + ".") and m.count(".") == s.count(".") + 1 for s in shimmed)})
    if other:
        report["notes"].append("uses exteraGram internals: " + ", ".join(other[:4]) + (" …" if len(other) > 4 else ""))
    return report


def main():
    exports = sdk_exports()
    ok = 0
    for path in sys.argv[1:]:
        r = analyze(path, exports)
        problems = []
        if r.get("meta_error"):
            problems.append("metadata: " + r["meta_error"])
        if r.get("compat"):
            problems.append("needs " + r["compat"])
        if r["missing_modules"]:
            problems.append("modules: " + ", ".join(sorted(r["missing_modules"])))
        if r["missing_names"]:
            problems.append("names: " + ", ".join(sorted(r["missing_names"])))
        if r["missing_methods"]:
            problems.append("BasePlugin methods: " + ", ".join(sorted(r["missing_methods"])))
        if r["missing_classes"]:
            problems.append("Telegram classes not in YiliGram: " + ", ".join(sorted(r["missing_classes"])))
        problems += r["notes"]
        status = "OK " if not problems else "GAP"
        if r["java_hooks"]:
            problems_note = "uses Java method hooks"
        else:
            problems_note = None
        ok += not problems
        print(f"[{status}] {r.get('id') or r['file']}" + (f"  ({problems_note})" if problems_note else ""))
        for p in problems:
            print(f"       - {p}")
        if r.get("requirements"):
            print(f"       requirements: {', '.join(r['requirements'])}")
    print(f"\n{ok}/{len(sys.argv) - 1} fully compatible")


if __name__ == "__main__":
    main()
