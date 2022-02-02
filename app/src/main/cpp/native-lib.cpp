#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG    "音视频"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)


//导入rtmp
extern "C" {
#include "librtmp/rtmp.h"
}

//用于保存sps pps 结构体
typedef struct {
    RTMP *rtmp;
    int8_t *sps;//sps数组
    int8_t *pps;//pps数组
    int16_t sps_len;//sps数组长度
    int16_t pps_len;//pps数组长度
} Live;


Live *live = NULL;


int sendVideo(int8_t *buf, int len, long tms);

int sendAudio(int8_t *buf, int len, long tms, int type);

void saveSPSPPS(int8_t *buf, int len, Live *live);

RTMPPacket *createSPSPPSPacket(Live *live);

RTMPPacket *createAudioPacket(int8_t *buf, int len, long tms, int type, Live *live);

int sendPacket(RTMPPacket *packet);

RTMPPacket *createVideoPacket(int8_t *buf, int len, long tms, Live *live);



//发送数据到rtmp服务器端
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wish_videopath_demo8_mediacodec_ScreenLive_sendData(JNIEnv *env, jobject thiz,
                                                             jbyteArray data_, jint len,
                                                             jlong tms, jint type) {
    // 确保不会取出空的 RTMP 数据包
    if (!data_) {
        return 0;
    }
    int ret;
    int8_t *data = env->GetByteArrayElements(data_, NULL);

    if (type == 1) {//视频
        LOGI("开始发送视频 %d", len);
        ret = sendVideo(data, len, tms);
    } else {//音频
        LOGI("开始发送音频 %d", len);
        ret = sendAudio(data, len, tms, type);
    }

    env->ReleaseByteArrayElements(data_, data, 0);
    return ret;
}

/**
*发送音频
*/
int sendAudio(int8_t *buf, int len, long tms, int type) {
    RTMPPacket *packet = createAudioPacket(buf, len, tms, type, live);
    int ret = sendPacket(packet);
    return ret;
}

//创建audio包
RTMPPacket *createAudioPacket(int8_t *buf, int len, long tms, int type, Live *live) {
    int body_size = len + 2;
    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, body_size);
    packet->m_body[0] = 0xAF;
    if (type == 2) {//音频头
        packet->m_body[1] = 0x00;
    } else {//正常数据
        packet->m_body[1] = 0x01;
    }
    memcpy(&packet->m_body[2], buf, len);
    //设置音频类型
    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_nBodySize = body_size;
    //通道值，音视频不能相同
    packet->m_nChannel = 0x05;
    packet->m_nTimeStamp = tms;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = live->rtmp->m_stream_id;
    return packet;
}

/**
 *发送帧数据到rtmp服务器
 *把pps和sps缓存下来，放在每一个I帧前面，
 */
int sendVideo(int8_t *buf, int len, long tms) {
    int ret = 0;
    //当前时sps,缓存sps、pps 到全局变量
    if (buf[4] == 0x67) {
        //判断live是否缓存过，liv缓存过不再进行缓存
        if (live && (!live->sps || !live->pps)) {
            saveSPSPPS(buf, len, live);
        }
        return ret;
    }

    //I帧，关键帧,非关键帧直接推送帧数据
    if (buf[4] == 0x65) {
        LOGI("找到关键帧,先发送sps数据 %d", len);
        //先推送sps、pps
        RTMPPacket *SPpacket = createSPSPPSPacket(live);
        sendPacket(SPpacket);
    }
    //推送帧数据
    RTMPPacket *packet = createVideoPacket(buf, len, tms, live);
    ret = sendPacket(packet);
    return ret;
}

//将rtmp包发送出去
int sendPacket(RTMPPacket *packet) {
    int r = RTMP_SendPacket(live->rtmp, packet, 0);
    RTMPPacket_Free(packet);
    free(packet);
    LOGI("发送packet: %d ", r);
    return r;
}


/**
 * 创建帧内数据的RTMPPacket,I帧和非关键帧都在此合成RTMPPacket
 */
