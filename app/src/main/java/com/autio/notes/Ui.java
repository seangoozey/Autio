package com.autio.notes;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Shared styles. Layout dimensions are dp; text sizes are sp. */
final class Ui {
    static final int BG=Color.rgb(12,19,24), SURFACE=Color.rgb(23,33,40), TEXT=Color.rgb(239,245,246), MUTED=Color.rgb(155,173,181);
    static final int MINT=Color.rgb(115,232,198), RED=Color.rgb(255,117,119), AMBER=Color.rgb(255,206,121), LINE=Color.rgb(48,64,73);
    static int dp(Context c,float n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c,int color,int radius,int border) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius));
        if(border!=0) d.setStroke(dp(c,1),border); return d;
    }
    static TextView text(Context c,String value,int size,int color) {
        TextView v=new TextView(c); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setFontFeatureSettings("tnum"); v.setIncludeFontPadding(false); return v;
    }
    static void medium(TextView v) { v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); }
    static LinearLayout column(Context c) { LinearLayout v=new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static void gap(LinearLayout p,int h) { p.addView(new View(p.getContext()),new LinearLayout.LayoutParams(1,dp(p.getContext(),h))); }
    static void install(Activity a,View content) {
        a.getWindow().setStatusBarColor(Color.TRANSPARENT); a.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        FrameLayout safe=new FrameLayout(a); safe.setBackgroundColor(BG); safe.addView(content);
        safe.setOnApplyWindowInsetsListener((v,i)->{
            if(Build.VERSION.SDK_INT>=30) { Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()); v.setPadding(b.left,b.top,b.right,b.bottom); }
            else v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());
            return i;
        });
        a.setContentView(safe); safe.requestApplyInsets();
    }
    static Button button(Context c,String label,String icon,int color,int fill,int border) {
        Button b=new Button(c); b.setText(label); b.setTextSize(17); b.setAllCaps(false); medium(b); b.setTextColor(color); b.setGravity(Gravity.CENTER);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(c,20),dp(c,12),dp(c,20),dp(c,12));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff),shape(c,fill,20,border),null));
        if(icon!=null) icon(b,icon,color); return b;
    }
    static void icon(Button b,String kind,int color) {
        Icon i=new Icon(kind,color); int s=dp(b.getContext(),22); i.setBounds(0,0,s,s); b.setCompoundDrawablesRelative(i,null,null,null); b.setCompoundDrawablePadding(dp(b.getContext(),12));
    }
    static void enabled(Button b,boolean value) { b.setEnabled(value); b.setAlpha(value?1f:.35f); }
    static String time(long ms) { long s=ms/1000; return s>=3600?String.format(java.util.Locale.US,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(java.util.Locale.US,"%02d:%02d",s/60,s%60); }
    static class Icon extends Drawable {
        final String kind; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(String kind,int color) { this.kind=kind; p.setColor(color); }
        @Override public void draw(Canvas c) {
            c.save(); c.translate(getBounds().left,getBounds().top); c.scale(getBounds().width()/24f,getBounds().height()/24f); p.setStyle(Paint.Style.FILL);
            switch(kind) {
                case "record": c.drawCircle(12,12,7,p); break;
                case "pause": c.drawRoundRect(5,4,10,20,1,1,p); c.drawRoundRect(14,4,19,20,1,1,p); break;
                case "stop": c.drawRoundRect(5,5,19,19,3,3,p); break;
                case "play": Path path=new Path(); path.moveTo(7,4); path.lineTo(20,12); path.lineTo(7,20); path.close(); c.drawPath(path,p); break;
                case "settings": p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); c.drawCircle(12,12,6,p); c.drawCircle(12,12,2,p); for(int i=0;i<8;i++) { double a=i*Math.PI/4; c.drawLine(12+(float)Math.cos(a)*6,12+(float)Math.sin(a)*6,12+(float)Math.cos(a)*9,12+(float)Math.sin(a)*9,p); } break;
                case "cancel": p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setStrokeCap(Paint.Cap.ROUND); c.drawLine(6,6,18,18,p); c.drawLine(18,6,6,18,p); break;
                case "back": p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setStrokeCap(Paint.Cap.ROUND); c.drawLine(18,12,5,12,p); c.drawLine(5,12,11,6,p); c.drawLine(5,12,11,18,p); break;
                default: c.drawRoundRect(3,9,7,15,2,2,p); c.drawRoundRect(10,3,14,21,2,2,p); c.drawRoundRect(17,6,21,18,2,2,p);
            }
            c.restore();
        }
        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { p.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

