"""Reflection helpers and Java method hooks (exteraGram-compatible ``hook_utils``)."""
from typing import Any, Dict, List

from java import jclass

_Class = jclass("java.lang.Class")
_handles: Dict[str, List[Any]] = {}


def find_class(class_name: str):
    """Java Class by name from the app's class loader, or None."""
    loader = jclass("org.telegram.messenger.ApplicationLoader").applicationContext.getClassLoader()
    try:
        return _Class.forName(class_name, False, loader)
    except Exception:
        return None


def _find_field(cls, name):
    while cls is not None:
        try:
            f = cls.getDeclaredField(name)
            f.setAccessible(True)
            return f
        except Exception:
            cls = cls.getSuperclass()
    return None


def get_private_field(obj, field_name: str):
    f = _find_field(obj.getClass(), field_name)
    return None if f is None else f.get(obj)


def set_private_field(obj, field_name: str, new_value) -> bool:
    f = _find_field(obj.getClass(), field_name)
    if f is None:
        return False
    f.set(obj, new_value)
    return True


def get_static_private_field(clazz, field_name: str):
    f = _find_field(clazz, field_name)
    return None if f is None else f.get(None)


def set_static_private_field(clazz, field_name: str, new_value) -> bool:
    f = _find_field(clazz, field_name)
    if f is None:
        return False
    f.set(None, new_value)
    return True


# ---------------------------------------------------------------- method hooks
# Native ART hooking (LSPlant) is a later stage of the YiliGram plugin engine. Until it lands,
# hook_method() logs a warning and returns None, which plugins already treat as "hook failed".

def _hook(plugin, method, handler, priority):
    import log_utils
    log_utils.Logger("plugin." + getattr(plugin, "id", "?")).warning(
        "Java method hooks are not supported by this YiliGram build yet: %s", method)
    return None


def _unhook(handle):
    if handle is not None and hasattr(handle, "unhook"):
        handle.unhook()


def _unhook_plugin(plugin_id: str):
    for h in _handles.pop(plugin_id, []):
        _unhook(h)
