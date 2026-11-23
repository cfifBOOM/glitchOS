package app.contouros.home;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.PowerManager;

final class AssistantPresence {
    static final String KEY = "assistant_presence_v1";
    static final String PERMISSION_QUESTION = "Хорошо, посижу в углу. Могу предложить помощь, если замечу что-то важное?";
    static final String BATTERY_QUESTION = "Заряд на исходе. Открыть настройки энергосбережения?";

    static PresenceMode read(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(QuietTile.PREFERENCES, Context.MODE_PRIVATE);
        try {
            return PresenceMode.valueOf(prefs.getString(KEY, PresenceMode.HERE.name()));
        } catch (IllegalArgumentException | ClassCastException e) {
            return PresenceMode.ALONE;
        }
    }

    static void set(Context context, PresenceMode mode) {
        if (mode != read(context)) {
            context.getSharedPreferences(QuietTile.PREFERENCES, Context.MODE_PRIVATE)
                    .edit().putString(KEY, mode.name()).apply();
        }
    }

    static int percent(Intent battery) {
        if (battery == null) return -1;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level < 0 || scale <= 0 ? -1 : Math.min(100, Math.round(level * 100f / scale));
    }

    static boolean plugged(Intent battery) {
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    static PresenceMode observe(Context context) {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        PowerManager power = context.getSystemService(PowerManager.class);
        return observe(context, percent(battery), plugged(battery), power != null && power.isPowerSaveMode());
    }

    static PresenceMode observe(Context context, int level, boolean plugged, boolean savingPower) {
        PresenceMode previous = read(context);
        PresenceMode mode = previous.withBattery(level, plugged, savingPower);
        if (mode != previous) {
            set(context, mode);
            if (mode == PresenceMode.BATTERY_OFFER && soundAllowed(context)) {
                context.getSystemService(AudioManager.class).playSoundEffect(AudioManager.FX_KEY_CLICK);
            }
        }
        return mode;
    }

    static boolean soundAllowed(Context context) {
        AudioManager audio = context.getSystemService(AudioManager.class);
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        return audio != null && notifications != null && notifications.areNotificationsEnabled()
                && notifications.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_ALL
                && audio.getRingerMode() == AudioManager.RINGER_MODE_NORMAL
                && !audio.isStreamMute(AudioManager.STREAM_NOTIFICATION)
                && audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) > 0;
    }
}
