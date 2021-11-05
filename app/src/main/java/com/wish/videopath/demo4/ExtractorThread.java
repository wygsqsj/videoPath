package com.wish.videopath.demo4;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 类名称：ExtractorMuxerThread
 * 类描述：
 * 将mp4和aac合并成一个MP4
 */
public class ExtractorThread extends Thread {
    Context context;

    public ExtractorThread(Context context) {
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
        MediaExtractor videoExtractor = null;//读取视频
        MediaExtractor audioExtractor = null;//读取音频
        MediaMuxer mp4Muxer = null;//合并mp4

        File videoFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "mixer.mp4");
        File audioFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "mixer.aac");
        File outputFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "newMixer.mp4");
        String videoPath = videoFile.getAbsolutePath();
        String audioPath = audioFile.getAbsolutePath();
        String outputPath = outputFile.getAbsolutePath();

        try {
            outputFile.createNewFile();

            //获取视频通道
            videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(videoPath);
            int videoIndex = -1;
            MediaFormat videoTrackFormat = null;
            int trackCount = videoExtractor.getTrackCount();//得到源文件通道数
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = videoExtractor.getTrackFormat(i);
                if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    videoTrackFormat = format; //获取指定（index）的通道格式
                    videoIndex = i;
                }
            }
            //获取音频通道
            audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(audioPath);
            int audioIndex = -1;
            MediaFormat audioTrackFormat = null;
            trackCount = audioExtractor.getTrackCount();
            for (int i = 0; i < trackCount; i++) {
                audioTrackFormat = audioExtractor.getTrackFormat(i);
                if (audioTrackFormat.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                    audioIndex = i;
                    break;
                }
            }

            videoExtractor.selectTrack(videoIndex);
            audioExtractor.selectTrack(audioIndex);


            //path:输出文件的名称 format:输出文件的格式；当前只支持MP4格式；
            mp4Muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            //添加通道,要添加视频和音频两个通道
            int audioTrackIndex = mp4Muxer.addTrack(audioTrackFormat);
            int videoTrackIndex = mp4Muxer.addTrack(videoTrackFormat);

            mp4Muxer.start();//开始合成video

            ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1024 * 8);
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            //读取视频，写入新的视频文件
            videoExtractor.unselectTrack(videoIndex);
            videoExtractor.selectTrack(videoIndex);
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
                //把ByteBuffer中的数据写入到在视频构造器设置的文件中
                mp4Muxer.writeSampleData(videoTrackIndex, byteBuffer, videoBufferInfo);
                videoExtractor.advance();//读取下一帧数据
            }
            audioExtractor.unselectTrack(audioIndex);
            audioExtractor.selectTrack(audioIndex);
            //读取音频，写入新的音频频文件
            while (true) {
                byteBuffer.clear();
                //把指定通道中的数据按偏移量读取到ByteBuffer中
                int data = audioExtractor.readSampleData(byteBuffer, 0);
                if (data < 0) {
                    break;
                }
                audioBufferInfo.size = data;
                audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                audioBufferInfo.offset = 0;
                audioBufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
                //把ByteBuffer中的数据写入到在音频构造器设置的文件中
                mp4Muxer.writeSampleData(audioTrackIndex, byteBuffer, audioBufferInfo);
                audioExtractor.advance();//读取下一帧数据
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mp4Muxer != null) {
                mp4Muxer.stop();
                mp4Muxer.release();
            }
            if (audioExtractor != null) {
                audioExtractor.release();
            }
            if (videoExtractor != null) {
                videoExtractor.release();
            }
        }
    }
}
