"""Managed Java subclasses implemented in Python (exteraGram-compatible ``extera_utils.classes``).

``@java_subclass(JavaClass, *interfaces)`` generates a real Java class at runtime
(``com.yiligram.hook.proxy.ProxyBuilder``, DexMaker) whose overridden and new methods call into
the Python class. ``new_instance()`` returns the Python *peer*; ``peer.java`` is the Java object.

Supported: ``joverride`` / ``joverload``, ``jmethod`` (explicit or annotated signatures),
``jfield`` with ``jgetmethod`` / ``jsetmethod``, ``jconstructor``, ``jpreconstructor``,
``__init__`` / ``on_post_init`` / ``on_release``, ``super()`` calls to the Java parent,
``from_java``, ``jrelease``, ``custom_name``. Not yet: MVEL-backed methods
(``jMVELmethod`` / ``jMVELoverride``); ``jclassbuilder`` receives YiliGram's ``ProxyBuilder``
instead of a DexMaker instance.
"""
import inspect
import threading
from typing import Any, Dict, List, Optional

from java import dynamic_proxy, jarray, jclass

import log_utils

_logger = log_utils.Logger("yili.classes")

_ProxyBuilder = jclass("com.yiligram.hook.proxy.ProxyBuilder")
_ProxyRuntime = jclass("com.yiligram.hook.proxy.ProxyRuntime")
_ProxyHandler = jclass("com.yiligram.hook.proxy.ProxyHandler")
_Object = jclass("java.lang.Object")
_Class = jclass("java.lang.Class")
_CALL_SUPER = _ProxyRuntime.CALL_SUPER
_PEER_FIELD = "yili_peer"

_PRIMITIVES = {
    "int": "java.lang.Integer", "long": "java.lang.Long", "boolean": "java.lang.Boolean",
    "float": "java.lang.Float", "double": "java.lang.Double", "short": "java.lang.Short",
    "byte": "java.lang.Byte", "char": "java.lang.Character", "void": "java.lang.Void",
}

# Classes generated for each plugin, released when the plugin unloads.
_plugin_classes: Dict[Optional[str], List[Any]] = {}
# Peers by the id stored in the Java object's yili_peer field (0 = none). Chaquopy cannot store
# arbitrary Python objects in Java fields, so the Java side only keeps the id.
_peers: Dict[int, Any] = {}
_next_peer_id = iter(range(1, 1 << 31))
_peers_lock = threading.Lock()
_creating = threading.local()


# ---------------------------------------------------------------- type parsing

def _java_type(t):
    """java.lang.Class for "int", "java.lang.String", "Foo[]", a jclass, or a Class."""
    if isinstance(t, str):
        name = t.strip()
        if name.endswith("[]"):
            component = _java_type(name[:-2])
            return jclass("java.lang.reflect.Array").newInstance(component, 0).getClass()
        if name in _PRIMITIVES:
            return jclass(_PRIMITIVES[name]).TYPE
        return jclass(name).getClass()
    if isinstance(t, type):  # Chaquopy jclass proxy
        return t.getClass()
    return t  # already a java.lang.Class


def _java_types(types) -> Optional[Any]:
    if types is None:
        return None
    return jarray(_Class)([_java_type(t) for t in types])


# ---------------------------------------------------------------- declarations

class _Decl:
    kind = ""


def joverride(name: Optional[str] = None, arg_types=None):
    """Overrides the parent Java method ``name`` (default: the Python method's name)."""
    if callable(name) and arg_types is None:  # used as bare @joverride
        fn = name
        fn._yili_decl = ("override", fn.__name__, None)
        return fn

    def decorate(fn):
        fn._yili_decl = ("override", name or fn.__name__, arg_types)
        return fn
    return decorate


def joverload(name: str, arg_types):
    """Overrides one overload of ``name``: ``@joverload("add", ["java.lang.Object"])``."""
    return joverride(name, arg_types)


def jmethod(*spec, **kwargs):
    """Adds a new Java method: ``@jmethod("name", ret, [args])``, ``@jmethod(ret, [args])``, or
    ``@jmethod()`` with Python annotations as the Java types."""
    name = kwargs.get("name")
    ret = kwargs.get("return_type")
    args = kwargs.get("arg_types")
    if len(spec) == 3:
        name, ret, args = spec
    elif len(spec) == 2:
        ret, args = spec
    elif len(spec) == 1 and callable(spec[0]):
        return jmethod()(spec[0])

    def decorate(fn):
        r, a = ret, args
        if r is None or a is None:
            sig = inspect.signature(fn)
            params = list(sig.parameters.values())[1:]
            if a is None:
                a = [p.annotation if p.annotation is not p.empty else "java.lang.Object" for p in params]
            if r is None:
                r = sig.return_annotation if sig.return_annotation is not sig.empty else "void"
        fn._yili_decl = ("method", name or fn.__name__, (r, a))
        return fn
    return decorate


