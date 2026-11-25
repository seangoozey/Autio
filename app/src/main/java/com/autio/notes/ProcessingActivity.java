package com.autio.notes;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;

public class ProcessingActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView session;
    private Button source, path;
    private final Button[] effects=new Button[CaptureProcessing.EFFECT_KEYS.length];
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            String report=RecorderService.processingReport;
            if(report.isEmpty()) report=CaptureProcessing.prefs(ProcessingActivity.this).getString("last_report","Start a recording to inspect its input and processing.");
            session.setText(("Recording".equals(RecorderService.state)?"Current session\n":"Last session\n")+report);
            handler.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=Ui.column(this);
        int padding=Ui.dp(this,24); root.setPadding(padding,padding,padding,padding);
        Button back=Ui.button(this,"Settings","back",Ui.MINT,Ui.BG,0); root.addView(back); back.setOnClickListener(v->finish());
        Ui.gap(root,20); root.addView(Ui.text(this,"Microphone processing",28,Ui.TEXT));
        Ui.gap(root,12); root.addView(Ui.text(this,"Changes apply when you start or resume recording. Compare short recordings with the same microphone position and speaking volume.",15,Ui.MUTED));
        Ui.gap(root,20);
        path=Ui.button(this,"",null,Ui.TEXT,Ui.SURFACE,Ui.LINE); root.addView(path);
        path.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Capture path").setSingleChoiceItems(new String[]{"Phone / Bluetooth (existing)","Android Auto host (experimental)"},CaptureProcessing.prefs(this).getBoolean("host_capture",false)?1:0,(dialog,which)->{ CaptureProcessing.prefs(this).edit().putBoolean("host_capture",which==1).apply(); updateLabels(); dialog.dismiss(); }).setNegativeButton("Close",null).show());
        Ui.gap(root,12); root.addView(Ui.text(this,"Host capture requires Autio open in Android Auto. Audio streams to this phone and saves normally. If access ends, the note pauses. Phone source and effect controls apply only to Phone / Bluetooth.",14,Ui.MUTED));
        Ui.gap(root,16);
        source=Ui.button(this,"",null,Ui.TEXT,Ui.SURFACE,Ui.LINE); root.addView(source); source.setOnClickListener(v->chooseSource());
        Ui.gap(root,12); root.addView(Ui.text(this,"Microphone is the original capture mode. Voice recognition favors speech capture. Voice communication requests a call-oriented path. Unprocessed requests minimal processing when supported. Changing the source may also change the microphone route.",14,Ui.MUTED));
        for(int i=0;i<effects.length;i++) {
            final int index=i;
            Ui.gap(root,16); effects[i]=Ui.button(this,"",null,Ui.TEXT,Ui.SURFACE,Ui.LINE); root.addView(effects[i]);
            effects[i].setOnClickListener(v->new AlertDialog.Builder(this).setTitle(CaptureProcessing.EFFECT_NAMES[index])
                .setSingleChoiceItems(CaptureProcessing.MODES,CaptureProcessing.mode(CaptureProcessing.prefs(this),CaptureProcessing.EFFECT_KEYS[index]),(dialog,which)->{
                    CaptureProcessing.prefs(this).edit().putInt(CaptureProcessing.EFFECT_KEYS[index],which).apply(); updateLabels(); dialog.dismiss();
                }).setNegativeButton("Close",null).show());
        }
        Ui.gap(root,16); root.addView(Ui.text(this,"Gain control adjusts voice level. Noise suppression reduces background sound. Echo cancellation reduces speaker sound picked up by the microphone. Android default leaves each effect under system control. Requests can be rejected for a particular input or source.",14,Ui.MUTED));
        Ui.gap(root,16); root.addView(Ui.text(this,"These controls affect Autio's Android capture session. Processing inside the Binize or Bluetooth link may remain active even when an Android effect reports Off.",14,Ui.AMBER));
        Ui.gap(root,20);
        Button reset=Ui.button(this,"Restore defaults",null,Ui.MINT,Ui.BG,Ui.LINE); root.addView(reset);
        reset.setOnClickListener(v->{ CaptureProcessing.prefs(this).edit().remove("host_capture").remove("source").remove("agc").remove("ns").remove("aec").apply(); updateLabels(); });
        Ui.gap(root,24); session=Ui.text(this,"",14,Ui.TEXT); session.setTextIsSelectable(true); root.addView(session);
        ScrollView scroll=new ScrollView(this); scroll.addView(root); Ui.install(this,scroll); updateLabels();
    }
    private void updateLabels() {
        boolean host=CaptureProcessing.prefs(this).getBoolean("host_capture",false);
        path.setText("Capture path\n"+(host?"Android Auto host (experimental)":"Phone / Bluetooth"));
        Ui.enabled(source,!host);
        source.setText("Audio source\n"+CaptureProcessing.sourceName(CaptureProcessing.selectedSource(this)));
        for(int i=0;i<effects.length;i++) {
            boolean available=CaptureProcessing.available(i);
            effects[i].setText(CaptureProcessing.EFFECT_NAMES[i]+"\n"+CaptureProcessing.MODES[CaptureProcessing.mode(CaptureProcessing.prefs(this),CaptureProcessing.EFFECT_KEYS[i])]+(available?"":" · unavailable on this device"));
            Ui.enabled(effects[i],available&&!host);
        }
    }
    private void chooseSource() {
        int selected=0;
        for(int i=0;i<CaptureProcessing.SOURCES.length;i++) if(CaptureProcessing.SOURCES[i]==CaptureProcessing.selectedSource(this)) selected=i;
        String[] labels=CaptureProcessing.SOURCE_NAMES.clone();
        if(!CaptureProcessing.supportsUnprocessed(this)) labels[3]+=" (unsupported on this device)";
        new AlertDialog.Builder(this).setTitle("Audio source").setSingleChoiceItems(labels,selected,(dialog,which)->{
            if(which==3&&!CaptureProcessing.supportsUnprocessed(this)) { Toast.makeText(this,"This phone does not advertise unprocessed capture",Toast.LENGTH_LONG).show(); return; }
            CaptureProcessing.prefs(this).edit().putInt("source",CaptureProcessing.SOURCES[which]).apply(); updateLabels(); dialog.dismiss();
        }).setNegativeButton("Close",null).show();
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
