package com.hinnka.mycamera.video;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class VideoWindFilterTest {
    private static byte[] tone(double hz, int channels, int amplitude) {
        byte[] b = new byte[48000 * channels * 2];
        for (int i=0;i<48000;i++) for (int c=0;c<channels;c++) {
            int x = (int)Math.round(amplitude*Math.sin(2*Math.PI*hz*i/48000));
            int k=(i*channels+c)*2; b[k]=(byte)x;b[k+1]=(byte)(x>>8);
        }
        return b;
    }
    private static double rms(byte[] b,int channels,int channel) {
        double sum=0;int count=0;
        for(int i=12000;i<b.length/(channels*2);i++) {
            int k=(i*channels+channel)*2;
            int x=(short)((b[k]&255)|(b[k+1]<<8));sum+=(double)x*x;count++;
        }
        return Math.sqrt(sum/count);
    }
    private static double gain(double hz,int cutoff) {
        byte[] b=tone(hz,1,16000);double before=rms(b,1,0);
        new VideoWindFilter(48000,1,cutoff).process(b,b.length);
        return rms(b,1,0)/before;
    }
    @Test public void offIsBitExact() {
        byte[] b=tone(71,2,32767);byte[] copy=b.clone();
        new VideoWindFilter(48000,2,0).process(b,b.length);assertArrayEquals(copy,b);
    }
    @Test public void highCutsRumbleButRetainsVoiceBand() {
        assertTrue(gain(20,160)<0.04);
        assertTrue(gain(1000,160)>0.98);
    }
    @Test public void strengthsProduceIncreasingWindAttenuation() {
        assertTrue(gain(50,65)>gain(50,100));
        assertTrue(gain(50,100)>gain(50,160));
    }
    @Test public void chunkBoundariesDoNotChangeSamples() {
        byte[] full=tone(123,2,25000), split=full.clone();
        new VideoWindFilter(48000,2,100).process(full,full.length);
        VideoWindFilter f=new VideoWindFilter(48000,2,100);
        for(int offset=0;offset<split.length;) {
            int count=Math.min(173*4,split.length-offset);
            byte[] part=Arrays.copyOfRange(split,offset,offset+count);f.process(part,count);
            System.arraycopy(part,0,split,offset,count);offset+=count;
        }
        assertArrayEquals(full,split);
    }
    @Test public void channelsAreIndependentAndTrailingBytesUntouched() {
        byte[] stereo=tone(400,2,20000);
        for(int i=0;i<stereo.length;i+=4){stereo[i+2]=0;stereo[i+3]=0;}
        new VideoWindFilter(48000,2,160).process(stereo,stereo.length);
        assertEquals(0.0,rms(stereo,2,1),0.0);assertTrue(rms(stereo,2,0)>10000);
        byte[] withTail={1,2,3,4,100,101,102,103};
        new VideoWindFilter(48000,2,160).process(withTail,4);
        assertArrayEquals(new byte[]{100,101,102,103},Arrays.copyOfRange(withTail,4,8));
    }
    @Test public void dcAndFullScaleRemainBounded() {
        byte[] b=new byte[96000];Arrays.fill(b,(byte)127);
        new VideoWindFilter(48000,1,160).process(b,b.length);
        assertTrue(rms(b,1,0)<2);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsIncompleteStereoFrame() {
        new VideoWindFilter(48000,2,160).process(new byte[8],6);
    }
}
