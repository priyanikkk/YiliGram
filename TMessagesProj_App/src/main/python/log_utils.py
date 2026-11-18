"""Plugin logging (``self.logger`` in plugins, ``from log_utils import get_logger``)."""
import enum
import inspect
import os
import time
import traceback
from typing import Any, Callable, List, Optional

try:
    from java import jclass
    _Log = jclass("android.util.Log")
except Exception:  # running outside Android (tests)
    _Log = None


class LogLevel(enum.IntEnum):
    VERBOSE = 2
    DEBUG = 3
    INFO = 4
    WARNING = 5
    ERROR = 6
    OFF = 100


class LogRecord:
    def __init__(self, level, tag, message, location, tb):
        self.level = level
        self.tag = tag
        self.message = message
        self.location = location
        self.traceback = tb
        self.created = time.time()
        self.text = message if tb is None else message + "\n" + tb


# Ring buffer read by the plugin manager's log viewer.
_buffer: List[LogRecord] = []
_BUFFER_MAX = 2000
developer_mode = False


def recent(limit: int = 500, tag_prefix: Optional[str] = None) -> List[LogRecord]:
    items = [r for r in _buffer if tag_prefix is None or r.tag.startswith(tag_prefix)]
    return items[-limit:]


def _coerce_level(level):
    if level is None or isinstance(level, LogLevel):
        return level
    if isinstance(level, str):
        return LogLevel[level.upper()]
    return LogLevel(int(level))


def dump(obj: Any, depth: int = 2, max_items: int = 20) -> str:
    seen = set()

    def fmt(o, d):
        if o is None or isinstance(o, (bool, int, float, str)):
            return repr(o)
        if id(o) in seen:
            return "<cycle>"
        if d <= 0:
            return "{…}"
        seen.add(id(o))
        try:
            if isinstance(o, dict):
                items = list(o.items())[:max_items]
                return "{" + ", ".join(f"{k!r}: {fmt(v, d - 1)}" for k, v in items) + "}"
            if isinstance(o, (list, tuple, set)):
                return "[" + ", ".join(fmt(v, d - 1) for v in list(o)[:max_items]) + "]"
            cls = type(o).__name__
            jname = getattr(getattr(o, "getClass", None), "__call__", None)
            if jname is not None:
                name = o.getClass().getName()
                if name.startswith(("java.", "android.")):
                    return str(o)
                fields = []
                for f in o.getClass().getFields()[:max_items]:
                    try:
                        fields.append(f"{f.getName()}={fmt(f.get(o), d - 1)}")
                    except Exception:
                        pass
                return f"{name}({', '.join(fields)})"
            attrs = {k: v for k, v in vars(o).items() if not k.startswith("_")} if hasattr(o, "__dict__") else {}
            return f"{cls}({', '.join(f'{k}={fmt(v, d - 1)}' for k, v in list(attrs.items())[:max_items])})"
        finally:
            seen.discard(id(o))

    return fmt(obj, depth)


class Logger:
    def __init__(self, tag: str):
        self.tag = tag
        self.level: Optional[LogLevel] = None
        self._parent: Optional["Logger"] = None
        self._handlers: List[Callable[[LogRecord], None]] = []

    @property
    def effective_level(self) -> LogLevel:
        if self.level is not None:
            return self.level
        if self._parent is not None:
            return self._parent.effective_level
        return LogLevel.DEBUG if developer_mode else LogLevel.INFO

    def __setattr__(self, key, value):
        if key == "level":
            value = _coerce_level(value)
        object.__setattr__(self, key, value)

    def is_enabled_for(self, level) -> bool:
        return _coerce_level(level) >= self.effective_level

    def child(self, name: str) -> "Logger":
        c = Logger(f"{self.tag}.{name}")
        c._parent = self
        return c

    def add_handler(self, handler):
        self._handlers.append(handler)

    def remove_handler(self, handler) -> bool:
        if handler in self._handlers:
            self._handlers.remove(handler)
            return True
        return False

    def _emit(self, level, msg, args, tb=None, stacklevel=1):
        if not self.is_enabled_for(level):
            return
        message = str(msg) % args if args else str(msg)
        location = None
        try:
            frame = inspect.stack()[stacklevel + 2]
            location = f"{os.path.basename(frame.filename)}:{frame.function}:{frame.lineno}"
        except Exception:
            pass
        record = LogRecord(level, self.tag, message, location, tb)
        _buffer.append(record)
        if len(_buffer) > _BUFFER_MAX:
            del _buffer[: len(_buffer) - _BUFFER_MAX]
        text = (f"[{location}] " if location else "") + record.text
        if _Log is not None:
            for i in range(0, len(text), 4000):  # logcat line limit
                _Log.println(int(level), self.tag, text[i:i + 4000])
        for h in list(self._handlers) + (list(self._parent._handlers) if self._parent else []):
            try:
                h(record)
            except Exception:
                pass

    def verbose(self, msg, *args, stacklevel=1):
        self._emit(LogLevel.VERBOSE, msg, args, stacklevel=stacklevel)

    def debug(self, msg, *args, stacklevel=1):
        self._emit(LogLevel.DEBUG, msg, args, stacklevel=stacklevel)

    def info(self, msg, *args, stacklevel=1):
        self._emit(LogLevel.INFO, msg, args, stacklevel=stacklevel)

    def warning(self, msg, *args, stacklevel=1):
        self._emit(LogLevel.WARNING, msg, args, stacklevel=stacklevel)

    def error(self, msg, *args, exc_info=None, stacklevel=1):
        tb = traceback.format_exc() if exc_info else None
        self._emit(LogLevel.ERROR, msg, args, tb, stacklevel=stacklevel)

    def exception(self, msg, *args, stacklevel=1):
        self._emit(LogLevel.ERROR, msg, args, traceback.format_exc(), stacklevel=stacklevel)

    def dump(self, obj, label=None, *, level=LogLevel.DEBUG, depth=2, max_items=20, stacklevel=1):
        text = dump(obj, depth, max_items)
        self._emit(level, f"{label}: {text}" if label else text, (), stacklevel=stacklevel)

    def timed(self, label, level=LogLevel.DEBUG):
        logger = self

        class _Timer:
            def __enter__(self):
                self.t = time.monotonic()
                return self

            def __exit__(self, exc_type, exc, tb):
                ms = (time.monotonic() - self.t) * 1000
                logger._emit(level, f"{label}: {ms:.1f} ms" + (" (failed)" if exc_type else ""), ())
                return False

        return _Timer()


_current_logger: Optional[Logger] = None


def get_logger(name: Optional[str] = None) -> Logger:
    """Logger of the plugin whose code is running (optionally a named child)."""
    base = _current_logger or Logger("plugin")
    return base.child(name) if name else base
