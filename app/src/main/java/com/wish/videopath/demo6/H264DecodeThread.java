package com.wish.videopath.demo6;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Environment;
import android.view.Surface;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * 读取H264数据进行解析显示到surface中
 * h264为定长和变长编码混合的，渲染需要的数据是通过解析变长编码来获取到的
 */

public class H264DecodeThread extends Thread {

    private Demo6Activity demo6Activity;
    private int width = 1280;
    private int height = 720;
    private int framerate = 30;//帧率
    private int biterate = 8500 * 1000;
    private Surface surface;
    private FileInputStream fis;
    private MediaCodec decodeCodec;
    private boolean hasVideo;

    public H264DecodeThread(Demo6Activity demo6Activity, int width, int height, int framerate, int biterate, Surface surface) {
        this.demo6Activity = demo6Activity;
        this.width = width;
        this.height = height;
        this.framerate = framerate;
        this.biterate = biterate;
        this.surface = surface;
    }


    @Override
    public void run() {
        super.run();
        try {
            fis = new FileInputStream(new File(demo6Activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    Demo6Activity.FILENAME264));
            //初始化codec
            decodeCodec = MediaCodec.createDecoderByType("video/avc");
            //初始化编码器
            final MediaFormat mediaformat = MediaFormat.createVideoFormat("video/avc", width, height);
          /*  //获取h264中的pps及sps数据
            byte[] header_sps = {0, 0, 0, 1, 103, 66, 0, 42, (byte) 149, (byte) 168, 30, 0, (byte) 137, (byte) 249, 102, (byte) 224, 32, 32, 32, 64};
            byte[] header_pps = {0, 0, 0, 1, 104, (byte) 206, 60, (byte) 128, 0, 0, 0, 1, 6, (byte) 229, 1, (byte) 151, (byte) 128};
            mediaformat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
            mediaformat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));*/

            //设置帧率
            mediaformat.setInteger(MediaFormat.KEY_FRAME_RATE, biterate);
            decodeCodec.configure(mediaformat, surface, null, 0);
            decodeCodec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long startMs = System.currentTimeMillis();
            long timeoutUs = 10000;
            byte[] marker0 = new byte[]{0, 0, 0, 1};
            byte[] dummyFrame = new byte[]{0x00, 0x00, 0x01, 0x20};
            byte[] streamBuffer = null;
            int bytes_cnt = 0;

        /*    while (true) {
                //所有的猪都运进猪厂后不再添加
                if (hasVideo) {
                    //从fis中获取数据
                    streamBuffer = new byte[1024 * 8];
                    int i = fis.read(streamBuffer, 0, streamBuffer.length);
                    if (i < 0) {
                        hasVideo = false;
                    } else {
                        bytes_cnt = streamBuffer.length;
                        if (bytes_cnt == 0) {
                            streamBuffer = dummyFrame;
                        }
                    }

                    int startIndex = 0;
                    int remaining = bytes_cnt;
                    while (true) {
                        if (remaining == 0 || startIndex >= remaining) {
                            break;
                        }
                        int nextFrameStart = KMPMatch(marker0, streamBuffer, startIndex + 2, remaining);
                        if (nextFrameStart == -1) {
                            nextFrameStart = remaining;
                        } else {
                        }

                        int inIndex = mCodec.dequeueInputBuffer(timeoutUs);
                        if (inIndex >= 0) {
                            ByteBuffer byteBuffer = inputBuffers[inIndex];
                            byteBuffer.clear();
                            byteBuffer.put(streamBuffer, startIndex, nextFrameStart - startIndex);
//在给指定Index的inputbuffer[]填充数据后，调用这个函数把数据传给解码器
                            mCodec.queueInputBuffer(inIndex, 0, nextFrameStart - startIndex, 0, 0);
                            startIndex = nextFrameStart;
                        } else {
                            Log.e(TAG, "aaaaa");
                            continue;
                        }

                        int outIndex = mCodec.dequeueOutputBuffer(info, timeoutUs);
                        if (outIndex >= 0) {
//帧控制是不在这种情况下工作，因为没有PTS H264是可用的
                            while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            boolean doRender = (info.size != 0);
//对outputbuffer的处理完后，调用这个函数把buffer重新返回给codec类。
                            mCodec.releaseOutputBuffer(outIndex, doRender);
                        } else {
                            Log.e(TAG, "bbbb");
                        }
                    }
                }
            }*/
               /*     ByteBuffer[] inputBuffers = decodeCodec.getInputBuffers();//所有的小推车
                    int inputIndex = decodeCodec.dequeueInputBuffer(0);//返回当前可用的小推车标号
                    if (inputIndex != -1) {
                        Log.i(LOG_TAG, "找到了input 小推车" + inputIndex);
                        //将MediaCodec数据取出来放到这个缓冲区里
                        ByteBuffer inputBuffer = inputBuffers[inputIndex];//拿到小推车
                        inputBuffer.clear();//扔出去里面旧的东西
                        //将audioExtractor里面的猪装载到小推车里面
                        int readSize = decodeCodec.readSampleData(inputBuffer, 0);
                        //audioExtractor没猪了，也要告知一下
                        if (readSize < 0) {
                            Log.i(LOG_TAG, "当前264文件已经读取完了");
                            decodeCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            hasVideo = false;
                        } else {//拿到猪
                            Log.i(LOG_TAG, "读取到了音频数据，当前音频数据的数据长度为：" + readSize);
                            //告诉工厂这头猪的小推车序号、猪的大小、猪在这群猪里的排行、屠宰的标志
                            decodeCodec.queueInputBuffer(inputIndex, 0, readSize, audioExtractor.getSampleTime(), 0);
                            //读取音频的下一帧
                            audioExtractor.advance();
                        }
                    } else {
                        Log.i(LOG_TAG, "没有可用的input 小推车");
                    }
                }

                //工厂已经把猪运进去了，但是是否加工成火腿肠还是未知的，我们要通过装火腿肠的筐来判断是否已经加工完了
                int outputIndex = decodeCodec.dequeueOutputBuffer(decodeBufferInfo, 0);//返回当前筐的标记
                switch (outputIndex) {
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.i(LOG_TAG, "输出的format已更改" + decodeCodec.getOutputFormat());
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.i(LOG_TAG, "超时，没获取到");
                        break;
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.i(LOG_TAG, "输出缓冲区已更改");
                        break;
                    default:
                        Log.i(LOG_TAG, "获取到解码后的数据了，当前解析后的数据长度为：" + decodeBufferInfo.size);
                        //获取所有的筐
                        ByteBuffer[] outputBuffers = decodeCodec.getOutputBuffers();
                        //拿到当前装满火腿肠的筐
                        ByteBuffer outputBuffer;
                        if (Build.VERSION.SDK_INT >= 21) {
                            outputBuffer = decodeCodec.getOutputBuffer(outputIndex);
                        } else {
                            outputBuffer = outputBuffers[outputIndex];
                        }
                        //将火腿肠放到新的容器里，便于后期装车运走
                        byte[] pcmData = new byte[decodeBufferInfo.size];
                        outputBuffer.get(pcmData);//写入到字节数组中
                        outputBuffer.clear();//清空当前筐
                        //装车
                        fos.write(pcmData);//数据写入文件中
                        fos.flush();
                        //把数据输出到surface中
                        decodeCodec.releaseOutputBuffer(outputIndex, true);
                        break;
                }
                if ((decodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.i(LOG_TAG, "表示当前编解码已经完事了");
                    break;
                }*/
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static int bitIndex = 0;//当前的下标

    /**
     * 解析哥伦布编码拿到当前对应原始数据
     * 哥伦布编码 以 1 为间隔，1前面的0代表往后堆读取的位数如： 0001 0100，1前面3个0，代表从1往后的3位都是当前类型的数据
     * 0x80 即 1000 0000 ，一个字节8位，取出当前字节里面1所在的位置，例如 0001 0100
     * 从最高位开始遍历，每次1000 0000 向右移动一位，然后 & 当前的字节数据，如果当前字节数据的当前位为1，那么
     * 1&1结果就是1，记录下当前的移动的步数，即1前面有几个0，这就是从1开始往后读取的位数，0001 0100，从1开始
     * 往后读3位得到 1010，高位补0得到数据0000 1010，哥伦布编码规则是原数据+1后再进行编码操作，我们解析出来的1010为
     * 加1后的数值，转换成10进制-1才是原始数据===>10 - 1 = 9，9就是我们最原始的数据了
     */
    private static int getDecodeOriginData(byte[] bytes) {
        int length = 0;//0的位数
        //每个字节8 位
        while (bitIndex < bytes.length * 8) {
            //找到当前下标在字节中的位置
            if ((bytes[bitIndex / 8] & (0x80 >> (bitIndex % 8))) != 0) {
                break;
            } else {
                //当前位是0，继续右移，并记录
                bitIndex++;
                length++;
            }
        }
        /**
         * 2进制转换10进制的值
         * 其实就是倒换，2进制从高位往低位走，10进制从低位往高位走
         * 比如原始的二级制数是 1101
         * 10进制转换：
         * 第一次  0001  1   dev<<1 = 0; 1101&1000=1; dev+=1 = 1 = 0001
         * 第二次  0011  3   dev<<1 = 0010 = 2; 1101&0100 = 1; dev+=1 = 3 = 0011
         * 第三次  0110  5   dev<<1 = 0110 = 5; 1101&0010 = 0;dev = 0110 = 5;
         * 第四次  1101  13  dev<<1 = 1100 = 12;1101&0001 = 1;dev+=1 = 1101 = 13
         *
         * startIndex 目前在1这个分隔符位置上，length 的数值为1后面的位数，所以先 startIndex++后才是对应的位置
         * 用原数据 & 当前StartIndex角标上的数据，如果为1，当前的十进制数+1
         * 二进制数每向右移动一位，十进制对应的二进制也要向左移动一位循环得出 1 右边的位数所对应的十进制数值
         * 最后1右移length的位置+右边的十进制数 = 转换后的十进制数
         */
        int dev = 0;//当前10进制对应的值
        for (int i = 0; i < length; i++) {
            bitIndex++;
            //向右移动0对应的位数，每次移动一次，转换10进制时增一个高位
            dev <<= 1;
            System.out.println("startIndex: " + bitIndex);
            //当前位置
            if ((bytes[bitIndex / 8] & (0x80 >> (bitIndex % 8))) != 0) {
                dev += 1;
                System.out.println("dev +=1 :" + dev);
            }
            System.out.println(" dev <<= 1:" + (dev));
        }
        //计算出右侧的数值加上1这个最高位就是解码出来的数据，根据规则减去1就是哥伦布编码前的原始数据
        int value = (1 << length) + dev - 1;
        return value;
    }

    //16进制源文件转byte数组
    public static byte[] getByteArray(String str) {
        int len = str.length();
        byte[] bs = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bs[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) +
                    Character.digit(str.charAt(i + 1), 16));
        }
        return bs;
    }

