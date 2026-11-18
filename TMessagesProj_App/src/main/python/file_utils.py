"""File helpers for plugins (exteraGram-compatible ``file_utils``)."""
import os
from typing import List, Optional

from java import jclass

_App = jclass("org.telegram.messenger.ApplicationLoader")
_FileLoader = jclass("org.telegram.messenger.FileLoader")


def get_files_dir() -> str:
    return str(_App.getFilesDirFixed().getAbsolutePath())


def get_plugins_dir() -> str:
    return os.path.join(str(_App.applicationContext.getFilesDir().getAbsolutePath()), "plugins")


def get_cache_dir() -> str:
    return str(_App.applicationContext.getCacheDir().getAbsolutePath())


def _media_dir(kind) -> str:
    try:
        return str(_FileLoader.getDirectory(kind).getAbsolutePath())
    except Exception:
        return get_cache_dir()


def get_images_dir() -> str: return _media_dir(_FileLoader.MEDIA_DIR_IMAGE)
def get_videos_dir() -> str: return _media_dir(_FileLoader.MEDIA_DIR_VIDEO)
def get_audios_dir() -> str: return _media_dir(_FileLoader.MEDIA_DIR_AUDIO)
def get_documents_dir() -> str: return _media_dir(_FileLoader.MEDIA_DIR_DOCUMENT)


def ensure_dir_exists(path: str):
    os.makedirs(path, exist_ok=True)


def list_dir(path: str, recursive: bool = False, include_files: bool = True, include_dirs: bool = False,
             extensions: Optional[List[str]] = None) -> List[str]:
    out = []
    for root, dirs, files in os.walk(path):
        if include_dirs:
            out += [os.path.join(root, d) for d in dirs]
        if include_files:
            for f in files:
                if extensions is None or any(f.lower().endswith(e.lower()) for e in extensions):
                    out.append(os.path.join(root, f))
        if not recursive:
            break
    return out


def write_file(path: str, content: str):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def read_file(path: str) -> Optional[str]:
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except Exception:
        return None


def write_file_bytes(path: str, content: bytes):
    with open(path, "wb") as f:
        f.write(content)


def read_file_bytes(path: str) -> Optional[bytes]:
    try:
        with open(path, "rb") as f:
            return f.read()
    except Exception:
        return None


def delete_file(path: str) -> bool:
    try:
        os.remove(path)
        return True
    except Exception:
        return False
