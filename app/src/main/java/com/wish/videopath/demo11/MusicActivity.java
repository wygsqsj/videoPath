package com.wish.videopath.demo11;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.wish.videopath.R;
import com.wish.videopath.util.FileUtil;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * 使用ffmpeg解码MP3交给opensl es播放
 */
public class MusicActivity extends AppCompatActivity implements PlayerListener {

    private Button btnPlay;
    private TextView currentTime, totalTime;
    private SeekBar audioSeekBar;
    private int position;
    private FFPlay play;
    private int total;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music);

        btnPlay = findViewById(R.id.btnPlay);
        currentTime = findViewById(R.id.currentTime);
        totalTime = findViewById(R.id.totalTime);
        audioSeekBar = findViewById(R.id.seekAudio);

        audioSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
//                position = total * i / 100;
                position = i;
                currentTime.setText(getTime(i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekTo();
            }
        });
    }

    //拖动seekBar
    private void seekTo() {
        Log.i(LOG_TAG, "当前seek 位置：" + position);
        if (play != null) {
            play.seekTo(position);
        }
    }

    //播放
    public void playAudio(View view) {
        if ("暂停".equals(btnPlay.getText().toString())) {

        } else {
            String url = FileUtil.copyAssFileToSD(this, "渡口.mp3");
            play = new FFPlay();
            play.setPlayerListener(this);
            play.setAudioUrl(url);
            play.initPlay();
            //设置初始化监听
            play.setListener(() -> {
                runOnUiThread(() -> btnPlay.setText("暂停"));
                play.startPlay();
            });
        }
    }

    //下一首
    public void startNext(View view) {

    }

    //上一首
    public void startPrevious(View view) {

    }

    @Override
    public void onLoad(boolean load) {

    }

    //播放回调
    @Override
    public void onCurrentTime(int curr, int total) {
        runOnUiThread(() -> {
            totalTime.setText(getTime(total));
            currentTime.setText(getTime(curr));

            this.total = total;
            audioSeekBar.setProgress(curr);
            audioSeekBar.setMax(total);
        });
    }

    @Override
    public void onError(int code, String msg) {

    }

    @Override
    public void onPause(boolean pause) {

    }

    @Override
    public void onDBValue(int db) {

    }

    @Override
    public void onComplete() {

    }

    @Override
    public String onNext() {
        return null;
    }

    //秒转换分秒
    private String getTime(int seconds) {
        int m = (seconds % 3600) / 60;
        int s = (seconds % 3600) % 60;
        StringBuilder stringBuilder = new StringBuilder();
        if (m > 0) {
            stringBuilder.append(m)
                    .append(":");
        } else {
            stringBuilder.append("00:");
        }
        stringBuilder.append(s);
        return stringBuilder.toString();
    }
}