package app.contouros.home;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Runs only while ringing. Waiting does not require a process, microphone, or network. */
public final class AlarmSoundService extends Service {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock wake;
    private Vibrator vibrator;
    private AssistantAlarm.Record record;
    private SharedPreferences preferences;
    private boolean fallback, hasFocus, prepared, ended;
    private final SharedPreferences.OnSharedPreferenceChangeListener changed = (p, key) -> { if (record != null && !valid()) finishSound(null); };
    @Override public void onCreate() {
        super.onCreate(); preferences = AssistantAlarm.prefs(this);
        preferences.registerOnSharedPreferenceChangeListener(changed);
    }
    private boolean valid() {
        AssistantAlarm.Record current = AssistantAlarm.read(this);
        return record != null && record.id.equals(current.id) && AssistantAlarm.RINGING.equals(current.state);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        AssistantAlarm.Record current = AssistantAlarm.read(this);
        if (!AssistantAlarm.RINGING.equals(current.state) || (intent != null && !current.id.equals(intent.getStringExtra("id")))) {
            stopSelfResult(startId); return START_NOT_STICKY;
        }
        if (record != null && record.id.equals(current.id) && !ended) return START_STICKY;
        releaseSound();
        ended = false; prepared = false; fallback = false; hasFocus = false;
        record = current;
        long remaining = record.at + AssistantAlarm.RING_LIMIT_MS - System.currentTimeMillis();
        if (remaining <= 0 || remaining > AssistantAlarm.RING_LIMIT_MS + 1000) { finishSound(AssistantAlarm.MISSED); return START_NOT_STICKY; }
        try {
            AssistantAlarm.channel(this);
            Notification notification = new Notification.Builder(this, AssistantAlarm.CHANNEL)
                    .setSmallIcon(R.drawable.ic_contour).setContentTitle("Пора. Я тебя разбужу.")
                    .setContentText("Будильник помощницы · нажми, чтобы остановить")
                    .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true)
                    .setContentIntent(AssistantAlarm.ring(this, record)).setFullScreenIntent(AssistantAlarm.ring(this, record), true)
                    .addAction(new Notification.Action.Builder(null, "Я проснулся", AssistantAlarm.broadcast(this, record, AssistantAlarm.DISMISS)).build()).build();
            startForeground(AssistantAlarm.NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glitchOS:alarm");
            wake.acquire(remaining + 5000);
            main.postDelayed(() -> finishSound(AssistantAlarm.MISSED), remaining);
            audio = getSystemService(AudioManager.class);
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
                    .setAcceptsDelayedFocusGain(false).setOnAudioFocusChangeListener(change -> {
                        if (ended) return;
                        if (change == AudioManager.AUDIOFOCUS_GAIN) { hasFocus = true; startPrepared(); }
                        else if (change == AudioManager.AUDIOFOCUS_LOSS) finishSound(AssistantAlarm.MISSED);
                        else {
                            hasFocus = false;
                            try { if (player != null && prepared) player.pause(); }
                            catch (RuntimeException e) { finishSound(AssistantAlarm.FAILED); }
                        }
                    }, main).build();
            hasFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if (!hasFocus) { finishSound(AssistantAlarm.FAILED); return START_NOT_STICKY; }
            vibrator = getSystemService(Vibrator.class);
            if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createWaveform(new long[] {0,350,300,350,1300},0),attributes);
            openPlayer(false);
        } catch (RuntimeException e) { finishSound(AssistantAlarm.FAILED); return START_NOT_STICKY; }
        return START_STICKY;
    }
    private void openPlayer(boolean useFallback) {
        if (ended || !valid()) return;
        if (player != null) player.release();
        player = new MediaPlayer(); MediaPlayer candidate = player; prepared = false; fallback = useFallback;
        candidate.setAudioAttributes(attributes); candidate.setLooping(true);
        candidate.setOnPreparedListener(p -> { if (p == player && !ended) { prepared = true; startPrepared(); } });
        candidate.setOnErrorListener((p, what, extra) -> {
            if (p == player && !ended) main.post(() -> { if (!ended) { if (!fallback) openPlayer(true); else finishSound(AssistantAlarm.FAILED); } });
            return true;
        });
        try {
            if (useFallback) {
                try (AssetFileDescriptor asset = getResources().openRawResourceFd(R.raw.alarm_chime)) {
                    candidate.setDataSource(asset.getFileDescriptor(),asset.getStartOffset(),asset.getLength());
                }
            } else candidate.setDataSource(this,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            candidate.prepareAsync();
        } catch (Exception e) { if (!useFallback) openPlayer(true); else finishSound(AssistantAlarm.FAILED); }
    }
    private void startPrepared() {
        if (!ended && prepared && hasFocus && valid()) {
            try { player.start(); } catch (RuntimeException e) { finishSound(AssistantAlarm.FAILED); }
        }
    }
    private void finishSound(String state) {
        if (ended) return; ended = true;
        if (record != null && state != null) AssistantAlarm.state(this,record,state);
        releaseSound(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    private void releaseSound() {
        main.removeCallbacksAndMessages(null);
        try { if (player != null) player.release(); } catch (RuntimeException ignored) { }
        player = null;
        try { if (vibrator != null) vibrator.cancel(); } catch (RuntimeException ignored) { }
        try { if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus); } catch (RuntimeException ignored) { }
        focus = null;
        try { if (wake != null && wake.isHeld()) wake.release(); } catch (RuntimeException ignored) { }
    }
    @Override public void onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(changed); ended = true; releaseSound(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
