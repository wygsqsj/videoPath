package com.wish.videopath.demo6;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static com.wish.videopath.MainActivity.LOG_TAG;

public class H264DecodeThread extends Thread {

    private Demo6Activity demo6Activity;
    private int width = 1280;
    private int height = 720;
    private int framerate = 30;//帧率
    private int biterate = 8500 * 1000;
    private Surface surface;
    private FileInputStream fis;
    private MediaCodec decodeCodec;
    private boolean hasVideo;

    public H264DecodeThread(Demo6Activity demo6Activity, int width, int height, int framerate, int biterate, Surface surface) {
        this.demo6Activity = demo6Activity;
        this.width = width;
        this.height = height;
        this.framerate = framerate;
        this.biterate = biterate;
        this.surface = surface;
    }


    @Override
    public void run() {
        super.run();
        try {
            fis = new FileInputStream(new File(demo6Activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    Demo6Activity.FILENAME264));
            //初始化codec
            decodeCodec = MediaCodec.createDecoderByType("video/avc");
            //初始化编码器
            final MediaFormat mediaformat = MediaFormat.createVideoFormat("video/avc", width, height);
          /*  //获取h264中的pps及sps数据
            byte[] header_sps = {0, 0, 0, 1, 103, 66, 0, 42, (byte) 149, (byte) 168, 30, 0, (byte) 137, (byte) 249, 102, (byte) 224, 32, 32, 32, 64};
            byte[] header_pps = {0, 0, 0, 1, 104, (byte) 206, 60, (byte) 128, 0, 0, 0, 1, 6, (byte) 229, 1, (byte) 151, (byte) 128};
            mediaformat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
            mediaformat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));*/

            //设置帧率
            mediaformat.setInteger(MediaFormat.KEY_FRAME_RATE, biterate);
            decodeCodec.configure(mediaformat, surface, null, 0);
            decodeCodec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long startMs = System.currentTimeMillis();
            long timeoutUs = 10000;
            byte[] marker0 = new byte[]{0, 0, 0, 1};
            byte[] dummyFrame = new byte[]{0x00, 0x00, 0x01, 0x20};
            byte[] streamBuffer = null;
            int bytes_cnt = 0;

            while (true) {
                //所有的猪都运进猪厂后不再添加
                if (hasVideo) {
                    //从fis中获取数据
                    streamBuffer = new byte[1024 * 8];
                    int i = fis.read(streamBuffer, 0, streamBuffer.length);
                    if (i < 0) {
                        hasVideo = false;
                    } else {
                        bytes_cnt = streamBuffer.length;
                        if (bytes_cnt == 0) {
                            streamBuffer = dummyFrame;
                        }
                    }

                    int startIndex = 0;
                    int remaining = bytes_cnt;
                    while (true) {
                        if (remaining == 0 || startIndex >= remaining) {
                            break;
                        }
                        int nextFrameStart = KMPMatch(marker0, streamBuffer, startIndex + 2, remaining);
                        if (nextFrameStart == -1) {
                            nextFrameStart = remaining;
                        } else {
                        }

                        int inIndex = mCodec.dequeueInputBuffer(timeoutUs);
                        if (inIndex >= 0) {
                            ByteBuffer byteBuffer = inputBuffers[inIndex];
                            byteBuffer.clear();
                            byteBuffer.put(streamBuffer, startIndex, nextFrameStart - startIndex);
//在给指定Index的inputbuffer[]填充数据后，调用这个函数把数据传给解码器
                            mCodec.queueInputBuffer(inIndex, 0, nextFrameStart - startIndex, 0, 0);
                            startIndex = nextFrameStart;
                        } else {
                            Log.e(TAG, "aaaaa");
                            continue;
                        }

                        int outIndex = mCodec.dequeueOutputBuffer(info, timeoutUs);
                        if (outIndex >= 0) {
//帧控制是不在这种情况下工作，因为没有PTS H264是可用的
                            while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            boolean doRender = (info.size != 0);
//对outputbuffer的处理完后，调用这个函数把buffer重新返回给codec类。
                            mCodec.releaseOutputBuffer(outIndex, doRender);
                        } else {
                            Log.e(TAG, "bbbb");
                        }
                    }
                }
            }
               /*     ByteBuffer[] inputBuffers = decodeCodec.getInputBuffers();//所有的小推车
                    int inputIndex = decodeCodec.dequeueInputBuffer(0);//返回当前可用的小推车标号
                    if (inputIndex != -1) {
                        Log.i(LOG_TAG, "找到了input 小推车" + inputIndex);
                        //将MediaCodec数据取出来放到这个缓冲区里
                        ByteBuffer inputBuffer = inputBuffers[inputIndex];//拿到小推车
                        inputBuffer.clear();//扔出去里面旧的东西
                        //将audioExtractor里面的猪装载到小推车里面
                        int readSize = decodeCodec.readSampleData(inputBuffer, 0);
                        //audioExtractor没猪了，也要告知一下
                        if (readSize < 0) {
                            Log.i(LOG_TAG, "当前264文件已经读取完了");
                            decodeCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            hasVideo = false;
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
                        //把数据输出到surface中
                        decodeCodec.releaseOutputBuffer(outputIndex, true);
                        break;
                }
                if ((decodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.i(LOG_TAG, "表示当前编解码已经完事了");
                    break;
                }*/
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
