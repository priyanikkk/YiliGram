"""YiliGram plugin engine core.

Loads exteraGram-compatible plugins (single-file ``.plugin`` / ``.py``), keeps their state,
registers their hooks and dispatches Telegram events to them. Called from Java
(``com.yiligram.plugins.python.PythonPluginEngine``) through Chaquopy.

Design rules:
* metadata is read with ``ast`` only — a plugin's code never runs before the user enables it;
* every call into plugin code is isolated: an exception is logged and counted, never thrown
  into Telegram; a plugin that keeps failing is disabled;
* safe mode: the id of the plugin being loaded is persisted before ``on_plugin_load``; if the
  process dies there, that plugin is disabled on the next start.
"""
import ast
import json
import os
import re
import sys
import threading
import traceback
import types
from typing import Any, Dict, List, Optional, Tuple

import log_utils

# Versions this engine reports to plugins' __app_version__ / __sdk_version__ checks.
# The app version is the exteraGram release whose SDK surface we implement.
COMPAT_APP_VERSION = "12.6.4"
SDK_VERSION = "1.4.5.3"
ENGINE_VERSION = "0.1.0"

ID_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_-]{1,31}$")
META_KEYS = {
    "__id__": "id", "__name__": "name", "__description__": "description", "__author__": "author",
    "__version__": "version", "__icon__": "icon", "__app_version__": "app_version",
    "__sdk_version__": "sdk_version", "__min_version__": "min_version",
    "__requirements__": "requirements", "__category__": "category",
}
MAX_CONSECUTIVE_ERRORS = 5

_lock = threading.RLock()
_dir: Optional[str] = None
_bridge = None  # Java PythonPluginEngine: onHooksChanged(), onPluginsChanged()
_state: Dict[str, Any] = {"enabled": {}, "loading": None, "errors": {}}
_plugins: Dict[str, Dict[str, Any]] = {}  # id -> record
_hooks: Dict[str, List[Tuple[int, Any]]] = {}
_substring_hooks: List[Tuple[str, int, Any]] = []
_send_hooks: List[Tuple[int, Any]] = []
_menu_items: Dict[str, Tuple[Any, Any]] = {}
_settings: Dict[str, Dict[str, Any]] = {}
_logger = log_utils.Logger("yili.plugins")


# ---------------------------------------------------------------- helpers

def _state_path() -> str:
    return os.path.join(_dir, ".state.json")


def _save_state():
    tmp = _state_path() + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(_state, f)
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, _state_path())


def _settings_path(plugin_id: str) -> str:
    return os.path.join(_dir, "settings", plugin_id + ".json")


def _notify(method: str):
    if _bridge is not None:
        try:
            getattr(_bridge, method)()
        except Exception:
            _logger.exception("bridge %s failed", method)


def _version_tuple(v: str) -> Tuple[int, ...]:
    parts = []
    for p in re.split(r"[.\-]", str(v)):
        if not p.isdigit():
            break
        parts.append(int(p))
    return tuple(parts) or (0,)


def check_version(requirement: Optional[str], current: str) -> bool:
    """Evaluates ``">=12.5.1"``-style requirements (also ``<=, ==, >, <`` and a bare version)."""
    if not requirement:
        return True
    m = re.match(r"^\s*(>=|<=|==|>|<)?\s*([0-9][0-9.]*)", str(requirement))
    if not m:
        return True
    op, ver = m.group(1) or ">=", m.group(2)
    a, b = _version_tuple(current), _version_tuple(ver)
    width = max(len(a), len(b))
    a, b = a + (0,) * (width - len(a)), b + (0,) * (width - len(b))
    return {">=": a >= b, "<=": a <= b, "==": a == b, ">": a > b, "<": a < b}[op]


# ---------------------------------------------------------------- metadata

