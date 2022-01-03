//
// Created by DELL on 2021/12/22.
//视频编码工具类
//
#include <inttypes.h>
#include <stdint.h>
#include "x264.h"
#include "RTMP_LOG.h"

//导入rtmp
extern "C" {
#include "librtmp/rtmp.h"
}

class VideoChannel {
    typedef void (*VideoCallBack)(RTMPPacket *packet);

public:
    VideoChannel();

    ~VideoChannel();

    //初始化编码器
    void createX264Encode(int width, int height, int fps, int bitrate);

    //编码数据
    void encodeData(int8_t *data);

    //发送sps和pps到rtmp
    void sendSPSPPS(uint8_t *sps, uint8_t *pps, int sps_len, int pps_len);

    //发送帧数据到rtmp
    void sendVideo(int type, int payload, uint8_t *p_payload);

    //设置传输层回调，将编码好的数据发送给rtmp
    void setVideoCallBack(VideoCallBack callBack);

private:
    int mWidth;
    int mHeight;
    int mFps;
    int mBitrate;
    int mYSize;
    int mUVSize;
    //x264编码器
    x264_t *mVideoCodec = nullptr;
    //输入缓冲区，类比与MediaCodec的inputBuffer
    x264_picture_t *pic_in = nullptr;
    //回调给传输层
    VideoCallBack mVideoCallBack;

    pthread_mutex_t mMutex;

};