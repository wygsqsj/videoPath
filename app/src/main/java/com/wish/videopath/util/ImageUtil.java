package com.wish.videopath.util;

public class ImageUtil {

    /**
     * Camera2 返回yuv三路数据转换成NV21,V在前，u在后
     *
     * @param y      Y 数据
     * @param u      U 数据
     * @param v      V 数据
     * @param nv21   生成的nv21，需要预先分配内存
     * @param stride 步长
     * @param height 图像高度
     */
    public static void yuvToNv21(byte[] y, byte[] u, byte[] v, byte[] nv21, int stride, int height) {
        System.arraycopy(y, 0, nv21, 0, y.length);
        // 注意，若length值为 y.length * 3 / 2 会有数组越界的风险，需使用真实数据长度计算
        int length = y.length + u.length / 2 + v.length / 2;
        int uIndex = 0, vIndex = 0;
        for (int i = stride * height; i < length; i += 2) {
            nv21[i] = v[vIndex];
            nv21[i + 1] = u[uIndex];
            vIndex += 2;
            uIndex += 2;
        }
    }

    /**
     * 旋转yuv数据，将横屏数据转换为竖屏
     */
    public static void revolveYuv(byte[] nv21, byte[] nv21_rotated, int width, int height) {
        int y_size = width * height;
        //uv高度
        int uv_height = height >> 1;
        //旋转y,左上角跑到右上角，左下角跑到左上角，从左下角开始遍历
        int k = 0;
        for (int i = 0; i < width; i++) {
            for (int j = height - 1; j > -1; j--) {
                nv21_rotated[k++] = nv21[width * j + i];
            }
        }
        //旋转uv
        for (int i = 0; i < width; i += 2) {
            for (int j = uv_height - 1; j > -1; j--) {
                nv21_rotated[k++] = nv21[y_size + width * j + i];
                nv21_rotated[k++] = nv21[y_size + width * j + i + 1];
            }
        }
    }

    /**
     * nv21 转换成nv12
     */
    public static void nv21ToNv12(byte[] nv21, byte[] nv12, int width, int height) {
        int frameSize = width * height;
        int i = 0, j = 0;
        System.arraycopy(nv21, 0, nv12, 0, frameSize);
        for (i = 0; i < frameSize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < frameSize / 2; j += 2) {
            nv12[frameSize + j - 1] = nv21[j + frameSize];
        }
        for (j = 0; j < frameSize / 2; j += 2) {
            nv12[frameSize + j] = nv21[j + frameSize - 1];
        }
    }

}
