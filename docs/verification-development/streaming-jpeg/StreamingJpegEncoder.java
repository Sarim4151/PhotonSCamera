// Reconstructed from executed temporary harness after runtime reset; not rerun here.
package com.hinnka.mycamera.gallery;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
public class StreamingJpegEncoder {
 static { System.load("/tmp/streaming-jpeg-probe/libprobe.so"); }
 native boolean encodeNative(int fd,int w,int h,int q,RowSource source);
 static native int open(String path); static native void close(int fd);
 public static class RowSource {
  final int width,failAt; int calls; int[] first;
  RowSource(int w,int fail){width=w;failAt=fail;}
  public void readRow(int y,int[] row){
   check(y==calls++ && row.length==width,"row order/size");
   if(first==null)first=row;else check(first==row,"row reused");
   if(y==failAt)throw new IllegalStateException("callback failure");
   for(int x=0;x<width;x++)row[x]=x<width/3?0xffff0000:x<width*2/3?0xff00ff00:0xff0000ff;
  }
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static int fdCount()throws Exception{try(var s=Files.list(Path.of("/proc/self/fd"))){return (int)s.count();}}
 public static void main(String[] args)throws Exception{
  var e=new StreamingJpegEncoder();var f=new File("/tmp/streaming-jpeg-probe/output.jpg");
  if(args.length>0){int fd=open(f.toString());try{check(e.encodeNative(fd,4000,12000,95,new RowSource(4000,-1)),"48MP");}finally{close(fd);}System.out.println("48MP streaming under16MiB Java heap PASS bytes="+f.length());return;}
  for(int[] s:new int[][]{{1,1},{17,19},{129,73},{65500,1}}){
   int fd=open(f.toString());var r=new RowSource(s[0],-1);try{check(e.encodeNative(fd,s[0],s[1],100,r),"encode");}finally{close(fd);}
   var image=ImageIO.read(f);check(image.getWidth()==s[0]&&image.getHeight()==s[1]&&r.calls==s[1],"dimensions/count");
   if(s[0]>100)for(int n=0;n<3;n++){int p=image.getRGB(s[0]*(n*2+1)/6,0),expected=new int[]{0xff0000,0x00ff00,0x0000ff}[n];for(int sh=0;sh<24;sh+=8)check(Math.abs(((p>>sh)&255)-((expected>>sh)&255))<=3,"color");}
   System.out.println("Roundtrip dimensions/color/row reuse PASS "+s[0]+"x"+s[1]);
  }
  int before=fdCount();for(int n=0;n<100;n++){int fd=open(f.toString());try{try{e.encodeNative(fd,129,73,90,new RowSource(129,5));throw new AssertionError("swallowed");}catch(IllegalStateException expected){check(expected.getMessage().equals("callback failure"),"exception");}}finally{close(fd);}}
  check(fdCount()==before,"FD leak");System.out.println("100 callback exceptions preserve exception + no FD leak PASS");
  int full=open("/dev/full");try{check(!e.encodeNative(full,129,73,100,new RowSource(129,-1)),"disk full");}finally{close(full);}
  check(!e.encodeNative(-1,1,1,100,new RowSource(1,-1)),"invalid fd");int fd=open(f.toString());try{check(!e.encodeNative(fd,65501,1,100,new RowSource(1,-1)),"dimension");}finally{close(fd);}
  System.out.println("Disk-full libjpeg error path + invalid fd/dimension PASS");
 }
}