def parse_metadata(source: str) -> Dict[str, Any]:
    """Reads the plugin's top-level ``__id__ = "..."`` literals with ast (no code runs)."""
    tree = ast.parse(source)
    meta: Dict[str, Any] = {}
    for node in tree.body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
            key = node.targets[0].id
        elif isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) and node.value is not None:
            key = node.target.id
        else:
            continue
        if key not in META_KEYS:
            continue
        try:
            meta[META_KEYS[key]] = ast.literal_eval(node.value)
        except Exception:
            pass
    if "min_version" in meta and "app_version" not in meta:
        meta["app_version"] = ">=" + str(meta["min_version"])
    meta.setdefault("version", "1.0")
    meta.setdefault("description", "")
    meta.setdefault("author", "")
    req = meta.get("requirements") or []
    if isinstance(req, str):
        req = [r.strip() for r in req.split(",") if r.strip()]
    meta["requirements"] = list(req)
    cat = meta.get("category") or []
    meta["category"] = [cat] if isinstance(cat, str) else list(cat)[:3]
    return meta


def validate_metadata(meta: Dict[str, Any]) -> Optional[str]:
    pid = meta.get("id")
    if not isinstance(pid, str) or not ID_RE.match(pid):
        return "Invalid plugin id: must be 2–32 characters, start with a letter, letters/digits/_/- only"
    if not meta.get("name"):
        return "Missing __name__"
    return None


# ---------------------------------------------------------------- lifecycle

def init(plugins_dir: str, bridge=None, developer_mode: bool = False) -> str:
    """Prepares the engine and loads enabled plugins. Returns a JSON report for Java."""
    global _dir, _bridge, _state
    _dir = plugins_dir
    _bridge = bridge
    log_utils.developer_mode = bool(developer_mode)
    os.makedirs(os.path.join(_dir, "settings"), exist_ok=True)
    import yili_pip
    yili_pip.init(os.path.join(_dir, "libs"))
    try:
        with open(_state_path(), encoding="utf-8") as f:
            _state.update(json.load(f))
    except FileNotFoundError:
        pass
    except Exception:
        _logger.exception("plugin state is corrupt, starting clean")
    report = {"safe_mode_disabled": None}
    crashed = _state.get("loading")
    if crashed:
        # The previous process died while this plugin was loading: disable it.
        _state["enabled"][crashed] = False
        _state["errors"][crashed] = "Disabled: the app closed while this plugin was starting"
        _state["loading"] = None
        report["safe_mode_disabled"] = crashed
        _save_state()
        _logger.warning("safe mode: disabled %s", crashed)
    scan()
    for pid in list(_plugins):
        if _state["enabled"].get(pid):
            load(pid)
    if developer_mode:
        try:
            import yili_devserver
            yili_devserver.start()
            report["dev_server"] = yili_devserver.PORT
        except Exception as e:
            _logger.exception("dev server failed to start")
            report["dev_server_error"] = str(e)
    return json.dumps(report)


def scan():
    """Indexes plugin files (metadata only)."""
    with _lock:
        found = {}
        for name in sorted(os.listdir(_dir)):
            if not name.endswith((".plugin", ".py")) or name.startswith("."):
                continue
            path = os.path.join(_dir, name)
            try:
                with open(path, encoding="utf-8") as f:
                    meta = parse_metadata(f.read())
            except Exception as e:
                _logger.warning("cannot read %s: %s", name, e)
                continue
            if validate_metadata(meta):
                continue
            pid = meta["id"]
            if pid in found and os.path.basename(found[pid]["path"]) == pid + ".plugin":
                continue  # duplicate id: the installed <id>.plugin wins over stray copies
            old = _plugins.get(pid, {})
            found[pid] = {
                "meta": meta, "path": path,
                "module": old.get("module"), "instance": old.get("instance"),
                "errors": old.get("errors", 0),
            }
        for pid in set(_plugins) - set(found):
            unload(pid)
        _plugins.clear()
        _plugins.update(found)


def compatibility_error(meta: Dict[str, Any]) -> Optional[str]:
    if not check_version(meta.get("app_version"), COMPAT_APP_VERSION):
        return f"Needs app version {meta.get('app_version')}"
    if not check_version(meta.get("sdk_version"), SDK_VERSION):
        return f"Needs SDK {meta.get('sdk_version')}"
    requirements = meta.get("requirements", [])
    if requirements:
        import yili_pip
        try:
            yili_pip.install(requirements, meta["id"], _logger)
        except Exception as e:
            return str(e)
    return None


