package app.contouros.home;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

public final class AssistantActivity extends Activity {
    static final String SHOW_WELCOME = "show_welcome";
    static final String WELCOME_DONE = "welcome_done";
    private static long lastClose = -1;
    private SharedPreferences quietPrefs, alarmPrefs;
    private ConversationStore memory;
    private PresenceView character;
    private TextView speech, status;
    private TextView question;
    private LinearLayout choices;
    private Button yes, no, callBack;
    private PresenceMode displayedMode;
    private EditText input;
    private ScrollView conversation;
    private boolean history, resumed, typing;
    private String reply;
    private final SharedPreferences.OnSharedPreferenceChangeListener changed = (p,key) -> refresh();
    private final BroadcastReceiver powerChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (resumed) AssistantPresence.observe(AssistantActivity.this);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        quietPrefs = getSharedPreferences(QuietTile.PREFERENCES, MODE_PRIVATE);
        alarmPrefs = AssistantAlarm.prefs(this);
        memory = new ConversationStore(this);
        AssistantAlarm.channel(this);
        List<ConversationStore.Message> messages = memory.read();
        if (state != null) { reply = state.getString("reply", "Я здесь."); history = state.getBoolean("history"); }
        else if (messages.isEmpty()) {
            reply = "О. Ты уже здесь? Привет.\nЯ пока осваиваюсь. Давай начнём с чего-нибудь простого.";
            memory.add(false,reply);
        } else if (AssistantPresence.read(this) == PresenceMode.HERE
                && lastClose >= 0 && SystemClock.elapsedRealtime()-lastClose < 6000) {
            reply = "Уже вернулся? Я ещё здесь."; memory.add(false,reply);
        } else reply = messages.get(messages.size()-1).text;
        render();
        if (state != null) input.setText(state.getString("draft", ""));
    }
    private void render() {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xff11121c);
        page.setOnApplyWindowInsetsListener((v,insets) -> {
            Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            Insets keyboard=insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(dp(16)+safe.left,safe.top,dp(16)+safe.right,Math.max(safe.bottom,keyboard.bottom));
            typing=insets.isVisible(WindowInsets.Type.ime());
            if(character!=null && conversation!=null){
                character.setFullPortrait(!typing);
                character.setCompact(typing);
                character.setLayoutParams(typing ? new LinearLayout.LayoutParams(-1,dp(112)) : new LinearLayout.LayoutParams(-1,0,1));
                sizeConversation();
            }
            return insets;
        });
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(button("‹",this::finish),new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView name=label("glitchOS",15,0xffbbf2cf); top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(button("История",() -> { history=!history; updateSpeech(); }),new LinearLayout.LayoutParams(-2,dp(48)));
        top.addView(button("Стоп",() -> respond(LocalAssistant.stop(this))),new LinearLayout.LayoutParams(-2,dp(48)));
        page.addView(top);
        character=new PresenceView(this); character.setFullPortrait(true);
        page.addView(character,new LinearLayout.LayoutParams(-1,0,1));
        conversation=new ScrollView(this); conversation.setFillViewport(false);
        speech=label("",20,0xffefeafa); speech.setPadding(dp(8),dp(12),dp(8),dp(12));
        LinearLayout dialogue = new LinearLayout(this);
        dialogue.setOrientation(LinearLayout.VERTICAL);
        dialogue.addView(speech);
        question = label("", 16, 0xffefeafa);
        question.setPadding(dp(8), 0, dp(8), dp(6));
        dialogue.addView(question);
        choices = new LinearLayout(this);
        yes = button("", () -> answerChoice(true));
        no = button("", () -> answerChoice(false));
        callBack = button("Позвать обратно", () -> sendText("Вернись"));
        choices.addView(yes, new LinearLayout.LayoutParams(0, -2, 1));
        choices.addView(no, new LinearLayout.LayoutParams(0, -2, 1));
        choices.addView(callBack, new LinearLayout.LayoutParams(-2, -2));
        dialogue.addView(choices);
        conversation.addView(dialogue,new ScrollView.LayoutParams(-1,-2));
        page.addView(conversation,new LinearLayout.LayoutParams(-1,dp(124)));
        status=label("",12,0xffb5aec7); status.setPadding(dp(8),dp(4),dp(8),dp(8));
        status.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle("Журнал будильника")
            .setMessage(alarmPrefs.getString("journal","Записей пока нет.")).setPositiveButton("Закрыть",null).show(); return true; });
        page.addView(status,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout composer=new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL);
        input=new EditText(this); input.setSingleLine(true); input.setHint("Написать…"); input.setTextSize(16);
        input.setTextColor(0xffefeafa); input.setHintTextColor(0xffa6a1b5); input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setBackground(surface(0xff252536,18)); input.setPadding(dp(16),0,dp(12),0);
        input.setOnEditorActionListener((v,action,event) -> { if(action==EditorInfo.IME_ACTION_SEND){send();return true;} return false; });
        composer.addView(input,new LinearLayout.LayoutParams(0,dp(56),1));
        Button send=button("↑",this::send); send.setContentDescription("Отправить сообщение");
        composer.addView(send,new LinearLayout.LayoutParams(dp(52),dp(52))); page.addView(composer);
        page.addView(button("Настройки будильника",this::configureAlarm),new LinearLayout.LayoutParams(-1,dp(48)));
        setContentView(page); page.requestApplyInsets(); updateSpeech(); refresh();
    }
    private void send() {
        String text=input.getText().toString().trim(); if(text.isEmpty())return;
        input.setText(""); sendText(text);
    }
    private void sendText(String text) { memory.add(true,text); respond(LocalAssistant.reply(this,text)); }
    private void answerChoice(boolean confirmed) {
        if (displayedMode != AssistantPresence.read(this)) { refresh(); return; }
        sendText(confirmed ? "Да" : "Нет");
    }
    private void respond(String text) { reply=text; memory.add(false,text); history=false; updateSpeech(); refresh(); }
    private void updateSpeech() {
        if(history) {
            StringBuilder b=new StringBuilder();
            for(ConversationStore.Message m:memory.read())b.append(m.owner?"Ты: ":"Она: ").append(m.text).append("\n\n");
            speech.setTextSize(15); speech.setText(b.toString());
            conversation.post(() -> conversation.fullScroll(ScrollView.FOCUS_DOWN));
        } else { speech.setTextSize(20); speech.setText(reply); conversation.scrollTo(0,0); }
        updateChoices();
    }
    private void updateChoices() {
        displayedMode = AssistantPresence.read(this);
        boolean permission = displayedMode == PresenceMode.ASKING;
        boolean offer = displayedMode == PresenceMode.BATTERY_OFFER;
        boolean asking = permission || offer;
        String prompt = permission ? AssistantPresence.PERMISSION_QUESTION : AssistantPresence.BATTERY_QUESTION;
        question.setText(prompt);
        question.setVisibility(!history && asking && !prompt.equals(reply) ? View.VISIBLE : View.GONE);
        choices.setVisibility(!history && (asking || displayedMode.isAway()) ? View.VISIBLE : View.GONE);
        yes.setText(permission ? "Можно предлагать" : "Открыть настройки");
        no.setText(permission ? "Без подсказок" : "Не сейчас");
        yes.setVisibility(asking ? View.VISIBLE : View.GONE);
        no.setVisibility(asking ? View.VISIBLE : View.GONE);
        callBack.setVisibility(displayedMode.isAway() && !asking ? View.VISIBLE : View.GONE);
        sizeConversation();
    }
    private void sizeConversation() {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) conversation.getLayoutParams();
        if (params == null) return;
        int height = typing ? 0 : dp(choices.getVisibility() == View.VISIBLE ? 184 : 124);
        float weight = typing ? 1 : 0;
        if (params.height != height || params.weight != weight) {
            params.height = height;
            params.weight = weight;
            conversation.setLayoutParams(params);
        }
    }
    private void refresh() {
        if(status==null)return;
        status.setText(LocalAssistant.status(this));
        PowerManager power=getSystemService(PowerManager.class);
        updateChoices();
        character.setAway(displayedMode.isAway());
        character.setMoving(resumed && ValueAnimator.areAnimatorsEnabled() && (power==null||!power.isPowerSaveMode())
                && !quietPrefs.getBoolean(QuietTile.QUIET,false));
    }
    private void configureAlarm() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            if(quietPrefs.getBoolean("asked_notifications",false) && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)){
                try{startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}
                catch(RuntimeException e){respond("Открой уведомления glitchOS в настройках Android.");}
                return;
            }
            quietPrefs.edit().putBoolean("asked_notifications",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);return;
        }
        NotificationManager n=getSystemService(NotificationManager.class);
        Intent target;
        if(!n.areNotificationsEnabled()) target=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
        else if(n.getNotificationChannel(AssistantAlarm.CHANNEL).getImportance()<NotificationManager.IMPORTANCE_HIGH)
            target=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,AssistantAlarm.CHANNEL);
        else if(!getSystemService(AlarmManager.class).canScheduleExactAlarms())target=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()));
        else if(Build.VERSION.SDK_INT>=34 && !n.canUseFullScreenIntent())target=new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName()));
        else target=new Intent(Settings.ACTION_SOUND_SETTINGS);
        try{startActivity(target);}catch(RuntimeException e){respond("Не получилось открыть этот раздел настроек. Разрешения доступны в настройках glitchOS.");}
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("reply",reply);state.putBoolean("history",history);state.putString("draft",input.getText().toString());}
    @Override protected void onStart(){super.onStart();alarmPrefs.registerOnSharedPreferenceChangeListener(changed);quietPrefs.registerOnSharedPreferenceChangeListener(changed);}
    @Override protected void onStop(){alarmPrefs.unregisterOnSharedPreferenceChangeListener(changed);quietPrefs.unregisterOnSharedPreferenceChangeListener(changed);super.onStop();}
    @Override protected void onResume() {
        super.onResume();
        AssistantAlarm.reconcile(this);
        resumed = true;
        IntentFilter power = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        power.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(powerChanged, power, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(powerChanged, power);
        AssistantPresence.observe(this);
        refresh();
    }
    @Override protected void onPause() {
        resumed = false;
        unregisterReceiver(powerChanged);
        character.setMoving(false);
        if (isFinishing()) lastClose = SystemClock.elapsedRealtime();
        super.onPause();
    }
    private TextView label(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    private Button button(String value,Runnable action){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),0,dp(8),0);b.setTextSize(13);b.setTextColor(0xffbbf2cf);b.setBackground(surface(0xff252536,16));b.setOnClickListener(v->action.run());return b;}
    private android.graphics.drawable.Drawable surface(int color,int radius){
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33bbf2cf),shape,null);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
