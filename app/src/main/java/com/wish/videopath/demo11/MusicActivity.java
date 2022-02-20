package com.wish.videopath.demo11;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.wish.videopath.R;
import com.wish.videopath.util.FileUtil;

/**
 * 使用ffmpeg解码MP3交给opensl es播放
 */
public class MusicActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music);
        String url = FileUtil.copyAssFileToSD(this, "渡口.mp3");
        FFPlay play = new FFPlay();
        play.setAudioUrl(url);
        play.initPlay();
        //设置初始化监听
        play.setListener(() -> {
            play.startPlay();
        });
    }


}