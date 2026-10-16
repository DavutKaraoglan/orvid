package com.orvid.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ScrollView;

final class SidePanel {

    interface OnClose {
        void closed();
    }

    private final ScrollView panel;
    private ValueAnimator animator;
    private OnClose onClose;
    private boolean open;
    private int generation;

    SidePanel(ScrollView panel) {
        this.panel = panel;
    }

    boolean isOpen() {
        return open;
    }

    void open(View content, OnClose onClose) {
        this.onClose = onClose;
        panel.removeAllViews();
        panel.addView(content);
        panel.scrollTo(0, 0);
        int target = Math.round(panel.getResources().getDisplayMetrics().density * 300);
        open = true;
        panel.setVisibility(View.VISIBLE);
        animate(currentWidth(), target, null);
    }

    void close(boolean animated) {
        if (!open) return;
        open = false;
        OnClose done = onClose;
        onClose = null;
        Runnable finish = () -> {
            panel.removeAllViews();
            panel.setVisibility(View.GONE);
            if (done != null) done.closed();
        };
        if (!animated) {
            generation++;
            if (animator != null) animator.cancel();
            setWidth(0);
            finish.run();
            return;
        }
        animate(currentWidth(), 0, finish);
    }

    private int currentWidth() {
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        return Math.max(0, lp.width);
    }

    private void animate(int from, int to, Runnable end) {
        final int token = ++generation;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(240);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            if (token == generation) setWidth((int) a.getAnimatedValue());
        });
        if (end != null) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator a) {
                    if (token == generation) end.run();
                }
            });
        }
        animator.start();
    }

    private void setWidth(int w) {
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        lp.width = w;
        panel.setLayoutParams(lp);
    }
}
