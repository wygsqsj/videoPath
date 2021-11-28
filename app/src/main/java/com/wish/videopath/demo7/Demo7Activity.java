package com.wish.videopath.demo7;

import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.View;

import com.wish.videopath.R;
import com.wish.videopath.util.ImageUtil;

import java.util.concurrent.LinkedBlockingQueue;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * Camera2相机获取数据保存成h264码流
 */
public class Demo7Activity extends AppCompatActivity implements TextureView.SurfaceTextureListener, Camera2Helper.CameraYUVReadListener {

    private TextureView textureView;
    private Camera2Helper helper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo7);
        textureView = findViewById(R.id.textView);
        textureView.setSurfaceTextureListener(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        helper = new Camera2Helper(this, textureView);
        helper.start();
        helper.setOnCameraDataPreviewListener(this);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        helper.closeCamera();
        encodeMuxerThread.stopEncode();
    }

    private byte[] nv21;
    private byte[] nv21_rotated;
    private byte[] nv12;
    private volatile LinkedBlockingQueue YUVQueue = new LinkedBlockingQueue(16);
    private H264EncodeThread encodeMuxerThread;

    //Camera2数据回调,先转换成NV21,再转换成NV12
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPreview(byte[] y, byte[] u, byte[] v, Size previewSize, int stride) {
        if (nv12 == null) {
            //存储长度YUV加起来Y 1 uv0.5
            int length = stride * previewSize.getHeight() * 3 / 2;
            Log.i(LOG_TAG, "stride" + stride);
            Log.i(LOG_TAG, "Size w h " + previewSize.getWidth() + " " + previewSize.getHeight());
            Log.i(LOG_TAG, "存储长度" + length);
            nv21 = new byte[length];
            nv21_rotated = new byte[length];
            nv12 = new byte[length];
            initMediaCodec(stride, previewSize.getHeight());
        }

        ImageUtil.yuvToNv21(y, u, v, nv21, stride, previewSize.getHeight());
        ImageUtil.revolveYuv(nv21, nv21_rotated, stride, previewSize.getHeight());
        ImageUtil.nv21ToNv12(nv21_rotated, nv12, stride, previewSize.getHeight());
        YUVQueue.add(nv12);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initMediaCodec(int width, int height) {
        encodeMuxerThread = new H264EncodeThread(this, width, height,
                30, width * height * 3 / 2);
        encodeMuxerThread.start();
    }

    public LinkedBlockingQueue<byte[]> getYUVQueue() {
        return YUVQueue;
    }

    public void stopEncode(View view) {
        if (encodeMuxerThread != null) {
            encodeMuxerThread.stopEncode();
        }
    }
}