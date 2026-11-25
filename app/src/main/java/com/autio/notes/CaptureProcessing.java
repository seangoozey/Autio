package com.autio.notes;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.media.audiofx.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Session-scoped effect handles. Android defaults are observed without attaching an override. */
final class CaptureProcessing implements AutoCloseable {
    static final String PREFS="capture_processing";
    static final String[] EFFECT_KEYS={"agc","ns","aec"};
    static final String[] EFFECT_NAMES={"Automatic gain control","Noise suppression","Echo cancellation"};
    static final String[] MODES={"Android default","Off","On"};
    static final int[] SOURCES={MediaRecorder.AudioSource.MIC,MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,MediaRecorder.AudioSource.UNPROCESSED};
    static final String[] SOURCE_NAMES={"Microphone (standard)","Voice recognition","Voice communication","Unprocessed"};
    private final int source;
    private final List<EffectState> effects=new ArrayList<>();
    private String capturePath="";

    static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS,0); }
    static int selectedSource(Context context) {
        int selected=prefs(context).getInt("source",MediaRecorder.AudioSource.MIC);
        for(int value:SOURCES) if(value==selected) return value;
        return MediaRecorder.AudioSource.MIC;
    }
    static String sourceName(int source) {
        for(int i=0;i<SOURCES.length;i++) if(SOURCES[i]==source) return SOURCE_NAMES[i];
        return "Source "+source;
    }
    static boolean supportsUnprocessed(Context context) {
        return "true".equals(context.getSystemService(AudioManager.class).getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
    }
    static boolean available(int index) {
        try {
            switch(index) {
                case 0: return AutomaticGainControl.isAvailable();
                case 1: return NoiseSuppressor.isAvailable();
                default: return AcousticEchoCanceler.isAvailable();
            }
        } catch(RuntimeException e) { return false; }
    }
    static int mode(SharedPreferences preferences,String key) {
        int value=preferences.getInt(key,0);
        return value>=0&&value<MODES.length?value:0;
    }
    CaptureProcessing(Context context,AudioRecord recorder) {
        source=recorder.getAudioSource();
        SharedPreferences preferences=prefs(context);
        for(int index=0;index<EFFECT_KEYS.length;index++) {
            int mode=mode(preferences,EFFECT_KEYS[index]);
            EffectState state=new EffectState(EFFECT_NAMES[index],mode);
            effects.add(state);
            if(mode==0) continue;
            if(!available(index)) { state.failure="unavailable on this device"; continue; }
            try {
                int session=recorder.getAudioSessionId();
                switch(index) {
                    case 0: state.effect=AutomaticGainControl.create(session); break;
                    case 1: state.effect=NoiseSuppressor.create(session); break;
                    default: state.effect=AcousticEchoCanceler.create(session); break;
                }
                if(state.effect==null) state.failure="unavailable for this session";
                else {
                    int result=state.effect.setEnabled(mode==2);
                    if(result!=AudioEffect.SUCCESS) state.failure="request rejected ("+result+")";
                }
            } catch(RuntimeException e) { state.failure="request failed ("+e.getClass().getSimpleName()+")"; }
        }
    }
    String report(AudioRecord recorder,String route,long nearLimit,long samples) {
        StringBuilder report=new StringBuilder(sourceName(source)).append("\nInput: ").append(route);
        report.append("\nSession ").append(recorder.getAudioSessionId()).append(" · ").append(recorder.getSampleRate()).append(" Hz");
        for(EffectState state:effects) {
            report.append("\n").append(state.name).append(" — ").append(MODES[state.mode]);
            if(state.effect!=null) {
                try {
                    report.append(" requested, Android reports ").append(state.effect.getEnabled()?"on":"off");
                    if(!state.effect.hasControl()) report.append(" (control held elsewhere)");
                } catch(RuntimeException e) { report.append(" · state unavailable"); }
            }
            if(state.failure!=null) report.append(" · ").append(state.failure);
        }
        try {
            AudioRecordingConfiguration configuration=recorder.getActiveRecordingConfiguration();
            if(configuration!=null) {
                StringBuilder path=new StringBuilder("\nDevice stream: ").append(configuration.getFormat().getSampleRate()).append(" Hz");
                path.append("\nEffects reported on capture path:");
                List<AudioEffect.Descriptor> descriptors=configuration.getEffects();
                if(descriptors.isEmpty()) path.append(" none exposed");
                for(AudioEffect.Descriptor descriptor:descriptors) path.append("\n• ").append(descriptor.name);
                capturePath=path.toString();
            }
        } catch(RuntimeException e) { report.append("\nCapture-path report unavailable"); }
        report.append(capturePath);
        report.append(String.format(Locale.US,"\nPCM samples near full scale (before AAC): %.3f%%",samples==0?0:nearLimit*100.0/samples));
        return report.toString();
    }
    @Override public void close() {
        for(EffectState state:effects) if(state.effect!=null) {
            try { state.effect.release(); } catch(RuntimeException ignored) {}
        }
        effects.clear();
    }
    private static final class EffectState {
        final String name;
        final int mode;
        AudioEffect effect;
        String failure;
        EffectState(String name,int mode) { this.name=name; this.mode=mode; }
    }
}