def load(plugin_id: str) -> Optional[str]:
    """Executes and starts a plugin. Returns an error message or None."""
    with _lock:
        rec = _plugins.get(plugin_id)
        if rec is None:
            return "Plugin not found"
        if rec.get("instance") is not None:
            return None
        meta = rec["meta"]
        err = compatibility_error(meta)
        if err:
            _state["errors"][plugin_id] = err
            _save_state()
            return err
        _state["loading"] = plugin_id
        _save_state()
        try:
            with open(rec["path"], encoding="utf-8") as f:
                source = f.read()
            module = types.ModuleType("yili_plugin_" + plugin_id.replace("-", "_"))
            module.__file__ = rec["path"]
            sys.modules[module.__name__] = module
            code = compile(source, rec["path"], "exec")
            plugin_logger = log_utils.Logger("plugin." + plugin_id)
            log_utils._current_logger = plugin_logger
            exec(code, module.__dict__)
            import base_plugin
            classes = [v for v in module.__dict__.values()
                       if isinstance(v, type) and issubclass(v, base_plugin.BasePlugin)
                       and v is not base_plugin.BasePlugin and v.__module__ == module.__name__]
            if not classes:
                raise RuntimeError("No BasePlugin subclass found")
            instance = classes[0]()
            instance.id = plugin_id
            instance.name = meta.get("name", plugin_id)
            instance.version = str(meta.get("version", "1.0"))
            instance.logger = plugin_logger
            rec["module"], rec["instance"], rec["errors"] = module, instance, 0
            instance.on_plugin_load()
            _state["errors"].pop(plugin_id, None)
            return None
        except BaseException as e:  # SystemExit from a plugin must not kill the app either
            rec["module"] = rec["instance"] = None
            _drop_registrations(plugin_id)
            msg = f"{type(e).__name__}: {e}"
            _state["errors"][plugin_id] = msg
            _logger.error("failed to load %s: %s\n%s", plugin_id, msg, traceback.format_exc())
            return msg
        finally:
            log_utils._current_logger = None
            _state["loading"] = None
            _save_state()
            _notify("onHooksChanged")


def unload(plugin_id: str):
    with _lock:
        rec = _plugins.get(plugin_id)
        if rec is None or rec.get("instance") is None:
            return
        try:
            rec["instance"].on_plugin_unload()
        except Exception:
            _logger.exception("on_plugin_unload of %s failed", plugin_id)
        _drop_registrations(plugin_id)
        sys.modules.pop(getattr(rec.get("module"), "__name__", ""), None)
        rec["module"] = rec["instance"] = None
        _notify("onHooksChanged")


def set_enabled(plugin_id: str, enabled: bool) -> Optional[str]:
    _state["enabled"][plugin_id] = bool(enabled)
    _save_state()
    if enabled:
        err = load(plugin_id)
        if err:
            _state["enabled"][plugin_id] = False
            _save_state()
        return err
    unload(plugin_id)
    return None


def inspect(path: str) -> str:
    """Metadata of a plugin file without installing or running it (JSON, with "error")."""
    try:
        with open(path, encoding="utf-8") as f:
            meta = parse_metadata(f.read())
    except Exception as e:
        return json.dumps({"error": f"Not a valid plugin: {e}"})
    meta["error"] = validate_metadata(meta)
    meta["installed_version"] = str(_plugins[meta["id"]]["meta"].get("version")) if meta.get("id") in _plugins else None
    return json.dumps(meta, default=str)


def install(path: str) -> str:
    """Copies a plugin file into the plugins dir. Returns JSON {id, error, updated}."""
    with open(path, encoding="utf-8") as f:
        source = f.read()
    try:
        meta = parse_metadata(source)
    except SyntaxError as e:
        return json.dumps({"error": f"Not a valid plugin: {e}"})
    err = validate_metadata(meta)
    if err:
        return json.dumps({"error": err})
    pid = meta["id"]
    was_enabled = _state["enabled"].get(pid, False)
    updated = pid in _plugins
    if updated:
        unload(pid)
        old = _plugins[pid]["path"]
        if os.path.basename(old) != pid + ".plugin":
            os.remove(old)
    target = os.path.join(_dir, pid + ".plugin")
    tmp = target + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(source)
    os.replace(tmp, target)
    scan()
    if was_enabled:
        load(pid)
    _notify("onPluginsChanged")
    return json.dumps({"id": pid, "error": None, "updated": updated})


