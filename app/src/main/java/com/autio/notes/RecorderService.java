package com.autio.notes;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecorderService extends Service {
    public static volatile String state = "Ready", detail = "Record a new audio note";
    public static volatile double db = -90, peak = -90;
    public static volatile long recordedMs;
    public static volatile long noteGeneration;
    public static volatile String inputSource = "Microphone not active";
    public static volatile String processingReport = "";
    public static volatile boolean hasNote;
    public static volatile boolean saving;
    public static volatile boolean carArmed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private AudioManager audio;
    private AudioFocusRequest focus;
    private AudioRecord recorder;
    private CaptureProcessing processing;
    private HostMicrophone host;
    private long sessionSamples, nearFullScaleSamples;
    private Thread worker;
    private AacRecording wave;
    private File pending;
    private volatile boolean running;
    private long silenceSince;
    private AudioManager.AudioRecordingCallback captureCallback;

    @Override public void onCreate() {
        super.onCreate();
        audio = getSystemService(AudioManager.class);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("recording", "Audio notes", NotificationManager.IMPORTANCE_LOW));
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(change -> {
                if (change < 0 && running) pause("Paused by Android audio interruption — tap Resume when ready");
            }, main).build();
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, "recording")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Autio • " + state)
            .setContentText(detail).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true);
        if(!saving) {
            String action = state.equals("Recording") ? "PAUSE" : hasNote ? "RESUME" : "START";
            b.addAction(new Notification.Action.Builder(null, action.equals("PAUSE") ? "Pause" : action.equals("START") ? "Record note" : "Resume", command(action, 1)).build());
            b.addAction(new Notification.Action.Builder(null, hasNote ? "Stop & save" : "End car session", command(hasNote ? "STOP" : "DISARM", 2)).build());
        }
        return b.build();
    }
    private PendingIntent command(String action, int id) {
        return PendingIntent.getService(this, id, new Intent(this, RecorderService.class).setAction(action), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private void notifyState() { getSystemService(NotificationManager.class).notify(1, notification()); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        try {
            if (saving) return START_NOT_STICKY;
            if("HOST_DISCONNECTED".equals(action)) {
                if(host!=null) pause("Android Auto disconnected — reconnect and tap Resume");
            } else if("ARM".equals(action)) {
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) throw new IOException("Allow microphone access on the phone first");
                carArmed=true;
                if(!hasNote) { state="Ready"; detail="Car recording enabled · microphone starts only when you choose Record"; }
                startForeground(1,notification());
            } else if("DISARM".equals(action)) {
                carArmed=false;
                if(hasNote) pause("Car session ended · your open note is paused");
                else { state="Ready"; detail="Car session ended"; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
            } else if (("START".equals(action)||"START_CAR".equals(action)) && !hasNote) {
                if(!carArmed) startForeground(1, notification());
                if("START_CAR".equals(action)) carArmed=true;
                if(getFilesDir().getUsableSpace()<32L*1024*1024) throw new IOException("Storage is nearly full. Free space before recording.");
                synchronized(NoteStorage.class) {
                pending = new File(getFilesDir(), "Note_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.US).format(new Date()) + ".aac.pending");
                wave = new AacRecording(pending);
                hasNote = true; noteGeneration++; recordedMs = 0; peak = -90;
                }
                resume();
            } else if ("RESUME".equals(action) && hasNote) resume();
            else if ("PAUSE".equals(action)) pause("Paused — tap Resume to continue this note");
            else if ("STOP".equals(action) && hasNote) finish();
            else if ("CANCEL".equals(action) && hasNote) cancel();
        } catch (Exception e) {
            pause("Recording error: " + e.getMessage());
            if (!hasNote) { carArmed=false; state="Error"; detail="Recorder could not start: "+e.getMessage(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
        }
        return START_NOT_STICKY;
    }
    private void resume() throws IOException {
        if (running) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw new IOException("Microphone permission is required");
        if (audio.getMode() == AudioManager.MODE_IN_CALL || audio.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
            state = "Paused"; detail = "Call in progress — resume after the call"; notifyState(); return;
        }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            state = "Paused"; detail = "Audio is busy — tap Resume when ready"; notifyState(); return;
        }
        try {
            if(CaptureProcessing.prefs(this).getBoolean("host_capture",false)) {
                host=new HostMicrophone();
                inputSource="Android Auto host microphone";
                processingReport="Android Auto host capture · mono PCM 16 kHz → AAC 44.1 kHz\nPhone audio source and Android effect overrides do not apply. Host processing is controlled by the head unit and Android Auto.";
            } else {
                int requestedSource=CaptureProcessing.selectedSource(this);
                if(requestedSource==MediaRecorder.AudioSource.UNPROCESSED&&!CaptureProcessing.supportsUnprocessed(this))
                    throw new IOException("Unprocessed capture is unavailable. Choose another source in Settings.");
                int size = Math.max(8192, AudioRecord.getMinBufferSize(AacRecording.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT));
                recorder = new AudioRecord(requestedSource, AacRecording.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("Microphone could not initialize");
                processing=new CaptureProcessing(this,recorder);
                sessionSamples=0;
                nearFullScaleSamples=0;
                AudioRecord source = recorder;
                captureCallback = new AudioManager.AudioRecordingCallback() {
                    @Override public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
                        if (running && recorder == source && configs.stream().anyMatch(AudioRecordingConfiguration::isClientSilenced))
                            pause("Paused — Android gave the microphone to another app");
                    }
                };
                recorder.registerAudioRecordingCallback(getMainExecutor(), captureCallback);
                recorder.startRecording();
                if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IOException("Microphone could not start");
                inputSource = "Detecting microphone";
                updateProcessingReport();
            }
            running = true; state = "Recording"; detail = "Listening • " + inputSource; silenceSince = SystemClock.elapsedRealtime();
            worker = new Thread(this::capture, "Autio-capture"); worker.start(); notifyState();
        } catch (Exception e) {
            if(host!=null) { try { host.stop(); } catch(RuntimeException ignored) {} }
            releaseRecorder(); audio.abandonAudioFocusRequest(focus);
            throw new IOException(e.getMessage(), e);
        }
    }
    private void capture() {
        short[] samples = new short[2048];
        long lastCheck = SystemClock.elapsedRealtime();
        long lastRouteCheck = 0;
        try {
            while (running) {
                int count = host!=null ? host.read(samples) : recorder.read(samples, 0, samples.length, AudioRecord.READ_BLOCKING);
                if (!running) break;
                if (count < 0) throw new IOException("Microphone read failed (" + count + ")");
                if (count == 0) continue;
                synchronized (lock) {
                    if (!running) break;
                    wave.write(samples, count);
                    recordedMs = wave.samples * 1000 / AacRecording.RATE;
                }
                double sum = 0; int max = 0;
                for (int i=0; i<count; i++) {
                    int value = Math.abs((int)samples[i]);
                    max = Math.max(max, value);
                    sum += (double)value * value;
                    if(value>=32700) nearFullScaleSamples++;
                }
                sessionSamples+=count;
                db = Math.max(-90, 20 * Math.log10(Math.max(1, Math.sqrt(sum/count)) / 32768));
                peak = Math.max(-90, 20 * Math.log10(Math.max(1, max) / 32768.0));
                long now = SystemClock.elapsedRealtime();
                if(now-lastRouteCheck>1000) { updateInputSource(); updateProcessingReport(); lastRouteCheck=now; }
                if (db > -55) silenceSince = now;
                detail = now - silenceSince > 5000 ? "Very low input — check microphone" : peak > -0.5 ? "Clipping — speak more softly" : "Listening • " + inputSource;
                if (audio.getMode() == AudioManager.MODE_IN_CALL || audio.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
                    main.post(() -> pause("Paused for a call — tap Resume when ready")); break;
                }
                if (now - lastCheck > 2000) { if(getFilesDir().getUsableSpace()<16L*1024*1024) { main.post(()->pause("Storage nearly full — stop and save this note")); break; } synchronized(lock) { wave.checkpoint(); } lastCheck = now; }
            }
        } catch (Exception e) {
            Thread failed=Thread.currentThread();
            if(running) main.post(() -> { if(running&&worker==failed) pause("Paused: " + e.getMessage()); });
        }
    }
    private void updateInputSource() {
        if(host!=null) return;
        try {
            AudioDeviceInfo device=recorder.getRoutedDevice();
            if(device==null) { inputSource="Input source unavailable"; return; }
            switch(device.getType()) {
                case AudioDeviceInfo.TYPE_BUILTIN_MIC: inputSource="Built-in microphone"; break;
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: inputSource="Bluetooth microphone"; break;
                case AudioDeviceInfo.TYPE_WIRED_HEADSET: inputSource="Wired headset microphone"; break;
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET: inputSource="USB microphone"; break;
                default: inputSource="External audio input";
            }
        } catch(RuntimeException ignored) { inputSource="Input source unavailable"; }
    }
    private void releaseRecorder() {
        if(host!=null) { CaptureProcessing.prefs(this).edit().putString("last_report",processingReport).apply(); host=null; }
        if (recorder != null) {
            updateProcessingReport();
            CaptureProcessing.prefs(this).edit().putString("last_report",processingReport).apply();
            if(processing!=null) { processing.close(); processing=null; }
            if (captureCallback != null) recorder.unregisterAudioRecordingCallback(captureCallback);
            recorder.release(); recorder = null;
        }
    }
    private void updateProcessingReport() {
        if(processing!=null&&recorder!=null) {
            processingReport=processing.report(recorder,inputSource,nearFullScaleSamples,sessionSamples);
        }
    }
    private void pause(String reason) {
        running = false;
        if(host!=null) { try { host.stop(); } catch(RuntimeException ignored) {} }
        if (recorder != null) { try { recorder.stop(); } catch (IllegalStateException ignored) {} }
        if (worker != null) {
            try { worker.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (worker.isAlive()) { detail = "Microphone is stopping — please wait"; return; }
            worker = null;
        }
        releaseRecorder(); audio.abandonAudioFocusRequest(focus); db = -90; peak = -90;
        if (hasNote && !saving) {
            state = "Paused"; detail = reason;
            try { synchronized(lock) { wave.checkpoint(); } } catch (IOException e) { detail = "Storage error: " + e.getMessage(); }
            notifyState();
        }
    }
    private void cancel() throws IOException {
        pause("Discarding note");
        if(worker!=null) throw new IOException("Microphone is still stopping. Try Cancel again.");
        File discardMarker=new File(pending.getPath()+".discard");
        try(FileOutputStream marker=new FileOutputStream(discardMarker)) { marker.getFD().sync(); }
        saving=true; state="Discarding"; detail="Discarding your note..."; notifyState();
        new Thread(()->{
            String result="Recording discarded";
            try {
                synchronized(lock) {
                    try { if(wave!=null) wave.close(); } catch(IOException ignored) {} finally { wave=null; }
                    File container=new File(pending.getPath().replace(".aac.pending",".m4a.pending"));
                    boolean removed=!pending.exists()||pending.delete();
                    removed=(!container.exists()||container.delete())&&removed;
                    if(!removed) result="Discarded · temporary cleanup pending"; else discardMarker.delete();
                }
            } finally {
                final String message=result;
                main.post(()->{
                    saving=false; hasNote=false; recordedMs=0; pending=null;
                    db=-90; peak=-90; inputSource="Microphone not active";
                    state="Ready"; detail=message;
                    if(carArmed) notifyState(); else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
                });
            }
        },"Autio-discard").start();
    }
    private void finish() throws IOException {
        pause("Saving note");
        if (worker != null) throw new IOException("Microphone is still stopping. Try Stop again.");
        saving = true; state = "Saving"; detail = "Finalizing your note…"; notifyState();
        new Thread(() -> {
            boolean finalized=false;
            try { saveNote(); finalized=true; }
            catch (Exception e) { detail = "Recovery needed · audio retained privately: " + e.getMessage(); }
            final boolean saved=finalized;
            main.post(() -> {
                saving = false; hasNote = false; state = saved?"Saved":"Recovery needed";
                DeliveryJobs.kick(this);
                if(carArmed) notifyState();
                else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
            });
        }, "Autio-save").start();
    }
    private void saveNote() throws IOException {
        synchronized(lock) { wave.close(); wave = null; }
        File saved = new File(getFilesDir(), pending.getName().replace(".pending", ""));
        if (!pending.renameTo(saved)) throw new IOException("Note is safe locally, but could not be finalized");
        File compressed = AacRecording.finalizeNote(saved);
        getSharedPreferences("durations", 0).edit().putLong(compressed.getName(), recordedMs).apply();
        saved.delete(); // Discard recoverable AAC staging only after a finalized M4A exists.
        saved = compressed;
        try { detail=DeliveryJobs.finishDelivery(this,saved); }
        catch(Exception e) { detail="Saved privately · delivery pending: "+e.getMessage(); DeliveryJobs.schedule(this); }

    }
    @Override public void onDestroy() {
        if (hasNote && !saving) pause("Paused because the recorder service stopped");
        if (wave != null) { try { wave.close(); } catch (IOException ignored) {} wave = null; }
        hasNote = false;
        carArmed = false;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

