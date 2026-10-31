package org.schabi.newpipe.fragments.list.videos;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.RelatedItemsHeaderBinding;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.info_list.dialog.InfoItemDialog;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.image.CoilHelper;

import java.io.Serializable;
import java.util.function.Supplier;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RelatedItemsFragment extends BaseListInfoFragment<InfoItem, RelatedItemsInfo>
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String INFO_KEY = "related_info_key";

    private RelatedItemsInfo relatedItemsInfo;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private RelatedItemsHeaderBinding headerBinding;
    private final CompositeDisposable commentsDisposables = new CompositeDisposable();

    public static RelatedItemsFragment getInstance(final StreamInfo info) {
        final RelatedItemsFragment instance = new RelatedItemsFragment();
        instance.setInitialData(info);
        return instance;
    }

    public RelatedItemsFragment() {
        super(UserAction.REQUESTED_STREAM);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_related_items, container, false);
    }

    @Override
    public void onDestroyView() {
        commentsDisposables.clear();
        headerBinding = null;
        super.onDestroyView();
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        if (relatedItemsInfo == null || relatedItemsInfo.getRelatedItems() == null) {
            return null;
        }

        headerBinding = RelatedItemsHeaderBinding
                .inflate(activity.getLayoutInflater(), itemsList, false);

        final SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(requireContext());
        final boolean autoplay = pref.getBoolean(getString(R.string.auto_queue_key), false);
        headerBinding.autoplaySwitch.setChecked(autoplay);
        headerBinding.autoplaySwitch.setOnCheckedChangeListener((compoundButton, b) ->
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                        .putBoolean(getString(R.string.auto_queue_key), b).apply());

        loadCommentsPreview();
        return headerBinding::getRoot;
    }

    /** Shows the first comment above the recommended videos; tapping opens all comments. */
    private void loadCommentsPreview() {
        final RelatedItemsHeaderBinding binding = headerBinding;
        if (binding == null || !PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(R.string.show_comments_key), true)) {
            return;
        }
        commentsDisposables.add(ExtractorHelper.getCommentsInfo(serviceId, url, false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    if (headerBinding != binding || info.isCommentsDisabled()
                            || info.getRelatedItems().isEmpty()) {
                        return;
                    }
                    final CommentsInfoItem first = info.getRelatedItems().get(0);
                    final Description description = first.getCommentText();
                    final String content = description == null ? "" : description.content();
                    final CharSequence text = description != null
                            && description.type() == Description.Type.HTML
                            ? HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT)
                            : content;
                    binding.commentsPreviewText.setText(text);
                    CoilHelper.INSTANCE.loadAvatar(binding.commentsPreviewAvatar,
                            first.getUploaderAvatars());
                    binding.commentsPreviewCard.setVisibility(View.VISIBLE);
                    binding.commentsPreviewCard.setOnClickListener(v -> {
                        final Fragment parent = getParentFragment();
                        if (parent instanceof VideoDetailFragment) {
                            ((VideoDetailFragment) parent).showCommentsTab();
                        }
                    });
                }, throwable -> { /* the preview is optional: ignore errors */ }));
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<InfoItem>> loadMoreItemsLogic() {
        return Single.fromCallable(ListExtractor.InfoItemsPage::emptyPage);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<RelatedItemsInfo> loadResult(final boolean forceLoad) {
        return Single.fromCallable(() -> relatedItemsInfo);
    }

    @Override
    public void showLoading() {
        super.showLoading();
        if (headerBinding != null) {
            headerBinding.getRoot().setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void handleResult(@NonNull final RelatedItemsInfo result) {
        super.handleResult(result);

        if (headerBinding != null) {
            headerBinding.getRoot().setVisibility(View.VISIBLE);
        }
        ViewUtils.slideUp(requireView(), 120, 96, 0.06f);

    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setTitle(final String title) {
        // Nothing to do - override parent
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        // Nothing to do - override parent
    }

    private void setInitialData(final StreamInfo info) {
        super.setInitialData(info.getServiceId(), info.getUrl(), info.getName());
        if (this.relatedItemsInfo == null) {
            this.relatedItemsInfo = new RelatedItemsInfo(info);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(INFO_KEY, relatedItemsInfo);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedState) {
        super.onRestoreInstanceState(savedState);
        final Serializable serializable = savedState.getSerializable(INFO_KEY);
        if (serializable instanceof RelatedItemsInfo) {
            this.relatedItemsInfo = (RelatedItemsInfo) serializable;
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        if (headerBinding != null && getString(R.string.auto_queue_key).equals(key)) {
            headerBinding.autoplaySwitch.setChecked(sharedPreferences.getBoolean(key, false));
        }
    }

    @Override
    protected ItemViewMode getItemViewMode() {
        ItemViewMode mode = super.getItemViewMode();
        // Only list mode is supported. Either List or card will be used.
        if (mode != ItemViewMode.LIST && mode != ItemViewMode.CARD) {
            mode = ItemViewMode.LIST;
        }
        return mode;
    }

    @Override
    protected void showInfoItemDialog(final StreamInfoItem item) {
        // Try and attach the InfoItemDialog to the parent fragment of the RelatedItemsFragment
        // so that its context is not lost when the RelatedItemsFragment is reinitialized,
        // e.g. when a new stream is loaded in a parent VideoDetailFragment.
        final Fragment parentFragment = getParentFragment();
        if (parentFragment != null) {
            try {
                new InfoItemDialog.Builder(
                        parentFragment.getActivity(),
                        parentFragment.getContext(),
                        parentFragment,
                        item
                ).create().show();
            } catch (final IllegalArgumentException e) {
                InfoItemDialog.Builder.reportErrorDuringInitialization(e, item);
            }
        } else {
            super.showInfoItemDialog(item);
        }
    }

}
