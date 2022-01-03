package com.wish.videopath.demo8.mediacodec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.wish.videopath.util.LiveTaskManager;

import java.nio.ByteBuffer;

import static com.wish.videopath.MainActivity.LOG_TAG;


/**
 * 将camera2得到的数据进行编码，存储到队列中
 */
public class CameraCodec extends Thread {

    private Camera2Helper camera2Helper;
    private boolean isEncode = true;
    private boolean inOutFinish, isFinishInput;
    private int width = 1280;
    private int height = 720;
    private int framerate = 30;//帧率
    private int biterate = 8500 * 1000;
    private String encodeMine = "video/avc";
    private byte[] nv12;
    //开启时间
    private long startTime;
    //传输层
    private ScreenLive screenLive;

    private MediaCodec encodeCodec;
    private long timeStamp;


    public CameraCodec(ScreenLive screenLive, Camera2Helper helper, int width, int height, int framerate, int biterate) {
        this.camera2Helper = helper;
        this.width = width;
        this.height = height;
        this.framerate = framerate;
        this.biterate = biterate;
        this.screenLive = screenLive;
        LiveTaskManager.getInstance().execute(this);
    }


    @Override
    public void run() {
        super.run();
        try {
            //构建对应的MeidaFormat，后期我们会将摄像头数据旋转90读成为竖屏，所以此处调换一下宽高
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(encodeMine, height, width);
            //设置yuv格式
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            //比特率
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 3 / 2);
            //描述视频格式的帧速率
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
            //关键帧之间的间隔，此处指定为1秒
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            //构建编码h264MediaCodec
            encodeCodec = MediaCodec.createEncoderByType(encodeMine);
            encodeCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            MediaCodec.BufferInfo encodeBufferInfo = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
            //启动编码器
            encodeCodec.start();
            while (isEncode || !inOutFinish) {
                if (startTime == 0) {
                    startTime = System.currentTimeMillis();//得到时间，毫秒
                }
                //获取当前的yuv数据
                if (camera2Helper != null && !camera2Helper.getYUVQueue().isEmpty()) {
                    //从猪肉工厂获取装猪的小推车，填充数据后发送到猪肉工厂进行处理
                    ByteBuffer[] inputBuffers = encodeCodec.getInputBuffers();//所有的小推车
                    int inputIndex = encodeCodec.dequeueInputBuffer(0);//返回当前可用的小推车标号
                    if (inputIndex != -1) {
                        /*
                         *注意手动触发I帧，放置编码时因为画面不动，长时间不输出I帧的问题
                         */
                        if (System.currentTimeMillis() - timeStamp > 2000) {//2秒一个I帧
                            Bundle bundle = new Bundle();
                            bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);//触发I帧
                            encodeCodec.setParameters(bundle);
                            timeStamp = System.currentTimeMillis();
                        }

                        Log.i(LOG_TAG, "找到了input 小推车" + inputIndex);
                        //将MediaCodec数据取出来放到这个缓冲区里
                        ByteBuffer inputBuffer = inputBuffers[inputIndex];//拿到小推车
                        inputBuffer.clear();//扔出去里面旧的东西
                        //从yuv队列中取出数据
                        nv12 = camera2Helper.getYUVQueue().poll();
                        if (nv12 == null) {
                            continue;
                        }
                        //audioExtractor没猪了，也要告知一下
                        if (nv12.length < 0) {
                            Log.i(LOG_TAG, "当前yuv数据异常");
                        } else {//拿到猪
                            //把转换后的YUV420格式的视频帧放到编码器输入缓冲区中
                            inputBuffer.limit(nv12.length);
                            inputBuffer.put(nv12, 0, nv12.length);
                            encodeCodec.queueInputBuffer(inputIndex, 0, nv12.length, System.currentTimeMillis(), 0);
                        }
                    } else {
                        Log.i(LOG_TAG, "没有可用的input 小推车");
                    }
                }


                //工厂已经把猪运进去了，但是是否加工成火腿肠还是未知的，我们要通过装火腿肠的筐来判断是否已经加工完了
                int outputIndex = encodeCodec.dequeueOutputBuffer(encodeBufferInfo, 10000);//返回当前筐的标记
                if (outputIndex >= 0) {
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

                    //将数据读取到outData中
                    byte[] outData = new byte[encodeBufferInfo.size];
                    outputBuffer.get(outData);

                    RTMPPacket rtmpPacket = new RTMPPacket();
                    rtmpPacket.setBuffer(outData);
                    rtmpPacket.setType(RTMPPacket.VIDEO_TYPE);
                    long tms = encodeBufferInfo.presentationTimeUs - startTime;
                    Log.i(LOG_TAG, "视频 tms:" + tms);
                    rtmpPacket.setTms(tms);
                    screenLive.addPacket(rtmpPacket);

                    encodeCodec.releaseOutputBuffer(outputIndex, false);
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (encodeCodec != null) {
                encodeCodec.release();
            }
        }
    }

    /**
     * 旋转yuv数据，将横屏数据转换为竖屏
     */
    private byte[] revolveYuv(byte[] yuvData) {
        byte[] revolveData = new byte[yuvData.length];
        int y_size = width * height;
        //uv高度
        int uv_height = height >> 1;
        //旋转y,左上角跑到右上角，左下角跑到左上角，从左下角开始遍历
        int k = 0;
        for (int i = 0; i < width; i++) {
            for (int j = height - 1; j > -1; j--) {
                revolveData[k++] = yuvData[width * j + i];
            }
        }
        //旋转uv
        for (int i = 0; i < width; i += 2) {
            for (int j = uv_height - 1; j > -1; j--) {
                revolveData[k++] = yuvData[y_size + width * j + i];
                revolveData[k++] = yuvData[y_size + width * j + i + 1];
            }
        }
        return revolveData;
    }

    //计算时间戳
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / framerate;
    }

    //在使用Camera的时候，设置预览的数据格式为NV21，转换成nv12
    private byte[] NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return null;
        int framesize = width * height;
        int i = 0, j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for (i = 0; i < framesize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j] = nv21[j + framesize - 1];
        }
        return nv12;
    }

    //Camera2的时候设置yuv格式为NV21，转换成YV12
    private void YV12toNV12(byte[] yv12bytes, byte[] nv12bytes, int width, int height) {
        int nLenY = width * height;
        int nLenU = nLenY / 4;

        System.arraycopy(yv12bytes, 0, nv12bytes, 0, width * height);
        for (int i = 0; i < nLenU; i++) {
            nv12bytes[nLenY + 2 * i] = yv12bytes[nLenY + i];
            nv12bytes[nLenY + 2 * i + 1] = yv12bytes[nLenY + nLenU + i];
        }
    }


    public void stopEncode() {
        isEncode = false;
    }
}
