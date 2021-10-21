package com.wish.videopath.demo1

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.wish.videopath.R


/**
 * 类名称：MyImageView
 * 类描述：
 *
 * @since: 2021/10/20 17:47
 */
class MyImageView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private val mPaint = Paint()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        mPaint.style = Paint.Style.FILL
        mPaint.color = Color.YELLOW

        val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.haha)
        canvas?.drawBitmap(bitmap, 0f, 0f, mPaint);//不限定图片大小  只指定左上角坐标
        bitmap.recycle()
    }
}