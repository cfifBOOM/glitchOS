package app.contouros.home;

import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import java.util.UUID;

/** One user-requested native alarm, independent of the dialogue and any future LLM. */
final class AssistantAlarm {
    static final String FIRE = "app.contouros.home.ALARM_FIRE";
    static final String DISMISS = "app.contouros.home.ALARM_DISMISS";
    static final String CHANNEL = "assistant_alarms_v1";
    static final int NOTIFICATION = 4101;
    static final long RING_LIMIT_MS = 120000;
    static final String WAITING = "waiting", RINGING = "ringing", DONE = "done", MISSED = "missed", FAILED = "failed";
    static final class Record {
        final String id, state; final long at;
        Record(String id, long at, String state) { this.id = id; this.at = at; this.state = state; }
        boolean active() { return WAITING.equals(state) || RINGING.equals(state); }
    }
    static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences("assistant_alarm_v1", Context.MODE_PRIVATE);
    }
    static Record read(Context c) {
        SharedPreferences p = prefs(c);
        return new Record(p.getString("id", ""), p.getLong("at", 0), p.getString("state", DONE));
    }
    static void channel(Context c) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Будильники помощницы", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Звук воспроизводится только во время срабатывания будильника.");
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    static String unavailable(Context c) {
        channel(c);
        NotificationManager n = c.getSystemService(NotificationManager.class);
        if (!n.areNotificationsEnabled() || n.getNotificationChannel(CHANNEL).getImportance() < NotificationManager.IMPORTANCE_HIGH)
            return "Сначала разреши уведомления будильника с высокой важностью. Кнопка доступа — внизу.";
        if (!c.getSystemService(AlarmManager.class).canScheduleExactAlarms())
            return "Сначала разреши точные будильники. Кнопка доступа — внизу.";
        if (c.getSystemService(AudioManager.class).getStreamVolume(AudioManager.STREAM_ALARM) == 0)
            return "Громкость будильника сейчас нулевая. Подними её в настройках звука.";
        return null;
    }
    static Record schedule(Context c, long at) {
        String problem = unavailable(c);
        if (problem != null) throw new IllegalStateException(problem);
        if (read(c).active()) throw new IllegalStateException("Один мой будильник уже активен. Сначала отмени его, если хочешь заменить.");
        if (at <= System.currentTimeMillis()) throw new IllegalArgumentException("Это время уже прошло.");
        Record record = new Record(UUID.randomUUID().toString(), at, WAITING);
        if (!prefs(c).edit().putString("id", record.id).putLong("at", at).putString("state", WAITING).commit())
            throw new IllegalStateException("Не удалось сохранить будильник. Я его не поставила.");
        try { register(c, record); audit(c, "SCHEDULED", record); }
        catch (RuntimeException e) { state(c, record, FAILED); throw e; }
        return record;
    }
    private static void register(Context c, Record r) {
        c.getSystemService(AlarmManager.class).setAlarmClock(
                new AlarmManager.AlarmClockInfo(r.at, show(c)), broadcast(c, r, FIRE));
    }
    static boolean state(Context c, Record r, String next) {
        if (!r.id.equals(read(c).id)) return false;
        boolean saved = prefs(c).edit().putString("state", next).commit();
        if (saved) audit(c, next, r);
        return saved;
    }
    static boolean cancel(Context c, String id) {
        Record r = read(c);
        if (!r.active() || !r.id.equals(id)) return false;
        boolean saved = state(c, r, DONE);
        // Cleanup paths are independent: an alarm-manager error must not leave sound playing.
        try { c.getSystemService(AlarmManager.class).cancel(broadcast(c, r, FIRE)); } catch (RuntimeException ignored) { }
        try { c.stopService(new Intent(c, AlarmSoundService.class)); } catch (RuntimeException ignored) { }
        try { c.getSystemService(NotificationManager.class).cancel(NOTIFICATION); } catch (RuntimeException ignored) { }
        return saved;
    }
    static void restore(Context c) {
        Record r = read(c);
        if (!r.active()) return;
        if (!WAITING.equals(r.state) || r.at <= System.currentTimeMillis()) { state(c, r, MISSED); return; }
        try { register(c, r); audit(c, "RESTORED", r); }
        catch (RuntimeException e) { state(c, r, FAILED); }
    }
    static void reconcile(Context c) {
        Record r = read(c);
        if (WAITING.equals(r.state) && !c.getSystemService(AlarmManager.class).canScheduleExactAlarms()) state(c, r, FAILED);
        else if (r.active() && System.currentTimeMillis() >= r.at + RING_LIMIT_MS) state(c, r, MISSED);
    }
    static PendingIntent broadcast(Context c, Record r, String action) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(action).setData(Uri.parse("glitchos-alarm:" + r.id)).putExtra("id", r.id);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent show(Context c) {
        return PendingIntent.getActivity(c, 1, new Intent(c, AssistantActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent ring(Context c, Record r) {
        Intent i = new Intent(c, AlarmRingActivity.class).setData(Uri.parse("glitchos-alarm:" + r.id)).putExtra("id", r.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ActivityOptions options = ActivityOptions.makeBasic();
        if (Build.VERSION.SDK_INT >= 35) options.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE, options.toBundle());
    }
    private static void audit(Context c, String event, Record r) {
        SharedPreferences p = prefs(c);
        String line = System.currentTimeMillis() + " " + event + " " + r.id + " " + r.at;
        String[] previous = p.getString("journal", "").split("\n");
        StringBuilder b = new StringBuilder();
        for (int i = Math.max(0, previous.length - 63); i < previous.length; i++) if (!previous[i].isEmpty()) b.append(previous[i]).append('\n');
        p.edit().putString("journal", b.append(line).toString()).apply();
    }
}
