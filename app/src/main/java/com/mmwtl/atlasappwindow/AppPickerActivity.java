package com.mmwtl.atlasappwindow;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppPickerActivity extends ScaledActivity {
    static final String EXTRA_COMPONENT = "component";
    static final String EXTRA_LABEL = "label";

    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private ListView list;
    private ProgressBar progress;
    private TextView summary;
    private AppAdapter adapter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        View content = buildContent();
        setContentView(content);
        Ui.applySystemBarInsets(content);
        load();
    }

    @Override protected void onDestroy() {
        loader.shutdownNow();
        super.onDestroy();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 18),
                Ui.dp(this, 20), Ui.dp(this, 12));
        root.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button back = Ui.button(this, "Назад");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back);
        TextView title = Ui.heading(this, "Выбор приложения", 24);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = Ui.dp(this, 16);
        toolbar.addView(title, titleParams);
        root.addView(toolbar);

        EditText search = new EditText(this);
        search.setHint("Название, пакет или Activity");
        search.setHintTextColor(Ui.SECONDARY);
        search.setTextColor(Ui.PRIMARY);
        search.setSingleLine(true);
        search.setPadding(Ui.dp(this, 16), Ui.dp(this, 12),
                Ui.dp(this, 16), Ui.dp(this, 12));
        search.setBackground(Ui.background(Ui.CARD, 8, this));
        root.addView(search, Ui.fullWrap());
        Ui.topMargin(search, this, 16);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) adapter.setQuery(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        summary = Ui.text(this, "Загрузка приложений…", 13, Ui.SECONDARY);
        root.addView(summary, Ui.fullWrap());
        Ui.topMargin(summary, this, 10);

        FrameLayout frame = new FrameLayout(this);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        frameParams.topMargin = Ui.dp(this, 8);
        root.addView(frame, frameParams);
        list = new ListView(this);
        list.setDividerHeight(Ui.dp(this, 6));
        frame.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        frame.addView(progress, progressParams);
        return root;
    }

    private void load() {
        loader.execute(() -> {
            List<AppEntry> entries = AppRepository.loadLaunchable(this);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                adapter = new AppAdapter(entries);
                list.setAdapter(adapter);
                adapter.refresh();
            });
        });
    }

    private void choose(AppEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle(entry.label)
                .setMessage(entry.componentKey)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Добавить", (dialog, which) -> {
                    Intent result = new Intent()
                            .putExtra(EXTRA_COMPONENT, entry.componentKey)
                            .putExtra(EXTRA_LABEL, entry.label);
                    setResult(RESULT_OK, result);
                    finish();
                }).show();
    }

    private final class AppAdapter extends BaseAdapter {
        private final List<AppEntry> all;
        private final List<AppEntry> visible = new ArrayList<>();
        private String query = "";

        AppAdapter(List<AppEntry> entries) { all = entries; }

        void setQuery(String value) {
            query = value == null ? "" : value.trim().toLowerCase(Locale.getDefault());
            refresh();
        }

        void refresh() {
            visible.clear();
            for (AppEntry entry : all) if (query.isEmpty() || entry.searchText.contains(query)) {
                visible.add(entry);
            }
            summary.setText("Найдено: " + visible.size());
            notifyDataSetChanged();
        }

        @Override public int getCount() { return visible.size(); }
        @Override public AppEntry getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            AppEntry entry = getItem(position);
            LinearLayout row = new LinearLayout(AppPickerActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(AppPickerActivity.this, 14), Ui.dp(AppPickerActivity.this, 10),
                    Ui.dp(AppPickerActivity.this, 14), Ui.dp(AppPickerActivity.this, 10));
            row.setBackground(Ui.background(Ui.CARD, 8, AppPickerActivity.this));
            ImageView icon = new ImageView(AppPickerActivity.this);
            try { icon.setImageDrawable(getPackageManager().getActivityIcon(entry.component)); }
            catch (Exception ignored) { icon.setImageResource(R.drawable.ic_launcher_foreground); }
            row.addView(icon, new LinearLayout.LayoutParams(
                    Ui.dp(AppPickerActivity.this, 46), Ui.dp(AppPickerActivity.this, 46)));
            LinearLayout labels = new LinearLayout(AppPickerActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelsParams.leftMargin = Ui.dp(AppPickerActivity.this, 14);
            row.addView(labels, labelsParams);
            labels.addView(Ui.heading(AppPickerActivity.this, entry.label, 16));
            labels.addView(Ui.text(AppPickerActivity.this, entry.componentKey, 11, Ui.SECONDARY));
            row.setOnClickListener(v -> choose(entry));
            return row;
        }
    }
}
