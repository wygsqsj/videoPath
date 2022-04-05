//
//  opensl es音乐播放
//
#include "OpenslPlay.h"

OpenslPlay::OpenslPlay(AudioPlayStatus *playStatus, AVCodecParameters *pParameters)
        : playStatus(playStatus), codecpar(pParameters) {
    queue = new OpenslQueue(playStatus);
    //这个里面放的是音频重采样的数据，即每秒播放的pcm数据大小,采样率*2个字节（16位采样）*2个通道
    buffer = static_cast<uint8_t *>(av_malloc(codecpar->sample_rate * 2 * 2));
}

OpenslPlay::~OpenslPlay() {
};

//c环境运行在子线程
void *decodePlay(void *data) {
    OpenslPlay *play = (OpenslPlay *) data;
    play->initOpenSL();
    //终止线程
    pthread_exit(&play->playThread);
}

void OpenslPlay::play() {
    //耗时操作，放到子线程里面
    pthread_create(&playThread, NULL, decodePlay, this);
}

//运行在子线程，返回重采样的大小，用于求时间
int OpenslPlay::resampleAudio() {
    while (playStatus != nullptr && !playStatus->exit) {
        avPacket = av_packet_alloc();
        //队列中没有数据
        if (queue->getAVPacket(avPacket) != 0) {
            LOGI("队列中没有数据");
            av_packet_free(&avPacket);
            av_free(avPacket);
            avPacket = NULL;
            continue;
        }

        //交给cpu进行解码
        ret = avcodec_send_packet(avCodecContext, avPacket);
        if (ret != 0) {
            LOGI("解码音频出错");
            av_packet_free(&avPacket);
            av_free(avPacket);
            avPacket = nullptr;
            continue;
        }

        avFrame = av_frame_alloc();
        //获取到解码后的音频数据到AvFrame
        ret = avcodec_receive_frame(avCodecContext, avFrame);
        if (ret == 0) {//进行重采样
            //有声道数没有声道布局，所以要设置声道布局
            if (avFrame->channels > 0 && avFrame->channel_layout == 0) {
                avFrame->channel_layout = av_get_default_channel_layout(avFrame->channels);
            } else if (avFrame->channels == 0 && avFrame->channel_layout > 0) {//有声道布局没有声道数，所以要设置声道数
                avFrame->channels = av_get_channel_layout_nb_channels(avFrame->channel_layout);
            }

            //设置转换器上下文
            SwrContext *swr_context = NULL;
            swr_context = swr_alloc_set_opts(
                    swr_context,//转换器上下文
                    avFrame->channel_layout,//输出得layout,例如5声道
                    AV_SAMPLE_FMT_S16,//输出得样本格式，例如S16,S24
                    avFrame->sample_rate,//输出的采样率
                    avFrame->channel_layout,//输入声道布局
                    (AVSampleFormat) (avFrame->format),//输入采样位数格式
                    avFrame->sample_rate,//输入采样率
                    0,//日志，可直接传0
                    nullptr
            );

            //转换器上下文初始化失败
            if (!swr_context || swr_init(swr_context) < 0) {
                av_packet_free(&avPacket);
                av_free(avPacket);
                avPacket = nullptr;

                av_frame_free(&avFrame);
                av_free(avFrame);
                avFrame = nullptr;

                if (swr_context != nullptr) {
                    swr_free(&swr_context);
                    swr_context = nullptr;
                }
                LOGI("转换器上下文初始化失败");
                break;
            }

            //重采样，进行转换
            int nb = swr_convert(swr_context,
                                 &buffer,//重采样后输出的PCM数据大小
                                 avFrame->nb_samples,//输出采样个数
                                 (const uint8_t **) (avFrame->data),
                                 avFrame->nb_samples);

            int out_channels = av_get_channel_layout_nb_channels(AV_CH_LAYOUT_STEREO);
            data_size = nb * out_channels * av_get_bytes_per_sample(AV_SAMPLE_FMT_S16);
            LOGI("重采样后的数据大小 %d,nb:%d,out_channels:%d", data_size, nb, out_channels);

            //计算当前的时间戳,帧数 * 单位(就是总时间/多少帧)
            cur_time = avFrame->pts * av_q2d(time_base);
            //保证播放时间的准确
            if (clock > cur_time) {
                cur_time = clock;
            }
            clock = cur_time;


            //释放掉当前音频帧的数据
            av_packet_free(&avPacket);
            av_free(avPacket);
            avPacket = nullptr;

            av_frame_free(&avFrame);
            av_free(avFrame);
            avFrame = nullptr;

            swr_free(&swr_context);
            swr_context = nullptr;

            break;
        } else {
            av_packet_free(&avPacket);
            av_free(avPacket);
            avPacket = nullptr;

            av_frame_free(&avFrame);
            av_free(avFrame);
            avFrame = nullptr;
            break;
        }
    }

    return data_size;
}

