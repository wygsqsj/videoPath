package com.wish.videopath.demo3;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * 类名称：CamaraView
 * 类描述：
 * <p>
 * 创建时间：2021/10/26
 */
public class CameraView extends SurfaceView implements SurfaceHolder.Callback{

    private Camera camera;

    public CameraView(Context context) {
        this(context, null, -1);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        camera = Camera.open();
        camera.setDisplayOrientation(90);
        getHolder().addCallback(this);
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera.setPreviewDisplay(getHolder());
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            camera.release();
        }
    }

}
