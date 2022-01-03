//
// Created by DELL on 2021/12/22.
//
#include <cstdint>
#include <x264.h>
#include <cstring>
#include <pthread.h>
#include "VideoChannel.h"
#include "librtmp/rtmp.h"

//初始化x264框架
void VideoChannel::createX264Encode(int width, int height, int fps, int bitrate) {
    // 加锁, 设置视频编码参数 与 编码互斥
    pthread_mutex_lock(&mMutex);

    mWidth = width;
    mHeight = height;
    mFps = fps;
    mBitrate = bitrate;
    mYSize = width * height;
    mUVSize = mYSize / 4;
    //初始化
    if (mVideoCodec) {
        x264_encoder_close(mVideoCodec);
        mVideoCodec = nullptr;
    }
    //类比与MedeaFormat
    x264_param_t param;
    x264_param_default_preset(&param,
                              "ultrafast",//编码器速度,越快质量越低，适合直播
                              "zerolatency"//编码质量
    );
    //编码等级
    param.i_level_idc = 32;
    param.i_csp = X264_CSP_I420;    //nv12
    param.i_width = width;
    param.i_height = height;
    //设置没有B帧
    param.i_bframe = 0;
    /*
     * 码率控制方式
     * X264_RC_CBR:恒定码率 cpu紧张时画面质量差，以网络传输稳定为先
     * X264_RC_VBR:动态码率，cpu紧张时花费更多时间，画面质量比较均衡，适合本地播放
     * X264_RC_ABR:平均码率，是一种折中方式，也是网络传输中最常用的方式
     *
     */
    param.rc.i_rc_method = X264_RC_ABR;
    //码率，k为单位，所以字节数除以1024
    param.rc.i_bitrate = bitrate / 1024;
    /*
     * 帧率
     * 代表1秒有多少帧
     * 帧率时间
     * 当前帧率为25，那么帧率时间我们一般理解成1/25=40ms
     * 但是帧率的单位不是时间，而是一个我们设定的值 i_fps_den/i_timebase_den
     * 例如当前是1000帧了，他对应的时间戳计算方式为：1000（1/25）
     *
     * 如果你的i_fps_den/i_timebase_den 设置的不是 1/fps,那么最终是以这两个参数为单位计算间隔的,一般我们都会
     * 设置成1/fps
    */
    param.i_fps_num = fps;
    param.i_fps_den = 1;
    param.i_timebase_den = param.i_fps_num;
    param.i_timebase_num = param.i_fps_den;
    //使用fps计算帧间距
    param.b_vfr_input = 0;
    //25帧一个I帧
    param.i_keyint_max = fps * 2;
    //sps和pps自动放到I帧前面
    param.b_repeat_headers = 1;
    //开启多线程
    param.i_threads = 1;
    //编码质量
    x264_param_apply_profile(&param, "baseline");
    //打开编码器
    mVideoCodec = x264_encoder_open(&param);
    //输入缓冲区
    pic_in = new x264_picture_t;
    //初始化缓冲区大小
    x264_picture_alloc(pic_in, X264_CSP_I420, width, height);

    // 解锁, 设置视频编码参数 与 编码互斥
    pthread_mutex_unlock(&mMutex);
}


/**
 * 将java层传递的yuv（NV21）数据编码成h264码流
 * @param data 输入的yuv数据
 * 将 y u v分别放到单个通道中，X264可以将多个帧的通道同时存入，例如I P P三帧的Y数据放如x264的y通道
 * 所以x264框架可以一次性输出好几个NAL单元
 *
 */
void VideoChannel::encodeData(int8_t *data) {
    // 加锁, 设置视频编码参数 与 编码互斥
    pthread_mutex_lock(&mMutex);

    //将Y放入x264的y通道
    memcpy(pic_in->img.plane[0], data, mYSize);
    // 取出u v数据放入通道
    for (int i = 0; i < mUVSize; i++) {
        //img.plane[1]里面放的是u数据；我们的yuv格式是NV21,data[1]是U数据
        *(pic_in->img.plane[1] + i) = *(data + mYSize + i * 2 + 1);
        //img.plane[2]里面放的是V数据；data[0]是V数据
        *(pic_in->img.plane[2] + i) = *(data + mYSize + i * 2);
    }

    //编码后的NAL个数，可以理解成编码出了几帧
    int pi_nal;
    //编码后的数据存储区,这里面放了pi_nal个帧的数据
    x264_nal_t *pp_nal;
    //输出的编码数据参数,类似于MedeaCodec编码EncodeInfo
    x264_picture_t pic_out;
    //开始编码
    x264_encoder_encode(mVideoCodec, &pp_nal, &pi_nal, pic_in, &pic_out);

    //缓存sps和pps
    uint8_t sps[100];
    uint8_t pps[100];
    int spsLen;
    int ppsLen;
    //编码后的数据
    if (pi_nal > 0) {
        for (int i = 0; i < pi_nal; i++) {
            LOGI("当前帧数：%d，当前帧大小：%d", i, pp_nal[i].i_payload);
            //rtmp 是将sps和pps一起打包发送出去的
            if (pp_nal[i].i_type == NAL_SPS) {
                //减去00000001分隔符的长度
                spsLen = pp_nal[i].i_payload - 4;
                memcpy(sps, pp_nal[i].p_payload + 4, spsLen);
            } else if (pp_nal[i].i_type == NAL_PPS) {
                //减去00000001分隔符的长度
                ppsLen = pp_nal[i].i_payload - 4;
                memcpy(pps, pp_nal[i].p_payload + 4, ppsLen);
                //发送到rtmp服务器
                sendSPSPPS(sps, pps, spsLen, ppsLen);
            } else {
                sendVideo(pp_nal[i].i_type, pp_nal[i].i_payload, pp_nal[i].p_payload);
            }

        }
    }

    // 解锁, 设置视频编码参数 与 编码互斥
    pthread_mutex_unlock(&mMutex);
}

