"""Developer server for hot-reloading plugins (exteraGram dev-server protocol).

Listens on 127.0.0.1:42690 while developer mode is on. Clients send consecutive UTF-8 JSON
objects ``{"@": command, "#": request_id, ...}``; every response repeats ``"#"``.
From a computer: ``adb forward tcp:42690 tcp:42690`` and run ``tools/yili-dev/yili_dev.py``.

Security: any app on the phone can reach a localhost port, so the server only runs in
developer mode, which the UI warns about.
"""
import json
import os
import socket
import tempfile
import threading
import traceback

import log_utils
import yili_engine as engine

PORT = 42690
_server = None
_logger = log_utils.Logger("yili.devserver")


def start(port: int = PORT):
    global _server
    if _server is not None:
        return
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("127.0.0.1", port))
    sock.listen(2)
    _server = sock
    threading.Thread(target=_accept_loop, args=(sock,), name="yili-devserver", daemon=True).start()
    _logger.info("dev server listening on 127.0.0.1:%d", port)


def stop():
    global _server
    if _server is not None:
        try:
            _server.close()
        finally:
            _server = None


def _accept_loop(sock):
    while True:
        try:
            conn, _ = sock.accept()
        except OSError:
            return
        threading.Thread(target=_serve, args=(conn,), daemon=True).start()


def _serve(conn):
    decoder = json.JSONDecoder()
    buf = ""
    with conn:
        while True:
            try:
                chunk = conn.recv(65536)
            except OSError:
                return
            if not chunk:
                return
            buf += chunk.decode("utf-8", "replace")
            while True:
                buf = buf.lstrip()
                if not buf:
                    break
                try:
                    msg, end = decoder.raw_decode(buf)
                except json.JSONDecodeError:
                    break  # incomplete object: wait for more data
                buf = buf[end:]
                reply = handle(msg)
                try:
                    conn.sendall(json.dumps(reply).encode("utf-8"))
                except OSError:
                    return


_debugger = None


def _start_debugger(msg) -> dict:
    """VS Code (debugpy) remote debugging. The adapter runs inside the app process because
    Chaquopy has no Python executable to spawn it from; it listens on loopback only."""
    global _debugger
    platform = msg.get("platform", "vscode")
    if platform != "vscode":
        return {"success": False, "error": f"Unsupported debugger platform {platform!r}: only vscode (debugpy) works"}
    host = msg.get("host") or "127.0.0.1"
    if host not in ("127.0.0.1", "localhost"):
        return {"success": False, "error": "The debugger only listens on 127.0.0.1; use adb forward"}
    port = int(msg.get("port") or 5678)
    if _debugger is not None:
        return {"success": True, "host": _debugger[0], "port": _debugger[1], "already_running": True}
    import yili_pip
    yili_pip.install(["debugpy>=1.8.1"], "__debugger__", _logger)
    # pydevd's Cython speedups are not available here.
    os.environ.setdefault("PYDEVD_USE_CYTHON", "NO")
    os.environ.setdefault("PYDEVD_USE_FRAME_EVAL", "NO")
    import debugpy
    debugpy.listen(("127.0.0.1", port), in_process_debug_adapter=True)
    _debugger = ("127.0.0.1", port)
    _logger.info("debugpy listening on 127.0.0.1:%d", port)
    return {"success": True, "host": "127.0.0.1", "port": port}


def handle(msg: dict) -> dict:
    rid = msg.get("#")
    try:
        result = _dispatch(msg.get("@"), msg)
    except Exception as e:
        _logger.exception("dev command %s failed", msg.get("@"))
        result = {"error": f"{type(e).__name__}: {e}", "traceback": traceback.format_exc()}
    result["#"] = rid
    return result


def _dispatch(cmd, msg) -> dict:
    pid = msg.get("plugin_id")
    if cmd == "ping":
        return {"pong": True, "app": "YiliGram", "sdk": engine.SDK_VERSION}
    if cmd == "get_plugins":
        return {"plugins": json.loads(engine.list_plugins())}
    if cmd == "enable_plugin":
        return {"success": engine.set_enabled(pid, True) is None, "error": engine._state["errors"].get(pid)}
    if cmd == "disable_plugin":
        engine.set_enabled(pid, False)
        return {"success": True}
    if cmd == "reload_plugin":
        was = engine._state["enabled"].get(pid)
        engine.unload(pid)
        engine.scan()
        err = engine.load(pid) if was else None
        engine._notify("onPluginsChanged")
        return {"success": err is None, "error": err}
    if cmd == "write_plugin":
        content = msg.get("content")
        if not isinstance(content, str):
            return {"success": False, "error": "content must be the plugin source as a string"}
        fd, path = tempfile.mkstemp(suffix=".plugin")
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as f:
                f.write(content)
            res = json.loads(engine.install(path))
        finally:
            os.remove(path)
        if res.get("error"):
            return {"success": False, "error": res["error"]}
        if pid and res["id"] != pid:
            return {"success": False, "error": f"plugin_id {pid} does not match __id__ {res['id']}"}
        err = engine.set_enabled(res["id"], True)
        return {"success": err is None, "error": err, "plugin_id": res["id"]}
    if cmd == "remove_plugin":
        engine.delete(pid)
        return {"success": True}
    if cmd == "start_debugger":
        return _start_debugger(msg)
    if cmd == "stop_debugger":
        if _debugger is None:
            return {"success": True}
        return {"success": False, "error": "debugpy cannot stop listening; restart the app to stop the debugger"}
    return {"success": False, "error": f"Unknown command: {cmd}"}
