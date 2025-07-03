package com.example.mangareader

import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

class MangaPagerAdapter(
    private val mangaPages: List<Bitmap>,
    private var isLandscapeMode: Boolean
) : RecyclerView.Adapter<MangaPagerAdapter.MangaViewHolder>() {

    private val viewHolders = mutableListOf<MangaViewHolder>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.manga_page_layout, parent, false)
        val holder = MangaViewHolder(view)
        viewHolders.add(holder)
        return holder
    }

    override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
        if (isLandscapeMode) {
            holder.bindLandscapePages(position)
        } else {
            holder.bindSinglePage(position)
        }
    }

    override fun getItemCount(): Int {
        return if (isLandscapeMode) {
            (mangaPages.size + 1) / 2
        } else {
            mangaPages.size
        }
    }

    fun updatePageMode(newLandscapeMode: Boolean) {
        isLandscapeMode = newLandscapeMode
        notifyDataSetChanged()
    }

    fun resetZoom() {
        viewHolders.forEach { it.resetZoom() }
    }

    inner class MangaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val pageContainer: LinearLayout = itemView.findViewById(R.id.pageContainer)
        private val singlePageView: ZoomableImageView = itemView.findViewById(R.id.singlePageView)
        private val leftPageView: ZoomableImageView = itemView.findViewById(R.id.leftPageView)
        private val rightPageView: ZoomableImageView = itemView.findViewById(R.id.rightPageView)
        private val doublePageContainer: LinearLayout = itemView.findViewById(R.id.doublePageContainer)

        fun bindSinglePage(position: Int) {
            if (position < mangaPages.size) {
                singlePageView.visibility = View.VISIBLE
                doublePageContainer.visibility = View.GONE
                singlePageView.setImageBitmap(mangaPages[position])
            }
        }

        fun bindLandscapePages(position: Int) {
            singlePageView.visibility = View.GONE
            doublePageContainer.visibility = View.VISIBLE

            doublePageContainer.apply {
                rotation = 90f
                orientation = LinearLayout.HORIZONTAL
            }

            val leftPageIndex = position * 2
            val rightPageIndex = leftPageIndex + 1

            // Handle first page display (show as single page in landscape)
            if (position == 0 && mangaPages.isNotEmpty()) {
                leftPageView.visibility = View.GONE
                rightPageView.visibility = View.VISIBLE
                rightPageView.setImageBitmap(mangaPages[0])
            } else {
                val adjustedLeftIndex = leftPageIndex - 1
                val adjustedRightIndex = rightPageIndex - 1

                // Left page
                if (adjustedLeftIndex >= 0 && adjustedLeftIndex < mangaPages.size) {
                    leftPageView.visibility = View.VISIBLE
                    leftPageView.setImageBitmap(mangaPages[adjustedLeftIndex])
                } else {
                    leftPageView.visibility = View.GONE
                }

                // Right page
                if (adjustedRightIndex >= 0 && adjustedRightIndex < mangaPages.size) {
                    rightPageView.visibility = View.VISIBLE
                    rightPageView.setImageBitmap(mangaPages[adjustedRightIndex])
                } else {
                    rightPageView.visibility = View.GONE
                }
            }
        }

        fun resetZoom() {
            singlePageView.resetZoom()
            leftPageView.resetZoom()
            rightPageView.resetZoom()
        }
    }
}

class ZoomableImageView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatImageView(context, attrs, defStyleAttr) {

    private var scaleFactor = 1f
    private var scaleGestureDetector: ScaleGestureDetector
    private var gestureDetector: GestureDetector
    private var matrix = Matrix()
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var baseMatrix = Matrix()
    private var baseScaleFactor = 1f

    init {
        scaleType = ScaleType.MATRIX

        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = max(0.5f * baseScaleFactor, min(scaleFactor, 5.0f * baseScaleFactor))

                // Apply scaling on top of the base matrix
                matrix.set(baseMatrix)
                matrix.postScale(scaleFactor / baseScaleFactor, scaleFactor / baseScaleFactor,
                    detector.focusX, detector.focusY)
                imageMatrix = matrix
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (scaleFactor > baseScaleFactor) {
                    matrix.postTranslate(-distanceX, -distanceY)
                    imageMatrix = matrix
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                // Handle long press for fullscreen toggle
                (context as? MainActivity)?.onTouchEvent(e)
            }
        })
    }

    override fun setImageBitmap(bitmap: Bitmap?) {
        super.setImageBitmap(bitmap)
        // Calculate initial fit-to-width scaling
        if (bitmap != null) {
            post { // Wait for view to be measured
                fitToWidth()
            }
        }
    }

    private fun fitToWidth() {
        drawable?.let { drawable ->
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            val drawableWidth = drawable.intrinsicWidth.toFloat()
            val drawableHeight = drawable.intrinsicHeight.toFloat()

            if (viewWidth > 0 && viewHeight > 0 && drawableWidth > 0 && drawableHeight > 0) {
                // Calculate scale to fit width
                baseScaleFactor = viewWidth / drawableWidth
                scaleFactor = baseScaleFactor

                // Calculate centering
                val scaledHeight = drawableHeight * baseScaleFactor
                val dy = if (scaledHeight < viewHeight) {
                    (viewHeight - scaledHeight) / 2f
                } else {
                    0f
                }

                // Set up base matrix
                baseMatrix.reset()
                baseMatrix.postScale(baseScaleFactor, baseScaleFactor)
                baseMatrix.postTranslate(0f, dy)

                matrix.set(baseMatrix)
                imageMatrix = matrix
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleFactor > baseScaleFactor && !scaleGestureDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    if (!isDragging) {
                        isDragging = true
                    }
                    matrix.postTranslate(dx, dy)
                    imageMatrix = matrix
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
            }
        }
        return true
    }

    fun resetZoom() {
        scaleFactor = baseScaleFactor
        matrix.set(baseMatrix)
        imageMatrix = matrix
    }
}