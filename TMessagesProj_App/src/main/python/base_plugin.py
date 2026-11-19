"""exteraGram-compatible plugin base classes (``from base_plugin import BasePlugin``).

Implemented from the public exteraGram plugin SDK documentation; no exteraGram code is used.
"""
import enum
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, List, Optional

import yili_engine as _engine
from log_utils import Logger


class HookStrategy(enum.Enum):
    DEFAULT = 0       # do nothing special
    CANCEL = 1        # stop the operation
    MODIFY = 2        # replace the object with the one in the HookResult
    MODIFY_FINAL = 3  # replace it and skip the remaining plugins


class HookResult:
    def __init__(self, strategy: HookStrategy = HookStrategy.DEFAULT, request: Any = None,
                 response: Any = None, update: Any = None, updates: Any = None, params: Any = None):
        self.strategy = strategy
        self.request = request
        self.response = response
        self.update = update
        self.updates = updates
        self.params = params

    def __repr__(self):
        return f"HookResult({self.strategy})"


class AppEvent(enum.Enum):
    START = "START"
    STOP = "STOP"
    PAUSE = "PAUSE"
    RESUME = "RESUME"


class MenuItemType(enum.Enum):
    MESSAGE_CONTEXT_MENU = "MESSAGE_CONTEXT_MENU"
    DRAWER_MENU = "DRAWER_MENU"
    MAIN_MENU = "MAIN_MENU"
    CHAT_ACTION_MENU = "CHAT_ACTION_MENU"
    PROFILE_ACTION_MENU = "PROFILE_ACTION_MENU"


@dataclass
class MenuItemData:
    menu_type: MenuItemType
    text: str
    on_click: Callable[[Dict[str, Any]], None]
    item_id: Optional[str] = None
    icon: Optional[str] = None
    subtext: Optional[str] = None
    condition: Optional[str] = None
    priority: int = 0


# ---------------------------------------------------------------- Xposed-style method hooks

class MethodHook:
    """Override before_hooked_method(param) and/or after_hooked_method(param)."""

    def before_hooked_method(self, param):
        pass

    def after_hooked_method(self, param):
        pass


class MethodReplacement(MethodHook):
    """Override replace_hooked_method(param); its return value replaces the original call."""

    def replace_hooked_method(self, param):
        return None

    def before_hooked_method(self, param):
        try:
            param.setResult(self.replace_hooked_method(param))
        except Exception as e:  # mirror Xposed: an exception becomes the call's exception
            param.setThrowable(_engine.to_java_exception(e))


class BaseHook(MethodHook):
    """Functional hook built by BasePlugin.hook_method(before=..., after=...)."""

    def __init__(self, before=None, after=None, before_filters=None, after_filters=None):
        self._before = before
        self._after = after
        self._before_filters = before_filters or []
        self._after_filters = after_filters or []

    def before_hooked_method(self, param):
        if self._before and all(f.matches(param) for f in self._before_filters):
            self._before(param)

    def after_hooked_method(self, param):
        if self._after and all(f.matches(param) for f in self._after_filters):
            self._after(param)


class _Filter:
    def __init__(self, fn, name):
        self._fn = fn
        self._name = name

    def matches(self, param) -> bool:
        try:
            return bool(self._fn(param))
        except Exception:
            return False

    def __repr__(self):
        return f"HookFilter.{self._name}"


class HookFilter:
    RESULT_IS_NULL = _Filter(lambda p: p.getResult() is None, "RESULT_IS_NULL")
    RESULT_NOT_NULL = _Filter(lambda p: p.getResult() is not None, "RESULT_NOT_NULL")
    RESULT_IS_TRUE = _Filter(lambda p: p.getResult() is True, "RESULT_IS_TRUE")
    RESULT_IS_FALSE = _Filter(lambda p: p.getResult() is False, "RESULT_IS_FALSE")

    @staticmethod
    def ResultIsInstanceOf(clazz):
        return _Filter(lambda p: clazz.isInstance(p.getResult()), "ResultIsInstanceOf")

    @staticmethod
    def ResultEqual(value):
        return _Filter(lambda p: p.getResult() == value, "ResultEqual")

    @staticmethod
    def ResultNotEqual(value):
        return _Filter(lambda p: p.getResult() != value, "ResultNotEqual")

    @staticmethod
    def ArgumentIsNull(index):
        return _Filter(lambda p: p.args[index] is None, "ArgumentIsNull")

    @staticmethod
    def ArgumentNotNull(index):
        return _Filter(lambda p: p.args[index] is not None, "ArgumentNotNull")

    @staticmethod
    def ArgumentIsTrue(index):
        return _Filter(lambda p: p.args[index] is True, "ArgumentIsTrue")

    @staticmethod
    def ArgumentIsFalse(index):
        return _Filter(lambda p: p.args[index] is False, "ArgumentIsFalse")

    @staticmethod
    def ArgumentIsInstanceOf(index, clazz):
        return _Filter(lambda p: clazz.isInstance(p.args[index]), "ArgumentIsInstanceOf")

    @staticmethod
    def ArgumentEqual(index, value):
        return _Filter(lambda p: p.args[index] == value, "ArgumentEqual")

    @staticmethod
    def ArgumentNotEqual(index, value):
        return _Filter(lambda p: p.args[index] != value, "ArgumentNotEqual")

    @staticmethod
    def Condition(condition, object=None):
        return _Filter(lambda p: _engine.mvel_condition(condition, p, object), "Condition")

    @staticmethod
    def Or(*filters):
        return _Filter(lambda p: any(f.matches(p) for f in filters), "Or")


