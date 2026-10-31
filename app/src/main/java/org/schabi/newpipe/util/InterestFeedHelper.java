package org.schabi.newpipe.util;

import android.content.Context;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.stream.model.StreamEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Builds a "for you" search query from what the user searched and watched recently,
 * so the home feed follows their interests. Everything stays on the device.
 */
public final class InterestFeedHelper {
    public static final String FALLBACK_QUERY = "AI technology phonk";
    private static final int HISTORY_LIMIT = 12;
    private static final Random RANDOM = new Random();
    private static String lastQuery = "";

    private InterestFeedHelper() { }

    /** Picks one interest at random from recent searches and watched videos. */
    public static Single<String> pickQuery(final Context context) {
        final AppDatabase db = NewPipeDatabase.getInstance(context.getApplicationContext());

        final Single<List<String>> searches = db.searchHistoryDAO()
                .getUniqueEntries(HISTORY_LIMIT)
                .firstOrError()
                .<List<String>>map(list -> list)
                .onErrorReturnItem(Collections.emptyList());
        final Single<List<StreamEntity>> watched = db.streamHistoryDAO()
                .getRecentlyWatchedStreams(HISTORY_LIMIT)
                .firstOrError()
                .<List<StreamEntity>>map(list -> list)
                .onErrorReturnItem(Collections.emptyList());

        return Single.zip(searches, watched, InterestFeedHelper::buildQuery)
                .subscribeOn(Schedulers.io());
    }

    private static String buildQuery(final List<String> searches,
                                     final List<StreamEntity> watched) {
        final List<String> candidates = new ArrayList<>();
        // searches are the strongest signal, so they are added twice
        for (final String search : searches) {
            if (search != null && search.trim().length() > 1) {
                candidates.add(search.trim());
                candidates.add(search.trim());
            }
        }
        for (final StreamEntity stream : watched) {
            final String shortTitle = shortTitle(stream.getTitle());
            if (!shortTitle.isEmpty()) {
                candidates.add(shortTitle);
            }
            final String uploader = stream.getUploader();
            if (uploader != null && uploader.trim().length() > 1) {
                candidates.add(uploader.trim());
            }
        }
        if (candidates.isEmpty()) {
            return FALLBACK_QUERY;
        }

        String pick = candidates.get(RANDOM.nextInt(candidates.size()));
        for (int i = 0; i < 4 && pick.equalsIgnoreCase(lastQuery); i++) {
            pick = candidates.get(RANDOM.nextInt(candidates.size()));
        }
        lastQuery = pick;
        return pick;
    }

    /** First few meaningful words of a title, without symbols. */
    private static String shortTitle(final String title) {
        if (title == null) {
            return "";
        }
        final String[] words = title.replaceAll("[^\\p{L}\\p{N} ]", " ").trim().split("\\s+");
        final StringBuilder sb = new StringBuilder();
        int count = 0;
        for (final String word : words) {
            if (word.length() < 2) {
                continue;
            }
            sb.append(sb.length() == 0 ? "" : " ").append(word);
            if (++count == 4) {
                break;
            }
        }
        return sb.toString();
    }
}
