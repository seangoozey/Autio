package com.autio.notes;

import androidx.car.app.CarContext;
import androidx.car.app.media.CarAudioRecord;
import java.io.IOException;

/** Live host audio, with continuous linear interpolation into the note encoder's rate. */
final class HostMicrophone {
    static volatile CarContext context;
    private static Object owner;
    static synchronized void attach(Object session,CarContext car) {
        owner=session; context=car;
        android.util.Log.i("AutioHost","Attached car session "+System.identityHashCode(session));
    }
    static synchronized boolean detach(Object session) {
        if(owner!=session) return false;
        owner=null; context=null;
        android.util.Log.i("AutioHost","Detached car session "+System.identityHashCode(session));
        return true;
    }
    private final CarAudioRecord record;
    private final byte[] bytes=new byte[CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE];
    private final PcmResampler resampler=new PcmResampler();
    HostMicrophone() throws IOException {
        CarContext car=context;
        if(car==null) throw new IOException("Open Autio on Android Auto before using the host microphone");
        if(car.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED) throw new IOException("Microphone permission is required");
        record=CarAudioRecord.create(car);
        try { record.startRecording(); }
        catch(RuntimeException e) { try { record.stopRecording(); } catch(RuntimeException ignored) {} throw e; }
    }
    int read(short[] output) throws IOException {
        int count=record.read(bytes,0,bytes.length);
        if(count<0) throw new IOException("Android Auto microphone ended — reconnect and tap Resume");
        if((count&1)!=0) throw new IOException("Invalid host microphone frame");
        return resampler.convert(bytes,count,output);
    }
    void stop() { record.stopRecording(); }
}