    public static void getNALUType() {
        String byteStr =
                "00 00 00 01 67 64 00 1F AC B4 02 80 2D D2 90 50 20 20 6D 0A 13 50".replaceAll(" ", "");
        byte[] bytes = getByteArray(byteStr);
        bitIndex = 4 * 8;//跳过0x0001分隔符
        int forbidden_bit = getBitDecimalOfLength(1, bytes);//第一位是 禁止位 0表示无错误 1表示有问题
        int importance = getBitDecimalOfLength(2, bytes);//2-3位表示重要性，0-3越高越好
        int type = getBitDecimalOfLength(5, bytes);//低5位表示当前NALU的type
        if (type == 7) {//表当前是fps
            int profile_idc = getBitDecimalOfLength(8, bytes);//type后8位代表的是编码等级
            //标志位
            int str0Flag = getBitDecimalOfLength(1, bytes);
            int str1Flag = getBitDecimalOfLength(1, bytes);
            int str2Flag = getBitDecimalOfLength(1, bytes);
            int str3Flag = getBitDecimalOfLength(1, bytes);
            //4个0 固定的标志位
            int zero_4bit = getBitDecimalOfLength(4, bytes);
            //这8位表示最大分辨率，最大帧数等也 是用来控制编码质量
            int max_frames = getBitDecimalOfLength(8, bytes);
            //后面开始使用哥伦布编码，一般为0，代表开始使用哥伦布编码
            int seq_parameter_set_id = getDecodeOriginData(bytes);
            //采样率 0-3的范围，0单色 1：yuv420 2：yuv422 3：yuv444
            int format_idc = getDecodeOriginData(bytes);
            //当前编码等级 ，100 为high，表示高画质
            if (profile_idc == 100) {
                //颜色位深
                int chroma_format_idc = getDecodeOriginData(bytes);
                //视频位深 0 8位
                int bt_depth_luma_minus8 = getDecodeOriginData(bytes);
                //颜色位深
                int bt_depth_chroma_minus8 = getDecodeOriginData(bytes);
                //转换标志位，占1个bit位
                int qpprime_y_zero_tranform_bypass_flag = getBitDecimalOfLength(1, bytes);
                //缩放标志位
                int seq_scaling_matrix_present_fla = getBitDecimalOfLength(1, bytes);
            }
            //最大帧率
            int log2_max_frame_num_minus4 = getDecodeOriginData(bytes);
            //播放顺序和解码顺序的映射
            int pic_order_cnt_type = getDecodeOriginData(bytes);
            //标志位
            int log2_max_pic = getDecodeOriginData(bytes);
            //编码顺序索引
            int num_ref_frames = getDecodeOriginData(bytes);
            //标志位
            int gaps_flag = getBitDecimalOfLength(1, bytes);
            //视频宽,指宏块的个数
            int width_ = (getDecodeOriginData(bytes) + 1) * 16;
            //视频高,指宏块的个数
            int heigth_ = getDecodeOriginData(bytes);


        }/* else if () {
        }else if () {
        }*/

    }

    //获取对应长度的数据，转换成10进制
    public static int getBitDecimalOfLength(int bitLength, byte[] h264) {
        int decimal = 0;
        for (int i = 0; i < bitLength; i++) {
            decimal <<= 1;
            if ((h264[bitIndex / 8] & (0x80 >> (bitIndex % 8))) != 0) {
                decimal += 1;
            }
            bitIndex++;
        }
        return decimal;
    }

    public static void main(String[] args) {
        getNALUType();

    }
}
