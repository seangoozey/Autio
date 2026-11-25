package com.autio.notes;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import java.io.*;

/** Device integration check: real capture, pause gap, focus loss, resume and WAV commit. */
public class RecorderSmokeTest extends Instrumentation {
    private boolean longAudio;
    private boolean media;
    private boolean reliability;
    private boolean processing, host, hostSession;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); hostSession=arguments!=null&&"true".equals(arguments.getString("hostSession")); host=arguments!=null&&"true".equals(arguments.getString("host")); longAudio = arguments != null && "true".equals(arguments.getString("longAudio")); reliability=arguments!=null&&"true".equals(arguments.getString("reliability")); media=arguments!=null&&"true".equals(arguments.getString("media")); processing=arguments!=null&&"true".equals(arguments.getString("processing")); start(); }
    private void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private void command(String action) {
        Context ctx = getTargetContext();
        ctx.startService(new Intent(ctx, RecorderService.class).setAction(action));
        SystemClock.sleep(350);
    }
    private void await(String state) {
        for (int i=0; i<60 && !RecorderService.state.equals(state); i++) SystemClock.sleep(100);
        require(RecorderService.state.equals(state), "Expected " + state + "; got " + RecorderService.state + ": " + RecorderService.detail);
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if(hostSession) { HostSessionSmokeTest.run(this,result); finish(Activity.RESULT_OK,result); return; }
            if(host) { HostCaptureSmokeTest.run(this,result); finish(Activity.RESULT_OK,result); return; }
            if(processing) { ProcessingSmokeTest.run(this,result); finish(Activity.RESULT_OK,result); return; }
            if(longAudio) { verifyHour(result); finish(Activity.RESULT_OK,result); return; }
            if(reliability) { ReliabilitySmokeTest.run(this,result); finish(Activity.RESULT_OK,result); return; }
            if(media) { AutoMediaSmokeTest.run(this,result); finish(Activity.RESULT_OK,result); return; }
            Context ctx = getTargetContext();
            startActivitySync(new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SystemClock.sleep(500);
            command("START"); await("Recording"); SystemClock.sleep(1200);
            require(RecorderService.recordedMs > 500, "No audio samples captured");
            command("PAUSE"); await("Paused");
            long first = RecorderService.recordedMs;
            SystemClock.sleep(1200);
            require(RecorderService.recordedMs == first, "Pause appended audio");
            command("RESUME"); await("Recording"); SystemClock.sleep(700);
            AudioManager manager = ctx.getSystemService(AudioManager.class);
            AudioFocusRequest interrupt = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setOnAudioFocusChangeListener(change -> {}, new Handler(Looper.getMainLooper())).build();
            require(manager.requestAudioFocus(interrupt) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED, "Could not simulate focus interruption");
            await("Paused");
            long interrupted = RecorderService.recordedMs;
            manager.abandonAudioFocusRequest(interrupt); SystemClock.sleep(600);
            require(RecorderService.state.equals("Paused"), "Unexpected automatic resume");
            require(RecorderService.recordedMs == interrupted, "Interruption appended audio");
            command("RESUME"); await("Recording"); SystemClock.sleep(700);
            command("PAUSE"); await("Paused");
            long expected = RecorderService.recordedMs;
            File[] active=ctx.getFilesDir().listFiles((dir,name)->name.endsWith(".aac.pending"));
            require(active!=null&&active.length==1,"Cannot identify this test note");
            String testName=active[0].getName().replace(".aac.pending",".m4a");
            command("STOP"); await("Saved");
            LibraryNote[] files=NoteStorage.library(ctx);
            require(files.length>0,"No committed note in library");
            LibraryNote latest=java.util.Arrays.stream(files).filter(n->n.name.equals(testName)).findFirst().orElseThrow();
            MediaMetadataRetriever metadata=new MediaMetadataRetriever(); metadata.setDataSource(ctx,latest.uri);
            long actual=Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); metadata.release();
            require(Math.abs(actual-expected)<150,"AAC duration mismatch: "+actual+" vs "+expected);
            require(latest.length()<expected*88200/1000/3,"Compression did not reduce file size");
            MediaExtractor extractor=new MediaExtractor(); extractor.setDataSource(ctx,latest.uri,null);
            require(extractor.getTrackCount()==1&&"audio/mp4a-latm".equals(extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME)),"Not a single AAC track"); extractor.release();
            if(NoteDelivery.folder(ctx)!=null) {
                require(!latest.pending,"Delivery did not reach managed library");
                require(!new File(ctx.getFilesDir(),latest.name).exists(),"Private duplicate retained after delivery");
                require(RecorderService.detail.startsWith("Saved in managed folder"),"Incorrect save status: "+RecorderService.detail);
            }
            int before=NoteStorage.notes(ctx).length;
            for(boolean paused:new boolean[]{false,true}) {
                command("START"); await("Recording"); SystemClock.sleep(600);
                if(paused) { command("PAUSE"); await("Paused"); }
                command("CANCEL"); await("Ready");
                require(!RecorderService.hasNote&&!RecorderService.saving&&RecorderService.recordedMs==0,"Cancel did not reset note");
                require(NoteStorage.notes(ctx).length==before,"Cancel committed a note");
                File[] pending=ctx.getFilesDir().listFiles((dir,name)->name.endsWith(".pending"));
                require(pending!=null&&pending.length==0,"Cancel retained recoverable staging");
            }
            result.putString("stream", "PASS: capture, paused gap, focus interruption, manual resume, stop while paused, AAC format/duration/size, compressed staging cleanup, delivery copy, cancel during recording and while paused.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("stream", "FAIL: " + e + "\n"); finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void verifyHour(Bundle result) throws Exception {
        File directory=getTargetContext().getCacheDir();
        File seed=new File(directory,"AacSeed.aac");
        File source=new File(directory,"LongAudioVerification.aac");
        File compressed=null;
        try {
            short[] tone=new short[4410];
            for(int i=0;i<tone.length;i++) tone[i]=(short)(6000*Math.sin(i*2*Math.PI*440/44100));
            try(AacRecording encoder=new AacRecording(seed)) { for(int i=0;i<10;i++) encoder.write(tone,tone.length); }
            byte[] bytes=java.nio.file.Files.readAllBytes(seed.toPath());
            java.util.List<byte[]> packets=new java.util.ArrayList<>();
            for(int offset=0;offset+7<=bytes.length;) {
                int length=((bytes[offset+3]&3)<<11)|((bytes[offset+4]&255)<<3)|((bytes[offset+5]&255)>>5);
                require(length>=7&&offset+length<=bytes.length,"Invalid seed ADTS frame");
                packets.add(java.util.Arrays.copyOfRange(bytes,offset,offset+length)); offset+=length;
            }
            require(!packets.isEmpty(),"No encoded AAC packets");
            int frames=(int)Math.ceil(3600*44100.0/1024);
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(source))) {
                for(int i=0;i<frames;i++) out.write(packets.get(i%packets.size()));
            }
            long began=SystemClock.elapsedRealtime();
            compressed=AacRecording.finalizeNote(source);
            long elapsed=SystemClock.elapsedRealtime()-began;
            MediaMetadataRetriever reader=new MediaMetadataRetriever(); reader.setDataSource(compressed.getAbsolutePath());
            long duration=Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); reader.release();
            require(Math.abs(duration-3600000)<150,"One-hour duration mismatch: "+duration);
            require(compressed.length()<35000000,"One-hour note exceeds expected size: "+compressed.length());
            MediaExtractor extractor=new MediaExtractor(); extractor.setDataSource(compressed.getAbsolutePath()); extractor.selectTrack(0);
            extractor.seekTo(3599000000L,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            require(extractor.readSampleData(java.nio.ByteBuffer.allocate(65536),0)>0,"Cannot seek to end of hour-long note"); extractor.release();
            result.putString("stream","PASS: synthetic one-hour AAC stream (repeated encoded frames); duration="+duration+" ms; M4A="+compressed.length()+" bytes; staging="+source.length()+" bytes; finalization="+elapsed+" ms; seek near end works. Test files removed.\n");
        } finally { seed.delete(); new File(directory,"AacSeed.m4a.pending").delete(); source.delete(); if(compressed!=null) compressed.delete(); }
    }
}
