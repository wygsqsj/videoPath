//
// Created by DELL on 2022/2/20.
//

#ifndef VIDEOPATH_OPENSLQUEUE_H
#define VIDEOPATH_OPENSLQUEUE_H

#include "queue"
#include "AudioPlayStatus.h"
#include "pthread.h"
#include "../android_log.h"

extern "C" {
//导入ffmpeg
#include "libavcodec/avcodec.h"
}

class OpenslQueue {

public:
    //状态对象
    AudioPlayStatus *status = NULL;
    //声明队列
    std::queue<AVPacket *> queuePacket;
    //线程锁
    pthread_mutex_t mutex;
    //条件变量
    pthread_cond_t condPacket;


public:

    OpenslQueue(AudioPlayStatus *status);

    virtual ~OpenslQueue();

    //入队函数
    int putAvPacket(AVPacket *packet);

    //出队函数
    int getAVPacket(AVPacket *avPacket);

    //队列大小
    int getQueueSize();

    int clearQueue();


};


#endif //VIDEOPATH_OPENSLQUEUE_H
