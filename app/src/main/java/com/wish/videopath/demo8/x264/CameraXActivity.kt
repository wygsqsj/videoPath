package com.wish.videopath.demo8.x264

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.wish.videopath.R
import com.wish.videopath.databinding.ActivityCameraXactivityBinding
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock

/**
 * CameraX的基本使用
 * CameraX基于Camera2,简化了Camera2的代码
 * CameraX的PreviewView最低支持版本21，为了兼容其他demo，此处先注释掉，如果想使用cameraX,修改
 * minSdkVersion 为 21
 * 此项目中为了兼容其他demo，
 */
class CameraXActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    private lateinit var binding: ActivityCameraXactivityBinding
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private var helper: VideoHelper? = null
    private val audioHelper: AudioHelper? = null
    private var livePush: LivePush? = null

    private var y: ByteArray? = null
    private lateinit var u: ByteArray
    private lateinit var v: ByteArray
    private val lock = ReentrantLock()
    private lateinit var nv21: ByteArray
    private var nv12: kotlin.ByteArray? = null
    private lateinit var nv21_rotated: kotlin.ByteArray

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraXactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        startCamera()
//
//        outputDirectory = getOutputDirectory()
//
//        cameraExecutor = Executors.newSingleThreadExecutor()
//
//        //发送层
//        livePush = LivePush()
//        livePush!!.startLive(Demo8Activity.RTMPURL)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }

        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }


//    //开启camera
//    @SuppressLint("RestrictedApi")
//    private fun startCamera() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//
//        cameraProviderFuture.addListener(Runnable {
//            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
//            val preview = Preview.Builder()
//                .build()
//                .also {
//                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
//                }
//
//            //后置摄像头
//            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//
//            //数据回调
//            val imageAnalyzer = ImageAnalysis.Builder()
//                .build()
//                .also {
//                    it.setAnalyzer(cameraExecutor,
//                        { image ->
//                            if (image.format == ImageFormat.YUV_420_888) {
//                                lock.lock()
//                                Log.i(LOG_TAG, "获取到数据：" + image.planes[0].buffer)
//                                // 重复使用同一批byte数组，减少gc频率
//                                if (y == null) {
//                                    y = ByteArray(
//                                        image.planes[0].buffer.limit() - image.planes[0].buffer.position()
//                                    )
//                                    u = ByteArray(
//                                        image.planes[1].buffer.limit() - image.planes[1].buffer.position()
//                                    )
//                                    v = ByteArray(
//                                        image.planes[2].buffer.limit() - image.planes[2].buffer.position()
//                                    )
//                                    Log.i(LOG_TAG, "y.length" + y?.size)
//                                    Log.i(LOG_TAG, "u.length" + u.size)
//                                    Log.i(LOG_TAG, "v.length" + v.size)
//                                }
//
//                                if (image.planes[0].buffer.remaining() == y?.size) {
//                                    image.planes[0].buffer.get(y)
//                                    image.planes[1].buffer.get(u)
//                                    image.planes[2].buffer.get(v)
//                                    if (nv12 == null) {
//                                        val length: Int =
//                                            image.planes[0].rowStride * image.height * 3 / 2
//                                        Log.i(LOG_TAG, "stride" + image.planes[0].rowStride)
//                                        Log.i(
//                                            LOG_TAG,
//                                            "Size w h " + image.planes[0].rowStride + " " + image.height
//                                        )
//                                        Log.i(LOG_TAG, "存储长度$length")
//                                        nv21 = ByteArray(length)
//                                        nv21_rotated = ByteArray(length)
//                                        nv12 = ByteArray(length)
//                                        //得到了宽高,初始化编码信息
//                                        if (livePush != null) {
//                                            livePush!!.native_setVideoEncInfo(
//                                                image.height, image.planes[0].rowStride,
//                                                15, image.planes[0].rowStride * image.height * 3 / 2
//                                            )
//                                        }
//                                    }
//                                    ImageUtil.yuvToNv21(
//                                        y,
//                                        u,
//                                        v,
//                                        nv21,
//                                        image.planes[0].rowStride,
//                                        image.height
//                                    )
//                                    ImageUtil.revolveYuv(
//                                        nv21,
//                                        nv21_rotated,
//                                        image.planes[0].rowStride,
//                                        image.height
//                                    )
//                                    if (livePush != null) {
//                                        livePush!!.native_pushVideo(nv21_rotated)
//                                    }
//                                }
//                                image.close()
//                                lock.unlock()
//                            }
//                        })
//                }
//            try {
//                cameraProvider.unbindAll()
//                /*
//                 * 当前生命周期绑定相机
//                 * 第三个参数 UseCase类型：VideoCapture视频捕获；ImageCapture图片捕获；Preview相机预览；
//                 * ImageAnalysis图像分析
//                 */
//                cameraProvider.bindToLifecycle(
//                    this, cameraSelector,
//                    preview,//当前得预览页面
//                    imageAnalyzer//视频数据捕获
//                )
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//
//        }, ContextCompat.getMainExecutor(this))
//    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()

        if (helper != null) {
            helper!!.closeCamera()
        }

        audioHelper?.stopAudio()

        if (livePush != null) {
            livePush!!.stopLive()
        }
    }
}
