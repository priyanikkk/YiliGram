package com.yiligram.ui;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

import org.telegram.messenger.AndroidUtilities;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal Markdown for plugin descriptions: **bold**, *italic* / _italic_, `code`, [text](url). */
public final class YiliMarkdown {
    private static final Pattern TOKEN = Pattern.compile(
            "\\*\\*(.+?)\\*\\*|__(.+?)__|(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])|(?<!\\w)_(?!\\s)(.+?)(?<!\\s)_(?!\\w)|`([^`]+)`|\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");

    private YiliMarkdown() {
    }

    public static CharSequence render(CharSequence source) {
        if (source == null) {
            return null;
        }
        String text = source.toString();
        SpannableStringBuilder out = new SpannableStringBuilder();
        Matcher m = TOKEN.matcher(text);
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            int start = out.length();
            if (m.group(1) != null || m.group(2) != null) {
                out.append(m.group(1) != null ? m.group(1) : m.group(2));
                out.setSpan(new org.telegram.ui.Components.TypefaceSpan(AndroidUtilities.bold()), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(3) != null || m.group(4) != null) {
                out.append(m.group(3) != null ? m.group(3) : m.group(4));
                out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(5) != null) {
                out.append(m.group(5));
                out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                out.append(m.group(6));
                out.setSpan(new URLSpan(m.group(7)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            last = m.end();
        }
        out.append(text, last, text.length());
        return out;
    }
}
