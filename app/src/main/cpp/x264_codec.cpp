#include <jni.h>
#include <string>
#include "VideoChannel.h"
#include "safeQueue.h"
#include "AudioChannel.h"
//导入rtmp
extern "C" {
#include "librtmp/rtmp.h"
}

void *start(void *args);

void releasePackets(RTMPPacket *pPacket);


int isStart = 0;
pthread_t pid;//子线程对象
RTMP *rtmp = nullptr;
//队列
SafeQueue<RTMPPacket *> packets;
//推流标志
int readyPushing = 0;
uint32_t start_time;

VideoChannel *videoChannel = nullptr;
AudioChannel *audioChannel = nullptr;

//编码层回调此方法，将编码好的数据放到队列中
void callBack(RTMPPacket *packet) {
    if (packet) {
        if (packets.size() > 50) {
            packets.clear();
        }
        packet->m_nTimeStamp = RTMP_GetTime() - start_time;
        packets.push(packet);
    }
}


//初始化编码层
extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo8_x264_LivePush_native_1init(JNIEnv *env, jobject thiz) {
    videoChannel = new VideoChannel;
    videoChannel->setVideoCallBack(callBack);
}

//初始化x264
extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo8_x264_LivePush_native_1setVideoEncInfo(JNIEnv *env, jobject thiz,
                                                                jint width, jint height, jint fps,
                                                                jint bitrate) {
    if (videoChannel) {
        videoChannel->createX264Encode(width, height, fps, bitrate);
    }
}


extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo8_x264_LivePush_native_1start(JNIEnv *env, jobject thiz, jstring rtmpUrl) {
    //连接rtmp服务器
    if (isStart) {
        return;
    }
    const char *path = env->GetStringUTFChars(rtmpUrl, 0);
    char *url = new char[strlen(path) + 1];
    strcpy(url, path);
    isStart = 1;
    //开启子线程连接服务器
    pthread_create(&pid, 0, start, url);

    env->ReleaseStringUTFChars(rtmpUrl, path);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo8_x264_LivePush_native_1pushVideo(JNIEnv *env, jobject thiz,
                                                          jbyteArray data_) {

    //没有实例化编码或者rtmp没连接成功时退出
    if (!videoChannel || !readyPushing) {
        return;
    }
    //转换成数组进行编码
    jbyte *data = env->GetByteArrayElements(data_, NULL);
    videoChannel->encodeData(data);
    env->ReleaseByteArrayElements(data_, data, 0);
}



extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo8_x264_LivePush_native_1stop(JNIEnv *env, jobject thiz) {
    isStart = 0;//关闭rtmp
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo8_x264_LivePush_native_1release(JNIEnv *env, jobject thiz) {
    if (videoChannel) {
        delete (videoChannel);
        videoChannel = nullptr;
    }

    if (audioChannel) {
        delete audioChannel;
        audioChannel = nullptr;
    }

    if (rtmp) {
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        rtmp = nullptr;
        LOGI("释放RTMP");
    }
}


void *start(void *args) {
    char *url = static_cast<char *>(args);
    //不断重试，链接服务器
    do {
        //初始化RTMP,申请内存
        rtmp = RTMP_Alloc();
        if (!rtmp) {
            LOGI("RTMP 创建失败");
            break;
        }
        RTMP_Init(rtmp);
        //设置超时时间
        rtmp->Link.timeout = 10;
        //设置地址
        int ret = RTMP_SetupURL(rtmp, (char *) url);
        if (!ret) {
            LOGI("RTMP 创建失败");
            break;
        }
        LOGI("connect %s", url);
        //设置输出模式
        RTMP_EnableWrite(rtmp);
        LOGI("connect Connect");
        //连接
        if (!(ret = RTMP_Connect(rtmp, 0))) break;
        LOGI("connect ConnectStream");
        //连接流
        if (!(ret = RTMP_ConnectStream(rtmp, 0))) break;
        LOGI("connect 成功");
        start_time = RTMP_GetTime();
        packets.setWork(1);
        RTMPPacket *packet = 0;

        //添加音频头到队列中
        if (audioChannel) {
            callBack(audioChannel->getAudioHead());
            LOGI("添加音频头到队列中");
        }
        //从队列中取出数据发送
        readyPushing = 1;
        while (isStart) {
            packets.pop(packet);
            if (!isStart) {
                break;
            }
            if (!packet) {
                continue;
            }
            packet->m_nInfoField2 = rtmp->m_stream_id;
            //发送
            ret = RTMP_SendPacket(rtmp, packet, 1);
            releasePackets(packet);
            if (!ret) {
                LOGI("发送数据失败！");
                break;
            }
        }
        releasePackets(packet);
    } while (false);

    delete url;
    return nullptr;
}

//释放rtmp
void releasePackets(RTMPPacket *packet) {
    if (packet) {
        RTMPPacket_Free(packet);
        delete packet;
        packet = nullptr;
    }
}

//初始化音频编码器faac
extern "C"
JNIEXPORT jint JNICALL
Java_com_wish_videopath_demo8_x264_LivePush_native_1initAudioCodec(JNIEnv *env, jobject thiz,
                                                               jint sample_rate,
                                                               jint channel_count) {
    audioChannel = new AudioChannel;
    audioChannel->setCallBack(callBack);
    audioChannel->initCodec(sample_rate, channel_count);
    return audioChannel->getInputByteNum();
}

//编码音频数据
extern "C"
JNIEXPORT void JNICALL
Java_com_wish_videopath_demo8_x264_LivePush_native_1pushAudio(JNIEnv *env, jobject thiz,
                                                          jbyteArray buffer) {
    //没有实例化编码或者rtmp没连接成功时退出
    if (!audioChannel || !readyPushing) {
        return;
    }
    jbyte *data = env->GetByteArrayElements(buffer, 0);
    audioChannel->encode(data);
    env->ReleaseByteArrayElements(buffer, data, 0);
}