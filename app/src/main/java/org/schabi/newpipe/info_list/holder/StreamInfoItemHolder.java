package org.schabi.newpipe.info_list.holder;

import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.Localization;

/*
 * Created by Christian Schabesberger on 01.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemHolder.java is part of NewPipe.
 * </p>
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p?
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe. If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class StreamInfoItemHolder extends StreamMiniInfoItemHolder {
    public final TextView itemAdditionalDetails;
    // only present in the card layout (null in the other item layouts)
    private final android.widget.ImageView itemUploaderAvatar;
    private final android.view.View itemMoreOptions;

    public StreamInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_item, parent);
    }

    public StreamInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                                final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);
        itemAdditionalDetails = itemView.findViewById(R.id.itemAdditionalDetails);
        itemUploaderAvatar = itemView.findViewById(R.id.itemUploaderAvatar);
        itemMoreOptions = itemView.findViewById(R.id.itemMoreOptions);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        super.updateFromItem(infoItem, historyRecordManager);

        if (!(infoItem instanceof StreamInfoItem)) {
            return;
        }
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        itemAdditionalDetails.setText(getStreamInfoDetailLine(item));

        if (itemUploaderAvatar != null) {
            org.schabi.newpipe.util.image.CoilHelper.INSTANCE.loadAvatar(itemUploaderAvatar,
                    item.getUploaderAvatars());
        }
        if (itemMoreOptions != null) {
            itemMoreOptions.setOnClickListener(v -> {
                final androidx.appcompat.widget.PopupMenu popup =
                        new androidx.appcompat.widget.PopupMenu(v.getContext(), v);
                popup.getMenu().add(0, 1, 0, R.string.not_interested);
                popup.getMenu().add(0, 2, 1, R.string.more_options);
                popup.setOnMenuItemClickListener(menuItem -> {
                    if (menuItem.getItemId() == 1) {
                        markNotInterested(item);
                    } else if (itemBuilder.getOnStreamSelectedListener() != null) {
                        // the same menu as a long press
                        itemBuilder.getOnStreamSelectedListener().held(item);
                    }
                    return true;
                });
                popup.show();
            });
        }
    }
            });
        }
    }

    private String getStreamInfoDetailLine(final StreamInfoItem infoItem) {
        String viewsAndDate = "";
        if (infoItem.getViewCount() >= 0) {
            if (infoItem.getStreamType().equals(StreamType.AUDIO_LIVE_STREAM)) {
                viewsAndDate = Localization
                        .listeningCount(itemBuilder.getContext(), infoItem.getViewCount());
            } else if (infoItem.getStreamType().equals(StreamType.LIVE_STREAM)) {
                viewsAndDate = Localization
                        .shortWatchingCount(itemBuilder.getContext(), infoItem.getViewCount());
            } else {
                viewsAndDate = Localization
                        .shortViewCount(itemBuilder.getContext(), infoItem.getViewCount());
            }
        }

        final String uploadDate = Localization.relativeTimeOrTextual(itemBuilder.getContext(),
                infoItem.getUploadDate(),
                infoItem.getTextualUploadDate());
        if (!TextUtils.isEmpty(uploadDate)) {
            if (viewsAndDate.isEmpty()) {
                return uploadDate;
            }

            return Localization.concatenateStrings(viewsAndDate, uploadDate);
        }

        return viewsAndDate;
    }

    /** Hides this video and its channel from the list, with an Undo message. */
    private void markNotInterested(final StreamInfoItem item) {
        final android.content.Context context = itemView.getContext();
        final androidx.recyclerview.widget.RecyclerView.Adapter<?> adapter = getBindingAdapter();
        com.google.android.material.snackbar.Snackbar snackbar =
                com.google.android.material.snackbar.Snackbar.make(itemView,
                        R.string.not_interested_done,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG);
        org.schabi.newpipe.util.NotInterestedHelper.add(context, item);
        if (adapter instanceof org.schabi.newpipe.info_list.InfoListAdapter) {
            final org.schabi.newpipe.info_list.InfoListAdapter listAdapter =
                    (org.schabi.newpipe.info_list.InfoListAdapter) adapter;
            final java.util.List<org.schabi.newpipe.extractor.InfoItem> before =
                    new java.util.ArrayList<>(listAdapter.getItemsList());
            snackbar = snackbar.setAction(R.string.undo, v -> {
                org.schabi.newpipe.util.NotInterestedHelper.remove(context, item);
                listAdapter.getItemsList().clear();
                listAdapter.getItemsList().addAll(before);
                listAdapter.notifyDataSetChanged();
            });
            listAdapter.getItemsList().removeIf(other ->
                    org.schabi.newpipe.util.NotInterestedHelper.isHidden(context, other));
            listAdapter.notifyDataSetChanged();
        }
        snackbar.show();
    }
}
