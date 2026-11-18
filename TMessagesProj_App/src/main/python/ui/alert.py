"""Alert dialogs for plugins (exteraGram-compatible ``ui.alert``)."""
from typing import Callable, Dict, List, Optional

from java import dynamic_proxy, jclass

_AlertDialog = jclass("org.telegram.ui.ActionBar.AlertDialog")
_Builder = jclass("org.telegram.ui.ActionBar.AlertDialog$Builder")
_OnButton = jclass("org.telegram.ui.ActionBar.AlertDialog$OnButtonClickListener")
_DialogInterface = jclass("android.content.DialogInterface")
_OnDismiss = jclass("android.content.DialogInterface$OnDismissListener")
_OnCancel = jclass("android.content.DialogInterface$OnCancelListener")
_Theme = jclass("org.telegram.ui.ActionBar.Theme")

ALERT_TYPE_MESSAGE = 0
ALERT_TYPE_LOADING = 2
ALERT_TYPE_SPINNER = 3


class _ButtonListener(dynamic_proxy(_OnButton)):
    def __init__(self, builder, fn):
        super().__init__()
        self._builder, self._fn = builder, fn

    def onClick(self, dialog, which):
        if self._fn:
            self._fn(self._builder, which)


class _DismissListener(dynamic_proxy(_OnDismiss)):
    def __init__(self, builder, fn):
        super().__init__()
        self._builder, self._fn = builder, fn

    def onDismiss(self, dialog):
        self._fn(self._builder)


class _CancelListener(dynamic_proxy(_OnCancel)):
    def __init__(self, builder, fn):
        super().__init__()
        self._builder, self._fn = builder, fn

    def onCancel(self, dialog):
        self._fn(self._builder)


class AlertDialogBuilder:
    BUTTON_POSITIVE = _DialogInterface.BUTTON_POSITIVE
    BUTTON_NEGATIVE = _DialogInterface.BUTTON_NEGATIVE
    BUTTON_NEUTRAL = _DialogInterface.BUTTON_NEUTRAL

    def __init__(self, context, progress_style: int = ALERT_TYPE_MESSAGE, resources_provider=None):
        self._builder = _Builder(context, progress_style, resources_provider)
        self._dialog = None

    def set_title(self, title: str):
        self._builder.setTitle(title); return self

    def set_message(self, message: str):
        self._builder.setMessage(message); return self

    def set_message_text_view_clickable(self, clickable: bool):
        self._builder.setMessageTextViewClickable(clickable); return self

    def set_view(self, view, height: int = -2):
        self._builder.setView(view, height); return self

    def set_items(self, items: List[str], listener=None, icons: Optional[List[int]] = None):
        self._builder.setItems(items, icons, _ButtonListener(self, listener)); return self

    def set_positive_button(self, text: str, listener=None):
        self._builder.setPositiveButton(text, _ButtonListener(self, listener)); return self

    def set_negative_button(self, text: str, listener=None):
        self._builder.setNegativeButton(text, _ButtonListener(self, listener)); return self

    def set_neutral_button(self, text: str, listener=None):
        self._builder.setNeutralButton(text, _ButtonListener(self, listener)); return self

    def make_button_red(self, button_type: int):
        self._builder.makeRed(button_type); return self

    def set_on_back_button_listener(self, listener=None):
        self._builder.setOnBackButtonListener(_ButtonListener(self, listener)); return self

    def set_on_dismiss_listener(self, listener=None):
        if listener:
            self._builder.setOnDismissListener(_DismissListener(self, listener))
        return self

    def set_on_cancel_listener(self, listener=None):
        if listener:
            self._builder.setOnCancelListener(_CancelListener(self, listener))
        return self

    def set_top_image(self, res_id: int, background_color: int):
        self._builder.setTopImage(res_id, background_color); return self

    def set_top_drawable(self, drawable, background_color: int):
        self._builder.setTopDrawable(drawable, background_color); return self

    def set_top_animation(self, res_id: int, size: int, auto_repeat: bool, background_color: int,
                          layer_colors: Optional[Dict[str, int]] = None):
        self._builder.setTopAnimation(res_id, size, auto_repeat, background_color); return self

    def set_dim_enabled(self, enabled: bool):
        self._builder.setDimEnabled(enabled); return self

    def set_dialog_button_color_key(self, theme_key: int):
        self._builder.setDialogButtonColorKey(theme_key); return self

    def set_blurred_background(self, blur: bool, blur_behind_if_possible: bool = True):
        self._builder.setBlurredBackground(blur); return self

    def set_cancelable(self, cancelable: bool):
        if self._dialog:
            self._dialog.setCancelable(cancelable)
        return self

    def set_canceled_on_touch_outside(self, cancel: bool):
        if self._dialog:
            self._dialog.setCanceledOnTouchOutside(cancel)
        return self

    def set_progress(self, progress: int):
        if self._dialog:
            self._dialog.setProgress(int(progress))
        return self

    def create(self):
        self._dialog = self._builder.create(); return self

    def show(self):
        self._dialog = self._builder.show(); return self

    def dismiss(self):
        if self._dialog:
            self._dialog.dismiss()

    def get_dialog(self):
        return self._dialog

    def get_button(self, button_type: int):
        return self._dialog.getButton(button_type) if self._dialog else None
