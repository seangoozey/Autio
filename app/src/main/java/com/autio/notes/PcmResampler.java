package com.autio.notes;

/** Continuous 16 kHz little-endian mono PCM to the encoder's 44.1 kHz rate. */
final class PcmResampler {
    private long inputIndex, outputIndex;
    private short previous;
    int convert(byte[] bytes,int count,short[] output) {
        int n=0;
        for(int i=0;i<count;i+=2) {
            short current=(short)((bytes[i]&255)|(bytes[i+1]<<8));
            if(inputIndex==0) { previous=current; output[n++]=current; outputIndex++; }
            else while(outputIndex*16000L<=inputIndex*AacRecording.RATE) {
                double fraction=(outputIndex*16000L-(inputIndex-1)*AacRecording.RATE)/(double)AacRecording.RATE;
                output[n++]=(short)Math.round(previous+(current-previous)*fraction);
                outputIndex++;
            }
            previous=current; inputIndex++;
        }
        return n;
    }
}
