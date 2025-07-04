package com.example.mangareader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class MangaPagerAdapter(
    private val pages: List<Bitmap>,
    private val isDoublePage: Boolean
) : RecyclerView.Adapter<MangaPagerAdapter.PageViewHolder>() {

    inner class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val zoomImageView = ZoomImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        return PageViewHolder(zoomImageView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val imageView = holder.imageView as ZoomImageView

        if (isDoublePage) {
            if (position == 0) {
                val left = pages.getOrNull(-1)
                val right = pages.getOrNull(position)

                val merged = mergeBitmapsSideBySide(imageView.context, left, right)
                imageView.setImageBitmap(merged)
                imageView.setFitMode(ZoomImageView.FitMode.FIT_TO_HEIGHT)
            } else {
                val left = pages.getOrNull(position * 2)
                val right = pages.getOrNull(position * 2 - 1)

                val merged = mergeBitmapsSideBySide(imageView.context, left, right)
                imageView.setImageBitmap(merged)
                imageView.setFitMode(ZoomImageView.FitMode.FIT_TO_HEIGHT)
            }
        } else {
            imageView.setImageBitmap(pages[position])
            imageView.setFitMode(ZoomImageView.FitMode.FIT_TO_WIDTH)
        }

        imageView.resetZoom()
    }

    override fun getItemCount(): Int {
        return if (isDoublePage) {
            // If odd number of pages, last page will be solo
            (pages.size + 1) / 2
        } else {
            pages.size
        }
    }

    private fun mergeBitmapsSideBySide(context: Context, left: Bitmap?, right: Bitmap?): Bitmap {
        if (left == null) return right ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        if (right == null) return left

        val height = maxOf(left.height, right.height)
        val width = left.width + right.width

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        canvas.drawBitmap(left, 0f, 0f, null)
        canvas.drawBitmap(right, left.width.toFloat(), 0f, null)

        return result
    }
}