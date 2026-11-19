"""Plugin repositories: Git repositories with an ``extera-plugins.json`` index (exteraGram format).

A repository URL is either a GitHub URL (``https://github.com/{owner}/{repo}``, optionally
``/tree/{ref}[/{dir}]``, read from raw.githubusercontent.com) or the direct https URL of an
index file on any host. The index (UTF-8 JSON, at most 1 MiB, ``schema`` 1) lists one entry per
plugin; files are downloaded relative to the index URL and must match the entry's ``size`` and
``sha256``, then the plugin's own ``__id__`` and ``__version__`` must match the entry.

Indexes are refreshed at most every 10 minutes with conditional requests (ETag /
Last-Modified). Telegram-channel repositories are not supported yet.
"""
import hashlib
import json
import os
import re
import tempfile
import threading
import time
from typing import Any, Dict, List, Optional, Tuple
from urllib.parse import quote, unquote, urljoin, urlsplit

import log_utils

INDEX_NAME = "extera-plugins.json"
SCHEMA = 1
MAX_INDEX = 1 << 20
MAX_PLUGIN = 2 << 20
REFRESH_SECONDS = 600
TIMEOUT = 20

_logger = log_utils.Logger("yili.repos")
_lock = threading.RLock()
_path: Optional[str] = None
_repos: List[Dict[str, Any]] = []


class RepoError(Exception):
    pass


def init(plugins_dir: str):
    global _path, _repos
    _path = os.path.join(plugins_dir, ".repos.json")
    try:
        with open(_path, encoding="utf-8") as f:
            _repos = json.load(f).get("repos", [])
    except Exception:
        _repos = []


def _save():
    if _path is None:
        return
    tmp = _path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump({"repos": _repos}, f)
    os.replace(tmp, _path)


# ---------------------------------------------------------------- URLs and versions

def index_url(repo_url: str) -> str:
    """The raw URL of a repository's index file."""
    url = repo_url.strip()
    parts = urlsplit(url)
    if parts.scheme != "https":
        raise RepoError("Only https repository URLs are supported")
    if parts.netloc.lower() in ("github.com", "www.github.com"):
        segs = [s for s in parts.path.split("/") if s]
        if len(segs) < 2:
            raise RepoError("GitHub URL must be https://github.com/{owner}/{repo}")
        owner, repo = segs[0], segs[1]
        if repo.endswith(".git"):
            repo = repo[:-4]
        ref, directory = "HEAD", ""
        if len(segs) > 2:
            if segs[2] != "tree" or len(segs) < 4:
                raise RepoError("Use https://github.com/{owner}/{repo}/tree/{ref}[/{dir}]")
            ref = segs[3]
            directory = "/".join(segs[4:])
        path = f"{owner}/{repo}/{ref}/" + (directory + "/" if directory else "") + INDEX_NAME
        return "https://raw.githubusercontent.com/" + path
    if not parts.path.endswith(".json"):
        raise RepoError(f"Give the direct https URL of {INDEX_NAME} for hosts other than GitHub")
    return url


def share_link(repo_url: str) -> str:
    return "https://t.me/exteraPluginRepo?git=" + quote(repo_url, safe="")


def parse_share_link(link: str) -> Optional[str]:
    """Repository URL from a t.me/exteraPluginRepo?git=… link, or None."""
    m = re.search(r"[?&]git=([^&]+)", link)
    return unquote(m.group(1)) if m and "exteraPluginRepo" in link else None


def version_key(version: Any) -> Tuple[int, ...]:
    """Leading numeric part, compared component by component (1.10.0 > 1.9.2; -beta ignored)."""
    m = re.match(r"\s*v?(\d+(?:\.\d+)*)", str(version or ""))
    return tuple(int(x) for x in m.group(1).split(".")) if m else ()


def is_newer(candidate: Any, installed: Any) -> bool:
    a, b = version_key(candidate), version_key(installed)
    n = max(len(a), len(b))
    return a + (0,) * (n - len(a)) > b + (0,) * (n - len(b))


# ---------------------------------------------------------------- index

def validate_index(data: Any) -> Dict[str, Any]:
    if not isinstance(data, dict):
        raise RepoError("Index is not a JSON object")
    schema = data.get("schema")
    if not isinstance(schema, int):
        raise RepoError("Index has no schema version")
    if schema > SCHEMA:
        raise RepoError("This repository needs a newer version of the app")
    if not isinstance(data.get("name"), str) or not data["name"].strip():
        raise RepoError("Index has no name")
    plugins = data.get("plugins")
    if not isinstance(plugins, list):
        raise RepoError("Index has no plugins list")
    seen = set()
    clean = []
    for p in plugins:
        if not isinstance(p, dict):
            continue
        pid = p.get("id")
        ok = (isinstance(pid, str) and isinstance(p.get("name"), str) and p.get("version") is not None
              and isinstance(p.get("file"), str) and isinstance(p.get("sha256"), str)
              and re.fullmatch(r"[0-9a-f]{64}", p["sha256"]) and isinstance(p.get("size"), int))
        if not ok or pid in seen:
            _logger.warning("skipping invalid index entry %r", pid)
            continue
        seen.add(pid)
        clean.append(p)
    data = dict(data)
    data["plugins"] = clean
    return data