def delete(plugin_id: str):
    unload(plugin_id)
    try:
        import yili_pip
        yili_pip.release(plugin_id)
    except Exception:
        _logger.exception("could not release requirements of %s", plugin_id)
    rec = _plugins.pop(plugin_id, None)
    if rec:
        try:
            os.remove(rec["path"])
        except FileNotFoundError:
            pass
    for d in (_state["enabled"], _state["errors"]):
        d.pop(plugin_id, None)
    try:
        os.remove(_settings_path(plugin_id))
    except FileNotFoundError:
        pass
    _save_state()
    _notify("onPluginsChanged")


def list_plugins() -> str:
    """JSON list for the plugin manager UI."""
    out = []
    for pid, rec in sorted(_plugins.items(), key=lambda kv: kv[1]["meta"].get("name", kv[0]).lower()):
        m = rec["meta"]
        out.append({
            "id": pid, "name": m.get("name"), "description": m.get("description"),
            "author": m.get("author"), "version": str(m.get("version")), "icon": m.get("icon"),
            "enabled": bool(_state["enabled"].get(pid)), "loaded": rec.get("instance") is not None,
            "error": _state["errors"].get(pid), "has_settings": _has_settings(rec),
            "category": m.get("category"),
        })
    return json.dumps(out)


def _has_settings(rec) -> bool:
    inst = rec.get("instance")
    if inst is None:
        return False
    import base_plugin
    return type(inst).create_settings is not base_plugin.BasePlugin.create_settings


def app_event(name: str):
    import base_plugin
    event = base_plugin.AppEvent(name)
    for pid, rec in list(_plugins.items()):
        inst = rec.get("instance")
        if inst is not None:
            _call(pid, inst.on_app_event, event)


# ---------------------------------------------------------------- hooks registry

def _plugin_id_of(plugin) -> str:
    return getattr(plugin, "id", "")


def register_hook(plugin, name: str, match_substring: bool, priority: int):
    with _lock:
        if match_substring:
            _substring_hooks.append((name, priority, plugin))
            _substring_hooks.sort(key=lambda t: -t[1])
        else:
            lst = _hooks.setdefault(name, [])
            lst.append((priority, plugin))
            lst.sort(key=lambda t: -t[0])
    _notify("onHooksChanged")


def unregister_hook(plugin, name: str):
    with _lock:
        if name in _hooks:
            _hooks[name] = [t for t in _hooks[name] if t[1] is not plugin]
            if not _hooks[name]:
                del _hooks[name]
        _substring_hooks[:] = [t for t in _substring_hooks if not (t[0] == name and t[2] is plugin)]
    _notify("onHooksChanged")


def register_send_message_hook(plugin, priority: int):
    with _lock:
        _send_hooks.append((priority, plugin))
        _send_hooks.sort(key=lambda t: -t[0])
    _notify("onHooksChanged")


def _drop_registrations(plugin_id: str):
    with _lock:
        for name in list(_hooks):
            _hooks[name] = [t for t in _hooks[name] if _plugin_id_of(t[1]) != plugin_id]
            if not _hooks[name]:
                del _hooks[name]
        _substring_hooks[:] = [t for t in _substring_hooks if _plugin_id_of(t[2]) != plugin_id]
        _send_hooks[:] = [t for t in _send_hooks if _plugin_id_of(t[1]) != plugin_id]
        for item_id in [k for k, (p, _) in _menu_items.items() if _plugin_id_of(p) == plugin_id]:
            del _menu_items[item_id]
        try:
            import hook_utils
            hook_utils._unhook_plugin(plugin_id)
        except Exception:
            pass


