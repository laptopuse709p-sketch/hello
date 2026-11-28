package org.schabi.newpipe.util;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.concurrent.Executors;

/**
 * Saves a picture of the current video frame. It first tries to load that exact frame from the
 * best quality video stream (real high resolution detail), and falls back to copying what the
 * player shows.
 */
public final class ScreenshotHelper {
    private ScreenshotHelper() { }

    public static void take(@NonNull final Context context, @Nullable final StreamInfo info,
                            final long positionMs, @NonNull final SurfaceView surface) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        Toast.makeText(app, R.string.screenshot_saving, Toast.LENGTH_SHORT).show();

        // the fallback frame is grabbed right now, on the main thread, so it matches the pause
        copyFromSurface(surface, main, fallback ->
                Executors.newSingleThreadExecutor().execute(() -> {
                    Bitmap result = loadBestQualityFrame(info, positionMs);
                    if (result == null) {
                        result = fallback;
                    }
                    final String name = result == null ? null : save(app, result);
                    main.post(() -> Toast.makeText(app,
                            name == null ? app.getString(R.string.screenshot_failed)
                                    : app.getString(R.string.screenshot_saved, name),
                            Toast.LENGTH_LONG).show());
                }));
    }

    @Nullable
    private static Bitmap loadBestQualityFrame(@Nullable final StreamInfo info,
                                               final long positionMs) {
        if (info == null) {
            return null;
        }
        VideoStream best = null;
        for (final VideoStream stream : info.getVideoOnlyStreams()) {
            if (!stream.isUrl() || stream.getFormat() != MediaFormat.MPEG_4) {
                continue; // mp4 video decodes everywhere
            }
            if (best == null || stream.getHeight() > best.getHeight()) {
                best = stream;
            }
        }
        if (best == null) {
            return null;
        }
        final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            final HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", DownloaderImpl.USER_AGENT);
            retriever.setDataSource(best.getContent(), headers);
            return retriever.getFrameAtTime(positionMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST);
        } catch (final Exception e) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (final Exception ignored) {
                // nothing to do
            }
        }
    }

    private interface BitmapCallback {
        void done(@Nullable Bitmap bitmap);
    }

    private static void copyFromSurface(final SurfaceView surface, final Handler handler,
                                        final BitmapCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                || surface.getWidth() <= 0 || surface.getHeight() <= 0) {
            callback.done(null);
            return;
        }
        final Bitmap bitmap = Bitmap.createBitmap(surface.getWidth(), surface.getHeight(),
                Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(surface, bitmap, result ->
                    callback.done(result == PixelCopy.SUCCESS ? bitmap : null), handler);
        } catch (final Exception e) {
            callback.done(null);
        }
    }

    @Nullable
    private static String save(final Context context, final Bitmap bitmap) {
        final String name = "Pipe_" + System.currentTimeMillis() + ".png";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                final ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Pipe");
                final Uri uri = context.getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return null;
                }
                try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            } else {
                final File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "Pipe");
                if (!dir.exists() && !dir.mkdirs()) {
                    return null;
                }
                final File file = new File(dir, name);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()},
                        null, null);
            }
            return name;
        } catch (final Exception e) {
            return null;
        }
    }
}
