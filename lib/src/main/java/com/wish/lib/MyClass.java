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

        System.out.println(power(2.00000, 3));
    }

    public static double power(double base, int exponent) {
        if (base == 0 && exponent == 0) {
            return -1;
        }
        double num = 1;
        //4^3 = 4*4*4 4^-3 = (1/4)*(1/4)*(1/4)
        if (exponent < 0) {
            base = (1 / base);
            exponent = -exponent;
        }
        for (int i = 0; i < exponent; i++) {
            num = num * base;
        }

        //将小数点去除，根据次方正负右移 或 左移，再将小数点还原
        return num;
    }
}