package com.wish.videopath.demo11;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Toast;

import com.wish.videopath.R;
import com.wish.videopath.util.FileUtil;

/**
 * 使用ffmpeg解码MP3交给opensl es播放
 */
public class MusicActivity extends AppCompatActivity {

    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music);
        Toast.makeText(this, getNative(), Toast.LENGTH_LONG).show();

        String url = FileUtil.copyAssFileToSD(this, "渡口.mp3");
    }

    private native String getNative();
}