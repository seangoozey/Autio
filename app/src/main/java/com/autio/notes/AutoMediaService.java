package com.autio.notes;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import android.support.v4.media.*;
import android.support.v4.media.session.*;
import androidx.media.MediaBrowserServiceCompat;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/** Real note playback plus explicitly named recorder actions for the Android Auto media host. */
public class AutoMediaService extends MediaBrowserServiceCompat {
    static final String ROOT="autio", NOTES="notes";
    static final String RECORD="com.autio.notes.RECORD", PAUSE="com.autio.notes.PAUSE_NOTE", RESUME="com.autio.notes.RESUME_NOTE", CANCEL="com.autio.notes.CANCEL_NOTE", SAVE="com.autio.notes.SAVE_NOTE";
    private final Handler main=new Handler(Looper.getMainLooper());
    private MediaSessionCompat session;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest playbackFocus;
    private LibraryNote selected;
    private volatile LibraryNote[] library=new LibraryNote[0];
    private boolean refreshingLibrary;
    private long lastLibraryRefresh;
    private boolean prepared,loading,foreground;
    private boolean needsArming;
    private boolean startingRecord;
    private long startRequestedAt;
    private android.graphics.Bitmap artwork;
    private String error,rendered="",noteList="";
    private final Runnable refresh=new Runnable() {
        @Override public void run() { update(); main.postDelayed(this,500); }
    };
    @Override public void onCreate() {
        super.onCreate();
        audio=getSystemService(AudioManager.class);
        artwork=android.graphics.Bitmap.createBitmap(512,512,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cover=new android.graphics.Canvas(artwork);
        android.graphics.drawable.Drawable logo=getDrawable(R.drawable.ic_autio);
        logo.setBounds(0,0,512,512); logo.draw(cover);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("playback","Note playback",NotificationManager.IMPORTANCE_LOW));
        playbackFocus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(change->{if(change<0) pausePlayback();},main).build();
        session=new MediaSessionCompat(this,"Autio notes");
        session.setSessionActivity(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE));
        session.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlayFromMediaId(String id,Bundle extras) { play(id); }
            @Override public void onPlayFromSearch(String query,Bundle extras) { playSearch(query); }
            @Override public void onPlay() {
                // Automatic media resumption never starts or resumes a microphone recording.
                if(RecorderService.hasNote||RecorderService.saving) return;
                if(prepared&&player!=null) resumePlayback();
                else { LibraryNote[] notes=NoteStorage.library(AutoMediaService.this); if(notes.length>0) play(selected==null?notes[0].getName():selected.getName()); }
            }
            @Override public void onPause() { if(RecorderService.hasNote&&!RecorderService.saving) recordCommand("PAUSE"); else pausePlayback(); }
            @Override public void onStop() { if(RecorderService.hasNote&&!RecorderService.saving) recordCommand("STOP"); else stopPlayback(); }
            @Override public void onSeekTo(long position) { if(prepared&&player!=null&&!RecorderService.hasNote) { player.seekTo((int)Math.max(0,Math.min(position,player.getDuration()))); update(); } }
            @Override public void onSkipToNext() { skip(1); }
            @Override public void onSkipToPrevious() { skip(-1); }
            @Override public void onCustomAction(String action,Bundle extras) {
                if(RECORD.equals(action)&&!RecorderService.hasNote&&!RecorderService.saving) { stopPlayback(); recordCommand("START"); }
                else if(PAUSE.equals(action)&&RecorderService.hasNote&&!RecorderService.saving) recordCommand("PAUSE");
                else if(RESUME.equals(action)&&RecorderService.hasNote&&!RecorderService.saving) { stopPlayback(); recordCommand("RESUME"); }
                else if(CANCEL.equals(action)&&RecorderService.hasNote&&!RecorderService.saving) recordCommand("CANCEL");
                else if(SAVE.equals(action)&&RecorderService.hasNote&&!RecorderService.saving) recordCommand("STOP");
            }
        },main);
        setSessionToken(session.getSessionToken()); session.setActive(true);
        LibraryNote[] notes=NoteStorage.library(this); if(notes.length>0) selected=notes[0];
        DeliveryJobs.kick(this); main.post(refresh);
    }
    @Override public BrowserRoot onGetRoot(String clientPackage,int clientUid,Bundle hints) {
        boolean trusted=clientUid==getApplicationInfo().uid||clientUid==android.os.Process.SYSTEM_UID
            ||"com.google.android.projection.gearhead".equals(clientPackage)||"com.google.android.gms".equals(clientPackage)
            ||"com.android.bluetooth".equals(clientPackage);
        if(!trusted) return null;
        Bundle extras=new Bundle(); extras.putBoolean("android.media.browse.SEARCH_SUPPORTED",false);
        return new BrowserRoot(ROOT,extras);
    }
    @Override public void onLoadChildren(String parent,Result<List<MediaBrowserCompat.MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> items=new ArrayList<>();
        if(ROOT.equals(parent)) {
            items.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(NOTES).setTitle("Saved notes").setSubtitle("Your audio notes, newest first").build(),MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        } else if(NOTES.equals(parent)) {
            for(LibraryNote note:NoteStorage.library(this)) {
                MediaDescriptionCompat description=new MediaDescriptionCompat.Builder().setMediaId(note.getName()).setTitle(title(note))
                    .setSubtitle((note.knownDuration(this)>=0?Ui.time(note.knownDuration(this))+" · ":"")+android.text.format.Formatter.formatShortFileSize(this,note.length())).build();
                items.add(new MediaBrowserCompat.MediaItem(description,MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
            }
        }
        result.sendResult(items);
    }
    private String title(LibraryNote file) {
        Date time=new Date(file.lastModified());
        try { time=new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS",Locale.US).parse(file.getName().substring(5,28)); } catch(Exception ignored) {}
        return new SimpleDateFormat("MMM d, h:mm a",Locale.getDefault()).format(time)+(file.getName().contains("recovered")?" · Recovered":"");
    }
    private void recordCommand(String command) {
        error=null;
        if(("START".equals(command)||"RESUME".equals(command))&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            error="Open Autio on your phone and allow microphone access first."; update(); return;
        }
        try {
            Intent intent=new Intent(this,RecorderService.class).setAction(command);
            if("START".equals(command)) {
                startingRecord=true; startRequestedAt=SystemClock.elapsedRealtime();
                intent.setAction("START_CAR");
                if(RecorderService.carArmed) startService(intent); else startForegroundService(intent);
            } else startService(intent);
        } catch(RuntimeException e) {
            startingRecord=false; needsArming=true;
            error="Android requires phone setup. Enable recording under Autio → Settings → Android Auto controls, then tap Record again.";
            android.util.Log.w("AutioAuto","Recording command denied",e);
        }
        rendered=""; update();
    }
    private LibraryNote noteForId(String id) {
        // Accept only entries in our own catalog, never paths supplied by external controllers.
        for(LibraryNote file:NoteStorage.library(this)) if(file.getName().equals(id)) return file;
        return null;
    }
    private void play(String id) {
        if(RecorderService.hasNote||RecorderService.saving) { error="Stop and save your open note before playing a saved note."; update(); return; }
        LibraryNote file=noteForId(id); if(file==null) { error="This note is no longer available."; update(); return; }
        stopPlayback(); error=null; selected=file;
        try {
            // Playback is user-initiated by the media controller, and remains audible in the background.
            startForegroundService(new Intent(this,AutoMediaService.class).setAction("PLAYBACK"));
            showPlaybackNotification();
            player=new MediaPlayer(); MediaPlayer source=player; loading=true;
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setDataSource(this,file.uri);
            player.setOnPreparedListener(p->{if(player!=source) return; prepared=true; loading=false; resumePlayback();});
            player.setOnCompletionListener(p->stopPlayback());
            player.setOnErrorListener((p,what,extra)->{stopPlayback(); error="Could not play this note."; update(); return true;});
            player.prepareAsync(); update();
        } catch(Exception e) { stopPlayback(); error="Playback could not start: "+e.getMessage(); update(); }
    }
    private void resumePlayback() {
        if(!prepared||player==null) return;
        try {
            showPlaybackNotification();
            if(audio.requestAudioFocus(playbackFocus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { error="Audio is busy. Try Play again."; update(); return; }
            player.start(); error=null; update();
        } catch(RuntimeException e) { error="Playback could not resume."; update(); }
    }
    private void pausePlayback() {
        if(prepared&&player!=null&&player.isPlaying()) player.pause();
        audio.abandonAudioFocusRequest(playbackFocus);
        if(foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground=false; }
        update();
    }
    private void stopPlayback() {
        if(player!=null) { player.release(); player=null; }
        prepared=false; loading=false;
        audio.abandonAudioFocusRequest(playbackFocus);
        if(foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground=false; }
        stopSelf(); rendered=""; update();
    }
    private void skip(int direction) {
        if(RecorderService.hasNote||RecorderService.saving) return;
        LibraryNote[] notes=NoteStorage.library(this);
        for(int i=0;i<notes.length;i++) if(selected!=null&&selected.equals(notes[i])) { int next=i+direction; if(next>=0&&next<notes.length) play(notes[next].getName()); return; }
    }
    private void showPlaybackNotification() {
        Notification notification=new Notification.Builder(this,"playback").setSmallIcon(R.drawable.ic_resume).setContentTitle("Autio Notes")
            .setContentText(selected==null?"Saved note playback":title(selected))
            .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE))
            .setStyle(new Notification.MediaStyle().setMediaSession((android.media.session.MediaSession.Token)session.getSessionToken().getToken()))
            .setOnlyAlertOnce(true).setOngoing(true).build();
        startForeground(2,notification); foreground=true;
    }
    private void update() {
        if(session==null) return;
        if(needsArming&&RecorderService.carArmed) { needsArming=false; error=null; }
        if(startingRecord&&RecorderService.hasNote) { startingRecord=false; needsArming=false; error=null; }
        if(startingRecord&&SystemClock.elapsedRealtime()-startRequestedAt>300&&"Error".equals(RecorderService.state)) {
            startingRecord=false; needsArming=true;
            error="Android requires phone setup. Enable recording under Autio → Settings → Android Auto controls, then tap Record again.";
        }
        boolean note=RecorderService.hasNote,saving=RecorderService.saving,recording=note&&"Recording".equals(RecorderService.state);
        long position=note||saving?RecorderService.recordedMs:prepared&&player!=null?player.getCurrentPosition():0;
        boolean playing=prepared&&player!=null&&player.isPlaying();
        String key=note+"/"+saving+"/"+recording+"/"+RecorderService.state+"/"+(position/1000)+"/"+(int)(RecorderService.db/6)+"/"+playing+"/"+loading+"/"+selected+"/"+error;
        if(!key.equals(rendered)) {
            rendered=key;
            PlaybackStateCompat.Builder state=new PlaybackStateCompat.Builder();
            long actions=PlaybackStateCompat.ACTION_STOP;
            if(note||saving) {
                if(recording) actions|=PlaybackStateCompat.ACTION_PAUSE;
                if(!saving) {
                    if(!recording) state.addCustomAction(RESUME,"Resume note",R.drawable.ic_resume);
                    state.addCustomAction(SAVE,"Stop and save note",R.drawable.ic_stop);
                }
            } else {
                actions|=PlaybackStateCompat.ACTION_PLAY|PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID|PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH|PlaybackStateCompat.ACTION_PAUSE|PlaybackStateCompat.ACTION_SEEK_TO;
                if(prepared) actions|=PlaybackStateCompat.ACTION_SKIP_TO_NEXT|PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
                state.addCustomAction(RECORD,"Record",R.drawable.ic_record);
            }
            int status=error!=null?PlaybackStateCompat.STATE_ERROR:loading||saving?PlaybackStateCompat.STATE_BUFFERING:recording||playing?PlaybackStateCompat.STATE_PLAYING:note||prepared?PlaybackStateCompat.STATE_PAUSED:PlaybackStateCompat.STATE_STOPPED;
            state.setActions(actions).setState(status,position,recording||playing?1f:0f,SystemClock.elapsedRealtime());
            if(error!=null) state.setErrorMessage(error);
            session.setPlaybackState(state.build());
            MediaMetadataCompat.Builder metadata=new MediaMetadataCompat.Builder().putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,artwork);
            if(note||saving) {
                String description=saving?"Saving note":recording?meterText():RecorderService.detail;
                metadata.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID,"active-note")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,-1)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE,saving?"Saving audio note":recording?"Recording audio note":"Audio note paused")
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,saving?"Saving audio note":recording?"Recording audio note":"Audio note paused")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,description).putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,description)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,RecorderService.inputSource);
            } else {
                metadata.putString(MediaMetadataCompat.METADATA_KEY_TITLE,selected==null?"Autio Notes":title(selected))
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,prepared?title(selected):"Ready to record")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,"Autio Notes")
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,error==null?(prepared?"Autio Notes":"Tap the ring-and-dot Record button beside Play"):error);
                if(selected!=null) metadata.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID,selected.getName()).putLong(MediaMetadataCompat.METADATA_KEY_DURATION,selected.duration(this));
            }
            session.setMetadata(metadata.build());
        }
        if(!refreshingLibrary&&SystemClock.elapsedRealtime()-lastLibraryRefresh>5000) {
            refreshingLibrary=true; lastLibraryRefresh=SystemClock.elapsedRealtime();
            new Thread(()->{ library=NoteStorage.library(this); main.post(()->{refreshingLibrary=false; update();}); },"Autio-library-refresh").start();
        }
        String signature=Arrays.toString(library);
        if(!signature.equals(noteList)) { noteList=signature; notifyChildrenChanged(NOTES); notifyChildrenChanged(ROOT); }
    }
    private String meterText() {
        int bars=(int)Math.max(0,Math.min(8,(RecorderService.db+60)*8/60));
        StringBuilder meter=new StringBuilder(); for(int i=0;i<8;i++) meter.append(i<bars?'▰':'▱');
        return Ui.time(RecorderService.recordedMs)+" · "+meter+String.format(Locale.US," %.0f dBFS",RecorderService.db);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null&&"PLAYBACK".equals(intent.getAction())) showPlaybackNotification();
        else if(intent!=null&&"android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(intent.getAction())) playSearch(intent.getStringExtra(android.app.SearchManager.QUERY));
        return START_NOT_STICKY;
    }
    private void playSearch(String query) {
        LibraryNote[] notes=NoteStorage.library(this);
        String text=query==null?"":query.trim().toLowerCase(Locale.getDefault());
        for(LibraryNote note:notes) {
            if(text.isEmpty()||text.equals("latest")||text.equals("latest note")||text.equals("autio notes")||title(note).toLowerCase(Locale.getDefault()).contains(text)) { play(note.getName()); return; }
        }
        error="No saved note matches that search."; update();
    }
    @Override public void onDestroy() {
        main.removeCallbacks(refresh);
        if(player!=null) { player.release(); player=null; }
        audio.abandonAudioFocusRequest(playbackFocus);
        session.release(); session=null; super.onDestroy();
    }
}
