package com.example.camerascanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.camerascanner.databinding.ActivityCameraBinding
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("CameraActivity", "OpenCV initialization failed!")
        } else {
            Log.d("CameraActivity", "OpenCV initialized successfully.")
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Setup UI
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCapture.setOnClickListener { takePhoto() }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // ImageCapture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // Real-time edge detection Analyzer
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraActivity", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val mat = imageProxy.toMat()
            val points = DocumentScanner.detectDocumentContour(mat)
            
            runOnUiThread {
                // If points are found, draw the overlay box
                if (points != null) {
                    binding.quadOverlay.setPoints(points, mat.width(), mat.height())
                } else {
                    binding.quadOverlay.setPoints(null, 1, 1)
                }
            }
            mat.release()
        } catch (e: Exception) {
            Log.e("CameraActivity", "Error analyzing image", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    cameraExecutor.execute {
                        processCapturedImage(image)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraActivity", "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun processCapturedImage(image: ImageProxy) {
        try {
            // Convert to bitmap and handle orientation rotation
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            
            // Adjust orientation if needed
            val rotationDegrees = image.imageInfo.rotationDegrees
            val correctedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            // Convert to OpenCV Mat
            val srcMat = Mat()
            Utils.bitmapToMat(correctedBitmap, srcMat)

            // Warp Perspective
            val resultMat = DocumentScanner.scanDocument(srcMat)

            // Convert back to Bitmap
            val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultMat, resultBitmap)

            // Save warped bitmap to a temp file and return result
            val tempFile = File(cacheDir, "scanned_doc.jpg")
            FileOutputStream(tempFile).use { out ->
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            srcMat.release()
            resultMat.release()
            image.close()

            val resultIntent = Intent().apply {
                data = Uri.fromFile(tempFile)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()

        } catch (e: Exception) {
            Log.e("CameraActivity", "Error processing capture", e)
            runOnUiThread {
                Toast.makeText(this, "Failed to scan document", Toast.LENGTH_SHORT).show()
            }
            image.close()
        }
    }

    // Helper: Convert ImageProxy (YUV_420_888) to Mat
    private fun ImageProxy.toMat(): Mat {
        val nv21 = yuv420ToNv21(this)
        val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)
        val rgbaMat = Mat()
        Imgproc.cvtColor(yuvMat, rgbaMat, Imgproc.COLOR_YUV2RGBA_NV21)

        val rotationDegrees = this.imageInfo.rotationDegrees
        val rotatedMat = Mat()
        return when (rotationDegrees) {
            90 -> {
                Core.rotate(rgbaMat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
                rgbaMat.release()
                yuvMat.release()
                rotatedMat
            }
            180 -> {
                Core.rotate(rgbaMat, rotatedMat, Core.ROTATE_180)
                rgbaMat.release()
                yuvMat.release()
                rotatedMat
            }
            270 -> {
                Core.rotate(rgbaMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
                rgbaMat.release()
                yuvMat.release()
                rotatedMat
            }
            else -> {
                yuvMat.release()
                rgbaMat
            }
        }
    }

    private fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val nv21: ByteArray
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        return nv21
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
