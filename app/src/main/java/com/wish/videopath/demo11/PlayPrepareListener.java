package com.wish.videopath.demo11;

/**
 * 当native层初始化完成播放器后回调给java，此时所有注册过此监听器的观察者都可以收到初始化完成的消息
 */
public interface PlayPrepareListener {

    void onPrepare();

}
