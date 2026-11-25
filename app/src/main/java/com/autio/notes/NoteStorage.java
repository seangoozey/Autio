package com.autio.notes;

import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;

final class NoteStorage {
    static File[] notes(Context ctx) {
        File[] files = ctx.getFilesDir().listFiles((dir, name) -> name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".aac"));
        if (files == null) return new File[0];
        java.util.Arrays.sort(files, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }
    static volatile String libraryError="";
    static LibraryNote[] library(Context context) {
        java.util.Map<String,LibraryNote> entries=new java.util.HashMap<>();
        String selection=NoteDelivery.folder(context);
        libraryError="";
        if(selection!=null) {
            android.net.Uri tree=android.net.Uri.parse(selection);
            android.net.Uri children=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree,android.provider.DocumentsContract.getTreeDocumentId(tree));
            try(android.database.Cursor cursor=context.getContentResolver().query(children,new String[]{"document_id","_display_name","_size","last_modified"},null,null,null)) {
                if(cursor==null) throw new IOException("Cannot read managed folder");
                while(cursor.moveToNext()) {
                    String name=cursor.getString(1);
                    if(name==null||!(name.endsWith(".m4a")||name.endsWith(".aac")||name.endsWith(".wav"))) continue;
                    entries.put(name,new LibraryNote(name,android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)),cursor.getLong(2),cursor.getLong(3),false));
                }
            } catch(Exception e) { libraryError="Managed folder unavailable: "+e.getMessage(); }
        }
        for(File file:notes(context)) {
            if(context.getSharedPreferences("delivered",0).getBoolean(NoteDelivery.receiptKey(context,file),false)) continue;
            entries.putIfAbsent(file.getName(),LibraryNote.local(file));
        }
        LibraryNote[] result=entries.values().toArray(new LibraryNote[0]);
        java.util.Arrays.sort(result,(a,b)->Long.compare(b.modified,a.modified));
        return result;
    }
    static String mime(File file) { return file.getName().endsWith(".m4a") ? "audio/mp4" : file.getName().endsWith(".aac") ? "audio/aac" : "audio/wav"; }
    static long duration(Context ctx, File file) {
        if(file.getName().endsWith(".wav")) return Math.max(0,file.length()-44)*1000/88200;
        long cached=ctx.getSharedPreferences("durations",0).getLong(file.getName(),-1);
        if(cached>=0) return cached;
        android.media.MediaMetadataRetriever reader=new android.media.MediaMetadataRetriever();
        try { reader.setDataSource(file.getAbsolutePath()); return Long.parseLong(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)); }
        catch(Exception e) { return 0; }
        finally { try { reader.release(); } catch(Exception ignored) {} }
    }
    static synchronized void recover(Context ctx) throws IOException {
        if (RecorderService.hasNote || RecorderService.saving) return;
        File[] discarded=ctx.getFilesDir().listFiles((dir,name)->name.endsWith(".aac.pending.discard"));
        if(discarded!=null) for(File marker:discarded) {
            File audio=new File(marker.getPath().replace(".discard",""));
            File container=new File(audio.getPath().replace(".aac.pending",".m4a.pending"));
            boolean removed=(!audio.exists()||audio.delete());
            removed=(!container.exists()||container.delete())&&removed;
            if(removed) marker.delete();
        }
        File[] aacPending = ctx.getFilesDir().listFiles((dir, name) -> name.endsWith(".aac.pending"));
        if(aacPending != null) for(File file : aacPending) {
            if(new File(file.getPath()+".discard").exists()) continue;
            File recovered = new File(ctx.getFilesDir(), file.getName().replace(".aac.pending", "_recovered.aac"));
            if(!file.renameTo(recovered)) throw new IOException("Could not recover " + file.getName());
            // Recover a closed container if valid; otherwise remux authoritative AAC packets.
            File container=new File(file.getPath().replace(".aac.pending", ".m4a.pending"));
            File renamedContainer=new File(recovered.getPath().replace(".aac",".m4a.pending"));
            if(container.exists()) container.renameTo(renamedContainer);
        }
        IOException failure=null;
        for(File file:notes(ctx)) if(file.getName().endsWith(".aac")) {
            try { File result=AacRecording.finalizeNote(file); if(result.exists()) file.delete(); }
            catch(IOException e) { failure=e; }
        }
        File[] pending = ctx.getFilesDir().listFiles((dir, name) -> name.endsWith(".wav.pending"));
        if (pending == null) { if(failure!=null) throw failure; return; }
        for (File file : pending) {
            try (RandomAccessFile wav = new RandomAccessFile(file, "rw")) {
                long bytes = Math.max(0, wav.length()-44) / 2 * 2;
                if (wav.length() < 44) continue;
                wav.setLength(44 + bytes);
                wav.seek(4); wav.writeInt(Integer.reverseBytes((int)(bytes+36)));
                wav.seek(40); wav.writeInt(Integer.reverseBytes((int)bytes)); wav.getFD().sync();
            }
            File recovered = new File(ctx.getFilesDir(), file.getName().replace(".wav.pending", "_recovered.wav"));
            if (!file.renameTo(recovered)) throw new IOException("Could not recover " + file.getName());
        }
        if(failure!=null) throw failure;
    }
}
