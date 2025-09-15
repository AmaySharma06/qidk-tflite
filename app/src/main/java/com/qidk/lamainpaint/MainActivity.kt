package com.qidk.lamainpaint

import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var statusTv: TextView
    private lateinit var drawingView: DrawingView
    private lateinit var outputView: ImageView
    private lateinit var selectImageBtn: Button
    private lateinit var clearMaskBtn: Button
    private lateinit var runBtn: Button
    private lateinit var clearResultBtn: Button
    private lateinit var toggleBackendBtn: Button

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // Toggle this if colors look wrong: try -1..1 normalization instead of 0..1.
    private val normalizeToMinus1To1 = false // Renamed variable

    private var currentBackend = "htp" // Track current backend

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val inputStream = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            drawingView.setImage(bitmap)
            outputView.setImageBitmap(null) // Clear previous result
            status("Image selected. Draw mask (black to remove).")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTv = findViewById(R.id.status)
        drawingView = findViewById(R.id.drawingView)
        outputView = findViewById(R.id.outputView)
        selectImageBtn = findViewById(R.id.selectImageBtn)
        clearMaskBtn = findViewById(R.id.clearMaskBtn)
        runBtn = findViewById(R.id.runBtn)
        clearResultBtn = findViewById(R.id.clearResultBtn)
        toggleBackendBtn = findViewById(R.id.toggleBackendBtn)

        selectImageBtn.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        clearMaskBtn.setOnClickListener {
            drawingView.clearMask()
            status("Mask cleared.")
        }

        runBtn.setOnClickListener {
            val inputBmp = drawingView.bitmap ?: run {
                status("No image selected.")
                return@setOnClickListener
            }
            val maskBmp = drawingView.getMaskBitmap() ?: run {
                status("No mask available.")
                return@setOnClickListener
            }
            runInpainting(inputBmp, maskBmp)
        }

        clearResultBtn.setOnClickListener {
            outputView.setImageBitmap(null)
            status("Result cleared.")
        }

        toggleBackendBtn.setOnClickListener {
            currentBackend = if (currentBackend == "htp") "cpu" else "htp"
            toggleBackendBtn.text = "Toggle Backend (${currentBackend.uppercase()})"
            status("Switched to $currentBackend backend. Restart app or re-run to apply.")
        }

        // Set initial button text
        toggleBackendBtn.text = "Toggle Backend (${currentBackend.uppercase()})"
    }

    private fun runInpainting(inputBmp: Bitmap, maskBmp: Bitmap) {
        try {
            status("Copying model and assets…")
            val modelDir = File(filesDir, "qaihub/lama").apply { mkdirs() }
            val onnxFile = copyAssetToFile("qaihub/lama/model.onnx", File(modelDir, "model.onnx"))
            copyAssetToFile("qaihub/lama/model.data", File(modelDir, "model.data")) // keep sibling name

            Log.i("LAMA", "Starting time calculation for LAMA");
            val startTime = System.currentTimeMillis()

            status("Initializing ONNX Runtime…")
            initOrtQnn(onnxFile.absolutePath)

            status("Preprocessing…")
            val targetW = 512
            val targetH = 512
            val resizedInput = inputBmp.scaleTo(targetW, targetH)
            val resizedMask  = maskBmp.scaleTo(targetW, targetH)

            val imageTensor = bitmapToCHWFloat(resizedInput, normalizeMinus1To1 = normalizeToMinus1To1)
            val maskTensor  = maskBitmapToCHWFloat(resizedMask) // 1x1xHxW, {1.0 = hole, 0.0 = keep}

            status("Running inference… (QNN/CPU auto)")
            val outputs = runSession(imageTensor, maskTensor)

            status("Postprocessing…")
            val outBitmap = chwToBitmap(outputs, targetW, targetH, denormalizedFromMinus1To1 = normalizeToMinus1To1)

            outputView.setImageBitmap(outBitmap)
            status("Done.")
            Log.i("LAMA", "Total time taken: ${System.currentTimeMillis() - startTime} ms")
        } catch (t: Throwable) {
            Log.e("LAMA", "Error", t)
            status("Error: ${t.message}")
        }
    }

    private fun initOrtQnn(modelPath: String) {
        // Create environment
        ortEnv?.close()
        ortEnv = OrtEnvironment.getEnvironment()

        val so = OrtSession.SessionOptions()

        // Set QNN options in a map (without prefix) and add the QNN EP.
        try {
            val backendType = currentBackend
            if (backendType == "htp") {
                val qnnOptions = mapOf(
                    "backend_type" to backendType,
                    // Simplified options for better compatibility
                    "htp_performance_mode" to "sustained_high_performance",
                    "enable_htp_fp16_precision" to "0"  // Try FP32 first
                )
                so.addQnn(qnnOptions)
            } else {
                // For CPU, no special options needed
                Log.i("LAMA", "Using CPU backend")
            }
            status("EP options set for backend: $backendType.")
            Log.i("LAMA", "Model set to run on $backendType")
        } catch (e: Exception) {
            Log.w("LAMA", "Failed to set EP options: ${e.message}")
            status("Failed to set EP options.")
        }

        // Create session
        ortSession?.close()
        ortSession = ortEnv!!.createSession(modelPath, so)
        Log.i("LAMA", "Model inputs: ${ortSession!!.inputNames}")
    }

    private fun runSession(imageCHW: FloatBuffer, maskCHW: FloatBuffer): FloatBuffer {
        val session = ortSession ?: error("ORT session not initialized")

        val shapeImage = longArrayOf(1, 3, 512, 512)
        val shapeMask  = longArrayOf(1, 1, 512, 512)

        val imageTensor = OnnxTensor.createTensor(ortEnv, imageCHW, shapeImage)
        val maskTensor  = OnnxTensor.createTensor(ortEnv, maskCHW, shapeMask)

        // Try to bind by common names; if unknown, use first/second names from the model
        val inputNames = session.inputNames.toList()
        val nameImage = inputNames.find { it.equals("image", true) } ?: inputNames.getOrNull(0) ?: "image"
        val nameMask  = inputNames.find { it.equals("mask", true) }  ?: inputNames.getOrNull(1) ?: "mask"

        val inputs = mapOf(
            nameImage to imageTensor as OnnxTensor,
            nameMask  to maskTensor as OnnxTensor
        )

        return session.run(inputs).use { result: OrtSession.Result ->
            // Assume single output; get as FloatBuffer
            val out = result[0] as OnnxTensor
            val fb = out.floatBuffer
            // Duplicate into a direct buffer we own (result will be closed on return)
            val copy = FloatBuffer.allocate(fb.remaining())
            copy.put(fb)
            copy.rewind()
            copy
        }
    }

    // --- Utilities ---

    private fun status(msg: String) {
        statusTv.text = "Status: $msg"
    }

    private fun copyAssetToFile(assetPath: String, dst: File): File {
        assets.open(assetPath).use { input ->
            FileOutputStream(dst).use { out ->
                input.copyTo(out)
            }
        }
        return dst
    }

    private fun Bitmap.scaleTo(w: Int, h: Int): Bitmap =
        Bitmap.createScaledBitmap(this, w, h, true)

    private fun bitmapToCHWFloat(bmp: Bitmap, normalizeMinus1To1: Boolean): FloatBuffer {
        val w = bmp.width
        val h = bmp.height
        val out = FloatBuffer.allocate(1 * 3 * h * w)
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        // CHW order: R plane, then G, then B
        val planeSize = w * h
        var rOff = 0
        var gOff = planeSize
        var bOff = 2 * planeSize

        // write into an array then put into buffer for speed
        val arr = FloatArray(3 * planeSize)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                var r = ((c shr 16) and 0xFF) / 255f
                var g = ((c shr 8) and 0xFF) / 255f
                var b = (c and 0xFF) / 255f

                if (normalizeMinus1To1) {
                    r = r * 2f - 1f
                    g = g * 2f - 1f
                    b = b * 2f - 1f
                }

                val idx = y * w + x
                arr[rOff + idx] = r
                arr[gOff + idx] = g
                arr[bOff + idx] = b
            }
        }
        out.put(arr)
        out.rewind()
        return out
    }

    private fun maskBitmapToCHWFloat(maskBmp: Bitmap): FloatBuffer {
        val w = maskBmp.width
        val h = maskBmp.height
        val out = FloatBuffer.allocate(1 * 1 * h * w)
        val arr = FloatArray(w * h)

        val pixels = IntArray(w * h)
        maskBmp.getPixels(pixels, 0, w, 0, 0, w, h)

        // Convert to {1.0 = hole, 0.0 = keep}. White(>=128) => 1.
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val gray = (0.299f * r + 0.587f * g + 0.114f * b)
                arr[y * w + x] = if (gray < 128f) 1f else 0f
            }
        }
        out.put(arr)
        out.rewind()
        return out
    }

    private fun chwToBitmap(chw: FloatBuffer, w: Int, h: Int, denormalizedFromMinus1To1: Boolean): Bitmap {
        val planeSize = w * h
        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(planeSize)
        val arr = FloatArray(chw.remaining())
        chw.get(arr)

        val rOff = 0
        val gOff = planeSize
        val bOff = 2 * planeSize

        for (i in 0 until planeSize) {
            var r = arr[rOff + i]
            var g = arr[gOff + i]
            var b = arr[bOff + i]

            if (denormalizedFromMinus1To1) {
                r = (r + 1f) * 0.5f
                g = (g + 1f) * 0.5f
                b = (b + 1f) * 0.5f
            }

            val ri = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val gi = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val bi = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            pixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
        outBmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return outBmp
    }

    override fun onDestroy() {
        super.onDestroy()
        ortSession?.close()
        ortEnv?.close()
    }
}