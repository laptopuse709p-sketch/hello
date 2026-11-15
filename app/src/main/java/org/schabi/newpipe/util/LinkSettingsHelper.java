package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

/** Helps the user let Pipe open youtube.com links (Android needs a one-time approval). */
public final class LinkSettingsHelper {
    private static final String KEY_PROMPT_SHOWN = "pipe_links_prompt_shown";

    private LinkSettingsHelper() { }

    /** Opens the system screen where "Add links" is enabled for this app. */
    public static void openSystemLinkSettings(final Context context) {
        final Uri pkg = Uri.parse("package:" + context.getPackageName());
        Intent intent = new Intent("android.settings.APP_OPEN_BY_DEFAULT_SETTINGS", pkg);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || intent.resolveActivity(context.getPackageManager()) == null) {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** Asks once, the first time the app opens. */
    public static void promptOnce(final Activity activity) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (prefs.getBoolean(KEY_PROMPT_SHOWN, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY_PROMPT_SHOWN, true).apply();
        new AlertDialog.Builder(activity)
                .setTitle(R.string.open_links_title)
                .setMessage(R.string.open_links_message)
                .setPositiveButton(R.string.open_links_action,
                        (d, w) -> openSystemLinkSettings(activity))
                .setNegativeButton(R.string.later, null)
                .show();
    }
}
