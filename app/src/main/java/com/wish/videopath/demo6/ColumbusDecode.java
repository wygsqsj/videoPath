package com.wish.videopath.demo6;

/**
 * 哥伦布编码解析
 * 哥伦布编码 以 1 为间隔，1前面的0代表往后堆读取的位数如： 0001 0100，1前面3个0，代表从1往后的3位都是当前类型的数据
 * 0x80 即 1000 0000 ，一个字节8位，取出当前字节里面1所在的位置，例如 0001 0100
 * 从最高位开始遍历，每次1000 0000 向右移动一位，然后 & 当前的字节数据，如果当前字节数据的当前位为1，那么
 * 1&1结果就是1，记录下当前的移动的步数，即1前面有几个0，这就是从1开始往后读取的位数，0001 0100，从1开始
 * 往后读3位得到 1010，高位补0得到数据0000 1010，哥伦布编码规则是原数据+1后再进行编码操作，我们解析出来的1010为
 */
public class ColumbusDecode {

    private static int bitIndex = 0;//当前的下标

    public static void setBitIndex(int bitIndex) {
        ColumbusDecode.bitIndex = bitIndex;
    }

    public static void getSizeFromSps(byte[] data) {
        for (int i = 0; i < data.length - 4; i++) {
            if (data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 0 && data[i + 3] == 1 && data[i + 4] == 0x67) {
                getNALUType(data);
            }
        }
    }

    /**
     * 无符号哥伦布编码
     */
    public static int ue(byte[] bytes) {
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
        bitIndex++;
        int decimal = 0;//当前10进制对应的值
        for (int i = 0; i < length; i++) {
            //向右移动0对应的位数，每次移动一次，转换10进制时增一个高位
            decimal <<= 1;
            System.out.println("startIndex: " + bitIndex);
            //当前位置
            if ((bytes[bitIndex / 8] & (0x80 >> (bitIndex % 8))) != 0) {
                decimal += 1;
                System.out.println("dev +=1 :" + decimal);
            }
            bitIndex++;
            System.out.println(" dev <<= 1:" + (decimal));
        }
        //计算出右侧的数值加上1这个最高位就是解码出来的数据，根据规则减去1就是哥伦布编码前的原始数据
        int value = (1 << length) + decimal - 1;
        return value;
    }

    /**
     * 有符号的哥伦布编码
     * 换算方式：n = (-1)^(k+1) * ceil(k/2)。
     */
    public static int se(byte[] bytes) {
        int UeVal = ue(bytes);
        double k = UeVal;
        int nValue = (int) Math.ceil(k / 2);
        //偶数取反，即(-1)^(k+1)
        if (UeVal % 2 == 0) {
            nValue = -nValue;
        }
        return nValue;
    }

    /**
     * 获取对应长度的数据，转换成10进制
     */
    public static int u(int bitLength, byte[] h264) {
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

    public static void getNALUType(byte[] bytes) {
        bitIndex = 4 * 8;//跳过0x0001分隔符
        int forbidden_bit = u(1, bytes);//第一位是 禁止位 0表示无错误 1表示有问题
        int importance = u(2, bytes);//2-3位表示重要性，0-3越高越好
        int type = u(5, bytes);//低5位表示当前NALU的type
        if (type == 7) {//表当前是fps
            int profile_idc = u(8, bytes);//type后8位代表的是编码等级
            //标志位
            int str0Flag = u(1, bytes);
            int str1Flag = u(1, bytes);
            int str2Flag = u(1, bytes);
            int str3Flag = u(1, bytes);
            int str4Flag = u(1, bytes);
            int str5Flag = u(1, bytes);
            // 固定的标志位
            int reserved_zero_2bits = u(2, bytes);
            //这8位表示最大分辨率，最大帧数等也 是用来控制编码质量
            int level_idc = u(8, bytes);
            //后面开始使用哥伦布编码，第一位原始数据一般为0，代表开始使用哥伦布编码
            int seq_parameter_set_id = ue(bytes);
            //当前编码等级，100 为high，表示高画质
            if (profile_idc == 100 || profile_idc == 110 ||
                    profile_idc == 122 || profile_idc == 244 || profile_idc == 44 ||
                    profile_idc == 83 || profile_idc == 86 || profile_idc == 118 ||
                    profile_idc == 128 || profile_idc == 138 || profile_idc == 139 ||
                    profile_idc == 134 || profile_idc == 135) {
                //颜色位深
                int chroma_format_idc = ue(bytes);
                if (chroma_format_idc == 3) {
                    int residual_colour_transform_flag = u(1, bytes);
                }
                //视频位深 0 8位
                int bit_depth_luma_minus8 = ue(bytes);
                //颜色位深
                int bit_depth_chroma_minus8 = ue(bytes);
                //转换标志位，占1个bit位
                int qpprime_y_zero_tranform_bypass_flag = u(1, bytes);
                //缩放标志位
                int seq_scaling_matrix_present_flag = u(1, bytes);
                int[] seq_scaling_list_present_flag = new int[8];
                if (seq_scaling_matrix_present_flag != 0) {
                    for (int i = 0; i < 8; i++) {
                        seq_scaling_list_present_flag[i] = u(1, bytes);
                    }
                }
            }
            //最大帧率
            int log2_max_frame_num_minus4 = ue(bytes);
            //
            int pic_order_cnt_type = ue(bytes);
            if (pic_order_cnt_type == 0) {
                int log2_max_pic_order_cnt_lsb_minus4 = ue(bytes);
            } else if (pic_order_cnt_type == 1) {
                int delta_pic_order_always_zero_flag = u(1, bytes);
                //有符号哥伦布编码
                int offset_for_non_ref_pic = se(bytes);
                int offset_for_top_to_bottom_field = se(bytes);

                int num_ref_frames_in_pic_order_cnt_cycle = ue(bytes);

                int[] offset_for_ref_frame = new int[num_ref_frames_in_pic_order_cnt_cycle];
                for (int i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; i++) {
                    offset_for_ref_frame[i] = se(bytes);
                }
            }
            int num_ref_frames = ue(bytes);
            int gaps_in_frame_num_value_allowed_flag = u(1, bytes);
            //视频宽,指宏块的个数
            int pic_width_in_mbs_minus1 = ue(bytes);
            //视频高,指宏块的个数
            int pic_height_in_map_units_minus1 = ue(bytes);

            System.out.println("宽：" + pic_width_in_mbs_minus1);
            System.out.println("高：" + pic_height_in_map_units_minus1);

            //+1是限定视频的宽高最小为16*16
            System.out.println("宽：" + (pic_width_in_mbs_minus1 + 1) * 16);
            System.out.println("高：" + (pic_height_in_map_units_minus1 + 1) * 16);
        }
    }


}
