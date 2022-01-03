package com.wish.videopath.demo8.mediacodec;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import androidx.annotation.RequiresApi;

import static com.wish.videopath.MainActivity.LOG_TAG;


/**
 * 视频编码服务
 */
public class VideoCodec extends Thread {
    private final Context context;
    //录屏
    private MediaProjection mediaProjection;
    //虚拟画布
    private VirtualDisplay virtualDisplay;
    //传输层
    private ScreenLive screenLive;


    private MediaCodec mediaCodec;

    private int width = 720;
    private int height = 1280;
    private int framerate = 15;//帧率
    private int biterate = 8500 * 1000;
    private String encodeMine = "video/avc";
    private Surface inputSurface;
    private boolean isLive;
    //开启时间
    private long startTime;
    //编码时间
    private long timeStamp;

    public VideoCodec(ScreenLive screenLive, Context context) {
        this.screenLive = screenLive;
        this.context = context;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startLive(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
        //构建对应的MeidaFormat
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        //注意此处要设置成surface类型
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        //比特率
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 4000_000/*width * height * 5*/);
        //描述视频格式的帧速率
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
        //关键帧之间的间隔，此处指定为1秒
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        //构建编码h264MediaCodec
        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = mediaCodec.createInputSurface();
            initVirtualDisplay();
            start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        super.run();
        isLive = true;
        MediaCodec.BufferInfo encodeBufferInfo = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
        try { //启动编码器
            mediaCodec.start();
            /*
             *注意手动触发I帧，放置编码时因为画面不动，长时间不输出I帧的问题
             */
            while (isLive) {
                if (System.currentTimeMillis() - timeStamp > 2000) {//2秒一个I帧
                    Bundle bundle = new Bundle();
                    bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);//触发I帧
                    mediaCodec.setParameters(bundle);
                    timeStamp = System.currentTimeMillis();
                }
                int outputIndex = mediaCodec.dequeueOutputBuffer(encodeBufferInfo, 10000);//返回当前筐的标记
                if (outputIndex >= 0) {
                    Log.i(LOG_TAG, "获取到surface中的数据，当前解析后的数据长度为：" + encodeBufferInfo.size);
                    if (startTime == 0) {
                        startTime = encodeBufferInfo.presentationTimeUs / 1000;//得到时间，毫秒
                    }
                    //获取所有的筐
                    ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                    //拿到当前装满火腿肠的筐
                    ByteBuffer outputBuffer;
                    if (Build.VERSION.SDK_INT >= 21) {
                        outputBuffer = mediaCodec.getOutputBuffer(outputIndex);
                    } else {
                        outputBuffer = outputBuffers[outputIndex];
                    }
                    //将数据读取到outData中
                    byte[] outData = new byte[encodeBufferInfo.size];
                    outputBuffer.get(outData);

                    RTMPPacket rtmpPacket = new RTMPPacket();
                    rtmpPacket.setBuffer(outData);
                    rtmpPacket.setType(RTMPPacket.VIDEO_TYPE);
                    long tms = encodeBufferInfo.presentationTimeUs / 1000 - startTime;
                    rtmpPacket.setTms(tms);
                    screenLive.addPacket(rtmpPacket);

                    //把筐放回工厂里面
                    mediaCodec.releaseOutputBuffer(outputIndex, false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (mediaCodec != null) {
                mediaCodec.release();
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initVirtualDisplay() {
        virtualDisplay = mediaProjection.createVirtualDisplay(
                LOG_TAG,  //virtualDisplay 的名字，随意写
                width,
                height,
                context.getResources().getDisplayMetrics().densityDpi, // virtualDisplay 的 dpi 值，这里都跟应用保持一致即可
                // 表示是一个虚拟的surface
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                inputSurface, //获取内容的 surface
                null, //回调
                null);  //回调执行的handler
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void stopCode() {
        isLive = false;
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
    }
}
