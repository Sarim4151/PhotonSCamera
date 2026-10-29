# Streaming JPEG host verification

These results were executed in the streaming_encoder agent session on 2026-10-24. Original temporary harness, headers, .so and class files disappeared when the tool runtime reset between turns; this record is reconstructed from the preserved tool execution transcript. It is not a fresh rerun and does not claim Android verification.

## Actual environment and dependencies

`java --list-modules`: OpenJDK17.0.20 with jdk.compiler.
`dpkg-query -W libjpeg-turbo8`: libjpeg-turbo8:amd64 2.1.5-2ubuntu2.
`strings /lib/x86_64-linux-gnu/libjpeg.so.8`: libjpeg-turbo version2.1.5(build20240408).

Official OpenJDK17 JNI headers were retrieved with GitHub fetch_file from openjdk/jdk17u master paths src/java.base/share/native/include/jni.h and src/java.base/unix/native/include/jni_md.h. Exact installed libjpeg headers were retrieved from libjpeg-turbo/libjpeg-turbo tag2.1.5: jpeglib.h,jmorecfg.h,jerror.h. Temporary jconfig.h defined JPEG_LIB_VERSION80 and BITS_IN_JSAMPLE8. All third-party headers remained temporary, outside production source.

## Commands actually executed

```sh
g++ -std=c++17 -Wall -Wextra -Werror -fPIC -shared -I/tmp/streaming-jpeg-probe /workspace/scratch/5dda72a7336a/recovery/source/PhotonSCamera-main/app/src/main/cpp/streaming_jpeg_jni.cpp /tmp/streaming-jpeg-probe/shim.cpp /lib/x86_64-linux-gnu/libjpeg.so.8 -o /tmp/streaming-jpeg-probe/libprobe.so
java -m jdk.compiler/com.sun.tools.javac.Main /tmp/streaming-jpeg-probe/com/hinnka/mycamera/gallery/StreamingJpegEncoder.java
java -Xcheck:jni -Djava.awt.headless=true -cp /tmp/streaming-jpeg-probe com.hinnka.mycamera.gallery.StreamingJpegEncoder
java -Xmx16m -Xcheck:jni -Djava.awt.headless=true -cp /tmp/streaming-jpeg-probe com.hinnka.mycamera.gallery.StreamingJpegEncoder large
```

The unchanged production native source was linked with a temporary JNI shim exposing open(path)/close(fd). Java used the exact production native symbol/class name and compatible readRow(int,int[]) method. Its supplier checked sequential y, row width, identical array identity, filled red/green/blue stripes, and optionally threw IllegalStateException at row5. ImageIO decoded the completed files, checking dimensions and stripe-center channels within3 values. Exception probes repeated100times and checked /proc/self/fd counts before/after. /dev/full tested libjpeg output failure; negative fd and width65501 tested input rejection. Large mode streamed4000x12000 without ImageIO decode, under-Xmx16m.

## Observed output (exit0)

```
Roundtrip dimensions/color/row reuse PASS 1x1
Roundtrip dimensions/color/row reuse PASS 17x19
Roundtrip dimensions/color/row reuse PASS 129x73
Roundtrip dimensions/color/row reuse PASS 65500x1
100 callback exceptions preserve exception + no FD leak PASS
Disk-full libjpeg error path + invalid fd/dimension PASS
48MP streaming under16MiB Java heap PASS bytes=1482625
Baseline SOF0 dimensions4000x12000 and 4:4:4 sampling PASS
JPEG EOI PASS
```

The final two checks used a separate Python JPEG marker parser: SOF0 contained width4000,height12000,3components, each sampling byte0x11; file endedFFD9. The attempted /usr/bin/time wrapper did not exist; this failed command was then rerun without it. No process-RSS number was obtained. A16MiB Java heap constrains Java allocations only; native boundedness also follows the scanline implementation and no coefficient optimization/progressive mode.

## Scope and limitations

Real JVM/JNI+libjpeg host runtime executed actual product native implementation. This checks native row callbacks, descriptor ownership, longjmp output failures, dimensions and pixel packing. It does not execute Android ParcelFileDescriptor/Kotlin private-path cleanup, Android libjpeg build/link, metadata/publication pipeline, Android decoder or device GPU.
