package com.wish.videopath.demo10;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import com.wish.videopath.R;

/**
 * FFmpeg得基本使用
 */
public class FFmpegActivity extends AppCompatActivity {

    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ffmpeg);
        TextView text = findViewById(R.id.tvffmpeg);
        text.setText(getText());
    }

    public native String getText();
}