class _Accessor:
    def __init__(self, kind, name):
        self.kind = kind
        self.name = name


def jgetmethod(name: str) -> _Accessor:
    return _Accessor("get", name)


def jsetmethod(name: str) -> _Accessor:
    return _Accessor("set", name)


class jfield(_Decl):
    """A field on the generated Java class, read and written like a normal attribute."""
    kind = "field"

    def __init__(self, type, default=None, methods=None):
        self.type = type
        self.default = default
        self.methods = list(methods or [])
        self.name = None

    def __set_name__(self, owner, name):
        self.name = name

    def __get__(self, peer, owner=None):
        if peer is None:
            return self
        java = peer.__dict__.get("_java")
        if java is None:
            return peer.__dict__.get("_pending_fields", {}).get(self.name, self.default)
        return getattr(java, self.name)

    def __set__(self, peer, value):
        java = peer.__dict__.get("_java")
        if java is None:
            peer.__dict__.setdefault("_pending_fields", {})[self.name] = value
        else:
            _ProxyRuntime.setField(java, self.name, value)


def jconstructor(arg_types=None):
    """Runs after the Java constructor with these parameter types (any, if None)."""
    def decorate(fn):
        fn._yili_decl = ("ctor", None, arg_types)
        return fn
    return decorate


def jpreconstructor(arg_types=None):
    """Called as ``fn(cls, *args)`` before the parent constructor; returns replacement
    arguments (list/tuple, a single value, or None to keep them)."""
    def decorate(fn):
        fn._yili_decl = ("prector", None, arg_types)
        return classmethod(fn)
    return decorate


def jclassbuilder():
    """``fn(cls, builder)`` runs before the class is generated; ``builder`` is YiliGram's
    ``com.yiligram.hook.proxy.ProxyBuilder``."""
    def decorate(fn):
        fn._yili_decl = ("builder", None, None)
        return classmethod(fn)
    return decorate


class _Mvel(_Decl):
    kind = "mvel"

    def __init__(self, *args, **kwargs):
        self.args = args
        self.kwargs = kwargs


def jMVELmethod(*args, **kwargs):
    return _Mvel(*args, **kwargs)


def jMVELoverride(*args, **kwargs):
    return _Mvel(*args, **kwargs)


jmvelmethod = jMVELmethod
jmveloverride = jMVELoverride


class PyObj:
    """Carries a Python object through Java APIs (Chaquopy wraps it as a PyObject)."""

    @staticmethod
    def create(obj):
        return obj


# ---------------------------------------------------------------- class state and dispatch

def _same_types(java_types, wanted) -> bool:
    if wanted is None:
        return True
    try:
        wanted_classes = [_java_type(t) for t in wanted]
    except Exception:
        return False
    return len(wanted_classes) == len(java_types) and all(a.equals(b) for a, b in zip(java_types, wanted_classes))


class _State:
    def __init__(self, cls, builder, java_class, method_names, ctor_types, plugin_id):
        self.cls = cls
        self.builder = builder
        self.java_class = java_class
        self.method_names = method_names  # method index -> Python attribute name
        self.ctor_types = ctor_types      # constructor index -> [java.lang.Class]
        self.plugin_id = plugin_id
        self.released = False


