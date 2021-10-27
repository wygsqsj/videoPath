package com.wish.videopath.demo4;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.wish.videopath.R;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class Demo4Activity extends AppCompatActivity {

    private int mVideoTrack, mAudioTrack;
    private MediaFormat mAudioFormat, mVideoFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo4);
    }

    //分离视频
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void extractor(View view) {
        /*MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(getResources().openRawResourceFd(R.raw.test4));
            int count = extractor.getTrackCount();//轨道数量
            Log.e(LOG_TAG, "轨道数量 = " + count);
            for (int i = 0; i < count; i++) {
                MediaFormat mediaFormat = extractor.getTrackFormat(i);
                String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                Log.e(LOG_TAG, i + "编号通道格式 = " + mime);
                //视频轨道
                if (mime.contains("video")) {
                    mVideoTrack = i;
                    mVideoFormat = mediaFormat;
                } else {//音频轨道
                    mAudioTrack = i;
                    mAudioFormat = mediaFormat;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }*/
        new ExtractorMuxerThread(this).start();
    }

    //合并视频和音频
    public void muxer(View view) {

    }
}

class ExtractorMuxerThread extends Thread {
    Context context;

    public ExtractorMuxerThread(Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        super.run();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mixer();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void mixer() {
        MediaExtractor videoExtractor = null;
//        MediaExtractor audioExtractor = null;
        MediaMuxer mixMediaMuxer = null;
        String outputVideoFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/haha.mp4";
//        String outputFilePath = Environment.getExternalStorageState() + "/mixer.mp4";
        File outputFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "mixer.mp4");
        String outputFilePath = outputFile.getAbsolutePath();

        try {
            outputFile.createNewFile();

            videoExtractor = new MediaExtractor();
//            videoExtractor.setDataSource(outputVideoFilePath);
            videoExtractor.setDataSource(context.getResources().openRawResourceFd(R.raw.test4));
            int videoIndex = -1;
            MediaFormat videoTrackFormat = null;
            int trackCount = videoExtractor.getTrackCount();//得到源文件通道数
            for (int i = 0; i < trackCount; i++) {
                videoTrackFormat = videoExtractor.getTrackFormat(i); //获取指定（index）的通道格式
                if (videoTrackFormat.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    videoIndex = i;
                    break;
                }
            }

         /*   audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(outputAudioFilePath);*/
//            int audioIndex = -1;
//            MediaFormat audioTrackFormat = null;
//            trackCount = audioExtractor.getTrackCount();
//            for (int i = 0; i < trackCount; i++) {
//                audioTrackFormat = audioExtractor.getTrackFormat(i);
//                if (audioTrackFormat.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
//                    audioIndex = i;
//                    break;
//                }
//            }

            videoExtractor.selectTrack(videoIndex);
//            audioExtractor.selectTrack(audioIndex);

            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

            //path:输出文件的名称 format:输出文件的格式；当前只支持MP4格式；
            mixMediaMuxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            //添加通道
            int videoTrackIndex = mixMediaMuxer.addTrack(videoTrackFormat);
//            int audioTrackIndex = mixMediaMuxer.addTrack(audioTrackFormat);

            mixMediaMuxer.start();//开始合成文件

            ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1024);
            long videotime;
            long audiotime;

            //获取视频每一帧之间的间隔
            {
                videoExtractor.readSampleData(byteBuffer, 0);
                if (videoExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
                    videoExtractor.advance();//读取下一帧数据
                }

                videoExtractor.readSampleData(byteBuffer, 0);
                long sampleTime = videoExtractor.getSampleTime();
                videoExtractor.advance();//读取下一帧数据

                videoExtractor.readSampleData(byteBuffer, 0);
                long sampleTime1 = videoExtractor.getSampleTime();
                videoExtractor.advance();//读取下一帧数据

                videotime = Math.abs(sampleTime - sampleTime1);
            }

            //获取音频每一帧之间的间隔
           /* {
                audioExtractor.readSampleData(byteBuffer, 0);
                if (audioExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
                    audioExtractor.advance();//读取下一帧数据
                }

                audioExtractor.readSampleData(byteBuffer, 0);
                long sampleTime = audioExtractor.getSampleTime();
                audioExtractor.advance();//读取下一帧数据

                audioExtractor.readSampleData(byteBuffer, 0);
                long sampleTime1 = audioExtractor.getSampleTime();
                audioExtractor.advance();//读取下一帧数据
                audiotime = Math.abs(sampleTime - sampleTime1);
            }*/

            videoExtractor.unselectTrack(videoIndex);
            videoExtractor.selectTrack(videoIndex);

            while (true) {
                //把指定通道中的数据按偏移量读取到ByteBuffer中
                int data = videoExtractor.readSampleData(byteBuffer, 0);
                if (data < 0) {
                    break;
                }
                videoBufferInfo.size = data;
                videoBufferInfo.presentationTimeUs += videotime;
                videoBufferInfo.offset = 0;
                videoBufferInfo.flags = videoExtractor.getSampleFlags();
                //把ByteBuffer中的数据写入到在构造器设置的文件中
                mixMediaMuxer.writeSampleData(videoTrackIndex, byteBuffer, videoBufferInfo);
                videoExtractor.advance();//读取下一帧数据
            }

           /* while (true) {
                int data = audioExtractor.readSampleData(byteBuffer, 0);
                if (data < 0) {
                    break;
                }
                audioBufferInfo.size = data;
                audioBufferInfo.presentationTimeUs += audiotime;
                audioBufferInfo.offset = 0;
                audioBufferInfo.flags = audioExtractor.getSampleFlags();

                mixMediaMuxer.writeSampleData(audioTrackIndex, byteBuffer, audioBufferInfo);
                audioExtractor.advance();
            }*/
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mixMediaMuxer != null) {
                mixMediaMuxer.stop();
                mixMediaMuxer.release();
            }
            if (videoExtractor != null) {
                videoExtractor.release();
            }
           /* if (audioExtractor != null) {
                audioExtractor.release();
            }*/
        }
    }
}