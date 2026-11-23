package app.contouros.checks;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Installed only for development; not included in the ROM. Exercises the actual target app. */
public class DeviceChecks extends Instrumentation {
    /** Runs navigation checks in the test process without restarting the HOME process. */
    public static final class Ui extends DeviceChecks { }
    private Bundle args;
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            Context c=getTargetContext();String scenario=args.getString("scenario","home");
            if(scenario.equals("backup")) {
                String name=args.getString("name","before-0.4-prefs.zip");
                if(!name.matches("[a-zA-Z0-9_.-]+\\.zip"))throw new IllegalArgumentException("Invalid backup name");
                File out=new File(c.getExternalFilesDir(null),name);
                if(out.exists())throw new IllegalStateException("Backup already exists");
                try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(out))){
                    Context[] contexts={c,c.createDeviceProtectedStorageContext()};int index=0;
                    for(Context context:contexts){
                        File directory=new File(context.getDataDir(),"shared_prefs");File[] files=directory.listFiles();
                        if(files!=null)for(File f:files)if(f.isFile()){
                            zip.putNextEntry(new ZipEntry((index==0?"ce/":"de/")+f.getName()));
                            try(FileInputStream in=new FileInputStream(f)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);}
                            zip.closeEntry();
                        }
                        index++;
                    }
                }
                result.putString("backup",out.toString());
            } else if(scenario.equals("home")) {
                Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClassName(c,"app.contouros.home.HomeActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Activity home=startActivitySync(intent);waitForIdleSync();SystemClock.sleep(1500);
                SharedPreferences prefs=c.getSharedPreferences("contour",0);
                JSONObject layout=new JSONObject(prefs.getString("layout_v1","{}"));
                if(layout.getInt("schema")!=2)throw new AssertionError("Layout not migrated");
                JSONArray tiles=layout.getJSONArray("tiles");HashSet<String> keys=new HashSet<>();
                for(int i=0;i<tiles.length();i++){
                    JSONObject tile=tiles.getJSONObject(i);String key=tile.getString("kind")+":"+tile.getString("component");
                    if(!keys.add(key))throw new AssertionError("Duplicate tile");
                }
                String before=prefs.getString("layout_v1","");
                runOnMainSync(home::recreate);SystemClock.sleep(1500);waitForIdleSync();
                if(!before.equals(prefs.getString("layout_v1","")))throw new AssertionError("Recreation changed layout");
                result.putInt("tiles",tiles.length());result.putInt("columns",prefs.getInt("grid_columns",4));
            } else if(scenario.equals("restore_history_file")) {
                File file=new File(c.getExternalFilesDir(null),"restore-test-history.json");
                JSONObject data=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
                SharedPreferences memory=c.getSharedPreferences("assistant_conversation_v1",0);
                if(!memory.getString("messages","[]").equals(data.getString("expected")))
                    throw new AssertionError("History changed since snapshot; not overwriting");
                String restored=data.getString("restored");
                if(new JSONArray(restored).length()>60)throw new AssertionError("History too long");
                if(!memory.edit().putString("messages",restored).commit())throw new AssertionError("History commit failed");
                c.deleteSharedPreferences("glitchos_device_checks");
                file.delete();
            } else if(scenario.equals("overview_gesture")) {
                int width=c.getResources().getDisplayMetrics().widthPixels;
                int height=c.getResources().getDisplayMetrics().heightPixels;
                long down=SystemClock.uptimeMillis();
                for(int i=0;i<=20;i++){
                    int y=height-6-i*height/50;
                    android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),
                            i==0?android.view.MotionEvent.ACTION_DOWN:android.view.MotionEvent.ACTION_MOVE,width/2f,y,0);
                    event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                    getUiAutomation().injectInputEvent(event,true);event.recycle();SystemClock.sleep(20);
                }
                SystemClock.sleep(700);
                android.view.MotionEvent up=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),
                        android.view.MotionEvent.ACTION_UP,width/2f,height-6-20*height/50,0);
                up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                getUiAutomation().injectInputEvent(up,true);up.recycle();SystemClock.sleep(700);
            } else if(scenario.equals("restore_history")) {
                SharedPreferences saved=c.getSharedPreferences("glitchos_device_checks",0);
                SharedPreferences memory=c.getSharedPreferences("assistant_conversation_v1",0);
                if(!memory.getString("messages","[]").equals(saved.getString("after","")))throw new AssertionError("History changed outside tests; not overwriting");
                if(!memory.edit().putString("messages",saved.getString("before","[]")).commit())throw new AssertionError("History commit failed");
                c.deleteSharedPreferences("glitchos_device_checks");
            } else if(scenario.equals("command") || scenario.equals("emulator_command")) {
                boolean checkpoint = scenario.equals("command");
                if (!checkpoint && !"ranchu".equals(android.os.Build.HARDWARE)
                        && !"goldfish".equals(android.os.Build.HARDWARE))
                    throw new IllegalStateException("Disposable emulator required");
                SharedPreferences saved=c.getSharedPreferences("glitchos_device_checks",0);
                String current=c.getSharedPreferences("assistant_conversation_v1",0).getString("messages","[]");
                if(checkpoint && saved.contains("after")&&!current.equals(saved.getString("after","")))
                    throw new AssertionError("History changed outside tests; preserve it before continuing");
                if(checkpoint && !saved.contains("before")&&!saved.edit().putString("before",current).commit())
                    throw new AssertionError("Cannot save history checkpoint");
                Activity a=startActivitySync(new Intent().setClassName(c,"app.contouros.home.AssistantActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
                String text=args.getString("text","");
                runOnMainSync(() -> {
                    ViewGroup content=a.findViewById(android.R.id.content);
                    EditText input=(EditText)find(content,null,true);
                    if(input==null)throw new AssertionError("Composer missing");input.setText(text);
                    View send=find(content,"↑",false);if(send==null)throw new AssertionError("Send missing");send.performClick();
                });
                waitForIdleSync();SystemClock.sleep(300);
                SharedPreferences alarms=c.createDeviceProtectedStorageContext().getSharedPreferences("assistant_alarm_v1",0);
                result.putString("alarm_state",alarms.getString("state","none"));
                result.putLong("alarm_at",alarms.getLong("at",0));
                JSONArray messages=new JSONArray(c.getSharedPreferences("assistant_conversation_v1",0).getString("messages","[]"));
                result.putString("reply",messages.getJSONObject(messages.length()-1).getString("text"));
                if(checkpoint && !saved.edit().putString("after",messages.toString()).commit())throw new AssertionError("Cannot save test history checkpoint");
            } else throw new IllegalArgumentException(scenario);
            result.putString("result","PASS");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("result","FAIL");result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}
    }
    private static View find(View view,String text,boolean edit){
        if(edit&&view instanceof EditText)return view;
        if(!edit&&view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),text,edit);if(found!=null)return found;}
        return null;
    }
}
