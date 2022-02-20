//
// ffmpeg解码类
//

#ifndef VIDEOPATH_MYFFMPEG_H
#define VIDEOPATH_MYFFMPEG_H


#include "AudioPlayStatus.h"
#include "CallJavaHelper.h"
#include "pthread.h"
#include "OpenslPlay.h"

extern "C" {
#include <libavformat/avformat.h>
};

class MyFFmpeg {
public:
    CallJavaHelper *callJava = NULL;
    const char *audioUrl = NULL;
    //解码线程
    pthread_t decodeThread = NULL;
    //ffmpeg解码上下文
    AVFormatContext *avFormatContext = NULL;
    //播放器
    OpenslPlay *audio = NULL;
    //播放状态
    AudioPlayStatus *playStatus = NULL;


public:
    MyFFmpeg(AudioPlayStatus *playStatus, CallJavaHelper *callJava, const char *audioUrl);

    virtual ~MyFFmpeg();

    void prepare();

    void decodeAudioThread();

    void start();

};


#endif //VIDEOPATH_MYFFMPEG_H
