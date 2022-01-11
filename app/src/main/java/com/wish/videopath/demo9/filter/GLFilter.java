package com.wish.videopath.demo9.filter;

/**
 * openGL 滤镜抽象接口
 */
public interface GLFilter {
    int onDrawFrame(int textureId);

    void setTransformMatrix(float[] arrary);

    void onReady(int width, int height);
}
