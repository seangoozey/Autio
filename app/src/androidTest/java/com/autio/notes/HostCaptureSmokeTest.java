package com.autio.notes;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;

final class HostCaptureSmokeTest {
    static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static void run(Instrumentation test,Bundle result) throws Exception {
        // Chunk boundaries must not change sample count, pitch, polarity, or waveform.
        byte[] input=new byte[32000];
        for(int i=0;i<16000;i++) {
            short value=(short)Math.round(12000*Math.sin(2*Math.PI*1000*i/16000));
            input[2*i]=(byte)value; input[2*i+1]=(byte)(value>>8);
        }
        short[] whole=new short[44100];
        int expected=new PcmResampler().convert(input,input.length,whole);
        require(Math.abs(expected-44100)<=3,"Resampler changed duration");
        PcmResampler chunks=new PcmResampler(); int position=0;
        for(int offset=0;offset<input.length;) {
            int size=Math.min(2*(1+(offset/2)%256),input.length-offset);
            byte[] part=java.util.Arrays.copyOfRange(input,offset,offset+size);
            short[] output=new short[2048]; int n=chunks.convert(part,size,output);
            for(int j=0;j<n;j++) require(output[j]==whole[position++],"Resampler boundary discontinuity");
            offset+=size;
        }
        require(position==expected,"Resampler chunk duration mismatch");
        for(int i=0;i<expected;i++) require(Math.abs(whole[i]-12000*Math.sin(2*Math.PI*1000*i/44100))<250,"Resampler pitch or endian mismatch");
        Context ctx=test.getTargetContext();
        android.content.SharedPreferences prefs=CaptureProcessing.prefs(ctx);
        boolean old=prefs.getBoolean("host_capture",false);
        require(HostMicrophone.context==null,"Run disconnected from Android Auto");
        try {
            test.startActivitySync(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            prefs.edit().putBoolean("host_capture",true).commit();
            ctx.startService(new Intent(ctx,RecorderService.class).setAction("START"));
            for(int i=0;i<60&&!RecorderService.hasNote;i++) SystemClock.sleep(100);
            SystemClock.sleep(500);
            require(RecorderService.state.equals("Paused"),"Missing host did not pause");
            require(RecorderService.recordedMs==0,"Silently fell back to phone microphone");
            require(RecorderService.detail.contains("Open Autio on Android Auto"),"No actionable missing-host message");
        } finally {
            ctx.startService(new Intent(ctx,RecorderService.class).setAction("CANCEL"));
            for(int i=0;i<60&&RecorderService.hasNote;i++) SystemClock.sleep(100);
            prefs.edit().putBoolean("host_capture",old).commit();
        }
        require(!RecorderService.hasNote,"Could not discard unavailable-host test note");
        result.putString("result","PASS: host PCM resampling duration, pitch, endianness, variable chunk continuity; unavailable host pauses without fallback; cancellation; preference restored.");
    }
}
