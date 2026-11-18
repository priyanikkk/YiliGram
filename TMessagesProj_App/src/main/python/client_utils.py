"""Telegram client helpers for plugins (exteraGram-compatible ``client_utils``)."""
from typing import Any, Callable, Optional

from java import dynamic_proxy, jclass

from android_utils import R

STAGE_QUEUE = "stageQueue"
GLOBAL_QUEUE = "globalQueue"
CACHE_CLEAR_QUEUE = "cacheClearQueue"
SEARCH_QUEUE = "searchQueue"
PHONE_BOOK_QUEUE = "phoneBookQueue"
THEME_QUEUE = "themeQueue"
EXTERNAL_NETWORK_QUEUE = "externalNetworkQueue"
PLUGINS_QUEUE = "pluginsQueue"

_Api = jclass("com.yiligram.plugins.PluginApi")
_RequestDelegate = jclass("org.telegram.tgnet.RequestDelegate")
_AccountInstance = jclass("org.telegram.messenger.AccountInstance")
_ConnectionsManager = jclass("org.telegram.tgnet.ConnectionsManager")
_MediaController = jclass("org.telegram.messenger.MediaController")

_hook_account = None  # set by the engine while a hook runs (thread-local in spirit)


def get_queue_by_name(name: str):
    return _Api.queue(name)


def run_on_queue(fn: Callable[[], Any], queue_name: str = PLUGINS_QUEUE, delay_ms: int = 0):
    q = _Api.queue(queue_name) or _Api.queue(PLUGINS_QUEUE)
    q.postRunnable(R(fn), int(delay_ms))


def get_selected_account() -> int:
    return int(_Api.selectedAccount())


def get_hook_account() -> Optional[int]:
    return _hook_account


def _acc(account: Optional[int]) -> int:
    return get_selected_account() if account is None else int(account)


class _Callback(dynamic_proxy(_RequestDelegate)):
    def __init__(self, fn):
        super().__init__()
        self._fn = fn

    def run(self, response, error):
        if self._fn is None:
            return
        try:
            self._fn(response, error)
        except Exception:
            import log_utils
            log_utils.get_logger().exception("send_request callback failed")


def send_request(request, fn: Optional[Callable] = None, account: Optional[int] = None) -> int:
    return int(_ConnectionsManager.getInstance(_acc(account)).sendRequest(request, _Callback(fn)))


# ---------------------------------------------------------------- controllers

def get_account_instance(account=None): return _AccountInstance.getInstance(_acc(account))
def get_messages_controller(account=None): return get_account_instance(account).getMessagesController()
def get_contacts_controller(account=None): return get_account_instance(account).getContactsController()
def get_media_data_controller(account=None): return get_account_instance(account).getMediaDataController()
def get_connections_manager(account=None): return get_account_instance(account).getConnectionsManager()
def get_location_controller(account=None): return get_account_instance(account).getLocationController()
def get_notifications_controller(account=None): return get_account_instance(account).getNotificationsController()
def get_messages_storage(account=None): return get_account_instance(account).getMessagesStorage()
def get_send_messages_helper(account=None): return get_account_instance(account).getSendMessagesHelper()
def get_file_loader(account=None): return get_account_instance(account).getFileLoader()
def get_secret_chat_helper(account=None): return get_account_instance(account).getSecretChatHelper()
def get_download_controller(account=None): return get_account_instance(account).getDownloadController()
def get_notifications_settings(account=None): return get_account_instance(account).getNotificationsSettings()
def get_notification_center(account=None): return get_account_instance(account).getNotificationCenter()
def get_user_config(account=None): return get_account_instance(account).getUserConfig()
def get_media_controller(): return _MediaController.getInstance()
def get_last_fragment(): return _Api.lastFragment()


class AccountClient:
    """Same helpers bound to one account (``self.client(account)`` in plugins)."""

    def __init__(self, account: int):
        self.account = account

    def __getattr__(self, name):
        fn = globals().get(name)
        if not callable(fn):
            raise AttributeError(name)
        return lambda *a, **kw: fn(*a, **{**kw, "account": kw.get("account", self.account)})


def get_client(account: Optional[int] = None) -> AccountClient:
    return AccountClient(_acc(account if account is not None else _hook_account))


# ---------------------------------------------------------------- messages

def _entities(text: str, parse_mode: Optional[str], is_caption=False):
    if not parse_mode:
        return text, None
    from extera_utils.text_formatting import parse_text
    parsed = parse_text(text, parse_mode, is_caption)
    body = parsed["caption" if is_caption else "message"]
    ArrayList = jclass("java.util.ArrayList")
    lst = ArrayList()
    for e in parsed["entities"]:
        lst.add(e)
    return body, lst


def send_text(peer: int, text: str, replyToMsg=None, parse_mode: Optional[str] = None, account=None):
    body, entities = _entities(text, parse_mode)
    _Api.sendText(_acc(account), int(peer), body, entities, replyToMsg, True, 0)


def send_photo(peer: int, path: str, caption: Optional[str] = None, high_quality: bool = False,
               parse_mode: Optional[str] = None, account=None):
    body, entities = _entities(caption, parse_mode, True) if caption else (None, None)
    _Api.sendPhoto(_acc(account), int(peer), path, body, entities, None)


def send_document(peer: int, path: str, caption: Optional[str] = None, account=None):
    _Api.sendDocument(_acc(account), int(peer), path, caption, None)


send_video = send_document
send_audio = send_document


def send_message(params: dict, parse_mode: Optional[str] = None, account=None):
    """Low-level: keys of SendMessagesHelper.SendMessageParams."""
    if "message" in params and "photo" not in params and "document" not in params:
        return send_text(params["peer"], params["message"], params.get("replyToMsg"), parse_mode, account)
    if "path" in params:
        return send_document(params["peer"], params["path"], params.get("caption"), account)
    raise ValueError("send_message: unsupported params " + ", ".join(params))


def edit_message(message_obj, text: Optional[str] = None, parse_mode: Optional[str] = None,
                 file_path: Optional[str] = None, with_spoiler: bool = False, account=None):
    if parse_mode and parse_mode.lower() not in ("html", "markdown"):
        raise ValueError("parse_mode must be 'html' or 'markdown'")
    TLRPC = "org.telegram.tgnet.TLRPC$"
    req = jclass(TLRPC + "TL_messages_editMessage")()
    req.peer = get_messages_controller(account).getInputPeer(message_obj.getDialogId())
    req.id = message_obj.getId()
    if text is not None:
        body, entities = _entities(text, parse_mode)
        req.message = body
        req.flags |= 2048
        if entities is not None and entities.size() > 0:
            req.entities = entities
            req.flags |= 8
    send_request(req, None, account)
