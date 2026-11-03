package org.schabi.newpipe.util;

import android.animation.ObjectAnimator;
import android.view.View;
import android.animation.ValueAnimator;

import org.schabi.newpipe.R;

/** Shows or hides a skeleton placeholder with a soft pulsing animation. */
public final class SkeletonHelper {
    private SkeletonHelper() { }

    public static void setActive(final View skeleton, final boolean active) {
        if (skeleton == null) {
            return;
        }
        final Object tag = skeleton.getTag(R.id.loading_skeleton);
        if (active) {
            skeleton.setVisibility(View.VISIBLE);
            if (!(tag instanceof ObjectAnimator)) {
                final ObjectAnimator pulse = ObjectAnimator.ofFloat(skeleton, View.ALPHA,
                        0.45f, 1f);
                pulse.setDuration(750);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.setRepeatMode(ValueAnimator.REVERSE);
                skeleton.setTag(R.id.loading_skeleton, pulse);
                pulse.start();
            }
        } else {
            if (tag instanceof ObjectAnimator) {
                ((ObjectAnimator) tag).cancel();
                skeleton.setTag(R.id.loading_skeleton, null);
            }
            skeleton.setAlpha(1f);
            skeleton.setVisibility(View.GONE);
        }
    }
}
