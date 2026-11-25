package com.autio.notes;

import android.media.*;
import java.io.*;
import java.nio.*;

/** Streaming AAC-LC. ADTS frames stay recoverable without a finalized MP4 header. */
final class AacRecording implements Closeable {
    static final int RATE=44100, BIT_RATE=64000;
    private final MediaCodec codec;
    private final FileOutputStream output;
    private final MediaMuxer liveMuxer;
    private int liveTrack=-1;
    private final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    long samples;
    private boolean closed;
    AacRecording(File file) throws IOException {
        output=new FileOutputStream(file);
        MediaCodec encoder=null; MediaMuxer muxer=null;
        try {
            encoder=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            MediaFormat format=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,RATE,1);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE,BIT_RATE);
            encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE); encoder.start();
            muxer=new MediaMuxer(containerPending(file).getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            codec=encoder; liveMuxer=muxer;
        } catch(Exception e) { if(encoder!=null) encoder.release(); if(muxer!=null) muxer.release(); output.close(); throw new IOException(e); }
    }
    void write(short[] pcm,int count) throws IOException {
        int offset=0; long progress=android.os.SystemClock.elapsedRealtime();
        while(offset<count) {
            boolean advanced=drain(false);
            int index=codec.dequeueInputBuffer(0);
            if(index>=0) {
                ByteBuffer target=codec.getInputBuffer(index);
                if(target==null) throw new IOException("AAC input buffer unavailable");
                target.clear(); target.order(ByteOrder.LITTLE_ENDIAN);
                int n=Math.min(count-offset,target.remaining()/2);
                for(int i=0;i<n;i++) target.putShort(pcm[offset+i]);
                codec.queueInputBuffer(index,0,n*2,samples*1000000L/RATE,0);
                samples+=n; offset+=n; advanced=true;
            }
            if(advanced) progress=android.os.SystemClock.elapsedRealtime();
            else { if(android.os.SystemClock.elapsedRealtime()-progress>5000) throw new IOException("AAC encoder not responding"); android.os.SystemClock.sleep(1); }
        }
        drain(false);
    }
    private boolean drain(boolean untilEnd) throws IOException {
        boolean advanced=false;
        long progress=android.os.SystemClock.elapsedRealtime();
        while(true) {
            int index=codec.dequeueOutputBuffer(info,0);
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER) {
                if(!untilEnd) return advanced;
                if(android.os.SystemClock.elapsedRealtime()-progress>5000) throw new IOException("AAC finalization timed out");
                android.os.SystemClock.sleep(1); continue;
            }
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                liveTrack=liveMuxer.addTrack(codec.getOutputFormat()); liveMuxer.start(); advanced=true; continue;
            }
            if(index<0) { advanced=true; continue; }
            progress=android.os.SystemClock.elapsedRealtime(); advanced=true;
            if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0) {
                ByteBuffer data=codec.getOutputBuffer(index);
                if(data==null) throw new IOException("AAC output buffer unavailable");
                int size=info.size+7;
                if(size>8191) throw new IOException("AAC packet exceeds ADTS frame limit");
                byte[] packet=new byte[size];
                packet[0]=(byte)0xff; packet[1]=(byte)0xf1; packet[2]=(byte)0x50;
                packet[3]=(byte)(0x40|(size>>11)); packet[4]=(byte)(size>>3); packet[5]=(byte)(((size&7)<<5)|0x1f); packet[6]=(byte)0xfc;
                data.position(info.offset); data.get(packet,7,info.size); output.write(packet);
                if(liveTrack<0) throw new IOException("AAC container not initialized");
                data.position(info.offset); data.limit(info.offset+info.size); liveMuxer.writeSampleData(liveTrack,data,info);
            }
            boolean end=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
            codec.releaseOutputBuffer(index,false);
            if(end) return true;
        }
    }
    void checkpoint() throws IOException { drain(false); output.getFD().sync(); }
    @Override public void close() throws IOException {
        if(closed) return;
        closed=true;
        try {
            long began=android.os.SystemClock.elapsedRealtime();
            while(true) {
                drain(false); int index=codec.dequeueInputBuffer(0);
                if(index>=0) { codec.queueInputBuffer(index,0,0,samples*1000000L/RATE,MediaCodec.BUFFER_FLAG_END_OF_STREAM); break; }
                if(android.os.SystemClock.elapsedRealtime()-began>5000) throw new IOException("AAC encoder could not stop");
                android.os.SystemClock.sleep(1);
            }
            drain(true); output.getFD().sync();
            if(liveTrack<0) throw new IOException("No audio captured");
            liveMuxer.stop();
        } finally { try { codec.stop(); } catch(Exception ignored) {} codec.release(); liveMuxer.release(); output.close(); }
    }
    private static File containerPending(File source) {
        return new File(source.getPath().replaceFirst("\\.aac(?:\\.pending)?$",".m4a.pending"));
    }
    /** Repackage AAC packets without decoding or re-encoding them. */
    static File finalizeNote(File aac) throws IOException {
        File result=new File(aac.getParentFile(),aac.getName().replaceFirst("\\.aac$",".m4a"));
        if(result.exists()) {
            MediaExtractor existing=new MediaExtractor();
            try { existing.setDataSource(result.getPath()); if(existing.getTrackCount()==1) { existing.selectTrack(0); if(existing.readSampleData(ByteBuffer.allocate(65536),0)>0) return result; } }
            catch(Exception ignored) {} finally { existing.release(); }
            throw new IOException("Existing compressed note needs inspection; original retained");
        }
        File pending=new File(result.getPath()+".pending");
        // Normal recording already muxes during capture. Only recovery needs packet repackaging.
        if(pending.exists()) {
            MediaExtractor check=new MediaExtractor(); boolean valid=false;
            try { check.setDataSource(pending.getAbsolutePath()); valid=check.getTrackCount()==1; }
            catch(Exception ignored) {} finally { check.release(); }
            if(valid) {
                if(!pending.renameTo(result)) throw new IOException("Could not finalize compressed note");
                return result;
            }
            if(!pending.delete()) throw new IOException("Could not replace interrupted audio container");
        }
        MediaExtractor extractor=new MediaExtractor(); MediaMuxer muxer=null; boolean started=false,complete=false;
        try {
            extractor.setDataSource(aac.getAbsolutePath());
            if(extractor.getTrackCount()!=1) throw new IOException("No recoverable AAC audio");
            MediaFormat format=extractor.getTrackFormat(0); extractor.selectTrack(0);
            muxer=new MediaMuxer(pending.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int track=muxer.addTrack(format); muxer.start(); started=true;
            ByteBuffer buffer=ByteBuffer.allocate(65536); MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            long frames=0;
            while(true) {
                buffer.clear(); int size=extractor.readSampleData(buffer,0); if(size<0) break;
                // ADTS AAC-LC has 1024 samples per frame; timestamps omit all paused intervals.
                info.set(0,size,frames*1024L*1000000L/RATE,0); buffer.position(0); buffer.limit(size);
                muxer.writeSampleData(track,buffer,info); frames++; extractor.advance();
            }
            if(frames==0) throw new IOException("No audio frames captured");
            muxer.stop(); started=false; muxer.release(); muxer=null;
            if(!pending.renameTo(result)) throw new IOException("Could not finalize compressed note");
            complete=true; return result;
        } catch(Exception e) { throw new IOException("Note retained as AAC: "+e.getMessage(),e); }
        finally { extractor.release(); if(muxer!=null) { if(started) try { muxer.stop(); } catch(Exception ignored) {} muxer.release(); } if(!complete) pending.delete(); }
    }
}