RTMPPacket *createVideoPacket(int8_t *buf, int len, long tms, Live *live) {
    buf += 4;
    len -= 4;
    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    int body_size = len + 9;
    LOGI("创建帧数据 Packet ,body_size:%d", body_size);
    //初始化内部body数组
    RTMPPacket_Alloc(packet, body_size);

    packet->m_body[0] = 0x27;//非关键帧
    if (buf[0] == 0x65) {//关键帧
        packet->m_body[0] = 0x17;
    }
    //固定
    packet->m_body[1] = 0x01;
    packet->m_body[2] = 0x00;
    packet->m_body[3] = 0x00;
    packet->m_body[4] = 0x00;
    //帧长度，4个字节存储
    packet->m_body[5] = (len >> 24) & 0xFF;
    packet->m_body[6] = (len >> 16) & 0xFF;
    packet->m_body[7] = (len >> 8) & 0xFF;
    packet->m_body[8] = (len) & 0xFF;
    //copy帧数据
    memcpy(&packet->m_body[9], buf, len);

    //设置视频类型
    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = body_size;
    //通道值，音视频不能相同
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = tms;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = live->rtmp->m_stream_id;
    return packet;
}

/**
 * 创建sps和pps的RTMPPacket
 */
RTMPPacket *createSPSPPSPacket(Live *live) {
    //sps、pps的packet
    int body_size = 16 + live->sps_len + live->pps_len;
    LOGI("创建sps Packet ,body_size:%d", body_size);
    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
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
    packet->m_body[i++] = live->sps[1];
    packet->m_body[i++] = live->sps[2];
    packet->m_body[i++] = live->sps[3];
    //固定
    packet->m_body[i++] = 0xFF;
    packet->m_body[i++] = 0xE1;
    //两个字节存储sps长度
    packet->m_body[i++] = (live->sps_len >> 8) & 0xFF;
    packet->m_body[i++] = (live->sps_len) & 0xFF;
    //sps内容写入
    memcpy(&packet->m_body[i], live->sps, live->sps_len);
    i += live->sps_len;

    //pps开始写入
    packet->m_body[i++] = 0x01;
    //pps长度
    packet->m_body[i++] = (live->pps_len >> 8) & 0xFF;
    packet->m_body[i++] = (live->pps_len) & 0xFF;
    memcpy(&packet->m_body[i], live->pps, live->pps_len);

    //设置视频类型
    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = body_size;
    //通道值，音视频不能相同
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = live->rtmp->m_stream_id;
    return packet;
}

/**
 * 缓存sps和pps到结构体Live中
 */
void saveSPSPPS(int8_t *buf, int len, Live *live) {
    for (int i = 0; i < len; ++i) {
        if (i + 4 < len) {
            //找到分隔符
            if (buf[i] == 0x00 && buf[i + 1] == 0x00 && buf[i + 2] == 0x00 && buf[i + 3] == 0x01) {
                //找到pps标识
                if (buf[i + 4] == 0x68) {
                    //存储sps
                    live->sps_len = i - 4;//减去sps前面得0001分隔符
                    //构建数组
                    live->sps = static_cast<int8_t *>(malloc(live->sps_len));
                    //从分隔符后开始到pps分隔符前复制到sps里面
                    memcpy(live->sps, buf + 4, live->sps_len);

                    //存储pps
                    live->pps_len = len - i - 4;
                    live->pps = static_cast<int8_t *>(malloc(live->pps_len));
                    memcpy(live->pps, buf + live->sps_len + 8, live->pps_len);
                    LOGI("sps 长度：%d , pps长度：%d", live->sps_len, live->pps_len);
                    break;
                }
            }
        }
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wish_videopath_demo8_mediacodec_ScreenLive_connect(JNIEnv *env, jobject thiz,
                                                            jstring url_) {
    //链接rtmp服务器
    int ret = 0;
    const char *url = env->GetStringUTFChars(url_, 0);
    //不断重试，链接服务器
    do {
        //初始化live数据
        live = (Live *) malloc(sizeof(Live));
        //清空live数据
        memset(live, 0, sizeof(Live));
        //初始化RTMP,申请内存
        live->rtmp = RTMP_Alloc();
        RTMP_Init(live->rtmp);
        //设置超时时间
        live->rtmp->Link.timeout = 10;
        LOGI("connect %s", url);
        //设置地址
        if (!(ret = RTMP_SetupURL(live->rtmp, (char *) url))) break;
        //设置输出模式
        RTMP_EnableWrite(live->rtmp);
        LOGI("connect Connect");
        //连接
        if (!(ret = RTMP_Connect(live->rtmp, 0))) break;
        LOGI("connect ConnectStream");
        //连接流
        if (!(ret = RTMP_ConnectStream(live->rtmp, 0))) break;
        LOGI("connect 成功");
    } while (0);

    //连接失败，释放内存
    if (!ret && live) {
        free(live);
        live = nullptr;
    }

    env->ReleaseStringUTFChars(url_, url);
    return ret;
}