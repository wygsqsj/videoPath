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
    //音乐时长
    int audioDuration = 0;
    //seek 时加锁
    pthread_mutex_t seek_mutex;
    //当前解码的帧数
    int frameCount;

public:
    MyFFmpeg(AudioPlayStatus *playStatus, CallJavaHelper *callJava, const char *audioUrl);

    virtual ~MyFFmpeg();

    void prepare();

    void decodeAudioThread();

    void start();

    void seekTo(int64_t secds);

    void pause();

    void resume();

    void setMute(int channel);

    void setVolume(int setVolume);
};


#endif //VIDEOPATH_MYFFMPEG_H
