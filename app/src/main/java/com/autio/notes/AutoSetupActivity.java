package com.autio.notes;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;

/** User-visible foreground setup for background microphone access from car controls. */
public class AutoSetupActivity extends Activity {
    private TextView status;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable() { public void run() {
        status.setText(RecorderService.carArmed?"Ready for this drive. Use Record in Android Auto.":"Tap Record in Android Auto to enable and start recording.");
        handler.postDelayed(this,500);
    }};
    private int d(int n) { return Ui.dp(this,n); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=Ui.column(this); root.setPadding(d(24),d(12),d(24),d(24));
        Button back=Ui.button(this,"Settings","back",Ui.MINT,Ui.BG,0); root.addView(back,new LinearLayout.LayoutParams(-2,d(52))); back.setOnClickListener(v->finish()); Ui.gap(root,24);
        TextView title=Ui.text(this,"Android Auto controls",28,Ui.TEXT); Ui.medium(title); root.addView(title); Ui.gap(root,16);
        TextView explanation=Ui.text(this,"The car's Record button now enables and starts recording in one step. Manual preparation is a fallback if Android asks you to enable microphone access on the phone.",16,Ui.MUTED); explanation.setLineSpacing(d(4),1); root.addView(explanation); Ui.gap(root,24);
        status=Ui.text(this,"",17,Ui.TEXT); root.addView(status); Ui.gap(root,20);
        Button enable=Ui.button(this,"Enable recording manually","record",Ui.BG,Ui.MINT,0); root.addView(enable,new LinearLayout.LayoutParams(-1,-2)); enable.setMinHeight(d(64));
        enable.setOnClickListener(v->{
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);
            else arm();
        });
        Ui.gap(root,12); Button end=Ui.button(this,"End car session",null,Ui.TEXT,Ui.SURFACE,Ui.LINE); root.addView(end,new LinearLayout.LayoutParams(-1,d(60))); end.setOnClickListener(v->{if(RecorderService.carArmed) startService(new Intent(this,RecorderService.class).setAction("DISARM"));});
        Ui.gap(root,24); TextView notes=Ui.text(this,"In Android Auto, open Autio Notes and use its custom recording actions (they may be under More). Play remains saved-note playback and never starts recording.\n\nThis prototype uses the input reported on your phone. A car-screen button alone does not guarantee that the car microphone is selected.\n\nFor this development build, Android Auto also needs Developer settings → Unknown sources enabled.",14,Ui.MUTED); notes.setLineSpacing(d(4),1); root.addView(notes);
        ScrollView scroll=new ScrollView(this); scroll.addView(root); Ui.install(this,scroll);
    }
    private void arm() { startForegroundService(new Intent(this,RecorderService.class).setAction("ARM")); }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) { super.onRequestPermissionsResult(request,permissions,results); if(request==1&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED) arm(); }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
