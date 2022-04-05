//
// opensl es音乐播放
//

#ifndef VIDEOPATH_OPENSLPLAY_H
#define VIDEOPATH_OPENSLPLAY_H

#include "OpenslQueue.h"
#include "AudioPlayStatus.h"
#include "CallJavaHelper.h"


extern "C" {
#include <libavcodec/avcodec.h>
#include <libswresample/swresample.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
}

class OpenslPlay {

public:
    int streamIndex = -1;//音频轨道
    //包含音视频参数的结构体。很重要，可以用来获取音视频参数中的宽度、高度、采样率、编码格式等信息。
    AVCodecParameters *codecpar = NULL;
    //解码器上下文
    AVCodecContext *avCodecContext = NULL;
    //音乐时长
    int audioDuration = 0;
    //时间单位, 总时间/帧数，用于获取时间戳：帧数*time_base
    AVRational time_base;
    //当前从队列中取出的packet对应的的时间戳
    double cur_time;

    //解码后的数据播放的时间，即真正的播放时间
    double clock;

    //上次回调给java的时间
    double last_call_java_time = 0;

    //回调java层,把时间传回java
    CallJavaHelper *callJava = NULL;

    OpenslQueue *queue = NULL;
    AudioPlayStatus *playStatus = NULL;

    pthread_t playThread;


    AVPacket *avPacket = NULL;
    AVFrame *avFrame = NULL;//解码出来的帧

    int ret = -1;
    //重采样后的缓冲区
    uint8_t *buffer = NULL;
    //当前声道  0 左声道 1右声道 2 立体声
    int channel = 2;

    int data_size;//buffer size

    //opsl es相关
    //引擎对象
    SLObjectItf engineObject = NULL;
    //引擎接口
    SLEngineItf engineEngine = NULL;
    //混音器
    SLObjectItf outputMixObject = NULL;
    //混音器接口,用于操作混音器
    SLEnvironmentalReverbItf outputMixEnvironmentalReverb = NULL;

    SLEnvironmentalReverbSettings reverbSettings = SL_I3DL2_ENVIRONMENT_PRESET_STONECORRIDOR;

    SLObjectItf pcmPlayerObject = NULL;
    //缓冲区队列接口
    SLAndroidSimpleBufferQueueItf pcmBufferQueue = NULL;
    //播放器的接口
    SLPlayItf pcmPlayerPlay = NULL;
    SLMuteSoloItf pcmMutePlay = NULL;
    SLVolumeItf pcmVolumePlay = NULL;
public:

    OpenslPlay(AudioPlayStatus *playStatus, AVCodecParameters *pParameters);

    virtual ~OpenslPlay();

    void setCallJava(CallJavaHelper *callJava);

    void play();//播放

    int resampleAudio();//返回重采样的大小，用于求时间

    void initOpenSL();//初始化openSL

    int getCurrentSampleRateForOpensles(int sample_rate);

    void pause();

    void resume();

    void setMute(int channel);

    void setVolume(int percent);
};


#endif //VIDEOPATH_OPENSLPLAY_H
