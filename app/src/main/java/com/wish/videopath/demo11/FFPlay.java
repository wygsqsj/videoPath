package com.wish.videopath.demo11;

import android.text.TextUtils;
import android.util.Log;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * native 音乐播放
 */
public class FFPlay {

    static {
        System.loadLibrary("native-lib");
    }

    //音频地址
    private String audioUrl;

    //native设置给java层的回调
    private PlayPrepareListener listener;

    private PlayerListener playerListener;

    public void setPlayerListener(PlayerListener playerListener) {
        this.playerListener = playerListener;
    }

    //由native调用
    public void setListener(PlayPrepareListener listener) {
        this.listener = listener;
    }

    //由native调用,当初始化ffmpeg和opensl后调用
    public void onCallPrepared() {
        Log.i(LOG_TAG, "native回调java,已经实例化好ffmpeg播放器，");
        if (listener != null) {
            listener.onPrepare();
        }
    }

    public void onPlayTimeCallBack(int curr, int total) {
        if (playerListener != null) {
            playerListener.onCurrentTime(curr, total);
        }
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    //开始调用底层初始化
    public void initPlay() {
        if (TextUtils.isEmpty(audioUrl)) {
            return;
        }
        //开启线程进行初始化
        new Thread(() -> initAudio(audioUrl)).start();
    }

    //播放音频，此方法必须在native初始化完成后进行调用才能生效
    public void startPlay() {
        //开启线程进行初始化
        new Thread(() -> playAudio()).start();
    }

    //调整转到对应位置进行播放
    public void seekTo(int position) {
        seekToSecds(position);
    }


    public void pauseAudio() {
        n_pause();
    }

    public void resumeAudio() {
        n_resume();
    }

    public void centerAudio() {
        setMute(0);
    }

    public void rightChannelAudio() {
        setMute(1);
    }

    public void leftChannelAudio() {
        setMute(2);
    }

    public void setVolume(int volume) {
        n_setVolume(volume);
    }

    private native void initAudio(String url);

    private native void playAudio();

    private native void seekToSecds(int secds);

    private native void n_pause();

    private native void n_resume();

    private native void setMute(int channel);

    private native void n_setVolume(int volume);

}
