package com.autio.notes;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.util.Objects;

/** A real document in the managed folder, or private audio still awaiting delivery. */
final class LibraryNote {
    final String name;
    final Uri uri;
    final long size, modified;
    final boolean pending;
    LibraryNote(String name,Uri uri,long size,long modified,boolean pending) {
        this.name=name; this.uri=uri; this.size=size; this.pending=pending;
        long timestamp=modified;
        if(name.startsWith("Note_")) try { timestamp=new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS",java.util.Locale.US).parse(name.substring(5,28)).getTime(); } catch(Exception ignored) {}
        this.modified=timestamp;
    }
    String getName() { return name; }
    long length() { return size; }
    long lastModified() { return modified; }
    long knownDuration(Context context) { return context.getSharedPreferences("durations",0).getLong(name,-1); }
    long duration(Context context) {
        long cached=context.getSharedPreferences("durations",0).getLong(name,-1);
        if(cached>=0) return cached;
        android.media.MediaMetadataRetriever reader=new android.media.MediaMetadataRetriever();
        try { reader.setDataSource(context,uri); long value=Long.parseLong(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)); context.getSharedPreferences("durations",0).edit().putLong(name,value).apply(); return value; }
        catch(Exception e) { return 0; }
        finally { try { reader.release(); } catch(Exception ignored) {} }
    }
    @Override public boolean equals(Object other) { return other instanceof LibraryNote&&uri.equals(((LibraryNote)other).uri); }
    @Override public int hashCode() { return Objects.hash(uri); }
    @Override public String toString() { return uri+":"+size+":"+modified; }
    static LibraryNote local(File file) { return new LibraryNote(file.getName(),Uri.fromFile(file),file.length(),file.lastModified(),true); }
}
