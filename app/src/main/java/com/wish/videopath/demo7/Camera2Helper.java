package com.wish.videopath.demo7;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * Camera2 API使用
 */
public class Camera2Helper {

    private TextureView textureView;
    private Context context;
    private CameraManager mCameraManager;
    private String mBackCameraId, mFrontCameraId;
    private CameraCharacteristics mBackCameraCharacteristics, mFrontCameraCharacteristics;
    private Integer mSensorOrientation;
    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mCRBuilder;
    private Handler mCameraHandler;
    private CameraCaptureSession mCameraSesstion;


    public Camera2Helper(Context context, TextureView textureView) {
        this.context = context;
        this.textureView = textureView;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public synchronized void start() {
        //获取Camera2服务
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            //当前手机支持的摄像头id
            String[] idList = mCameraManager.getCameraIdList();
            for (String cameraId : idList) {
                // 拿到装在所有相机信息的  CameraCharacteristics 类
                // 拿到装在所有相机信息的  CameraCharacteristics 类
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                //拿到相机的方向，前置，后置，外置
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    //后置摄像头
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        mBackCameraId = cameraId;
                        mBackCameraCharacteristics = characteristics;
                        Log.i(LOG_TAG, "获取到后置摄像头)");
                    } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        //前置摄像头
                        mFrontCameraId = cameraId;
                        mFrontCameraCharacteristics = characteristics;
                        Log.i(LOG_TAG, "获取到前置摄像头)");
                    }
                }
            }
            openCamera();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 打开摄像头
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void openCamera() throws CameraAccessException {
        //拿到配置的map
        StreamConfigurationMap map = mBackCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        //获取摄像头传感器的方向
        mSensorOrientation = mBackCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        //获取预览尺寸
        Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
        //获取最佳尺寸
        Size bestSize = getBestSize(textureView.getWidth(), textureView.getHeight(), previewSizes);
        /**
         * 配置预览属性
         * 与 Cmaera1 不同的是，Camera2 是把尺寸信息给到 Surface (SurfaceView 或者 ImageReader)，
         * Camera2 会根据 Surface 配置的大小，输出对应尺寸的画面;
         * 注意摄像头的 width > height ，而我们使用竖屏，所以宽高要变化一下
         */
        textureView.getSurfaceTexture().setDefaultBufferSize(bestSize.getHeight(), bestSize.getWidth());
        Log.i(LOG_TAG, "当前设置的size尺寸,width: " + textureView.getWidth() + " height:" + textureView.getHeight());

        /**
         * 摄像头数据通道
         * 直接返回YUV420数据，摄像头采集的还是nv21，底层帮我们转换成NV12
         * 最后一个参数代表输出几路数据，比如我预览使用一路，直播使用一路，就让底层输出2路数据
         */
        mImageReader = ImageReader.newInstance(textureView.getWidth(), textureView.getHeight(), ImageFormat.YUV_420_888, 2);

        //监听数据何时可用,第二个参数是个Handler,用于回调给当前的线程
        HandlerThread cameraThread = new HandlerThread("camera");//构建一个子线程，并使用子线程的handler
        cameraThread.start();
        mCameraHandler = new Handler(cameraThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull @NotNull Message msg) {
                Log.i(LOG_TAG, "cameraHandler 收到消息" + msg);
                return false;
            }
        });
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                //回调摄像头返回给我们的数据
                Log.i(LOG_TAG, "摄像头数据回调 onImageAvailable");
                //当前yuv数据封装成的Image
                Image image = reader.acquireLatestImage();

                //关闭当前的数据才能接受到新数据
                image.close();

            }
        }, mCameraHandler);

        //打开摄像头
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        //打开摄像头，对应的状态返回给我们
        mCameraManager.openCamera(mBackCameraId, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                //摄像头被打卡
                Log.i(LOG_TAG, "摄像头被打开了 onOpened回调");
                mCameraDevice = camera;
                //建立会话
                try {
                    createCameraSession();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                Log.i(LOG_TAG, "摄像头关闭 onDisconnected");
                camera.close();
                mCameraDevice = null;

            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                Log.i(LOG_TAG, "摄像头出错 " + error);
                camera.close();
                mCameraDevice = null;
            }
        }, mCameraHandler);
    }

    //建立摄像头数据会话，设置两个数据出口
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCameraSession() throws CameraAccessException {
        //构建Surface，将摄像头数据输入到我们TextureView中
        SurfaceTexture texture = textureView.getSurfaceTexture();
        Surface surface = new Surface(texture);

        //开启请求
        mCRBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        mCRBuilder.addTarget(surface);

        //设置拍照模式
        mCRBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        //摄像头数据输出到通道中
        mCRBuilder.addTarget(mImageReader.getSurface());

        //构建会话链接,输出的surface数量代表输出的通道数量
        mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                Log.i(LOG_TAG, "摄像头会话建立 ");
                mCameraSesstion = session;
                //设置重复请求
                try {
                    mCameraSesstion.setRepeatingRequest(mCRBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                    }, mCameraHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

            }
        }, mCameraHandler);
    }

    public void closeCamera() {
        if (mCameraSesstion != null) {
            mCameraSesstion.close();
        }

    }


    /**
     * 根据当前texture宽高从摄像头支持的分辨率中获取最合适的分辨率
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Size getBestSize(int width, int height, Size[] previewSizes) {
        //降序排序
        List<Size> outputSizes = Arrays.asList(previewSizes);
        Collections.sort(outputSizes, new Comparator<Size>() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public int compare(Size o1, Size o2) {
                return o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight();
            }
        });
        Collections.reverse(outputSizes);

        Size previewSize = outputSizes.get(0);

        List<Size> sizes = new ArrayList<>();
        //计算预览窗口高宽比，高宽比，高宽比
        float ratio = ((float) height / width);
        //首先选取宽高比与预览窗口高宽比一致且最大的输出尺寸
        for (int i = 0; i < outputSizes.size(); i++) {
            if (((float) outputSizes.get(i).getWidth()) / outputSizes.get(i).getHeight() == ratio) {
                sizes.add(outputSizes.get(i));
            }
        }
        if (sizes.size() > 0) {
            return sizes.get(0);
        }
        //如果不存在宽高比与预览窗口高宽比一致的输出尺寸，则选择与其宽高比最接近的输出尺寸
        sizes.clear();
     /*   float detRatioMin = Float.MAX_VALUE;
        for (int i = 0; i < outputSizes.size(); i++) {
            Size size = outputSizes.get(i);
            float curRatio = ((float) size.getWidth()) / size.getHeight();
            if (Math.abs(curRatio - ratio) < detRatioMin) {
                detRatioMin = curRatio;
                previewSize = size;
            }
        }*/

        //如果宽高比最接近的输出尺寸太小，则选择与预览窗口面积最接近的输出尺寸
        long area = width * height;
        long detAreaMin = Long.MAX_VALUE;
        for (int i = 0; i < outputSizes.size(); i++) {
            Size size = outputSizes.get(i);
            long curArea = size.getWidth() * size.getHeight();
            if (Math.abs(curArea - area) < detAreaMin) {
                detAreaMin = curArea;
                previewSize = size;
            }
        }
        return previewSize;
    }
}
