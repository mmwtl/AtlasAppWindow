package com.mmwtl.atlasappwindow;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/** Settings and preset launcher for the Android 11 freeform-task implementation. */
public final class MainActivity extends ScaledActivity {
    private static final int REQUEST_PICK_APP = 4001;
    private static final int MIN_WINDOW_SIZE_PX = 220;
    private static final long STATUS_REFRESH_MS = 750L;

    private static final int SIDE_LEFT = 0;
    private static final int SIDE_TOP = 1;
    private static final int SIDE_RIGHT = 2;
    private static final int SIDE_BOTTOM = 3;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable statusRefresh = new Runnable() {
        @Override public void run() {
            refreshStatus();
            main.postDelayed(this, STATUS_REFRESH_MS);
        }
    };

    private Prefs prefs;
    private TextView backendState;
    private TextView permissionState;
    private Button stopButton;
    private Button addPresetButton;
    private LinearLayout presetList;
    private WindowPreviewView preview;
    private TextView windowSize;
    private final SeekBar[] marginBars = new SeekBar[4];
    private final TextView[] marginValues = new TextView[4];
    private int displayWidth;
    private int displayHeight;
    private int leftMargin;
    private int topMargin;
    private int rightMargin;
    private int bottomMargin;
    private int selectedUiScale;
    private boolean resumed;
    private boolean geometryUpdating;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        Rect display = getSystemService(WindowManager.class).getCurrentWindowMetrics().getBounds();
        displayWidth = Math.max(1, display.width());
        displayHeight = Math.max(1, display.height());
        readMargins(prefs.bounds().clampTo(displayWidth, displayHeight));

