package app.contouros.home;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AlarmRingActivity extends Activity {
    private String id;
    private SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener changed = (p,key) -> check();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setShowWhenLocked(true); setTurnScreenOn(true);
        getWindow().setDecorFitsSystemWindows(false); id = getIntent().getStringExtra("id"); prefs = AssistantAlarm.prefs(this);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(0xff11121c);
        page.setOnApplyWindowInsetsListener((v,insets) -> {
            Insets s = insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            int d = Math.round(24*getResources().getDisplayMetrics().density);
            v.setPadding(d+s.left,d+s.top,d+s.right,d+s.bottom); return insets;
        });
        PresenceView girl = new PresenceView(this); girl.setFullPortrait(true);
        page.addView(girl,new LinearLayout.LayoutParams(-1,0,1));
        TextView text = new TextView(this); text.setText("Пора. Я здесь."); text.setTextSize(30); text.setTextColor(0xffefeafa); text.setGravity(Gravity.CENTER);
        page.addView(text,new LinearLayout.LayoutParams(-1,-2));
        Button stop = new Button(this); stop.setAllCaps(false); stop.setText("Я проснулся · остановить");
        stop.setOnClickListener(v -> { AssistantAlarm.cancel(this,id); finish(); });
        page.addView(stop,new LinearLayout.LayoutParams(-1,Math.round(64*getResources().getDisplayMetrics().density)));
        setContentView(page); page.requestApplyInsets(); check();
    }
    private void check() { AssistantAlarm.Record r = AssistantAlarm.read(this); if (!r.id.equals(id)||!AssistantAlarm.RINGING.equals(r.state)) finish(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); id = intent.getStringExtra("id"); check(); }
    @Override protected void onStart() { super.onStart(); prefs.registerOnSharedPreferenceChangeListener(changed); check(); }
    @Override protected void onStop() { prefs.unregisterOnSharedPreferenceChangeListener(changed); super.onStop(); }
}
