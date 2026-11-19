"""Global handlers for links, deeplinks and intents (exteraGram-compatible ``intents`` module).

YiliGram calls :func:`dispatch_intent` around ``LaunchActivity.handleIntent``: before-handlers can
consume an intent (return ``True``), after-handlers only observe it. Matching follows the
exteraGram documentation: scheme, host, path (with ``{name}`` templates), required query
arguments (``required_path_args_names``), action, flags, MIME type and categories.
Handlers run in ascending priority; callbacks receive only the arguments they ask for.
"""
import inspect
import itertools
import re
import threading
from typing import Any, Callable, Dict, List, Optional
from urllib.parse import parse_qsl, urlsplit

import log_utils

_logger = log_utils.Logger("yili.intents")
_lock = threading.RLock()
_handlers: Dict[str, "_Handler"] = {}
_counter = itertools.count(1)

STANDARD_ARGS = ("intent", "scheme", "host", "path", "query_args", "action", "flags", "type", "categories")


class _Handler:
    def __init__(self, handler_id, plugin_id, callback, before, priority, filters):
        self.handler_id = handler_id
        self.plugin_id = plugin_id
        self.callback = callback
        self.before = before
        self.priority = priority
        self.order = next(_counter)
        self.scheme = filters.get("scheme")
        self.host = filters.get("host")
        self.action = filters.get("action")
        self.type = filters.get("type")
        self.required = list(filters.get("required_path_args_names") or [])
        self.whitelist_flags = list(filters.get("whitelist_flags") or [])
        self.blacklist_flags = list(filters.get("blacklist_flags") or [])
        self.categories = list(filters.get("categories") or [])
        self.path_regex = _path_regex(filters.get("path"))

    def match(self, ctx: Dict[str, Any]) -> Optional[Dict[str, str]]:
        """Path-template variables if this handler matches, else None."""
        if self.scheme is not None and (ctx["scheme"] or "").lower() != self.scheme.lower():
            return None
        if self.host is not None and (ctx["host"] or "").lower() != self.host.lower():
            return None
        if self.action is not None and ctx["action"] != self.action:
            return None
        if self.type is not None and ctx["type"] != self.type:
            return None
        flags = ctx["flags"] or 0
        if any((flags & f) != f for f in self.whitelist_flags):
            return None
        if any(flags & f for f in self.blacklist_flags):
            return None
        if any(c not in ctx["categories"] for c in self.categories):
            return None
        if any(name not in ctx["query_args"] for name in self.required):
            return None
        if self.path_regex is None:
            return {}
        m = self.path_regex.match(ctx["path"] or "")
        return m.groupdict() if m else None


class HandlerHandle:
    def __init__(self, handler_id: str):
        self.handler_id = handler_id

    def unhandle(self):
        IntentsManager.unhandle(self.handler_id)

    def __repr__(self):
        return f"HandlerHandle({self.handler_id!r})"


class IntentsManager:
    class HandlerNotRegistered(Exception):
        pass

    @staticmethod
    def new_global_before_handler(callback: Callable, scheme: Optional[str] = None, host: Optional[str] = None,
                                  path: Optional[str] = None, required_path_args_names: Optional[List[str]] = None,
                                  action: Optional[str] = None, whitelist_flags: Optional[List[int]] = None,
                                  blacklist_flags: Optional[List[int]] = None, type: Optional[str] = None,
                                  categories: Optional[List[str]] = None, priority: int = 0) -> HandlerHandle:
        return _register(True, callback, priority, dict(
            scheme=scheme, host=host, path=path, required_path_args_names=required_path_args_names,
            action=action, whitelist_flags=whitelist_flags, blacklist_flags=blacklist_flags,
            type=type, categories=categories))

    @staticmethod
    def new_global_after_handler(callback: Callable, scheme: Optional[str] = None, host: Optional[str] = None,
                                 path: Optional[str] = None, required_path_args_names: Optional[List[str]] = None,
                                 action: Optional[str] = None, whitelist_flags: Optional[List[int]] = None,
                                 blacklist_flags: Optional[List[int]] = None, type: Optional[str] = None,
                                 categories: Optional[List[str]] = None, priority: int = 0) -> HandlerHandle:
        return _register(False, callback, priority, dict(
            scheme=scheme, host=host, path=path, required_path_args_names=required_path_args_names,
            action=action, whitelist_flags=whitelist_flags, blacklist_flags=blacklist_flags,
            type=type, categories=categories))

    @staticmethod
    def unhandle(handler_id: str):
        with _lock:
            if handler_id not in _handlers:
                raise IntentsManager.HandlerNotRegistered(f"Handler is not registered: {handler_id}")
            del _handlers[handler_id]
            _sync_java()

    @staticmethod
    def parse(url: str) -> Dict[str, str]:
        parts = urlsplit(url)
        out = {"scheme": parts.scheme, "host": parts.hostname or parts.netloc}
        if parts.path:
            out["path"] = parts.path
        if parts.query:
            out["query"] = parts.query
        return out


