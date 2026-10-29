// Reconstructed test-only fd shim. Not production code.
#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
extern "C" JNIEXPORT jint JNICALL Java_com_hinnka_mycamera_gallery_StreamingJpegEncoder_open(JNIEnv* env,jclass,jstring path) {
 const char* s=env->GetStringUTFChars(path,nullptr);if(!s)return -1;
 int fd=open(s,O_WRONLY|O_CREAT|O_TRUNC,0600);env->ReleaseStringUTFChars(path,s);return fd;
}
extern "C" JNIEXPORT void JNICALL Java_com_hinnka_mycamera_gallery_StreamingJpegEncoder_close(JNIEnv*,jclass,jint fd){close(fd);}
