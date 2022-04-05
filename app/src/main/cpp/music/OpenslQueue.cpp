//
// 存放ffmpeg解码出来的数据，放到此队列中，opensl 从此队列中取数据进行播放
//
#include "OpenslQueue.h"


OpenslQueue::OpenslQueue(AudioPlayStatus *status) : status(status) {
    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&condPacket, NULL);
}

OpenslQueue::~OpenslQueue() {
    //销毁线程锁
    pthread_mutex_destroy(&mutex);
    //销毁条件变量
    pthread_cond_destroy(&condPacket);
}

//入队
int OpenslQueue::putAvPacket(AVPacket *avPacket) {
    pthread_mutex_lock(&mutex);
    queuePacket.push(avPacket);
    LOGI("OpenslQueue 入队，当前队列大小%d", queuePacket.size());
    //唤醒等待线程
    pthread_cond_signal(&condPacket);
    pthread_mutex_unlock(&mutex);
    return 0;
}

int OpenslQueue::getAVPacket(AVPacket *avPacket) {
    pthread_mutex_lock(&mutex);
    while (status != NULL && !status->exit) {
        if (queuePacket.size() > 0) {
            //从队列头部取出
            AVPacket *packet = queuePacket.front();
            //进行copy
            if (av_packet_ref(avPacket, packet) == 0) {
                queuePacket.pop();
            }
            //释放
            av_packet_free(&packet);
            av_free(packet);
            packet = NULL;
            LOGI("从队列里面取出一个avPacket，当前队列大小%d", queuePacket.size());
            break;
        } else {
            LOGI("没有数据线程等待");
            //没有数据线程等待
            pthread_cond_wait(&condPacket, &mutex);
        }
    }

    pthread_mutex_unlock(&mutex);
    return 0;
}

int OpenslQueue::getQueueSize() {
    int size = 0;
    pthread_mutex_lock(&mutex);
    size = queuePacket.size();
    pthread_mutex_unlock(&mutex);
    return size;
}

int OpenslQueue::clearQueue() {
    pthread_mutex_lock(&mutex);
    while (queuePacket.size() > 0) {
        //从队列头部取出
        AVPacket *packet = queuePacket.front();
        queuePacket.pop();
        //释放
        av_packet_free(&packet);
        av_free(packet);
        packet = NULL;
    }
    LOGI("清空avpacke队列");
    pthread_mutex_unlock(&mutex);
    return 0;
}

