# On-device plugin engine self-tests

Plugins that exercise parts of the engine that need a real Android runtime (ART, Chaquopy).
They only log results (tag `plugin.<id>`, lines starting with `SELFTEST` / `PROXY`) and send
nothing. Use a test account or emulator.

| Plugin | Checks |
|---|---|
| `hook_selftest.plugin` | Java method hooks: `MethodReplacement` with Python int → `int` result, `invoke_original`, unhooking, a throwing hook is isolated, constructor hooks with `thisObject` |
| `proxy_selftest.plugin` | Class proxies: `joverload` + `super()`, overriding `size()`, `jmethod`, `jfield` with Java getter/setter, `jpreconstructor` / `jconstructor`, `from_java`, implementing `Runnable`, `jrelease` |

Run one with developer mode on (Settings → Plugins) and the dev server:

```bash
adb forward tcp:42690 tcp:42690
python3 tools/yili-dev/yili_dev.py push tools/device-tests/proxy_selftest.plugin
adb logcat -s plugin.proxy_selftest
```
