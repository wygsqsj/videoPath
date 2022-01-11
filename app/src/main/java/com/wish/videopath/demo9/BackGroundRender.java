package com.wish.videopath.demo9;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * openGL各种渲染器
 */
public class BackGroundRender implements GLSurfaceView.Renderer {

    /**
     * 初始化操作，在渲染前
     */
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        //清空之前的数据 类似caves.restore
        GLES20.glClearColor(0f, 0.2f, 0f, 0.5f);
    }

    //宽高改变，手机横屏
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    /*
     *类似与onDraw
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(0.4f, 0.1f, 20f, 0f);
    }


}