//opensl从此处取数据，不断的被调用
void pcmBufferCallBack(SLAndroidSimpleBufferQueueItf bufferQueueItf, void *data) {
    LOGI("pcmBufferCallBack 执行");
    auto *play = (OpenslPlay *) data;
    if (play != nullptr && play->playStatus != nullptr && !play->playStatus->exit) {
        //从FFMpeg生产的AVPacket队列取出数据，进行解码，得到的是要播放的pcm数据大小
        int bufferSize = play->resampleAudio();

        if (bufferSize > 0) {
            //计算当前的播放时间，即pcm数据大小/播放这些pcm所需要的字节 = 播放的时间
            //play->clock是当前pcm解码出来的时间 + 播放所需要时间 = 当前的时间戳
            play->clock += bufferSize / (double) (play->codecpar->sample_rate * 2 * 2);

            //回调给java层，控制频率在1秒内回调一次
            if (play->clock - play->last_call_java_time > 1) {
                play->callJava->onPlayTimeCallBack(CHILD_THREAD, play->clock, play->audioDuration);
                play->last_call_java_time = play->clock;
            }

            LOGI("OpenslPlay 获取");
            //将数据添加到opensl的队列中去进行播放
            (*play->pcmBufferQueue)->Enqueue(play->pcmBufferQueue,
                                             (char *) play->buffer,
                                             bufferSize);
        } else {
            LOGI("当前没有获取到转码后的pcm数据");
        }

    } else {
        LOGI("OpenslPlay 获取完成");
    }
}

//初始化openSl
void OpenslPlay::initOpenSL() {
    struct timeval t_start, t_end;
    gettimeofday(&t_start, nullptr);
    LOGI("Start time: %ld us", t_start.tv_usec);
    /***********  1 创建引擎 获取SLEngineItf***************/
    //创建引擎
    SLresult result;
    result = slCreateEngine(&engineObject, 0, 0, 0, 0, 0);
    if (result != SL_RESULT_SUCCESS) {
        LOGI("创建引擎失败 slCreateEngine Start time: %ld us", t_start.tv_usec);
        return;
    }
    //初始化引擎内部变量，实现Realize接口对象
    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        LOGI("创建引擎失败 Realize Start time: %ld us", t_start.tv_usec);
        return;
    }
    //初始化engineEngine引擎接口，通过接口来操作引擎
    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
    if (result != SL_RESULT_SUCCESS) {
        LOGI("创建引擎失败 GetInterface Start time: %ld us", t_start.tv_usec);
        return;
    }

    if (engineEngine) {
        LOGI("创建引擎 get SLEngineItf success");
    } else {
        LOGI("创建引擎 get SLEngineItf failed");
    }

    /***********  2 创建混音器 ***************/
    //创建混音器，给哪个喇叭分配音频数据，通过接口创建
    const SLInterfaceID mids[1] = {SL_IID_ENVIRONMENTALREVERB};//环境混响音效
    const SLboolean mreq[1] = {SL_BOOLEAN_FALSE};//是否强制实现回响音效
    result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 1, mids, mreq);
    if (result != SL_RESULT_SUCCESS) {
        LOGI("创建引擎 CreateOutputMix failed");
        return;
    } else {
        LOGI("创建引擎 CreateOutputMix success");
    }

    //依旧是通过混音器接口来操作混音器
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        LOGI("创建引擎 mixer init failed");
    } else {
        LOGI("创建引擎 mixer init success");
    }

    result = (*outputMixObject)->GetInterface(outputMixObject, SL_IID_ENVIRONMENTALREVERB,
                                              &outputMixEnvironmentalReverb);

    if (SL_RESULT_SUCCESS == result) {
        result = (*outputMixEnvironmentalReverb)->SetEnvironmentalReverbProperties(
                outputMixEnvironmentalReverb, &reverbSettings
        );
        (void) result;
    }

    /***********  3 配置音频信息 ***************/
    //播放解码后的数据
    SLDataLocator_OutputMix outputMix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSnk = {&outputMix, 0};

    //缓冲队列
    SLDataLocator_AndroidSimpleBufferQueue android_queue = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
                                                            2};

    LOGI("创建引擎 当前sample_rate  %d", codecpar->sample_rate);
    //音频格式
    SLDataFormat_PCM pcm = {
            SL_DATAFORMAT_PCM,//PCM格式数据
            2,//双声道
            static_cast<SLuint32>(getCurrentSampleRateForOpensles(codecpar->sample_rate)),//44100频率
            SL_PCMSAMPLEFORMAT_FIXED_16,//16位
            SL_PCMSAMPLEFORMAT_FIXED_16,//16位
            SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, //立体声（前左前右）
            SL_BYTEORDER_LITTLEENDIAN//结束位
    };

    SLDataSource slDataSource = {&android_queue, &pcm};

    /************* 4 创建播放器 ****************/
    //创建播放器
    /* const SLInterfaceID ids[1] = {SL_IID_BUFFERQUEUE};
     const SLboolean req[1] = {SL_BOOLEAN_TRUE};*/
    const SLInterfaceID ids[] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME, SL_IID_MUTESOLO};
    const SLboolean req[] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};

    result = (*engineEngine)->CreateAudioPlayer(engineEngine, &pcmPlayerObject, &slDataSource,
                                                &audioSnk, sizeof(ids) / sizeof(SLInterfaceID),
                                                ids, req);
