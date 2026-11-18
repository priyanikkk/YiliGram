"""Host-side tests of the plugin engine (no Android, no java module).

    python3.11 -m unittest discover -s TMessagesProj_App/src/test/python
"""
import json
import os
import shutil
import sys
import tempfile
import types
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "TMessagesProj_App", "src", "main", "python"))

# ui.settings is pure Python; stub the java bridge for modules that need it at import time.
sys.modules.setdefault("java", types.SimpleNamespace(jclass=lambda name: None, dynamic_proxy=lambda c: object))

import yili_engine as engine  # noqa: E402

EXAMPLES = os.path.join(ROOT, "examples", "plugins")


class Params:
    def __init__(self, message):
        self.message = message


class EngineTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.src = tempfile.mkdtemp()
        engine._plugins.clear()
        engine._hooks.clear()
        engine._send_hooks.clear()
        engine._substring_hooks.clear()
        engine._settings.clear()
        engine._state = {"enabled": {}, "loading": None, "errors": {}}
        engine.init(self.dir)

    def tearDown(self):
        shutil.rmtree(self.dir)
        shutil.rmtree(self.src)

    def install(self, name):
        return json.loads(engine.install(os.path.join(EXAMPLES, name)))

    def test_metadata_of_all_examples(self):
        for name in os.listdir(EXAMPLES):
            if name.endswith(".plugin"):
                with open(os.path.join(EXAMPLES, name), encoding="utf-8") as f:
                    meta = engine.parse_metadata(f.read())
                self.assertIsNone(engine.validate_metadata(meta), name)

    def test_invalid_id_rejected(self):
        meta = engine.parse_metadata('__id__ = "1bad"\n__name__ = "x"\n')
        self.assertIsNotNone(engine.validate_metadata(meta))

    def test_version_checks(self):
        self.assertTrue(engine.check_version(">=12.5.1", "12.6.4"))
        self.assertFalse(engine.check_version(">=13.0", "12.6.4"))
        self.assertTrue(engine.check_version("<=1.4.5.3", "1.4.5.3"))
        self.assertTrue(engine.check_version(None, "1"))

    def test_send_message_hook_modifies(self):
        r = self.install("hello_command.plugin")
        self.assertIsNone(r["error"])
        self.assertIsNone(engine.set_enabled("hello_command", True))
        code, params = engine.dispatch_send_message(0, Params(".hello Alice"))
        self.assertEqual(code, 2)
        self.assertEqual(params.message, "Hello, Alice! 👋")
        code, _ = engine.dispatch_send_message(0, Params("just text"))
        self.assertEqual(code, 0)

    def test_request_hook_cancels_and_respects_setting(self):
        self.install("ghost_typing.plugin")
        engine.set_enabled("ghost_typing", True)
        summary = json.loads(engine.hook_summary())
        self.assertIn("TL_messages_setTyping", summary["names"])
        self.assertEqual(engine.dispatch_pre_request("TL_messages_setTyping", 0, object())[0], 1)
        engine.set_setting("ghost_typing", "enabled", False)
        self.assertEqual(engine.dispatch_pre_request("TL_messages_setTyping", 0, object())[0], 0)

    def test_disable_drops_hooks(self):
        self.install("ghost_typing.plugin")
        engine.set_enabled("ghost_typing", True)
        engine.set_enabled("ghost_typing", False)
        self.assertEqual(json.loads(engine.hook_summary())["names"], [])

    def test_settings_json_bridge(self):
        self.install("ghost_typing.plugin")
        engine.set_enabled("ghost_typing", True)
        items = json.loads(engine.settings_json("ghost_typing"))
        self.assertEqual([i["kind"] for i in items], ["header", "switch"])
        self.assertTrue(items[1]["value"])
        engine.settings_action("ghost_typing", 1, "set", "false")
        self.assertFalse(engine.get_setting("ghost_typing", "enabled"))

    def test_failing_plugin_is_isolated_and_disabled(self):
        src = ('from base_plugin import BasePlugin\n__id__ = "boom"\n__name__ = "Boom"\n'
               'class B(BasePlugin):\n'
               '    def on_plugin_load(self):\n        self.add_on_send_message_hook()\n'
               '    def on_send_message_hook(self, account, params):\n        raise ValueError("boom")\n')
        path = os.path.join(self.src, "boom_src.py")
        with open(path, "w") as f:
            f.write(src)
        engine.install(path)
        os.remove(path)
        engine.set_enabled("boom", True)
        for _ in range(engine.MAX_CONSECUTIVE_ERRORS):
            self.assertEqual(engine.dispatch_send_message(0, Params("x"))[0], 0)
        self.assertFalse(engine._state["enabled"]["boom"])
        self.assertIn("errors in a row", engine._state["errors"]["boom"])

    def test_safe_mode_disables_plugin_that_crashed_while_loading(self):
        self.install("hello_command.plugin")
        engine.set_enabled("hello_command", True)
        engine._state["loading"] = "hello_command"  # simulate a crash during on_plugin_load
        engine._save_state()
        report = json.loads(engine.init(self.dir))
        self.assertEqual(report["safe_mode_disabled"], "hello_command")
        self.assertFalse(engine._state["enabled"]["hello_command"])

    def test_duplicate_id_prefers_installed_file(self):
        self.install("hello_command.plugin")
        stray = os.path.join(self.dir, "zzz_copy.py")
        shutil.copy(os.path.join(EXAMPLES, "hello_command.plugin"), stray)
        engine.scan()
        self.assertTrue(engine._plugins["hello_command"]["path"].endswith("hello_command.plugin"))

    def test_incompatible_app_version(self):
        src = '__id__ = "future"\n__name__ = "F"\n__app_version__ = ">=99.0"\n'
        path = os.path.join(self.src, "f.py")
        with open(path, "w") as f:
            f.write(src)
        engine.install(path)
        os.remove(path)
        err = engine.set_enabled("future", True)
        self.assertIn("Needs app version", err)


