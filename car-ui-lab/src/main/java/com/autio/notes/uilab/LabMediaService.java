package com.autio.notes.uilab;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media.MediaBrowserServiceCompat;
import java.util.*;

/** Minimal media plumbing for a clearly labeled, sensor-free template layout experiment. */
public class LabMediaService extends MediaBrowserServiceCompat {
    private static MediaSessionCompat session;
    static synchronized MediaSessionCompat session(Context context) {
        if(session==null) {
            session=new MediaSessionCompat(context.getApplicationContext(),"Autio layout lab");
            session.setCallback(new MediaSessionCompat.Callback() {
                @Override public void onPlayFromSearch(String query,Bundle extras) {
                    session.setPlaybackState(new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_ERROR,0,0).setErrorMessage("This layout preview has no playable audio").build());
                }
            });
            session.setMetadata(new MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE,"Autio interface preview").putString(MediaMetadataCompat.METADATA_KEY_ARTIST,"No audio is recorded or played").build());
            session.setPlaybackState(new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_STOPPED,0,0).setActions(0).build());
        }
        return session;
    }
    @Override public void onCreate() { super.onCreate(); setSessionToken(session(this).getSessionToken()); }
    @Override public BrowserRoot onGetRoot(String name,int uid,Bundle extras) { return new BrowserRoot("lab",null); }
    @Override public void onLoadChildren(String parent,Result<List<MediaBrowserCompat.MediaItem>> result) { result.sendResult(Collections.emptyList()); }
}
