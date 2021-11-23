package com.wish.videopath.demo7;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Log;
import android.view.TextureView;

import androidx.annotation.RequiresApi;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * Camera2 API使用
 */
public class Camera2Helper {

    private Context context;

    public Camera2Helper(Context context) {
        this.context = context;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public synchronized void start(TextureView textureView) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] idList = cameraManager.getCameraIdList();
            for (String cameraId : idList) {
                Log.i(LOG_TAG, "camera2 id: " + cameraId);
                if ("0".equals(cameraId)) {
                    //打开前置摄像头
                    cameraManager.getCameraCharacteristics(cameraId);
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
