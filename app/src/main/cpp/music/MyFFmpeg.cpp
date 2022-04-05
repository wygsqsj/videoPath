//
// ffmpeg解码类
//

#include <syslog.h>
#include "MyFFmpeg.h"


MyFFmpeg::MyFFmpeg(AudioPlayStatus *playStatus, CallJavaHelper *callJava, const char *audioUrl) :
        playStatus(playStatus), callJava(callJava), audioUrl(audioUrl) {
    pthread_mutex_init(&seek_mutex, NULL);
}

MyFFmpeg::~MyFFmpeg() {
    pthread_mutex_destroy(&seek_mutex);
};

//当前运行在子线程,再通过data转移到c++环境下运行
void *decodeFFmpeg(void *data) {
    MyFFmpeg *ffmpeg = (MyFFmpeg *) data;
    ffmpeg->decodeAudioThread();
    //释放线程
    pthread_exit(&ffmpeg->decodeThread);
}

//初始化ffmpeg
void MyFFmpeg::prepare() {
    //开启子线程初始化ffmpeg
    pthread_create(&decodeThread, NULL, decodeFFmpeg, this);
}

//实际初始化ffmpeg解码的函数，此时运行在子线程，又能获取到当前的成员变量
void MyFFmpeg::decodeAudioThread() {
    //注册组件
    av_register_all();
    //初始化网络流,即可以播放网络地址得音视频
    avformat_network_init();
    //初始化上下文
    avFormatContext = avformat_alloc_context();
    //打开视频文件或者流
    if (avformat_open_input(&avFormatContext, audioUrl, NULL, NULL) != 0) {
        LOGI("打开文件失败");
        return;
    }
    //查找文件流信息
    if (avformat_find_stream_info(avFormatContext, NULL) < 0) {
        LOGI("打开文件流失败");
        return;
    }
    //解封装
    int audioIndex = -1;
    for (int i = 0; i < avFormatContext->nb_streams; i++) {
        if (avFormatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            audioIndex = i;
            break;
        }
    }

    if (audioIndex == -1) {
        LOGI("没有找到音频流轨道");
        return;
    }
    LOGI("找到音频流轨道%d", audioIndex);
    //初始化播放器
    if (audio == NULL) {
        audio = new OpenslPlay(playStatus, avFormatContext->streams[audioIndex]->codecpar);
        audio->setCallJava(callJava);
        audio->streamIndex = audioIndex;
    }

    //将解码上下文传递给播放器
    audio->avCodecContext = avFormatContext->streams[audioIndex]->codec;

    //时间单位
    audio->time_base = avFormatContext->streams[audioIndex]->time_base;

    //获取当前音乐的总时长
    audio->audioDuration = avFormatContext->duration / AV_TIME_BASE;

    audioDuration = audio->audioDuration;


    //实例化音频解码器
    AVCodec *audioCodec = avcodec_find_decoder(audio->avCodecContext->codec_id);
    if (!audioCodec) {
        LOGI("实例化音频解码器失败");
        return;
    }

    if (avcodec_parameters_to_context(audio->avCodecContext, audio->codecpar)) {//将解码器中信息复制到上下文当中
        LOGI("avcodec_parameters_to_context ERROR");
        return;
    }

    if (avcodec_open2(audio->avCodecContext, audioCodec, NULL) < 0) {
        LOGI("没有打开音频解码器");
        return;
    }

    callJava->onCallReady(CHILD_THREAD);
}

//从音频文件中读取数据
void MyFFmpeg::start() {

    if (audio == NULL) {
        LOGI("audio播放器未准备好！停止播放");
        return;
    }

    audio->play();

    while (playStatus != nullptr && !playStatus->exit) {
        pthread_mutex_lock(&seek_mutex);
        //当前在seek时
        if (playStatus->isSeek) {
            continue;
        }

        //队列大小大于100不放入新的数据了
        if (audio->queue->getQueueSize() > 100) {
            pthread_mutex_unlock(&seek_mutex);
            continue;
        }
        LOGI("当前frameCount:%d", frameCount);

        AVPacket *avPacket = av_packet_alloc();
        int ret = av_read_frame(avFormatContext, avPacket);
        if (ret >= 0) {
            //当前是音频轨道
            if (avPacket->stream_index == audio->streamIndex) {
                frameCount++;
                LOGI("解码第%d帧", frameCount);
                //扔到队列中去
                audio->queue->putAvPacket(avPacket);
            } else {
                LOGI("非当前音频轨道");
                //释放掉申请的avpacke
                av_packet_free(&avPacket);
                av_free(avPacket);
                avPacket = NULL;
            }
        } else {//读取完成
            LOGI("读取avpacket异常:%d", ret);
            //释放掉申请的avpacke
            av_packet_free(&avPacket);
            av_free(avPacket);
            avPacket = nullptr;
            //解码完成，但是不一定播放完成
            while (playStatus != NULL && !playStatus->exit) {
                if (audio->queue->getQueueSize() > 0) {
                    continue;
                } else {
                    playStatus->exit = true;
                    break;
                }
            }
        }
        pthread_mutex_unlock(&seek_mutex);
    }

    LOGI("已解码完成");

}

//跳转到规定位置播放
void MyFFmpeg::seekTo(int64_t secds) {
    if (audioDuration <= 0 || secds < 0 || audio == nullptr) {
        LOGI("当前已播放完成，别seek了");
        return;
    }
    LOGI("当前要seek的位置：%d", secds);

    pthread_mutex_lock(&seek_mutex);

    //清空之前解析得数据
    playStatus->isSeek = true;
    audio->queue->clearQueue();
    audio->clock = 0;
    audio->last_call_java_time = 0;

    int64_t rel = secds * AV_TIME_BASE;

    int  ret =  avformat_seek_file(avFormatContext,//上下文
                       -1,//轨道，-1代表所有轨道都seek
                       INT64_MIN,
                       rel,//微秒
                       INT64_MAX,
                       0);

    LOGI("当前seek是否成功：%d", ret);

    avcodec_flush_buffers(audio->avCodecContext);

    playStatus->isSeek = false;

    frameCount = 0;

    pthread_mutex_unlock(&seek_mutex);
    LOGI("seek完成");
}

//暂停录音
void MyFFmpeg::pause() {
    if (audio != nullptr) {
        audio->pause();
    }
}

//播放录音
void MyFFmpeg::resume() {
    if (audio != nullptr) {
        audio->resume();
    }
}

//设置声道  0 左声道 1右声道 2 立体声
void MyFFmpeg::setMute(int channel) {
    if (audio != nullptr) {
        audio->setMute(channel);
    }
}

//设置声道  0 左声道 1右声道 2 立体声
void MyFFmpeg::setVolume(int volume) {
    if (audio != nullptr) {
        audio->setVolume(volume);
    }
}

