package com.wish.videopath.demo5;


import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * 将decode出来的pcm数据重新编码成aac格式,不通过FileInputStream写，通过MediaMuxer写出，不需要自己添加ADTS
 * mime：用来表示媒体文件的格式 mp3为audio/mpeg；aac为audio/mp4a-latm；mp4为video/mp4v-es
 */
public class EncodeAACThreadV2 extends Thread {

    private Context context;
    private File pcmFile, newAACFile;
    private boolean hasAudio = true;
    private MediaMuxer audioMuxer = null;
    private FileInputStream fis = null;
    private MediaCodec encodeCodec;


    public EncodeAACThreadV2(Demo5Activity demo5Activity) {
        context = demo5Activity;
        pcmFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "demo5.pcm");
        newAACFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "newAAC2.aac");
        if (!pcmFile.exists()) {
            Toast.makeText(context, "请先解码数据再重新编码！", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            newAACFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        super.run();
        try {
            //pcm文件获取
            fis = new FileInputStream(pcmFile);
            audioMuxer = new MediaMuxer(newAACFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            /*
             *手动构建编码Format,参数含义：mine类型、采样率、通道数量
             *设置音频采样率，44100是目前的标准，但是某些设备仍然支持22050，16000，11025
             */
            MediaFormat encodeFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2);
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            //比特率 声音中的比特率是指将模拟声音信号转换成数字声音信号后，单位时间内的二进制数据量，是间接衡量音频质量的一个指标
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, AudioFormat.ENCODING_PCM_16BIT);
            //最大的缓冲区大小，如果inputBuffer大小小于我们定义的缓冲区大小，可能报出缓冲区溢出异常
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 8 * 8);

            //构建编码器
            encodeCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
            //数据格式，surface用来渲染解析出来的数据;加密用的对象；标志 encode ：1 decode：0
            encodeCodec.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            MediaCodec.BufferInfo encodeBufferInfo = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            //启动编码
            encodeCodec.start();

            //启动音频Muxer输出
            int audioTrackIndex = 0;
            /*
             *此处由于是自定义的MediaFormat，在我的小米mix2s上，调用audioMuxer会报错，网上说因为当前的mediaFormat缺少CSD标志,手动设置的
             * csd标志是错误的
             *所以我监听了INFO_OUTPUT_FORMAT_CHANGED 事件在更改了MediaFormat之后再设置format
             */
          /*  ByteBuffer byteBuffer = ByteBuffer.allocate(3);
            encodeFormat.setByteBuffer("csd-0", byteBuffer);
            Log.i(LOG_TAG, "当前输出的format：" + encodeFormat);
            audioTrackIndex = audioMuxer.addTrack(encodeFormat);
            audioMuxer.start();//开始合成audio*/
            /*
             * 同步方式，流程是在while中
             * dequeueInputBuffer -> queueInputBuffer填充数据 -> dequeueOutputBuffer -> releaseOutputBuffer显示画面
             */
            boolean hasAudio = true;
            byte[] pcmData = new byte[1024 * 8 * 8];
            while (true) {
                //所有的猪都运进猪厂后不再添加
                if (hasAudio) {
                    //从猪肉工厂获取装猪的小推车，填充数据后发送到猪肉工厂进行处理
                    ByteBuffer[] inputBuffers = encodeCodec.getInputBuffers();//所有的小推车
                    int inputIndex = encodeCodec.dequeueInputBuffer(0);//返回当前可用的小推车标号
                    if (inputIndex != -1) {
                        Log.i(LOG_TAG, "找到了input 小推车" + inputIndex);
                        //将MediaCodec数据取出来放到这个缓冲区里
                        ByteBuffer inputBuffer = inputBuffers[inputIndex];//拿到小推车
                        inputBuffer.clear();//扔出去里面旧的东西
                        //将pcm数据装载到小推车里面
                        int size = fis.read(pcmData);
                        //audioExtractor没猪了，也要告知一下
                        if (size < 0) {
                            Log.i(LOG_TAG, "当前pcm已经读取完了");
                            encodeCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            hasAudio = false;
                        } else {//拿到猪
                            inputBuffer.limit(size);
                            inputBuffer.put(pcmData, 0, size);
                            Log.i(LOG_TAG, "读取到了音频数据，当前音频数据的数据长度为：" + size);
                         /*   int pts = inc++ * presentationTimeUs;
                            Log.i(LOG_TAG, "当前时间戳计算为：" + pts);*/
                            //告诉工厂这头猪的小推车序号、猪的大小、猪在这群猪里的排行、屠宰的标志
                            encodeCodec.queueInputBuffer(inputIndex, 0, size, 0, 0);
                        }
                    } else {
                        Log.i(LOG_TAG, "没有可用的input 小推车");
                    }
                }

                //工厂已经把猪运进去了，但是是否加工成火腿肠还是未知的，我们要通过装火腿肠的筐来判断是否已经加工完了
                int outputIndex = encodeCodec.dequeueOutputBuffer(encodeBufferInfo, 0);//返回当前筐的标记
                switch (outputIndex) {
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.i(LOG_TAG, "输出的format已更改" + encodeCodec.getOutputFormat());
                        audioTrackIndex = audioMuxer.addTrack(encodeCodec.getOutputFormat());
                        audioMuxer.start();//开始合成audio
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.i(LOG_TAG, "超时，没获取到");
                        break;
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.i(LOG_TAG, "输出缓冲区已更改");
                        break;
                    default:
                        Log.i(LOG_TAG, "获取到编码后的数据了，当前解析后的数据长度为：" + encodeBufferInfo.size);
                        //获取所有的筐
                        ByteBuffer[] outputBuffers = encodeCodec.getOutputBuffers();
                        //拿到当前装满火腿肠的筐
                        ByteBuffer outputBuffer;
                        if (Build.VERSION.SDK_INT >= 21) {
                            outputBuffer = encodeCodec.getOutputBuffer(outputIndex);
                        } else {
                            outputBuffer = outputBuffers[outputIndex];
                        }
                        videoBufferInfo.size = encodeBufferInfo.size;
                        videoBufferInfo.presentationTimeUs = 0;
                        videoBufferInfo.offset = 0;
                        videoBufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
                        //通过MediaMuxer写入
                        audioMuxer.writeSampleData(audioTrackIndex, outputBuffer, videoBufferInfo);
                        //把筐放回工厂里面
                        encodeCodec.releaseOutputBuffer(outputIndex, false);
                        break;
                }
                if ((encodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.i(LOG_TAG, "表示当前编解码已经完事了");
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (encodeCodec != null) {
                encodeCodec.stop();
                encodeCodec.release();
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (audioMuxer != null) {
                audioMuxer.stop();
                audioMuxer.release();
            }
        }
    }

}
