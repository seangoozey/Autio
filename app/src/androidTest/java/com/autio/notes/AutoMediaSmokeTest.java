package com.autio.notes;

import android.app.*;
import android.content.*;
import android.os.*;
import android.support.v4.media.*;
import android.support.v4.media.session.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the same browser/session protocol used by Android Auto, on the actual phone. */
final class AutoMediaSmokeTest {
    interface Check { boolean ok(); }
    static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    static void await(Check check,String message) { for(int i=0;i<100&&!check.ok();i++) SystemClock.sleep(100); require(check.ok(),message+"; recorder="+RecorderService.state+" / "+RecorderService.detail); }
    static void run(Instrumentation test,Bundle result) throws Exception {
        Context ctx=test.getTargetContext();
        require(!RecorderService.hasNote,"A user note is open; test will not touch it");
        AtomicReference<MediaBrowserCompat> reference=new AtomicReference<>();
        CountDownLatch connected=new CountDownLatch(1);
        AtomicReference<List<MediaBrowserCompat.MediaItem>> catalog=new AtomicReference<>();
        CountDownLatch loaded=new CountDownLatch(1);
        boolean ownNote=false;
        try {
            // Deliberately connect before opening any phone activity.
            test.runOnMainSync(()->{
                MediaBrowserCompat browser=new MediaBrowserCompat(ctx,new ComponentName(ctx,AutoMediaService.class),new MediaBrowserCompat.ConnectionCallback() {
                    @Override public void onConnected() { connected.countDown(); }
                    @Override public void onConnectionFailed() { connected.countDown(); }
                },null);
                reference.set(browser); browser.connect();
            });
            require(connected.await(10,TimeUnit.SECONDS),"Media browser did not connect");
            MediaBrowserCompat browser=reference.get(); require(browser.isConnected(),"Media browser connection refused");
            require(AutoMediaService.ROOT.equals(browser.getRoot()),"Unexpected browser root");
            test.runOnMainSync(()->browser.subscribe(AutoMediaService.NOTES,new MediaBrowserCompat.SubscriptionCallback() {
                @Override public void onChildrenLoaded(String id,List<MediaBrowserCompat.MediaItem> children) { catalog.set(children); loaded.countDown(); }
                @Override public void onError(String id) { loaded.countDown(); }
            }));
            require(loaded.await(10,TimeUnit.SECONDS)&&catalog.get()!=null&&!catalog.get().isEmpty(),"Saved-note catalog unavailable");
            for(MediaBrowserCompat.MediaItem item:catalog.get()) require(item.isPlayable(),"Catalog includes a fake or unplayable entry");
            MediaControllerCompat controller=new MediaControllerCompat(ctx,browser.getSessionToken());
            await(()->controller.getPlaybackState()!=null,"No initial session state");
            require(controller.getPlaybackState().getCustomActions().stream().anyMatch(a->AutoMediaService.RECORD.equals(a.getAction())),"Record action not advertised");
            require((controller.getPlaybackState().getActions()&(PlaybackStateCompat.ACTION_SKIP_TO_NEXT|PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))==0,"Idle skip buttons displace Record");
            controller.getTransportControls().sendCustomAction(AutoMediaService.RECORD,null);
            ownNote=true;
            await(()->RecorderService.hasNote||controller.getPlaybackState().getState()==PlaybackStateCompat.STATE_ERROR,"Record neither started nor reported setup restriction");
            if(RecorderService.hasNote) {
                require(RecorderService.carArmed,"Record did not enable its drive session");
                controller.getTransportControls().stop(); await(()->!RecorderService.hasNote&&!RecorderService.saving,"Direct record did not save");
                ctx.startService(new Intent(ctx,RecorderService.class).setAction("DISARM"));
                await(()->!RecorderService.carArmed,"Test session did not end");
            }
            ownNote=false;
            Activity activity=test.startActivitySync(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            test.runOnMainSync(()->ctx.startForegroundService(new Intent(ctx,RecorderService.class).setAction("ARM")));
            await(()->RecorderService.carArmed,"Car session did not arm");
            await(()->controller.getPlaybackState().getState()!=PlaybackStateCompat.STATE_ERROR,"Arming did not clear setup error");
            test.runOnMainSync(()->activity.moveTaskToBack(true));
            controller.getTransportControls().sendCustomAction(AutoMediaService.RECORD,null); ownNote=true;
            await(()->"Recording".equals(RecorderService.state),"Custom Record failed in background");
            SystemClock.sleep(1200);
            require(RecorderService.recordedMs>500,"Background record captured no samples");
            await(()->controller.getMetadata()!=null&&controller.getMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE).contains("Recording"),"Recording metadata missing");
            require(controller.getPlaybackState().getCustomActions().stream().noneMatch(a->AutoMediaService.PAUSE.equals(a.getAction())),"Duplicate custom Pause advertised");
            require(controller.getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ARTIST).contains("dBFS"),"Car input-level metadata missing");
            controller.getTransportControls().sendCustomAction(AutoMediaService.PAUSE,null);
            await(()->"Paused".equals(RecorderService.state),"Custom Pause failed");
            long paused=RecorderService.recordedMs;
            controller.getTransportControls().play(); SystemClock.sleep(700);
            require("Paused".equals(RecorderService.state)&&RecorderService.recordedMs==paused,"Automatic Play resumed microphone");
            controller.getTransportControls().sendCustomAction(AutoMediaService.RESUME,null);
            await(()->"Recording".equals(RecorderService.state),"Custom Resume failed"); SystemClock.sleep(700);
            controller.getTransportControls().pause(); await(()->"Paused".equals(RecorderService.state),"System media Pause did not pause recording");
            controller.getTransportControls().stop(); await(()->!RecorderService.hasNote&&!RecorderService.saving,"System Stop did not save");
            require(RecorderService.carArmed,"Saving ended the car session unexpectedly"); ownNote=false;
            String noteId=catalog.get().get(0).getMediaId();
            controller.getTransportControls().playFromMediaId(noteId,null);
            await(()->controller.getPlaybackState().getState()==PlaybackStateCompat.STATE_PLAYING,"Saved-note playback failed");
            controller.getTransportControls().pause(); await(()->controller.getPlaybackState().getState()==PlaybackStateCompat.STATE_PAUSED,"Playback Pause failed");
            controller.getTransportControls().seekTo(1000); SystemClock.sleep(600);
            require(controller.getPlaybackState().getPosition()>=800,"Playback seek failed");
            controller.getTransportControls().stop(); await(()->controller.getPlaybackState().getState()==PlaybackStateCompat.STATE_STOPPED,"Playback Stop failed");
            controller.getTransportControls().playFromMediaId("../not-a-note",null);
            await(()->controller.getPlaybackState().getState()==PlaybackStateCompat.STATE_ERROR,"Invalid media id was not rejected");
            require(!RecorderService.hasNote,"Invalid media id started recording");
            result.putString("stream","PASS: browser startup before activity, real catalog, custom Record/Pause/Resume from background, input-level metadata, automatic Play cannot resume microphone, system Pause/Stop, saved-note playback/seek, persistent armed session and invalid-id rejection.\n");
        } finally {
            if(ownNote&&RecorderService.hasNote) { ctx.startService(new Intent(ctx,RecorderService.class).setAction("STOP")); for(int i=0;i<80&&RecorderService.hasNote;i++) SystemClock.sleep(100); }
            if(RecorderService.carArmed) ctx.startService(new Intent(ctx,RecorderService.class).setAction("DISARM"));
            if(reference.get()!=null) test.runOnMainSync(()->reference.get().disconnect());
        }
    }
}