def _http_get(url: str, headers: Optional[Dict[str, str]] = None, limit: int = MAX_INDEX):
    import requests
    r = requests.get(url, headers=headers or {}, timeout=TIMEOUT, stream=True)
    if r.status_code == 304:
        return r, None
    if r.status_code != 200:
        raise RepoError(f"HTTP {r.status_code} for {url}")
    body = b""
    for chunk in r.iter_content(65536):
        body += chunk
        if len(body) > limit:
            raise RepoError(f"{url} is larger than {limit // 1024} KiB")
    return r, body


def _find(repo_url: str) -> Optional[Dict[str, Any]]:
    for r in _repos:
        if r["url"] == repo_url:
            return r
    return None


def add_repo(repo_url: str) -> Dict[str, Any]:
    """Adds a repository after loading its index once. Returns the stored record."""
    shared = parse_share_link(repo_url)
    if shared:
        repo_url = shared
    url = index_url(repo_url)
    with _lock:
        if _find(repo_url):
            raise RepoError("Repository is already added")
    record = {"url": repo_url, "index_url": url, "etag": None, "last_modified": None,
              "fetched_at": 0, "index": None, "error": None}
    _refresh_one(record, force=True)
    if record["index"] is None:
        raise RepoError(record["error"] or "Cannot load the repository")
    with _lock:
        _repos.append(record)
        _save()
    return record


def remove_repo(repo_url: str):
    with _lock:
        _repos[:] = [r for r in _repos if r["url"] != repo_url]
        _save()


def _refresh_one(record: Dict[str, Any], force: bool):
    if not force and time.time() - record.get("fetched_at", 0) < REFRESH_SECONDS:
        return
    headers = {}
    if record.get("index") is not None:
        if record.get("etag"):
            headers["If-None-Match"] = record["etag"]
        if record.get("last_modified"):
            headers["If-Modified-Since"] = record["last_modified"]
    try:
        resp, body = _http_get(record["index_url"], headers)
        record["fetched_at"] = time.time()
        if body is not None:
            record["index"] = validate_index(json.loads(body.decode("utf-8")))
            record["etag"] = resp.headers.get("ETag")
            record["last_modified"] = resp.headers.get("Last-Modified")
        record["error"] = None
    except Exception as e:
        record["error"] = str(e) if isinstance(e, RepoError) else f"{type(e).__name__}: {e}"
        _logger.warning("repository %s: %s", record["url"], record["error"])


def refresh(force: bool = False):
    with _lock:
        records = list(_repos)
    for r in records:
        _refresh_one(r, force)
    with _lock:
        _save()


# ---------------------------------------------------------------- catalog and install

def catalog(installed: Dict[str, str]) -> List[Dict[str, Any]]:
    """Repositories with their plugins; ``installed`` maps plugin id → installed version."""
    out = []
    with _lock:
        for r in _repos:
            idx = r.get("index") or {}
            plugins = []
            for p in idx.get("plugins", []):
                have = installed.get(p["id"])
                plugins.append({
                    "id": p["id"], "name": p["name"], "version": str(p["version"]),
                    "description": p.get("description") or "", "author": p.get("author") or "",
                    "category": p.get("category"), "installed": have,
                    "update": have is not None and is_newer(p["version"], have),
                })
            out.append({"url": r["url"], "name": idx.get("name") or r["url"], "description": idx.get("description") or "",
                        "author": idx.get("author") or "", "homepage": idx.get("homepage"),
                        "error": r.get("error"), "plugins": plugins})
    return out


def download(repo_url: str, plugin_id: str) -> str:
    """Downloads and verifies a plugin file; returns a temporary path (caller deletes it)."""
    with _lock:
        record = _find(repo_url)
        if record is None or record.get("index") is None:
            raise RepoError("Unknown repository")
        entry = next((p for p in record["index"]["plugins"] if p["id"] == plugin_id), None)
        base = record["index_url"]
    if entry is None:
        raise RepoError(f"{plugin_id} is not in this repository")
    file_url = urljoin(base, entry["file"])
    if urlsplit(file_url).scheme != "https":
        raise RepoError("Plugin files must be served over https")
    if entry["size"] > MAX_PLUGIN:
        raise RepoError("Plugin file is too large")
    _, body = _http_get(file_url, limit=MAX_PLUGIN)
    if len(body) != entry["size"]:
        raise RepoError(f"Size mismatch: index says {entry['size']}, got {len(body)}")
    if hashlib.sha256(body).hexdigest() != entry["sha256"]:
        raise RepoError("SHA-256 mismatch: the file does not match the index")
    import yili_engine
    meta = yili_engine.parse_metadata(body.decode("utf-8", "replace"))
    if meta.get("id") != plugin_id:
        raise RepoError(f"File declares id {meta.get('id')!r}, index says {plugin_id!r}")
    if str(meta.get("version")) != str(entry["version"]):
        raise RepoError(f"File declares version {meta.get('version')!r}, index says {entry['version']!r}")
    fd, path = tempfile.mkstemp(suffix=".plugin")
    with os.fdopen(fd, "wb") as f:
        f.write(body)
    return path


def catalog_json(installed_json: str) -> str:
    return json.dumps(catalog(json.loads(installed_json)))
