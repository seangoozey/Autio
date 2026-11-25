package com.autio.notes;

import android.app.Instrumentation;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Isolated staging/preferences; only uniquely named .syncthing test documents touch the folder. */
final class ReliabilitySmokeTest {
    static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    static void run(Instrumentation test,Bundle result) throws Exception {
        Context real=test.getTargetContext();
        require(!RecorderService.hasNote&&!RecorderService.saving,"User recording is open");
        String folder=NoteDelivery.folder(real); require(folder!=null,"Managed folder required for integration check");
        String prefix=".syncthing.autio-check-"+UUID.randomUUID();
        File directory=new File(real.getCacheDir(),prefix); require(directory.mkdir(),"Cannot create isolated staging");
        Context isolated=new ContextWrapper(real) {
            @Override public File getFilesDir() { return directory; }
            @Override public SharedPreferences getSharedPreferences(String name,int mode) { return real.getSharedPreferences(prefix+name,mode); }
        };
        NoteDelivery.prefs(isolated).edit().putString("folder",folder).putBoolean("wake",false).commit();
        List<Uri> created=new ArrayList<>();
        try {
            File seed=new File(directory,prefix+".aac.pending");
            short[] tone=new short[4410]; for(int i=0;i<tone.length;i++) tone[i]=(short)(6000*Math.sin(i*2*Math.PI*440/44100));
            try(AacRecording encoder=new AacRecording(seed)) { for(int i=0;i<10;i++) encoder.write(tone,tone.length); }
            // Simulate an unusable MP4 after a process death. Authoritative ADTS must recover it.
            File container=new File(seed.getPath().replace(".aac.pending",".m4a.pending"));
            Files.write(container.toPath(),new byte[]{0,1,2});
            NoteStorage.recover(isolated);
            File[] staged=NoteStorage.notes(isolated); require(staged.length==1&&staged[0].getName().endsWith(".m4a"),"AAC did not recover to M4A");
            File recovered=staged[0]; byte[] expected=Files.readAllBytes(recovered.toPath()); String name=recovered.getName();
            // Broken access retains the durable queue; restored access retries without opening activity.
            NoteDelivery.prefs(isolated).edit().putString("folder","content://invalid.example/tree/missing").commit();
            require(!DeliveryJobs.drain(isolated)&&recovered.exists(),"Failed delivery lost private audio");
            NoteDelivery.prefs(isolated).edit().putString("folder",folder).commit();
            require(DeliveryJobs.drain(isolated),"Retry failed: "+DeliveryJobs.status);
            require(!recovered.exists(),"Verified delivery retained private duplicate");
            LibraryNote[] library=NoteStorage.library(isolated);
            LibraryNote delivered=Arrays.stream(library).filter(n->n.name.equals(name)).findFirst().orElseThrow(); created.add(delivered.uri);
            try(InputStream in=real.getContentResolver().openInputStream(delivered.uri)) { require(Arrays.equals(expected,in.readAllBytes()),"Delivered bytes differ"); }
            // A process death between finalization and AAC cleanup must not block recovery.
            File other=new File(directory,prefix+"-duplicate.aac");
            try(AacRecording encoder=new AacRecording(other)) { for(int i=0;i<3;i++) encoder.write(tone,tone.length); }
            AacRecording.finalizeNote(other); NoteStorage.recover(isolated);
            require(!other.exists(),"Finalized AAC residue not cleaned");
            new File(other.getPath().replace(".aac",".m4a")).delete();
            // Receipt survives a lost private-cleanup step and an external managed-folder deletion.
            Files.write(recovered.toPath(),expected);
            require(DocumentsContract.deleteDocument(real.getContentResolver(),delivered.uri),"Could not delete test managed note");
            require(DeliveryJobs.drain(isolated),"Receipt cleanup failed");
            require(Arrays.stream(NoteStorage.library(isolated)).noneMatch(n->n.name.equals(name)),"Deleted managed note was resurrected");
            require(!recovered.exists(),"Receipt did not release private residue");
            // A durable discard decision takes precedence over recovery.
            File cancelled=new File(directory,prefix+"-cancel.aac.pending");
            Files.write(cancelled.toPath(),new byte[]{1,2,3});
            Files.write(new File(cancelled.getPath()+".discard").toPath(),new byte[0]);
            NoteStorage.recover(isolated); require(!cancelled.exists(),"Discarded audio was recovered");
            require(NoteStorage.notes(isolated).length==0,"Test left staging behind");
            result.putString("stream","PASS: interrupted AAC recovery/remux, delivery failure retains audio, restored-folder retry, byte verification, private cleanup, crash after finalization, managed-folder deletion stays deleted, and crash during discard.\n");
        } finally {
            for(Uri uri:created) try { DocumentsContract.deleteDocument(real.getContentResolver(),uri); } catch(Exception ignored) {}
            File[] files=directory.listFiles(); if(files!=null) for(File file:files) file.delete(); directory.delete();
            for(String name:new String[]{"delivery","delivered","durations","exports"}) real.deleteSharedPreferences(prefix+name);
        }
    }
}
