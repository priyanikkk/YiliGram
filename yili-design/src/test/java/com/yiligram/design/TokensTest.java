package com.yiligram.design;

import static org.junit.Assert.assertEquals;

import com.yiligram.design.motion.SpringSpec;
import com.yiligram.design.motion.YiliMotion;
import com.yiligram.design.shape.YiliShapes;
import com.yiligram.design.type.TypeStyle;
import com.yiligram.design.type.YiliType;

import org.junit.After;
import org.junit.Test;

public class TokensTest {

    @After
    public void reset() {
        YiliMotion.configure(YiliMotion.Scheme.EXPRESSIVE, 1f, 1f, true);
        YiliType.configure(1f, 0);
        YiliShapes.setRoundness(1f);
    }

    @Test
    public void springTokensMatchMdc() {
        SpringSpec s = YiliMotion.token(YiliMotion.Scheme.EXPRESSIVE, YiliMotion.Kind.SPATIAL, YiliMotion.Speed.DEFAULT);
        assertEquals(380f, s.stiffness, 0f);
        assertEquals(0.8f, s.dampingRatio, 0f);
        s = YiliMotion.token(YiliMotion.Scheme.EXPRESSIVE, YiliMotion.Kind.SPATIAL, YiliMotion.Speed.FAST);
        assertEquals(800f, s.stiffness, 0f);
        assertEquals(0.6f, s.dampingRatio, 0f);
        s = YiliMotion.token(YiliMotion.Scheme.STANDARD, YiliMotion.Kind.SPATIAL, YiliMotion.Speed.SLOW);
        assertEquals(300f, s.stiffness, 0f);
        assertEquals(0.9f, s.dampingRatio, 0f);
        s = YiliMotion.token(YiliMotion.Scheme.STANDARD, YiliMotion.Kind.EFFECTS, YiliMotion.Speed.FAST);
        assertEquals(3800f, s.stiffness, 0f);
        assertEquals(1f, s.dampingRatio, 0f);
    }

    @Test
    public void userMotionPreferences() {
        YiliMotion.configure(YiliMotion.Scheme.EXPRESSIVE, 2f, 0f, true);
        SpringSpec s = YiliMotion.spatial(YiliMotion.Speed.DEFAULT);
        assertEquals(380f * 4f, s.stiffness, 0.01f);
        assertEquals(1f, s.dampingRatio, 0f);
        YiliMotion.configure(YiliMotion.Scheme.EXPRESSIVE, 1f, 0.5f, true);
        assertEquals(0.9f, YiliMotion.spatial(YiliMotion.Speed.DEFAULT).dampingRatio, 1e-6f);
    }

    @Test
    public void shapeScale() {
        assertEquals(28f, YiliShapes.Corner.EXTRA_LARGE.dp, 0f);
        assertEquals(48f, YiliShapes.Corner.EXTRA_EXTRA_LARGE.dp, 0f);
        assertEquals(50f, YiliShapes.Corner.FULL.resolvePx(2f, 100f, 300f), 0f);
        assertEquals(24f, YiliShapes.Corner.MEDIUM.resolvePx(2f, 100f, 300f), 0f);
        YiliShapes.setRoundness(2f);
        assertEquals(56f, YiliShapes.radiusDp(YiliShapes.Corner.EXTRA_LARGE), 0f);
    }

    @Test
    public void typeScale() {
        TypeStyle t = YiliType.token(YiliType.Style.TITLE_MEDIUM, true);
        assertEquals(16f, t.sizeSp, 0f);
        assertEquals(700, t.weight);
        t = YiliType.token(YiliType.Style.DISPLAY_LARGE, false);
        assertEquals(57f, t.sizeSp, 0f);
        assertEquals(64f, t.lineHeightSp, 0f);
        YiliType.configure(1.25f, 100);
        t = YiliType.get(YiliType.Style.BODY_LARGE);
        assertEquals(20f, t.sizeSp, 1e-4f);
        assertEquals(500, t.weight);
    }
}
