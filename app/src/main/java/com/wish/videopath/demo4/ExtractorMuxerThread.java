package com.wish.videopath.demo4;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.RequiresApi;

import com.wish.videopath.R;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 类名称：ExtractorMuxerThread
 * 类描述：
 * 将mp4拆分成一个MP4和aac
 */
public class ExtractorMuxerThread extends Thread {
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
        MediaExtractor videoExtractor = null;//读取
        MediaMuxer videoMuxer = null;//输出视频
        MediaMuxer audioMuxer = null;//输出音频

        File outputFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "mixer.mp4");
        File outputAudioFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "mixer.aac");
        String outputFilePath = outputFile.getAbsolutePath();
        String outputAudioFilePath = outputAudioFile.getAbsolutePath();

        try {
            outputFile.createNewFile();
            outputAudioFile.createNewFile();
            //读取MP4
            videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(context.getResources().openRawResourceFd(R.raw.test4));

            //获取通道
            int videoIndex = -1;
            int audioIndex = -1;
            MediaFormat videoTrackFormat = null;
            MediaFormat audioTrackFormat = null;
            int trackCount = videoExtractor.getTrackCount();//得到源文件通道数
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = videoExtractor.getTrackFormat(i);
                if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    videoTrackFormat = format; //获取指定（index）的通道格式
                    videoIndex = i;
                } else if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                    audioTrackFormat = format; //获取指定（index）的通道格式
                    audioIndex = i;
                }
            }

            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();

            //path:输出文件的名称 format:输出文件的格式；当前只支持MP4格式；
            videoMuxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            audioMuxer = new MediaMuxer(outputAudioFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            //添加通道
            int videoTrackIndex = videoMuxer.addTrack(videoTrackFormat);
            videoMuxer.start();//开始合成video
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1024*8);

            //读取视频，写入新的视频文件
            videoExtractor.selectTrack(videoIndex);
            videoBufferInfo.presentationTimeUs = 0;
            while (true) {
                //把指定通道中的数据按偏移量读取到ByteBuffer中
                byteBuffer.clear();
                int data = videoExtractor.readSampleData(byteBuffer, 0);
                if (data < 0) {
                    break;
                }
                videoBufferInfo.size = data;
                videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                videoBufferInfo.offset = 0;
                videoBufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
                //把ByteBuffer中的数据写入到在视频构造器设置的文件中
                videoMuxer.writeSampleData(videoTrackIndex, byteBuffer, videoBufferInfo);

                videoExtractor.advance();//读取下一帧数据
            }

            int audioTrackIndex = audioMuxer.addTrack(audioTrackFormat);
            audioMuxer.start();//开始合成audio
            //读取音频，写入新的音频频文件
            videoExtractor.unselectTrack(videoIndex);
            videoExtractor.selectTrack(audioIndex);
            videoBufferInfo.presentationTimeUs = 0;
            while (true) {
                byteBuffer.clear();
                //把指定通道中的数据按偏移量读取到ByteBuffer中
                int data = videoExtractor.readSampleData(byteBuffer, 0);
                if (data < 0) {
                    break;
                }
                videoBufferInfo.size = data;
                videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                videoBufferInfo.offset = 0;
                videoBufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
                //把ByteBuffer中的数据写入到在音频构造器设置的文件中
                audioMuxer.writeSampleData(audioTrackIndex, byteBuffer, videoBufferInfo);
                videoExtractor.advance();//读取下一帧数据
            }


        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (videoMuxer != null) {
                videoMuxer.stop();
                videoMuxer.release();
            }
            if (audioMuxer != null) {
                audioMuxer.stop();
                audioMuxer.release();
            }
            if (videoExtractor != null) {
                videoExtractor.release();
            }
        }
    }
}