def hook_summary() -> str:
    """What Java must intercept: exact names, whether substring hooks exist, send hooks."""
    with _lock:
        return json.dumps({
            "names": sorted(_hooks),
            "substrings": sorted({t[0] for t in _substring_hooks}),
            "send_message": bool(_send_hooks),
        })


def _hooks_for(name: str) -> List[Any]:
    with _lock:
        result = [(p, plug) for p, plug in _hooks.get(name, [])]
        result += [(p, plug) for sub, p, plug in _substring_hooks if sub in name]
    result.sort(key=lambda t: -t[0])
    return [plug for _, plug in result]


def _call(plugin_id: str, fn, *args):
    """Runs plugin code; returns (ok, result). Disables the plugin after repeated failures."""
    rec = _plugins.get(plugin_id)
    try:
        log_utils._current_logger = getattr(rec.get("instance"), "logger", None) if rec else None
        result = fn(*args)
        if rec:
            rec["errors"] = 0
        return True, result
    except Exception:
        if rec is not None:
            rec["errors"] = rec.get("errors", 0) + 1
            getattr(rec.get("instance"), "logger", _logger).exception("hook failed")
            if rec["errors"] >= MAX_CONSECUTIVE_ERRORS:
                _state["errors"][plugin_id] = f"Disabled after {MAX_CONSECUTIVE_ERRORS} errors in a row"
                set_enabled(plugin_id, False)
                _notify("onPluginsChanged")
        return False, None
    finally:
        log_utils._current_logger = None


def _dispatch(name: str, plugins: List[Any], method: str, field: str, obj: Any, *prefix) -> List[Any]:
    """Runs a hook chain. Returns [strategy, object]: 0 default, 1 cancel, 2 modified."""
    modified = False
    for plugin in plugins:
        ok, res = _call(_plugin_id_of(plugin), getattr(plugin, method), *prefix, obj)
        if not ok or res is None:
            continue
        strategy = getattr(getattr(res, "strategy", None), "value", 0)
        if strategy == 1:  # CANCEL
            return [1, None]
        if strategy in (2, 3):  # MODIFY / MODIFY_FINAL
            new = getattr(res, field, None)
            if new is not None:
                obj = new
                modified = True
            if strategy == 3:
                break
    return [2 if modified else 0, obj]


def dispatch_pre_request(name: str, account: int, request: Any) -> List[Any]:
    return _dispatch(name, _hooks_for(name), "pre_request_hook", "request", request, name, account)


def dispatch_post_request(name: str, account: int, response: Any, error: Any) -> List[Any]:
    plugins = _hooks_for(name)
    modified, obj = False, response
    for plugin in plugins:
        ok, res = _call(_plugin_id_of(plugin), plugin.post_request_hook, name, account, obj, error)
        if not ok or res is None:
            continue
        strategy = getattr(getattr(res, "strategy", None), "value", 0)
        if strategy == 1:
            return [1, None]
        if strategy in (2, 3) and getattr(res, "response", None) is not None:
            obj, modified = res.response, True
            if strategy == 3:
                break
    return [2 if modified else 0, obj]


def dispatch_update(name: str, account: int, update: Any) -> List[Any]:
    return _dispatch(name, _hooks_for(name), "on_update_hook", "update", update, name, account)


def dispatch_updates(name: str, account: int, updates: Any) -> List[Any]:
    return _dispatch(name, _hooks_for(name), "on_updates_hook", "updates", updates, name, account)


def dispatch_send_message(account: int, params: Any) -> List[Any]:
    with _lock:
        plugins = [plug for _, plug in _send_hooks]
    return _dispatch("send_message", plugins, "on_send_message_hook", "params", params, account)


# ---------------------------------------------------------------- settings

def _load_settings(plugin_id: str) -> Dict[str, Any]:
    if plugin_id not in _settings:
        try:
            with open(_settings_path(plugin_id), encoding="utf-8") as f:
                _settings[plugin_id] = json.load(f)
        except Exception:
            _settings[plugin_id] = {}
    return _settings[plugin_id]


def get_setting(plugin_id: str, key: str, default=None):
    return _load_settings(plugin_id).get(key, default)


