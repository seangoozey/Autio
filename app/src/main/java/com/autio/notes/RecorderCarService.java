package com.autio.notes;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.car.app.*;
import androidx.core.graphics.drawable.IconCompat;
import androidx.car.app.media.MediaPlaybackManager;
import androidx.car.app.model.*;
import androidx.car.app.validation.HostValidator;
import androidx.lifecycle.*;
import java.io.File;
import java.text.DateFormat;
import java.util.*;

/** A dedicated recorder surface; all commands use the existing media/recorder pipeline. */
@androidx.annotation.OptIn(markerClass=androidx.car.app.annotations.ExperimentalCarApi.class)
public class RecorderCarService extends CarAppService {
    static CarIcon icon(android.content.Context context,int resource) { return new CarIcon.Builder(IconCompat.createWithResource(context,resource)).build(); }
    @Override public HostValidator createHostValidator() {
        if((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)!=0)
            return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
        return new HostValidator.Builder(this).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build();
    }
    @Override public Session onCreateSession() { return new RecorderSession(); }

    class RecorderSession extends Session implements DefaultLifecycleObserver {
        MediaBrowserCompat browser;
        MediaControllerCompat controller;
        RecorderScreen screen;
        boolean playbackRequested;
        String connectionError;
        @Override public Screen onCreateScreen(Intent intent) {
            HostMicrophone.attach(this,getCarContext());
            screen=new RecorderScreen(this);
            getLifecycle().addObserver(this);
            playbackRequested=isPlayback(intent);
            browser=new MediaBrowserCompat(getCarContext(),new ComponentName(getCarContext(),AutoMediaService.class),new MediaBrowserCompat.ConnectionCallback() {
                @Override public void onConnected() {
                    controller=new MediaControllerCompat(getCarContext(),browser.getSessionToken());
                    getCarContext().getCarService(MediaPlaybackManager.class).registerMediaPlaybackToken(browser.getSessionToken());
                    connectionError=null; screen.invalidate();
                    if(playbackRequested) showPlayback();
                }
                @Override public void onConnectionFailed() { connectionError="Could not connect to Autio. Reopen the app to try again."; screen.invalidate(); }
                @Override public void onConnectionSuspended() { controller=null; connectionError="Connection interrupted. Reopen Autio."; screen.invalidate(); }
            },null);
            browser.connect();
            return screen;
        }
        boolean isPlayback(Intent intent) { return intent!=null&&"androidx.car.app.media.action.SHOW_MEDIA_PLAYBACK".equals(intent.getAction()); }
        @Override public void onNewIntent(Intent intent) { if(isPlayback(intent)) { playbackRequested=true; if(controller!=null) showPlayback(); } }
        void showPlayback() {
            playbackRequested=false;
            ScreenManager screens=getCarContext().getCarService(ScreenManager.class);
            if(!(screens.getTop() instanceof PlaybackScreen)) screens.push(new PlaybackScreen(this));
        }
        @Override public void onDestroy(LifecycleOwner owner) { if(HostMicrophone.detach(this)) { if(RecorderService.hasNote&&!RecorderService.saving&&"Android Auto host microphone".equals(RecorderService.inputSource)) getCarContext().startService(new Intent(getCarContext(),RecorderService.class).setAction("HOST_DISCONNECTED")); } if(browser!=null) browser.disconnect(); controller=null; }
    }
    /** Explicit controls avoid dependence on the developer host's playback-template lookup. */
    static class PlaybackScreen extends Screen implements DefaultLifecycleObserver {
        final RecorderSession session;
        final Handler handler=new Handler(Looper.getMainLooper());
        final Runnable refresh=new Runnable() { @Override public void run() { invalidate(); handler.postDelayed(this,1000); }};
        PlaybackScreen(RecorderSession session) { super(session.getCarContext()); this.session=session; getLifecycle().addObserver(this); }
        @Override public void onStart(LifecycleOwner owner) { handler.post(refresh); }
        @Override public void onStop(LifecycleOwner owner) { handler.removeCallbacks(refresh); }
        @Override public Template onGetTemplate() {
            MediaControllerCompat controller=session.controller;
            PlaybackStateCompat state=controller==null?null:controller.getPlaybackState();
            MediaMetadataCompat metadata=controller==null?null:controller.getMetadata();
            boolean playing=state!=null&&state.getState()==PlaybackStateCompat.STATE_PLAYING;
            boolean note=RecorderService.hasNote||RecorderService.saving;
            String title=metadata==null?"Saved note":metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
            String detail=note?"Stop and save your open note before playback":state!=null&&state.getErrorMessage()!=null?state.getErrorMessage().toString():playing?"Playing":"Playback stopped or paused";
            Pane.Builder pane=new Pane.Builder().addRow(new Row.Builder().setTitle(title==null?"Saved note":title).addText(detail).build());
            if(!note&&state!=null&&metadata!=null) pane.addRow(new Row.Builder().setTitle(Ui.time(Math.max(0,state.getPosition()))+" / "+Ui.time(Math.max(0,metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)))).build());
            if(controller!=null&&!note) {
                pane.addAction(new Action.Builder().setTitle(playing?"Pause":"Play").setIcon(icon(getCarContext(),playing?R.drawable.ic_pause:R.drawable.ic_resume)).setFlags(Action.FLAG_PRIMARY).setBackgroundColor(CarColor.BLUE)
                    .setOnClickListener(()->{ PlaybackStateCompat current=controller.getPlaybackState(); if(current!=null&&current.getState()==PlaybackStateCompat.STATE_PLAYING) controller.getTransportControls().pause(); else controller.getTransportControls().play(); }).build());
                pane.addAction(new Action.Builder().setTitle("Stop").setIcon(icon(getCarContext(),R.drawable.ic_stop)).setOnClickListener(()->controller.getTransportControls().stop()).build());
            }
            return new PaneTemplate.Builder(pane.build()).setHeader(new Header.Builder().setTitle("Saved note playback").setStartHeaderAction(Action.BACK).build()).build();
        }
    }

    static class RecorderScreen extends Screen implements DefaultLifecycleObserver {
        final RecorderSession session;
        final Handler handler=new Handler(Looper.getMainLooper());
        String rendered="";
        boolean visible;
        final Runnable refresh=new Runnable() { @Override public void run() {
            CharSequence error=session.controller==null||session.controller.getPlaybackState()==null?null:session.controller.getPlaybackState().getErrorMessage();
            String key=RecorderService.state+"/"+RecorderService.detail+"/"+RecorderService.hasNote+"/"+RecorderService.saving+"/"+(RecorderService.recordedMs/1000)+"/"+(int)(RecorderService.db/6)+"/"+session.connectionError+"/"+error;
            if(!key.equals(rendered)) { rendered=key; invalidate(); }
            if(visible) handler.postDelayed(this,1000);
        }};
        RecorderScreen(RecorderSession session) { super(session.getCarContext()); this.session=session; getLifecycle().addObserver(this); }
        @Override public void onStart(LifecycleOwner owner) { HostMicrophone.attach(session,getCarContext()); visible=true; handler.post(refresh); }
        @Override public void onStop(LifecycleOwner owner) { visible=false; handler.removeCallbacks(refresh); }
        void command(String action) {
            // The screen handling the tap is authoritative, including after host reconnection.
            HostMicrophone.attach(session,getCarContext());
            if(session.controller!=null) session.controller.getTransportControls().sendCustomAction(action,null);
        }
        @Override public Template onGetTemplate() {
            if(session.controller==null&&session.connectionError==null)
                return new PaneTemplate.Builder(new Pane.Builder().setLoading(true).build()).setHeader(header()).build();
            boolean note=RecorderService.hasNote, saving=RecorderService.saving;
            boolean recording=note&&"Recording".equals(RecorderService.state);
            String title=saving?("Discarding".equals(RecorderService.state)?"Discarding note…":"Saving note…"):note?(recording?"Recording":"Paused"):"Ready to record";
            String detail=session.connectionError;
            if(detail==null&&session.controller!=null&&session.controller.getPlaybackState()!=null
                &&session.controller.getPlaybackState().getErrorMessage()!=null)
                detail=session.controller.getPlaybackState().getErrorMessage().toString();
            if(detail==null) detail=RecorderService.detail;
            Pane.Builder pane=new Pane.Builder().addRow(new Row.Builder().setTitle(title).addText(detail).build());
            if(note||saving) pane.addRow(new Row.Builder().setTitle(Ui.time(RecorderService.recordedMs))
                .addText(recording?String.format(Locale.US,"Input %.0f dBFS · %s",RecorderService.db,RecorderService.inputSource):saving?RecorderService.detail:"Resume continues this same note").build());
            else pane.addRow(new Row.Builder().setTitle("Audio notes").addText("Record a new note, or open Saved notes above").build());
            if(!saving&&session.controller!=null) {
                pane.addAction(new Action.Builder().setTitle(!note?"Record":recording?"Pause":"Resume").setIcon(icon(getCarContext(),!note?R.drawable.ic_record:recording?R.drawable.ic_pause:R.drawable.ic_resume))
                    .setFlags(Action.FLAG_PRIMARY).setBackgroundColor(!note?CarColor.RED:CarColor.BLUE)
                    .setOnClickListener(()->command(!RecorderService.hasNote?AutoMediaService.RECORD:"Recording".equals(RecorderService.state)?AutoMediaService.PAUSE:AutoMediaService.RESUME)).build());
                if(note) pane.addAction(new Action.Builder().setTitle("Finish note…").setIcon(icon(getCarContext(),R.drawable.ic_stop)).setOnClickListener(()->getScreenManager().push(new FinishScreen(session))).build());
            }
            return new PaneTemplate.Builder(pane.build()).setHeader(header()).build();
        }
        Header header() {
            Header.Builder header=new Header.Builder().setTitle("Autio Notes").setStartHeaderAction(Action.APP_ICON);
            return header.addEndHeaderAction(new Action.Builder().setTitle("Saved notes").setOnClickListener(()->getScreenManager().push(new NotesScreen(session))).build()).build();
        }
    }
    static class FinishScreen extends Screen {
        final RecorderSession session;
        final long generation=RecorderService.noteGeneration;
        FinishScreen(RecorderSession session) { super(session.getCarContext()); this.session=session; }
        @Override public Template onGetTemplate() {
            return new MessageTemplate.Builder("Save this note, or discard it permanently. Back keeps the note open.").setTitle("Finish this recording?").setHeaderAction(Action.BACK)
                .addAction(new Action.Builder().setTitle("Stop & save").setIcon(icon(getCarContext(),R.drawable.ic_stop)).setOnClickListener(()->{
                    if(session.controller!=null&&RecorderService.hasNote&&generation==RecorderService.noteGeneration) session.controller.getTransportControls().sendCustomAction(AutoMediaService.SAVE,null);
                    getScreenManager().pop();
                }).build())
                .addAction(new Action.Builder().setTitle("Discard").setIcon(icon(getCarContext(),R.drawable.ic_cancel)).setOnClickListener(()->{
                    if(session.controller!=null&&RecorderService.hasNote&&generation==RecorderService.noteGeneration) session.controller.getTransportControls().sendCustomAction(AutoMediaService.CANCEL,null);
                    getScreenManager().pop();
                }).build()).build();
        }
    }
    static class NotesScreen extends Screen {
        final RecorderSession session;
        NotesScreen(RecorderSession session) { super(session.getCarContext()); this.session=session; }
        @Override public Template onGetTemplate() {
            ItemList.Builder list=new ItemList.Builder().setNoItemsMessage("No saved notes yet");
            LibraryNote[] notes=NoteStorage.library(getCarContext());
            for(int i=0;i<Math.min(notes.length,50);i++) {
                LibraryNote note=notes[i];
                list.addItem(new Row.Builder().setTitle(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(note.lastModified())))
                    .addText(note.knownDuration(getCarContext())>=0?Ui.time(note.knownDuration(getCarContext())):android.text.format.Formatter.formatShortFileSize(getCarContext(),note.size)).setOnClickListener(()->{
                        if(RecorderService.hasNote||RecorderService.saving) { CarToast.makeText(getCarContext(),"Stop and save your open note first",CarToast.LENGTH_LONG).show(); return; }
                        if(session.controller!=null) { session.controller.getTransportControls().playFromMediaId(note.getName(),null); session.showPlayback(); }
                    }).build());
            }
            return new ListTemplate.Builder().setSingleList(list.build()).setHeader(new Header.Builder().setTitle("Saved notes").setStartHeaderAction(Action.BACK).build()).build();
        }
    }
}
