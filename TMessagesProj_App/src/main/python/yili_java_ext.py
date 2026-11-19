"""exteraGram's Chaquopy-fork conveniences on stock Chaquopy, for Java *objects*.

exteraGram ships a modified Chaquopy; plugins written for it may rely on:

* JavaBean access by default: ``obj.title`` reads ``getTitle()`` / ``isTitle()`` and
  ``obj.title = x`` calls ``setTitle(x)`` when there is no public field of that name
  (``JGS`` / ``JNGS`` switch it on/off per object);
* ``obj.JA`` / ``obj.JNA``: access non-public declared fields and methods by name;
* chain modifiers ``obj.JIR.a().b()`` (keep returning ``obj``), ``JNIR`` (stop),
  ``obj.JS.x.y()`` (missing members and None become ``JNone``), ``JNS`` (stop);
* ``from java import JNone``.

This module installs them by giving the Python proxy of ``java.lang.Object`` (the root of every
Chaquopy object proxy) a ``__getattr__`` / ``__setattr__``. Normal Chaquopy lookups run first,
so existing code is unaffected; only names Chaquopy does not know reach this code.
Not available: the class-level (static) variants, e.g. ``SomeClass.JA`` — Chaquopy's class
metaclass is a compiled type.
"""
from typing import Any

from java import jclass

_MODIFIERS = {
    "JA": ("access_all", True), "JAccessAll": ("access_all", True),
    "JNA": ("access_all", False), "JNotAccessAll": ("access_all", False),
    "JGS": ("bean", True), "JUseGetterAndSetter": ("bean", True),
    "JNGS": ("bean", False), "JNotUseGetterAndSetter": ("bean", False),
}
_CHAIN = {
    "JIR": ("ignore", True), "JIgnoreResult": ("ignore", True),
    "JNIR": ("ignore", False), "JNotIgnoreResult": ("ignore", False),
    "JS": ("safe", True), "JSafe": ("safe", True),
    "JNS": ("safe", False), "JNotSafe": ("safe", False),
}
_FLAGS = "_yili_flags"
_installed = False


class _JNoneType:
    """Falsy sentinel of safe chains: swallows attribute access and calls."""
    _instance = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def __bool__(self):
        return False

    def __getattr__(self, name):
        if name.startswith("__"):
            raise AttributeError(name)
        return self

    def __call__(self, *args, **kwargs):
        return self

    def __repr__(self):
        return "JNone"


JNone = _JNoneType()


def _flags(obj) -> dict:
    d = obj.__dict__.get(_FLAGS)
    if d is None:
        d = {"access_all": False, "bean": True}
        obj.__dict__[_FLAGS] = d
    return d


def _cap(name: str) -> str:
    return name[:1].upper() + name[1:]


def _is_field_like(name: str) -> bool:
    return bool(name) and name[0].islower() and not any(
        name.startswith(p) and len(name) > len(p) and name[len(p)].isupper() for p in ("get", "set", "is"))


def _declared_field(obj, name):
    c = obj.getClass()
    while c is not None:
        try:
            f = c.getDeclaredField(name)
            f.setAccessible(True)
            return f
        except Exception:
            c = c.getSuperclass()
    return None


def _declared_methods(obj, name):
    out = []
    c = obj.getClass()
    while c is not None:
        for m in c.getDeclaredMethods():
            if m.getName() == name:
                m.setAccessible(True)
                out.append(m)
        c = c.getSuperclass()
    return out


def _reflective_call(obj, methods):
    Coerce = jclass("com.yiligram.hook.Coerce")

    def call(*args):
        last = None
        for m in methods:
            if len(m.getParameterTypes()) != len(args):
                continue
            try:
                return m.invoke(obj, Coerce.args(m.getParameterTypes(), list(args)))
            except Exception as e:  # wrong overload: try the next one
                last = e
        raise last or TypeError(f"no overload of {methods[0].getName()} takes {len(args)} arguments")
    return call


def _getattr(self, name):
    if name.startswith("_"):
        raise AttributeError(name)
    mod = _MODIFIERS.get(name)
    if mod is not None:
        _flags(self)[mod[0]] = mod[1]
        return self
    chain = _CHAIN.get(name)
    if chain is not None:
        return _Chain(self, **{chain[0]: chain[1]})
    flags = _flags(self)
    if flags["bean"] and _is_field_like(name):
        cls = type(self)
        for prefix in ("get", "is"):
            accessor = prefix + _cap(name)
            if getattr(cls, accessor, None) is not None:
                return getattr(self, accessor)()
    if flags["access_all"]:
        f = _declared_field(self, name)
        if f is not None:
            return f.get(self)
        methods = _declared_methods(self, name)
        if methods:
            return _reflective_call(self, methods)
    raise AttributeError(f"'{type(self).__name__}' object has no attribute '{name}'")


def _make_setattr(original):
    def _setattr(self, name, value):
        if name.startswith("_") or getattr(type(self), name, None) is not None:
            return original(self, name, value)
        flags = self.__dict__.get(_FLAGS) or {"access_all": False, "bean": True}
        if flags["bean"] and _is_field_like(name):
            setter = getattr(type(self), "set" + _cap(name), None)
            if setter is not None:
                getattr(self, "set" + _cap(name))(value)
                return None
        if flags["access_all"]:
            f = _declared_field(self, name)
            if f is not None:
                f.set(self, jclass("com.yiligram.hook.Coerce").value(f.getType(), value))
                return None
        return original(self, name, value)
    return _setattr


class _Chain:
    """One modifier chain (JIR / JS): wraps a Java object until the chain ends."""

    def __init__(self, target, ignore=False, safe=False):
        object.__setattr__(self, "_target", target)
        object.__setattr__(self, "_ignore", ignore)
        object.__setattr__(self, "_safe", safe)

    def _wrap(self, value):
        if value is None:
            return JNone if self._safe else None
        if self._safe and _is_java(value):
            return _Chain(value, False, True)
        return value

    def __getattr__(self, name):
        chain = _CHAIN.get(name)
        if chain is not None:
            opts = {"ignore": self._ignore, "safe": self._safe, chain[0]: chain[1]}
            return _Chain(self._target, **opts)
        try:
            value = getattr(self._target, name)
        except AttributeError:
            if self._safe:
                return JNone
            raise
        if callable(value) and not _is_java(value):
            def call(*args, **kwargs):
                try:
                    result = value(*args, **kwargs)
                except AttributeError:
                    if self._safe:
                        return JNone
                    raise
                if self._ignore:
                    return self
                return self._wrap(result)
            return call
        return self._wrap(value)

    def __setattr__(self, name, value):
        setattr(self._target, name, value)

    def __bool__(self):
        return bool(self._target)

    def __repr__(self):
        return f"<chain {self._target!r}>"


def _is_java(value) -> bool:
    try:
        return isinstance(value, jclass("java.lang.Object"))
    except Exception:
        return False


def install():
    """Patches Chaquopy's java.lang.Object proxy once; adds java.JNone."""
    global _installed
    if _installed:
        return
    import java
    java.JNone = JNone
    Object = jclass("java.lang.Object")
    original_setattr = Object.__setattr__
    Object.__getattr__ = _getattr
    Object.__setattr__ = _make_setattr(original_setattr)
    _installed = True


def unwrap(value: Any) -> Any:
    """The Java object behind a chain wrapper (for passing into Java APIs)."""
    return value._target if isinstance(value, _Chain) else value
