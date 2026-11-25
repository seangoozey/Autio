package com.autio.notes.uilab;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class LabActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view=new TextView(this); view.setTextSize(22); view.setPadding(40,100,40,40);
        view.setText("Autio UI Lab\n\nOpen this app from Android Auto to test the recording screen layout.\n\nThis separate experiment does not record audio or alter Autio Notes. It needs the Android Auto CAL beta feature enabled.");
        setContentView(view);
    }
}
