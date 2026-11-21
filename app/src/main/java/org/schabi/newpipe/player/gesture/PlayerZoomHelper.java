package org.schabi.newpipe.player.gesture;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;

import java.util.function.BooleanSupplier;

/**
 * Pinch to zoom the video (up to 4x) and one finger to move around while zoomed.
 * Zoom is a plain enlargement of the video view, clipped to the player area.
 */
public final class PlayerZoomHelper {
    private static final float MAX_SCALE = 4f;

    private final View target;
    private final BooleanSupplier allowed;
    private final ScaleGestureDetector detector;
    private final int touchSlop;

    private float scale = 1f;
    private float tx = 0f;
    private float ty = 0f;
    private float lastX;
    private float lastY;
    private boolean panning;
    private boolean multiTouchGesture;

    public PlayerZoomHelper(@NonNull final Context context, @NonNull final View target,
                            @NonNull final BooleanSupplier allowed) {
        this.target = target;
        this.allowed = allowed;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.detector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(@NonNull final ScaleGestureDetector d) {
                        scale = Math.max(1f, Math.min(MAX_SCALE, scale * d.getScaleFactor()));
                        apply();
                        return true;
                    }

                    @Override
                    public void onScaleEnd(@NonNull final ScaleGestureDetector d) {
                        if (scale < 1.05f) {
                            reset();
                        }
                    }
                });
    }

    public boolean isZoomed() {
        return scale > 1.01f;
    }

    /** @return true if this helper used the event, so the normal player gestures must skip it */
    public boolean onTouch(@NonNull final MotionEvent e) {
        if (!allowed.getAsBoolean()) {
            return false;
        }
        final int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            multiTouchGesture = false;
            panning = false;
            lastX = e.getX();
            lastY = e.getY();
        }
        detector.onTouchEvent(e);
        if (e.getPointerCount() > 1 || detector.isInProgress()) {
            multiTouchGesture = true;
        }
        if (multiTouchGesture) {
            // ignore the leftover finger after a pinch until everything is lifted
            lastX = e.getX();
            lastY = e.getY();
            return true;
        }
        if (isZoomed()) {
            if (action == MotionEvent.ACTION_MOVE) {
                final float dx = e.getX() - lastX;
                final float dy = e.getY() - lastY;
                if (!panning && Math.hypot(dx, dy) > touchSlop) {
                    panning = true;
                }
                if (panning) {
                    tx += dx;
                    ty += dy;
                    lastX = e.getX();
                    lastY = e.getY();
                    apply();
                    return true;
                }
            } else if (panning) {
                return true; // finish the pan without triggering a tap
            }
        }
        return false;
    }

    private void apply() {
        final float maxX = (scale - 1f) * target.getWidth() / 2f;
        final float maxY = (scale - 1f) * target.getHeight() / 2f;
        tx = Math.max(-maxX, Math.min(maxX, tx));
        ty = Math.max(-maxY, Math.min(maxY, ty));
        target.setScaleX(scale);
        target.setScaleY(scale);
        target.setTranslationX(tx);
        target.setTranslationY(ty);
        CustomBottomSheetBehavior.zoomed = isZoomed();
    }

    /** Back to normal size (new video, leaving fullscreen or the immersive view). */
    public void reset() {
        scale = 1f;
        tx = 0f;
        ty = 0f;
        panning = false;
        multiTouchGesture = false;
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setTranslationX(0f);
        target.setTranslationY(0f);
        CustomBottomSheetBehavior.zoomed = false;
    }
}
