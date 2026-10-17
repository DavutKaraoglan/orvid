package com.orvid.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public final class AmbientView extends View {

    private static final int BASE = 0xFF000000;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect video = new Rect();
    private final int[] from = {BASE};
    private final int[] to = {BASE};
    private final int[] now = {BASE};
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private ValueAnimator animator;
    private LinearGradient gradient;
    private int gradientColor;
    private int gradientHeight;

    public AmbientView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setVideoRect(int left, int top, int right, int bottom) {
        video.set(left, top, right, bottom);
        invalidate();
    }

    public boolean barsVisible() {
        int w = getWidth();
        return !video.isEmpty() && (video.left > 0 || video.right < w);
    }

    public void setGlow(int color) {
        int target = dim(color);
        if (!barsVisible()) {
            if (animator != null) animator.cancel();
            now[0] = target;
            return;
        }
        from[0] = now[0];
        to[0] = target;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(4000);
        animator.addUpdateListener(a -> {
            now[0] = (int) argb.evaluate((float) a.getAnimatedValue(), from[0], to[0]);
            invalidate();
        });
        animator.start();
    }

    private static int dim(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return 0xFF000000 | (r * 6 / 10) << 16 | (g * 6 / 10) << 8 | (b * 6 / 10);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.clipRect(0, 0, w, h);
        canvas.drawColor(BASE);
        if (video.isEmpty() || video.left <= 0 && video.right >= w) return;
        if (gradient == null || gradientColor != now[0] || gradientHeight != h) {
            gradient = new LinearGradient(0, h, 0, h * 0.15f, now[0], BASE, Shader.TileMode.CLAMP);
            gradientColor = now[0];
            gradientHeight = h;
        }
        paint.setShader(gradient);
        if (video.left > 0) canvas.drawRect(0, 0, video.left, h, paint);
        if (video.right < w) canvas.drawRect(video.right, 0, w, h, paint);
        paint.setShader(null);
    }
}