//    (*engineEngine)->CreateAudioPlayer(engineEngine, &pcmPlayerObject, &slDataSource, &audioSnk, 1, ids, req);
    if (result != SL_RESULT_SUCCESS) {
        LOGI("创建opensl播放器失败");
    } else {
        LOGI("创建opensl播放器成功");
    }

    //初始化播放器
    result = (*pcmPlayerObject)->Realize(pcmPlayerObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        LOGI("创建引擎 audio player init failed");
    } else {
        LOGI("创建引擎 audio player init success");
    }

    //获取播放器接口，通过接口控制暂停、播放
    result = (*pcmPlayerObject)->GetInterface(pcmPlayerObject, SL_IID_PLAY, &pcmPlayerPlay);
    if (result != SL_RESULT_SUCCESS) {
        LOGI("opensl播放器 get SL_IID_PLAY failed");
    } else {
        LOGI("opensl播放器 get SL_IID_PLAY success");
    }
    //用于操作声道
    (*pcmPlayerObject)->GetInterface(pcmPlayerObject, SL_IID_MUTESOLO, &pcmMutePlay);

    //操作音量
    (*pcmPlayerObject)->GetInterface(pcmPlayerObject, SL_IID_VOLUME, &pcmVolumePlay);

    //获取播放队列接口
    result = (*pcmPlayerObject)->GetInterface(pcmPlayerObject, SL_IID_BUFFERQUEUE, &pcmBufferQueue);

    if (result != SL_RESULT_SUCCESS) {
        LOGI("opensl播放器 get SL_IID_BUFFERQUEUE failed");
    } else {
        LOGI("opensl播放器 get SL_IID_BUFFERQUEUE success");
    }

    //设置回调函数
    (*pcmBufferQueue)->RegisterCallback(pcmBufferQueue, pcmBufferCallBack, this);
    //设置播放状态
    (*pcmPlayerPlay)->SetPlayState(pcmPlayerPlay, SL_PLAYSTATE_PLAYING);
    // 启动播放，不断的回调函数pcmBufferCallBack
    pcmBufferCallBack(pcmBufferQueue, this);

    gettimeofday(&t_end, NULL);
    LOGI("End time: %ld us", t_end.tv_usec);

    long cost_time = t_end.tv_usec - t_start.tv_usec;
    LOGI("opensled create cost:%ld ms", cost_time / 1000);

}

