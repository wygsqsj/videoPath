//
// Created by DELL on 2022/2/20.
//

#ifndef VIDEOPATH_CALLJAVAHELPER_H
#define VIDEOPATH_CALLJAVAHELPER_H

#define MAIN_THREAD 0
#define CHILD_THREAD 1

#include "jni.h"
#include <linux/stddef.h>
#include "../android_log.h"

class CallJavaHelper {

public:
    JavaVM *javaVm = NULL;
    JNIEnv *jniEnv = NULL;
    jobject jobj;

    jmethodID jMethodId, jCallTimeMethodId;

public:

    CallJavaHelper(JavaVM *javaVm, JNIEnv *jniEnv, jobject *jobj);

    virtual ~CallJavaHelper();

    void onCallReady(int threadType);

    void onPlayTimeCallBack(int threadType, int curr, int total);

};


#endif //VIDEOPATH_CALLJAVAHELPER_H
