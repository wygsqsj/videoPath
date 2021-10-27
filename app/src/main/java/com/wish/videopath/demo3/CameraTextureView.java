package com.wish.videopath.demo3;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

import java.io.IOException;

/**
 * 类名称：CameraTextureView
 * 类描述：
 * <p>
 * 创建时间：2021/10/26
 */
public class CameraTextureView extends TextureView implements TextureView.SurfaceTextureListener {
    private Camera mCamera;

    public CameraTextureView(Context context) {
        this(context, null, -1);
    }

    public CameraTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public CameraTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        try {
            mCamera = Camera.open();
            // 打开摄像头并将展示方向旋转90度
            mCamera.setDisplayOrientation(90);
            setSurfaceTextureListener(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        try {
            mCamera.setPreviewTexture(surface);
            mCamera.startPreview();
            //设置NV21数据回调
            mCamera.setPreviewCallback((bytes, camera) -> {
                Log.i("音视频","camera数据："+bytes.length);
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mCamera.stopPreview();
        mCamera.release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    private Camera.PreviewCallback mCameraPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Log.d("音视频", "onPreviewFrame: data.length=" + data.length);
        }
    };
}
