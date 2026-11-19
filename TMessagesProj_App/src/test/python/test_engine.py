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

    def test_inspect_does_not_install(self):
        meta = json.loads(engine.inspect(os.path.join(EXAMPLES, "ghost_typing.plugin")))
        self.assertEqual(meta["id"], "ghost_typing")
        self.assertIsNone(meta["error"])
        self.assertIsNone(meta["installed_version"])
        self.assertNotIn("ghost_typing", engine._plugins)
        self.install("ghost_typing.plugin")
        meta = json.loads(engine.inspect(os.path.join(EXAMPLES, "ghost_typing.plugin")))
        self.assertEqual(meta["installed_version"], "1.0")

    def test_exteragram_plugin_manager_shim(self):
        self.install("hello_command.plugin")
        from com.exteragram.messenger.plugins import PluginsController  # served by yili_extera_compat
        pc = PluginsController.getInstance()
        self.assertEqual(pc.plugins.get("hello_command").getName(), "Hello command")
        self.assertIsNone(pc.plugins.get("nope"))
        self.assertTrue(pc.setPluginEnabled("hello_command", True))
        self.assertTrue(pc.isPluginEnabled("hello_command"))

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


class _FakeUri:
    def __init__(self, url):
        self.url = url

    def toString(self):
        return self.url


class _FakeIntent:
    def __init__(self, url, action="android.intent.action.VIEW", flags=0, categories=()):
        self.data = _FakeUri(url) if url else None
        self.action = action
        self.flags = flags
        self.categories = categories

    def getData(self):
        return self.data

    def getAction(self):
        return self.action

    def getFlags(self):
        return self.flags

    def getType(self):
        return None

    def getCategories(self):
        cats = list(self.categories)
        return types.SimpleNamespace(toArray=lambda: cats) if cats else None


class IntentsTest(unittest.TestCase):
    def setUp(self):
        import intents
        self.intents = intents
        intents._handlers.clear()
        self.IM = intents.IntentsManager

    def test_filters_arguments_and_consume(self):
        seen = []
        self.IM.new_global_before_handler(lambda id: seen.append(("chat", id)), scheme="tg", host="chat",
                                          required_path_args_names=["id"])
        self.IM.new_global_before_handler(lambda user_id, path=None: seen.append(("profile", user_id, path)),
                                          scheme="tg", host="profile", path="user/{user_id}")
        stop = self.IM.new_global_before_handler(lambda: True, scheme="tg", host="stop")
        self.assertFalse(self.intents.dispatch_intent(True, _FakeIntent("tg://chat")))
        self.assertFalse(self.intents.dispatch_intent(True, _FakeIntent("tg://chat?id=5")))
        self.assertFalse(self.intents.dispatch_intent(True, _FakeIntent("tg://profile/user/42")))
        self.assertEqual(seen, [("chat", "5"), ("profile", "42", "/user/42")])
        self.assertTrue(self.intents.dispatch_intent(True, _FakeIntent("tg://stop")))
        stop.unhandle()
        self.assertFalse(self.intents.dispatch_intent(True, _FakeIntent("tg://stop")))
        with self.assertRaises(self.IM.HandlerNotRegistered):
            self.IM.unhandle(stop.handler_id)

    def test_priority_kwargs_and_after(self):
        order = []
        self.IM.new_global_before_handler(lambda **kw: order.append(("b10", kw["host"])), scheme="https", priority=10)
        self.IM.new_global_before_handler(lambda *a: order.append(("b0", a[1])), scheme="https", priority=0)
        self.IM.new_global_after_handler(lambda scheme: order.append(("after", scheme)) or True, scheme="https")
        self.assertFalse(self.intents.dispatch_intent(True, _FakeIntent("https://t.me/x")))
        self.assertFalse(self.intents.dispatch_intent(False, _FakeIntent("https://t.me/x")))
        self.assertEqual(order, [("b0", "https"), ("b10", "t.me"), ("after", "https")])

    def test_flags_and_parse(self):
        hits = []
        self.IM.new_global_before_handler(lambda: hits.append(1), whitelist_flags=[4], blacklist_flags=[1])
        self.intents.dispatch_intent(True, _FakeIntent(None, flags=4))
        self.intents.dispatch_intent(True, _FakeIntent(None, flags=5))
        self.intents.dispatch_intent(True, _FakeIntent(None, flags=0))
        self.assertEqual(hits, [1])
        self.assertEqual(self.IM.parse("tg://chat?id=123"), {"scheme": "tg", "host": "chat", "query": "id=123"})


