package org.schabi.newpipe.fragments.detail;

import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Supplier;

/**
 * Keeps an {@link ImageView} filled with a blurred copy of the frame that the player is
 * currently showing. The player keeps drawing to its one and only surface; only a tiny copy of
 * the picture is taken a few times per second, so no second player is needed.
 *
 * Scaling a tiny bitmap up already blurs it. On Android 12 and newer a real blur effect is
 * applied on top. If a frame can't be copied, the stream's thumbnail is shown instead.
 */
public class VideoFrameBlur {
    private static final long INTERVAL_MS = 200;
    private static final int COPY_WIDTH = 64;

    private final ImageView target;
    private final Supplier<SurfaceView> surfaceSupplier;
    private final Supplier<Drawable> fallbackSupplier;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean running = false;
    private boolean copyPending = false;
    private boolean hasFrame = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            captureFrame();
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    public VideoFrameBlur(@NonNull final ImageView target,
                          @NonNull final Supplier<SurfaceView> surfaceSupplier,
                          @NonNull final Supplier<Drawable> fallbackSupplier) {
        this.target = target;
        this.surfaceSupplier = surfaceSupplier;
        this.fallbackSupplier = fallbackSupplier;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        hasFrame = false;
        target.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            target.setRenderEffect(
                    RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP));
        }
        showFallback();
        handler.post(tick);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            target.setRenderEffect(null);
        }
        target.setImageDrawable(null);
    }

    private void showFallback() {
        if (hasFrame) {
            return;
        }
        final Drawable fallback = fallbackSupplier.get();
        if (fallback != null) {
            target.setImageDrawable(fallback);
        }
    }

    private void captureFrame() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || copyPending) {
            return;
        }
        final SurfaceView surfaceView = surfaceSupplier.get();
        if (surfaceView == null
                || surfaceView.getWidth() <= 0
                || surfaceView.getHeight() <= 0
                || !surfaceView.getHolder().getSurface().isValid()) {
            showFallback();
            return;
        }

        final int height = Math.max(1,
                Math.round(COPY_WIDTH * surfaceView.getHeight() / (float) surfaceView.getWidth()));
        final Bitmap bitmap = Bitmap.createBitmap(COPY_WIDTH, height, Bitmap.Config.ARGB_8888);
        copyPending = true;
        try {
            PixelCopy.request(surfaceView, bitmap, result -> {
                copyPending = false;
                if (!running) {
                    return;
                }
                if (result == PixelCopy.SUCCESS) {
                    hasFrame = true;
                    target.setImageBitmap(bitmap);
                } else {
                    showFallback();
                }
            }, handler);
        } catch (final IllegalArgumentException e) {
            // The surface went away between the check and the request.
            copyPending = false;
            showFallback();
        }
    }

}
