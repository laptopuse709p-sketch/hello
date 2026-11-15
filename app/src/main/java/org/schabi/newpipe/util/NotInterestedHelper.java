package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Remembers what the user marked "Not interested" (stored only on this phone):
 * the channel and the video, plus the video title so matching interests can be dropped
 * from the home feed.
 */
public final class NotInterestedHelper {
    private static final String PREFS = "pipe_not_interested";
    private static final String KEY_CHANNEL_URLS = "channel_urls";
    private static final String KEY_CHANNEL_NAMES = "channel_names";
    private static final String KEY_VIDEO_URLS = "video_urls";
    private static final String KEY_TITLES = "titles";
    private static final String SEPARATOR = "\u0001";
    private static final int MAX_TITLES = 60;

    private NotInterestedHelper() { }

    private static SharedPreferences prefs(@NonNull final Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isHidden(@NonNull final Context context, @NonNull final InfoItem item) {
        if (!(item instanceof StreamInfoItem)) {
            return false;
        }
        final StreamInfoItem stream = (StreamInfoItem) item;
        final SharedPreferences p = prefs(context);
        final String channelUrl = stream.getUploaderUrl();
        if (channelUrl != null && !channelUrl.isEmpty()
                && p.getStringSet(KEY_CHANNEL_URLS, new HashSet<>()).contains(channelUrl)) {
            return true;
        }
        return p.getStringSet(KEY_VIDEO_URLS, new HashSet<>()).contains(stream.getUrl());
    }

    @NonNull
    public static List<InfoItem> filter(@NonNull final Context context,
                                        @NonNull final List<? extends InfoItem> items) {
        final List<InfoItem> result = new ArrayList<>();
        for (final InfoItem item : items) {
            if (!isHidden(context, item)) {
                result.add(item);
            }
        }
        return result;
    }

    public static void add(@NonNull final Context context, @NonNull final StreamInfoItem item) {
        final SharedPreferences p = prefs(context);
        final SharedPreferences.Editor editor = p.edit();
        editor.putStringSet(KEY_VIDEO_URLS, with(p, KEY_VIDEO_URLS, item.getUrl(), true));
        final String channelUrl = item.getUploaderUrl();
        if (channelUrl != null && !channelUrl.isEmpty()) {
            editor.putStringSet(KEY_CHANNEL_URLS, with(p, KEY_CHANNEL_URLS, channelUrl, true));
        }
        final String name = item.getUploaderName();
        if (name != null && !name.trim().isEmpty()) {
            editor.putStringSet(KEY_CHANNEL_NAMES, with(p, KEY_CHANNEL_NAMES,
                    name.trim().toLowerCase(Locale.ROOT), true));
        }
        final List<String> titles = blockedTitles(context);
        final String title = item.getName() == null ? ""
                : item.getName().trim().toLowerCase(Locale.ROOT);
        if (!title.isEmpty() && !titles.contains(title)) {
            titles.add(title);
            while (titles.size() > MAX_TITLES) {
                titles.remove(0);
            }
            editor.putString(KEY_TITLES, String.join(SEPARATOR, titles));
        }
        editor.apply();
    }

    /** Undo: forgets this video, its channel and its title. */
    public static void remove(@NonNull final Context context, @NonNull final StreamInfoItem item) {
        final SharedPreferences p = prefs(context);
        final SharedPreferences.Editor editor = p.edit();
        editor.putStringSet(KEY_VIDEO_URLS, with(p, KEY_VIDEO_URLS, item.getUrl(), false));
        final String channelUrl = item.getUploaderUrl();
        if (channelUrl != null) {
            editor.putStringSet(KEY_CHANNEL_URLS, with(p, KEY_CHANNEL_URLS, channelUrl, false));
        }
        final String name = item.getUploaderName();
        if (name != null) {
            editor.putStringSet(KEY_CHANNEL_NAMES, with(p, KEY_CHANNEL_NAMES,
                    name.trim().toLowerCase(Locale.ROOT), false));
        }
        final List<String> titles = blockedTitles(context);
        titles.remove(item.getName() == null ? "" : item.getName().trim().toLowerCase(Locale.ROOT));
        editor.putString(KEY_TITLES, String.join(SEPARATOR, titles));
        editor.apply();
    }

    public static void clear(@NonNull final Context context) {
        prefs(context).edit().clear().apply();
    }

    @NonNull
    public static Set<String> blockedChannelNames(@NonNull final Context context) {
        return new HashSet<>(prefs(context).getStringSet(KEY_CHANNEL_NAMES, new HashSet<>()));
    }

    @NonNull
    public static List<String> blockedTitles(@NonNull final Context context) {
        final String stored = prefs(context).getString(KEY_TITLES, "");
        final List<String> titles = new ArrayList<>();
        if (stored != null && !stored.isEmpty()) {
            for (final String title : stored.split(SEPARATOR)) {
                if (!title.isEmpty()) {
                    titles.add(title);
                }
            }
        }
        return titles;
    }

    private static Set<String> with(final SharedPreferences p, final String key,
                                    final String value, final boolean add) {
        final Set<String> copy = new HashSet<>(p.getStringSet(key, new HashSet<>()));
        if (add) {
            copy.add(value);
        } else {
            copy.remove(value);
        }
        return copy;
    }
}
