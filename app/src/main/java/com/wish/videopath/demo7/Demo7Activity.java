package com.wish.videopath.demo7;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.view.TextureView;

import com.wish.videopath.R;

/**
 * Camera2相机获取数据保存成h264码流
 */
public class Demo7Activity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private TextureView textureView;
    private Camera2Helper helper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo7);
        textureView = findViewById(R.id.textView);
        textureView.setSurfaceTextureListener(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        helper = new Camera2Helper(this, textureView);
        helper.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        helper.closeCamera();
    }
}