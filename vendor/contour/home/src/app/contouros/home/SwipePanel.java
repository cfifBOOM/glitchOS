package app.contouros.home;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** Intercept horizontal swipes only; vertical scrolling and taps stay with the child. */
final class SwipePanel extends FrameLayout {
    private final Runnable action;
    private final int direction,slop;
    private float x,y;
    private boolean horizontal;
    SwipePanel(Context c,int direction,Runnable action){super(c);this.direction=direction;this.action=action;slop=ViewConfiguration.get(c).getScaledTouchSlop();}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();horizontal=false;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
            float dx=e.getX()-x,dy=e.getY()-y;
            if(dx*direction>slop*2&&Math.abs(dx)>Math.abs(dy)*1.8f){horizontal=true;return true;}
        }
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!horizontal)return super.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_UP){
            if((e.getX()-x)*direction>Math.max(slop*3,getWidth()*.15f))action.run();
            horizontal=false;
        }else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)horizontal=false;
        return true;
    }
}
