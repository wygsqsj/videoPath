package com.wish.videopath.demo8.mediacodec;

/**
 * RTMP数据包
 */
public class RTMPPacket {
    private byte[] buffer;
    private long tms;//时间戳

    public static final int AUDIO_TYPE = 0;
    public static final int VIDEO_TYPE = 1;
    public static final int AUDIO_HEAD_TYPE = 2;

    private int type;

    public RTMPPacket() {
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public long getTms() {
        return tms;
    }

    public void setBuffer(byte[] newData) {
        this.buffer = newData;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setTms(long tms) {
        this.tms = tms;
    }
}
