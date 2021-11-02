package com.wish.videopath.demo5;

import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.wish.videopath.R;

/**
 * MediaCodec AAC编解码,将aac解析成pcm再编码成mp3
 * 通过M
 */
public class Demo5Activity extends AppCompatActivity {

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo5);
    }

    // pcm编码成mp3
    public void encode(View view) {
    }

    //aac解码成pcm
    public void decode(View view) {
//        new DecodeAACThread(this).start();
        new DecodeAACAsyn(this).start();
    }
}