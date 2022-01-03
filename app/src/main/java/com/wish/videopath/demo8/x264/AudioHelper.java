package com.wish.videopath.demo8.x264;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.wish.videopath.util.LiveTaskManager;

import java.util.concurrent.ExecutorService;

/**
 * 采集音频数据推流到服务器
 */
public class AudioHelper extends Thread {

    private AudioRecord audioRecord;
    private boolean isRecoding;

    /**
     * 通道数
     */
    private final int channelCount = 2;
    /**
     * 采样率，现在能够保证在所有设备上使用的采样率是44100Hz, 但是其他的采样率（22050, 16000, 11025）在一些设备上也可以使用。
     */
    private final int SAMPLE_RATE_HZ = 44100;

    /**
     * 声道数。CHANNEL_IN_MONO and CHANNEL_IN_STEREO. 其中CHANNEL_IN_MONO是可以保证在所有设备能够使用的。
     * CHANNEL_IN_STEREO 双通道使用
     */
    private int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;

    /**
     * 返回的音频数据的格式。 ENCODING_PCM_8BIT, ENCODING_PCM_16BIT, and ENCODING_PCM_FLOAT.
     */
    private int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    /**
     * 单线程线程池, 在该线程中进行音频采样
     */
    private ExecutorService mExecutorService;


    //传输层
    private LivePush livePush;
    private byte[] buffer;

    public AudioHelper(LivePush livePush) {
        this.livePush = livePush;
        int inputByteNum = livePush.native_initAudioCodec(SAMPLE_RATE_HZ, channelCount);
        /*
         * 缓冲区，此处的最小buffer只能作为参考值，不同于MedeaCodec我们可以直接使用此缓冲区大小，当设备不支持硬编时
         * getMinBufferSize会返回-1，所以还要根据faac返回给我们的输入区大小来确定
         * faac会返回给我们一个缓冲区大小，将他和缓冲区大小比较之后采用最大值
         */
        int minBufferSize = Math.max(inputByteNum, AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT));

        //初始化录音数据缓冲区，要根据faac返回的采样数据大小构建，否则传输给faac编码的音频数据大小不一致时编码出来的数据会出现杂音
        buffer = new byte[minBufferSize];
        try {
            //初始化录音器，使用的是对比得到的数据大小
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_HZ,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    minBufferSize);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startAudio() {
        //开始录音
        audioRecord.startRecording();
        isRecoding = true;
        LiveTaskManager.getInstance().execute(this);
    }

    @Override
    public void run() {
        //不断的读取数据
        while (isRecoding) {
            int len = audioRecord.read(buffer, 0, buffer.length);
            if (livePush != null && len > 0) {
                livePush.native_pushAudio(buffer);
            }
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    public void stopAudio() {
        isRecoding = false;
    }
}
