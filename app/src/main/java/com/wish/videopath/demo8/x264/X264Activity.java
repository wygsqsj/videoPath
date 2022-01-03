package com.wish.videopath.demo8.x264;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.view.TextureView;

import com.wish.videopath.databinding.ActivityX264Binding;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import static com.wish.videopath.demo8.Demo8Activity.RTMPURL;


/**
 * 软编实现推流到rtmp服务器
 */
public class X264Activity extends AppCompatActivity implements TextureView.SurfaceTextureListener {


    private ActivityX264Binding binding;
    private VideoHelper helper;
    private AudioHelper audioHelper;
    private LivePush livePush;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityX264Binding.inflate(getLayoutInflater());
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


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startRTMP() {
        //发送层
        livePush = new LivePush();

        //视频数据
        helper = new VideoHelper(this, binding.textureView, livePush);
        helper.start();

        //音频数据
        audioHelper = new AudioHelper(livePush);
        audioHelper.startAudio();

        livePush.startLive(RTMPURL);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (helper != null) {
            helper.closeCamera();
        }

        if (audioHelper != null) {
            audioHelper.stopAudio();
        }

        if (livePush != null) {
            livePush.stopLive();
        }
    }
}