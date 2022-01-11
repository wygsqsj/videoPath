package com.wish.videopath.demo9;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.SurfaceHolder;

public class CameraGLView extends GLSurfaceView implements CameraRender.Callback {

    private CameraRender render;

    public CameraGLView(Context context) {
        super(context, null);
    }

    public CameraGLView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    private void initView() {
        //设置openGL版本，当前设置为2，同时再清单文件中也要配置
        setEGLContextClientVersion(2);
        //设置渲染器
        render = new CameraRender(getContext(), this);
        setRenderer(render);
        //渲染器刷新模式 RENDERMODE_WHEN_DIRTY 被动渲染 调用requestRender 或 onResume时进行渲染
        // RENDERMODE_CONTINUOUSLY 表示持续渲染
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }


    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        if (render != null) {
            render.onDestroy();
        }
    }

    private void setUpCamera() {
        new CameraXHelper(getContext(), render).startCamera();
    }

    //开启摄像头
    @Override
    public void onSurfaceChanged() {
        setUpCamera();
    }


    @Override
    public void onFrameAvailable() {
        //手动刷新当前glview
        requestRender();
    }

    public void setProgress(float progress) {
        if (render != null) {
            render.setProgress(progress);
        }
    }


    public void setTint(float progress) {
        if (render != null) {
            render.setTint(progress);
        }
    }
}