//根据频率返回对应的数据源
int OpenslPlay::getCurrentSampleRateForOpensles(int sample_rate) {
    int rate = 0;
    switch (sample_rate) {
        case 8000:
            rate = SL_SAMPLINGRATE_8;
            break;
        case 11025:
            rate = SL_SAMPLINGRATE_11_025;
            break;
        case 12000:
            rate = SL_SAMPLINGRATE_12;
            break;
        case 16000:
            rate = SL_SAMPLINGRATE_16;
            break;
        case 22050:
            rate = SL_SAMPLINGRATE_22_05;
            break;
        case 24000:
            rate = SL_SAMPLINGRATE_24;
            break;
        case 32000:
            rate = SL_SAMPLINGRATE_32;
            break;
        case 44100:
            rate = SL_SAMPLINGRATE_44_1;
            break;
        case 48000:
            rate = SL_SAMPLINGRATE_48;
            break;
        case 64000:
            rate = SL_SAMPLINGRATE_64;
            break;
        case 88200:
            rate = SL_SAMPLINGRATE_88_2;
            break;
        case 96000:
            rate = SL_SAMPLINGRATE_96;
            break;
        case 192000:
            rate = SL_SAMPLINGRATE_192;
            break;
        default:
            rate = SL_SAMPLINGRATE_44_1;
    }
    return rate;
}

void OpenslPlay::setCallJava(CallJavaHelper *callJava) {
    this->callJava = callJava;
}

//暂停录音
void OpenslPlay::pause() {
    if (pcmPlayerPlay != nullptr) {
        (*pcmPlayerPlay)->SetPlayState(pcmPlayerPlay, SL_PLAYSTATE_PAUSED);
    }
}

//播放录音
void OpenslPlay::resume() {
    if (pcmPlayerPlay != nullptr) {
        (*pcmPlayerPlay)->SetPlayState(pcmPlayerPlay, SL_PLAYSTATE_PLAYING);
    }
}

//设置声道  0 左声道 1右声道 2 立体声
void OpenslPlay::setMute(int channel) {
    if (pcmMutePlay == nullptr) {
        LOGI("声道操作接口未初始化");
        return;
    }

    switch (channel) {
        case 0:
            (*pcmMutePlay)->SetChannelMute(pcmMutePlay, 0, true);//true 代表关闭
            (*pcmMutePlay)->SetChannelMute(pcmMutePlay, 1, false);//false 代表开启
            break;
        case 1:
            (*pcmMutePlay)->SetChannelMute(pcmMutePlay, 0, false);
            (*pcmMutePlay)->SetChannelMute(pcmMutePlay, 1, true);
            break;
        case 2:
            (*pcmMutePlay)->SetChannelMute(pcmMutePlay, 0, false);
            (*pcmMutePlay)->SetChannelMute(pcmMutePlay, 1, false);
            break;
    }

}

//设置声音大小
void OpenslPlay::setVolume(int percent) {
    if (pcmVolumePlay == nullptr) {
        LOGI("声音操作接口未初始化");
        return;
    }
    if (pcmVolumePlay != nullptr) {
        if (percent > 30) {
            (*pcmVolumePlay)->SetVolumeLevel(pcmVolumePlay, (100 - percent) * -20);
        } else if (percent > 25) {
            (*pcmVolumePlay)->SetVolumeLevel(pcmVolumePlay, (100 - percent) * -22);
        } else if (percent > 20) {
            (*pcmVolumePlay)->SetVolumeLevel(pcmVolumePlay, (100 - percent) * -25);
        } else if (percent > 15) {
            (*pcmVolumePlay)->SetVolumeLevel(pcmVolumePlay, (100 - percent) * -28);
        } else if (percent > 10) {
            (*pcmVolumePlay)->SetVolumeLevel(pcmVolumePlay, (100 - percent) * -30);
        } else if (percent > 5) {
            (*pcmVolumePlay)->SetVolumeLevel(pcmVolumePlay, (100 - percent) * -34);
        } else if (percent > 3) {
            (*pcmVolumePlay)->SetVolumeLevel(pcmVolumePlay, (100 - percent) * -37);
        } else if (percent > 0) {
            (*pcmVolumePlay)->SetVolumeLevel(pcmVolumePlay, (100 - percent) * -40);
        } else {
            (*pcmVolumePlay)->SetVolumeLevel(pcmVolumePlay, (100 - percent) * -100);
        }
    }

}

