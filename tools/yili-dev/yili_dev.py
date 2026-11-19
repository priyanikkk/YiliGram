#!/usr/bin/env python3
"""YiliGram plugin dev client: push a plugin to the phone and hot-reload it on every save.

    adb forward tcp:42690 tcp:42690          # once per connection
    python3 tools/yili-dev/yili_dev.py ping
    python3 tools/yili-dev/yili_dev.py push my_plugin.plugin
    python3 tools/yili-dev/yili_dev.py watch my_plugin.plugin
    python3 tools/yili-dev/yili_dev.py list
    python3 tools/yili-dev/yili_dev.py debug          # VS Code: attach to 127.0.0.1:5678

Talks the exteraGram dev-server protocol (JSON objects over TCP 127.0.0.1:42690), so it also
works with exteraGram. Developer mode must be on in Settings → Plugins.
"""
import argparse
import itertools
import json
import os
import socket
import subprocess
import sys
import time

_ids = itertools.count(1)


class Client:
    def __init__(self, host="127.0.0.1", port=42690, timeout=15):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.buf = ""
        self.decoder = json.JSONDecoder()

    def call(self, command, **fields):
        rid = f"req-{next(_ids)}"
        self.sock.sendall(json.dumps({"@": command, "#": rid, **fields}).encode())
        while True:
            self.buf = self.buf.lstrip()
            try:
                obj, end = self.decoder.raw_decode(self.buf)
                self.buf = self.buf[end:]
                if obj.get("#") == rid:
                    return obj
                continue
            except json.JSONDecodeError:
                pass
            data = self.sock.recv(65536)
            if not data:
                raise ConnectionError("connection closed by the phone")
            self.buf += data.decode()


def ensure_forward(port):
    try:
        subprocess.run(["adb", "forward", f"tcp:{port}", f"tcp:{port}"], check=False,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except FileNotFoundError:
        pass


def push(client, path):
    with open(path, encoding="utf-8") as f:
        res = client.call("write_plugin", content=f.read())
    status = "✔" if res.get("success") else "✘"
    print(f"{time.strftime('%H:%M:%S')} {status} {res.get('plugin_id', path)} {res.get('error') or ''}".rstrip())
    return res


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("command", choices=["ping", "list", "push", "watch", "enable", "disable", "reload",
                                        "remove", "debug"])
    ap.add_argument("target", nargs="?", help="plugin file (push/watch) or plugin id")
    ap.add_argument("--port", type=int, default=42690)
    ap.add_argument("--no-adb", action="store_true", help="don't run adb forward")
    ap.add_argument("--debug-port", type=int, default=5678, help="debugpy port (debug command)")
    args = ap.parse_args()
    if not args.no_adb:
        ensure_forward(args.port)
    c = Client(port=args.port)
    if args.command == "ping":
        print(c.call("ping"))
    elif args.command == "list":
        for p in c.call("get_plugins").get("plugins", []):
            state = "on " if p["enabled"] else "off"
            print(f"[{state}] {p['id']:<24} {p['version']:<8} {p.get('error') or p['name']}")
    elif args.command == "debug":
        if not args.no_adb:
            ensure_forward(args.debug_port)
        res = c.call("start_debugger", host="127.0.0.1", port=args.debug_port, platform="vscode")
        if not res.get("success"):
            sys.exit(f"✘ {res.get('error')}")
        print(f"debugpy is listening; attach VS Code to 127.0.0.1:{res['port']} "
              f"(remoteRoot: /data/user/0/<package>/files/plugins)")
    elif args.command == "push":
        sys.exit(0 if push(c, args.target).get("success") else 1)
    elif args.command == "watch":
        last = None
        print(f"watching {args.target} (Ctrl+C to stop)")
        while True:
            try:
                mtime = os.path.getmtime(args.target)
            except FileNotFoundError:
                mtime = None
            if mtime and mtime != last:
                last = mtime
                try:
                    push(c, args.target)
                except (ConnectionError, OSError) as e:
                    print("reconnecting:", e)
                    time.sleep(1)
                    c = Client(port=args.port)
            time.sleep(0.5)
    else:
        cmd = {"enable": "enable_plugin", "disable": "disable_plugin", "reload": "reload_plugin",
               "remove": "remove_plugin"}[args.command]
        print(c.call(cmd, plugin_id=args.target))


if __name__ == "__main__":
    main()
