package com.wish.videopath;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.wish.videopath.demo2.Demo2Activity;
import com.wish.videopath.demo3.Demo3Activity;
import com.wish.videopath.demo4.Demo4Activity;
import com.wish.videopath.demo5.Demo5Activity;

public class MainActivity extends AppCompatActivity {

    public static String LOG_TAG = "音视频";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        if (Build.VERSION.SDK_INT >= 23) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.RECORD_AUDIO,
                    }, 666);
        }
    }

    public void startUp1(View view) {
        Intent intent = new Intent(this, Demo2Activity.class);
        startActivity(intent);
    }

    public void startUp2(View view) {
        Intent intent = new Intent(this, Demo4Activity.class);
        startActivity(intent);
    }

    public void startUp3(View view) {
        Intent intent = new Intent(this, Demo5Activity.class);
        startActivity(intent);
    }

    public void startUp4(View view) {
        Intent intent = new Intent(this, Demo5Activity.class);
        startActivity(intent);
    }
}