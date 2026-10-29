#include <jni.h>
#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <csetjmp>
#include <unistd.h>
#include <jpeglib.h>

namespace {
struct ErrorManager {
    jpeg_error_mgr base;
    jmp_buf jump;
};
void failJpeg(j_common_ptr jpeg) {
    longjmp(reinterpret_cast<ErrorManager *>(jpeg->err)->jump, 1);
}
// State lives on the heap: values changed after setjmp remain defined on error.
// No C++ object requiring destruction crosses a libjpeg longjmp boundary.
struct Encoder {
    jpeg_compress_struct jpeg;
    ErrorManager error;
    FILE *file;
    jint *argb;
    JSAMPLE *rgb;
    bool created;
};
void release(Encoder *state) {
    if (state->created) jpeg_destroy_compress(&state->jpeg);
    if (state->file) fclose(state->file);
    free(state->argb);
    free(state->rgb);
    free(state);
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hinnka_mycamera_gallery_StreamingJpegEncoder_encodeNative(
    JNIEnv *env, jobject, jint fd, jint width, jint height, jint quality, jobject source) {
    if (fd < 0 || width <= 0 || height <= 0 || width > JPEG_MAX_DIMENSION ||
        height > JPEG_MAX_DIMENSION || quality < 1 || quality > 100 || !source) return JNI_FALSE;
    jclass sourceClass = env->GetObjectClass(source);
    if (!sourceClass) return JNI_FALSE;
    jmethodID readRow = env->GetMethodID(sourceClass, "readRow", "(I[I)V");
    env->DeleteLocalRef(sourceClass);
    if (!readRow) return JNI_FALSE;
    jintArray row = env->NewIntArray(width);
    if (!row) return JNI_FALSE;
    Encoder *state = static_cast<Encoder *>(calloc(1, sizeof(Encoder)));
    if (!state) { env->DeleteLocalRef(row); return JNI_FALSE; }
    state->argb = static_cast<jint *>(malloc(static_cast<size_t>(width) * sizeof(jint)));
    state->rgb = static_cast<JSAMPLE *>(malloc(static_cast<size_t>(width) * 3));
    int ownedFd = dup(fd);
    if (ownedFd >= 0) {
        state->file = fdopen(ownedFd, "wb");
        if (!state->file) close(ownedFd);
    }
    if (!state->argb || !state->rgb || !state->file) {
        release(state); env->DeleteLocalRef(row); return JNI_FALSE;
    }
    state->jpeg.err = jpeg_std_error(&state->error.base);
    state->error.base.error_exit = failJpeg;
    if (setjmp(state->error.jump)) {
        release(state); env->DeleteLocalRef(row); return JNI_FALSE;
    }
    // Set before creation so a partial allocation in jpeg_CreateCompress is destroyed.
    state->created = true;
    jpeg_create_compress(&state->jpeg);
    jpeg_stdio_dest(&state->jpeg, state->file);
    state->jpeg.image_width = static_cast<JDIMENSION>(width);
    state->jpeg.image_height = static_cast<JDIMENSION>(height);
    state->jpeg.input_components = 3;
    state->jpeg.in_color_space = JCS_RGB;
    jpeg_set_defaults(&state->jpeg);
    jpeg_set_quality(&state->jpeg, quality, TRUE);
    for (int component = 0; component < 3; ++component) {
        state->jpeg.comp_info[component].h_samp_factor = 1;
        state->jpeg.comp_info[component].v_samp_factor = 1;
    }
    // Entropy optimization and progressive scans retain full-image coefficient arrays.
    state->jpeg.optimize_coding = FALSE;
    jpeg_start_compress(&state->jpeg, TRUE);
    while (state->jpeg.next_scanline < state->jpeg.image_height) {
        env->CallVoidMethod(source, readRow, static_cast<jint>(state->jpeg.next_scanline), row);
        if (env->ExceptionCheck()) {
            release(state); env->DeleteLocalRef(row); return JNI_FALSE;
        }
        env->GetIntArrayRegion(row, 0, width, state->argb);
        if (env->ExceptionCheck()) {
            release(state); env->DeleteLocalRef(row); return JNI_FALSE;
        }
        for (int x = 0; x < width; ++x) {
            const uint32_t pixel = static_cast<uint32_t>(state->argb[x]);
            state->rgb[x * 3] = (pixel >> 16) & 255;
            state->rgb[x * 3 + 1] = (pixel >> 8) & 255;
            state->rgb[x * 3 + 2] = pixel & 255;
        }
        JSAMPROW scanline = state->rgb;
        if (jpeg_write_scanlines(&state->jpeg, &scanline, 1) != 1) {
            release(state); env->DeleteLocalRef(row); return JNI_FALSE;
        }
    }
    jpeg_finish_compress(&state->jpeg);
    const bool flushed = fflush(state->file) == 0 && fsync(fileno(state->file)) == 0;
    const bool closed = fclose(state->file) == 0;
    state->file = nullptr;
    release(state);
    env->DeleteLocalRef(row);
    return flushed && closed ? JNI_TRUE : JNI_FALSE;
}