        View content = buildContent();
        setContentView(content);
        Ui.applySystemBarInsets(content);
        renderPresets();
        refreshPermissions();
        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        refreshPermissions();
        renderPresets();
        main.removeCallbacks(statusRefresh);
        main.post(statusRefresh);
    }

    @Override protected void onPause() {
        resumed = false;
        main.removeCallbacks(statusRefresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_APP || resultCode != RESULT_OK || data == null) return;
        String component = data.getStringExtra(AppPickerActivity.EXTRA_COMPONENT);
        String label = data.getStringExtra(AppPickerActivity.EXTRA_LABEL);
        try {
            Preset preset = prefs.savePreset(
                    new Preset(Preset.idFor(label, component), component, label));
            renderPresets();
            Toast.makeText(this, "Добавлен пресет " + preset.slot + " — «" + preset.label + "»",
                    Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException error) {
            Toast.makeText(this, "Доступно не более " + Preset.MAX_COUNT + " пресетов",
                    Toast.LENGTH_LONG).show();
        } catch (RuntimeException error) {
            AppLog.warn("Picker returned an invalid component", error);
            Toast.makeText(this, "Приложение не добавлено: некорректный компонент",
                    Toast.LENGTH_LONG).show();
        }
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 22),
                Ui.dp(this, 24), Ui.dp(this, 28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.heading(this, "Atlas App Window", 28), Ui.fullWrap());
        TextView intro = Ui.text(this,
                "Открывает выбранное приложение как отдельную свободную задачу Android в заданных "
                        + "границах. Это практическая freeform-аппроксимация, а не настоящее "
                        + "встраивание чужого приложения в виджет. Область HOME снаружи окна "
                        + "остаётся доступной для касаний.",
                14, Ui.SECONDARY);
        intro.setLineSpacing(0f, 1.12f);
        root.addView(intro, Ui.fullWrap());
        Ui.topMargin(intro, this, 8);

        root.addView(buildStatusCard());
        root.addView(buildAdbCard());
        root.addView(buildPresetCard());
        root.addView(buildGeometryCard());
        root.addView(buildChromeCard());
        root.addView(buildBehaviorCard());
        return scroll;
    }

    private View buildStatusCard() {
        LinearLayout card = Ui.card(this);
        Ui.topMargin(card, this, 18);
        card.addView(Ui.heading(this, "Состояние и доступы", 20));

        backendState = Ui.text(this, "Проверка ещё не выполнялась", 14, Ui.SECONDARY);
        backendState.setLineSpacing(0f, 1.1f);
        card.addView(backendState, Ui.fullWrap());
        Ui.topMargin(backendState, this, 10);

        permissionState = Ui.text(this, "", 13, Ui.SECONDARY);
        card.addView(permissionState, Ui.fullWrap());
        Ui.topMargin(permissionState, this, 8);

        Button overlay = Ui.button(this, "Разрешить оформление поверх окон");
        overlay.setOnClickListener(v -> openAppSpecificSettings(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "настройки overlay"));
        card.addView(overlay, Ui.fullWrap());
        Ui.topMargin(overlay, this, 12);

        Button usage = Ui.button(this, "Открыть доступ к статистике использования");
        usage.setOnClickListener(v -> openAppSpecificSettings(
                Settings.ACTION_USAGE_ACCESS_SETTINGS, "Usage access"));
        card.addView(usage, Ui.fullWrap());
        Ui.topMargin(usage, this, 8);

        stopButton = Ui.button(this, "Остановить Atlas App Window");
        stopButton.setOnClickListener(v -> OverlayService.stop(this));
        card.addView(stopButton, Ui.fullWrap());
        Ui.topMargin(stopButton, this, 10);
        return card;
    }

    private View buildAdbCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, "ADB подключение", 20));

        LinearLayout helperRow = new LinearLayout(this);
        helperRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout helperCopy = new LinearLayout(this);
        helperCopy.setOrientation(LinearLayout.VERTICAL);
        TextView helperTitle = Ui.heading(this, "ADB helper", 15);
        helperCopy.addView(helperTitle, Ui.fullWrap());
        TextView helperDescription = Ui.text(this,
                "Опциональное подключение к локальному adbd", 13, Ui.SECONDARY);
        helperCopy.addView(helperDescription, Ui.fullWrap());
        Ui.topMargin(helperDescription, this, 2);
        helperRow.addView(helperCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView endpointBadge = Ui.text(this, "127.0.0.1", 12, Ui.ACCENT);
        endpointBadge.setGravity(Gravity.CENTER);
        endpointBadge.setPadding(Ui.dp(this, 12), Ui.dp(this, 7),
                Ui.dp(this, 12), Ui.dp(this, 7));
        endpointBadge.setBackground(Ui.background(Ui.NESTED, 8, this));
        helperRow.addView(endpointBadge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(helperRow, Ui.fullWrap());
        Ui.topMargin(helperRow, this, 12);

        EditText port = adbField(
                String.valueOf(prefs.getInt(Prefs.KEY_ADB_PORT, Prefs.DEFAULT_ADB_PORT)),
                InputType.TYPE_CLASS_NUMBER);
        port.setHint("5555");
        port.setSelectAllOnFocus(true);
        LinearLayout portBox = outlinedField("Порт ADB", port);
        card.addView(portBox, Ui.fullWrap());
        Ui.topMargin(portBox, this, 14);

        Button probe = Ui.primaryButton(this, "Сохранить и проверить");
        probe.setOnClickListener(v -> savePortAndProbe(port));
        card.addView(probe, Ui.fullWrap());
        Ui.topMargin(probe, this, 12);

        Button developer = Ui.outlinedButton(this, "Открыть настройки разработчика");
        developer.setOnClickListener(v -> openSettings(
                new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                "настройки разработчика"));
        card.addView(developer, Ui.fullWrap());
        Ui.topMargin(developer, this, 8);

        TextView adbHint = Ui.text(this,
                "Локальный ADB не нужен для самого пробного запуска. Он опционален и нужен для "
                        + "точного поиска, resize и удаления уже запущенной задачи. Нужен legacy "
                        + "ADB endpoint на 127.0.0.1 (например, от OEM/helper или после внешней "
                        + "команды adb tcpip 5555). Стандартный Android 11 Wireless Debugging "
                        + "с TLS pairing библиотека Atlas App Window сама не настраивает.",
                12, Ui.SECONDARY);
        adbHint.setLineSpacing(0f, 1.1f);
        card.addView(adbHint, Ui.fullWrap());
        Ui.topMargin(adbHint, this, 14);
        return card;
    }

    private EditText adbField(String value, int inputType) {
        EditText field = new EditText(this);
        field.setText(value);
        field.setTextColor(Ui.PRIMARY);
        field.setHintTextColor(Ui.SECONDARY);
        field.setTextSize(16);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 3));
        field.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        return field;
    }

    private LinearLayout outlinedField(String label, EditText field) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 14), Ui.dp(this, 8),
                Ui.dp(this, 14), Ui.dp(this, 7));
        box.setBackground(Ui.stroked(android.graphics.Color.TRANSPARENT,
                8, Ui.OUTLINE, 1, this));
        TextView labelView = Ui.text(this, label, 12, Ui.SECONDARY);
        box.addView(labelView, Ui.fullWrap());
        box.addView(field, Ui.fullWrap());
        return box;
    }

    private View buildPresetCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, "Приложения", 20));
        TextView note = Ui.text(this,
                "Доступно пять постоянных слотов. Для каждого занятого слота Atlas включает "
                        + "отдельную обычную иконку лаунчера «Atlas: Пресет N». Это не shortcut "
                        + "и работает на лаунчерах без поддержки ярлыков. Также доступен явный "
                        + "intent " + CommandContract.ACTION_SHOW + ".",
                13, Ui.SECONDARY);
        note.setLineSpacing(0f, 1.08f);
        card.addView(note, Ui.fullWrap());
        Ui.topMargin(note, this, 8);

        addPresetButton = Ui.button(this, "+ Добавить приложение");
        addPresetButton.setOnClickListener(v -> pickApplication());
        card.addView(addPresetButton, Ui.fullWrap());
        Ui.topMargin(addPresetButton, this, 12);

        presetList = new LinearLayout(this);
        presetList.setOrientation(LinearLayout.VERTICAL);
        card.addView(presetList, Ui.fullWrap());
        Ui.topMargin(presetList, this, 8);
        return card;
    }

    private View buildGeometryCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, "Границы окна", 20));
        TextView note = Ui.text(this,
                "Четыре значения — отступы от краёв экрана в физических пикселях. "
                        + "Минимальный размер окна: " + MIN_WINDOW_SIZE_PX + " × "
                        + MIN_WINDOW_SIZE_PX + " px.",
                13, Ui.SECONDARY);
        card.addView(note, Ui.fullWrap());
        Ui.topMargin(note, this, 8);

        preview = new WindowPreviewView(this);
        preview.setChromeStyle(prefs.chromeStyle());
        preview.setBackground(Ui.background(Ui.NESTED, 8, this));
        card.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 330)));
        Ui.topMargin(preview, this, 14);

        int horizontalMaximum = Math.max(0,
                displayWidth - Math.min(MIN_WINDOW_SIZE_PX, displayWidth));
        int verticalMaximum = Math.max(0,
                displayHeight - Math.min(MIN_WINDOW_SIZE_PX, displayHeight));
        addMarginControl(card, SIDE_LEFT, "Слева", horizontalMaximum, leftMargin);
        addMarginControl(card, SIDE_TOP, "Сверху", verticalMaximum, topMargin);
        addMarginControl(card, SIDE_RIGHT, "Справа", horizontalMaximum, rightMargin);
        addMarginControl(card, SIDE_BOTTOM, "Снизу", verticalMaximum, bottomMargin);

        windowSize = Ui.heading(this, "", 15);
        card.addView(windowSize, Ui.fullWrap());
        Ui.topMargin(windowSize, this, 10);

        Button apply = Ui.button(this, "Сохранить и применить к активному окну");
        apply.setOnClickListener(v -> saveAndApplyBounds());
        card.addView(apply, Ui.fullWrap());
        Ui.topMargin(apply, this, 10);
        updateGeometryUi();
        return card;
    }

    @SuppressWarnings("deprecation")
    private View buildBehaviorCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, "Поведение и интерфейс", 20));

        Switch autoStart = new Switch(this);
        autoStart.setText("Автозапуск сервиса после загрузки ГУ");
        autoStart.setTextColor(Ui.PRIMARY);
        autoStart.setTextSize(14);
        autoStart.setChecked(prefs.getBoolean(Prefs.KEY_AUTO_START, false));
        autoStart.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        autoStart.setOnCheckedChangeListener((button, checked) -> {
            prefs.putBoolean(Prefs.KEY_AUTO_START, checked);
            Toast.makeText(this,
                    checked ? "Автозапуск включён" : "Автозапуск выключен",
                    Toast.LENGTH_SHORT).show();
        });
        card.addView(autoStart, Ui.fullWrap());

        TextView scaleTitle = Ui.heading(this, "Масштаб настроек", 16);
        card.addView(scaleTitle, Ui.fullWrap());
        Ui.topMargin(scaleTitle, this, 14);
        selectedUiScale = ScaledActivity.configuredScaleTenths(this);
        TextView scaleValue = Ui.text(this, formatScale(selectedUiScale), 13, Ui.SECONDARY);
        card.addView(scaleValue, Ui.fullWrap());

        SeekBar scale = new SeekBar(this);
        scale.setMax(ScaledActivity.MAX_SCALE_TENTHS - ScaledActivity.MIN_SCALE_TENTHS);
        scale.setProgress(selectedUiScale - ScaledActivity.MIN_SCALE_TENTHS);
        scale.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                selectedUiScale = ScaledActivity.MIN_SCALE_TENTHS + progress;
                scaleValue.setText(formatScale(selectedUiScale));
            }
        });
        card.addView(scale, Ui.fullWrap());

        Button applyScale = Ui.button(this, "Применить масштаб");
        applyScale.setOnClickListener(v -> {
            int current = ScaledActivity.configuredScaleTenths(this);
            if (current == selectedUiScale) {
                Toast.makeText(this, "Масштаб уже применён", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.putInt(Prefs.KEY_UI_SCALE, selectedUiScale);
            recreate();
        });
        card.addView(applyScale, Ui.fullWrap());
        Ui.topMargin(applyScale, this, 8);
        return card;
    }

    @SuppressWarnings("deprecation")
    private View buildChromeCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, "Оформление окна", 20));
        TextView note = Ui.text(this,
                "Шапка примыкает к freeform-задаче без рамки. Если выключить шапку, кнопки "
                        + "управления станут плавающей группой справа над окном. Скругление "
                        + "визуальное: углы закрывает нетактильный overlay Atlas.",
                13, Ui.SECONDARY);
        note.setLineSpacing(0f, 1.08f);
        card.addView(note, Ui.fullWrap());
        Ui.topMargin(note, this, 8);

        ChromeStyle current = prefs.chromeStyle();
        Switch header = chromeSwitch("Показывать серую шапку", current.headerVisible);
        Switch title = chromeSwitch("Показывать название приложения", current.titleVisible);
        Switch controls = chromeSwitch("Показывать кнопки управления", current.controlsVisible);
        title.setEnabled(current.headerVisible);

        card.addView(header, Ui.fullWrap());
        Ui.topMargin(header, this, 10);
        card.addView(title, Ui.fullWrap());
        card.addView(controls, Ui.fullWrap());

        TextView heightTitle = Ui.heading(this,
                "Высота шапки: " + current.headerHeightDp + " dp", 16);
        card.addView(heightTitle, Ui.fullWrap());
        Ui.topMargin(heightTitle, this, 12);

        SeekBar height = new SeekBar(this);
        height.setMax(ChromeStyle.MAX_HEADER_HEIGHT_DP - ChromeStyle.MIN_HEADER_HEIGHT_DP);
        height.setProgress(current.headerHeightDp - ChromeStyle.MIN_HEADER_HEIGHT_DP);
        height.setEnabled(current.headerVisible);
        height.setOnSeekBarChangeListener(new SimpleSeekListener() {
            private int selected = current.headerHeightDp;

            @Override public void onProgressChanged(
                    SeekBar seekBar, int progress, boolean fromUser) {
                selected = ChromeStyle.MIN_HEADER_HEIGHT_DP + progress;
                heightTitle.setText("Высота шапки: " + selected + " dp");
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.putInt(Prefs.KEY_HEADER_HEIGHT, selected);
                applyChromeSettings();
            }
        });
        card.addView(height, Ui.fullWrap());

        header.setOnCheckedChangeListener((button, checked) -> {
            prefs.putBoolean(Prefs.KEY_HEADER_VISIBLE, checked);
            title.setEnabled(checked);
            height.setEnabled(checked);
            applyChromeSettings();
        });
        title.setOnCheckedChangeListener((button, checked) -> {
            prefs.putBoolean(Prefs.KEY_TITLE_VISIBLE, checked);
            applyChromeSettings();
        });
        controls.setOnCheckedChangeListener((button, checked) -> {
            prefs.putBoolean(Prefs.KEY_CONTROLS_VISIBLE, checked);
            applyChromeSettings();
        });

        TextView warning = Ui.text(this,
                "При выключенных шапке и кнопках окно останется без элементов управления Atlas; "
                        + "закрыть его можно из настроек или уведомления.",
                12, Ui.SECONDARY);
        card.addView(warning, Ui.fullWrap());
        Ui.topMargin(warning, this, 8);
        return card;
    }

    @SuppressWarnings("deprecation")
    private Switch chromeSwitch(String label, boolean checked) {
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextColor(Ui.PRIMARY);
        toggle.setTextSize(14);
        toggle.setChecked(checked);
        toggle.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        return toggle;
    }

    private void applyChromeSettings() {
        if (preview != null) {
            preview.setChromeStyle(prefs.chromeStyle());
        }
        OverlayService.updateChrome(this);
    }

    @SuppressWarnings("deprecation")
    private void pickApplication() {
        if (prefs.presets().size() >= Preset.MAX_COUNT) {
            Toast.makeText(this, "Все пять слотов заняты", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(new Intent(this, AppPickerActivity.class), REQUEST_PICK_APP);
    }

    private void renderPresets() {
        if (presetList == null) return;
        List<Preset> presets = prefs.presets();
        LauncherPresetPublisher.sync(this, presets);
        if (addPresetButton != null) {
            boolean available = presets.size() < Preset.MAX_COUNT;
            addPresetButton.setEnabled(available);
            addPresetButton.setText(available
                    ? "+ Добавить приложение (" + presets.size() + "/" + Preset.MAX_COUNT + ")"
                    : "Все пять слотов заняты");
        }
        presetList.removeAllViews();
        BackendStatus status = OverlayService.lastStatus();
        String activeId = status != null && status.state == BackendStatus.State.ACTIVE
                && status.activePreset != null
                ? status.activePreset.id : "";
        presetList.setTag(activeId);
        if (presets.isEmpty()) {
            TextView empty = Ui.text(this,
                    "Пресетов пока нет. Добавьте launcher Activity установленного приложения.",
                    13, Ui.SECONDARY);
            empty.setPadding(Ui.dp(this, 12), Ui.dp(this, 12),
                    Ui.dp(this, 12), Ui.dp(this, 12));
            empty.setBackground(Ui.background(Ui.NESTED, 8, this));
            presetList.addView(empty, Ui.fullWrap());
            return;
        }
        for (Preset preset : presets) {
            boolean active = status != null && status.state == BackendStatus.State.ACTIVE
                    && status.activePreset != null
                    && preset.id.equals(status.activePreset.id);
            presetList.addView(buildPresetRow(preset, active));
        }
    }

    private View buildPresetRow(Preset preset, boolean active) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12),
                Ui.dp(this, 14), Ui.dp(this, 12));
        row.setBackground(Ui.stroked(Ui.NESTED, 8,
                active ? Ui.ACCENT : Ui.CARD, active ? 2 : 1, this));
        LinearLayout.LayoutParams rowParams = Ui.fullWrap();
        rowParams.bottomMargin = Ui.dp(this, 8);
        row.setLayoutParams(rowParams);

        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setContentDescription(null);
        try {
            ComponentName component = ComponentName.unflattenFromString(preset.component);
            if (component == null) throw new PackageManager.NameNotFoundException();
            icon.setImageDrawable(getPackageManager().getActivityIcon(component));
        } catch (PackageManager.NameNotFoundException error) {
            icon.setImageResource(R.mipmap.ic_launcher);
        }
        identity.addView(icon, new LinearLayout.LayoutParams(
                Ui.dp(this, 42), Ui.dp(this, 42)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = Ui.dp(this, 12);
        identity.addView(labels, labelsParams);
        labels.addView(Ui.heading(this,
                "Пресет " + preset.slot + "  •  " + preset.label
                        + (active ? "  • активно" : ""), 16));
        TextView component = Ui.text(this, preset.component, 11, Ui.SECONDARY);
        component.setSingleLine(true);
        component.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        component.setTextIsSelectable(true);
        labels.addView(component, Ui.fullWrap());
        TextView id = Ui.text(this,
                "Preset ID: " + preset.id + "  •  extra: " + CommandContract.EXTRA_PRESET,
                10, Ui.SECONDARY);
        id.setTextIsSelectable(true);
        labels.addView(id, Ui.fullWrap());
        row.addView(identity, Ui.fullWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button launch = Ui.button(this, active ? "Показать" : "Открыть / переключить");
        launch.setOnClickListener(v -> OverlayService.show(this, preset.id));
        actions.addView(launch, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button delete = Ui.button(this, "Удалить");
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        deleteParams.leftMargin = Ui.dp(this, 8);
        actions.addView(delete, deleteParams);
        delete.setOnClickListener(v -> confirmDelete(preset, active));
        row.addView(actions, Ui.fullWrap());
        Ui.topMargin(actions, this, 10);
        return row;
    }

    private void confirmDelete(Preset preset, boolean active) {
        String consequence = active
                ? " Активное окно будет остановлено."
                : "";
        new AlertDialog.Builder(this)
                .setTitle("Удалить «" + preset.label + "»?")
                .setMessage("Пресет " + preset.slot
                        + " и его отдельная иконка лаунчера будут удалены." + consequence)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    if (active) OverlayService.stop(this);
                    prefs.deletePreset(preset.id);
                    renderPresets();
                })
                .show();
    }

    private void addMarginControl(
            LinearLayout parent, int side, String title, int maximum, int initial) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(this, title, 14, Ui.PRIMARY);
        header.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = Ui.text(this, initial + " px", 13, Ui.SECONDARY);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(value, new LinearLayout.LayoutParams(
                Ui.dp(this, 112), ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(header, Ui.fullWrap());
        Ui.topMargin(header, this, 10);

        SeekBar bar = new SeekBar(this);
        bar.setMax(maximum);
        bar.setProgress(Math.min(maximum, Math.max(0, initial)));
        bar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                if (fromUser) updateMargin(side, progress);
            }
        });
        parent.addView(bar, Ui.fullWrap());
        marginBars[side] = bar;
        marginValues[side] = value;
    }

    private void updateMargin(int side, int requested) {
        if (geometryUpdating) return;
        int opposite;
        int axisSize;
        switch (side) {
            case SIDE_LEFT -> {
                opposite = rightMargin;
                axisSize = displayWidth;
            }
            case SIDE_RIGHT -> {
                opposite = leftMargin;
                axisSize = displayWidth;
            }
            case SIDE_TOP -> {
                opposite = bottomMargin;
                axisSize = displayHeight;
            }
            case SIDE_BOTTOM -> {
                opposite = topMargin;
                axisSize = displayHeight;
            }
            default -> throw new IllegalArgumentException("Unknown side");
        }
        int minimum = Math.min(MIN_WINDOW_SIZE_PX, axisSize);
        int allowed = Math.max(0, axisSize - minimum - opposite);
        int accepted = Math.max(0, Math.min(requested, allowed));
        switch (side) {
            case SIDE_LEFT -> leftMargin = accepted;
            case SIDE_TOP -> topMargin = accepted;
            case SIDE_RIGHT -> rightMargin = accepted;
            case SIDE_BOTTOM -> bottomMargin = accepted;
            default -> throw new IllegalArgumentException("Unknown side");
        }
        if (accepted != requested) {
            geometryUpdating = true;
            marginBars[side].setProgress(accepted);
            geometryUpdating = false;
        }
        updateGeometryUi();
    }

    private void updateGeometryUi() {
        if (preview == null) return;
        int[] values = {leftMargin, topMargin, rightMargin, bottomMargin};
        for (int side = 0; side < values.length; side++) {
            if (marginValues[side] != null) marginValues[side].setText(values[side] + " px");
        }
        int width = displayWidth - leftMargin - rightMargin;
        int height = displayHeight - topMargin - bottomMargin;
        preview.setGeometry(displayWidth, displayHeight,
                leftMargin, topMargin, rightMargin, bottomMargin);
        if (windowSize != null) {
            windowSize.setText("Результат: " + width + " × " + height + " px  •  ["
                    + leftMargin + ", " + topMargin + ", "
                    + (displayWidth - rightMargin) + ", "
                    + (displayHeight - bottomMargin) + "]");
        }
    }

    private void readMargins(WindowBounds bounds) {
        int minimumWidth = Math.min(MIN_WINDOW_SIZE_PX, displayWidth);
        int minimumHeight = Math.min(MIN_WINDOW_SIZE_PX, displayHeight);
        leftMargin = Math.min(bounds.left, Math.max(0, displayWidth - minimumWidth));
        topMargin = Math.min(bounds.top, Math.max(0, displayHeight - minimumHeight));
        rightMargin = Math.min(Math.max(0, displayWidth - bounds.right),
                Math.max(0, displayWidth - minimumWidth - leftMargin));
        bottomMargin = Math.min(Math.max(0, displayHeight - bounds.bottom),
                Math.max(0, displayHeight - minimumHeight - topMargin));
    }

    private WindowBounds selectedBounds() {
        return new WindowBounds(leftMargin, topMargin,
                displayWidth - rightMargin, displayHeight - bottomMargin);
    }

    private void saveAndApplyBounds() {
        WindowBounds bounds = selectedBounds();
        prefs.putBounds(bounds);
        BackendStatus status = OverlayService.lastStatus();
        if (status != null && status.state == BackendStatus.State.ACTIVE
                && status.activePreset != null) {
            OverlayService.resize(this);
            Toast.makeText(this, "Границы сохранены; resize отправлен активной задаче",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Границы сохранены для следующего запуска",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void savePortAndProbe(EditText portField) {
        String raw = portField.getText().toString().trim();
        final int port;
        try {
            port = Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            portField.setError("Введите порт от 1 до 65535");
            return;
        }
        if (port < 1 || port > 65535) {
            portField.setError("Введите порт от 1 до 65535");
            return;
        }
        prefs.putInt(Prefs.KEY_ADB_PORT, port);
        portField.setText(String.valueOf(port));
        OverlayService.probe(this);
        Toast.makeText(this, "Проверяется локальный ADB 127.0.0.1:" + port,
                Toast.LENGTH_LONG).show();
    }

    private void refreshPermissions() {
        if (permissionState == null) return;
        boolean overlay = Settings.canDrawOverlays(this);
        boolean usage = hasUsageAccess();
        permissionState.setText("Оформление overlay: " + yesNo(overlay)
                + "  •  Usage access: " + yesNo(usage));
        permissionState.setTextColor(overlay && usage ? Ui.ACCENT : Ui.SECONDARY);
    }

    private boolean hasUsageAccess() {
        return ForegroundAppDetector.hasUsageAccess(this);
    }

    private void refreshStatus() {
        if (backendState == null) return;
        BackendStatus status = OverlayService.lastStatus();
        if (status == null) {
            backendState.setText("Сервис не запускался");
            backendState.setTextColor(Ui.SECONDARY);
            stopButton.setEnabled(false);
            return;
        }
        String text = stateLabel(status.state) + "\n" + status.detail;
        if (!android.text.TextUtils.equals(backendState.getText(), text)) {
            backendState.setText(text);
        }
        int color = status.state == BackendStatus.State.ERROR
                ? Ui.ERROR
                : status.state == BackendStatus.State.READY
                        || status.state == BackendStatus.State.ACTIVE
                        ? Ui.ACCENT : Ui.SECONDARY;
        if (backendState.getCurrentTextColor() != color) backendState.setTextColor(color);
        boolean serviceRunning = OverlayService.isRunning();
        if (stopButton.isEnabled() != serviceRunning) stopButton.setEnabled(serviceRunning);
        if (resumed) {
            renderActivePresetOnly(status.state == BackendStatus.State.ACTIVE
                    && status.activePreset != null ? status.activePreset.id : "");
        }
    }

    private void renderActivePresetOnly(String activeId) {
        // Rebuilding every polling tick would reset touch feedback. Only rebuild when the marker
        // differs from the rendered rows' content description.
        Object rendered = presetList == null ? null : presetList.getTag();
        if (activeId.equals(rendered)) return;
        presetList.setTag(activeId);
        renderPresets();
    }

    private void openSettings(Intent intent, String label) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            AppLog.warn("Cannot open " + label, error);
            Toast.makeText(this, "Прошивка не предоставляет " + label,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openAppSpecificSettings(String action, String label) {
        if (!SystemSettingsLauncher.open(this, action, label)) {
            Toast.makeText(this, "Прошивка не предоставляет " + label,
                    Toast.LENGTH_LONG).show();
        }
    }

    private static String yesNo(boolean value) {
        return value ? "разрешено" : "не разрешено";
    }

    private static String stateLabel(BackendStatus.State state) {
        if (state == null) return "НЕИЗВЕСТНО";
        return switch (state) {
            case IDLE -> "ОЖИДАНИЕ";
            case CONNECTING -> "ПРОВЕРКА";
            case READY -> "ГОТОВО";
            case LAUNCHING -> "ЗАПУСК";
            case ACTIVE -> "АКТИВНО";
            case ERROR -> "ОШИБКА";
        };
    }

    private static String formatScale(int tenths) {
        return String.format(Locale.getDefault(), "%d%%", tenths * 10);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