def set_setting(plugin_id: str, key: str, value, reload_settings: bool = False):
    data = _load_settings(plugin_id)
    data[key] = value
    with open(_settings_path(plugin_id), "w", encoding="utf-8") as f:
        json.dump(data, f)
    if reload_settings:
        _notify("onPluginsChanged")


def export_settings(plugin_id: str) -> Dict[str, Any]:
    return dict(_load_settings(plugin_id))


def import_settings(plugin_id: str, settings: Dict[str, Any], reload_settings: bool = True):
    data = _load_settings(plugin_id)
    data.update(settings)
    with open(_settings_path(plugin_id), "w", encoding="utf-8") as f:
        json.dump(data, f)
    if reload_settings:
        _notify("onPluginsChanged")


def create_settings(plugin_id: str) -> List[Any]:
    rec = _plugins.get(plugin_id)
    inst = rec.get("instance") if rec else None
    if inst is None:
        return []
    ok, items = _call(plugin_id, inst.create_settings)
    return list(items or []) if ok else []


# ---------------------------------------------------------------- menu items

def add_menu_item(plugin, item) -> str:
    item_id = item.item_id or f"{_plugin_id_of(plugin)}:{len(_menu_items)}"
    _menu_items[item_id] = (plugin, item)
    _notify("onPluginsChanged")
    return item_id


def remove_menu_item(item_id: str):
    _menu_items.pop(item_id, None)
    _notify("onPluginsChanged")


def menu_items(menu_type: str) -> List[Any]:
    items = [(p, i) for p, i in _menu_items.values() if i.menu_type.value == menu_type]
    items.sort(key=lambda t: -t[1].priority)
    return items


def click_menu_item(item_id: str, context: Dict[str, Any]):
    entry = _menu_items.get(item_id)
    if entry:
        plugin, item = entry
        _call(_plugin_id_of(plugin), item.on_click, context)


# ---------------------------------------------------------------- Java method hooks

def hook_method(plugin, method, handler, priority):
    import hook_utils
    return hook_utils._hook(plugin, method, handler, priority)


def unhook_method(handle):
    import hook_utils
    hook_utils._unhook(handle)


def to_java_exception(e: Exception):
    from java import jclass
    return jclass("java.lang.RuntimeException")(f"{type(e).__name__}: {e}")


def mvel_condition(condition: str, param, obj) -> bool:
    """Minimal evaluator for HookFilter.Condition; full MVEL support comes with class proxies."""
    _logger.warning("HookFilter.Condition is not supported yet: %s", condition)
    return True


# ---------------------------------------------------------------- settings UI bridge
# The settings screen is native (Java); it receives a JSON description of the plugin's
# create_settings() items and sends user actions back by item index.

_settings_items: Dict[str, List[Any]] = {}


def settings_json(plugin_id: str) -> str:
    items = create_settings(plugin_id)
    _settings_items[plugin_id] = items
    out = []
    for item in items:
        kind = getattr(item, "kind", None)
        d = {"kind": kind or "unknown"}
        for attr in ("text", "subtext", "key", "hint", "items", "accent", "red", "icon", "multiline", "max_length"):
            v = getattr(item, attr, None)
            if v is not None:
                d[attr] = v
        key = getattr(item, "key", None)
        if key is not None:
            d["value"] = get_setting(plugin_id, key, getattr(item, "default", None))
        d["clickable"] = bool(getattr(item, "on_click", None) or getattr(item, "create_sub_fragment", None))
        out.append(d)
    return json.dumps(out)


def settings_action(plugin_id: str, index: int, action: str, value_json: str = "null"):
    items = _settings_items.get(plugin_id) or []
    if not 0 <= index < len(items):
        return
    item = items[index]
    value = json.loads(value_json)
    key = getattr(item, "key", None)
    if action == "set" and key is not None:
        set_setting(plugin_id, key, value)
        cb = getattr(item, "on_change", None)
        if cb:
            _call(plugin_id, cb, value)
    elif action == "click":
        cb = getattr(item, "on_click", None)
        if cb:
            _call(plugin_id, cb, None)
