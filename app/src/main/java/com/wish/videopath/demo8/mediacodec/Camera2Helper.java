package com.wish.videopath.demo8.mediacodec;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
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

import com.wish.videopath.util.ImageUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import static com.wish.videopath.MainActivity.LOG_TAG;


/**
 * Camera2 API使用
 */
public class Camera2Helper {

    private ScreenLive screenLive;
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
    private CameraYUVReadListener readListener;
    Point maxPreviewSize = new Point(1920, 1080);
    Point minPreviewSize = new Point(1280, 720);
    Point previewViewSize;
    private Size mPreviewSize;
    private byte[] y;
    private byte[] u;
    private byte[] v;
    private ReentrantLock lock = new ReentrantLock();
    private LinkedBlockingDeque<byte[]> deque = new LinkedBlockingDeque<>();
    private CameraCodec cameraCodec;
    private byte[] nv21, nv12, nv21_rotated;

    public Camera2Helper(Context context, TextureView textureView, ScreenLive screenLive) {
        this.context = context;
        this.textureView = textureView;
        this.screenLive = screenLive;
    }

    public LinkedBlockingDeque<byte[]> getYUVQueue() {
        return deque;
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
    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void openCamera() throws CameraAccessException {
        //拿到配置的map
        StreamConfigurationMap map = mBackCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        //获取摄像头传感器的方向
        mSensorOrientation = mBackCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        //获取预览尺寸
        Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
        //获取最佳尺寸
        mPreviewSize = getBestSize(previewSizes);
        Log.i(LOG_TAG, "获取最佳尺寸,width: " + mPreviewSize.getWidth() + " height:" + mPreviewSize.getHeight());


        /**
         * 摄像头数据通道
         * 直接返回YUV420数据，摄像头采集的还是nv21，底层帮我们转换成NV12
         * 最后一个参数代表输出几路数据，比如我预览使用一路，直播使用一路，就让底层输出2路数据
         */
        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);

        //监听数据何时可用,第二个参数是个Handler,用于回调给当前的线程
        HandlerThread cameraThread = new HandlerThread("camera");//构建一个子线程，并使用子线程的handler
        cameraThread.start();
        mCameraHandler = new Handler(cameraThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                Log.i(LOG_TAG, "cameraHandler 收到消息" + msg);
                return false;
            }
        });
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                //回调摄像头返回给我们的数据
                //当前yuv数据封装成的Image
                Image image = reader.acquireNextImage();
                // Y:U:V == 4:2:2
                if (image.getFormat() == ImageFormat.YUV_420_888) {
                    Image.Plane[] planes = image.getPlanes();
                    // 加锁确保y、u、v来源于同一个Image
                    lock.lock();
                    // 重复使用同一批byte数组，减少gc频率
                    if (y == null) {
                        y = new byte[planes[0].getBuffer().limit() - planes[0].getBuffer().position()];
                        u = new byte[planes[1].getBuffer().limit() - planes[1].getBuffer().position()];
                        v = new byte[planes[2].getBuffer().limit() - planes[2].getBuffer().position()];
                        Log.i(LOG_TAG, "y.length" + y.length);
                        Log.i(LOG_TAG, "u.length" + u.length);
                        Log.i(LOG_TAG, "v.length" + v.length);
                    }
                    if (image.getPlanes()[0].getBuffer().remaining() == y.length) {
                        planes[0].getBuffer().get(y);
                        planes[1].getBuffer().get(u);
                        planes[2].getBuffer().get(v);
                        if (cameraCodec == null) {
                            cameraCodec = new CameraCodec(screenLive, Camera2Helper.this, planes[0].getRowStride(), mPreviewSize.getHeight(),
                                    15, mPreviewSize.getHeight() * mPreviewSize.getWidth() * 3 / 2);
                        }

                        if (nv12 == null) {
                            int length = planes[0].getRowStride() * mPreviewSize.getHeight() * 3 / 2;
                            Log.i(LOG_TAG, "stride" + planes[0].getRowStride());
                            Log.i(LOG_TAG, "Size w h " + mPreviewSize.getWidth() + " " + mPreviewSize.getHeight());
                            Log.i(LOG_TAG, "存储长度" + length);
                            nv21 = new byte[length];
                            nv21_rotated = new byte[length];
                            nv12 = new byte[length];
                        }
                        ImageUtil.yuvToNv21(y, u, v, nv21, planes[0].getRowStride(), mPreviewSize.getHeight());
                        ImageUtil.revolveYuv(nv21, nv21_rotated, planes[0].getRowStride(), mPreviewSize.getHeight());
                        ImageUtil.nv21ToNv12(nv21_rotated, nv12, planes[0].getRowStride(), mPreviewSize.getHeight());
                        if (deque != null) {
                            deque.push(nv12);
                        }
                    }
                    lock.unlock();
                }
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
        /**
         * 配置预览属性
         * 与 Cmaera1 不同的是，Camera2 是把尺寸信息给到 Surface (SurfaceView 或者 ImageReader)，
         * Camera2 会根据 Surface 配置的大小，输出对应尺寸的画面;
         * 注意摄像头的 width > height ，而我们使用竖屏，所以宽高要变化一下
         */
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Log.i(LOG_TAG, "当前设置的size尺寸,width: " + textureView.getWidth() + " height:" + textureView.getHeight());

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
    private Size getBestSize(Size[] previewSizes) {
        List<Size> sizes = Arrays.asList(previewSizes);
        Size defaultSize = sizes.get(0);
        Size[] tempSizes = sizes.toArray(new Size[0]);
        Arrays.sort(tempSizes, new Comparator<Size>() {
            @Override
            public int compare(Size o1, Size o2) {
                if (o1.getWidth() > o2.getWidth()) {
                    return -1;
                } else if (o1.getWidth() == o2.getWidth()) {
                    return o1.getHeight() > o2.getHeight() ? -1 : 1;
                } else {
                    return 1;
                }
            }
        });
        sizes = new ArrayList<>(Arrays.asList(tempSizes));
        for (int i = sizes.size() - 1; i >= 0; i--) {
            if (maxPreviewSize != null) {
                if (sizes.get(i).getWidth() > maxPreviewSize.x || sizes.get(i).getHeight() > maxPreviewSize.y) {
                    sizes.remove(i);
                    continue;
                }
            }
            if (minPreviewSize != null) {
                if (sizes.get(i).getWidth() < minPreviewSize.x || sizes.get(i).getHeight() < minPreviewSize.y) {
                    sizes.remove(i);
                }
            }
        }
        if (sizes.size() == 0) {
            String msg = "can not find suitable previewSize, now using default";
            return defaultSize;
        }
        Size bestSize = sizes.get(0);
        float previewViewRatio;
        if (previewViewSize != null) {
            previewViewRatio = (float) previewViewSize.x / (float) previewViewSize.y;
        } else {
            previewViewRatio = (float) bestSize.getWidth() / (float) bestSize.getHeight();
        }

        if (previewViewRatio > 1) {
            previewViewRatio = 1 / previewViewRatio;
        }

        for (Size s : sizes) {
            if (Math.abs((s.getHeight() / (float) s.getWidth()) - previewViewRatio) < Math.abs(bestSize.getHeight() / (float) bestSize.getWidth() - previewViewRatio)) {
                bestSize = s;
            }
        }
        return bestSize;
    }

    public void setOnCameraDataPreviewListener(CameraYUVReadListener readListener) {
        this.readListener = readListener;
    }

    public interface CameraYUVReadListener {
        public void onPreview(byte[] y, byte[] u, byte[] v, Size width, int stride);
    }
}
