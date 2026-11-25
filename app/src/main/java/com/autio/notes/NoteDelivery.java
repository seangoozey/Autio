package com.autio.notes;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;

/** Copies committed notes only. No network credentials or transfer claims. */
final class NoteDelivery {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("delivery",0); }
    static String folder(Context c) { return prefs(c).getString("folder",null); }
    static String folderLabel(Context c) {
        String tree=folder(c); if(tree==null) return "No sync folder selected";
        try { return DocumentsContract.getTreeDocumentId(Uri.parse(tree)).replace("primary:",""); }
        catch(Exception e) { return "Selected folder"; }
    }
    static String syncthingPackage(Context c) {
        for(String name:new String[]{"com.github.catfriend1.syncthingandroid","com.github.catfriend1.syncthingfork"}) {
            try { c.getPackageManager().getPackageInfo(name,0); return name; } catch(PackageManager.NameNotFoundException ignored) {}
        }
        return null;
    }
    static void requestSync(Context c) {
        if(!prefs(c).getBoolean("wake",true)) return;
        String pkg=syncthingPackage(c); if(pkg==null) return;
        c.sendBroadcast(new Intent(pkg+".action.START").setPackage(pkg));
    }
    private static Uri find(Context c,Uri tree,String name,long size) throws IOException {
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        try(Cursor cursor=c.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_SIZE},null,null,null)) {
            if(cursor==null) throw new IOException("Cannot read sync folder");
            while(cursor.moveToNext()) {
                if(name.equals(cursor.getString(1))) {
                    if(size!=cursor.getLong(2)) throw new IOException("A different file already has this name");
                    return DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0));
                }
            }
        }
        return null;
    }
    static String receiptKey(Context c,File file) { return folder(c)+"/"+file.getName(); }
    static String digest(InputStream in) throws Exception {
        java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
        byte[] bytes=new byte[65536]; int n;
        while((n=in.read(bytes))!=-1) { if(Thread.currentThread().isInterrupted()) throw new IOException("Delivery interrupted"); hash.update(bytes,0,n); }
        return android.util.Base64.encodeToString(hash.digest(),android.util.Base64.NO_WRAP);
    }
    static void verify(Context c,File file,Uri copy) throws IOException {
        try(InputStream original=new FileInputStream(file); InputStream delivered=c.getContentResolver().openInputStream(copy)) {
            if(delivered==null||!digest(original).equals(digest(delivered))) throw new IOException("Managed copy did not match original");
        } catch(Exception e) { throw new IOException("Copy verification failed: "+e.getMessage(),e); }
    }
    static void receipt(Context c,File file) throws IOException {
        if(!c.getSharedPreferences("delivered",0).edit().putBoolean(receiptKey(c,file),true).commit()) throw new IOException("Could not remember completed delivery");
    }
    static synchronized String deliver(Context c,File file) throws IOException {
        String selection=folder(c); if(selection==null) return "Saved locally · choose a sync folder in Delivery";
        // A completed receipt is final: external deletion must never recreate the note.
        if(c.getSharedPreferences("delivered",0).getBoolean(receiptKey(c,file),false)) return "Previously delivered to managed folder";
        Uri tree=Uri.parse(selection); Uri existing=find(c,tree,file.getName(),file.length());
        if(existing!=null) { verify(c,file,existing); receipt(c,file); requestSync(c); return "In sync folder · transfer handled by Syncthing"; }
        Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        // Syncthing excludes its temporary namespace from scans. Publish by rename only after close.
        String tempName=".syncthing.autio-"+java.util.UUID.randomUUID()+".tmp";
        Uri temp=DocumentsContract.createDocument(c.getContentResolver(),parent,"application/octet-stream",tempName);
        if(temp==null) throw new IOException("Could not create a file in the sync folder");
        try {
            try(InputStream in=new FileInputStream(file); OutputStream out=c.getContentResolver().openOutputStream(temp,"wt")) {
                if(out==null) throw new IOException("Could not open sync copy");
                byte[] buffer=new byte[65536]; int n; while((n=in.read(buffer))!=-1) { if(Thread.currentThread().isInterrupted()) throw new IOException("Delivery interrupted"); out.write(buffer,0,n); }
            }
            Uri published=DocumentsContract.renameDocument(c.getContentResolver(),temp,file.getName());
            if(published==null) throw new IOException("Sync folder does not support finalizing a copy");
            verify(c,file,published); receipt(c,file);
        } catch(Exception e) {
            try { DocumentsContract.deleteDocument(c.getContentResolver(),temp); } catch(Exception ignored) {}
            throw new IOException("Sync copy pending: "+e.getMessage(),e);
        }
        requestSync(c);
        return "Copied to sync folder · transfer handled by Syncthing";
    }
}
