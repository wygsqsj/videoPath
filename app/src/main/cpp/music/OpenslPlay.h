//
// opensl es音乐播放
//

#ifndef VIDEOPATH_OPENSLPLAY_H
#define VIDEOPATH_OPENSLPLAY_H

#include "OpenslQueue.h"
#include "AudioPlayStatus.h"


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

    OpenslQueue *queue = NULL;
    AudioPlayStatus *playStatus = NULL;

    pthread_t playThread;

    AVPacket *avPacket = NULL;
    AVFrame *avFrame = NULL;//解码出来的帧

    int ret = -1;
    //重采样后的缓冲区
    uint8_t *buffer = NULL;

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

    void play();//播放

    int resampleAudio();//返回重采样的大小，用于求时间

    void initOpenSL();//初始化openSL

    int getCurrentSampleRateForOpensles(int sample_rate);
};


#endif //VIDEOPATH_OPENSLPLAY_H
