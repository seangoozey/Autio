package com.autio.notes;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.Map;

/** Real capture checks for source changes, session effect overrides and pause/resume cleanup. */
final class ProcessingSmokeTest {
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void await(String state) {
        for(int i=0;i<80&&!RecorderService.state.equals(state);i++) SystemClock.sleep(100);
        require(RecorderService.state.equals(state),"Expected "+state+" but got "+RecorderService.state+" / "+RecorderService.detail);
    }
    private static void command(Context context,String action) {
        context.startService(new Intent(context,RecorderService.class).setAction(action));
        SystemClock.sleep(300);
    }
    static void run(Instrumentation test,Bundle result) throws Exception {
        Context context=test.getTargetContext();
        require(!RecorderService.hasNote&&!RecorderService.saving,"A note is already open");
        SharedPreferences preferences=CaptureProcessing.prefs(context);
        Map<String,?> previous=preferences.getAll();
        try {
            test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            preferences.edit().putInt("source",MediaRecorder.AudioSource.MIC).putInt("agc",1).putInt("ns",1).putInt("aec",1).commit();
            command(context,"START"); await("Recording"); SystemClock.sleep(1200);
            require(RecorderService.recordedMs>500,"Processing overrides prevented capture");
            require(RecorderService.processingReport.startsWith("Microphone (standard)"),"Requested source not used");
            require(RecorderService.processingReport.contains("Off"),"Requested Off states not reported");
            require(!RecorderService.processingReport.contains("Off requested, Android reports on")||RecorderService.processingReport.contains("rejected"),"An unfulfilled Off request was reported as successful");
            preferences.edit().putInt("source",MediaRecorder.AudioSource.VOICE_RECOGNITION).putInt("agc",2).putInt("ns",2).putInt("aec",2).commit();
            require(RecorderService.processingReport.startsWith("Microphone (standard)"),"Live session changed before resume");
            command(context,"PAUSE"); await("Paused");
            long paused=RecorderService.recordedMs;
            command(context,"RESUME"); await("Recording"); SystemClock.sleep(1000);
            require(RecorderService.recordedMs>paused+500,"Resume with new settings captured no audio");
            require(RecorderService.processingReport.startsWith("Voice recognition"),"Resume did not apply selected source");
            require(RecorderService.processingReport.contains("On"),"On requests missing from report");
            command(context,"PAUSE"); await("Paused");
            preferences.edit().putInt("source",MediaRecorder.AudioSource.MIC).putInt("agc",0).putInt("ns",0).putInt("aec",0).commit();
            command(context,"RESUME"); await("Recording"); SystemClock.sleep(700);
            require(RecorderService.processingReport.contains("Android default"),"Defaults were not restored");
            for(int source:new int[]{MediaRecorder.AudioSource.VOICE_COMMUNICATION,MediaRecorder.AudioSource.UNPROCESSED}) {
                if(source==MediaRecorder.AudioSource.UNPROCESSED&&!CaptureProcessing.supportsUnprocessed(context)) continue;
                command(context,"PAUSE"); await("Paused");
                preferences.edit().putInt("source",source).commit();
                command(context,"RESUME"); await("Recording"); SystemClock.sleep(700);
                require(RecorderService.processingReport.startsWith(CaptureProcessing.sourceName(source)),"Alternative source was not applied");
            }
            command(context,"CANCEL"); await("Ready");
            require(preferences.getString("last_report","").contains("PCM samples near full scale"),"Last-session report not retained");
            result.putString("stream","PASS: real capture with Off/On requests, source changes applied on resume, pause/resume sample continuity, restoring defaults, diagnostic report persistence, and cancel.\n");
        } finally {
            if(RecorderService.hasNote&&!RecorderService.saving) { command(context,"CANCEL"); await("Ready"); }
            SharedPreferences.Editor restore=preferences.edit();
            for(String key:new String[]{"source","agc","ns","aec"}) {
                restore.remove(key);
                if(previous.get(key) instanceof Integer) restore.putInt(key,(Integer)previous.get(key));
            }
            restore.commit();
        }
    }
}
