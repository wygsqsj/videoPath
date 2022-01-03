package com.wish.videopath.demo8.mediacodec;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import com.wish.videopath.util.LiveTaskManager;

import java.nio.ByteBuffer;

import static com.wish.videopath.MainActivity.LOG_TAG;
import static com.wish.videopath.demo8.mediacodec.RTMPPacket.AUDIO_HEAD_TYPE;

/**
 * 采集音频数据推流到服务器
 */
public class AudioCodec extends Thread {

    private MediaCodec mediaCodec;
    private AudioRecord audioRecord;
    private boolean isRecoding;
    /**
     * 采样率，现在能够保证在所有设备上使用的采样率是44100Hz, 但是其他的采样率（22050, 16000, 11025）在一些设备上也可以使用。
     */
    private final int SAMPLE_RATE_INHZ = 44100;

    /**
     * 声道数。CHANNEL_IN_MONO and CHANNEL_IN_STEREO. 其中CHANNEL_IN_MONO是可以保证在所有设备能够使用的。
     */
    private int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    /**
     * 返回的音频数据的格式。 ENCODING_PCM_8BIT, ENCODING_PCM_16BIT, and ENCODING_PCM_FLOAT.
     */
    private int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    //缓冲区
    private int minBufferSize =
            AudioRecord.getMinBufferSize(SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT);
    private long startTime;
    //传输层
    private ScreenLive screenLive;


    public void startLive(ScreenLive screenLive) {
        this.screenLive = screenLive;
        MediaFormat mediaFormat =
                MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE_INHZ, 1);

        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);

        //比特率 声音中的比特率是指将模拟声音信号转换成数字声音信号后，单位时间内的二进制数据量，是间接衡量音频质量的一个指标
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
        //最大的缓冲区大小，如果inputBuffer大小小于我们定义的缓冲区大小，可能报出缓冲区溢出异常
//        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 8 * 8);

        try {
            //构建编码器
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            //数据格式，surface用来渲染解析出来的数据;加密用的对象；标志 encode ：1 decode：0
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            //启动编码
            mediaCodec.start();
            //初始化录音器
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_INHZ,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    minBufferSize);
            LiveTaskManager.getInstance().execute(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        //发送音频头
        RTMPPacket rtmpPacket = new RTMPPacket();
        byte[] audioHeadInfo = {0x12, 0x08};
        rtmpPacket.setBuffer(audioHeadInfo);
        rtmpPacket.setType(AUDIO_HEAD_TYPE);
        screenLive.addPacket(rtmpPacket);

        //开始录音
        audioRecord.startRecording();
        isRecoding = true;
        MediaCodec.BufferInfo encodeBufferInfo = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
        byte[] buffer = new byte[minBufferSize];

        if (startTime == 0) {
            startTime = System.currentTimeMillis();//得到时间，毫秒
        }

        while (isRecoding) {
            int len = audioRecord.read(buffer, 0, minBufferSize);
            Log.i(LOG_TAG, "获取到录音数据" + len);
            if (len <= 0) {
                continue;
            }
            //从猪肉工厂获取装猪的小推车，填充数据后发送到猪肉工厂进行处理
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();//所有的小推车
            int inputIndex = mediaCodec.dequeueInputBuffer(0);//返回当前可用的小推车标号
            if (inputIndex != -1) {
                //将MediaCodec数据取出来放到这个缓冲区里
                ByteBuffer inputBuffer = inputBuffers[inputIndex];//拿到小推车
                inputBuffer.clear();//扔出去里面旧的东西
                //将pcm数据装载到小推车里面
                inputBuffer.limit(len);
                inputBuffer.put(buffer, 0, len);
                //告诉工厂这头猪的小推车序号、猪的大小、猪在这群猪里的排行、屠宰的标志
                mediaCodec.queueInputBuffer(inputIndex, 0, len, System.currentTimeMillis(), 0);
            }

            //工厂已经把猪运进去了，但是是否加工成火腿肠还是未知的，我们要通过装火腿肠的筐来判断是否已经加工完了
            int outputIndex = mediaCodec.dequeueOutputBuffer(encodeBufferInfo, 0);//返回当前筐的标记
            while (outputIndex >= 0 && isRecoding) {
                //获取所有的筐
                ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                //拿到当前装满火腿肠的筐
                ByteBuffer outputBuffer;
                if (Build.VERSION.SDK_INT >= 21) {
                    outputBuffer = mediaCodec.getOutputBuffer(outputIndex);
                } else {
                    outputBuffer = outputBuffers[outputIndex];
                }
                //将火腿肠放到新的容器里，便于后期装车运走
                byte[] newData = new byte[encodeBufferInfo.size];
                outputBuffer.get(newData);


                rtmpPacket = new RTMPPacket();
                rtmpPacket.setBuffer(newData);
                rtmpPacket.setType(RTMPPacket.AUDIO_TYPE);
                long tms = encodeBufferInfo.presentationTimeUs - startTime;
                Log.i(LOG_TAG, "音频 tms:" + tms);
                rtmpPacket.setTms(tms);
                screenLive.addPacket(rtmpPacket);

                //把筐放回工厂里面
                mediaCodec.releaseOutputBuffer(outputIndex, false);
                outputIndex = mediaCodec.dequeueOutputBuffer(encodeBufferInfo, 0);
            }
        }

        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        startTime = 0;
    }

    public void stopAudio() {
        isRecoding = false;
    }
}
