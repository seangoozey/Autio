package com.autio.notes;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Actual RMS and peak sample levels; never displays simulated activity. */
public class InputMeterView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private double rms=-90,peak=-90;
    private boolean active;
    public InputMeterView(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    public void update(double rms,double peak,boolean active) { this.rms=rms; this.peak=peak; this.active=active; invalidate(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(),h=getHeight(),gap=Ui.dp(getContext(),3),bottom=Ui.dp(getContext(),25);
        int segments=30; float cell=(w-gap*(segments-1))/segments;
        for(int i=0;i<segments;i++) {
            double threshold=-60+(i+1)*2;
            paint.setColor(active&&rms>=threshold?(threshold>-6?Ui.RED:threshold>-18?Ui.AMBER:Ui.MINT):Ui.LINE);
            float x=i*(cell+gap); c.drawRoundRect(x,0,x+cell,h-bottom,Ui.dp(getContext(),2),Ui.dp(getContext(),2),paint);
        }
        if(active&&peak>-60) {
            float x=(float)Math.min(1,Math.max(0,(peak+60)/60))*w; paint.setColor(Ui.TEXT);
            c.drawRoundRect(Math.max(0,x-Ui.dp(getContext(),1)),0,Math.min(w,x+Ui.dp(getContext(),1)),h-bottom,2,2,paint);
        }
        paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL)); paint.setTextSize(Ui.dp(getContext(),11)); paint.setColor(Ui.MUTED);
        for(int tick:new int[]{-60,-36,-18,0}) {
            String label=String.valueOf(tick); float x=(tick+60)/60f*w;
            x=Math.max(0,Math.min(w-paint.measureText(label),x-paint.measureText(label)/2)); c.drawText(label,x,h-Ui.dp(getContext(),3),paint);
        }
    }
}

