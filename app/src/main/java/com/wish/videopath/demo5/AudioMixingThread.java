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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.LinkedBlockingQueue;

import androidx.annotation.RequiresApi;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * 将两个音频进行混音，替换掉原视频的声音
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class AudioMixingThread extends Thread {
    private final Demo5Activity context;

    private int volume1 = 20;//音频1设置音量大小
    private int volume2 = 100;//音频2设置音量大小
    private AssetFileDescriptor inputAudio1;
    private AssetFileDescriptor inputAudio2;
    private AssetFileDescriptor videoAF;
    private File outFile;

    //音视频截取开始时间及结束时间
    private long startTimeUs = 0;
    private long endTimeUs = 4000000;

    //两个解码器一个编码器
    private MediaCodec audioDecode1;
    private MediaCodec audioDecode2;
    private MediaCodec encodeCodec;

    //两个分离器一个合成器
    private MediaExtractor audioExtractor1 = new MediaExtractor();
    private MediaExtractor audioExtractor2 = new MediaExtractor();
    private MediaExtractor wavExtractor = new MediaExtractor();
    private MediaMuxer newVideoMuxer;
    //两个输入音频format一个输出音频format
    private MediaFormat audioFormat1, audioFormat2, wavAudioFormat;

    private MediaCodec.BufferInfo decodeInfo1 = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
    private MediaCodec.BufferInfo decodeInfo2 = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
    //启动音频Muxer输出
    private int audioTrackIndex = 0;

    //混音需要用的临时数据
    private short temp1, temp2;
    private int temp3;

    private File audioPcm2, audioPcm1, mixPcm, wavFile;


    public AudioMixingThread(Demo5Activity context) {
        this.context = context;
        inputAudio1 = context.getResources().openRawResourceFd(R.raw.demo5);
        inputAudio2 = context.getResources().openRawResourceFd(R.raw.see);
        videoAF = context.getResources().openRawResourceFd(R.raw.animal);
        outFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "mixing.mp4");
        audioPcm2 = new File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "audio2.pcm");
        audioPcm1 = new File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "audio1.pcm");
        mixPcm = new File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "mixPcm.pcm");
        wavFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "mixWav.wav");
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void run() {
        super.run();
        try {
            //aac解码成pcm
            decodeAudio1();
            decodeAudio2();
            //将两个pcm混音成一个pcm
            mixingAudio();
            //将混音后的pcm更改为wav文件
            mixPcm2Wav();
            //wav编码成aac，替换原视频音频
            writeNewAudio();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (audioExtractor1 != null) {
                audioExtractor1.release();
            }
            if (audioExtractor2 != null) {
                audioExtractor2.release();
            }

            if (videoExtractor != null) {
                videoExtractor.release();
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

            if (newVideoMuxer != null) {
                newVideoMuxer.stop();
                newVideoMuxer.release();
            }

        }
    }

    private void mixPcm2Wav() throws IOException {
        wavFile.createNewFile();
        new PcmToWavUtil(44100, 2, 16)
                .pcmToWav(mixPcm.getAbsolutePath(), wavFile.getAbsolutePath());
    }

    int videoTrackIndex;
    MediaExtractor videoExtractor;
    int videoIndex = 0;
    MediaFormat videoTrackFormat = null;

    private void writeNewAudio() throws Exception {
        //初始化合成器
        newVideoMuxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        //初始化原视频分离器，从原视频中取出视频轨道
        videoExtractor = new MediaExtractor();
        videoExtractor.setDataSource(videoAF);

        int trackCount = videoExtractor.getTrackCount();//得到源文件通道数
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = videoExtractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                videoTrackFormat = format; //获取指定（index）的通道格式
                videoIndex = i;
            } else if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                wavAudioFormat = format; //获取指定（index）的通道格式
            }
        }
        //为合成器添加视频轨道
        videoTrackIndex = newVideoMuxer.addTrack(videoTrackFormat);

 /*       // 将音频轨道设置为aac
        int audioBitrate = wavAudioFormat.getInteger(MediaFormat.KEY_BIT_RATE);
        // 将音频轨道设置为aac
        wavAudioFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        //为合成器添加音频轨道
        audioTrackIndex = newVideoMuxer.addTrack(wavAudioFormat);

        newVideoMuxer.start();//开始合成*/

        //构建wav分离器，获取音频轨道，得到format
        wavExtractor.setDataSource(wavFile.getAbsolutePath());
        int count = wavExtractor.getTrackCount();
        for (int i = 0; i < count; i++) {
            MediaFormat format = wavExtractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                Log.i(LOG_TAG, "wav找到了通道" + i);
                wavExtractor.selectTrack(i);
                break;
            }
        }

        //获取最大的wav缓存
        int maxBufferSize = 8 * 1024 * 1024;
        if (wavAudioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = wavAudioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        }
        Log.i(LOG_TAG, "wav编码缓存区大小" + maxBufferSize);

        //将wav编码成aac,构建编码器
        MediaFormat encodeFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2);
        encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        //比特率 声音中的比特率是指将模拟声音信号转换成数字声音信号后，单位时间内的二进制数据量，是间接衡量音频质量的一个指标
        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, AudioFormat.ENCODING_PCM_16BIT);
        //最大的缓冲区大小，如果inputBuffer大小小于我们定义的缓冲区大小，可能报出缓冲区溢出异常
        encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBufferSize);
        //构建编码器
        encodeCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        //数据格式，surface用来渲染解析出来的数据;加密用的对象；标志 encode ：1 decode：0
        encodeCodec.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        //启动编码
        encodeCodec.start();


        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer wavBuffer = ByteBuffer.allocateDirect(maxBufferSize);

        //音频编码后通过合成器写入文件
        boolean hasWav = true;
        while (true) {
            if (hasWav) {
                int inputIndex = encodeCodec.dequeueInputBuffer(0);//返回当前可用的小推车标号
                if (inputIndex > -1) {
                    long pts = wavExtractor.getSampleTime();
                    Log.i(LOG_TAG, "当前pts:" + pts);
                    if (pts < 0) {
                        Log.i(LOG_TAG, "wav数据读取完成");
                        encodeCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        hasWav = false;
                    } else {
                        //从wav分离器中读取数据放入编码器进行编码
                        int dataSize = wavExtractor.readSampleData(wavBuffer, 0);
                        ByteBuffer inputBuffer = encodeCodec.getInputBuffer(inputIndex);
                        inputBuffer.clear();
                        inputBuffer.put(wavBuffer);
                        encodeCodec.queueInputBuffer(inputIndex, 0, dataSize, pts, wavExtractor.getSampleFlags());
                        wavExtractor.advance();
                    }
                }
            }
            int outIndex = encodeCodec.dequeueOutputBuffer(audioBufferInfo, 0);//返回当前可用的小推车标号
            if (outIndex > -1) {
                if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.i(LOG_TAG, "音频编码已经完事了");
                    break;
                }
                Log.i(LOG_TAG, "编码器获取编码码后的数据：" + audioBufferInfo.size);
                //获取所有的筐
                ByteBuffer[] outputBuffers = encodeCodec.getOutputBuffers();
                //拿到当前装满火腿肠的筐
                ByteBuffer outputBuffer;
                if (Build.VERSION.SDK_INT >= 21) {
                    outputBuffer = encodeCodec.getOutputBuffer(outIndex);
                } else {
                    outputBuffer = outputBuffers[outIndex];
                }
                newVideoMuxer.writeSampleData(audioTrackIndex, outputBuffer, audioBufferInfo);

                encodeCodec.releaseOutputBuffer(outIndex, false);
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.i(LOG_TAG, "新的音频数据输出的format已更改" + encodeCodec.getOutputFormat());
                audioTrackIndex = newVideoMuxer.addTrack(encodeCodec.getOutputFormat());
                newVideoMuxer.start();//开始合成
            }

        }
        Log.i(LOG_TAG, "音频编码done");
        //开始写入视频文件
        videoExtractor.selectTrack(videoIndex);
        videoExtractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        int maxVideoBufferSize = 8 * 1024 * 1024;
        if (videoTrackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxVideoBufferSize = videoTrackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        }
        Log.i(LOG_TAG, "视频写入缓冲区大小： " + maxVideoBufferSize);
        ByteBuffer videoBuffer = ByteBuffer.allocateDirect(maxVideoBufferSize);
        MediaCodec.BufferInfo VideoBufferInfo = new MediaCodec.BufferInfo();

        while (true) {
            long pts = videoExtractor.getSampleTime();
            long newPts = pts - startTimeUs;
            if (pts == -1) {
                Log.i(LOG_TAG, "当前视频已经读到末尾，跳出");
                break;
            }
            if (pts < startTimeUs) {
                Log.i(LOG_TAG, "当前视频截取起始点未到" + pts);
                videoExtractor.advance();
                continue;
            }
            if (pts > endTimeUs) {
                Log.i(LOG_TAG, "已经到达当前视频截取末尾，跳出" + pts);
                break;
            }

            int videoDataSize = videoExtractor.readSampleData(videoBuffer, 0);
            VideoBufferInfo.flags = videoExtractor.getSampleFlags();
            VideoBufferInfo.presentationTimeUs = newPts;
            VideoBufferInfo.size = videoDataSize;
            if (videoDataSize < 0) {
                Log.i(LOG_TAG, "当前视频已经读取完了");
                break;
            }
            Log.i(LOG_TAG, "写入视频数据： " + videoDataSize);
            newVideoMuxer.writeSampleData(videoTrackIndex, videoBuffer, VideoBufferInfo);
            videoExtractor.advance();
        }
    }

    private void mixingAudio() throws FileNotFoundException {
        FileInputStream fisAudio1 = new FileInputStream(audioPcm1);
        FileInputStream fisAudio2 = new FileInputStream(audioPcm2);
        FileOutputStream mixAudio = new FileOutputStream(mixPcm);
        int bufSize = 4096;
        byte[] audio1Buffer = new byte[bufSize];
        byte[] audio2Buffer = new byte[bufSize];
        byte[] audio3Buffer = new byte[bufSize];
        float newVolume1 = volume1 * 1.0f / 100;
        float newVolume2 = volume2 * 1.0f / 100f;

        boolean isFinishAudio1 = false, isFinishAudio2 = false;

        try {
            while (!isFinishAudio1 || !isFinishAudio2) {
                int audioSize1 = fisAudio1.read(audio1Buffer);
                int audioSize2 = fisAudio2.read(audio2Buffer);
                if (audioSize1 < 0) {
                    isFinishAudio1 = true;
                }
                if (audioSize2 < 0) {
                    isFinishAudio2 = true;
                }
                Log.i(LOG_TAG, "音频1合成大小" + audioSize1);
                Log.i(LOG_TAG, "音频2合成大小" + audioSize2);
                if (audioSize1 >= 0 && audioSize2 >= 0) {
                    for (int i = 0; i < audioSize1; i += 2) {
                        //声音数据的排列顺序为低8位在前，高8位在后,此处还原为真实的数据，即低8位放到后面，高8位放到前面
                        temp1 = (short) ((audio1Buffer[i] & 0xff) | ((audio1Buffer[i + 1] & 0xff) << 8));
                        temp2 = (short) ((audio2Buffer[i] & 0xff) | ((audio2Buffer[i + 1] & 0xff) << 8));
                        //声音大小的控制通过振幅来控制，用当前字节*对应的音量即可得到
                        temp1 = (short) (temp1 * newVolume1);
                        temp2 = (short) (temp2 * newVolume2);
                        temp3 = temp1 + temp2;
                        //超出的部分舍弃掉
                        if (temp3 > 32767) {
                            temp3 = 32767;
                        } else if (temp3 < -32768) {
                            temp3 = -32768;
                        }
                        //存入新的数据中
                        audio3Buffer[i] = (byte) (temp3 & 0xff);
                        audio3Buffer[i + 1] = (byte) ((temp3 >> 8) & 0xff);
                    }
                    mixAudio.write(audio3Buffer);
                } else {
                    if (audioSize1 >= 0) {
                        Log.i(LOG_TAG, "当前音频1未写入：" + audioSize1);
                        mixAudio.write(audio1Buffer);
                    }
                    if (audioSize2 >= 0) {
                        Log.i(LOG_TAG, "当前音频2未写入：" + audioSize2);
                        mixAudio.write(audio2Buffer);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                mixAudio.close();
                fisAudio1.close();
                fisAudio2.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void decodeAudio1() throws Exception {
        //获取音频轨道，得到format
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
        //初始化MiediaCodec
        audioDecode1 = MediaCodec.createDecoderByType(audioFormat1.getString(MediaFormat.KEY_MIME));
        //数据格式，surface用来渲染解析出来的数据;加密用的对象；标志 encode ：1 decode：0
        audioDecode1.configure(audioFormat1, null, null, 0);
        //启动解码器
        audioDecode1.start();

        //audio1解码出来的pcm原始数据
        if (!audioPcm1.exists()) {
            audioPcm1.createNewFile();
        }
        FileChannel fosAudio1 = new FileOutputStream(audioPcm1).getChannel();

        //跳到截取开始的位置
        audioExtractor1.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        int maxBufferSize;
        if (audioFormat1.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = audioFormat1.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            maxBufferSize = 8 * 1024 * 1024;
        }
        Log.d(LOG_TAG, "audio1 最大的缓冲大小：" + maxBufferSize);
        ByteBuffer audioBuffer1 = ByteBuffer.allocateDirect(maxBufferSize);
        MediaCodec.BufferInfo audioInfo1 = new MediaCodec.BufferInfo();

        while (true) {
            //从猪肉工厂获取装猪的小推车，填充数据后发送到猪肉工厂进行处理
            ByteBuffer[] inputBuffers = audioDecode1.getInputBuffers();//所有的小推车
            int inputIndex = audioDecode1.dequeueInputBuffer(10_000);//返回当前可用的小推车标号
            if (inputIndex != -1) {
                long pts = audioExtractor1.getSampleTime();
                if (pts < startTimeUs) {
                    Log.d(LOG_TAG, "当前时间不到截取范围，读取下一帧");
                    audioExtractor1.advance();
                    continue;
                } else if (pts > endTimeUs) {
                    Log.d(LOG_TAG, "当前时间超过截取范围，跳出");
                    break;
                } else if (pts == -1) {
                    Log.d(LOG_TAG, "当前读取到了视频末尾");
                    break;
                }

                audioInfo1.flags = audioExtractor1.getSampleFlags();
                audioInfo1.presentationTimeUs = pts;
                audioInfo1.size = audioExtractor1.readSampleData(audioBuffer1, 0);

                ByteBuffer inputBuffer = audioDecode1.getInputBuffer(inputIndex);//拿到小推车
                inputBuffer.clear();//扔出去里面旧的东西
                inputBuffer.put(audioBuffer1);//将audioExtractor里面的猪装载到小推车里面
                //告诉工厂这头猪的小推车序号、猪的大小、猪在这群猪里的排行、屠宰的标志
                audioDecode1.queueInputBuffer(
                        inputIndex,
                        0,
                        audioInfo1.size,
                        audioInfo1.presentationTimeUs,
                        audioInfo1.flags
                );
                //读取音频的下一帧
                audioExtractor1.advance();
            }

            int outputIndex = audioDecode1.dequeueOutputBuffer(decodeInfo1, 10_000);
            while (outputIndex > -1) {
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
                fosAudio1.write(outputBuffer);
                //把筐放回工厂里面
                audioDecode1.releaseOutputBuffer(outputIndex, false);
                //再次读取，直到输出缓冲区里没有数据
                outputIndex = audioDecode1.dequeueOutputBuffer(decodeInfo1, 10_000);
            }
        }

    }

    private void decodeAudio2() throws Exception {
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

        //初始化MiediaCodec
        audioDecode2 = MediaCodec.createDecoderByType(audioFormat2.getString(MediaFormat.KEY_MIME));
        //数据格式，surface用来渲染解析出来的数据;加密用的对象；标志 encode ：1 decode：0
        audioDecode2.configure(audioFormat2, null, null, 0);
        //启动解码
        audioDecode2.start();

        //audio1解码出来的pcm原始数据

        if (!audioPcm2.exists()) {
            audioPcm2.createNewFile();
        }
        FileChannel fosAudio2 = new FileOutputStream(audioPcm2).getChannel();

        //跳到截取开始的位置
        audioExtractor2.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        int maxBufferSize;
        if (audioFormat2.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = audioFormat2.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            maxBufferSize = 8 * 1024 * 1024;
        }
        Log.d(LOG_TAG, "audio2 最大的缓冲大小：" + maxBufferSize);
        ByteBuffer audioBuffer2 = ByteBuffer.allocateDirect(maxBufferSize);
        MediaCodec.BufferInfo audioInfo2 = new MediaCodec.BufferInfo();

        while (true) {
            //从猪肉工厂获取装猪的小推车，填充数据后发送到猪肉工厂进行处理
            int inputIndex = audioDecode2.dequeueInputBuffer(10_000);//返回当前可用的小推车标号
            if (inputIndex != -1) {
                long pts = audioExtractor2.getSampleTime();
                if (pts < startTimeUs) {
                    Log.d(LOG_TAG, "当前时间不到截取范围，读取下一帧");
                    audioExtractor2.advance();
                    continue;
                } else if (pts > endTimeUs) {
                    Log.d(LOG_TAG, "当前时间超过截取范围，跳出");
                    break;
                } else if (pts == -1) {
                    Log.d(LOG_TAG, "当前读取到了视频末尾");
                    break;
                }

                audioInfo2.flags = audioExtractor2.getSampleFlags();
                audioInfo2.presentationTimeUs = pts;
                audioInfo2.size = audioExtractor2.readSampleData(audioBuffer2, 0);

                ByteBuffer inputBuffer = audioDecode2.getInputBuffer(inputIndex);//拿到小推车
                inputBuffer.clear();//扔出去里面旧的东西
                inputBuffer.put(audioBuffer2);//将audioExtractor里面的猪装载到小推车里面
                //告诉工厂这头猪的小推车序号、猪的大小、猪在这群猪里的排行、屠宰的标志
                audioDecode2.queueInputBuffer(
                        inputIndex,
                        0,
                        audioInfo2.size,
                        audioInfo2.presentationTimeUs,
                        audioInfo2.flags
                );
                //读取音频的下一帧
                audioExtractor2.advance();
            }

            int outputIndex = audioDecode2.dequeueOutputBuffer(decodeInfo2, 10_000);
            while (outputIndex > -1) {
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
                fosAudio2.write(outputBuffer);
                //把筐放回工厂里面
                audioDecode2.releaseOutputBuffer(outputIndex, false);
                //再次读取，直到输出缓冲区里没有数据
                outputIndex = audioDecode2.dequeueOutputBuffer(decodeInfo2, 10_000);
            }
        }
    }

}
