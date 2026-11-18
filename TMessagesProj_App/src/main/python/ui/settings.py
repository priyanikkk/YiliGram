"""Plugin settings items (exteraGram-compatible ``ui.settings``).

Plain data objects; YiliGram's plugin settings screen renders them as M3 list items.
"""
from dataclasses import dataclass
from typing import Any, Callable, List, Optional


@dataclass
class Header:
    text: str
    kind: str = "header"


@dataclass
class Divider:
    text: Optional[str] = None
    kind: str = "divider"


@dataclass
class Switch:
    key: str
    text: str
    default: bool
    subtext: Optional[str] = None
    icon: Optional[str] = None
    on_change: Optional[Callable[[bool], None]] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None
    kind: str = "switch"


@dataclass
class Selector:
    key: str
    text: str
    default: int
    items: List[str]
    icon: Optional[str] = None
    on_change: Optional[Callable[[int], None]] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None
    kind: str = "selector"


@dataclass
class Input:
    key: str
    text: str
    default: Optional[str] = None
    subtext: Optional[str] = None
    icon: Optional[str] = None
    on_change: Optional[Callable[[str], None]] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None
    kind: str = "input"


@dataclass
class Text:
    text: str
    subtext: Optional[str] = None
    icon: Optional[str] = None
    accent: Optional[bool] = None
    red: Optional[bool] = None
    on_click: Optional[Callable[[Any], None]] = None
    on_long_click: Optional[Callable] = None
    create_sub_fragment: Optional[Callable[[], List[Any]]] = None
    link_alias: Optional[str] = None
    kind: str = "text"


@dataclass
class EditText:
    key: str
    hint: str
    default: Optional[str] = None
    multiline: Optional[bool] = None
    max_length: Optional[int] = None
    mask: Optional[str] = None
    on_change: Optional[Callable[[str], None]] = None
    kind: str = "edit_text"


@dataclass
class Custom:
    item: Any = None
    view: Any = None
    factory: Any = None
    factory_args: Any = None
    on_click: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    create_sub_fragment: Optional[Callable[[], List[Any]]] = None
    link_alias: Optional[str] = None
    kind: str = "custom"
