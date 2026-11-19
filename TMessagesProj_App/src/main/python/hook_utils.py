"""Reflection helpers and Java method hooks (exteraGram-compatible ``hook_utils``)."""
from typing import Any, Dict, List

from java import jclass

_Class = jclass("java.lang.Class")
_handles: Dict[str, List[Any]] = {}


def find_class(class_name: str):
    """The Java class as a Chaquopy ``jclass`` (static fields and methods are attributes),
    or None if it does not exist. ``find_class(name).getClass()`` is the ``java.lang.Class``.
    Same contract as exteraGram's ``hook_utils.find_class``."""
    try:
        return jclass(class_name)
    except Exception:
        return None


def as_java_class(cls):
    """``java.lang.Class`` for a jclass proxy (``find_class`` result) or a Class itself."""
    if isinstance(cls, type):
        return cls.getClass()
    return cls


def _find_field(cls, name):
    cls = as_java_class(cls)
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
# Xposed-style hooks run on com.yiligram.hook.YiliHook (LSPlant). Each plugin handler is wrapped
# in a Java YiliHook.Callback proxy; its errors go through the engine like any other plugin hook,
# so a plugin whose hook keeps failing is disabled (and its hooks removed).

_Bridge = None


def _bridge_class():
    global _Bridge
    if _Bridge is None:
        from java import dynamic_proxy
        import yili_engine

        class Bridge(dynamic_proxy(jclass("com.yiligram.hook.YiliHook$Callback"))):
            def __init__(self, plugin_id, handler):
                super().__init__()
                self.plugin_id = plugin_id
                self.handler = handler

            def before(self, param):
                fn = getattr(self.handler, "before_hooked_method", None)
                if fn is not None:
                    yili_engine._call(self.plugin_id, fn, param)

            def after(self, param):
                fn = getattr(self.handler, "after_hooked_method", None)
                if fn is not None:
                    yili_engine._call(self.plugin_id, fn, param)

        _Bridge = Bridge
    return _Bridge


def unavailable_reason():
    """None when Java method hooks work on this device, otherwise why not."""
    try:
        hook = jclass("com.yiligram.hook.YiliHook")
        return None if hook.isAvailable() else (hook.getError() or "unknown error")
    except Exception as e:
        return f"{type(e).__name__}: {e}"


def _hook(plugin, method, handler, priority):
    import log_utils
    plugin_id = getattr(plugin, "id", "?")
    try:
        bridge = _bridge_class()(plugin_id, handler)
        handle = jclass("com.yiligram.hook.YiliHook").hook(method, bridge, int(priority))
    except Exception as e:
        log_utils.Logger("plugin." + plugin_id).error("cannot hook %s: %s", method, e)
        return None
    _handles.setdefault(plugin_id, []).append(handle)
    return handle


def _unhook(handle):
    if handle is None:
        return
    for handles in _handles.values():
        if handle in handles:
            handles.remove(handle)
            break
    if hasattr(handle, "unhook"):
        handle.unhook()


def deoptimize(method) -> bool:
    """Stops ART from inlining ``method`` so hooks on what it calls are not skipped."""
    try:
        return bool(jclass("com.yiligram.hook.YiliHook").deoptimize(method))
    except Exception:
        return False


def invoke_original(method, this_object, *args):
    """Calls the original implementation of a (possibly hooked) method."""
    return jclass("com.yiligram.hook.YiliHook").invokeOriginal(method, this_object, list(args))


def _unhook_plugin(plugin_id: str):
    for h in _handles.pop(plugin_id, []):
        _unhook(h)
