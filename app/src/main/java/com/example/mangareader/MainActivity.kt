package com.example.mangareader

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
        SharedPreferencesManager.saveUriString(this, uri)
        uri?.let { loadDocument(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        isRTL = SharedPreferencesManager.isLTR(this)
        isDoublePage = SharedPreferencesManager.isDoublePageEnabled(this)

        initViews()
        setupListeners()

        val action = intent?.action
        val data = intent?.data

        if ((Intent.ACTION_VIEW == action || Intent.ACTION_SEND == action) && data != null) {
            SharedPreferencesManager.saveUriString(this, data)
            try {
                contentResolver.takePersistableUriPermission(
                    data, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Toast.makeText(this, "This file is not accessible", Toast.LENGTH_SHORT).show()
            }
            loadDocument(data)
        }

        if (SharedPreferencesManager.isLoadFileEnabled(this)) {
            val uriString = SharedPreferencesManager.loadUriString(this)

            if (!uriString.isNullOrEmpty()) {
                val uri = Uri.parse(uriString)
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    Toast.makeText(this, "This file is not accessible", Toast.LENGTH_SHORT).show()
                }

                Handler(Looper.getMainLooper()).post {
                    loadDocument(uri!!)
                }
            }
        }
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        emptyView = findViewById(R.id.emptyView)
        mangaTitle = findViewById(R.id.mangaTitle)
        pageIndicator = findViewById(R.id.pageIndicator)

        adapter = MangaPagerAdapter(emptyList(), isDoublePage)
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
        adapter = MangaPagerAdapter(pages, isDoublePage)
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
        viewPager.layoutDirection = if (isRTL) {
            View.LAYOUT_DIRECTION_RTL
        } else {
            View.LAYOUT_DIRECTION_LTR
        }
        requestedOrientation = if (isDoublePage) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        SharedPreferencesManager.setDoublePageEnabled(this, isDoublePage)
        SharedPreferencesManager.setRTL(this, isRTL)
        // Force recreation after a slight delay to allow orientation to take effect
        if (SharedPreferencesManager.isRecreateEnabled(this)) {
            SharedPreferencesManager.setRecreateEnabled(this, false)
            SharedPreferencesManager.setLoadFileEnabled(this, true)
            Handler(Looper.getMainLooper()).postDelayed({
                recreate()
            }, 200)
        }
    }

    private fun updatePageIndicator() {
        if (isDoublePage) {
            pageIndicator.text = "${currentPage + 1}\n-\n${(pages.size / 2) + 1}"
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
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
            SharedPreferencesManager.setRTL(this, true)
            btnRTL.isChecked = isRTL
            btnLTR.isChecked = !isRTL
            reloadViewPager()
        }

        btnLTR.setOnClickListener {
            isRTL = false
            SharedPreferencesManager.setRTL(this, false)
            btnRTL.isChecked = isRTL
            btnLTR.isChecked = !isRTL
            reloadViewPager()
        }

        btnSingle.setOnClickListener {
            isDoublePage = false
            SharedPreferencesManager.setDoublePageEnabled(this, false)
            SharedPreferencesManager.setRecreateEnabled(this, true)
            btnSingle.isChecked = !isDoublePage
            btnDouble.isChecked = isDoublePage
            reloadViewPager()
        }

        btnDouble.setOnClickListener {
            isDoublePage = true
            SharedPreferencesManager.setDoublePageEnabled(this, true)
            SharedPreferencesManager.setRecreateEnabled(this, true)
            btnSingle.isChecked = !isDoublePage
            btnDouble.isChecked = isDoublePage
            reloadViewPager()
        }

        dialog.show()

    }

    private fun reloadViewPager() {
        adapter = MangaPagerAdapter(pages, isDoublePage)
        viewPager.adapter = adapter
        setupViewPager()
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