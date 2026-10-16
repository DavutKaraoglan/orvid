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

    public DragSheet(Context context, AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        dismissPx = Ui.dp(context, 110);
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
                break;
            case MotionEvent.ACTION_MOVE:
                if (scroller != null && scroller.canScrollVertically(-1)) break;
                float dy = event.getRawY() - downY;
                if (dy > slop * 2 && dy > Math.abs(event.getRawX() - downX) * 1.5f) {
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
                setTranslationY(Math.max(0, event.getRawY() - downY));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean far = getTranslationY() > dismissPx;
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
