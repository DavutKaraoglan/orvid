package com.orvid.ui;

import android.app.Activity;
import android.view.MotionEvent;

import com.orvid.app.OrvidApp;

public final class Panic {

    private static final float DROP_DP = 100;
    private static final float SPAN_DRIFT = 0.35f;

    private float startX;
    private float startY;
    private float startSpan;
    private boolean tracking;

    public boolean feed(Activity activity, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                tracking = e.getPointerCount() == 2 && OrvidApp.get().panicExit();
                if (tracking) {
                    startX = midX(e);
                    startY = midY(e);
                    startSpan = span(e);
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return false;
                if (e.getPointerCount() != 2) {
                    tracking = false;
                    return false;
                }
                float drop = midY(e) - startY;
                float sideways = Math.abs(midX(e) - startX);
                float drift = startSpan > 0 ? Math.abs(span(e) - startSpan) / startSpan : 1;
                if (drift > SPAN_DRIFT || sideways > Math.max(drop, 0) * 0.5f + Ui.dp(activity, 24)) {
                    tracking = false;
                    return false;
                }
                if (drop < Ui.dp(activity, DROP_DP)) return false;
                tracking = false;
                exit(activity);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                return false;
            default:
                return false;
        }
    }

    private static void exit(Activity activity) {
        activity.getWindow().getDecorView().setVisibility(android.view.View.INVISIBLE);
        try {
            activity.moveTaskToBack(true);
            activity.finishAndRemoveTask();
            activity.overridePendingTransition(0, 0);
        } catch (RuntimeException ignored) {
        }
        new Thread(() -> OrvidApp.get().wipeAndExit(), "orvid-panic").start();
    }

    private static float midX(MotionEvent e) {
        return (e.getX(0) + e.getX(1)) / 2;
    }

    private static float midY(MotionEvent e) {
        return (e.getY(0) + e.getY(1)) / 2;
    }

    private static float span(MotionEvent e) {
        return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
    }
}