class _Handler(dynamic_proxy(_ProxyHandler)):
    def __init__(self, cls):
        super().__init__()
        self.cls = cls

    def preConstruct(self, ctor, args):
        cls = self.cls
        state = cls._yili_state
        types = state.ctor_types[ctor]
        values = list(args)
        for fn in cls._yili_prectors:
            if _same_types(types, fn._yili_types):
                result = fn(cls, *values)
                if result is None:
                    continue
                values = list(result) if isinstance(result, (list, tuple)) else [result]
        return jarray(_Object)(values)

    def postConstruct(self, java_self, ctor, args):
        cls = self.cls
        args = list(args)
        pending = getattr(_creating, "pending", None)
        if pending is not None and pending[0] is cls:
            peer, init_args = pending[1], pending[2]
            _creating.pending = None
        else:
            peer, init_args = cls.__new__(cls), None
        _attach(peer, java_self)
        init = init_args if init_args is not None else args
        peer.__init__(*init)
        types = cls._yili_state.ctor_types[ctor]
        for fn in cls._yili_ctors:
            if _same_types(types, fn._yili_types):
                fn(peer, *args)
        peer.on_post_init(*args)

    def invoke(self, java_self, index, args):
        state = self.cls._yili_state
        name = state.method_names.get(index)
        if name is None or state.released:
            return _CALL_SUPER
        peer = _peer_of(java_self)
        if peer is None:
            return _CALL_SUPER
        return getattr(peer, name)(*list(args))


def _attach(peer, java_self):
    pending = peer.__dict__.pop("_pending_fields", {})
    peer.__dict__["_java"] = java_self
    for klass in type(peer).__mro__:
        for attr, value in vars(klass).items():
            if isinstance(value, jfield) and attr not in pending and value.default is not None:
                _ProxyRuntime.setField(java_self, value.name, value.default)
    for attr, value in pending.items():
        _ProxyRuntime.setField(java_self, attr, value)
    with _peers_lock:
        peer_id = next(_next_peer_id)
        _peers[peer_id] = peer
    peer.__dict__["_peer_id"] = peer_id
    _ProxyRuntime.setField(java_self, _PEER_FIELD, peer_id)


def _peer_of(java_self):
    try:
        peer_id = _ProxyRuntime.getField(java_self, _PEER_FIELD)
    except Exception:
        return None
    return _peers.get(peer_id) if peer_id else None


def _detach(peer):
    java = peer.__dict__.get("_java")
    with _peers_lock:
        _peers.pop(peer.__dict__.pop("_peer_id", 0), None)
    if java is not None:
        try:
            _ProxyRuntime.setField(java, _PEER_FIELD, 0)
        except Exception:
            pass
    peer.__dict__["_java"] = None


def _caller_plugin_id() -> Optional[str]:
    frame = inspect.currentframe()
    try:
        while frame is not None:
            pid = frame.f_globals.get("__id__")
            if isinstance(pid, str) and frame.f_globals.get("__name__") != __name__:
                return pid
            frame = frame.f_back
        return None
    finally:
        del frame


# ---------------------------------------------------------------- Base

class Base:
    """Base class of managed Java subclasses (see ``java_subclass``)."""

    _yili_state: Optional[_State] = None
    _yili_prectors: List[Any] = []
    _yili_ctors: List[Any] = []

    def __init__(self, *args, **kwargs):
        pass

    def on_post_init(self, *args):
        pass

    def on_release(self):
        pass

    @property
    def java(self):
        return self.__dict__.get("_java")

    @property
    def this(self):
        return self.__dict__.get("_java")

    def __getattr__(self, name):
        java = self.__dict__.get("_java")
        if java is None or name.startswith("__"):
            raise AttributeError(name)
        return getattr(java, name)

    def jrelease(self):
        """Disconnects this peer: the Java object's methods then behave like the parent class."""
        java = self.__dict__.get("_java")
        if java is None:
            return
        try:
            self.on_release()
        finally:
            _detach(self)

    # -- class-level API
    @classmethod
    def bind(cls, base, *interfaces, methods=None, constructors=None, custom_name=None):
        return _bind(cls, base, interfaces, custom_name)

    @classmethod
    def java_class(cls):
        cls._require_bound()
        return cls._yili_state.java_class

    @classmethod
    def new_java_instance(cls, *args):
        cls._require_bound()
        return _ProxyRuntime.newInstance(cls._yili_state.java_class, jarray(_Object)(list(args)))

    @classmethod
    def new_instance(cls, *args, init_args=None):
        cls._require_bound()
        peer = cls.__new__(cls)
        _creating.pending = (cls, peer, list(init_args) if init_args is not None else None)
        try:
            java = _ProxyRuntime.newInstance(cls._yili_state.java_class, jarray(_Object)(list(args)))
        finally:
            _creating.pending = None
        found = _peer_of(java)
        return found if found is not None else peer

    @classmethod
    def from_java(cls, java_obj):
        cls._require_bound()
        if java_obj is None:
            return None
        peer = _peer_of(java_obj)
        if peer is not None:
            return peer
        if not cls._yili_state.java_class.isInstance(java_obj):
            raise TypeError(f"{java_obj} is not an instance of {cls.__name__}")
        peer = cls.__new__(cls)
        _attach(peer, java_obj)
        return peer

    @classmethod
    def _require_bound(cls):
        if cls.__dict__.get("_yili_state") is None:
            raise RuntimeError(f"{cls.__name__} is not bound to a Java class: use @java_subclass(...)")


