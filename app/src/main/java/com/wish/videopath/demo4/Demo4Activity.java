package com.wish.videopath.demo4;

import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.wish.videopath.R;

/**
 * MediaExtractor 和 MediaMuxer API，知道如何解析和封装 mp4 文件
 */
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
        new ExtractorMuxerThread(this).start();
    }

    //合并视频和音频
    public void muxer(View view) {
        new ExtractorThread(this).start();
    }
}