class ReposTest(unittest.TestCase):
    def setUp(self):
        import yili_repos
        self.repos = yili_repos
        self.dir = tempfile.mkdtemp()
        yili_repos.init(self.dir)
        with open(os.path.join(EXAMPLES, "hello_command.plugin"), "rb") as f:
            self.plugin = f.read()
        import hashlib
        meta = engine.parse_metadata(self.plugin.decode())
        self.index = {"schema": 1, "name": "Test repo", "plugins": [{
            "id": meta["id"], "name": "Hello", "version": meta["version"], "file": "plugins/hello.plugin",
            "sha256": hashlib.sha256(self.plugin).hexdigest(), "size": len(self.plugin)}]}
        self.requests = []

        def fake_get(url, headers=None, limit=0):
            self.requests.append((url, dict(headers or {})))
            resp = types.SimpleNamespace(headers={"ETag": '"v1"'})
            if url.endswith("extera-plugins.json"):
                if (headers or {}).get("If-None-Match") == '"v1"':
                    return resp, None
                return resp, json.dumps(self.index).encode()
            if url.endswith("plugins/hello.plugin"):
                return resp, self.plugin
            raise self.repos.RepoError("404")
        self._orig = yili_repos._http_get
        yili_repos._http_get = fake_get

    def tearDown(self):
        self.repos._http_get = self._orig
        shutil.rmtree(self.dir)

    def test_urls_and_versions(self):
        r = self.repos
        self.assertEqual(r.index_url("https://github.com/you/my-plugins"),
                         "https://raw.githubusercontent.com/you/my-plugins/HEAD/extera-plugins.json")
        self.assertEqual(r.index_url("https://github.com/you/p/tree/v2/sub/dir"),
                         "https://raw.githubusercontent.com/you/p/v2/sub/dir/extera-plugins.json")
        self.assertEqual(r.index_url("https://example.org/x/extera-plugins.json"), "https://example.org/x/extera-plugins.json")
        with self.assertRaises(r.RepoError):
            r.index_url("http://github.com/you/p")
        self.assertEqual(r.parse_share_link(r.share_link("https://github.com/a/b")), "https://github.com/a/b")
        self.assertTrue(r.is_newer("1.10.0", "1.9.2"))
        self.assertFalse(r.is_newer("1.2.0-beta", "1.2"))
        self.assertTrue(r.is_newer("2", "1.9.9"))

    def test_add_catalog_download_and_conditional_refresh(self):
        r = self.repos
        r.add_repo("https://github.com/you/repo")
        cat = r.catalog({})
        self.assertEqual(cat[0]["name"], "Test repo")
        self.assertIsNone(cat[0]["plugins"][0]["installed"])
        self.assertTrue(r.catalog({"hello_command": "0.1"})[0]["plugins"][0]["update"])
        path = r.download("https://github.com/you/repo", "hello_command")
        with open(path, "rb") as f:
            self.assertEqual(f.read(), self.plugin)
        os.remove(path)
        self.assertTrue(self.requests[-1][0].endswith("/HEAD/plugins/hello.plugin"))
        r.refresh(force=True)
        self.assertEqual(self.requests[-1][1].get("If-None-Match"), '"v1"')
        self.assertEqual(r.catalog({})[0]["name"], "Test repo")  # 304 keeps the index
        # Persisted and reloaded.
        r.init(self.dir)
        self.assertEqual(len(r.catalog({})), 1)

    def test_rejects_tampered_file(self):
        r = self.repos
        r.add_repo("https://github.com/you/repo")
        self.plugin = self.plugin + b"\n# changed"
        with self.assertRaises(r.RepoError) as ctx:
            r.download("https://github.com/you/repo", "hello_command")
        self.assertIn("mismatch", str(ctx.exception))

    def test_rejects_newer_schema(self):
        self.index["schema"] = 2
        with self.assertRaises(self.repos.RepoError) as ctx:
            self.repos.add_repo("https://github.com/you/repo")
        self.assertIn("newer version", str(ctx.exception))
