"""YiliGram extras for plugins: Material 3 Expressive colors and components.

Optional and additive — exteraGram plugins never import it, so compatibility is unaffected.

    import yili_ui
    color = yili_ui.color("primaryContainer")
    yili_ui.show_loading_bulletin("Working…")
"""
from java import jclass

_Theme = jclass("com.yiligram.design.YiliTheme")
_ColorRole = jclass("com.yiligram.design.color.ColorRole")


def color(role: str) -> int:
    """ARGB of an M3 color role of the current scheme (e.g. "primary", "surfaceContainerHigh")."""
    r = _ColorRole.fromKey(role)
    if r is None:
        raise ValueError(f"Unknown M3 color role: {role}")
    return int(_Theme.colors().get(r))


def is_dark() -> bool:
    return bool(_Theme.colors().spec.dark)


def button(context, text: str, style: str = "filled"):
    """M3 Expressive button view (styles: filled, tonal, outlined, elevated, text)."""
    B = jclass("com.yiligram.design.widget.YiliButton")
    b = B(context).setStyle(getattr(jclass("com.yiligram.design.widget.YiliButton$Style"), style.upper()))
    b.setText(text)
    return b


def loading_indicator(context, contained: bool = False):
    """M3 Expressive morphing loading indicator view."""
    v = jclass("com.yiligram.design.widget.YiliLoadingIndicator")(context)
    v.setContained(contained)
    return v
