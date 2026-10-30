package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A frame layout that watches for vertical drags on its content and reports them to a
 * {@link Listener}. Dragging down while the page is at its top enters the immersive view,
 * and dragging up while the immersive view is open leaves it. All other touches, including
 * normal list scrolling, pass through untouched.
 */
public class ImmersiveDragLayout extends FrameLayout {

    public interface Listener {
        boolean isImmersive();

        boolean canStartEnterDrag(float rawX, float rawY);

        void onDragStart(boolean entering);

        /** @param totalDy vertical finger movement since the drag started, down is positive */
        void onDragMove(float totalDy);

        void onDragEnd(float totalDy, float velocityY);
    }

    private static final float DIRECTION_RATIO = 1.5f;

    private final int touchSlop;
    @Nullable
    private Listener listener;
    @Nullable
    private VelocityTracker velocityTracker;

    private float downX;
    private float downY;
    private float dragOriginY;
    private boolean candidateEnter;
    private boolean dragging;

    public ImmersiveDragLayout(@NonNull final Context context) {
        this(context, null);
    }

    public ImmersiveDragLayout(@NonNull final Context context,
                               @Nullable final AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setListener(@Nullable final Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        if (listener == null) {
            return false;
        }
        track(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                dragging = false;
                candidateEnter = !listener.isImmersive()
                        && listener.canStartEnterDrag(ev.getRawX(), ev.getRawY());
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    return true;
                }
                if (ev.getPointerCount() > 1) {
                    break;
                }
                return maybeStartDrag(ev);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                recycleTracker();
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        if (listener == null) {
            return super.onTouchEvent(ev);
        }
        track(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                dragging = false;
                candidateEnter = false;
                // While the immersive view is open, an empty area of the screen still needs to
                // receive the drag that closes it.
                return listener.isImmersive();
            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    return maybeStartDrag(ev) || listener.isImmersive();
                }
                listener.onDragMove(ev.getY() - dragOriginY);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    float velocity = 0;
                    if (velocityTracker != null) {
                        velocityTracker.computeCurrentVelocity(1000);
                        velocity = velocityTracker.getYVelocity();
                    }
                    listener.onDragEnd(ev.getY() - dragOriginY,
                            ev.getActionMasked() == MotionEvent.ACTION_CANCEL ? 0 : velocity);
                    recycleTracker();
                    return true;
                }
                recycleTracker();
                return false;
            default:
                return dragging;
        }
    }

    private boolean maybeStartDrag(final MotionEvent ev) {
        final float dy = ev.getY() - downY;
        final float dx = ev.getX() - downX;
        if (Math.abs(dy) <= touchSlop || Math.abs(dy) <= Math.abs(dx) * DIRECTION_RATIO) {
            return false;
        }
        final boolean immersive = listener.isImmersive();
        final boolean wantsEnter = !immersive && candidateEnter && dy > 0;
        final boolean wantsExit = immersive && dy < 0;
        if (!wantsEnter && !wantsExit) {
            return false;
        }
        dragging = true;
        dragOriginY = ev.getY();
        listener.onDragStart(wantsEnter);
        return true;
    }

    private void track(final MotionEvent ev) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);
    }

    private void recycleTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }
}