def _path_regex(path: Optional[str]):
    if path is None:
        return None
    if not path.startswith("/"):
        path = "/" + path
    pattern, pos = "", 0
    for m in re.finditer(r"\{([A-Za-z_][A-Za-z0-9_]*)\}", path):
        pattern += re.escape(path[pos:m.start()]) + f"(?P<{m.group(1)}>[^/]+)"
        pos = m.end()
    pattern += re.escape(path[pos:])
    return re.compile("^" + pattern + "/?$")


def _caller_plugin_id() -> Optional[str]:
    """The plugin whose code called us: plugin modules define ``__id__`` at top level."""
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


def _register(before: bool, callback: Callable, priority: int, filters: Dict[str, Any]) -> HandlerHandle:
    if not callable(callback):
        raise TypeError("callback must be callable")
    handler_id = f"{'before' if before else 'after'}-{next(_counter)}"
    with _lock:
        _handlers[handler_id] = _Handler(handler_id, _caller_plugin_id(), callback, before, int(priority), filters)
        _sync_java()
    return HandlerHandle(handler_id)


def drop_plugin(plugin_id: str):
    """Removes every handler registered by a plugin (called when it is unloaded)."""
    with _lock:
        for hid in [h for h, v in _handlers.items() if v.plugin_id == plugin_id]:
            del _handlers[hid]
        _sync_java()


def _sync_java():
    try:
        from java import jclass
        jclass("com.yiligram.plugins.Plugins").setIntentHooks(bool(_handlers))
    except Exception:
        pass  # host-side tests


def _context(intent) -> Dict[str, Any]:
    data = intent.getData()
    url = str(data.toString()) if data is not None else ""
    parts = urlsplit(url) if url else None
    query = dict(parse_qsl(parts.query, keep_blank_values=True)) if parts else {}
    cats = intent.getCategories()
    return {
        "intent": intent,
        "scheme": parts.scheme if parts else None,
        "host": (parts.hostname or parts.netloc or None) if parts else None,
        "path": parts.path if parts else None,
        "query_args": query,
        "action": intent.getAction(),
        "flags": intent.getFlags(),
        "type": intent.getType(),
        "categories": [str(c) for c in cats.toArray()] if cats is not None else [],
    }


def _call_with(callback: Callable, ctx: Dict[str, Any]):
    """Calls ``callback`` with only the arguments its signature asks for."""
    try:
        sig = inspect.signature(callback)
    except (TypeError, ValueError):
        return callback(**ctx)
    params = sig.parameters.values()
    if any(p.kind == p.VAR_POSITIONAL for p in params):
        args = [ctx.get(n) for n in STANDARD_ARGS]
        kwargs = ctx if any(p.kind == p.VAR_KEYWORD for p in params) else {}
        kwargs = {k: v for k, v in kwargs.items() if k not in STANDARD_ARGS}
        return callback(*args, **kwargs)
    if any(p.kind == p.VAR_KEYWORD for p in params):
        named = {p.name for p in params if p.kind in (p.POSITIONAL_OR_KEYWORD, p.KEYWORD_ONLY)}
        missing = [p.name for p in params if p.default is p.empty and p.name in named and p.name not in ctx
                   and p.kind in (p.POSITIONAL_OR_KEYWORD, p.KEYWORD_ONLY)]
        if missing:
            return None
        return callback(**ctx)
    kwargs = {}
    for p in params:
        if p.kind not in (p.POSITIONAL_OR_KEYWORD, p.KEYWORD_ONLY):
            continue
        if p.name in ctx:
            kwargs[p.name] = ctx[p.name]
        elif p.default is p.empty:
            return None  # a required value this intent does not have
    return callback(**kwargs)


def dispatch_intent(before: bool, intent) -> bool:
    """Runs matching handlers. Returns True if a before-handler consumed the intent."""
    with _lock:
        handlers = sorted((h for h in _handlers.values() if h.before == before),
                          key=lambda h: (h.priority, h.order))
    if not handlers:
        return False
    ctx = _context(intent)
    import yili_engine
    for h in handlers:
        variables = h.match(ctx)
        if variables is None:
            continue
        full = dict(ctx["query_args"])
        full.update(variables)
        full.update(ctx)
        if h.plugin_id is not None and h.plugin_id in yili_engine._plugins:
            ok, result = yili_engine._call(h.plugin_id, lambda: _call_with(h.callback, full))
        else:
            try:
                ok, result = True, _call_with(h.callback, full)
            except Exception:
                _logger.exception("intent handler %s failed", h.handler_id)
                ok, result = False, None
        if before and ok and result is True:
            return True
    return False