if __name__ == "__main__":
    unittest.main()


@unittest.skipUnless(os.environ.get("YILI_NETWORK_TESTS") == "1", "needs network (YILI_NETWORK_TESTS=1)")
class PipTest(unittest.TestCase):
    def setUp(self):
        import yili_pip
        self.pip = yili_pip
        self.libs = tempfile.mkdtemp()
        yili_pip.init(self.libs)

    def tearDown(self):
        sys.path.remove(self.libs)
        shutil.rmtree(self.libs)

    def test_install_import_release(self):
        self.pip.install(["tinydb>=4"], "demo")
        import importlib
        importlib.invalidate_caches()
        tinydb = importlib.import_module("tinydb")
        self.assertTrue(hasattr(tinydb, "TinyDB"))
        self.assertIn("tinydb", self.pip.release("demo"))
        self.assertFalse(any(n.startswith("tinydb") for n in os.listdir(self.libs)))

    def test_rejects_c_extension_only_package(self):
        with self.assertRaises(self.pip.PipError) as ctx:
            self.pip.install(["numpy"], "demo")
        self.assertIn("No pure-Python wheel", str(ctx.exception))


class DevServerTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        engine._plugins.clear()
        engine._hooks.clear()
        engine._send_hooks.clear()
        engine._state = {"enabled": {}, "loading": None, "errors": {}}
        engine.init(self.dir)
        import yili_devserver
        self.dev = yili_devserver

    def tearDown(self):
        shutil.rmtree(self.dir)

    def test_protocol(self):
        self.assertTrue(self.dev.handle({"@": "ping", "#": "1"})["pong"])
        with open(os.path.join(EXAMPLES, "hello_command.plugin"), encoding="utf-8") as f:
            res = self.dev.handle({"@": "write_plugin", "#": "2", "content": f.read()})
        self.assertTrue(res["success"], res)
        self.assertEqual(res["#"], "2")
        plugins = self.dev.handle({"@": "get_plugins", "#": "3"})["plugins"]
        self.assertTrue(plugins[0]["enabled"])
        self.assertTrue(self.dev.handle({"@": "reload_plugin", "#": "4", "plugin_id": "hello_command"})["success"])
        self.assertTrue(self.dev.handle({"@": "remove_plugin", "#": "5", "plugin_id": "hello_command"})["success"])
        self.assertIn("Unknown command", self.dev.handle({"@": "nope", "#": "6"})["error"])
