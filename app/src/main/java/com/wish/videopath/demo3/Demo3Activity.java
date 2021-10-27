package com.wish.videopath.demo3;

import android.Manifest;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.wish.videopath.R;

/**
 * 在 Android 平台使用 Camera API 进行视频的采集
 * ，分别使用 SurfaceView、TextureView 来预览 Camera 数据，取到 NV21 的数据回调
 */
public class Demo3Activity extends AppCompatActivity {

    private Camera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo3);
        CameraTextureView textureView = findViewById(R.id.cameraView);
        if (Build.VERSION.SDK_INT >= 23) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, 666);
        }
    }

    private void initCamera() {
        try {
            camera = Camera.open();
            // 打开摄像头并将展示方向旋转90度
            camera.setDisplayOrientation(90);
        } catch (Exception e) {
            Toast.makeText(this, "打开失败！", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

    }

    /**
     * 点击按钮，开启摄像头
     */
    public void startCamera(View view) {
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}