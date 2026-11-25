package com.autio.notes;

import android.app.job.*;
import android.content.*;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Disk staging is the durable queue; receipts prevent resurrecting managed-folder deletions. */
public class DeliveryJobs extends JobService {
    static final int JOB=7101;
    static final AtomicBoolean running=new AtomicBoolean();
    static volatile String status="";
    static void schedule(Context context) {
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(scheduler.getPendingJob(JOB)==null) scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(context,DeliveryJobs.class))
            .setPersisted(true).setPeriodic(15*60*1000L).build());
    }
    static void kick(Context context) {
        Context app=context.getApplicationContext(); schedule(app);
        new Thread(()->drain(app),"Autio-recovery-delivery").start();
    }
    static boolean drain(Context context) {
        if(!running.compareAndSet(false,true)) return true;
        boolean complete=true;
        try {
            synchronized(NoteStorage.class) {
                if(RecorderService.hasNote||RecorderService.saving) return false;
                try { NoteStorage.recover(context); }
                catch(Exception e) { status="Recovery pending: "+e.getMessage(); complete=false; }
            }
            for(File file:NoteStorage.notes(context)) {
                if(Thread.currentThread().isInterrupted()) return false;
                if(file.getName().endsWith(".aac")) { status="Recovery pending · original retained privately"; complete=false; continue; }
                try { finishDelivery(context,file); }
                catch(Exception e) { status="Saved privately · delivery pending: "+e.getMessage(); complete=false; }
            }
            if(complete) {
                status=NoteDelivery.folder(context)==null?"Choose a managed folder in Settings":"Managed folder is up to date";
                new Handler(Looper.getMainLooper()).post(()->{
                    if(!RecorderService.hasNote&&!RecorderService.saving&&"Recovery needed".equals(RecorderService.state)) {
                        RecorderService.state="Saved"; RecorderService.detail="Recovered audio saved in managed folder";
                    }
                });
            }
            return complete;
        } finally { running.set(false); }
    }
    static String finishDelivery(Context context,File file) throws IOException {
        if(NoteDelivery.folder(context)==null) throw new IOException("Choose a managed folder in Settings");
        NoteDelivery.deliver(context,file);
        // deliver has recorded a durable receipt only after the completed copy was verified.
        if(file.exists()&&!file.delete()) throw new IOException("Delivered; private staging cleanup pending");
        return "Saved in managed folder · transfer handled by Syncthing";
    }
    private Thread worker;
    private volatile boolean stopped;
    @Override public boolean onStartJob(JobParameters params) {
        stopped=false;
        worker=new Thread(()->{ drain(this); new Handler(Looper.getMainLooper()).post(()->{ if(!stopped) jobFinished(params,false); }); },"Autio-delivery-job");
        worker.start(); return true;
    }
    @Override public boolean onStopJob(JobParameters params) { stopped=true; if(worker!=null) worker.interrupt(); return true; }
}
