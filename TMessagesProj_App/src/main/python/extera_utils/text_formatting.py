"""HTML / Markdown → Telegram message entities (exteraGram-compatible ``parse_text``)."""
import enum
import html
import re
from html.parser import HTMLParser
from typing import Any, Dict, List, Optional, Tuple

from java import jclass

_T = "org.telegram.tgnet.TLRPC$"


class TLEntityType(enum.Enum):
    CODE = "code"
    PRE = "pre"
    STRIKETHROUGH = "strike"
    TEXT_LINK = "text_link"
    BOLD = "bold"
    ITALIC = "italic"
    UNDERLINE = "underline"
    SPOILER = "spoiler"
    CUSTOM_EMOJI = "custom_emoji"
    BLOCKQUOTE = "blockquote"


class RawEntity:
    def __init__(self, type: TLEntityType, offset: int, length: int, url=None, language=None,
                 document_id=None, collapsed=False):
        self.type, self.offset, self.length = type, offset, length
        self.url, self.language, self.document_id, self.collapsed = url, language, document_id, collapsed

    def to_tl(self):
        cls = {
            TLEntityType.CODE: "TL_messageEntityCode", TLEntityType.PRE: "TL_messageEntityPre",
            TLEntityType.STRIKETHROUGH: "TL_messageEntityStrike", TLEntityType.TEXT_LINK: "TL_messageEntityTextUrl",
            TLEntityType.BOLD: "TL_messageEntityBold", TLEntityType.ITALIC: "TL_messageEntityItalic",
            TLEntityType.UNDERLINE: "TL_messageEntityUnderline", TLEntityType.SPOILER: "TL_messageEntitySpoiler",
            TLEntityType.CUSTOM_EMOJI: "TL_messageEntityCustomEmoji",
            TLEntityType.BLOCKQUOTE: "TL_messageEntityBlockquote",
        }[self.type]
        e = jclass(_T + cls)()
        e.offset, e.length = self.offset, self.length
        if self.url is not None:
            e.url = self.url
        if self.language is not None:
            e.language = self.language
        if self.document_id is not None:
            e.document_id = int(self.document_id)
        if self.type == TLEntityType.BLOCKQUOTE and self.collapsed:
            e.collapsed = True
            e.flags |= 1
        return e


def _utf16_len(s: str) -> int:
    return len(s.encode("utf-16-le")) // 2


class _Html(HTMLParser):
    TAGS = {"b": TLEntityType.BOLD, "strong": TLEntityType.BOLD, "i": TLEntityType.ITALIC,
            "em": TLEntityType.ITALIC, "u": TLEntityType.UNDERLINE, "s": TLEntityType.STRIKETHROUGH,
            "del": TLEntityType.STRIKETHROUGH, "strike": TLEntityType.STRIKETHROUGH, "code": TLEntityType.CODE,
            "pre": TLEntityType.PRE, "spoiler": TLEntityType.SPOILER, "tg-spoiler": TLEntityType.SPOILER,
            "a": TLEntityType.TEXT_LINK, "blockquote": TLEntityType.BLOCKQUOTE, "emoji": TLEntityType.CUSTOM_EMOJI}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.text = ""
        self.stack: List[Tuple[str, int, Dict[str, Any]]] = []
        self.entities: List[RawEntity] = []

    def handle_starttag(self, tag, attrs):
        if tag in self.TAGS:
            self.stack.append((tag, _utf16_len(self.text), dict(attrs)))
        elif tag == "br":
            self.text += "\n"

    def handle_endtag(self, tag):
        for i in range(len(self.stack) - 1, -1, -1):
            if self.stack[i][0] == tag:
                _, start, attrs = self.stack.pop(i)
                length = _utf16_len(self.text) - start
                if length > 0:
                    t = self.TAGS[tag]
                    if t == TLEntityType.CODE and self.entities and self.entities[-1].type == TLEntityType.PRE \
                            and self.entities[-1].offset == start:
                        continue
                    self.entities.append(RawEntity(
                        t, start, length, url=attrs.get("href"), language=attrs.get("language"),
                        document_id=attrs.get("id"),
                        collapsed="expandable" in attrs or "collapsed" in attrs))
                break

    def handle_data(self, data):
        self.text += data


_MD = [
    (re.compile(r"```(\w+)?\n?([\s\S]*?)```"), TLEntityType.PRE),
    (re.compile(r"`([^`]+)`"), TLEntityType.CODE),
    (re.compile(r"!\[([^\]]+)\]\(tg://emoji\?id=(\d+)\)"), TLEntityType.CUSTOM_EMOJI),
    (re.compile(r"\[([^\]]+)\]\(([^)]+)\)"), TLEntityType.TEXT_LINK),
    (re.compile(r"\|\|([\s\S]+?)\|\|"), TLEntityType.SPOILER),
    (re.compile(r"__([\s\S]+?)__"), TLEntityType.UNDERLINE),
    (re.compile(r"\*\*([\s\S]+?)\*\*"), TLEntityType.BOLD),
    (re.compile(r"\*([^*\n]+)\*"), TLEntityType.BOLD),
    (re.compile(r"_([^_\n]+)_"), TLEntityType.ITALIC),
    (re.compile(r"~([^~\n]+)~"), TLEntityType.STRIKETHROUGH),
]


def _markdown_to_html(text: str) -> str:
    """Converts the plugin Markdown dialect to HTML, then reuses the HTML parser."""
    out = html.escape(text, quote=False)
    lines, quote, collapsed = [], [], False
    for line in out.split("\n"):
        m = re.match(r"^(\*\*)?&gt; ?(.*)$", line)
        if m:
            collapsed = collapsed or bool(m.group(1))
            quote.append(m.group(2))
            continue
        if quote:
            attr = " expandable" if collapsed else ""
            lines.append(f"<blockquote{attr}>" + "\n".join(quote) + "</blockquote>")
            quote, collapsed = [], False
        lines.append(line)
    if quote:
        lines.append(f"<blockquote{' expandable' if collapsed else ''}>" + "\n".join(quote) + "</blockquote>")
    out = "\n".join(lines)
    for regex, t in _MD:
        if t == TLEntityType.PRE:
            out = regex.sub(lambda m: f'<pre language="{m.group(1) or ""}">{m.group(2)}</pre>', out)
        elif t == TLEntityType.CODE:
            out = regex.sub(r"<code>\1</code>", out)
        elif t == TLEntityType.CUSTOM_EMOJI:
            out = regex.sub(r'<emoji id="\2">\1</emoji>', out)
        elif t == TLEntityType.TEXT_LINK:
            out = regex.sub(r'<a href="\2">\1</a>', out)
        else:
            tag = {TLEntityType.SPOILER: "spoiler", TLEntityType.UNDERLINE: "u", TLEntityType.BOLD: "b",
                   TLEntityType.ITALIC: "i", TLEntityType.STRIKETHROUGH: "s"}[t]
            out = regex.sub(rf"<{tag}>\1</{tag}>", out)
    return out


def parse_text(text: str, parse_mode: Optional[str] = "HTML", is_caption: bool = False) -> Dict[str, Any]:
    mode = (parse_mode or "HTML").lower()
    if mode not in ("html", "markdown"):
        raise ValueError("parse_mode must be 'HTML' or 'Markdown'")
    source = _markdown_to_html(text) if mode == "markdown" else text
    p = _Html()
    p.feed(source)
    p.close()
    entities = [e.to_tl() for e in sorted(p.entities, key=lambda e: e.offset)]
    return {("caption" if is_caption else "message"): p.text, "entities": entities}
