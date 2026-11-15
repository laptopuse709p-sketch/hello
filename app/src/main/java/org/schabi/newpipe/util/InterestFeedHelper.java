package org.schabi.newpipe.util;

import android.content.Context;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Builds the "All" home feed from the user's own history (kept on the device):
 * <ol>
 *   <li>interests are ranked: recent searches count most, then channels watched often;</li>
 *   <li>the best interest plus two rotating ones are searched at the same time;</li>
 *   <li>the results are mixed one by one, duplicates and already watched videos removed.</li>
 * </ol>
 */
public final class InterestFeedHelper {
    public static final String FALLBACK_QUERY = "AI technology phonk";

    private static final int SEARCH_LIMIT = 15;
    private static final int WATCH_LIMIT = 40;
    private static final int ITEMS_PER_TOPIC = 10;
    private static final int MAX_ITEMS = 30;
    /** Advances on every load so pull-to-refresh moves on to other interests. */
    private static int refreshIndex = 0;

    private InterestFeedHelper() { }

    /** A merged result; {@code firstTopic} is the search the "load more" continues. */
    public static final class Feed {
        public final SearchInfo info;
        public final String firstTopic;

        Feed(final SearchInfo info, final String firstTopic) {
            this.info = info;
            this.firstTopic = firstTopic;
        }
    }

    private static final class Plan {
        final List<String> topics;
        final Set<String> watchedUrls;

        Plan(final List<String> topics, final Set<String> watchedUrls) {
            this.topics = topics;
            this.watchedUrls = watchedUrls;
        }
    }

    public static Single<Feed> loadFeed(final Context context, final int serviceId,
                                        final List<String> filter) {
        final AppDatabase db = NewPipeDatabase.getInstance(context.getApplicationContext());
        final Single<List<String>> searches = db.searchHistoryDAO()
                .getUniqueEntries(SEARCH_LIMIT)
                .firstOrError()
                .<List<String>>map(list -> list)
                .onErrorReturnItem(Collections.emptyList());
        final Single<List<StreamEntity>> watched = db.streamHistoryDAO()
                .getRecentlyWatchedStreams(WATCH_LIMIT)
                .firstOrError()
                .<List<StreamEntity>>map(list -> list)
                .onErrorReturnItem(Collections.emptyList());

        final Context appContext = context.getApplicationContext();
        return Single.zip(searches, watched,
                        (s, w) -> makePlan(appContext, s, w))
                .subscribeOn(Schedulers.io())
                .flatMap(plan -> fetch(appContext, serviceId, filter, plan));
    }

    private static Plan makePlan(final Context context, final List<String> searches,
                                 final List<StreamEntity> watched) {
        final Set<String> blockedNames = NotInterestedHelper.blockedChannelNames(context);
        final List<String> blockedTitles = NotInterestedHelper.blockedTitles(context);
        final Map<String, Double> scores = new HashMap<>();
        final Map<String, String> display = new HashMap<>();

        // recent searches are the strongest signal
        for (int i = 0; i < searches.size(); i++) {
            final String text = searches.get(i) == null ? "" : searches.get(i).trim();
            if (text.length() > 1 && !isBlockedTopic(text, blockedNames, blockedTitles)) {
                add(scores, display, text, 4.0 / (1 + 0.25 * i));
            }
        }
        // channels the user keeps watching, newer watches count more
        final Set<String> watchedUrls = new HashSet<>();
        for (int i = 0; i < watched.size(); i++) {
            final StreamEntity stream = watched.get(i);
            watchedUrls.add(stream.getUrl());
            final String uploader = stream.getUploader() == null
                    ? "" : stream.getUploader().trim();
            if (uploader.length() > 1
                    && !blockedNames.contains(uploader.toLowerCase(Locale.ROOT))) {
                add(scores, display, uploader, 1.0 + 1.0 / (1 + 0.15 * i));
            }
        }

        final List<Map.Entry<String, Double>> ranked = new ArrayList<>(scores.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        final List<String> interests = new ArrayList<>();
        for (final Map.Entry<String, Double> entry : ranked) {
            interests.add(display.get(entry.getKey()));
        }

        final List<String> topics = new ArrayList<>();
        if (interests.isEmpty()) {
            topics.add(FALLBACK_QUERY);
        } else {
            topics.add(interests.get(0)); // the strongest interest is always there
            final List<String> rest = interests.subList(1, interests.size());
            if (rest.size() <= 2) {
                topics.addAll(rest);
            } else {
                final int start = (refreshIndex * 2) % rest.size();
                topics.add(rest.get(start));
                topics.add(rest.get((start + 1) % rest.size()));
            }
        }
        refreshIndex++;
        return new Plan(topics, watchedUrls);
    }

    /** A search interest is dropped if it is a hidden channel or appears in a hidden title. */
    private static boolean isBlockedTopic(final String text, final Set<String> blockedNames,
                                          final List<String> blockedTitles) {
        final String lower = text.toLowerCase(Locale.ROOT);
        if (blockedNames.contains(lower)) {
            return true;
        }
        if (lower.length() >= 3) {
            for (final String title : blockedTitles) {
                if (title.contains(lower)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void add(final Map<String, Double> scores, final Map<String, String> display,
                            final String text, final double score) {
        final String key = text.toLowerCase(Locale.ROOT);
        scores.merge(key, score, Double::sum);
        display.putIfAbsent(key, text);
    }

    @SuppressWarnings("unchecked")
    private static Single<Feed> fetch(final Context context, final int serviceId,
                                      final List<String> filter, final Plan plan) {
        final List<Single<Optional<SearchInfo>>> calls = new ArrayList<>();
        for (final String topic : plan.topics) {
            calls.add(ExtractorHelper.searchFor(serviceId, topic, filter, "")
                    .map(Optional::of)
                    .onErrorReturnItem(Optional.empty()));
        }
        return Single.zip(calls, results -> {
            SearchInfo base = null;
            String baseTopic = null;
            final List<List<InfoItem>> lists = new ArrayList<>();
            for (int i = 0; i < results.length; i++) {
                final Optional<SearchInfo> result = (Optional<SearchInfo>) results[i];
                if (!result.isPresent()) {
                    continue;
                }
                if (base == null) {
                    base = result.get();
                    baseTopic = plan.topics.get(i);
                }
                final List<InfoItem> usable = new ArrayList<>();
                for (final InfoItem item : result.get().getRelatedItems()) {
                    if (item instanceof StreamInfoItem
                            && !plan.watchedUrls.contains(item.getUrl())
                            && !NotInterestedHelper.isHidden(context, item)
                            && usable.size() < ITEMS_PER_TOPIC) {
                        usable.add(item);
                    }
                }
                lists.add(usable);
            }
            if (base == null) {
                return new Feed(null, null);
            }
            // mix the topics one video at a time, skipping duplicates
            final List<InfoItem> merged = new ArrayList<>();
            final Set<String> seen = new HashSet<>();
            for (int round = 0; round < ITEMS_PER_TOPIC && merged.size() < MAX_ITEMS; round++) {
                for (final List<InfoItem> list : lists) {
                    if (round < list.size() && seen.add(list.get(round).getUrl())
                            && merged.size() < MAX_ITEMS) {
                        merged.add(list.get(round));
                    }
                }
            }
            if (!merged.isEmpty()) {
                base.setRelatedItems(merged);
            }
            return new Feed(base, baseTopic);
        }).flatMap(feed -> feed.info != null
                ? Single.just(feed)
                : ExtractorHelper.searchFor(serviceId, FALLBACK_QUERY, filter, "")
                        .map(info -> new Feed(info, FALLBACK_QUERY)));
    }
}
