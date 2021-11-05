package com.wish.videopath.demo6;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.wish.videopath.R;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * MediaCodec编解码，将摄像头捕捉的视频内容保存成.h264并显示倒surfaceView中
 */
public class Demo6Activity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceView surfaceview;

    private Button mCameraEncode, mCameraMuxerEncode, mScreenEncode;

    private Camera mCamera;

    private Camera.Parameters parameters;

    int width = 1280;

    int height = 720;

    int framerate = 30;

    int biterate = 8500 * 1000;

    //待解码视频缓冲队列,volatitle 保证多线程的可见性
    private volatile LinkedBlockingQueue YUVQueue = new LinkedBlockingQueue(16);


    private H264EncodeThread encodeThread;
    private H264EncodeMuxerThread encodeMuxerThread;
    private H264EncodeScreenThread screenThread;
    //录屏queue
    private volatile LinkedBlockingQueue screenQueue = new LinkedBlockingQueue(16);
    private MediaProjection mMediaProjection;    //录屏api
    private MediaProjectionManager mediaManager;
    private ActivityResultLauncher<Intent> resultLauncher;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo6);
        surfaceview = findViewById(R.id.demo6Surface);
        mCameraEncode = findViewById(R.id.btnStart264);
        mCameraMuxerEncode = findViewById(R.id.btnCameraMuxer);
        mScreenEncode = findViewById(R.id.btnStop264);
        surfaceview.getHolder().addCallback(this);

        initMediaProjection();
    }

    private void initMediaProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            //代替startActivityForResult
            resultLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getData() != null) {
                            mMediaProjection = mediaManager.getMediaProjection(result.getResultCode(), result.getData());
                            startDisplay();
                        }
                    });
        }
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
        }

        if (encodeThread != null) {
            encodeThread.stopEncode();
        }

        if (encodeMuxerThread != null) {
            encodeMuxerThread.stopEncode();
        }
    }

    public void putYUVData(byte[] buffer, int length) {
//        Log.i(LOG_TAG, "获取到摄像头数据" + length);
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
            mCamera.setPreviewDisplay(surfaceview.getHolder());
            //调用startPreview()用以更新preview的surface，必须要在拍照之前start Preview
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (encodeThread != null) {
            encodeThread.stopEncode();
        }
        if (encodeMuxerThread != null) {
            encodeMuxerThread.stopEncode();
        }
        if (screenThread != null) {
            screenThread.stopEncode();
        }

        if (null != mCamera) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    public LinkedBlockingQueue<byte[]> getYUVQueue() {
        return YUVQueue;
    }

    public LinkedBlockingQueue<byte[]> getScreenQueue() {
        return screenQueue;
    }


    //摄像头录制h264，IO流直接写入
    public void startH264(View view) {
        if (mCameraEncode.getText().toString().contains("开始录制")) {
            //创建AvEncoder对象
            encodeThread = new H264EncodeThread(this, width, height, framerate, biterate);
            //启动编码线程
            encodeThread.start();
            mCameraEncode.setText("正在录制");
        } else {
            encodeThread.stopEncode();
            mCameraEncode.setText("开始录制");
            encodeThread = null;
        }
    }

    //摄像头录制h264，使用Muxer方式，有时间戳
    public void startH264OfMuxer(View view) {
        if (mCameraMuxerEncode.getText().toString().contains("开始录制_Muxer")) {
            //创建AvEncoder对象
            encodeMuxerThread = new H264EncodeMuxerThread(this, width, height, framerate, biterate);
            //启动编码线程
            encodeMuxerThread.start();
            mCameraMuxerEncode.setText("正在录制");
        } else {
            encodeMuxerThread.stopEncode();
            mCameraMuxerEncode.setText("开始录制_Muxer");
            encodeMuxerThread = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void screenRecording(View view) {
        if (mScreenEncode.getText().toString().contains("开始录屏")) {
            resultLauncher.launch(mediaManager.createScreenCaptureIntent());
        } else {
            screenThread.stopEncode();
            mScreenEncode.setText("开始录屏");
            screenThread = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startDisplay() {
        //创建AvEncoder对象
        screenThread = new H264EncodeScreenThread(this, width, height, framerate, biterate, mMediaProjection);
        //启动编码线程
        screenThread.start();
        mScreenEncode.setText("正在录屏");
    }
}