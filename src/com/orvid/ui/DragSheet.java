package com.orvid.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

public class DragSheet extends LinearLayout {

    public interface Listener {
        void onDismissed();
    }

    private final int slop;
    private final int dismissPx;
    private Listener listener;
    private View scroller;
    private boolean enabled = true;
    private float downY;
    private float downX;
    private boolean dragging;
    private boolean aboveScroller;
    private final int flingPx;
    private final int flingSpeed;
    private android.view.VelocityTracker velocity;

    private void track(MotionEvent event) {
        if (velocity == null) velocity = android.view.VelocityTracker.obtain();
        velocity.addMovement(event);
    }

    private float speedDown() {
        if (velocity == null) return 0;
        velocity.computeCurrentVelocity(1000);
        float v = velocity.getYVelocity();
        velocity.recycle();
        velocity = null;
        return v;
    }

    public DragSheet(Context context, AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        dismissPx = Ui.dp(context, 64);
        flingPx = Ui.dp(context, 16);
        flingSpeed = ViewConfiguration.get(context).getScaledMinimumFlingVelocity() * 4;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setScroller(View view) {
        scroller = view;
    }

    public void setDragEnabled(boolean value) {
        enabled = value;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!enabled) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = event.getRawY();
                downX = event.getRawX();
                dragging = false;
                aboveScroller = scroller == null || event.getY() < scroller.getTop();
                if (velocity != null) velocity.clear();
                track(event);
                break;
            case MotionEvent.ACTION_MOVE:
                track(event);
                if (!aboveScroller && scroller.canScrollVertically(-1)) break;
                float dy = event.getRawY() - downY;
                if (dy > slop && dy > Math.abs(event.getRawX() - downX)) {
                    dragging = true;
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!enabled) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                track(event);
                setTranslationY(Math.max(0, event.getRawY() - downY));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                track(event);
                boolean flung = event.getActionMasked() == MotionEvent.ACTION_UP
                        && speedDown() > flingSpeed && getTranslationY() > flingPx;
                boolean far = flung || getTranslationY() > dismissPx;
                dragging = false;
                if (far && listener != null) {
                    listener.onDismissed();
                } else {
                    animate().translationY(0).setDuration(160).start();
                }
                return true;
        }
        return dragging;
    }
}
