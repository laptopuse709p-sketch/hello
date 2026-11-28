package org.schabi.newpipe.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

/** Incognito mode: while on, nothing is saved to watch history, search history or progress. */
public final class IncognitoHelper {
    private static final String KEY = "incognito_mode";

    private IncognitoHelper() { }

    public static boolean isOn(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY, false);
    }

    /** @return the new state */
    public static boolean toggle(@NonNull final Context context) {
        final boolean on = !isOn(context);
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(KEY, on).apply();
        return on;
    }
}
