package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.fragments.list.topic.TopicFragment;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;

/**
 * The home screen: a row of topic chips on top and the matching video feed below.
 */
public class HomeFragment extends BaseFragment {
    private static final String STATE_SELECTED_TOPIC = "home_selected_topic";
    private static final String APP_TITLE = "Pipe";

    private static final int[] TOPIC_LABELS = {
            R.string.topic_all,
            R.string.topic_ai,
            R.string.topic_technology,
            R.string.topic_phonk,
    };
    private static final String[] TOPIC_QUERIES = {
            "AI technology phonk",
            "AI",
            "technology",
            "phonk",
    };

    private LinearLayout chipsContainer;
    private int selectedTopic = 0;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            selectedTopic = savedInstanceState.getInt(STATE_SELECTED_TOPIC, 0);
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        setTitle(APP_TITLE);

        chipsContainer = rootView.findViewById(R.id.topic_chips);
        chipsContainer.removeAllViews();
        for (int i = 0; i < TOPIC_LABELS.length; i++) {
            chipsContainer.addView(createChip(i));
        }
        updateChipSelection();

        if (getChildFragmentManager().findFragmentById(R.id.topic_holder) == null) {
            showTopic(selectedTopic);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_TOPIC, selectedTopic);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        chipsContainer = null;
    }

    private View createChip(final int index) {
        final TextView chip = new TextView(requireContext());
        chip.setText(TOPIC_LABELS[index]);
        chip.setTextSize(14);
        chip.setBackgroundResource(R.drawable.bg_topic_chip);
        chip.setBackgroundTintList(requireContext()
                .getColorStateList(R.color.topic_chip_bg));
        chip.setTextColor(requireContext().getColorStateList(R.color.topic_chip_text));
        final int h = (int) (14 * getResources().getDisplayMetrics().density);
        final int v = (int) (7 * getResources().getDisplayMetrics().density);
        chip.setPadding(h, v, h, v);
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
        chip.setLayoutParams(params);
        chip.setOnClickListener(view -> {
            if (selectedTopic != index) {
                selectedTopic = index;
                updateChipSelection();
                showTopic(index);
            }
        });
        return chip;
    }

    private void updateChipSelection() {
        if (chipsContainer == null) {
            return;
        }
        for (int i = 0; i < chipsContainer.getChildCount(); i++) {
            chipsContainer.getChildAt(i).setSelected(i == selectedTopic);
        }
    }

    private void showTopic(final int index) {
        final TopicFragment fragment = TopicFragment.newInstance(
                ServiceHelper.getSelectedServiceId(requireContext()), TOPIC_QUERIES[index]);
        fragment.useAsFrontPage(true);
        getChildFragmentManager().beginTransaction()
                .replace(R.id.topic_holder, fragment)
                .commit();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_main_fragment, menu);

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            try {
                NavigationHelper.openSearchFragment(getFM(),
                        ServiceHelper.getSelectedServiceId(activity), "");
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening search fragment", e);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
