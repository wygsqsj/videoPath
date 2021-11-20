package com.wish.videopath.demo6;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * 类名称：H264EncodeThread
 * 类描述：将摄像头采集到视频数据编码成h264X，使用IO处添加时间戳没添加上
 * <p>
 * 摄像头在Activity中已经开启，返回的数据流存放在一个que
 */
public class H264EncodeThread extends Thread {

    private Demo6Activity demo6Activity;
    private boolean isEncode = true;
    private boolean inOutFinish, isFinishInput;
    private int width = 1280;
    private int height = 720;
    private int framerate = 30;//帧率
    private int biterate = 8500 * 1000;
    private String encodeMine = "video/avc";
    private byte[] configByte, yuvData, revolveData;


    private MediaCodec encodeCodec;
    private File out264File;
    private FileOutputStream fos;


    public H264EncodeThread(Demo6Activity demo6Activity, int width, int height, int framerate, int biterate) {
        this.demo6Activity = demo6Activity;
        this.width = width;
        this.height = height;
        this.framerate = framerate;
        this.biterate = biterate;
    }

    boolean isflag = false;

    @Override
    public void run() {
        super.run();
        try {
            out264File = new File(demo6Activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES), demo6Activity.FILENAME264);
            out264File.createNewFile();
            fos = new FileOutputStream(out264File);
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

            long generateIndex = 0;//计算时间戳用的index
            long pts = 0;//时间戳
            long startTime = System.nanoTime();
            while (isEncode || !inOutFinish) {
                if (isEncode) {
                    //获取当前的yuv数据
                    if (!demo6Activity.getYUVQueue().isEmpty()) {
                        //从猪肉工厂获取装猪的小推车，填充数据后发送到猪肉工厂进行处理
                        ByteBuffer[] inputBuffers = encodeCodec.getInputBuffers();//所有的小推车
                        int inputIndex = encodeCodec.dequeueInputBuffer(0);//返回当前可用的小推车标号
                        if (inputIndex != -1) {
                            Log.i(LOG_TAG, "找到了input 小推车" + inputIndex);
                            //将MediaCodec数据取出来放到这个缓冲区里
                            ByteBuffer inputBuffer = inputBuffers[inputIndex];//拿到小推车
                            inputBuffer.clear();//扔出去里面旧的东西
                            //从yuv队列中取出数据
                            yuvData = demo6Activity.getYUVQueue().poll();
                            //摄像头默认是横着的，需要旋转90度
                            revolveData = revolveYuv(yuvData);
                            //保存一张yuv图片
                            if (!isflag) {
                                capture(revolveData);
                                isflag = true;
                            }
                            byte[] yuv420sp = new byte[width * height * 3 / 2];
                            //把待编码的视频帧转换为YUV420格式
                            NV21ToNV12(revolveData, yuv420sp, height, width);

                            //audioExtractor没猪了，也要告知一下
                            if (revolveData.length < 0) {
                                Log.i(LOG_TAG, "当前yuv数据异常");
                            } else {//拿到猪
                                //把转换后的YUV420格式的视频帧放到编码器输入缓冲区中
                                Log.i(LOG_TAG, "yuv数据转换成功，当前数据的数据长度为：" + yuvData.length);
                                inputBuffer.limit(revolveData.length);
                                inputBuffer.put(revolveData, 0, yuvData.length);
                                //计算时间戳,配置了但是没有用，所以此处传0也可以
                                pts = computePresentationTime(generateIndex);
                                Log.i(LOG_TAG, "当前时间戳：" + pts);
                                encodeCodec.queueInputBuffer(inputIndex, 0, revolveData.length, pts, 0);
                                generateIndex += 1;
                            }
                        } else {
                            Log.i(LOG_TAG, "没有可用的input 小推车");
                        }
                    }
                } else {
                    if (!isFinishInput) {
                        Log.i(LOG_TAG, "停止录入");
                        int inputIndex = encodeCodec.dequeueInputBuffer(0);//返回当前可用的小推车标号
                        encodeCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isFinishInput = true;
                    }
                }
                //工厂已经把猪运进去了，但是是否加工成火腿肠还是未知的，我们要通过装火腿肠的筐来判断是否已经加工完了
                int outputIndex = encodeCodec.dequeueOutputBuffer(encodeBufferInfo, 10000);//返回当前筐的标记
                switch (outputIndex) {
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.i(LOG_TAG, "输出的format已更改" + encodeCodec.getOutputFormat());
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
                        //将数据读取到outData中
                        byte[] outData = new byte[encodeBufferInfo.size];
                        outputBuffer.get(outData);
                        //当前是初始化编解码器数据,不是媒体数据，sps、pps等初始化数据
                        if (encodeBufferInfo.flags == BUFFER_FLAG_CODEC_CONFIG) {
                            Log.i(LOG_TAG, "配置信息编码完成");
                            configByte = new byte[encodeBufferInfo.size];
                            configByte = outData;
                        } else if (encodeBufferInfo.flags == BUFFER_FLAG_KEY_FRAME) {//当前的数据中包含关键帧数据
                            Log.i(LOG_TAG, "I帧编码完成");
                            //将初始化数据和当前的关键帧数据合并后写入到h264文件中
                            byte[] keyframe = new byte[encodeBufferInfo.size + configByte.length];
                            System.arraycopy(configByte, 0, keyframe, 0, configByte.length);
                            //把编码后的视频帧从编码器输出缓冲区中拷贝出来
                            System.arraycopy(outData, 0, keyframe, configByte.length, outData.length);
                            fos.write(keyframe, 0, keyframe.length);
                        } else {
                            //写到文件中
                            Log.i(LOG_TAG, "非I帧数据编码完成");
                            fos.write(outData, 0, outData.length);
                        }
                        //把筐放回工厂里面
                        encodeCodec.releaseOutputBuffer(outputIndex, false);
                        break;
                }
                if ((encodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.i(LOG_TAG, "表示当前编解码已经完事了");
                    inOutFinish = true;
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (encodeCodec != null) {
                encodeCodec.release();
            }
            if (fos != null) {
                try {
                    fos.flush();
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void capture(byte[] data) {
        File img = new File(demo6Activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "sdf.jpg");
        try {
            img.createNewFile();
            FileOutputStream fos = new FileOutputStream(img);
            YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, height, width, null);
            yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()),
                    100, fos);
        } catch (Exception e) {


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
