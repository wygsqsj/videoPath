package com.wish.videopath.demo5;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.wish.videopath.R;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 类名称：EncodeAACThread
 * 类描述：将AAC通过MediaCodec接码成PCM文件
 * <p>
 * 创建时间：2021/10/29
 */
class DecodeAACThread extends Thread {

    private Context context;
    private MediaFormat audioFormat;

    public DecodeAACThread(Demo5Activity demo5Activity) {
        context = demo5Activity;

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void run() {
        super.run();
        //通过MediaExtractor获取音频通道
        MediaExtractor audioExtractor = new MediaExtractor();
        try {
            audioExtractor.setDataSource(context.getResources().openRawResourceFd(R.raw.demo5));
            int count = audioExtractor.getTrackCount();
            for (int i = 0; i < count; i++) {
                audioFormat = audioExtractor.getTrackFormat(i);
                if (audioFormat.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                    audioExtractor.selectTrack(i);
                    break;
                }
            }
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1024 * 8);

            //初始化MiediaCodec
            MediaCodec decodeCodec = MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
            decodeCodec.configure(audioFormat, null, null, 0);
            //这个info是用来装载需要解析的数据的，可以理解成绑猪的绳子
            MediaCodec.BufferInfo inputInfo = new MediaCodec.BufferInfo();
            //启动解码
            decodeCodec.start();

            //从猪肉工厂获取装猪的小推车，填充数据后发送到猪肉工厂进行处理
            ByteBuffer[] inputBuffers = decodeCodec.getInputBuffers();//所有的小推车
            int inputIndex = decodeCodec.dequeueInputBuffer(0);//返回当前可用的小推车标号
            //将MediaCodec数据取出来放到这个缓冲区里
            ByteBuffer inputBuffer = inputBuffers[inputIndex];//拿到小推车
            inputBuffer.clear();//扔出去里面旧的东西
            //将audioExtractor里面的猪装载到小推车里面
            int readSize = audioExtractor.readSampleData(inputBuffer, 0);
            //audioExtractor没猪了
            if (readSize < 0) {
                decodeCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {//拿到猪
                //告诉工厂这头猪的小推车序号、大小、猪在这群猪里的排行、
                decodeCodec.queueInputBuffer(inputIndex, 0, 0, audioExtractor.getSampleTime(),
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }


        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (audioExtractor != null) {
                audioExtractor.release();
            }
        }


    }
}
