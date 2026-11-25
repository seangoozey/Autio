package com.autio.notes;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView state,duration,level,peak,detail,guidance;
    private InputMeterView meter;
    private Button primary,stop,cancel;
    private String renderedState="";
    private final Runnable refresh=new Runnable() { public void run() { render(); handler.postDelayed(this,100); } };
    private int d(int n) { return Ui.dp(this,n); }
    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root=Ui.column(this); root.setPadding(d(24),d(landscape?0:12),d(24),d(landscape?12:20));
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=Ui.text(this,"autio",28,Ui.TEXT); Ui.medium(brand); brand.setLetterSpacing(-.04f);
        Ui.Icon logo=new Ui.Icon("levels",Ui.MINT); logo.setBounds(0,0,d(25),d(25));
        brand.setCompoundDrawablesRelative(logo,null,null,null); brand.setCompoundDrawablePadding(d(10)); brand.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(brand,new LinearLayout.LayoutParams(0,d(landscape?44:56),1));
        Button library=Ui.button(this,"Saved notes",null,Ui.MINT,Ui.BG,0); library.setTextSize(14); library.setPadding(d(12),0,0,0);
        cancel=Ui.button(this,"Cancel","cancel",Ui.RED,Ui.BG,0);
        cancel.setOnClickListener(v->confirmCancel());
        header.addView(library,new LinearLayout.LayoutParams(-2,d(landscape?44:56))); library.setOnClickListener(v->startActivity(new Intent(this,NotesActivity.class))); Button settings=Ui.button(this,"","settings",Ui.MINT,Ui.BG,0); settings.setContentDescription("Settings");
        settings.setPadding(d(12),0,d(12),0); header.addView(settings,new LinearLayout.LayoutParams(d(48),d(48))); settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class))); root.addView(header);
        Ui.gap(root,32);
        LinearLayout hero=Ui.column(this); hero.setGravity(Gravity.CENTER_HORIZONTAL);
        state=Ui.text(this,"READY TO RECORD",12,Ui.MINT); Ui.medium(state); state.setLetterSpacing(.12f); state.setPadding(d(16),d(9),d(16),d(9)); hero.addView(state,new LinearLayout.LayoutParams(-2,-2));
        Ui.gap(hero,landscape?8:20);
        duration=Ui.text(this,"00:00",64,Ui.TEXT); duration.setGravity(Gravity.CENTER); duration.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        duration.setAutoSizeTextTypeUniformWithConfiguration(32,64,1,android.util.TypedValue.COMPLEX_UNIT_SP); hero.addView(duration,new LinearLayout.LayoutParams(-1,d(landscape?64:84)));
        TextView elapsed=Ui.text(this,"RECORDED TIME",11,Ui.MUTED); elapsed.setLetterSpacing(.16f); elapsed.setGravity(Gravity.CENTER); hero.addView(elapsed); root.addView(hero); Ui.gap(root,32);
        LinearLayout card=Ui.column(this); card.setPadding(d(20),d(20),d(20),d(18)); card.setBackground(Ui.shape(this,Ui.SURFACE,24,0));
        TextView heading=Ui.text(this,"MICROPHONE INPUT",11,Ui.MUTED); heading.setLetterSpacing(.12f); Ui.medium(heading); card.addView(heading); Ui.gap(card,16);
        LinearLayout readings=new LinearLayout(this); readings.setGravity(Gravity.BOTTOM);
        level=Ui.text(this,"— dBFS",24,Ui.TEXT); Ui.medium(level); readings.addView(level,new LinearLayout.LayoutParams(0,-2,1)); peak=Ui.text(this,"Peak —",12,Ui.MUTED); readings.addView(peak); card.addView(readings);
        Ui.gap(card,16); meter=new InputMeterView(this); card.addView(meter,new LinearLayout.LayoutParams(-1,d(55)));
        Ui.gap(card,12); detail=Ui.text(this,"Microphone starts when you record",13,Ui.MUTED); detail.setMinLines(2); detail.setLineSpacing(d(3),1); card.addView(detail); root.addView(card);
        root.addView(new View(this),new LinearLayout.LayoutParams(1,d(28),1));
        guidance=Ui.text(this,"Capture a thought. Pick up where you paused.",14,Ui.MUTED); guidance.setGravity(Gravity.CENTER); guidance.setMinLines(2); guidance.setLineSpacing(d(3),1); root.addView(guidance); Ui.gap(root,16);
        primary=Ui.button(this,"Record a note","record",Ui.BG,Ui.MINT,0); primary.setTextSize(20); root.addView(primary,new LinearLayout.LayoutParams(-1,d(80)));
        primary.setOnClickListener(v->{
            if(RecorderService.state.equals("Recording")) { send("PAUSE"); return; }
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1); return; }
            send(RecorderService.hasNote?"RESUME":"START");
        });
        Ui.gap(root,12); stop=Ui.button(this,"Stop & save","stop",Ui.TEXT,Ui.BG,Ui.LINE);
        LinearLayout finishControls=new LinearLayout(this); finishControls.addView(stop,new LinearLayout.LayoutParams(0,d(60),1));
        finishControls.addView(cancel,new LinearLayout.LayoutParams(-2,d(60))); root.addView(finishControls); stop.setOnClickListener(v->send("STOP"));
        Ui.gap(root,16); TextView footer=Ui.text(this,"Interruptions pause your note. You choose when to resume.",12,Ui.MUTED); footer.setGravity(Gravity.CENTER); root.addView(footer);
        if(landscape) {
            // Use the width for two independent panels rather than stacking a portrait page.
            root.removeAllViews(); root.addView(header);
            LinearLayout body=new LinearLayout(this); body.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout controls=Ui.column(this); controls.setGravity(Gravity.CENTER_VERTICAL);
            controls.addView(hero); Ui.gap(controls,12);
            controls.addView(primary,new LinearLayout.LayoutParams(-1,d(56))); Ui.gap(controls,8);
            controls.addView(finishControls,new LinearLayout.LayoutParams(-1,d(48))); stop.getLayoutParams().height=d(48); cancel.getLayoutParams().height=d(48);
            body.addView(controls,new LinearLayout.LayoutParams(0,-1,1));
            body.addView(new View(this),new LinearLayout.LayoutParams(d(24),1));
            body.addView(card,new LinearLayout.LayoutParams(0,-2,1));
            root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        }
        Ui.install(this,root);
        DeliveryJobs.kick(this);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2);
        render();
    }
    private void render() {
        String current=RecorderService.state;
        boolean recording=current.equals("Recording"),active=RecorderService.hasNote,saving=RecorderService.saving;
        duration.setText(Ui.time(RecorderService.recordedMs)); meter.update(RecorderService.db,RecorderService.peak,recording);
        level.setText(recording?String.format(Locale.US,"%.0f dBFS",RecorderService.db):"— dBFS"); peak.setText(recording?String.format(Locale.US,"Peak %.0f",RecorderService.peak):"Peak —");
        cancel.setVisibility(active&&!saving?View.VISIBLE:View.GONE);
        boolean warning=recording&&(RecorderService.peak>-.5||RecorderService.detail.startsWith("Very low")); detail.setTextColor(warning?Ui.AMBER:Ui.MUTED);
        if(recording) detail.setText(RecorderService.detail.startsWith("Listening")?"Live · "+RecorderService.inputSource:RecorderService.detail);
        else if(saving) detail.setText(RecorderService.detail);
        else if(current.equals("Saved")) detail.setText(RecorderService.detail);
        else if(active||current.equals("Recovery needed")||current.equals("Error")) detail.setText(RecorderService.detail); else detail.setText(DeliveryJobs.status.isEmpty()?"Microphone starts when you record":DeliveryJobs.status);
        String key=current+active+saving;
        if(!key.equals(renderedState)) {
            renderedState=key; int accent=recording?Ui.RED:active?Ui.AMBER:Ui.MINT;
            state.setText(saving?("Discarding".equals(current)?"DISCARDING NOTE":"SAVING NOTE"):recording?"●  RECORDING":active?"Ⅱ  PAUSED":current.equals("Saved")?"NOTE SAVED":current.equals("Recovery needed")?"RECOVERY NEEDED":"READY TO RECORD");
            state.setTextColor(accent); state.setBackground(Ui.shape(this,(accent&0xffffff)|0x18000000,24,0));
            primary.setText(saving?("Discarding".equals(current)?"Discarding…":"Saving…"):recording?"Pause recording":active?"Resume recording":"Record a note"); primary.setContentDescription(primary.getText());
            primary.setTextColor(recording?Ui.TEXT:Ui.BG);
            primary.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22ffffff),Ui.shape(this,recording?Ui.SURFACE:Ui.MINT,20,recording?Ui.LINE:0),null));
            Ui.icon(primary,recording?"pause":active?"play":"record",recording?Ui.TEXT:Ui.BG); Ui.enabled(primary,!saving); Ui.enabled(stop,active&&!saving);
            guidance.setText(saving?RecorderService.detail:recording?"Recording your note. Pause whenever you need.":active?"Your note is still open. Resume or save it.":current.equals("Saved")?"Saved to your library. Record starts a new note.":"Tap Record to start a new note.");
            state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==1&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED) send(RecorderService.hasNote?"RESUME":"START");
    }
    private void confirmCancel() {
        long generation=RecorderService.noteGeneration;
        new android.app.AlertDialog.Builder(this).setTitle("Discard this recording?")
            .setMessage("This audio note will not be saved or synced.").setNegativeButton("Keep note",null)
            .setPositiveButton("Discard",(dialog,which)->{if(RecorderService.hasNote&&generation==RecorderService.noteGeneration) send("CANCEL");}).show();
    }
    private void send(String action) {
        Intent intent=new Intent(this,RecorderService.class).setAction(action); if(action.equals("START")) startForegroundService(intent); else startService(intent);
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}

