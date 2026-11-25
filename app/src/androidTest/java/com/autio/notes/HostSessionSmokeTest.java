package com.autio.notes;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;

/** Interactive DHU regression: a car Record tap must recover a lost context registration. */
final class HostSessionSmokeTest {
    static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static void run(Instrumentation test,Bundle result) throws Exception {
        Context ctx=test.getTargetContext();
        android.content.SharedPreferences prefs=CaptureProcessing.prefs(ctx);
        boolean previous=prefs.getBoolean("host_capture",false);
        try {
            prefs.edit().putBoolean("host_capture",true).commit();
            test.startActivitySync(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            android.util.Log.i("AutioHostTest","Waiting for dedicated car screen");
            for(int i=0;i<1200&&HostMicrophone.context==null;i++) SystemClock.sleep(100);
            require(HostMicrophone.context!=null,"Dedicated car screen did not attach");
            require(!RecorderService.hasNote,"An existing recording must not be replaced");
            // Reproduce the reported precondition without destroying the screen handling Record.
            SystemClock.sleep(1000); // Allow the screen onStart registration to finish first.
            test.runOnMainSync(()->HostMicrophone.context=null);
            android.util.Log.i("AutioHostTest","Registration cleared; tap Record on car screen now");
            for(int i=0;i<1200&&!RecorderService.hasNote;i++) SystemClock.sleep(100);
            require(RecorderService.hasNote,"No car Record command received");
            SystemClock.sleep(1000);
            require(HostMicrophone.context!=null,"Record did not restore car session registration");
            require(RecorderService.inputSource.equals("Android Auto host microphone"),"Host did not open: "+RecorderService.detail);
            require(!RecorderService.detail.contains("Open Autio on Android Auto"),"Original missing-session error recurred");
            result.putString("result","PASS: dedicated car Record tap restores lost host registration and opens host microphone; no phone fallback.");
        } finally {
            if(RecorderService.hasNote) {
                ctx.startService(new Intent(ctx,RecorderService.class).setAction("CANCEL"));
                for(int i=0;i<60&&RecorderService.hasNote;i++) SystemClock.sleep(100);
            }
            prefs.edit().putBoolean("host_capture",previous).commit();
        }
    }
}
