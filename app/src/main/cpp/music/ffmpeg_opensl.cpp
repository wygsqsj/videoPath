/**
 * 使用ffmpeg 解码MP3音乐，交给opensl es进行播放
 */
#include <jni.h>
#include <string>
#include <android/native_window_jni.h>
#include "../android_log.h"
#include "CallJavaHelper.h"
#include "MyFFmpeg.h"
#include "AudioPlayStatus.h"

extern "C" {
//导入ffmpeg
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavutil/imgutils.h"
#include "libswscale/swscale.h"
#include "libavutil/time.h"
#include "libswresample/swresample.h"
}

JavaVM *javaVm = NULL;
CallJavaHelper *callJava = NULL;
MyFFmpeg *ffmpeg = NULL;
AudioPlayStatus *playStatus = NULL;

//固定写法，用于获取jvm实例
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *unused) {
    JNIEnv *env;
    javaVm = vm;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    return JNI_VERSION_1_6;
}

extern "C"
//初始化音频解码器
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo11_FFPlay_initAudio(JNIEnv *env, jobject thiz, jstring _url) {
    //string转换
    const char *audioUrl = env->GetStringUTFChars(_url, 0);

    //初始化ffmpeg
    if (ffmpeg == NULL) {
        if (callJava == NULL) {
            callJava = new CallJavaHelper(javaVm, env, &thiz);
        }
        //初始化播放状态
        if (playStatus == NULL) {
            playStatus = new AudioPlayStatus;
            //初始化ffmpeg
            ffmpeg = new MyFFmpeg(playStatus, callJava, audioUrl);
        }
        //开始初始化
        ffmpeg->prepare();
    }
    env->ReleaseStringUTFChars(_url, audioUrl);
}

//初始化完成后开始播放音频
extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo11_FFPlay_playAudio(JNIEnv *env, jobject thiz) {
    if (ffmpeg != NULL) {
        ffmpeg->start();
    }
}

//seekbar拖动跳转到某个位置播放
extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo11_FFPlay_seekToSecds(JNIEnv *env, jobject thiz, jint secds) {
    if (ffmpeg != NULL) {
        ffmpeg->seekTo(secds);
    }

}

//暂停录音
extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo11_FFPlay_n_1pause(JNIEnv *env, jobject thiz) {
    if (ffmpeg != nullptr) {
        ffmpeg->pause();
    }

}

extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo11_FFPlay_n_1resume(JNIEnv *env, jobject thiz) {
    if (ffmpeg != nullptr) {
        ffmpeg->resume();
    }

}

//设置声道 0 左声道 1右声道 2 立体声
extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo11_FFPlay_setMute(JNIEnv *env, jobject thiz, jint channel) {
    if (ffmpeg != nullptr) {
        ffmpeg->setMute(channel);
    }
}

//设置声道 0 左声道 1右声道 2 立体声
extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo11_FFPlay_n_1setVolume(JNIEnv *env, jobject thiz, jint volume) {
    if (ffmpeg != nullptr) {
        ffmpeg->setVolume(volume);
    }
}