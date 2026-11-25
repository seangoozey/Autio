package com.autio.notes;

import android.app.Activity;
import android.content.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class NotesActivity extends Activity {
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private Button playingButton;
    private TextView playingTime;
    private SeekBar playingSeek;
    private LibraryNote playingFile;
    private boolean prepared;
    private boolean firstResume=true;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable() {
        public void run() {
            if(player!=null&&prepared&&playingSeek!=null) {
                playingSeek.setProgress(player.getCurrentPosition());
                playingTime.setText(Ui.time(player.getCurrentPosition())+" / "+Ui.time(player.getDuration()));
            }
            handler.postDelayed(this,250);
        }
    };
    private int d(int n) { return Ui.dp(this,n); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        audio=getSystemService(AudioManager.class);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(change->{if(change<0) releasePlayer();},handler).build();
        LinearLayout root=Ui.column(this); root.setPadding(d(24),d(12),d(24),d(24));
        Button back=Ui.button(this,"Recorder","back",Ui.MINT,Ui.BG,0); back.setTextSize(14); back.setPadding(0,0,d(16),0);
        root.addView(back,new LinearLayout.LayoutParams(-2,d(52))); back.setOnClickListener(v->finish()); Ui.gap(root,24);
        TextView title=Ui.text(this,"Saved notes",32,Ui.TEXT); Ui.medium(title); root.addView(title); Ui.gap(root,10);
        LibraryNote[] notes=NoteStorage.library(this);
        TextView summary=Ui.text(this,notes.length+ (notes.length==1?" note":" notes")+" · in managed folder or awaiting delivery",14,Ui.MUTED); root.addView(summary); Ui.gap(root,16);
        if(!NoteStorage.libraryError.isEmpty()) root.addView(Ui.text(this,NoteStorage.libraryError,14,Ui.AMBER));
        if(notes.length==0) {
            LinearLayout empty=Ui.column(this); empty.setPadding(d(24),d(36),d(24),d(36)); empty.setBackground(Ui.shape(this,Ui.SURFACE,24,0));
            TextView label=Ui.text(this,"A place for your thoughts",22,Ui.TEXT); Ui.medium(label); empty.addView(label); Ui.gap(empty,12);
            empty.addView(Ui.text(this,"Record your first note, then stop to save it here.",16,Ui.MUTED)); root.addView(empty);
        }
        for(LibraryNote file:notes) addNote(root,file);
        Ui.gap(root,8); TextView location=Ui.text(this,"The managed folder is your library. Private notes are kept only while awaiting delivery. Changes made in the managed folder appear when you reopen this list.",12,Ui.MUTED); location.setLineSpacing(d(4),1); root.addView(location);
        ScrollView scroll=new ScrollView(this); scroll.addView(root); Ui.install(this,scroll);
    }
    private void addNote(LinearLayout root,LibraryNote file) {
        LinearLayout card=Ui.column(this); card.setPadding(d(20),d(20),d(20),d(16)); card.setBackground(Ui.shape(this,Ui.SURFACE,24,0));
        Date timestamp=new Date(file.lastModified());
        try { timestamp=new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS",Locale.US).parse(file.getName().substring(5,28)); } catch(Exception ignored) {}
        TextView date=Ui.text(this,new SimpleDateFormat("EEE, MMM d",Locale.getDefault()).format(timestamp),19,Ui.TEXT); Ui.medium(date); card.addView(date); Ui.gap(card,6);
        long duration=file.knownDuration(this);
        String description=new SimpleDateFormat("h:mm a",Locale.getDefault()).format(timestamp)+"  ·  "+(duration>=0?Ui.time(duration)+"  ·  ":"")+android.text.format.Formatter.formatShortFileSize(this,file.length())+(file.getName().contains("recovered")?"  ·  Recovered":"")+(file.pending?"  ·  Delivery pending":"");
        TextView info=Ui.text(this,description,14,Ui.MUTED); card.addView(info); Ui.gap(card,16);
        SeekBar seek=new SeekBar(this); seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.MINT)); seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.MINT));
        seek.setMax((int)Math.max(0,Math.min(Integer.MAX_VALUE,duration))); seek.setContentDescription("Playback position"); seek.setVisibility(View.GONE); card.addView(seek,new LinearLayout.LayoutParams(-1,d(40)));
        TextView time=Ui.text(this,"",12,Ui.MUTED); time.setVisibility(View.GONE); card.addView(time);
        LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        Button play=Ui.button(this,"Play","play",Ui.MINT,Ui.SURFACE,Ui.LINE); actions.addView(play,new LinearLayout.LayoutParams(0,d(52),1));
        View spacer=new View(this); actions.addView(spacer,new LinearLayout.LayoutParams(d(12),1));
        Button share=Ui.button(this,"Share",null,Ui.TEXT,Ui.SURFACE,Ui.LINE); actions.addView(share,new LinearLayout.LayoutParams(0,d(52),1)); card.addView(actions);
        play.setOnClickListener(v->play(file,play,seek,time));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int progress,boolean user) { if(user&&prepared&&player!=null&&playingSeek==s) player.seekTo(progress); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        share.setOnClickListener(v->{
            Ui.enabled(share,false); share.setText("Preparing…");
            new Thread(()->{
                try {
                    android.net.Uri uri=file.pending?androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".files",new File(file.uri.getPath())):file.uri;
                    runOnUiThread(()->{
                        if(isFinishing()||isDestroyed()) return;
                        Ui.enabled(share,true); share.setText("Share");
                        Intent intent=new Intent(Intent.ACTION_SEND).setType(NoteStorage.mime(new File(file.name))).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setClipData(ClipData.newRawUri("Audio note",uri)); startActivity(Intent.createChooser(intent,"Share note"));
                    });
                } catch(Exception e) { runOnUiThread(()->{if(isDestroyed()) return; Ui.enabled(share,true); share.setText("Share"); Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}); }
            },"Autio-share").start();
        });
        root.addView(card); Ui.gap(root,16);
    }
    private void play(LibraryNote file,Button button,SeekBar seek,TextView time) {
        if(file.equals(playingFile)&&player!=null&&prepared) {
            if(player.isPlaying()) { player.pause(); button.setText("Resume"); Ui.icon(button,"play",Ui.MINT); }
            else { if(audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { player.start(); button.setText("Pause"); Ui.icon(button,"pause",Ui.MINT); } }
            return;
        }
        releasePlayer();
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { Toast.makeText(this,"Audio is busy. Try again shortly.",Toast.LENGTH_SHORT).show(); return; }
        try {
            player=new MediaPlayer(); MediaPlayer source=player;
            playingFile=file; playingButton=button; playingSeek=seek; playingTime=time; prepared=false;
            button.setText("Loading…"); Ui.enabled(button,false);
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setDataSource(this,file.uri);
            player.setOnPreparedListener(p->{
                if(player!=source) return;
                prepared=true; Ui.enabled(button,true); button.setText("Pause"); Ui.icon(button,"pause",Ui.MINT);
                seek.setMax(p.getDuration()); seek.setVisibility(View.VISIBLE); time.setVisibility(View.VISIBLE); p.start();
            });
            player.setOnCompletionListener(p->releasePlayer());
            player.setOnErrorListener((p,what,extra)->{releasePlayer(); Toast.makeText(this,"Could not play this note",Toast.LENGTH_LONG).show(); return true;});
            player.prepareAsync();
        } catch(IOException e) { releasePlayer(); Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    private void releasePlayer() {
        if(player!=null) { player.release(); player=null; }
        prepared=false; playingFile=null;
        if(playingButton!=null) { playingButton.setText("Play"); Ui.icon(playingButton,"play",Ui.MINT); Ui.enabled(playingButton,true); playingButton=null; }
        if(playingSeek!=null) { playingSeek.setVisibility(View.GONE); playingSeek=null; }
        if(playingTime!=null) { playingTime.setVisibility(View.GONE); playingTime=null; }
        if(audio!=null&&focus!=null) audio.abandonAudioFocusRequest(focus);
    }
    @Override protected void onResume() { super.onResume(); if(!firstResume) { recreate(); return; } firstResume=false; handler.post(tick); }
    @Override protected void onStop() { handler.removeCallbacks(tick); releasePlayer(); super.onStop(); }
}