def hook_filters(*filters):
    """Decorator for before_hooked_method / after_hooked_method of a MethodHook subclass."""
    def decorate(fn):
        def wrapper(self, param):
            if all(f.matches(param) for f in filters):
                return fn(self, param)
        wrapper.__wrapped__ = fn
        return wrapper
    return decorate


# ---------------------------------------------------------------- BasePlugin

class BasePlugin:
    """Base class of every plugin. Exactly one subclass per plugin file."""

    def __init__(self):
        self.id: str = ""
        self.name: str = ""
        self.version: str = "1.0"
        self.logger: Logger = Logger("plugin")
        self._menu_items: List[str] = []

    # -- lifecycle (override)
    def on_plugin_load(self):
        pass

    def on_plugin_unload(self):
        pass

    def on_app_event(self, event_type: AppEvent):
        pass

    def create_settings(self) -> List[Any]:
        return []

    # -- hooks (override the callbacks, register in on_plugin_load)
    def pre_request_hook(self, request_name: str, account: int, request: Any) -> HookResult:
        return HookResult()

    def post_request_hook(self, request_name: str, account: int, response: Any, error: Any) -> HookResult:
        return HookResult()

    def on_update_hook(self, update_name: str, account: int, update: Any) -> HookResult:
        return HookResult()

    def on_updates_hook(self, container_name: str, account: int, updates: Any) -> HookResult:
        return HookResult()

    def on_send_message_hook(self, account: int, params: Any) -> HookResult:
        return HookResult()

    def add_hook(self, name: str, match_substring: bool = False, priority: int = 0):
        _engine.register_hook(self, name, match_substring, priority)

    def add_on_send_message_hook(self, priority: int = 0):
        _engine.register_send_message_hook(self, priority)

    def remove_hook(self, name: str):
        _engine.unregister_hook(self, name)

    # -- settings
    def get_setting(self, key: str, default: Any = None) -> Any:
        return _engine.get_setting(self.id, key, default)

    def set_setting(self, key: str, value: Any, reload_settings: bool = False):
        _engine.set_setting(self.id, key, value, reload_settings)

    def reload_settings(self):
        """Asks an open settings screen of this plugin to rebuild its items."""
        _engine._notify("onPluginsChanged")

    def export_settings(self) -> Dict[str, Any]:
        return _engine.export_settings(self.id)

    def import_settings(self, settings: Dict[str, Any], reload_settings: bool = True):
        _engine.import_settings(self.id, settings, reload_settings)

    # -- menu items
    def add_menu_item(self, item: MenuItemData) -> str:
        item_id = _engine.add_menu_item(self, item)
        self._menu_items.append(item_id)
        return item_id

    def remove_menu_item(self, item_id: str):
        _engine.remove_menu_item(item_id)
        if item_id in self._menu_items:
            self._menu_items.remove(item_id)

    # -- Java method hooks (Xposed-style)
    def hook_method(self, method, handler=None, before=None, after=None,
                    before_filters=None, after_filters=None, priority: int = 50):
        if handler is None:
            handler = BaseHook(before, after, before_filters, after_filters)
        return _engine.hook_method(self, method, handler, priority)

    def hook_all_methods(self, cls, method_name: str, handler, priority: int = 50) -> list:
        import hook_utils
        return [self.hook_method(m, handler, priority=priority)
                for m in hook_utils.as_java_class(cls).getDeclaredMethods() if m.getName() == method_name]

    def hook_all_constructors(self, cls, handler, priority: int = 50) -> list:
        import hook_utils
        return [self.hook_method(c, handler, priority=priority)
                for c in hook_utils.as_java_class(cls).getDeclaredConstructors()]

    def unhook_method(self, unhook_obj):
        _engine.unhook_method(unhook_obj)

    # -- accounts / logging
    def client(self, account: Optional[int] = None):
        import client_utils
        return client_utils.get_client(account)

    def log(self, message):  # legacy
        self.logger.info(str(message))
