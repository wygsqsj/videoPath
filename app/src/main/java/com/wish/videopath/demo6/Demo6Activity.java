package com.wish.videopath.demo6;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.appcompat.app.AppCompatActivity;

import com.wish.videopath.R;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * MediaCodec编解码，将摄像头捕捉的视频内容保存成.h264并显示倒surfaceView中
 */
public class Demo6Activity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceView surfaceview;

    private SurfaceHolder surfaceHolder;

    private Camera mCamera;

    private Camera.Parameters parameters;

    int width = 1280;

    int height = 720;

    int framerate = 30;

    int biterate = 8500 * 1000;

    private static int yuvqueuesize = 10;

    //待解码视频缓冲队列，静态成员！
    public static ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<byte[]>(yuvqueuesize);
    private AvcEncoder avcCodec;

//    private AvcEncoder avcCodec;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo6);
        surfaceview = findViewById(R.id.demo6Surface);
        surfaceHolder = surfaceview.getHolder();
        surfaceHolder.addCallback(this);
    }

    //摄像头获取到的yuv数据回调
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        //将当前帧图像保存在队列中
        putYUVData(data, data.length);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        initBackCamera();
        //创建AvEncoder对象
        avcCodec = new AvcEncoder(width, height, framerate, biterate);
        //启动编码线程
        avcCodec.StartEncoderThread();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (null != mCamera) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
//            avcCodec.StopThread();
        }
    }

    public void putYUVData(byte[] buffer, int length) {
        Log.i(LOG_TAG, "获取到摄像头数据" + length);
        if (YUVQueue.size() >= 10) {
            YUVQueue.poll();
        }
        YUVQueue.add(buffer);
    }


    //配置摄像头
    private void initBackCamera() {
        //获取Camera的实例
        mCamera = Camera.open(0);
        if (mCamera == null) {
            throw new RuntimeException("摄像机打开失败！");
        }
        try {
            //设置camera数据回调
            mCamera.setPreviewCallback(this);
            mCamera.setDisplayOrientation(90);
            if (parameters == null) {
                parameters = mCamera.getParameters();
            }
            //获取默认的camera配置
            parameters = mCamera.getParameters();
            //设置预览格式
            parameters.setPreviewFormat(ImageFormat.NV21);
            //设置预览图像分辨率
            parameters.setPreviewSize(width, height);
            //配置camera参数
            mCamera.setParameters(parameters);
            //将完全初始化的SurfaceHolder传入到setPreviewDisplay(SurfaceHolder)中
            //没有surface的话，相机不会开启preview预览
            mCamera.setPreviewDisplay(surfaceHolder);
            //调用startPreview()用以更新preview的surface，必须要在拍照之前start Preview
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}