//
// Created by DELL on 2021/12/30.
//

#ifndef RTMPDEMO_AUDIOCHANNEL_H
#define RTMPDEMO_AUDIOCHANNEL_H

#include "faac.h"
#include <inttypes.h>
#include <stdint.h>
#include "RTMP_LOG.h"
//导入rtmp
extern "C" {
#include "librtmp/rtmp.h"
}

typedef void(*AudioCallback)(RTMPPacket *);

class AudioChannel {
public:
    AudioChannel();

    ~AudioChannel();

    void initCodec(int sampleRate, int channels);

    void setCallBack(AudioCallback callback);

    int getInputByteNum() {
        return inputByteNum;
    }

    void encode(int8_t *data);

    RTMPPacket *getAudioHead();


public:
    AudioCallback callbackAudio;//设置rtmp数据回调
    faacEncHandle codec = 0;
    int chanelCount = 2;
    //编码出的音频一帧最大大小
    unsigned long maxOutputBytes;
    //输出数据缓冲区
    unsigned char *outputBuffer = 0;
    //输入的数据大小
    unsigned long inputByteNum;
};


#endif //RTMPDEMO_AUDIOCHANNEL_H
