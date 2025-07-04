package com.example.mangareader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomImageView(context: Context, attrs: AttributeSet? = null) : AppCompatImageView(context, attrs) {

    private val matrix = Matrix()
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    private var scaleFactor = 1f
    private var fitScale = 1f
    private var fitMode = FitMode.FIT_TO_WIDTH

    private var lastX = 0f
    private var lastY = 0f

    var onZoomStateChanged: ((Boolean) -> Unit)? = null

    enum class FitMode {
        FIT_TO_WIDTH, FIT_TO_HEIGHT
    }

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (event.pointerCount > 1 || scaleFactor > fitScale) {
            parent.requestDisallowInterceptTouchEvent(true)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleFactor > fitScale) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    matrix.postTranslate(dx, dy)
                    fixTranslation()
                    invalidate()
                    lastX = event.x
                    lastY = event.y
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (scaleFactor <= fitScale) {
                    resetZoom()
                }
            }
        }

        return true
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val focusX = e.x
            val focusY = e.y

            if (scaleFactor > fitScale) {
                resetZoom()
            } else {
                val targetScale = fitScale * 2.5f
                scaleFactor = targetScale
                matrix.postScale(2.5f, 2.5f, focusX, focusY)
                fixTranslation()
                invalidate()
                onZoomStateChanged?.invoke(true)
            }

            return true
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = detector.scaleFactor
            val focusX = detector.focusX
            val focusY = detector.focusY

            val newScale = (scaleFactor * scale).coerceIn(fitScale, 4f)
            val scaleChange = newScale / scaleFactor
            scaleFactor = newScale

            matrix.postScale(scaleChange, scaleChange, focusX, focusY)
            fixTranslation()
            invalidate()
            onZoomStateChanged?.invoke(scaleFactor > fitScale)
            return true
        }
    }

    override fun onDraw(canvas: Canvas) {
        imageMatrix = matrix
        super.onDraw(canvas)
    }

    fun resetZoom() {
        scaleFactor = fitScale
        matrix.reset()
        applyInitialFit()
        invalidate()
        onZoomStateChanged?.invoke(false)
    }

    fun isZoomed(): Boolean = scaleFactor > fitScale

    fun setFitMode(mode: FitMode) {
        fitMode = mode
        post {
            resetZoom()
        }
    }

    private fun applyInitialFit() {
        drawable?.let { drawable ->
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            val imageWidth = drawable.intrinsicWidth.toFloat()
            val imageHeight = drawable.intrinsicHeight.toFloat()

            fitScale = when (fitMode) {
                FitMode.FIT_TO_WIDTH -> viewWidth / imageWidth
                FitMode.FIT_TO_HEIGHT -> viewHeight / imageHeight
            }

            val dx = (viewWidth - imageWidth * fitScale) / 2f
            val dy = (viewHeight - imageHeight * fitScale) / 2f

            matrix.reset()
            matrix.postScale(fitScale, fitScale)
            matrix.postTranslate(dx, dy)
            invalidate()
        }
    }

    private fun fixTranslation() {
        drawable?.let {
            val bounds = RectF(0f, 0f, it.intrinsicWidth.toFloat(), it.intrinsicHeight.toFloat())
            matrix.mapRect(bounds)

            val deltaX = getFix(bounds.left, bounds.right, width.toFloat())
            val deltaY = getFix(bounds.top, bounds.bottom, height.toFloat())
            matrix.postTranslate(deltaX, deltaY)
        }
    }

    private fun getFix(min: Float, max: Float, viewSize: Float): Float {
        return when {
            max - min < viewSize -> (viewSize - (max + min)) / 2f
            min > 0 -> -min
            max < viewSize -> viewSize - max
            else -> 0f
        }
    }
}
