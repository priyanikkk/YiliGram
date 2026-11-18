"""Python stand-ins for the exteraGram plugin-manager classes some plugins import directly.

Plugins sometimes reach into exteraGram's Java internals, e.g.
``from com.exteragram.messenger.plugins import PluginsController``. YiliGram has no such Java
classes; this module answers those imports with small Python objects backed by yili_engine.
Only the plugin-manager API is covered; other exteraGram internals (badges, feed, ...) still
raise ImportError, which plugins usually handle.
"""
import importlib.abc
import importlib.machinery
import sys
import types

import yili_engine as engine


class _PluginRecord:
    """What ``PluginsController.getInstance().plugins.get(id)`` returns."""

    def __init__(self, plugin_id):
        self.id = plugin_id
        rec = engine._plugins.get(plugin_id, {})
        meta = rec.get("meta", {})
        self.name = meta.get("name", plugin_id)
        self.version = str(meta.get("version", ""))
        self.description = meta.get("description", "")
        self.author = meta.get("author", "")

    def getId(self): return self.id
    def getName(self): return self.name
    def getVersion(self): return self.version
    def isEnabled(self): return bool(engine._state["enabled"].get(self.id))


class _PluginsMap:
    def get(self, plugin_id):
        return _PluginRecord(plugin_id) if plugin_id in engine._plugins else None

    def containsKey(self, plugin_id):
        return plugin_id in engine._plugins

    def keySet(self):
        return list(engine._plugins)

    def values(self):
        return [_PluginRecord(p) for p in engine._plugins]

    def __contains__(self, plugin_id):
        return plugin_id in engine._plugins

    def __iter__(self):
        return iter(list(engine._plugins))


class PluginsController:
    _instance = None

    def __init__(self):
        self.plugins = _PluginsMap()

    @classmethod
    def getInstance(cls):
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def setPluginEnabled(self, plugin_id, enabled, *callback):
        error = engine.set_enabled(str(plugin_id), bool(enabled))
        for cb in callback:
            if callable(cb):
                cb(error is None)
        return error is None

    def isPluginEnabled(self, plugin_id):
        return bool(engine._state["enabled"].get(str(plugin_id)))

    def reloadPlugin(self, plugin_id):
        plugin_id = str(plugin_id)
        was = engine._state["enabled"].get(plugin_id)
        engine.unload(plugin_id)
        if was:
            engine.load(plugin_id)
        engine._notify("onPluginsChanged")

    def loadPluginSettings(self, plugin_id):
        engine._notify("onPluginsChanged")

    def getPluginEngine(self):
        return engine

    def getPluginsDir(self):
        return engine._dir


def PluginSettingsActivity(plugin, *args):
    """Returns YiliGram's settings fragment for a plugin (record, id string or BasePlugin)."""
    from java import jclass
    plugin_id = getattr(plugin, "id", None) or str(plugin)
    name = getattr(plugin, "name", None) or plugin_id
    return jclass("com.yiligram.ui.PluginSettingsActivity")(plugin_id, name)


_MODULES = {
    "com.exteragram.messenger.plugins": {"PluginsController": PluginsController},
    "com.exteragram.messenger.plugins.ui": {"PluginSettingsActivity": PluginSettingsActivity},
}
_PACKAGES = {"com.exteragram", "com.exteragram.messenger"}


class _Finder(importlib.abc.MetaPathFinder, importlib.abc.Loader):
    def find_spec(self, fullname, path, target=None):
        if fullname in _MODULES or fullname in _PACKAGES:
            return importlib.machinery.ModuleSpec(fullname, self, is_package=True)
        if fullname == "com" and "com" not in sys.modules:
            # Only when nothing else provides "com" (on Android, Chaquopy's Java importer does).
            for finder in sys.meta_path:
                if finder is self:
                    continue
                try:
                    if finder.find_spec(fullname, path, target) is not None:
                        return None
                except Exception:
                    continue
            return importlib.machinery.ModuleSpec(fullname, self, is_package=True)
        return None

    def create_module(self, spec):
        return None

    def exec_module(self, module):
        module.__path__ = []
        for name, value in _MODULES.get(module.__name__, {}).items():
            setattr(module, name, value)


def install():
    if not any(isinstance(f, _Finder) for f in sys.meta_path):
        sys.meta_path.insert(0, _Finder())
