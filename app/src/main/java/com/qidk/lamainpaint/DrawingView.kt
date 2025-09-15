package com.qidk.lamainpaint

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var imageBitmap: Bitmap? = null
    private var maskBitmap: Bitmap? = null
    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 20f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val canvasPaint = Paint(Paint.DITHER_FLAG)
    private var canvas: Canvas? = null
    private val path = Path()

    fun setImage(bmp: Bitmap) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val aspectRatio = bmp.height.toFloat() / bmp.width.toFloat()
        val newHeight = (screenWidth * aspectRatio).toInt()

        imageBitmap = Bitmap.createScaledBitmap(bmp, screenWidth, newHeight, true)
        maskBitmap = Bitmap.createBitmap(screenWidth, newHeight, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE) // White background for mask (keep)
        }
        canvas = Canvas(maskBitmap!!)
        requestLayout()
        invalidate()
    }

    fun getMaskBitmap(): Bitmap? = maskBitmap

    val bitmap: Bitmap? get() = imageBitmap

    fun clearMask() {
        maskBitmap?.eraseColor(Color.WHITE)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        imageBitmap?.let { bmp ->
            // Draw the original image
            canvas.drawBitmap(bmp, 0f, 0f, null)
            // Draw the mask with transparency for preview
            maskBitmap?.let { mask ->
                val maskPaint = Paint().apply {
                    alpha = 128 // Semi-transparent
                }
                canvas.drawBitmap(mask, 0f, 0f, maskPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (imageBitmap == null) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                path.moveTo(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                canvas?.drawPath(path, paint)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                canvas?.drawPath(path, paint)
                path.reset()
                invalidate()
            }
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = imageBitmap?.width ?: MeasureSpec.getSize(widthMeasureSpec)
        val height = imageBitmap?.height ?: MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
