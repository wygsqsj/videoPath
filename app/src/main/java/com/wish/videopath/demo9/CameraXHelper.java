package com.wish.videopath.demo9;

import android.content.Context;

import com.google.common.util.concurrent.ListenableFuture;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

/**
 * cameraX 摄像头
 */
public class CameraXHelper {

    private Preview.SurfaceProvider surfaceProvider;
    private Context context;
    private ExecutorService cameraExecutor;

    public CameraXHelper(Context context, Preview.SurfaceProvider surfaceProvider) {
        this.context = context;
        this.surfaceProvider = surfaceProvider;
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    //开启camera
    public void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            ProcessCameraProvider cameraProvider = null;
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();

                //绑定当前相机的预览视图，在Render中进行了实现，将摄像头返回的数据用SurfaceTexture进行渲染
                preview.setSurfaceProvider(surfaceProvider);

                //后置摄像头
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                //数据回调
                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder().build();

                imageAnalyzer.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull @NotNull ImageProxy image) {
                    }
                });

                cameraProvider.unbindAll();
                /*
                 * 当前生命周期绑定相机
                 * 第三个参数 UseCase类型：VideoCapture视频捕获；ImageCapture图片捕获；Preview相机预览；
                 * ImageAnalysis图像分析
                 */
                Camera camera = cameraProvider.bindToLifecycle(
                        (LifecycleOwner) context,
                        cameraSelector,
                        preview,
                        imageAnalyzer//视频数据捕获
                );

                // 控制闪光灯、切换摄像头等。。。
                CameraInfo cameraInfo = camera.getCameraInfo();
                CameraControl cameraControl = camera.getCameraControl();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(context));
    }


    private void onDestroy() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
    }

}
