package com.yiligram.ui;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yiligram.config.YiliConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Random;

/**
 * Built-in typing animation for the message field.
 *
 * <ul>
 * <li>Typed characters come out of a blur, rise from below and grow with a slight overshoot; several
 * characters at once (a suggestion, a swipe-typed word) appear one after another.</li>
 * <li>Deleted characters float up, shrink and burst into particles drawn over the field.</li>
 * </ul>
 *
 * Each new character gets its own short-lived {@link ReplacementSpan} that measures exactly like plain
 * text, so the layout never moves, and removes itself at the end. Only characters that are actually new
 * animate, also while the keyboard keeps rewriting its composing word. Text with other spans (emoji,
 * entities, formatting) and big pastes are left alone.
 */
public final class YiliTextAnimation implements TextWatcher {

    private static final long APPEAR = 420;
    private static final long STAGGER = 35;
    private static final long VANISH = 520;
    private static final int MAX_RUN = 24;
    private static final int MAX_DELETE = 16;

    private final WeakReference<EditText> view;
    private final Particles particles;
    private String replaced = "";

    private YiliTextAnimation(EditText view) {
        this.view = new WeakReference<>(view);
        this.particles = new Particles(view);
    }

    public static void attach(EditText editText) {
        YiliTextAnimation a = new YiliTextAnimation(editText);
        editText.addTextChangedListener(a);
        editText.getOverlay().add(a.particles);
    }

