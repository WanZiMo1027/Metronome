#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include "lame.h"

static void throw_illegal_state(JNIEnv *env, const char *message) {
    jclass exception_class = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (exception_class != NULL) {
        (*env)->ThrowNew(env, exception_class, message);
    }
}

JNIEXPORT jlong JNICALL
Java_com_yuntian_metronome_metronome_NativeLameMp3Encoder_nativeCreate(
        JNIEnv *env,
        jobject instance) {
    (void) instance;
    lame_t encoder = lame_init();
    if (encoder == NULL) {
        throw_illegal_state(env, "Unable to initialize LAME");
        return 0;
    }

    lame_set_in_samplerate(encoder, 44100);
    lame_set_out_samplerate(encoder, 44100);
    lame_set_num_channels(encoder, 2);
    lame_set_mode(encoder, JOINT_STEREO);
    lame_set_VBR(encoder, vbr_off);
    lame_set_brate(encoder, 192);
    lame_set_quality(encoder, 2);
    if (lame_init_params(encoder) < 0) {
        lame_close(encoder);
        throw_illegal_state(env, "LAME rejected the MP3 encoding parameters");
        return 0;
    }
    return (jlong) (intptr_t) encoder;
}

JNIEXPORT jbyteArray JNICALL
Java_com_yuntian_metronome_metronome_NativeLameMp3Encoder_nativeEncode(
        JNIEnv *env,
        jobject instance,
        jlong handle,
        jshortArray samples) {
    (void) instance;
    lame_t encoder = (lame_t) (intptr_t) handle;
    if (encoder == NULL) {
        throw_illegal_state(env, "LAME encoder is closed");
        return NULL;
    }

    jsize sample_count = (*env)->GetArrayLength(env, samples);
    if ((sample_count & 1) != 0) {
        throw_illegal_state(env, "Stereo PCM must contain an even number of samples");
        return NULL;
    }
    int frame_count = sample_count / 2;
    int output_capacity = (int) (1.25 * frame_count) + 7200;
    unsigned char *output = (unsigned char *) malloc((size_t) output_capacity);
    if (output == NULL) {
        throw_illegal_state(env, "Unable to allocate MP3 output buffer");
        return NULL;
    }

    jshort *pcm = (*env)->GetShortArrayElements(env, samples, NULL);
    if (pcm == NULL) {
        free(output);
        return NULL;
    }
    int encoded = lame_encode_buffer_interleaved(
            encoder,
            pcm,
            frame_count,
            output,
            output_capacity);
    (*env)->ReleaseShortArrayElements(env, samples, pcm, JNI_ABORT);

    if (encoded < 0) {
        free(output);
        throw_illegal_state(env, "LAME failed to encode a PCM block");
        return NULL;
    }
    jbyteArray result = (*env)->NewByteArray(env, encoded);
    if (result != NULL && encoded > 0) {
        (*env)->SetByteArrayRegion(env, result, 0, encoded, (const jbyte *) output);
    }
    free(output);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_yuntian_metronome_metronome_NativeLameMp3Encoder_nativeFlush(
        JNIEnv *env,
        jobject instance,
        jlong handle) {
    (void) instance;
    lame_t encoder = (lame_t) (intptr_t) handle;
    if (encoder == NULL) {
        throw_illegal_state(env, "LAME encoder is closed");
        return NULL;
    }

    unsigned char output[7200];
    int encoded = lame_encode_flush(encoder, output, sizeof(output));
    if (encoded < 0) {
        throw_illegal_state(env, "LAME failed to flush the MP3 stream");
        return NULL;
    }
    jbyteArray result = (*env)->NewByteArray(env, encoded);
    if (result != NULL && encoded > 0) {
        (*env)->SetByteArrayRegion(env, result, 0, encoded, (const jbyte *) output);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_yuntian_metronome_metronome_NativeLameMp3Encoder_nativeClose(
        JNIEnv *env,
        jobject instance,
        jlong handle) {
    (void) env;
    (void) instance;
    lame_t encoder = (lame_t) (intptr_t) handle;
    if (encoder != NULL) {
        lame_close(encoder);
    }
}
