package com.wish.videopath.demo9;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.wish.videopath.demo9.filter.GLFilter;
import com.wish.videopath.demo9.filter.ScreenFilter;
import com.wish.videopath.demo9.filter.WhiteBalanceFilter;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceRequest;

import static com.wish.videopath.MainActivity.LOG_TAG;

/**
 * 为摄像头获取的数据添加Render
 * 实现Preview.SurfaceProvider，构建SurfaceTexture用于摄像头预览
 */
public class CameraRender implements GLSurfaceView.Renderer, Preview.SurfaceProvider,
        SurfaceTexture.OnFrameAvailableListener {

    private final CameraGLView cameraGLView;
    private final Context context;
    private int[] textures = new int[1];
    private SurfaceTexture surfaceTexture;
    private float[] textureMatrix = new float[16];
    private GLFilter filter;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public CameraRender(Context context, CameraGLView cameraGLView) {
        this.context = context;
        this.cameraGLView = cameraGLView;
    }

    /**
     * 绘制预览帧，创建一个SurfaceTexture，此SurfaceTexture用于后续摄像头数据的处理
     */
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        gl.glGenTextures(textures.length, textures, 0);
        surfaceTexture = new SurfaceTexture(textures[0]);
//        surfaceTexture.attachToGLContext(textures[0]);
        filter = new WhiteBalanceFilter(context);
    }

    /**
     * 宽高改变，手机横屏时进行回调，当此方法回调时开启摄像头；通知滤镜层级当前的宽高
     */
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.i(LOG_TAG, "onSurfaceChanged回调，w: " + width + " h： " + height);
        //设置数据回调监听
        resetPreviewTexture(width, height);
        cameraGLView.onSurfaceChanged();
        //设置openGl宽高
        filter.onReady(width, height);
    }

    /**
     * cameraX获取数据回调给{@link #onFrameAvailable} ,surfaceTexture收到数据后通知glSurface.requestRender()
     * 然后回调到这里，此时surfaceTexture已经获取到摄像头最新的数据
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        if (gl == null || surfaceTexture == null) return;
        gl.glClearColor(0f, 0f, 0f, 0f);
        gl.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        //将摄像头的数据更新到surfaceTexture
        surfaceTexture.updateTexImage();
        //传递一个纹理矩阵，这个矩阵不是数据；最后会传递给openGl的vMatrix
        surfaceTexture.getTransformMatrix(textureMatrix);

        //将摄像头数据发送给openGl进行滤镜添加
        filter.setTransformMatrix(textureMatrix);
        filter.onDrawFrame(textures[0]);
    }


    /**
     * 实现CameraX和SurfaceTexture的绑定
     * SurfaceTexture中我们将CameraX返回的数据添加滤镜,再根据SurfaceTexture构建新的Surface用于渲染当前帧，并及时释放
     */
    @Override
    public void onSurfaceRequested(@NonNull @NotNull SurfaceRequest request) {
        if (surfaceTexture == null) {
            return;
        }
        Surface surface = new Surface(surfaceTexture);
        request.provideSurface(surface, executor, result -> {
            surface.release();
            surfaceTexture.release();
        });
    }

    /**
     * 为surfaceView添加数据监听，当摄像头有数据时回调给{@link #onFrameAvailable} 方法
     */
    private void resetPreviewTexture(int width, int height) {
        if (surfaceTexture != null) {
            surfaceTexture.setOnFrameAvailableListener(this);
            surfaceTexture.setDefaultBufferSize(width, height);
        }
    }

    /**
     * 当摄像头有数据过来回调此方法，此时应该通知openGl重新渲染，因为我们的openGl渲染方式是手动通知，此处通过
     * 回调给GLSurfaceView，调用requestRender刷新当前预览
     */
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        cameraGLView.onFrameAvailable();
    }

    public void setProgress(float progress) {
        if (filter instanceof WhiteBalanceFilter) {
            ((WhiteBalanceFilter) filter).setTemperature(progress);
        }
    }

    public void setTint(float progress) {
        if (filter instanceof WhiteBalanceFilter) {
            ((WhiteBalanceFilter) filter).setTint(progress);
        }
    }

    public void onDestroy() {

    }

    /**
     * 当视图刷新时回调给view层刷新当前预览
     */
    public interface Callback {
        void onSurfaceChanged();

        void onFrameAvailable();
    }
}
