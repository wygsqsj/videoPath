#include <jni.h>
#include <string>
#include "RTMP_LOG.h"
#include <android/native_window_jni.h>

extern "C" {
//导入ffmpeg
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavutil/imgutils.h"
#include "libswscale/swscale.h"
#include "libavutil/time.h"
#include "libswresample/swresample.h"
}

static AVFormatContext *avFormatContext;
static AVCodecContext *avCodecContext, *audioCodecContext;
AVCodec *avCodec, *audioCodec;
ANativeWindow *nativeWindow;
ANativeWindow_Buffer windowBuffer;
static AVPacket *avPacket;
static AVFrame *avFrame, *rgbAvFrame;
static AVFrame *audioAVFrame;
uint8_t *outbuffer;
struct SwsContext *swsContext;

/**
 * 初始化ffmpeg
 */
extern "C"

JNIEXPORT jint
Java_com_wish_videopath_demo10_FFmpegActivity_play(JNIEnv *env, jobject thiz, jstring url_,
                                                   jobject surface_) {
    const char *url = env->GetStringUTFChars(url_, 0);
    //注册组件
    avcodec_register_all();
    //初始化网络流,即可以播放网络地址得音视频
    avformat_network_init();
    //初始化上下文
    avFormatContext = avformat_alloc_context();
    //打开视频文件或者流
    if (avformat_open_input(&avFormatContext, url, NULL, NULL) != 0) {
        LOGI("打开视频文件失败");
        return -1;
    }
    //查找文件流信息
    if (avformat_find_stream_info(avFormatContext, NULL) < 0) {
        LOGI("打开文件流失败");
        return -1;
    }

    //解封装
    //视频、音频轨道索引
    int videoIndex = -1, audioIndex = -1;
    for (int i = 0; i < avFormatContext->nb_streams; i++) {
        if (avFormatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            videoIndex = i;
        }
        if (avFormatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            audioIndex = i;
        }
    }

    //查找文件流信息
    if (videoIndex == -1) {
        LOGI("没有找到视频流轨道");
        return -1;
    }
    LOGI("找到视频流轨道%d", videoIndex);
    if (audioIndex == -1) {
        LOGI("没有找到音频流轨道");
        return -1;
    }
    LOGI("找到音频流轨道%d", audioIndex);
    //解析视频，获取解码器上下文
    avCodecContext = avFormatContext->streams[videoIndex]->codec;
    //解析音频，获取解码器上下文
    audioCodecContext = avFormatContext->streams[audioIndex]->codec;

    //实例化视频解码器
    avCodec = avcodec_find_decoder(avCodecContext->codec_id);
    //实例化音频解码器
    audioCodec = avcodec_find_decoder(audioCodecContext->codec_id);

    //打开视频解码器
    if (avcodec_open2(avCodecContext, avCodec, NULL) < 0) {
        LOGI("没有打开视频解码器");
        return -1;
    }
    LOGI("打开视频解码器");
    nativeWindow = ANativeWindow_fromSurface(env, surface_);
    if (0 == nativeWindow) {
        LOGI("没有获取到nativeWindow");
        return -1;
    }
    //打开音频解码器
    if (avcodec_open2(audioCodecContext, audioCodec, NULL) < 0) {
        LOGI("没有打开音频解码器");
        return -1;
    }
    LOGI("打开音频解码器");

    //初始化音频Frame
    audioAVFrame = av_frame_alloc();
    int out_channer_nb = av_get_channel_layout_nb_channels(AV_CH_LAYOUT_STEREO);
    //调用java层初始化AudioTrack
    jclass classPlay = env->GetObjectClass(thiz);
    jmethodID initAudioMethod = env->GetMethodID(classPlay, "initAudioTrack", "(II)V");
    env->CallVoidMethod(thiz, initAudioMethod, 44100, out_channer_nb);
    jmethodID playAudio = env->GetMethodID(classPlay, "playAudio", "([BI)V");
    //音频重采样转换上下文
    SwrContext *swrContext = swr_alloc();
    //将解析后得数据重采样成统一格式，与java层audioTrack相同配置
    uint64_t out_ch_layout = AV_CH_LAYOUT_STEREO;
    enum AVSampleFormat out_format = AV_SAMPLE_FMT_S16;
    int out_sample_rate = audioCodecContext->sample_rate;
    swr_alloc_set_opts(
            swrContext,//转换器上下文
            out_ch_layout,//输出得layout,例如5声道
            out_format,//输出得样本格式，例如S16,S24
            out_sample_rate,//输出的采样率

            audioCodecContext->channel_layout,//输入的layout
            audioCodecContext->sample_fmt,//输入的样本格式
            audioCodecContext->sample_rate,//输入的采样率
            0,//日志，可直接传0
            NULL
    );
    //初始话转换上下文
    swr_init(swrContext);
    //音频转换缓冲区,即1秒钟解析的pcm数据
    uint8_t *audioOutBuffer = (uint8_t *) av_malloc(44100 * 2);


    //初始化视频Frame
    //使用三个容器 AvPacket 放h264压缩数据 AvFrame 解码后得视频数据,通过转换存档新的AvFrame中; AvFrame 校正宽高后的视频容器，用于渲染到surface
    avPacket = static_cast<AVPacket *>(malloc(sizeof(AVPacket)));
    //由ffmepg确定了大小
    avFrame = av_frame_alloc();
    //我们自己计算他的大小，根据avFrame和surface来确定
    rgbAvFrame = av_frame_alloc();
    //根据原视频信息获取大小
    int width = avCodecContext->width;
    int height = avCodecContext->height;
    int numBytes = av_image_get_buffer_size(AV_PIX_FMT_RGBA, width, height, 1);
    //实例化缓冲区，旧的AvFrame转换成新AvFrame的缓冲
    outbuffer = (uint8_t *) av_malloc(numBytes * sizeof(uint8_t));
    /*
     * rgbAvFrame->data rgb三个通道的地址
     * rgbAvFrame->linesize 步长，对齐后的宽度
     * outbuffer 缓冲区地址
     * AV_PIX_FMT_RGBA 格式为rgb
     * align 对齐字节数按1个字节对齐
     */
    av_image_fill_arrays(rgbAvFrame->data, rgbAvFrame->linesize, outbuffer, AV_PIX_FMT_RGBA, width,
                         height, 1);
    //新的AvFrame需要一个转换器上下文
    swsContext = sws_getContext(width, height, avCodecContext->pix_fmt, width, height,
                                AV_PIX_FMT_RGBA, SWS_BICUBIC, NULL, NULL, NULL);

    if (ANativeWindow_setBuffersGeometry(nativeWindow, width, height,
                                         WINDOW_FORMAT_RGBA_8888) < 0) {
        LOGI("AvFrame转换器设置失败");
        return -1;
    }
    LOGI("AvFrame转换器设置成功");
    //获取h264数据渲染到surface
    while (true) {
        if (av_read_frame(avFormatContext, avPacket) >= 0) {
            if (avPacket->stream_index == videoIndex) {//视频数据
                //发给cpu进行解码
                int ret = avcodec_send_packet(avCodecContext, avPacket);
                if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                    LOGI("解码出错");
                    break;
                }
                //获取到解码后的视频数据到第一个AvFrame
                ret = avcodec_receive_frame(avCodecContext, avFrame);
                if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                    LOGI("读取视频频出错");
                    return -1;
                }
                //将AvFrame转换成surface可以直接使用的avFrame
                sws_scale(swsContext,//转换的上下文
                          avFrame->data,//待转换的数据，已解码的原始视频数据
                          avFrame->linesize,//读取的长度
                          0,//从哪读
                          avCodecContext->height,//读取的长度
                          rgbAvFrame->data,//新的Frame数据接收区
                          rgbAvFrame->linesize//长度
                );
                if (ANativeWindow_lock(nativeWindow, &windowBuffer, NULL) < 0) {
                    LOGI("surface加锁失败");
                } else {
                    //渲染到surface,将新旧avFrame转换
                    uint8_t *dst = (uint8_t *) windowBuffer.bits;
                    for (int h = 0; h < height; h++) {
                        memcpy(dst + h * windowBuffer.stride * 4,
                               outbuffer + h * rgbAvFrame->linesize[0],
                               rgbAvFrame->linesize[0]);
                    }
                }
                //暂停解析时间，防止播放过快
                av_usleep(1000 * 20);
                //释放锁
                ANativeWindow_unlockAndPost(nativeWindow);
            } else if (avPacket->stream_index == audioIndex) {
                //解析音频
                //发给cpu进行解码
                int ret = avcodec_send_packet(audioCodecContext, avPacket);
                if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                    LOGI("解码音频出错");
                    return -1;
                }
                //获取到解码后的音频数据到AvFrame
                ret = avcodec_receive_frame(audioCodecContext, audioAVFrame);
                if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                    LOGI("读取音频出错");
                    return -1;
                }
                //读取解析后得数据
                int size = av_samples_get_buffer_size(
                        NULL,
                        out_channer_nb,
                        audioAVFrame->nb_samples,
                        AV_SAMPLE_FMT_S16,
                        1);

                //重采样，进行转换
                swr_convert(swrContext, &audioOutBuffer, 44100 * 2,
                            (const uint8_t **) (audioAVFrame->data), audioAVFrame->nb_samples);

                //将转换后的数据放到jbyte中传给java
                if (size > 0) {
                    jbyteArray audioData = env->NewByteArray(size);
                    env->SetByteArrayRegion(audioData, 0, size,
                                            reinterpret_cast<const jbyte *>(audioOutBuffer));
                    //回调传给java层
                    env->CallVoidMethod(thiz, playAudio, audioData, size);
                }
            }
        } else {
            return -1;
        }
    }

    //释放上下文
    av_frame_free(&avFrame);
    av_free(avPacket);
    sws_freeContext(swsContext);
    avcodec_close(avCodecContext);
    avformat_close_input(&avFormatContext);
    avformat_free_context(avFormatContext);
    env->ReleaseStringUTFChars(url_, url);
    return -1;
}
