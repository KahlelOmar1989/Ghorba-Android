package com.ghorba.assistant;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class OrbView extends View {
    private Bitmap bitmap;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase=0f;
    private boolean speaking=false;
    private ValueAnimator animator;
    public OrbView(Context context) {
        super(context);
        try { bitmap=BitmapFactory.decodeStream(context.getAssets().open("ghorba_orb.webp")); }
        catch(Exception ignored){}
        animator=ValueAnimator.ofFloat(0f,1f);
        animator.setDuration(2100);animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});
    }
    public void setSpeaking(boolean s){speaking=s;invalidate();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(animator!=null)animator.start();}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float cx=getWidth()/2f,cy=getHeight()/2f;
        float diameter=Math.min(getWidth()-32f,getHeight()-26f);
        float radius=diameter/2f;
        float pulse=(float)Math.sin(phase*Math.PI*2.0);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb(100,155,92,244));paint.setStrokeWidth(speaking?8f:5f);
        c.drawCircle(cx,cy,radius+5+10*pulse,paint);
        paint.setColor(Color.argb(75,63,190,255));paint.setStrokeWidth(3f);
        c.drawCircle(cx,cy,radius+13-7*pulse,paint);
        paint.setStyle(Paint.Style.FILL);
        if(bitmap!=null){
            float extra=speaking?4f*(1+Math.abs(pulse)):0f;
            RectF dest=new RectF(cx-radius-extra,cy-radius-extra,cx+radius+extra,cy+radius+extra);
            c.drawBitmap(bitmap,null,dest,paint);
        }
    }
}
