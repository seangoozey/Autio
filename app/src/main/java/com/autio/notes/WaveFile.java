package com.autio.notes;

import java.io.*;

/** PCM16 mono WAV. The header is refreshed on pause so interrupted notes are recoverable. */
final class WaveFile implements Closeable {
    static final int RATE = 44100;
    private final RandomAccessFile file;
    long bytes;
    WaveFile(File path) throws IOException {
        file = new RandomAccessFile(path, "rw");
        file.setLength(0);
        file.write(new byte[44]);
        checkpoint();
    }
    void write(short[] samples, int count) throws IOException {
        if (bytes + count * 2L > 0xffffffffL - 36) throw new IOException("Note reached WAV size limit. Stop and begin a new note.");
        byte[] data = new byte[count * 2];
        for (int i = 0; i < count; i++) { data[2*i] = (byte)samples[i]; data[2*i+1] = (byte)(samples[i] >> 8); }
        file.write(data);
        bytes += data.length;
    }
    private void le(int value, int size) throws IOException {
        for (int i = 0; i < size; i++) file.write(value >> (8*i));
    }
    void checkpoint() throws IOException {
        file.seek(0);
        file.writeBytes("RIFF"); le((int)(36 + bytes), 4); file.writeBytes("WAVEfmt ");
        le(16,4); le(1,2); le(1,2); le(RATE,4); le(RATE*2,4); le(2,2); le(16,2);
        file.writeBytes("data"); le((int)bytes,4);
        file.getFD().sync();
        file.seek(44 + bytes);
    }
    @Override public void close() throws IOException { try { checkpoint(); } finally { file.close(); } }
}
