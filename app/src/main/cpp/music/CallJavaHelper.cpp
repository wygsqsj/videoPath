//
// Created by DELL on 2022/2/20.
//

#include "CallJavaHelper.h"

CallJavaHelper::CallJavaHelper(JavaVM *javaVm, JNIEnv *jniEnv, jobject *jobj) : javaVm(
        javaVm), jniEnv(jniEnv) {
    //设为全局对象
    this->jobj = jniEnv->NewGlobalRef(*jobj);

    jclass jclz = jniEnv->GetObjectClass(*jobj);
    if (!jclz) {
        LOGI("回调java线程，获取class异常");
        return;
    }
    //ffmpeg初始化成功
    jMethodId = jniEnv->GetMethodID(jclz, "onCallPrepared", "()V");

    //音频播放时间
    jCallTimeMethodId = jniEnv->GetMethodID(jclz, "onPlayTimeCallBack", "(II)V");

}

CallJavaHelper::~CallJavaHelper() = default;

void CallJavaHelper::onCallReady(int threadType) {
    if (threadType == MAIN_THREAD) {
        jniEnv->CallVoidMethod(jobj, jMethodId);
    } else {
        JNIEnv *env;
        if (javaVm->AttachCurrentThread(&env, 0) != JNI_OK) {
            LOGI("回调java线程，jvm绑定线程异常");
            return;
        }

        env->CallVoidMethod(jobj, jMethodId);
        //释放线程
        javaVm->DetachCurrentThread();

    }

}

void CallJavaHelper::onPlayTimeCallBack(int threadType, int curr, int total) {
    if (threadType == MAIN_THREAD) {
        jniEnv->CallVoidMethod(jobj, jCallTimeMethodId, curr, total);
    } else {
        JNIEnv *env;
        if (javaVm->AttachCurrentThread(&env, 0) != JNI_OK) {
            LOGI("回调java线程，jvm绑定线程异常");
            return;
        }

        env->CallVoidMethod(jobj, jCallTimeMethodId, curr, total);
        //释放线程
        javaVm->DetachCurrentThread();

    }
}




