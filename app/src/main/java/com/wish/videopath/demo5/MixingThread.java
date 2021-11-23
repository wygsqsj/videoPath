package com.wish.videopath.demo5;

import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.wish.videopath.R;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import androidx.annotation.RequiresApi;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * 将raw下的两个音频混音，并控制音量大小，输出到sd卡项目的中music里面
 * <p>
 * Muxer获取音频轨道，得到format，取出数据
 * 构建MediaCodec解码音频成pcm数据
 * 将两个pcm数据相加混合成新的数据
 * 构建MediaCodec编码器将pcm编码成新的音频
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class MixingThread extends Thread {
    private final Demo5Activity context;

    private int volume1 = 20;//音频1设置音量大小
    private int volume2 = 100;//音频2设置音量大小
    private AssetFileDescriptor inputAudio1;
    private AssetFileDescriptor inputAudio2;
    private File outFile;

    private byte[] audioData1;
    private byte[] audioData2;
    private byte[] audioData3;

    //两个解码器一个编码器
    private MediaCodec audioDecode1;
    private MediaCodec audioDecode2;
    private MediaCodec encodeCodec;

    //两个分离器一个合成器
    private MediaExtractor audioExtractor1 = new MediaExtractor();
    private MediaExtractor audioExtractor2 = new MediaExtractor();
    private MediaMuxer audioMuxer;
    //两个输入音频format一个输出音频format
    private MediaFormat audioFormat1, audioFormat2;
    private int audioTrack1, audioTrack2;

    private MediaCodec.BufferInfo decodeInfo1 = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
    private MediaCodec.BufferInfo decodeInfo2 = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
    private MediaCodec.BufferInfo encodeBufferInfo = new MediaCodec.BufferInfo();//将混音后的数据编码
    private MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();//将混音后的数据编码

    //启动音频Muxer输出
    private int audioTrackIndex = 0;

    //audio 解码后的数据缓存
    private LinkedBlockingQueue<byte[]> audioQueue1 = new LinkedBlockingQueue<byte[]>(16);
    private LinkedBlockingQueue<byte[]> audioQueue2 = new LinkedBlockingQueue<byte[]>(16);
    private LinkedBlockingQueue<byte[]> audioQueue3 = new LinkedBlockingQueue<byte[]>(16);

    //混音需要用的临时数据
    private short temp1, temp2;
    private int temp3;

    boolean hasAudio1 = true;
    boolean hasAudio2 = true;
    boolean finishWriteInput = false;
    boolean finishWrite = false;


    public MixingThread(Demo5Activity context) {
        this.context = context;
        inputAudio1 = context.getResources().openRawResourceFd(R.raw.zlj);
        inputAudio2 = context.getResources().openRawResourceFd(R.raw.see);
        outFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "mixing.mp3");
    }

    @Override
    public void run() {
        super.run();
        try {
            //Muxer获取音频轨道，得到format，取出数据
            getTrack1();
            getTrack2();
            //构建MediaCodec解码音频成pcm数据
            initMediaCodec1();
            initMediaCodec2();
            //构建编码器
            initEncodeMediaCodec();
            //将两个pcm数据相加混合成新的数据
            //构建MediaCodec编码器将pcm编码成新的音频
            while (!finishWrite) {
                decodeAudio1();
                decodeAudio2();
                mixingAudio();
                writeNewAudio();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (audioExtractor1 != null) {
                audioExtractor1.release();
            }
            if (audioExtractor2 != null) {
                audioExtractor2.release();
            }
            if (audioMuxer != null) {
                audioMuxer.release();
            }


            if (audioDecode1 != null) {
                audioDecode1.stop();
                audioDecode1.release();
            }
            if (audioDecode2 != null) {
                audioDecode2.stop();
                audioDecode2.release();
            }
            if (encodeCodec != null) {
                encodeCodec.stop();
                encodeCodec.release();
            }
        }


    }

    private void initEncodeMediaCodec() throws IOException {
        //pcm文件获取
        audioMuxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
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

        //启动编码
        encodeCodec.start();
    }


    private void writeNewAudio() {
        if (!finishWriteInput) {
            //两个音频还未读取完成
            byte[] pcmData = audioQueue3.poll();
            if (pcmData == null || pcmData.length == 0) {
                Log.i(LOG_TAG, "未获取到音频数据，跳出");
                if (!hasAudio1 && !hasAudio2 && audioQueue3.size() == 0) {
                    //从猪肉工厂获取装猪的小推车，填充数据后发送到猪肉工厂进行处理
                    ByteBuffer[] inputBuffers = encodeCodec.getInputBuffers();//所有的小推车
                    int inputIndex = encodeCodec.dequeueInputBuffer(0);//返回当前可用的小推车标号
                    if (inputIndex != -1) {
                        finishWriteInput = true;
                        Log.i(LOG_TAG, "新的音频编码input已经读取完了");
                        encodeCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }
                return;
            }
            //从猪肉工厂获取装猪的小推车，填充数据后发送到猪肉工厂进行处理
            ByteBuffer[] inputBuffers = encodeCodec.getInputBuffers();//所有的小推车
            int inputIndex = encodeCodec.dequeueInputBuffer(0);//返回当前可用的小推车标号
            if (inputIndex != -1) {
                Log.i(LOG_TAG, "新的音频数据获取到缓冲区");
                //将MediaCodec数据取出来放到这个缓冲区里
                ByteBuffer inputBuffer = inputBuffers[inputIndex];//拿到小推车
                inputBuffer.clear();//扔出去里面旧的东西
                inputBuffer.limit(pcmData.length);
                inputBuffer.put(pcmData, 0, pcmData.length);
                encodeCodec.queueInputBuffer(inputIndex, 0, pcmData.length, 0, 0);
            }
        }

        //工厂已经把猪运进去了，但是是否加工成火腿肠还是未知的，我们要通过装火腿肠的筐来判断是否已经加工完了
        int outputIndex = encodeCodec.dequeueOutputBuffer(encodeBufferInfo, 0);//返回当前筐的标记
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            Log.i(LOG_TAG, "新的音频数据输出的format已更改" + encodeCodec.getOutputFormat());
            audioTrackIndex = audioMuxer.addTrack(encodeCodec.getOutputFormat());
            audioMuxer.start();//开始合成audio
        } else if (outputIndex > -1) {
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
        }
        if ((encodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.i(LOG_TAG, "新的音频数据编码已经完事了");
            finishWrite = true;
        }
    }


    private void mixingAudio() {
        Log.i(LOG_TAG, "当前audio1集合大小：" + audioQueue1.size());
        Log.i(LOG_TAG, "当前audio2集合大小：" + audioQueue2.size());

        byte[] mixAudio1 = audioQueue1.poll();
        byte[] mixAudio2 = audioQueue2.poll();
        if (mixAudio1 != null && mixAudio2 != null) {
            Log.i(LOG_TAG, "audio1混合数据大小：" + mixAudio1.length);
            Log.i(LOG_TAG, "audio2混合数据大小：" + mixAudio2.length);
            audioData3 = new byte[mixAudio1.length];
            //一个声音采样占2个字节，用short标识即可
            //将两个short字节相加，得到混合后的音频
            for (int i = 0; i < mixAudio1.length; i += 2) {
                //声音数据的排列顺序为低8位在前，高8位在后,此处还原为真实的数据，即低8位放到后面，高8位放到前面
                temp1 = (short) ((mixAudio1[i] & 0xff) | ((mixAudio1[i + 1] & 0xff) << 8));
                if (i + 1 < mixAudio2.length) {
                    temp2 = (short) ((mixAudio2[i] & 0xff) | ((mixAudio2[i + 1] & 0xff) << 8));
                    //声音大小的控制通过振幅来控制，用当前字节*对应的音量即可得到
                    temp1 = (short) (temp1 * volume1 / 100f);
                    temp2 = (short) (temp2 * volume2 / 100f);
                    temp3 = temp1 + temp2;
                    //超出的部分舍弃掉
                    if (temp3 > 32767) {
                        temp3 = 32767;
                    } else if (temp3 < -32768) {
                        temp3 = -32768;
                    }
                } else {
                    temp3 = temp1;
                }
                //存入新的数据中
                audioData3[i] = (byte) (temp3 & 0xff);
                audioData3[i + 1] = (byte) ((temp3 >> 8) & 0xff);
            }
            audioQueue3.add(audioData3);
        } else {
            if (hasAudio1 && !hasAudio2 && mixAudio1 != null) {
                Log.i(LOG_TAG, "当前音频1未写入：" + mixAudio1.length);
                audioQueue3.add(mixAudio1);
            }
            if (!hasAudio1 && hasAudio2 && mixAudio2 != null) {
                Log.i(LOG_TAG, "当前音频2未写入：" + mixAudio2.length);
                audioQueue3.add(mixAudio2);
            }
        }
    }

    private void decodeAudio1() {
        if (hasAudio1) {
            //从猪肉工厂获取装猪的小推车，填充数据后发送到猪肉工厂进行处理
            ByteBuffer[] inputBuffers = audioDecode1.getInputBuffers();//所有的小推车
            int inputIndex = audioDecode1.dequeueInputBuffer(0);//返回当前可用的小推车标号
            if (inputIndex != -1) {
                Log.i(LOG_TAG, "audio1获取到缓冲区" + inputIndex);
                //将MediaCodec数据取出来放到这个缓冲区里
                ByteBuffer inputBuffer = inputBuffers[inputIndex];//拿到小推车
                inputBuffer.clear();//扔出去里面旧的东西
                //将audioExtractor里面的猪装载到小推车里面
                int readSize = audioExtractor1.readSampleData(inputBuffer, 0);
                //audioExtractor没猪了，也要告知一下
                if (readSize < 0) {
                    Log.i(LOG_TAG, "audio1已经读取完了");
                    audioDecode1.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    hasAudio1 = false;
                } else {//拿到猪
                    Log.i(LOG_TAG, "audio1获取到的数据长度为：" + readSize);
                    //告诉工厂这头猪的小推车序号、猪的大小、猪在这群猪里的排行、屠宰的标志
                    audioDecode1.queueInputBuffer(inputIndex, 0, readSize, audioExtractor1.getSampleTime(), 0);
                    //读取音频的下一帧
                    audioExtractor1.advance();
                }
            }
        }
        int outputIndex = audioDecode1.dequeueOutputBuffer(decodeInfo1, 0);
        if (outputIndex > -1) {
            Log.i(LOG_TAG, "audio1获取到解码后的数据：" + decodeInfo1.size);
            //获取所有的筐
            ByteBuffer[] outputBuffers = audioDecode1.getOutputBuffers();
            //拿到当前装满火腿肠的筐
            ByteBuffer outputBuffer;
            if (Build.VERSION.SDK_INT >= 21) {
                outputBuffer = audioDecode1.getOutputBuffer(outputIndex);
            } else {
                outputBuffer = outputBuffers[outputIndex];
            }
            //将火腿肠放到新的容器里，便于后期装车运走
            audioData1 = new byte[decodeInfo1.size];
            outputBuffer.get(audioData1);//写入到字节数组中
            outputBuffer.clear();//清空当前筐
            //把筐放回工厂里面
            audioDecode1.releaseOutputBuffer(outputIndex, false);

            audioQueue1.add(audioData1);
        }
        if ((decodeInfo1.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.i(LOG_TAG, "audio1解码已经完事了");
            return;
        }
    }

    private void decodeAudio2() {
        if (hasAudio2) {
            //从猪肉工厂获取装猪的小推车，填充数据后发送到猪肉工厂进行处理
            ByteBuffer[] inputBuffers = audioDecode2.getInputBuffers();//所有的小推车
            int inputIndex = audioDecode2.dequeueInputBuffer(0);//返回当前可用的小推车标号
            if (inputIndex != -1) {
                Log.i(LOG_TAG, "audio2获取到缓冲区" + inputIndex);
                //将MediaCodec数据取出来放到这个缓冲区里
                ByteBuffer inputBuffer = inputBuffers[inputIndex];//拿到小推车
                inputBuffer.clear();//扔出去里面旧的东西
                //将audioExtractor里面的猪装载到小推车里面
                int readSize = audioExtractor2.readSampleData(inputBuffer, 0);
                //audioExtractor没猪了，也要告知一下
                if (readSize < 0) {
                    Log.i(LOG_TAG, "audio2已经读取完了");
                    audioDecode2.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    hasAudio2 = false;
                } else {//拿到猪
                    Log.i(LOG_TAG, "audio2获取到的数据长度为：" + readSize);
                    //告诉工厂这头猪的小推车序号、猪的大小、猪在这群猪里的排行、屠宰的标志
                    audioDecode2.queueInputBuffer(inputIndex, 0, readSize, audioExtractor2.getSampleTime(), 0);
                    //读取音频的下一帧
                    audioExtractor2.advance();
                }
            }
        }
        int outputIndex = audioDecode2.dequeueOutputBuffer(decodeInfo2, 0);
        if (outputIndex > -1) {
            Log.i(LOG_TAG, "audio2获取到解码后的数据：" + decodeInfo2.size);
            //获取所有的筐
            ByteBuffer[] outputBuffers = audioDecode2.getOutputBuffers();
            //拿到当前装满火腿肠的筐
            ByteBuffer outputBuffer;
            if (Build.VERSION.SDK_INT >= 21) {
                outputBuffer = audioDecode2.getOutputBuffer(outputIndex);
            } else {
                outputBuffer = outputBuffers[outputIndex];
            }
            //将火腿肠放到新的容器里，便于后期装车运走
            audioData2 = new byte[decodeInfo2.size];
            outputBuffer.get(audioData2);//写入到字节数组中
            outputBuffer.clear();//清空当前筐
            //把筐放回工厂里面
            audioDecode2.releaseOutputBuffer(outputIndex, false);
            //保存数据
            audioQueue2.add(audioData2);
        }
        if ((decodeInfo2.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.i(LOG_TAG, "audio2解码已经完事了");
            return;
        }
    }

    private void initMediaCodec1() throws IOException {
        //初始化MiediaCodec
        audioDecode1 = MediaCodec.createDecoderByType(audioFormat1.getString(MediaFormat.KEY_MIME));
        //数据格式，surface用来渲染解析出来的数据;加密用的对象；标志 encode ：1 decode：0
        audioDecode1.configure(audioFormat1, null, null, 0);
        if (audioFormat1.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            Log.i(LOG_TAG, "audioFormat1 最大帧大小： "+audioFormat1.getValueTypeForKey(MediaFormat.KEY_MAX_INPUT_SIZE));
        }


        //启动解码
        audioDecode1.start();
    }

    private void initMediaCodec2() throws IOException {
        //初始化MiediaCodec
        audioDecode2 = MediaCodec.createDecoderByType(audioFormat2.getString(MediaFormat.KEY_MIME));
        //数据格式，surface用来渲染解析出来的数据;加密用的对象；标志 encode ：1 decode：0
        audioDecode2.configure(audioFormat2, null, null, 0);
        MediaCodec.BufferInfo decodeBufferInfo = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
        if (audioFormat2.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            Log.i(LOG_TAG, "audioFormat2 最大帧大小： "+audioFormat2.getValueTypeForKey(MediaFormat.KEY_MAX_INPUT_SIZE));
        }
        //启动解码
        audioDecode2.start();
    }


    private void getTrack1() throws IOException {
        audioExtractor1.setDataSource(inputAudio1);
        int count = audioExtractor1.getTrackCount();
        for (int i = 0; i < count; i++) {
            audioFormat1 = audioExtractor1.getTrackFormat(i);
            if (audioFormat1.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                Log.i(LOG_TAG, "音频1找到了通道" + i);
                audioExtractor1.selectTrack(i);
                break;
            }
        }
    }

    private void getTrack2() throws IOException {
        audioExtractor2.setDataSource(inputAudio2);
        int count = audioExtractor2.getTrackCount();
        for (int i = 0; i < count; i++) {
            audioFormat2 = audioExtractor2.getTrackFormat(i);
            if (audioFormat2.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                Log.i(LOG_TAG, "音频2找到了通道" + i);
                audioExtractor2.selectTrack(i);
                break;
            }
        }
    }
}
