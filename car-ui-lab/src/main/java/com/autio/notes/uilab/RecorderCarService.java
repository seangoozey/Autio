package com.autio.notes.uilab;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import androidx.car.app.*;
import androidx.car.app.media.MediaPlaybackManager;
import androidx.car.app.model.*;
import androidx.car.app.validation.HostValidator;

/** Debug-only UI probe. It has no microphone permission and cannot affect real recordings. */
@androidx.annotation.OptIn(markerClass=androidx.car.app.annotations.ExperimentalCarApi.class)
public class RecorderCarService extends CarAppService {
    @Override public HostValidator createHostValidator() {
        if((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0) return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
        return new HostValidator.Builder(this).build();
    }
    @Override public Session onCreateSession() {
        return new Session() {
            @Override public Screen onCreateScreen(Intent intent) {
                getCarContext().getCarService(MediaPlaybackManager.class).registerMediaPlaybackToken(LabMediaService.session(getCarContext()).getSessionToken());
                return new RecorderScreen(getCarContext());
            }
        };
    }
    static class RecorderScreen extends Screen {
        private int state;
        RecorderScreen(CarContext context) { super(context); }
        @Override public Template onGetTemplate() {
            String title=state==0?"Ready to record":state==1?"Recording layout":"Paused layout";
            Pane.Builder pane=new Pane.Builder()
                .addRow(new Row.Builder().setTitle(title).addText("Interface preview · no audio is captured").build())
                .addRow(new Row.Builder().setTitle("00:00").addText("Testing button layout and state changes").build());
            pane.addAction(new Action.Builder().setTitle(state==0?"Record":state==1?"Pause":"Resume")
                .setFlags(Action.FLAG_PRIMARY).setBackgroundColor(state==0?CarColor.RED:CarColor.BLUE)
                .setOnClickListener(()->{state=state==1?2:1; invalidate();}).build());
            if(state!=0) pane.addAction(new Action.Builder().setTitle("Stop & save").setOnClickListener(()->{state=0; invalidate();}).build());
            return new PaneTemplate.Builder(pane.build())
                .setHeader(new Header.Builder().setTitle("Autio UI Lab").setStartHeaderAction(Action.APP_ICON).build()).build();
        }
    }
}
