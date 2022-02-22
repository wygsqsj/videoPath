package com.wish.videopath.demo11;

/**
 * 音乐播放中得各种状态回调，例如播放时间
 */
public interface PlayerListener {
    void onLoad(boolean load);

    void onCurrentTime(int curr, int total);

    void onError(int code, String msg);

    void onPause(boolean pause);

    void onDBValue(int db);

    void onComplete();

    String onNext();
}
