"""Bulletins (snackbars) for plugins (exteraGram-compatible ``ui.bulletin``)."""
from typing import Callable, Optional

from java import jclass

from android_utils import R

_Api = jclass("com.yiligram.plugins.PluginApi")
_Raw = jclass("org.telegram.messenger.R$raw")


class BulletinHelper:
    DURATION_SHORT = 1500
    DURATION_LONG = 2750
    DURATION_PROLONG = 5000

    @staticmethod
    def _show(icon, title, subtitle=None, button=None, on_click=None, fragment=None, duration=DURATION_LONG):
        _Api.showBulletin(fragment, int(icon), str(title), subtitle, button,
                          R(on_click) if on_click else None, int(duration))

    @staticmethod
    def show_info(message: str, fragment=None):
        BulletinHelper._show(_Raw.info, message, fragment=fragment)

    @staticmethod
    def show_error(message: str, fragment=None):
        BulletinHelper._show(_Raw.error, message, fragment=fragment)

    @staticmethod
    def show_success(message: str, fragment=None):
        BulletinHelper._show(_Raw.contact_check, message, fragment=fragment)

    @staticmethod
    def show_simple(text: str, icon_res_id: int, fragment=None):
        BulletinHelper._show(icon_res_id, text, fragment=fragment)

    @staticmethod
    def show_two_line(title: str, subtitle: str, icon_res_id: int, fragment=None):
        BulletinHelper._show(icon_res_id, title, subtitle=subtitle, fragment=fragment)

    @staticmethod
    def show_with_button(text: str, icon_res_id: int, button_text: str, on_click: Optional[Callable[[], None]],
                         fragment=None, duration: int = DURATION_PROLONG):
        BulletinHelper._show(icon_res_id, text, button=button_text, on_click=on_click, fragment=fragment,
                             duration=duration)

    @staticmethod
    def show_undo(text: str, on_undo: Callable[[], None], on_action: Optional[Callable[[], None]] = None,
                  subtitle: Optional[str] = None, fragment=None):
        undo = jclass("org.telegram.messenger.LocaleController").getString(
            jclass("org.telegram.messenger.R$string").Undo)
        BulletinHelper._show(_Raw.info, text, subtitle=subtitle, button=undo, on_click=on_undo,
                             fragment=fragment, duration=BulletinHelper.DURATION_PROLONG)

    @staticmethod
    def show_copied_to_clipboard(message: Optional[str] = None, fragment=None):
        text = message or jclass("org.telegram.messenger.LocaleController").getString(
            jclass("org.telegram.messenger.R$string").TextCopied)
        BulletinHelper._show(_Raw.copy, text, fragment=fragment)

    @staticmethod
    def show_link_copied(is_private_link_info: bool = False, fragment=None):
        LC = jclass("org.telegram.messenger.LocaleController")
        S = jclass("org.telegram.messenger.R$string")
        BulletinHelper._show(_Raw.voip_invite, LC.getString(S.LinkCopied), fragment=fragment)

    @staticmethod
    def show_file_saved_to_gallery(is_video: bool = False, amount: int = 1, fragment=None):
        LC = jclass("org.telegram.messenger.LocaleController")
        S = jclass("org.telegram.messenger.R$string")
        BulletinHelper._show(_Raw.ic_download, LC.getString(S.VideoSavedHint if is_video else S.PhotoSavedHint),
                             fragment=fragment)

    @staticmethod
    def show_file_saved_to_downloads(file_type_enum_name: str = "UNKNOWN", amount: int = 1, fragment=None):
        BulletinHelper._show(_Raw.ic_download, "Saved to Downloads", fragment=fragment)
