package com.example.mangareader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var emptyView: TextView
    private lateinit var mangaTitle: TextView
    private lateinit var pageIndicator: TextView

    private lateinit var adapter: MangaPagerAdapter
    private var pages: List<Bitmap> = emptyList()
    private var currentPage = 0

    private var isRTL = true
    private var isDoublePage = false

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadDocument(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        emptyView = findViewById(R.id.emptyView)
        mangaTitle = findViewById(R.id.mangaTitle)
        pageIndicator = findViewById(R.id.pageIndicator)

        adapter = MangaPagerAdapter(emptyList(), isDoublePage, isRTL)
        viewPager.adapter = adapter
        viewPager.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.settingsBtn).setOnClickListener {
            showSettingsDialog()
        }

        findViewById<TextView>(R.id.emptyView).setOnClickListener {
            openFilePicker()
        }

        findViewById<ImageButton>(R.id.leftBtn).setOnClickListener {
            val target = if (isRTL) currentPage + 1 else currentPage - 1
            navigateToPage(target)
        }

        findViewById<ImageButton>(R.id.rightBtn).setOnClickListener {
            val target = if (isRTL) currentPage - 1 else currentPage + 1
            navigateToPage(target)
        }

        findViewById<Button>(R.id.zoomResetBtn).setOnClickListener {
            val recyclerView = viewPager.getChildAt(0) as? RecyclerView ?: return@setOnClickListener
            val holder = recyclerView.findViewHolderForAdapterPosition(currentPage) as? MangaPagerAdapter.PageViewHolder
            val imageView = holder?.imageView as? ZoomImageView
            imageView?.resetZoom()
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updatePageIndicator()
            }
        })
    }

    private fun openFilePicker() {
        openFileLauncher.launch("*/*")
    }

    private fun loadDocument(uri: Uri) {
        val fileName = getFileName(uri)
        mangaTitle.text = fileName

        pages = loadPdfAsBitmaps(uri)
        adapter = MangaPagerAdapter(pages, isDoublePage, isRTL)
        viewPager.adapter = adapter

        emptyView.visibility = View.GONE
        viewPager.visibility = View.VISIBLE
        currentPage = 0

        setupViewPager()
        updatePageIndicator()
    }

    private fun getFileName(uri: Uri): String {
        var name = "Manga"
        contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }

    private fun setupViewPager() {
        viewPager.orientation = if (isDoublePage) ViewPager2.ORIENTATION_VERTICAL else ViewPager2.ORIENTATION_HORIZONTAL
        viewPager.layoutDirection = if (isRTL) {
            View.LAYOUT_DIRECTION_RTL
        } else {
            View.LAYOUT_DIRECTION_LTR
        }
    }

    private fun updatePageIndicator() {
        if (isDoublePage) {
            pageIndicator.text = "${currentPage + 1} / ${(pages.size / 2) + 1}"
        } else {
            pageIndicator.text = "${currentPage + 1} / ${pages.size}"
        }
    }

    private fun navigateToPage(target: Int) {
        if (target in pages.indices) {
            viewPager.setCurrentItem(target, true)
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.settings_dialog, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.show()

        dialogView.findViewById<TextView>(R.id.openFileOption).setOnClickListener {
            dialog.dismiss()
            openFilePicker()
        }

        val btnRTL = dialogView.findViewById<RadioButton>(R.id.btnRTL)
        val btnLTR = dialogView.findViewById<RadioButton>(R.id.btnLTR)
        val btnSingle = dialogView.findViewById<RadioButton>(R.id.btnSinglePage)
        val btnDouble = dialogView.findViewById<RadioButton>(R.id.btnDoublePage)

        btnRTL.isChecked = isRTL
        btnLTR.isChecked = !isRTL
        btnSingle.isChecked = !isDoublePage
        btnDouble.isChecked = isDoublePage

        btnRTL.setOnClickListener {
            isRTL = true
            btnRTL.isChecked = isRTL
            btnLTR.isChecked = !isRTL
            reloadViewPager()
        }

        btnLTR.setOnClickListener {
            isRTL = false
            btnRTL.isChecked = isRTL
            btnLTR.isChecked = !isRTL
            reloadViewPager()
        }

        btnSingle.setOnClickListener {
            isDoublePage = false
            btnSingle.isChecked = !isDoublePage
            btnDouble.isChecked = isDoublePage
            reloadViewPager()
        }

        btnDouble.setOnClickListener {
            isDoublePage = true
            btnSingle.isChecked = !isDoublePage
            btnDouble.isChecked = isDoublePage
            reloadViewPager()
        }
    }

    private fun reloadViewPager() {
        val effectivePages = if (isDoublePage && isRTL) {
            pages.reversed()
        } else {
            pages
        }

        adapter = MangaPagerAdapter(effectivePages, isDoublePage, isRTL)
        viewPager.adapter = adapter
        setupViewPager()

        // Start at correct position
        val initialPage = if (isDoublePage && isRTL) {
            (effectivePages.size - 1) / 2
        } else 0

        viewPager.setCurrentItem(initialPage, false)
    }

    private fun loadPdfAsBitmaps(uri: Uri): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()

        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val renderer = PdfRenderer(descriptor)

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val width = page.width * 2 // upscale for better quality
                    val height = page.height * 2

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)

                    page.close()
                }

                renderer.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }

        return bitmaps
    }

}