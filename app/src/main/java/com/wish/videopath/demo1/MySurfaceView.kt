package com.wish.videopath.demo1

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import com.wish.videopath.R


/**
 * 类名称：MySurfaceView
 * 类描述：
 *
 * 创建时间：2021/10/21
 */
@RequiresApi(Build.VERSION_CODES.O)
class MySurfaceView(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
    SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback, Runnable {

    // 可以控制SurfaceView的大小，格式，可以监控或者改变SurfaceView
    private var mSurfaceHolder: SurfaceHolder = holder

    // 画布
    private lateinit var mCanvas: Canvas

    // 子线程标志位
    private var isDrawing = false

    init {
        mSurfaceHolder.addCallback(this)//注册SurfaceHolder
        focusable = FOCUSABLE
    }

    constructor(context: Context?) : this(context, null, -1) {
    }

    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, -1) {
    }


    override fun run() {
        drawing()
    }

    private fun drawing() {
        mCanvas = mSurfaceHolder.lockCanvas()
        val mPaint = Paint()
        mPaint.style = Paint.Style.FILL
        mPaint.color = Color.GREEN
        //构建bitmap
        val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.haha)
        //bitmap显示区域
        val rect = RectF(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
        mCanvas.drawRect(rect, mPaint)
        //绘制
        mCanvas.drawBitmap(bitmap, 0f, 0f, mPaint)
        mSurfaceHolder.unlockCanvasAndPost(mCanvas)
    }

    override fun surfaceCreated(p0: SurfaceHolder) {
        isDrawing = true;
        Thread(this).start()
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
        TODO("Not yet implemented")
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        isDrawing = false
    }

}