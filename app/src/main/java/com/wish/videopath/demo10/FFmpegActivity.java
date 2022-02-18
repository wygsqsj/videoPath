package com.wish.videopath.demo10;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.wish.videopath.R;
import com.wish.videopath.util.FileUtil;

import java.io.File;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * FFmpeg得基本使用
 */
public class FFmpegActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    static {
        System.loadLibrary("native-lib");
    }

    private Surface surface;
    private SurfaceView surfaceview;
    private AudioTrack audioTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ffmpeg);
        surfaceview = findViewById(R.id.ffmpegSurface);
        surfaceview.getHolder().addCallback(this);
    }


    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        this.surface = holder.getSurface();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        if (surface != null) {
            surface = null;
        }
    }

    //点击播放
    public void play(View view) {
        String url = FileUtil.copyAssFileToSD(this, "demo4.mp4");
//        String url = "http://vfx.mtime.cn/Video/2019/03/09/mp4/190309153658147087.mp4";
        play(url, surface);
    }

    //初始化音频播放器,由natice层进行调用
    private void initAudioTrack(int sampleRate, int channel) {
        Log.i(LOG_TAG, "初始化AudioTrack");
        int minBuffer = AudioTrack.getMinBufferSize(sampleRate, channel, AudioFormat.ENCODING_PCM_16BIT);

        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channel == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer,
                AudioTrack.MODE_STREAM
        );

        audioTrack.play();
    }


    //由natice层进行调用传输数据给AudioTrack播放
    private void playAudio(byte[] buffer, int length) {
        if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.write(buffer, 0, length);
        }
    }

    public native int play(String url, Surface surface);


}