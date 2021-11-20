package com.wish.videopath.demo5;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.wish.videopath.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * 类名称：EncodeAACThread
 * 类描述：将AAC通过MediaCodec接码成PCM文件
 * <p>
 * 创建时间：2021/10/29
 */
class DecodeAACThread extends Thread {

    private Context context;
    private MediaFormat audioFormat;
    private File pcmFile;
    private boolean hasAudio = true;
    private FileOutputStream fos = null;
    private int startTime = 1;
    private int endTime = 8;


    public DecodeAACThread(Demo5Activity demo5Activity) {
        context = demo5Activity;
        pcmFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "demo5.pcm");
        try {
            pcmFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void run() {
        super.run();
        //通过MediaExtractor获取音频通道
        MediaExtractor audioExtractor = new MediaExtractor();
        MediaCodec decodeCodec = null;
        //pcm文件输出
//        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(pcmFile.getAbsoluteFile());

            audioExtractor.setDataSource(context.getResources().openRawResourceFd(R.raw.see));
            int count = audioExtractor.getTrackCount();
            for (int i = 0; i < count; i++) {
                audioFormat = audioExtractor.getTrackFormat(i);
                if (audioFormat.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                    audioExtractor.selectTrack(i);
                    Log.i(LOG_TAG, "aac 找到了通道" + i);
                    break;
                }
            }

            //初始化MiediaCodec
            decodeCodec = MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
            //数据格式，surface用来渲染解析出来的数据;加密用的对象；标志 encode ：1 decode：0
            decodeCodec.configure(audioFormat, null, null, 0);
            MediaCodec.BufferInfo decodeBufferInfo = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
            //启动解码
            decodeCodec.start();
            /*
             * 同步方式，流程是在while中
             * dequeueInputBuffer -> queueInputBuffer填充数据 -> dequeueOutputBuffer -> releaseOutputBuffer显示画面
             */
            boolean hasAudio = true;
            audioExtractor.seekTo(startTime*1000000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while (true) {
                //所有的猪都运进猪厂后不再添加
                if (hasAudio) {
                    //从猪肉工厂获取装猪的小推车，填充数据后发送到猪肉工厂进行处理
                    ByteBuffer[] inputBuffers = decodeCodec.getInputBuffers();//所有的小推车
                    int inputIndex = decodeCodec.dequeueInputBuffer(0);//返回当前可用的小推车标号

                    long presentationTime = audioExtractor.getSampleTime();
                    Log.i(LOG_TAG, "当前时间戳：" + presentationTime);
                    if (presentationTime > endTime * 1000000) {
                        Log.i(LOG_TAG, "超过时间了");
                        decodeCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        hasAudio = false;
                        continue;
                    }
                    if (inputIndex != -1) {
                        Log.i(LOG_TAG, "找到了input 小推车" + inputIndex);
                        //将MediaCodec数据取出来放到这个缓冲区里
                        ByteBuffer inputBuffer = inputBuffers[inputIndex];//拿到小推车
                        inputBuffer.clear();//扔出去里面旧的东西
                        //将audioExtractor里面的猪装载到小推车里面
                        int readSize = audioExtractor.readSampleData(inputBuffer, 0);
                        //audioExtractor没猪了，也要告知一下
                        if (readSize < 0) {
                            Log.i(LOG_TAG, "当前音频已经读取完了");
                            decodeCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            hasAudio = false;
                        } else {//拿到猪
                            Log.i(LOG_TAG, "读取到了音频数据，当前音频数据的数据长度为：" + readSize);
                            //告诉工厂这头猪的小推车序号、猪的大小、猪在这群猪里的排行、屠宰的标志
                            decodeCodec.queueInputBuffer(inputIndex, 0, readSize, audioExtractor.getSampleTime(), 0);
                            //读取音频的下一帧
                            audioExtractor.advance();
                        }
                    } else {
                        Log.i(LOG_TAG, "没有可用的input 小推车");
                    }

                }

                //工厂已经把猪运进去了，但是是否加工成火腿肠还是未知的，我们要通过装火腿肠的筐来判断是否已经加工完了
                int outputIndex = decodeCodec.dequeueOutputBuffer(decodeBufferInfo, 0);//返回当前筐的标记
                switch (outputIndex) {
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.i(LOG_TAG, "输出的format已更改" + decodeCodec.getOutputFormat());
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.i(LOG_TAG, "超时，没获取到");
                        break;
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.i(LOG_TAG, "输出缓冲区已更改");
                        break;
                    default:
                        Log.i(LOG_TAG, "获取到解码后的数据了，当前解析后的数据长度为：" + decodeBufferInfo.size);
                        //获取所有的筐
                        ByteBuffer[] outputBuffers = decodeCodec.getOutputBuffers();
                        //拿到当前装满火腿肠的筐
                        ByteBuffer outputBuffer;
                        if (Build.VERSION.SDK_INT >= 21) {
                            outputBuffer = decodeCodec.getOutputBuffer(outputIndex);
                        } else {
                            outputBuffer = outputBuffers[outputIndex];
                        }
                        //将火腿肠放到新的容器里，便于后期装车运走
                        byte[] pcmData = new byte[decodeBufferInfo.size];
                        outputBuffer.get(pcmData);//写入到字节数组中
                        outputBuffer.clear();//清空当前筐
                        //装车
                        fos.write(pcmData);//数据写入文件中
                        fos.flush();
                        //把筐放回工厂里面
                        decodeCodec.releaseOutputBuffer(outputIndex, false);
                        break;
                }
                if ((decodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.i(LOG_TAG, "表示当前编解码已经完事了");
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (audioExtractor != null) {
                audioExtractor.release();
            }
            if (decodeCodec != null) {
                decodeCodec.stop();
                decodeCodec.release();
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
