"""Android helpers for plugins (exteraGram-compatible)."""
from java import dynamic_proxy, jclass

_Runnable = jclass("java.lang.Runnable")
_OnClick = jclass("android.view.View$OnClickListener")
_OnLongClick = jclass("android.view.View$OnLongClickListener")
_Api = jclass("com.yiligram.plugins.PluginApi")


class R(dynamic_proxy(_Runnable)):
    """java.lang.Runnable backed by a Python callable."""

    def __init__(self, fn):
        super().__init__()
        self._fn = fn

    def run(self):
        try:
            self._fn()
        except Exception:
            import log_utils
            log_utils.get_logger().exception("Runnable failed")


class OnClickListener(dynamic_proxy(_OnClick)):
    def __init__(self, fn):
        super().__init__()
        self._fn = fn

    def onClick(self, view):
        self._fn(view)


class OnLongClickListener(dynamic_proxy(_OnLongClick)):
    def __init__(self, fn):
        super().__init__()
        self._fn = fn

    def onLongClick(self, view):
        return bool(self._fn(view))


def run_on_ui_thread(func, delay: int = 0):
    _Api.runOnUiThread(R(func), int(delay))


def copy_to_clipboard(text: str):
    _Api.copyToClipboard(str(text))


def log(data):
    """Legacy: prefer self.logger."""
    import log_utils
    logger = log_utils.get_logger()
    if isinstance(data, (str, int, float, bool)) or data is None:
        logger.debug(str(data))
    else:
        logger.dump(data)
