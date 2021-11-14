package com.wish.videopath.demo6;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import androidx.annotation.RequiresApi;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * 读取H264数据进行解析显示到surface中
 * h264为定长和变长编码混合的，渲染需要的数据是通过解析变长编码来获取到的
 * 从文件中读取数据，实时计算分隔符的位置
 */

public class H264DecodeThreadV2 extends Thread {

    private Demo6Activity demo6Activity;
    private int width = 1280;
    private int height = 720;
    private int framerate = 30;//帧率
    private int biterate = 8500 * 1000;
    private Surface surface;
    private FileInputStream fis;
    private DataInputStream is;
    private File h264File;
    private MediaCodec decodeCodec;
    private boolean hasVideo;
    private boolean isStop;

    public H264DecodeThreadV2(Demo6Activity demo6Activity, int width, int height, int framerate, int biterate, Surface surface) {
        this.demo6Activity = demo6Activity;
        this.width = width;
        this.height = height;
        this.framerate = framerate;
        this.biterate = biterate;
        this.surface = surface;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void run() {
        super.run();
        try {
            h264File = new File(demo6Activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES), Demo6Activity.FILENAME264);
            fis = new FileInputStream(h264File);
            is = new DataInputStream(fis);
            //初始化codec
            decodeCodec = MediaCodec.createDecoderByType("video/avc");
            //初始化编码器
            final MediaFormat mediaformat = MediaFormat.createVideoFormat("video/avc", width, height);

            //设置帧率
            mediaformat.setInteger(MediaFormat.KEY_FRAME_RATE, biterate);
            decodeCodec.configure(mediaformat, surface, null, 0);
            decodeCodec.start();

            //开始索引和当前得索引
            long startIndex = 1;
            long nextIndex = 0;
            int totalSize = fis.available();
            boolean isFinish = true;
            Log.i(LOG_TAG, "当前的数据大小" + totalSize);
            RandomAccessFile in = new RandomAccessFile(h264File, "r");

            Log.i(LOG_TAG, "当前的指针" + in.getFilePointer());

            MediaCodec.BufferInfo decodeBufferInfo = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息

            while (true) {
                Log.i(LOG_TAG, "当前的startIndex" + startIndex);
                if (startIndex >= totalSize) {
                    Log.i(LOG_TAG, "到末尾了" + nextIndex);
                    break;
                }
                //获取codec输入数据载体
                int inputIndex = decodeCodec.dequeueInputBuffer(10000);
                if (inputIndex != -1) {
                    Log.i(LOG_TAG, "找到了input 小推车" + inputIndex);
                    //跳过分隔符
                    in.skipBytes(1);
                    Log.i(LOG_TAG, "跳过后的指针" + in.getFilePointer());
                    //找到一个NALU单元
                    while (true) {
                        if (findByFrame(in)) {//找到分隔符
                            nextIndex = in.getFilePointer();
                            Log.i(LOG_TAG, "当前的nextIndex" + nextIndex);
                            in.seek(startIndex);
                            break;
                        }
                    }

                    int dataLength = (int) (nextIndex - startIndex);
                    byte[] bytes = new byte[dataLength];
                    in.read(bytes, 0, dataLength);
                    Log.i(LOG_TAG, "当前NALU长度" + dataLength);
                    ByteBuffer inputBuffer = decodeCodec.getInputBuffer(inputIndex);
                    inputBuffer.clear();
                    //把下一帧放入解码缓存
                    inputBuffer.put(bytes, 0, dataLength);
                    decodeCodec.queueInputBuffer(inputIndex, 0, dataLength, 0, 0);
                    //下一帧获取从当前末尾开始
                    Log.i(LOG_TAG, "当前的指针" + in.getFilePointer());
                    startIndex = nextIndex;
                } else {
                    Log.i(LOG_TAG, "没有可用的input 小推车");
                }

                //获取codec解码好的数据
                int outputIndex = decodeCodec.dequeueOutputBuffer(decodeBufferInfo, 10000);//返回当前筐的标记
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
                        //config时已经把surface传给了codec，释放一下，第二个参数指定为true，codec自动会渲染到surface中
                        decodeCodec.releaseOutputBuffer(outputIndex, true);
                        break;
                }


            }
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

    //获取当前分隔符的位置，即0x01前的一位，目的是为了找出NAL单元的末尾
    private boolean findByFrame(RandomAccessFile in) throws IOException {
        if (in.read() == 0 && in.read() == 0 && in.read() == 0 && in.read() == 1) {
            in.seek(in.getFilePointer() - 4);
            return true;
        }
        if (in.getFilePointer() >= in.length() - 1) {
            return true;
        }
        return false;
    }
}
