"""Runtime installer for plugins' ``__requirements__`` (pure-Python wheels from PyPI).

Chaquopy's pip only runs at build time, so YiliGram resolves and installs requirements itself:
* only ``*-none-any.whl`` wheels are accepted (no C extensions, no sdists);
* every wheel's sha256 is checked against PyPI's digest;
* dependencies (``Requires-Dist``) are installed recursively, honoring environment markers;
* all plugins share one ``libs`` directory; each distribution remembers which plugins need it,
  and is removed when the last of them is deleted.

Errors use the messages from the exteraGram docs: "No pure-Python wheel found",
"Dependency conflict", "Package not found".
"""
import hashlib
import io
import json
import os
import re
import shutil
import sys
import zipfile
from typing import Dict, List, Optional, Set

PYPI = "https://pypi.org/pypi/{name}/json"
TIMEOUT = 20

_libs: Optional[str] = None


class PipError(Exception):
    pass


def init(libs_dir: str):
    global _libs
    _libs = libs_dir
    os.makedirs(_libs, exist_ok=True)
    if _libs not in sys.path:
        sys.path.insert(0, _libs)


def _owners_path() -> str:
    return os.path.join(_libs, ".yili_owners.json")


def _load_owners() -> Dict[str, List[str]]:
    try:
        with open(_owners_path(), encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def _save_owners(owners: Dict[str, List[str]]):
    with open(_owners_path(), "w", encoding="utf-8") as f:
        json.dump(owners, f)


def _canonical(name: str) -> str:
    return re.sub(r"[-_.]+", "-", name).lower()


def installed_version(name: str) -> Optional[str]:
    """Version of a distribution available to plugins (shared libs or bundled with the app)."""
    from importlib import metadata
    try:
        return metadata.version(name)
    except metadata.PackageNotFoundError:
        return None


def _environment() -> Dict[str, str]:
    from packaging.markers import default_environment
    env = default_environment()
    env["extra"] = ""
    return env


def _pick_wheel(name: str, specifier) -> Dict:
    import requests
    from packaging.version import InvalidVersion, Version
    r = requests.get(PYPI.format(name=name), timeout=TIMEOUT)
    if r.status_code == 404:
        raise PipError(f"Package not found: {name}")
    r.raise_for_status()
    releases = r.json().get("releases", {})
    candidates = []
    for ver, files in releases.items():
        try:
            v = Version(ver)
        except InvalidVersion:
            continue
        if v.is_prerelease and not specifier.prereleases:
            continue
        if not specifier.contains(v, prereleases=True):
            continue
        for f in files:
            fn = f.get("filename", "")
            if fn.endswith("-none-any.whl") and re.search(r"-py3[^-]*-none-any\.whl$|-py2\.py3-none-any\.whl$", fn) \
                    and not f.get("yanked"):
                candidates.append((v, f))
    if not candidates:
        raise PipError(f"No pure-Python wheel found for {name}{specifier}")
    candidates.sort(key=lambda t: t[0])
    return candidates[-1][1]


def _install_wheel(info: Dict) -> List[str]:
    """Downloads, verifies and extracts a wheel. Returns its Requires-Dist lines."""
    import requests
    data = requests.get(info["url"], timeout=TIMEOUT).content
    expected = info.get("digests", {}).get("sha256")
    if expected and hashlib.sha256(data).hexdigest() != expected:
        raise PipError(f"Checksum mismatch for {info['filename']}")
    requires = []
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for member in z.namelist():
            target = os.path.normpath(os.path.join(_libs, member))
            if not target.startswith(os.path.normpath(_libs) + os.sep):
                raise PipError(f"Unsafe path in wheel: {member}")
        z.extractall(_libs)
        meta = [n for n in z.namelist() if n.endswith(".dist-info/METADATA")]
        if meta:
            for line in z.read(meta[0]).decode("utf-8", "replace").splitlines():
                if line.startswith("Requires-Dist:"):
                    requires.append(line.split(":", 1)[1].strip())
    return requires


def install(requirements: List[str], plugin_id: str, logger=None) -> None:
    """Makes ``requirements`` importable for ``plugin_id``. Raises PipError on failure."""
    from importlib import invalidate_caches
    from packaging.requirements import Requirement
    if _libs is None:
        raise PipError("pip is not initialized")
    owners = _load_owners()
    env = _environment()
    queue = [Requirement(r) for r in requirements]
    seen: Set[str] = set()
    while queue:
        req = queue.pop(0)
        if req.marker is not None and not req.marker.evaluate(env):
            continue
        name = _canonical(req.name)
        if name in seen:
            continue
        seen.add(name)
        have = installed_version(req.name)
        if have is not None:
            if req.specifier.contains(have, prereleases=True):
                owners.setdefault(name, [])
                if plugin_id not in owners[name]:
                    owners[name].append(plugin_id)
                continue
            if name not in owners:
                # Bundled with the app: cannot be replaced.
                raise PipError(f"Dependency conflict: {req} (bundled {have})")
            other = [o for o in owners[name] if o != plugin_id]
            if other:
                raise PipError(f"Dependency conflict: {req} (installed {have} for {', '.join(other)})")
        if logger:
            logger.info("pip: installing %s", req)
        wheel = _pick_wheel(req.name, req.specifier)
        for dep in _install_wheel(wheel):
            queue.append(Requirement(dep))
        owners.setdefault(name, [])
        if plugin_id not in owners[name]:
            owners[name].append(plugin_id)
        invalidate_caches()
    _save_owners(owners)


def release(plugin_id: str) -> List[str]:
    """Drops ``plugin_id`` from every distribution; deletes the ones nobody needs anymore."""
    owners = _load_owners()
    removed = []
    for name in list(owners):
        if plugin_id in owners[name]:
            owners[name].remove(plugin_id)
        if not owners[name]:
            _remove_distribution(name)
            del owners[name]
            removed.append(name)
    _save_owners(owners)
    return removed


def _remove_distribution(name: str):
    for entry in os.listdir(_libs):
        if not entry.endswith(".dist-info"):
            continue
        dist = re.sub(r"-[^-]+\.dist-info$", "", entry)
        if _canonical(dist) != name:
            continue
        record = os.path.join(_libs, entry, "RECORD")
        top_dirs = set()
        try:
            with open(record, encoding="utf-8") as f:
                for line in f:
                    path = line.split(",", 1)[0]
                    full = os.path.normpath(os.path.join(_libs, path))
                    if full.startswith(os.path.normpath(_libs) + os.sep) and os.path.isfile(full):
                        os.remove(full)
                    first = path.split("/", 1)[0]
                    if "/" in path and not first.endswith(".dist-info"):
                        top_dirs.add(first)
        except FileNotFoundError:
            pass
        # Bytecode caches are not listed in RECORD.
        for top in top_dirs:
            for root, dirs, _ in os.walk(os.path.join(_libs, top)):
                for d in dirs:
                    if d == "__pycache__":
                        shutil.rmtree(os.path.join(root, d), ignore_errors=True)
        shutil.rmtree(os.path.join(_libs, entry), ignore_errors=True)
    # Drop now-empty package directories.
    for root, dirs, files in os.walk(_libs, topdown=False):
        if root != _libs and not os.listdir(root):
            os.rmdir(root)
