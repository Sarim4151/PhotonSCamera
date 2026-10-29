package com.hinnka.mycamera.video;

/** Stateful, sample-count-preserving PCM16LE Butterworth high-pass. Owned by one audio loop. */
public final class VideoWindFilter {
    private final int channels;
    private final boolean bypass;
    private final double b0,b1,b2,a1,a2;
    private final double[] z1,z2;
    public VideoWindFilter(int sampleRate,int channels,int cutoffHz) {
        if(sampleRate<=0 || channels<1 || channels>2 || cutoffHz<0 || cutoffHz>=sampleRate/2)
            throw new IllegalArgumentException("Invalid wind filter format");
        this.channels=channels;this.bypass=cutoffHz==0;
        z1=new double[channels];z2=new double[channels];
        double w=2*Math.PI*cutoffHz/sampleRate, c=Math.cos(w), alpha=Math.sin(w)/Math.sqrt(2.0);
        double norm=1+alpha;
        b0=(1+c)/(2*norm);b1=-(1+c)/norm;b2=b0;a1=-2*c/norm;a2=(1-alpha)/norm;
    }
    public void process(byte[] pcm,int byteCount) {
        if(byteCount<0 || byteCount>pcm.length || byteCount%(channels*2)!=0)
            throw new IllegalArgumentException("PCM must contain complete interleaved frames");
        if(bypass)return;
        for(int k=0;k<byteCount;k+=2) {
            int channel=(k/2)%channels;
            double x=(short)((pcm[k]&255)|(pcm[k+1]<<8));
            double y=b0*x+z1[channel];
            z1[channel]=b1*x-a1*y+z2[channel];z2[channel]=b2*x-a2*y;
            int value=(int)Math.max(-32768,Math.min(32767,Math.round(y)));
            pcm[k]=(byte)value;pcm[k+1]=(byte)(value>>8);
        }
    }
}
