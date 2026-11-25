package com.autio.notes;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

public class SettingsActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=Ui.column(this); int pad=Ui.dp(this,24); root.setPadding(pad,pad,pad,pad);
        Button back=Ui.button(this,"Recorder","back",Ui.MINT,Ui.BG,0); root.addView(back); back.setOnClickListener(v->finish()); Ui.gap(root,24);
        root.addView(Ui.text(this,"Settings",32,Ui.TEXT)); Ui.gap(root,24);
        Button processing=Ui.button(this,"Microphone processing",null,Ui.TEXT,Ui.SURFACE,Ui.LINE);
        root.addView(processing);
        processing.setOnClickListener(v->startActivity(new Intent(this,ProcessingActivity.class)));
        Ui.gap(root,12);
        Button delivery=Ui.button(this,"Note delivery",null,Ui.TEXT,Ui.SURFACE,Ui.LINE); root.addView(delivery); delivery.setOnClickListener(v->startActivity(new Intent(this,DeliveryActivity.class))); Ui.gap(root,12);
        Button car=Ui.button(this,"Android Auto controls",null,Ui.TEXT,Ui.SURFACE,Ui.LINE); root.addView(car); car.setOnClickListener(v->startActivity(new Intent(this,AutoSetupActivity.class)));
        Ui.gap(root,24); root.addView(Ui.text(this,"The managed folder is your library. Private audio is retained only until delivery succeeds. Deleting or moving a delivered note out of that folder removes it from Autio's library. Existing Music/Autio copies from older versions are unchanged.",15,Ui.MUTED));
        ScrollView scroll=new ScrollView(this); scroll.addView(root); Ui.install(this,scroll);
    }
}
