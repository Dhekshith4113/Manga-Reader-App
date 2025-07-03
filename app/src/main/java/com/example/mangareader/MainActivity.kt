package com.example.mangareader

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.SharedFlow
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var mangaTitle: TextView
    private lateinit var pageIndicator: TextView
    private lateinit var emptyView: TextView
    private lateinit var settingsButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var zoomResetButton: Button

    private var mangaPages: MutableList<Bitmap> = mutableListOf()
    private var currentMangaTitle = "Manga Reader"
    private var isFullscreen = false
    private lateinit var mangaAdapter: MangaPagerAdapter
    private lateinit var gestureDetector: GestureDetector

    companion object {
        private const val PICK_FILE_REQUEST = 1
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupGestureDetector()
        setupViewPager()
        checkPermissions()
    }

    private fun initializeViews() {
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        viewPager = findViewById(R.id.viewPager)
        mangaTitle = findViewById(R.id.mangaTitle)
        pageIndicator = findViewById(R.id.pageIndicator)
        emptyView = findViewById(R.id.emptyView)
        settingsButton = findViewById(R.id.settingsButton)
        backButton = findViewById(R.id.backButton)
        zoomResetButton = findViewById(R.id.zoomResetButton)

        backButton.setOnClickListener { finish() }
        settingsButton.setOnClickListener { showSettingsDialog() }
        zoomResetButton.setOnClickListener { resetZoom() }

        emptyView.setOnClickListener { openFileChooser() }
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                toggleFullscreen()
            }
        })
    }

    private fun setupViewPager() {
        val isLandscapeMode = SharedPreferencesManager.isLandscapeModeEnabled(this)
        mangaAdapter = MangaPagerAdapter(mangaPages, isLandscapeMode)
        viewPager.adapter = mangaAdapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicator(position)
            }
        })
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ permissions
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.settings_dialog, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        if (SharedPreferencesManager.isLeftToRightEnabled(this)) {
            dialogView.findViewById<RadioButton>(R.id.btnLTR).isChecked = true
            dialogView.findViewById<RadioButton>(R.id.btnRTL).isChecked = false
        } else {
            dialogView.findViewById<RadioButton>(R.id.btnLTR).isChecked = false
            dialogView.findViewById<RadioButton>(R.id.btnRTL).isChecked = true
        }

        if (SharedPreferencesManager.isLandscapeModeEnabled(this)) {
            dialogView.findViewById<RadioButton>(R.id.btnSinglePage).isChecked = false
            dialogView.findViewById<RadioButton>(R.id.btnDoublePage).isChecked = true
        } else {
            dialogView.findViewById<RadioButton>(R.id.btnSinglePage).isChecked = true
            dialogView.findViewById<RadioButton>(R.id.btnDoublePage).isChecked = false
        }

        dialogView.findViewById<TextView>(R.id.openFileOption).setOnClickListener {
            openFileChooser()
            dialog.dismiss()
        }

        dialogView.findViewById<RadioButton>(R.id.btnRTL).setOnClickListener {
            SharedPreferencesManager.setLeftToRightEnabled(this, false)
            updateViewPagerDirection()
            dialog.dismiss()
        }

        dialogView.findViewById<RadioButton>(R.id.btnLTR).setOnClickListener {
            SharedPreferencesManager.setLeftToRightEnabled(this, true)
            updateViewPagerDirection()
            dialog.dismiss()
        }

        dialogView.findViewById<RadioButton>(R.id.btnSinglePage).setOnClickListener {
            SharedPreferencesManager.setLandscapeModeEnabled(this, false)
            updatePageMode()
            dialog.dismiss()
        }

        dialogView.findViewById<RadioButton>(R.id.btnDoublePage).setOnClickListener {
            SharedPreferencesManager.setLandscapeModeEnabled(this, true)
            updatePageMode()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateViewPagerDirection() {
        viewPager.layoutDirection = if (SharedPreferencesManager.isLeftToRightEnabled(this)) {
            View.LAYOUT_DIRECTION_LTR
        } else {
            View.LAYOUT_DIRECTION_RTL
        }
    }

    private fun updatePageMode() {
        mangaAdapter.updatePageMode(SharedPreferencesManager.isLandscapeModeEnabled(this))
    }

    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ))
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

        try {
            startActivityForResult(Intent.createChooser(intent, "Select Manga File"), PICK_FILE_REQUEST)
        } catch (e: Exception) {
            // Fallback to basic file picker
            val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(fallbackIntent, PICK_FILE_REQUEST)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                // Take persistable URI permission
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // Permission not available, but we can still try to read the file
                }
                loadMangaFromUri(uri)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied. Some features may not work.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadMangaFromUri(uri: Uri) {
        try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val displayName = it.getString(nameIndex)
                        currentMangaTitle = displayName?.substringBeforeLast('.') ?: "Manga"
                        mangaTitle.text = currentMangaTitle
                    }
                }
            }

            val inputStream = contentResolver.openInputStream(uri)
            mangaPages.clear()

            loadFromPdfFile(inputStream)

            if (mangaPages.isNotEmpty()) {
                emptyView.visibility = View.GONE
                viewPager.visibility = View.VISIBLE
                mangaAdapter.notifyDataSetChanged()
                updatePageIndicator(0)
                Toast.makeText(this, "Loaded ${mangaPages.size} pages", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No images found in the selected file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading manga: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Method 1: Using Android's built-in PDF renderer (API 21+)
    private fun loadFromPdfFile(inputStream: InputStream?) {
        inputStream?.let { stream ->
            try {
                // Create a temporary file for the PDF
                val tempFile = File.createTempFile("temp_pdf", ".pdf", cacheDir)
                tempFile.deleteOnExit()

                // Copy input stream to temporary file
                FileOutputStream(tempFile).use { output ->
                    stream.copyTo(output)
                }

                // Open PDF using PdfRenderer (requires API 21+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    val fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val pdfRenderer = PdfRenderer(fileDescriptor)

                    // Get screen dimensions for scaling
                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels

                    for (pageIndex in 0 until pdfRenderer.pageCount) {
                        val page = pdfRenderer.openPage(pageIndex)

                        // Calculate appropriate bitmap size to fit screen width
                        val originalWidth = page.width
                        val originalHeight = page.height

                        // Calculate scale factor to fit screen width
                        val scaleFactor = screenWidth.toFloat() / originalWidth.toFloat()

                        // Calculate new dimensions
                        val newWidth = (originalWidth * scaleFactor).toInt()
                        val newHeight = (originalHeight * scaleFactor).toInt()

                        // Ensure minimum quality by setting a minimum width
                        val minWidth = 800
                        val finalWidth = maxOf(newWidth, minWidth)
                        val finalHeight = (originalHeight * (finalWidth.toFloat() / originalWidth.toFloat())).toInt()

                        // Create bitmap with calculated size
                        val bitmap = Bitmap.createBitmap(
                            finalWidth,
                            finalHeight,
                            Bitmap.Config.ARGB_8888
                        )

                        // Render page to bitmap
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        mangaPages.add(bitmap)

                        page.close()
                    }

                    pdfRenderer.close()
                    fileDescriptor.close()
                } else {
                    // Fallback for older Android versions
                    Toast.makeText(this, "PDF support requires Android 5.0+", Toast.LENGTH_LONG).show()
                }

                // Clean up temporary file
                tempFile.delete()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error loading PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updatePageIndicator(position: Int) {
        val totalPages = if (SharedPreferencesManager.isLandscapeModeEnabled(this)) {
            (mangaPages.size + 1) / 2
        } else {
            mangaPages.size
        }
        pageIndicator.text = "${position + 1} / $totalPages"
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            topBar.visibility = View.GONE
            bottomBar.visibility = View.GONE
        } else {
            topBar.visibility = View.VISIBLE
            bottomBar.visibility = View.VISIBLE
        }
    }

    private fun resetZoom() {
        mangaAdapter.resetZoom()
    }

    private fun handleIntentFile(intent: Intent?) {
        intent?.data?.let { uri ->
            // Take persistable URI permission
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Permission not available, but we can still try to read the file
            }
            loadMangaFromUri(uri)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntentFile(intent)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }
}