    private static boolean enabled() {
        return YiliConfig.get(YiliConfig.TEXT_ANIMATION) && YiliConfig.get(YiliConfig.ANIMATIONS);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        replaced = count > 0 && count <= 64 ? s.subSequence(start, start + count).toString() : "";
        if (!enabled() || count <= 0 || count > MAX_DELETE) {
            return;
        }
        // Pure deletion, or the keyboard shortening its composing word: the removed tail vanishes.
        int removedFrom = start, removedTo = start + count;
        if (after > 0) {
            if (after >= count) return;
            CharSequence keep = s.subSequence(start, start + after);
            // "hello" -> "hell" arrives as replace(start, 5, "hell"): only the tail is gone.
            removedFrom = start + after;
            if (!replaced.startsWith(keep.toString())) return;
        }
        EditText v = view.get();
        if (v == null || v.getLayout() == null) return;
        particles.capture(v, s, removedFrom, removedTo);
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (!enabled() || !(s instanceof Spannable) || count <= 0) {
            return;
        }
        int from = start, to = start + count;
        if (before > 0) {
            if (before >= count || !s.subSequence(start, start + before).toString().equals(replaced)) {
                return; // replacement, autocorrect or paste over a selection
            }
            from = start + before;
        }
        if (to - from > MAX_RUN || to > s.length()) {
            return;
        }
        Spannable sp = (Spannable) s;
        for (Object o : sp.getSpans(from, to, Object.class)) {
            if (!(o instanceof android.text.style.SuggestionSpan) && (sp.getSpanFlags(o) & Spanned.SPAN_COMPOSING) == 0
                    && !(o instanceof android.text.NoCopySpan) && !(o instanceof TypingSpan)) {
                return; // emoji or entity spans
            }
        }
        long now = SystemClock.uptimeMillis();
        int n = 0;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (Character.isSurrogate(c) || c == '\n') {
                return;
            }
        }
        for (int i = from; i < to; i++) {
            if (s.charAt(i) == ' ') continue;
            sp.setSpan(new TypingSpan(view, now + STAGGER * n++), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    /** One character's entrance: blur 6dp -> 0, rise 10dp, scale 0.35 -> 1 with overshoot. */
    static final class TypingSpan extends ReplacementSpan {
        private final long startTime;
        private final WeakReference<EditText> view;
        private boolean removing;

        TypingSpan(WeakReference<EditText> view, long startTime) {
            this.view = view;
            this.startTime = startTime;
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
            float t = Math.max(0f, Math.min(1f, (SystemClock.uptimeMillis() - startTime) / (float) APPEAR));
            EditText v = view.get();
            if (t >= 1f) {
                canvas.drawText(text, start, end, x, y, paint);
                if (v != null && !removing) {
                    removing = true;
                    v.post(() -> {
                        if (v.getText() instanceof Spannable) {
                            ((Spannable) v.getText()).removeSpan(this);
                        }
                    });
                }
                return;
            }
            float pos = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(t);
            float scale = 0.35f + 0.65f * overshoot(t);
            float alpha = Math.min(1f, t * 2.2f);
            float blur = AndroidUtilities.dp(6) * (1f - CubicBezierInterpolator.EASE_OUT.getInterpolation(t));
            float w = paint.measureText(text, start, end);
            int a = paint.getAlpha();
            android.graphics.MaskFilter mf = paint.getMaskFilter();
            canvas.save();
            canvas.translate(0, AndroidUtilities.dp(10) * (1f - pos));
            canvas.scale(scale, scale, x + w / 2f, y - paint.getTextSize() * 0.35f);
            paint.setAlpha((int) (a * alpha));
            if (blur > 0.5f) {
                paint.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
            }
            canvas.drawText(text, start, end, x, y, paint);
            paint.setMaskFilter(mf);
            paint.setAlpha(a);
            canvas.restore();
            if (v != null) {
                v.postInvalidateOnAnimation();
            }
        }

        /** 0..1 with a ~8% overshoot near the end (back-out). */
        private static float overshoot(float t) {
            float s = 1.9f;
            float u = t - 1f;
            return u * u * ((s + 1f) * u + s) + 1f;
        }
    }

    /** Overlay of deleted characters: they float up, shrink, fade and shed particles. */
    static final class Particles extends Drawable {
        private static final class Ghost {
            String ch;
            float x, y;
            long start;
            float[] px, py, vx, vy, size;
        }

        private final WeakReference<EditText> view;
        private final ArrayList<Ghost> ghosts = new ArrayList<>();
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();

        Particles(EditText v) {
            view = new WeakReference<>(v);
        }

        void capture(EditText v, CharSequence s, int from, int to) {
            Layout layout = v.getLayout();
            if (layout == null || to > s.length()) return;
            textPaint.set(v.getPaint());
            long now = SystemClock.uptimeMillis();
            int offX = v.getTotalPaddingLeft() - v.getScrollX();
            int offY = v.getTotalPaddingTop() - v.getScrollY();
            for (int i = from; i < to; i++) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c) || Character.isSurrogate(c)) continue;
                int line = layout.getLineForOffset(i);
                Ghost g = new Ghost();
                g.ch = String.valueOf(c);
                g.x = offX + layout.getPrimaryHorizontal(i);
                g.y = offY + layout.getLineBaseline(line);
                g.start = now + (to - 1 - i) * 20L;
                int n = 7;
                g.px = new float[n];
                g.py = new float[n];
                g.vx = new float[n];
                g.vy = new float[n];
                g.size = new float[n];
                float cw = textPaint.measureText(g.ch);
                for (int k = 0; k < n; k++) {
                    g.px[k] = g.x + random.nextFloat() * cw;
                    g.py[k] = g.y - random.nextFloat() * textPaint.getTextSize() * 0.7f;
                    double ang = Math.toRadians(-160 + random.nextFloat() * 140);
                    float sp = AndroidUtilities.dp(40 + random.nextFloat() * 70);
                    g.vx[k] = (float) Math.cos(ang) * sp;
                    g.vy[k] = (float) Math.sin(ang) * sp;
                    g.size[k] = AndroidUtilities.dp(0.8f + random.nextFloat() * 1.4f);
                }
                ghosts.add(g);
            }
            if (getBounds().isEmpty()) {
                setBounds(0, 0, v.getWidth(), v.getHeight());
            }
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            EditText v = view.get();
            if (v == null || ghosts.isEmpty()) return;
            if (getBounds().width() != v.getWidth() || getBounds().height() != v.getHeight()) {
                setBounds(0, 0, v.getWidth(), v.getHeight());
            }
            long now = SystemClock.uptimeMillis();
            int color = v.getCurrentTextColor();
            int baseAlpha = android.graphics.Color.alpha(color);
            dot.setColor(color);
            textPaint.setColor(color);
            for (int i = ghosts.size() - 1; i >= 0; i--) {
                Ghost g = ghosts.get(i);
                float t = (now - g.start) / (float) VANISH;
                if (t >= 1f) {
                    ghosts.remove(i);
                    continue;
                }
                if (t < 0) t = 0;
                float e = CubicBezierInterpolator.EASE_OUT.getInterpolation(t);
                // The character itself: up, smaller, gone in the first half.
                float ct = Math.min(1f, t * 2f);
                if (ct < 1f) {
                    float w = textPaint.measureText(g.ch);
                    canvas.save();
                    canvas.translate(0, -AndroidUtilities.dp(12) * e);
                    float sc = 1f - 0.5f * ct;
                    canvas.scale(sc, sc, g.x + w / 2f, g.y - textPaint.getTextSize() * 0.35f);
                    textPaint.setAlpha((int) (baseAlpha * (1f - ct)));
                    canvas.drawText(g.ch, g.x, g.y, textPaint);
                    canvas.restore();
                }
                // Particles: thrown out, slowed down, fading.
                float secs = t * VANISH / 1000f;
                dot.setAlpha((int) (baseAlpha * (1f - t) * 0.9f));
                for (int k = 0; k < g.px.length; k++) {
                    float dx = g.vx[k] * secs * (1f - 0.45f * t);
                    float dy = g.vy[k] * secs * (1f - 0.45f * t) + AndroidUtilities.dp(60) * secs * secs;
                    canvas.drawCircle(g.px[k] + dx, g.py[k] + dy, g.size[k] * (1f - 0.5f * t), dot);
                }
            }
            if (!ghosts.isEmpty()) {
                invalidateSelf();
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
