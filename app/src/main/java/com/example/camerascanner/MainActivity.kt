package com.example.camerascanner

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.camerascanner.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var scannedImageUri: Uri? = null

    // Register activity result launcher for CameraActivity
    private val scanDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                displayScannedImage(uri)
            }
        }
    }

    // Register activity result launcher for Gallery Picker
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                processGalleryImage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize OpenCV
        OpenCVLoader.initDebug()

        binding.btnScan.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            scanDocumentLauncher.launch(intent)
        }

        binding.btnGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        binding.btnSave.setOnClickListener {
            saveImageToGallery()
        }

        binding.btnShare.setOnClickListener {
            shareImage()
        }
    }

    private fun displayScannedImage(uri: Uri) {
        scannedImageUri = uri
        binding.ivPreview.setImageURI(uri)
        binding.tvPlaceholder.visibility = View.GONE
        binding.scannedOptionsLayout.visibility = View.VISIBLE
    }

    private fun processGalleryImage(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show()
                return
            }

            // Convert to OpenCV Mat
            val srcMat = Mat()
            Utils.bitmapToMat(originalBitmap, srcMat)

            // Warp perspective using OpenCV
            val resultMat = DocumentScanner.scanDocument(srcMat)

            // Convert back to Bitmap
            val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultMat, resultBitmap)

            // Save to temp file
            val tempFile = File(cacheDir, "scanned_doc.jpg")
            FileOutputStream(tempFile).use { out ->
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            srcMat.release()
            resultMat.release()

            displayScannedImage(Uri.fromFile(tempFile))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToGallery() {
        val uri = scannedImageUri ?: return
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val filename = "Scan_${System.currentTimeMillis()}.jpg"
            var outputStream: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/ScannerPro")
                }
                val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    outputStream = contentResolver.openOutputStream(imageUri)
                }
            } else {
                val imagesDir = File(getExternalFilesDir(null), "ScannerPro")
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                val image = File(imagesDir, filename)
                outputStream = FileOutputStream(image)
            }

            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                Toast.makeText(this, "Saved to Gallery successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage() {
        val uri = scannedImageUri ?: return
        val file = File(uri.path ?: return)
        
        try {
            val contentUri = FileProvider.getUriForFile(
                this,
                "com.example.camerascanner.fileprovider",
                file
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "image/jpeg"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Scanned Document"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to share image", Toast.LENGTH_SHORT).show()
        }
    }
}
