/**
 * 使用ffmpeg 解码MP3音乐，交给opensl es进行播放
 */
#include <jni.h>
#include <string>
#include <android/native_window_jni.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_wish_videopath_demo11_MusicActivity_getNative(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("你好");
}