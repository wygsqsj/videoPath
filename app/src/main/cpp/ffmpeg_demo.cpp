#include <jni.h>
#include <string>

extern "C" {
//导入ffmpeg
#include "libavcodec/avcodec.h"
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_wish_videopath_demo10_FFmpegActivity_getText(JNIEnv *env, jobject thiz) {
    std::string js = avcodec_configuration();
    return env->NewStringUTF(js.c_str());
}
