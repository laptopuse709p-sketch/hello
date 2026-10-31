package org.schabi.newpipe.fragments.list.topic;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.InterestFeedHelper;

import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.core.Single;

/**
 * A feed of videos for one topic. It shows the search results for the topic's query,
 * so the content follows the chosen topic and is not a random trending list.
 */
public class TopicFragment extends BaseListInfoFragment<InfoItem, SearchInfo> {
    private static final List<String> VIDEO_FILTER = Collections.singletonList("videos");
    private static final String APP_TITLE = "Pipe";

    /** Special query: build the feed from the user's search and watch history. */
    public static final String FOR_YOU = "@for_you";

    @State
    String query = "";
    /** The real search text in use (differs from {@link #query} in For You mode). */
    @State
    String activeQuery = "";

    public static TopicFragment newInstance(final int serviceId, final String topicQuery) {
        final TopicFragment instance = new TopicFragment();
        instance.query = topicQuery;
        instance.setInitialData(serviceId, "topic:" + topicQuery, APP_TITLE);
        return instance;
    }

    public TopicFragment() {
        super(UserAction.SEARCHED);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kiosk, container, false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        // The parent home screen owns the toolbar menu (search), so add nothing here.
    }

    @Override
    public Single<SearchInfo> loadResult(final boolean forceReload) {
        if (FOR_YOU.equals(query)) {
            // a fresh interest every time the feed loads or is pulled to refresh
            return InterestFeedHelper.pickQuery(requireContext())
                    .flatMap(picked -> {
                        activeQuery = picked;
                        return ExtractorHelper.searchFor(serviceId, picked, VIDEO_FILTER, "");
                    });
        }
        activeQuery = query;
        return ExtractorHelper.searchFor(serviceId, query, VIDEO_FILTER, "");
    }

    @Override
    public Single<ListExtractor.InfoItemsPage<InfoItem>> loadMoreItemsLogic() {
        final String moreQuery = activeQuery == null || activeQuery.isEmpty()
                ? (FOR_YOU.equals(query) ? InterestFeedHelper.FALLBACK_QUERY : query)
                : activeQuery;
        return ExtractorHelper.getMoreSearchItems(serviceId, moreQuery, VIDEO_FILTER, "",
                currentNextPage);
    }

    @Override
    public void handleResult(@NonNull final SearchInfo result) {
        super.handleResult(result);
        name = APP_TITLE;
        setTitle(APP_TITLE);
    }
}
