package app.contouros.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HomeActivity extends Activity {
    private static final int BG = Color.rgb(9, 13, 18);
    private static final int SURFACE = Color.rgb(22, 29, 37);
    private static final int TEXT = Color.rgb(239, 244, 248);
    private static final int MUTED = Color.rgb(162, 176, 189);
    private static final int MINT = Color.rgb(187, 242, 207);
    private static final int LILAC = Color.rgb(203, 195, 243);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<AppEntry> apps = new ArrayList<>();
    private final List<Binding> bindings = new ArrayList<>();
    private SharedPreferences prefs;
    private HomeStore store;
    private List<TileSpec> tiles;
    private LinearLayout root;
    private ScrollView scroll;
    private TextView clock;
    private TextView date;
    private boolean resumed;
    private boolean receiversRegistered;
    private boolean packagesRegistered;
    private boolean networkRegistered;
    private boolean loadingApps;
    private int batteryPercent = -1;
    private boolean charging;
    private ConnectivityManager connectivity;
    private AlarmManager alarmManager;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refreshState();
            main.postDelayed(this, 60000 - System.currentTimeMillis() % 60000);
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged = (p, key) -> {
        if (QuietTile.QUIET.equals(key)) {
            refreshState();
            TileService.requestListeningState(this, new ComponentName(this, QuietTile.class));
        } else if (AssistantPresence.KEY.equals(key)) refreshState();
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) readBattery(intent);
            refreshState();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { loadApps(); }
    };

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) { stateChanged(); }
        @Override public void onLost(Network network) { stateChanged(); }
        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) { stateChanged(); }
        private void stateChanged() { main.post(() -> { if (resumed) refreshState(); }); }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        prefs = getSharedPreferences(QuietTile.PREFERENCES, MODE_PRIVATE);
        store = new HomeStore(prefs);
        tiles = store.read();
        connectivity = getSystemService(ConnectivityManager.class);
        alarmManager = getSystemService(AlarmManager.class);
        renderHome();
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) controller.setSystemBarsAppearance(0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    }

    @Override protected void onStart() {
        super.onStart();
        prefs.registerOnSharedPreferenceChangeListener(preferencesChanged);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        Intent battery;
        if (Build.VERSION.SDK_INT >= 33) battery = registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else battery = registerReceiver(stateReceiver, filter);
        receiversRegistered = true;
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packageReceiver,packages,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(packageReceiver,packages);
        packagesRegistered=true;
        if (battery != null) readBattery(battery);
        try {
            if (connectivity != null) { connectivity.registerDefaultNetworkCallback(networkCallback); networkRegistered = true; }
        } catch (SecurityException | IllegalArgumentException ignored) { networkRegistered = false; }
        loadApps();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        main.removeCallbacks(tick);
        tick.run();
    }

    private void openAssistant(boolean welcome) {
        startActivity(new Intent(this, AssistantActivity.class)
                .putExtra(AssistantActivity.SHOW_WELCOME, welcome));
    }

    @Override protected void onPause() {
        resumed = false;
        main.removeCallbacks(tick);
        stopAnimation();
        super.onPause();
    }

    @Override protected void onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        if (receiversRegistered) { unregisterReceiver(stateReceiver); receiversRegistered = false; }
        if (packagesRegistered) { unregisterReceiver(packageReceiver); packagesRegistered = false; }
        if (networkRegistered && connectivity != null) {
            connectivity.unregisterNetworkCallback(networkCallback);
            networkRegistered = false;
        }
        super.onStop();
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && scroll != null) scroll.smoothScrollTo(0, 0);
    }

    private void renderHome() {
        int oldScroll = scroll == null ? 0 : scroll.getScrollY();
        stopAnimation();
        bindings.clear();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        SwipePanel swipe = new SwipePanel(this, -1, () -> showApps(false));
        swipe.addView(scroll);
        root.addView(swipe, new LinearLayout.LayoutParams(-1, 0, 1));

        clock = text("", 54, TEXT);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clock.setMaxLines(1);
        clock.setAutoSizeTextTypeUniformWithConfiguration(32, 54, 2, TypedValue.COMPLEX_UNIT_SP);
        clock.setOnClickListener(v -> openAlarms());
        clock.setOnLongClickListener(v -> { showSettings(); return true; });
        clock.setContentDescription("Время. Открыть будильники");
        content.addView(clock);
        date = text("", 15, MUTED);
        date.setPadding(dp(2), dp(4), 0, dp(22));
        content.addView(date);

        TileBoard board = new TileBoard(this, store.columns());
        for (TileSpec tile : tiles) board.addTile(createTile(tile), tile);
        content.addView(board, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (tiles.isEmpty()) {
            TextView empty = text("Место свободно. Добавь сюда то, что нужно под рукой.", 18, MUTED);
            empty.setPadding(0, dp(24), 0, dp(24));
            content.addView(empty);
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setPadding(0, dp(14), 0, 0);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        Button settings = button("Настроить", this::showSettings);
        footer.addView(settings, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button all = button("Все приложения  →", () -> showApps(false));
        footer.addView(all, new LinearLayout.LayoutParams(0, dp(48), 1));
        content.addView(footer);
        refreshState();
        scroll.post(() -> { if (!isDestroyed()) scroll.scrollTo(0, oldScroll); });
    }

    private View createTile(TileSpec spec) {
        Binding binding = new Binding(spec); bindings.add(binding);
        boolean small = spec.span == TileSpec.SMALL;
        FrameLayout card = new FrameLayout(this);
        int surface = TileSpec.APP.equals(spec.kind) ? new int[]{0xff38344c,0xff344b49,0xff394253,0xff51415a}[Math.floorMod(spec.component.hashCode(),4)] : SURFACE;
        card.setBackground(ripple(surface, dp(3))); card.setClipToOutline(true);
        card.setFocusable(true); card.setClickable(true);
        card.setOnLongClickListener(v -> { editTile(spec); return true; });
        card.setContentDescription(title(spec)); binding.card = card;
        if (TileSpec.PRESENCE.equals(spec.kind)) {
            binding.presence = new PresenceView(this);
            card.addView(binding.presence, new FrameLayout.LayoutParams(-1,-1));
            binding.value = text("", 16, TEXT); binding.detail = text("",12,MUTED);
            // The source illustration already contains a caption; draw the portrait without it.
            binding.value.setPadding(dp(10),dp(8),dp(10),dp(10));
            card.addView(binding.value, new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
            card.setContentDescription("Помощница. Открыть диалог");
            card.setOnClickListener(v -> openAssistant(false));
        } else if (TileSpec.APP.equals(spec.kind)) {
            AppEntry app = findApp(spec.component);
            ImageView icon = new ImageView(this);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            try { if(app!=null)icon.setImageDrawable(app.info.loadIcon(getPackageManager())); else icon.setImageResource(R.drawable.ic_contour); }
            catch(RuntimeException e){icon.setImageResource(R.drawable.ic_contour);}
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            FrameLayout iconArea = new FrameLayout(this);
            iconArea.addView(icon, new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER));
            // Scale with the actual cell dimensions, including six-column and large-font layouts.
            // The label has its own space so a larger icon cannot cover it.
            iconArea.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int edge = Math.min(right-left, bottom-top);
                int size = Math.max(1, Math.min(edge-dp(12), Math.min(dp(160), Math.round(edge*0.55f))));
                ViewGroup.LayoutParams params = icon.getLayoutParams();
                if (params.width != size || params.height != size) {
                    params.width = size; params.height = size; icon.setLayoutParams(params);
                }
            });
            content.addView(iconArea, new LinearLayout.LayoutParams(-1,0,1));
            binding.value=text(app==null?spec.label:app.label,14,TEXT);
            binding.value.setMaxLines(2);binding.value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            binding.value.setPadding(dp(10),0,dp(8),dp(9));
            if(!small)content.addView(binding.value,new LinearLayout.LayoutParams(-1,-2));
            card.addView(content,new FrameLayout.LayoutParams(-1,-1));
            binding.detail=text("",12,MUTED);
            card.setOnClickListener(v -> launchApp(spec.component));
        } else {
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_VERTICAL);content.setPadding(dp(small?7:12),dp(6),dp(small?7:12),dp(6));
            TextView title=text(title(spec),small?9:12,MUTED);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            content.addView(title,new LinearLayout.LayoutParams(-1,-2));
            binding.value=text("—",small?17:28,TileSpec.BATTERY.equals(spec.kind)?MINT:TEXT);
            binding.value.setMaxLines(1);binding.value.setAutoSizeTextTypeUniformWithConfiguration(small?10:16,small?19:30,1,TypedValue.COMPLEX_UNIT_SP);
            content.addView(binding.value,new LinearLayout.LayoutParams(-1,0,1));
            binding.detail=text("",12,MUTED);binding.detail.setMaxLines(2);binding.detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if(!small)content.addView(binding.detail);
            if(TileSpec.BATTERY.equals(spec.kind)||TileSpec.STORAGE.equals(spec.kind)) {
                binding.progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
                binding.progress.setMax(100);binding.progress.setProgressTintList(ColorStateList.valueOf(MINT));
                if(!small){LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(-1,dp(3));bar.topMargin=dp(8);content.addView(binding.progress,bar);}
            }
            card.addView(content,new FrameLayout.LayoutParams(-1,-1));
            card.setOnClickListener(v -> openSystemTile(spec.kind));
        }
        return card;
    }

    private void refreshState() {
        if (clock == null || prefs == null) return;
        Date now = new Date();
        clock.setText(DateFormat.getTimeFormat(this).format(now));
        date.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now));
        boolean quiet = prefs.getBoolean(QuietTile.QUIET, false);
        PowerManager power = getSystemService(PowerManager.class);
        boolean savingPower = power != null && power.isPowerSaveMode();
        PresenceMode mode = resumed ? AssistantPresence.observe(this, batteryPercent, charging, savingPower)
                : AssistantPresence.read(this);
        boolean economical = savingPower || (batteryPercent >= 0 && batteryPercent <= 20 && !charging);
        boolean animate = resumed && !quiet && !economical && ValueAnimator.areAnimatorsEnabled();
        AlarmManager.AlarmClockInfo next = alarmManager == null ? null : alarmManager.getNextAlarmClock();
        String[] network = networkState();
        long total = -1;
        long available = -1;
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            total = stat.getTotalBytes();
            available = stat.getAvailableBytes();
        } catch (IllegalArgumentException | SecurityException ignored) { }
        for (Binding binding : bindings) {
            switch (binding.spec.kind) {
                case TileSpec.PRESENCE:
                    binding.presence.setAway(mode.isAway());
                    binding.presence.setMoving(animate);
                    binding.value.setText(mode == PresenceMode.BATTERY_OFFER ? "Заряд на исходе" : "");
                    binding.card.setContentDescription(mode.isAway() ? "Помощница. Сидит в углу спиной. Открыть диалог"
                            : mode == PresenceMode.BATTERY_OFFER ? "Помощница. Предлагает энергосбережение. Открыть диалог"
                            : "Помощница. Открыть диалог");
                    binding.detail.setText("Открыть диалог");
                    break;
                case TileSpec.BATTERY:
                    binding.value.setText(batteryPercent < 0 ? "—" : batteryPercent + "%");
                    binding.detail.setText(batteryPercent < 0 ? "Нет данных" : charging ? "На зарядке" : "От батареи");
                    binding.progress.setProgress(Math.max(0, batteryPercent));
                    break;
                case TileSpec.ALARM:
                    binding.value.setText(next == null ? "Нет" : DateFormat.getTimeFormat(this).format(new Date(next.getTriggerTime())));
                    binding.detail.setText(next == null ? "Будильник не задан" : new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date(next.getTriggerTime())));
                    break;
                case TileSpec.STORAGE:
                    binding.value.setText(available < 0 ? "—" : Formatter.formatShortFileSize(this, available));
                    binding.detail.setText(total > 0 ? "Свободно из " + Formatter.formatShortFileSize(this, total) : "Нет данных о памяти");
                    binding.progress.setProgress(total <= 0 ? 0 : (int) Math.round(100.0 * (total - available) / total));
                    binding.progress.setContentDescription("Занято " + binding.progress.getProgress() + " процентов памяти");
                    break;
                case TileSpec.NETWORK:
                    binding.value.setText(network[0]);
                    binding.detail.setText(network[1]);
                    break;
                case TileSpec.APP:
                    AppEntry app = findApp(binding.spec.component);
                    binding.detail.setText(app != null ? "Открыть" : loadingApps ? "Проверка приложения…" : "Недоступно · удерживай");
                    break;
                default: break;
            }
            if (!TileSpec.PRESENCE.equals(binding.spec.kind)) binding.card.setContentDescription(title(binding.spec) + ". " + binding.value.getText() + ". " + binding.detail.getText());
        }
    }

    private void readBattery(Intent intent) {
        batteryPercent = AssistantPresence.percent(intent);
        charging = AssistantPresence.plugged(intent);
    }

    private String[] networkState() {
        try {
            Network network = connectivity == null ? null : connectivity.getActiveNetwork();
            NetworkCapabilities caps = network == null ? null : connectivity.getNetworkCapabilities(network);
            if (caps == null) return new String[]{"Нет сети", "Открыть подключения"};
            String type = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "VPN"
                    : caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi"
                    : caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Мобильная"
                    : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet" : "Сеть";
            String status = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) ? "Нужен вход в сеть"
                    : caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? "Интернет доступен" : "Интернет не подтверждён";
            return new String[]{type, status};
        } catch (SecurityException e) { return new String[]{"—", "Нет доступа к состоянию"}; }
    }

    private void openSystemTile(String kind) {
        switch (kind) {
            case TileSpec.BATTERY: safeStart(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)); break;
            case TileSpec.ALARM: openAlarms(); break;
            case TileSpec.STORAGE: safeStart(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)); break;
            case TileSpec.NETWORK: safeStart(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)); break;
            default: break;
        }
    }

    private void openAlarms() {
        AlarmManager.AlarmClockInfo next = alarmManager == null ? null : alarmManager.getNextAlarmClock();
        if (next != null && next.getShowIntent() != null) {
            try { next.getShowIntent().send(); return; }
            catch (PendingIntent.CanceledException ignored) { }
        }
        safeStart(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
    }

    private void safeStart(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) { toast("Этот экран недоступен в установленной системе"); }
    }

    private void stopAnimation() { for (Binding binding : bindings) if (binding.presence != null) binding.presence.setMoving(false); }

    private void loadApps() {
        if (loadingApps) return;
        loadingApps = true;
        worker.execute(() -> {
            List<AppEntry> found = new ArrayList<>();
            boolean success = false;
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_ALL);
                Set<String> seen = new HashSet<>();
                for (ResolveInfo info : list) {
                    if (info.activityInfo == null || !info.activityInfo.exported || !info.activityInfo.enabled
                            || !info.activityInfo.applicationInfo.enabled || getPackageName().equals(info.activityInfo.packageName)) continue;
                    ComponentName component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                    String flat = component.flattenToString();
                    if (seen.add(flat)) found.add(new AppEntry(info.loadLabel(getPackageManager()).toString(), flat, info));
                }
                Collator collator = Collator.getInstance();
                Collections.sort(found, (a, b) -> collator.compare(a.label, b.label));
                success = true;
            } catch (RuntimeException ignored) { }
            final boolean scanSucceeded = success;
            main.post(() -> {
                if (isDestroyed()) return;
                loadingApps = false;
                apps.clear();
                apps.addAll(found);
                if (scanSucceeded && !found.isEmpty()) {
                    List<TileSpec> installed = new ArrayList<>();
                    for (AppEntry app : apps) installed.add(new TileSpec(TileSpec.APP, app.component, app.label, TileSpec.SMALL));
                    tiles = store.syncApps(installed);
                }
                renderHome();
            });
        });
    }

    private AppEntry findApp(String component) { for (AppEntry app : apps) if (app.component.equals(component)) return app; return null; }

    private void launchApp(String flat) {
        ComponentName component = ComponentName.unflattenFromString(flat);
        if (component == null || findApp(flat) == null) { toast("Приложение недоступно. Удерживай плитку, чтобы убрать её"); return; }
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        safeStart(intent);
    }

    private void showApps(boolean pin) {
        if (loadingApps && apps.isEmpty()) { toast("Список приложений ещё загружается"); return; }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(4), dp(12), 0);
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Найти приложение");
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setPadding(dp(12), dp(8), dp(12), dp(8));
        content.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setEmptyView(null);
        AppAdapter adapter = new AppAdapter(new ArrayList<>(apps));
        list.setAdapter(adapter);
        int height = Math.min(dp(420), (int) (getResources().getDisplayMetrics().heightPixels * 0.48f));
        content.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        TextView empty = text(apps.isEmpty() ? "Нет доступных приложений" : "Ничего не найдено", 15, MUTED);
        empty.setPadding(dp(12), dp(24), dp(12), dp(24));
        content.addView(empty);
        list.setEmptyView(empty);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(pin ? "Приложение на главный экран" : "Приложения")
                .setView(content).setNegativeButton("Закрыть", null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry entry = adapter.filtered.get(position);
            if (pin) addTile(new TileSpec(TileSpec.APP, entry.component, entry.label, 1));
            else launchApp(entry.component);
            dialog.dismiss();
        });
        if (!pin) list.setOnItemLongClickListener((parent, view, position, id) -> {
            AppEntry entry = adapter.filtered.get(position);
            addTile(new TileSpec(TileSpec.APP, entry.component, entry.label, 1));
            dialog.dismiss();
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.filter(s.toString()); }
            @Override public void afterTextChanged(Editable editable) { }
        });
        if (!pin) {
            android.app.Dialog page = new android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar);
            SwipePanel swipe = new SwipePanel(this, 1, page::dismiss);
            swipe.setBackgroundColor(BG);
            swipe.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
            LinearLayout sheet = new LinearLayout(this); sheet.setOrientation(LinearLayout.VERTICAL); sheet.setBackgroundColor(BG);
            Button back = button("←  Рабочий стол", page::dismiss); sheet.addView(back,new LinearLayout.LayoutParams(-1,dp(48)));
            content.removeView(list); content.removeView(empty);
            sheet.addView(content); sheet.addView(list,new LinearLayout.LayoutParams(-1,0,1));sheet.addView(empty);
            swipe.addView(sheet);page.setContentView(swipe);
            list.setOnItemClickListener((parent, view, position, id) -> { launchApp(adapter.filtered.get(position).component);page.dismiss(); });
            list.setOnItemLongClickListener((parent, view, position, id) -> {
                AppEntry entry=adapter.filtered.get(position);addTile(new TileSpec(TileSpec.APP,entry.component,entry.label,TileSpec.SMALL));page.dismiss();return true;
            });
            page.show();
            Window window = page.getWindow();
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            window.setNavigationBarContrastEnforced(false);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.setLayout(-1,-1);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            sheet.setFocusableInTouchMode(true);sheet.requestFocus();swipe.requestApplyInsets();return;
        }
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void showAddTile() {
        List<TileSpec> choices = new ArrayList<>();
        for (TileSpec tile : HomeStore.defaults()) if (!hasTile(tile.key())) choices.add(tile);
        String[] labels = new String[choices.size() + 1];
        labels[0] = "Приложение";
        for (int i = 0; i < choices.size(); i++) labels[i + 1] = title(choices.get(i));
        new AlertDialog.Builder(this).setTitle("Добавить плитку").setItems(labels, (d, which) -> {
            if (which == 0) showApps(true); else addTile(choices.get(which - 1));
        }).setNegativeButton("Отмена", null).show();
    }

    private boolean hasTile(String key) { for (TileSpec tile : tiles) if (tile.key().equals(key)) return true; return false; }

    private void addTile(TileSpec tile) {
        if (hasTile(tile.key())) { toast("Эта плитка уже на экране"); return; }
        if (tiles.size() >= HomeStore.MAX_TILES) { toast("Достигнут предел количества плиток"); return; }
        tiles.add(tile);
        commitLayout();
        toast("Плитка добавлена");
    }

    private void editTile(TileSpec spec) {
        int index = -1;
        for (int i = 0; i < tiles.size(); i++) if (tiles.get(i).key().equals(spec.key())) { index = i; break; }
        if (index < 0) return;
        final int selected = index;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        String[] sizes={"Маленькая · 1 × 1","Средняя · 2 × 2","Широкая · 4 × 2","Большая · 4 × 4"};
        for(int size=1;size<=4;size++) {
            final int chosen=size;
            if(size!=spec.span){labels.add(sizes[size-1]);actions.add(() -> {tiles.set(selected,new TileSpec(spec.kind,spec.component,spec.label,chosen));commitLayout();});}
        }
        if (selected > 0) { labels.add("Раньше в порядке плиток"); actions.add(() -> { Collections.swap(tiles, selected, selected - 1); commitLayout(); }); }
        if (selected < tiles.size() - 1) { labels.add("Позже в порядке плиток"); actions.add(() -> { Collections.swap(tiles, selected, selected + 1); commitLayout(); }); }
        labels.add("Убрать с главного экрана");
        actions.add(() -> { tiles.remove(selected); commitLayout(); });
        if (TileSpec.APP.equals(spec.kind)) {
            labels.add("О приложении");
            actions.add(() -> {
                ComponentName name = ComponentName.unflattenFromString(spec.component);
                if (name != null) safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + name.getPackageName())));
            });
        }
        new AlertDialog.Builder(this).setTitle(title(spec)).setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton("Закрыть", null).show();
    }

    private void commitLayout() { store.write(tiles); renderHome(); }

    private void showSettings() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Плотность: " + store.columns() + " клетки в ряд");
        actions.add(() -> new AlertDialog.Builder(this).setTitle("Плотность плиток")
            .setSingleChoiceItems(new String[]{"4 клетки · крупнее", "6 клеток · компактнее"}, store.columns()==6?1:0,
                (d,which) -> {store.setColumns(which==0?4:6);d.dismiss();renderHome();}).setNegativeButton("Закрыть",null).show());
        labels.add("Добавить плитку"); actions.add(this::showAddTile);
        labels.add("Открыть помощницу"); actions.add(() -> openAssistant(true));
        if (store.canUndo()) { labels.add("Отменить последнюю правку плиток"); actions.add(() -> { store.undo(); tiles = store.read(); renderHome(); }); }
        labels.add("Настройки Android"); actions.add(() -> safeStart(new Intent(Settings.ACTION_SETTINGS)));
        labels.add("О первом выпуске"); actions.add(this::showAbout);
        labels.add("Вернуть начальные плитки"); actions.add(() -> new AlertDialog.Builder(this).setTitle("Вернуть начальные плитки?")
                .setMessage("Изменится только расположение плиток. Приложения и их данные сохранятся. Последнюю правку можно отменить в этом меню.")
                .setNegativeButton("Отмена", null).setPositiveButton("Вернуть", (d, w) -> { tiles = HomeStore.defaults(); commitLayout(); }).show());
        new AlertDialog.Builder(this).setTitle("Твой главный экран").setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton("Закрыть", null).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("glitchOS · 0.5.0")
                .setMessage("Напиши «оставь меня», чтобы помощница села в угол своей карточки. Позвать её обратно можно словом «вернись». Её сигналы зависят от звукового режима телефона. У будильников своя громкость.\n\nНа телефоне сохраняются последние 60 реплик.")
                .setPositiveButton("Понятно", null).show();
    }

    private String title(TileSpec spec) {
        switch (spec.kind) {
            case TileSpec.PRESENCE: return "Помощница";
            case TileSpec.BATTERY: return "Заряд";
            case TileSpec.ALARM: return "Следующий будильник";
            case TileSpec.STORAGE: return "Память";
            case TileSpec.NETWORK: return "Подключение";
            default: return spec.label;
        }
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("tnum");
        return view;
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(MUTED);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(ripple(Color.TRANSPARENT, dp(16)));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private RippleDrawable ripple(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radius);
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(28, 187, 242, 207)), background, mask);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }

    private static final class Binding {
        final TileSpec spec;
        View card;
        TextView value;
        TextView detail;
        ProgressBar progress;
        PresenceView presence;
        Binding(TileSpec spec) { this.spec = spec; }
    }

    private static final class AppEntry {
        final String label;
        final String component;
        final ResolveInfo info;
        AppEntry(String label, String component, ResolveInfo info) { this.label = label; this.component = component; this.info = info; }
    }

    private final class AppAdapter extends BaseAdapter {
        final List<AppEntry> original;
        final List<AppEntry> filtered = new ArrayList<>();
        AppAdapter(List<AppEntry> entries) { original = entries; filter(""); }
        void filter(String query) {
            String needle = query.trim().toLowerCase(Locale.getDefault());
            filtered.clear();
            for (AppEntry app : original) if (app.label.toLowerCase(Locale.getDefault()).contains(needle)) filtered.add(app);
            notifyDataSetChanged();
        }
        @Override public int getCount() { return filtered.size(); }
        @Override public Object getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            AppEntry entry = filtered.get(position);
            LinearLayout row;
            ImageView icon;
            TextView label;
            if (recycled instanceof LinearLayout) {
                row = (LinearLayout) recycled;
                icon = (ImageView) row.getChildAt(0);
                label = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(HomeActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(64));
                row.setPadding(dp(10), dp(10), dp(10), dp(10));
                icon = new ImageView(HomeActivity.this);
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
                label = text("", 16, TEXT);
                label.setPadding(dp(16), 0, 0, 0);
                row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
            label.setText(entry.label);
            try { icon.setImageDrawable(entry.info.loadIcon(getPackageManager())); }
            catch (RuntimeException ignored) { icon.setImageResource(R.drawable.ic_contour); }
            return row;
        }
    }
}
