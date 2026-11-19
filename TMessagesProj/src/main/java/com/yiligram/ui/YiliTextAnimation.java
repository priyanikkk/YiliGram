package com.yiligram.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yiligram.config.YiliConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.lang.ref.WeakReference;

/**
 * Built-in typing animation for the message field: newly typed characters fade in, rise a few dp
 * and grow to full size. Each inserted run gets a short-lived {@link ReplacementSpan} that measures
 * exactly like plain text (so layout never moves) and is removed when its animation ends. Only the
 * characters actually added are animated, also while the keyboard rewrites its composing word.
 * Runs containing other spans (emoji, mentions, formatting) or surrogate pairs are left alone.
 */
public final class YiliTextAnimation implements TextWatcher {

    private static final long DURATION = 260;
    private static final int MAX_RUN = 12;

    private final WeakReference<EditText> view;
    private String replaced = "";

    private YiliTextAnimation(EditText view) {
        this.view = new WeakReference<>(view);
    }

    public static void attach(EditText editText) {
        editText.addTextChangedListener(new YiliTextAnimation(editText));
    }

    private static boolean enabled() {
        return YiliConfig.get(YiliConfig.TEXT_ANIMATION) && YiliConfig.get(YiliConfig.ANIMATIONS);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        replaced = count > 0 && count <= 64 ? s.subSequence(start, start + count).toString() : "";
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (!enabled() || !(s instanceof Spannable) || count <= 0) {
            return;
        }
        // Only the new tail: a composing word rewritten from "hel" to "hell" animates just "l".
        int from = start, to = start + count;
        if (before > 0) {
            if (before >= count || !s.subSequence(start, start + before).toString().equals(replaced)) {
                return; // replacement, autocorrect or paste over a selection
            }
            from = start + before;
        }
        if (to - from > MAX_RUN || to > s.length()) {
            return; // paste or programmatic set: no animation
        }
        Spannable sp = (Spannable) s;
        if (sp.getSpans(from, to, Object.class).length > 0) {
            for (Object o : sp.getSpans(from, to, Object.class)) {
                if (!(o instanceof android.text.style.SuggestionSpan) && (sp.getSpanFlags(o) & Spanned.SPAN_COMPOSING) == 0
                        && !(o instanceof android.text.NoCopySpan)) {
                    return; // emoji or entity spans
                }
            }
        }
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (Character.isSurrogate(c) || c == '\n') {
                return;
            }
        }
        sp.setSpan(new TypingSpan(view), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    /** Draws its characters with the entrance animation, then removes itself. */
    static final class TypingSpan extends ReplacementSpan {
        private final long startTime = SystemClock.uptimeMillis();
        private final WeakReference<EditText> view;
        private boolean removing;

        TypingSpan(WeakReference<EditText> view) {
            this.view = view;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            if (fm != null) {
                paint.getFontMetricsInt(fm);
            }
            return Math.round(paint.measureText(text, start, end));
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            float t = Math.min(1f, (SystemClock.uptimeMillis() - startTime) / (float) DURATION);
            float e = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(t);
            int alpha = paint.getAlpha();
            float w = paint.measureText(text, start, end);
            canvas.save();
            canvas.translate(0, AndroidUtilities.dp(6) * (1f - e));
            float s = 0.7f + 0.3f * e;
            canvas.scale(s, s, x + w / 2f, y);
            paint.setAlpha((int) (alpha * e));
            canvas.drawText(text, start, end, x, y, paint);
            paint.setAlpha(alpha);
            canvas.restore();

            EditText v = view.get();
            if (v == null) {
                return;
            }
            if (t < 1f) {
                v.postInvalidateOnAnimation();
            } else if (!removing) {
                removing = true;
                v.post(() -> {
                    if (v.getText() instanceof Spannable) {
                        ((Spannable) v.getText()).removeSpan(this);
                    }
                });
            }
        }
    }
}
