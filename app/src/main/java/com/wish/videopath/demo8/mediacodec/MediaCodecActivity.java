package com.wish.videopath.demo8.mediacodec;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.view.TextureView;

import com.wish.videopath.databinding.ActivityMediaCodecBinding;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import static com.wish.videopath.demo8.Demo8Activity.RTMPURL;

/**
 * 硬编解码实现推流到rtmp服务器
 */
public class MediaCodecActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {


    private Camera2Helper helper;

    private ActivityMediaCodecBinding binding;
    private ScreenLive screenLive;
    private AudioCodec audioCodec;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMediaCodecBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (Build.VERSION.SDK_INT >= 23) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO,
                        }, 666);
            } else {
                binding.textureView.setSurfaceTextureListener(this);
            }
        } else {
            binding.textureView.setSurfaceTextureListener(this);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        startRTMP();
    }


    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (helper != null) {
            helper.closeCamera();
        }

        if (audioCodec != null) {
            audioCodec.stopAudio();
        }

        if (screenLive != null) {
            screenLive.stopLive();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        binding.textureView.setSurfaceTextureListener(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startRTMP() {
        //发送层
        screenLive = new ScreenLive();
        screenLive.startLive(RTMPURL);
        //视频数据
        helper = new Camera2Helper(this, binding.textureView, screenLive);
        helper.start();
//        //音频数据
        audioCodec = new AudioCodec();
        audioCodec.startLive(screenLive);
    }
}