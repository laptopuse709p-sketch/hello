package org.schabi.newpipe.util;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Loads the stream info of the first few videos in the background, so that opening one of
 * them is instant (the result lands in the info cache). Errors are ignored.
 */
public final class StreamPrefetcher {
    private StreamPrefetcher() { }

    public static void prefetch(final int serviceId,
                                @NonNull final List<? extends InfoItem> items,
                                final int max) {
        int started = 0;
        for (final InfoItem item : items) {
            if (started >= max) {
                break;
            }
            if (item instanceof StreamInfoItem
                    && !ExtractorHelper.isCached(serviceId, item.getUrl(),
                            InfoCache.Type.STREAM)) {
                started++;
                // not forced, so the result is stored in the cache
                ExtractorHelper.getStreamInfo(serviceId, item.getUrl(), false)
                        .subscribeOn(Schedulers.io())
                        .subscribe(info -> { }, throwable -> { });
            }
        }
    }
}
