LOCAL_PATH := $(call my-dir)

LAME_ROOT := $(LOCAL_PATH)/third_party/lame-4.0

include $(CLEAR_VARS)
LOCAL_MODULE := mp3lame
LOCAL_C_INCLUDES := \
    $(LAME_ROOT) \
    $(LAME_ROOT)/include \
    $(LAME_ROOT)/libmp3lame
LOCAL_EXPORT_C_INCLUDES := $(LAME_ROOT)/include
LOCAL_CFLAGS := \
    -O3 \
    -DSTDC_HEADERS \
    -DHAVE_LIMITS_H \
    -DHAVE_STDINT_H \
    -DHAVE_STDLIB_H \
    -DHAVE_STRING_H \
    -DHAVE_UNISTD_H \
    -DHAVE_ERRNO_H \
    -DHAVE_FCNTL_H \
    -DHAVE_SYS_TYPES_H \
    -DHAVE_SYS_STAT_H \
    -Dieee754_float32_t=float
LOCAL_SRC_FILES := \
    third_party/lame-4.0/libmp3lame/VbrTag.c \
    third_party/lame-4.0/libmp3lame/bitstream.c \
    third_party/lame-4.0/libmp3lame/encoder.c \
    third_party/lame-4.0/libmp3lame/fft.c \
    third_party/lame-4.0/libmp3lame/gain_analysis.c \
    third_party/lame-4.0/libmp3lame/id3tag.c \
    third_party/lame-4.0/libmp3lame/lame.c \
    third_party/lame-4.0/libmp3lame/mpglib_interface.c \
    third_party/lame-4.0/libmp3lame/newmdct.c \
    third_party/lame-4.0/libmp3lame/presets.c \
    third_party/lame-4.0/libmp3lame/psymodel.c \
    third_party/lame-4.0/libmp3lame/quantize.c \
    third_party/lame-4.0/libmp3lame/quantize_pvt.c \
    third_party/lame-4.0/libmp3lame/reservoir.c \
    third_party/lame-4.0/libmp3lame/set_get.c \
    third_party/lame-4.0/libmp3lame/tables.c \
    third_party/lame-4.0/libmp3lame/takehiro.c \
    third_party/lame-4.0/libmp3lame/util.c \
    third_party/lame-4.0/libmp3lame/vbrquantize.c \
    third_party/lame-4.0/libmp3lame/version.c
LOCAL_LDLIBS := -lm
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := metronome_mp3
LOCAL_SRC_FILES := metronome_mp3_jni.c
LOCAL_SHARED_LIBRARIES := mp3lame
LOCAL_LDLIBS := -llog
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)
