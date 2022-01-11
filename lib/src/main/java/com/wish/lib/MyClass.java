package com.wish.lib;

/**
 * 实现函数 double Power(double base, int exponent)，求base的exponent次方。
 * <p>
 * 注意：
 * 1.保证base和exponent不同时为0。
 * 2.不得使用库函数，同时不需要考虑大数问题
 * 3.有特殊判题，不用考虑小数点后面0的位数。
 * <p>
 * 数据范围：  ，  ,保证最终结果一定满足
 * 进阶：空间复杂度  ，时间复杂度
 * <p>
 * 示例1
 * 输入：
 * 2.00000,3
 * 复制
 * 返回值：
 * 8.00000
 * 复制
 * 示例2
 * 输入：
 * 2.10000,3
 * 复制
 * 返回值：
 * 9.26100
 * 复制
 * 示例3
 * 输入：
 * 2.00000,-2
 * 复制
 * 返回值：
 * 0.25000
 * 复制
 * 说明：
 * 2的-2次方等于1/4=0.25
 */

public class MyClass {

    public static void main(String[] args) {
        Runtime rn = Runtime.getRuntime();
        Process p = null;
        try {
            p = rn.exec("cmd.exe /c D:/Program Files (x86)/pngquant/pngquant.exe D:/Program Files (x86)/pngquant/bg.png");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}