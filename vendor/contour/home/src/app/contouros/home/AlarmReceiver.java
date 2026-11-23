package app.contouros.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AssistantAlarm.restore(c); return;
        }
        AssistantAlarm.Record r = AssistantAlarm.read(c);
        if (!r.id.equals(intent.getStringExtra("id"))) return;
        if (AssistantAlarm.DISMISS.equals(action)) { AssistantAlarm.cancel(c, r.id); return; }
        if (!AssistantAlarm.FIRE.equals(action) || !AssistantAlarm.WAITING.equals(r.state)) return;
        long delay = System.currentTimeMillis() - r.at;
        if (delay < -1000) return;
        if (delay >= AssistantAlarm.RING_LIMIT_MS) { AssistantAlarm.state(c, r, AssistantAlarm.MISSED); return; }
        if (!AssistantAlarm.state(c, r, AssistantAlarm.RINGING)) return;
        try { c.startForegroundService(new Intent(c, AlarmSoundService.class).putExtra("id", r.id)); }
        catch (RuntimeException e) { AssistantAlarm.state(c, r, AssistantAlarm.FAILED); }
    }
}
