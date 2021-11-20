package com.wish.videopath.demo5;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.wish.videopath.R;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.wish.videopath.MainActivity.LOG_TAG;

public class DecodeAACAsyn extends Thread {

    private Context context;
    private MediaFormat audioFormat;
    private File pcmFile;
    private FileOutputStream fos = null;

    private MediaCodec decodeCodec = null;
    private Queue<byte[]> mOutDataQueue = new LinkedBlockingQueue<>();
    private Queue<Integer> mInputDataQueue = new LinkedBlockingQueue<>();

    private MediaExtractor audioExtractor = new MediaExtractor();

    private Handler mHandler;

    private Runnable outRunnable = () -> {
        try {
            Log.e(LOG_TAG, "outRunnable,当前线程： " + Thread.currentThread().getName());
            byte[] pcmData = mOutDataQueue.poll();
            if (pcmData == null) {
                return;
            }
            Log.e(LOG_TAG, "Handler回调收到,当前数据大小：" + pcmData.length);
            //装车
            fos.write(pcmData);//数据写入文件中
            fos.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    };

    private Runnable inputRunnable = () -> {
        try {
            Log.e(LOG_TAG, "inputRunnable,当前线程： " + Thread.currentThread().getName());

            Integer index = mInputDataQueue.poll();
            if (index == null) {
                return;
            }

            ByteBuffer buffer;
            if (Build.VERSION.SDK_INT >= 21) {
                buffer = decodeCodec.getInputBuffer(index);
            } else {
                buffer = decodeCodec.getInputBuffers()[index];
            }
            int sampleSize = audioExtractor.readSampleData(buffer, 0);
            if (sampleSize < 0) {
                Log.i(LOG_TAG, "当前音频已经读取完了");
                decodeCodec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                Log.i(LOG_TAG, "读取到了音频数据，当前音频数据的数据长度为：" + sampleSize);
                long sampleTime = audioExtractor.getSampleTime();
                decodeCodec.queueInputBuffer(index, 0, sampleSize, sampleTime, 0);
                audioExtractor.advance();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    };

    public DecodeAACAsyn(Demo5Activity demo5Activity) {
        context = demo5Activity;
        pcmFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "demo5a.pcm");
        try {
            pcmFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void run() {
        super.run();
        Looper.prepare();
        mHandler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(@NonNull @NotNull Message msg) {
                super.handleMessage(msg);
                if (msg.what == 0) {
                    destory();
                }
            }
        };
        Log.e(LOG_TAG, "Decode,当前线程： " + Thread.currentThread().getName());
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
            /*
             * 通过回调方式来进行数据的编码，比刚才手动调用方式更合理，回调运行在主线程，记得切换线程
             */
            decodeCodec.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                    Log.i(LOG_TAG, "异步回调，onInputBufferAvailable,当前index:" + index);
                    mInputDataQueue.offer(index);
                    mHandler.post(inputRunnable);

                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                    Log.i(LOG_TAG, "异步回调，onOutputBufferAvailable,当前index:" + index);
                    Log.i(LOG_TAG, "获取到解码后的数据了，当前解析后的数据长度为：" + info.size);
                    //拿到当前装满火腿肠的筐
                    ByteBuffer outputBuffer;
                    if (Build.VERSION.SDK_INT >= 21) {
                        outputBuffer = codec.getOutputBuffer(index);
                    } else {
                        outputBuffer = codec.getOutputBuffers()[index];
                    }
                    //将火腿肠放到新的容器里，便于后期装车运走
                    byte[] pcmData = new byte[info.size];
                    outputBuffer.get(pcmData);//写入到字节数组中
                    outputBuffer.clear();//清空当前筐
                    //将装猪的数据放到队列里面，通过handler发送消息在子线程装入数据
                    mOutDataQueue.offer(pcmData);
                    mHandler.post(outRunnable);
                    //把筐放回工厂里面
                    codec.releaseOutputBuffer(index, false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        mHandler.sendEmptyMessage(0);
                    }
                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                    Log.i(LOG_TAG, "异步回调，onError" + e.toString());
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    Log.i(LOG_TAG, "异步回调，onOutputFormatChanged" + format.toString());

                }
            });
            //先配置callBack，再配置config；数据格式，surface用来渲染解析出来的数据;加密用的对象；标志 encode ：1 decode：0
            decodeCodec.configure(audioFormat, null, null, 0);

            //启动解码
            decodeCodec.start();

        } catch (IOException e) {
            e.printStackTrace();
        }

        Looper.loop();
    }

    public void destory() {
        Log.i(LOG_TAG, "销毁资源");
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