VideoChannel::VideoChannel() {
    // 初始化互斥锁, 设置视频编码参数 与 编码互斥
    pthread_mutex_init(&mMutex, 0);
}


VideoChannel::~VideoChannel() {
    // 销毁互斥锁, 设置视频编码参数 与 编码互斥
    pthread_mutex_destroy(&mMutex);
    if (mVideoCodec) {
        x264_encoder_close(mVideoCodec);
        mVideoCodec = nullptr;
        LOGI("释放X264");
    }
}

//发送sps和pps
void VideoChannel::sendSPSPPS(uint8_t *sps, uint8_t *pps, int sps_len, int pps_len) {
    //sps、pps的packet
    int body_size = 16 + sps_len + pps_len;
    LOGI("创建sps Packet ,body_size:%d", body_size);
    RTMPPacket *packet = new RTMPPacket;
    //初始化packet数据,申请数组
    RTMPPacket_Alloc(packet, body_size);
    int i = 0;
    //固定协议字节标识
    packet->m_body[i++] = 0x17;
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x01;
    //sps配置信息
    packet->m_body[i++] = sps[1];
    packet->m_body[i++] = sps[2];
    packet->m_body[i++] = sps[3];
    //固定
    packet->m_body[i++] = 0xFF;
    packet->m_body[i++] = 0xE1;
    //两个字节存储sps长度
    packet->m_body[i++] = (sps_len >> 8) & 0xFF;
    packet->m_body[i++] = (sps_len) & 0xFF;
    //sps内容写入
    memcpy(&packet->m_body[i], sps, sps_len);
    i += sps_len;

    //pps开始写入
    packet->m_body[i++] = 0x01;
    //pps长度
    packet->m_body[i++] = (pps_len >> 8) & 0xFF;
    packet->m_body[i++] = (pps_len) & 0xFF;
    memcpy(&packet->m_body[i], pps, pps_len);

    //设置视频类型
    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = body_size;
    //通道值，音视频不能相同
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    //回调给传输层
    if (mVideoCallBack) {
        mVideoCallBack(packet);
    }
}

//创建帧数据RTMPPacket
void VideoChannel::sendVideo(int type, int payload, uint8_t *p_payload) {
    //分隔符 右000000001 和 000001两种
    if (p_payload[2] == 0x00) {
        payload -= 4;
        p_payload += 4;
    } else if (p_payload[2] == 0x01) {
        payload -= 3;
        p_payload += 3;
    }
    RTMPPacket *packet = new RTMPPacket;
    int body_size = payload + 9;
    LOGI("创建帧数据 Packet ,body_size:%d", body_size);
    //初始化内部body数组
    RTMPPacket_Alloc(packet, body_size);

    packet->m_body[0] = 0x27;//非关键帧
    if (type == NAL_SLICE_IDR) {//关键帧
        packet->m_body[0] = 0x17;
    }
    //固定
    packet->m_body[1] = 0x01;
    packet->m_body[2] = 0x00;
    packet->m_body[3] = 0x00;
    packet->m_body[4] = 0x00;
    //帧长度，4个字节存储
    packet->m_body[5] = (payload >> 24) & 0xFF;
    packet->m_body[6] = (payload >> 16) & 0xFF;
    packet->m_body[7] = (payload >> 8) & 0xFF;
    packet->m_body[8] = (payload) & 0xFF;
    //copy帧数据
    memcpy(&packet->m_body[9], p_payload, payload);

    //设置视频类型
    packet->m_hasAbsTimestamp = 0;
    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = body_size;
    //通道值，音视频不能相同
    packet->m_nChannel = 0x04;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    if (mVideoCallBack) {
        mVideoCallBack(packet);
    }
}

//设置传输层回调
void VideoChannel::setVideoCallBack(VideoChannel::VideoCallBack callBack) {
    this->mVideoCallBack = callBack;
}