def java_subclass(base, *interfaces, methods=None, constructors=None, custom_name=None):
    """Class decorator: ``@java_subclass(JavaBase, Interface1, …)``."""
    def decorate(cls):
        _bind(cls, base, interfaces, custom_name)
        return cls
    return decorate


def _bind(cls, base, interfaces, custom_name):
    if cls.__dict__.get("_yili_state") is not None:
        raise RuntimeError(f"{cls.__name__} is already bound")
    builder = _ProxyBuilder(_java_type(base), jarray(_Class)([_java_type(i) for i in interfaces]),
                            custom_name or cls.__name__)
    builder.addField(_PEER_FIELD, jclass("java.lang.Integer").TYPE)

    method_names: Dict[int, str] = {}
    supers: Dict[str, str] = {}  # Python name -> Java name with a parent implementation
    prectors, ctors, builders = [], [], []
    for attr, value in list(vars(cls).items()):
        if isinstance(value, _Mvel):
            raise NotImplementedError(f"{cls.__name__}.{attr}: MVEL-backed methods are not supported by "
                                      "YiliGram yet; use a Python method")
        if isinstance(value, jfield):
            builder.addField(value.name or attr, _java_type(value.type))
            for acc in value.methods:
                if acc.kind == "get":
                    builder.addGetter(acc.name, value.name or attr)
                else:
                    builder.addSetter(acc.name, value.name or attr)
            continue
        target = value.__func__ if isinstance(value, classmethod) else value
        decl = getattr(target, "_yili_decl", None)
        if decl is None:
            continue
        kind, java_name, extra = decl
        if kind == "override":
            for idx in builder.override(java_name, _java_types(extra)):
                method_names[idx] = attr
            supers[attr] = java_name
        elif kind == "method":
            ret, args = extra
            idx = builder.addMethod(java_name, _java_type(ret), jarray(_Class)([_java_type(a) for a in args]))
            method_names[idx] = attr
        elif kind == "prector":
            target._yili_types = extra
            prectors.append(target)
        elif kind == "ctor":
            target._yili_types = extra
            ctors.append(target)
        elif kind == "builder":
            builders.append(target)

    # super().<python name>(...) reaches the Java parent through generated yili_super_* bridges.
    bridge_ns = {}
    for py_name, java_name in supers.items():
        bridge_ns[py_name] = _make_super_bridge(java_name)
    bases = tuple(b for b in cls.__bases__)
    if bridge_ns:
        bridge = type(cls.__name__ + "_JavaSuper", bases, bridge_ns)
        cls.__bases__ = (bridge,)

    for fn in builders:
        fn(cls, builder)
    cls._yili_prectors = prectors
    cls._yili_ctors = ctors
    handler = _Handler(cls)
    java_class = builder.build(handler)
    ctor_types = [list(c.getParameterTypes()) for c in builder.getConstructors()]
    plugin_id = _caller_plugin_id()
    cls._yili_state = _State(cls, builder, java_class, method_names, ctor_types, plugin_id)
    cls._yili_handler = handler  # keep the proxy alive
    _plugin_classes.setdefault(plugin_id, []).append(cls)
    return cls


def _make_super_bridge(java_name):
    def bridge(self, *args):
        java = self.__dict__.get("_java")
        if java is None:
            raise RuntimeError("peer is not attached to a Java object")
        return getattr(java, "yili_super_" + java_name)(*args)
    bridge.__name__ = java_name
    return bridge


def drop_plugin(plugin_id: str):
    """Detaches every class generated by a plugin (called when it unloads)."""
    classes = _plugin_classes.pop(plugin_id, [])
    for cls in classes:
        state = cls.__dict__.get("_yili_state")
        if state is not None:
            state.released = True
            _ProxyRuntime.release(state.java_class)
    for peer in [p for p in list(_peers.values()) if isinstance(p, tuple(classes))] if classes else []:
        try:
            peer.on_release()
        except Exception:
            _logger.exception("on_release of %s failed", type(peer).__name__)
        _detach(peer)

