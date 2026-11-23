package app.contouros.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import java.lang.ref.WeakReference;

/** The usual portrait is a crop of the concept. The seated pose is a separate asset. */
final class PresenceView extends View {
    private static WeakReference<Bitmap> shared = new WeakReference<>(null);
    private static WeakReference<Bitmap> sharedSeated = new WeakReference<>(null);
    private final Bitmap portrait;
    private Bitmap seated;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect source = new Rect();
    private final RectF target = new RectF();
    private boolean fullPortrait, moving, compact, away;
    private final Runnable frame = new Runnable() {
        @Override public void run() { if (moving && isShown() && isAttachedToWindow()) { invalidate(); postDelayed(this,50); } }
    };
    PresenceView(Context c) {
        super(c);
        Bitmap image = shared.get();
        if (image == null) {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false;
            image = BitmapFactory.decodeResource(getResources(),R.drawable.assistant_storyboard,options);
            shared = new WeakReference<>(image);
        }
        portrait = image; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void setCompact(boolean value) { compact=value; invalidate(); }
    void setFullPortrait(boolean full) { fullPortrait = full; invalidate(); }
    void setAway(boolean value) {
        if (away == value) return;
        away = value;
        if (away) {
            setMoving(false);
            seated = sharedSeated.get();
            if (seated == null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inScaled = false;
                options.inSampleSize = 2;
                seated = BitmapFactory.decodeResource(getResources(), R.drawable.assistant_seated, options);
                sharedSeated = new WeakReference<>(seated);
            }
        }
        invalidate();
    }
    void setMoving(boolean value) {
        boolean next = value && !away;
        if (moving == next) return;
        moving = next;
        removeCallbacks(frame);
        if (moving && isShown()) post(frame);
        invalidate();
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (moving) post(frame); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(frame); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); removeCallbacks(frame); if (moving && visibility==VISIBLE) post(frame); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(0xff222132);
        if (away) {
            if (seated != null) {
                int w = getWidth(), h = getHeight();
                float fraction = Math.min(w, h) < 88 * getResources().getDisplayMetrics().density ? .82f : .57f;
                float scale = Math.min(w * fraction / seated.getWidth(), h * fraction / seated.getHeight());
                float inset = Math.min(w, h) * .035f;
                target.set(w - inset - seated.getWidth() * scale, h - inset - seated.getHeight() * scale,
                        w - inset, h - inset);
                canvas.drawBitmap(seated, null, target, paint);
            }
            return;
        }
        if (portrait == null) return;
        if (fullPortrait) source.set(1110,70,1460,806); else source.set(708,282,977,548);
        float scale = (fullPortrait || compact) ? Math.min((float)getWidth()/source.width(),(float)getHeight()/source.height())
                : Math.max((float)getWidth()/source.width(),(float)getHeight()/source.height());
        float width = source.width()*scale, height = source.height()*scale;
        float breath = moving ? (float)Math.sin(SystemClock.uptimeMillis()/1800.0) : 0;
        float x=(getWidth()-width)/2, y=(getHeight()-height)/2+breath*getHeight()*.002f;
        target.set(x,y,x+width,y+height); canvas.drawBitmap(portrait,source,target,paint);
        paint.setShader(new LinearGradient(0,getHeight()*.70f,0,getHeight(),0x00222132,0xaa11121c,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,getWidth(),getHeight(),paint); paint.setShader(null);
    }
}
