package com.wish.videopath.demo5;

import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.wish.videopath.R;

/**
 * MediaCodec AAC编解码,将aac解析成pcm再编码回aac
 */
public class Demo5Activity extends AppCompatActivity {

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo5);
    }

    // pcm编码成aac
    public void encode(View view) {
        //IO流写入
        new EncodeAACThread(this).start();
//        使用MideaMuxer写入aac
//        new EncodeAACThreadV2(this).start();
    }

    //aac解码成pcm
    public void decode(View view) {
        new DecodeAACThread(this).start();
//        new DecodeAACAsyn(this).start();
    }

    //将两个音频合并成一个音频
    public void mixing(View view) {

        new MixingThread(this).start();
    }
}