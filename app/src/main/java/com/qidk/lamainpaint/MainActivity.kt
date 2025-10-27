package com.qidk.lamainpaint

import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.qidk.lamainpaint.tflite.AIHubDefaults
import com.qidk.lamainpaint.tflite.TFLiteHelpers
import java.util.HashSet

class MainActivity : AppCompatActivity() {

    private lateinit var statusTv: TextView
    private lateinit var drawingView: DrawingView
    private lateinit var outputView: ImageView
    private lateinit var selectImageBtn: Button
    private lateinit var clearMaskBtn: Button
    private lateinit var runBtn: Button
    private lateinit var clearResultBtn: Button
    private lateinit var backendRadioGroup: RadioGroup

    private var lamaInpainting: LamaInpainting? = null

    // Toggle this if colors look wrong: try -1..1 normalization instead of 0..1.
    private val normalizeToMinus1To1 = false

    private enum class BackendType {
        CPU, GPU, NPU
    }
    
    private var selectedBackend = BackendType.CPU // Default to CPU
    private val modelFilename = "lama.tflite" // TFLite model filename

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
        backendRadioGroup = findViewById(R.id.backendRadioGroup)

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

        // Handle backend selection changes
        backendRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedBackend = when (checkedId) {
                R.id.radioCpu -> BackendType.CPU
                R.id.radioGpu -> BackendType.GPU
                R.id.radioNpu -> BackendType.NPU
                else -> BackendType.CPU
            }
            
            val backendName = selectedBackend.name
            status("Switched to $backendName backend. Re-run to apply.")
            Log.i("LAMA", "Backend changed to: $backendName")
            
            // Clean up existing model and reload with new backend
            lamaInpainting?.close()
            lamaInpainting = null
        }

        status("Ready. Select an image and draw a mask.")
    }

    private fun runInpainting(inputBmp: Bitmap, maskBmp: Bitmap) {
        // Run inference on background thread to avoid ANR
        Thread {
            try {
                Log.i("LAMA", "Starting inpainting with TFLite on ${selectedBackend.name} backend")
                val startTime = System.currentTimeMillis()

                // Initialize model if not already done
                if (lamaInpainting == null) {
                    runOnUiThread { status("Loading TFLite model for ${selectedBackend.name}...") }
                    
                    // Try to initialize with selected backend, fallback to CPU if fails
                    var initSuccess = false
                    var attemptedBackend = selectedBackend
                    
                    while (!initSuccess) {
                        try {
                            // Determine which delegates to use based on selected backend
                            val enabledDelegates = HashSet<TFLiteHelpers.DelegateType>()
                            
                            when (attemptedBackend) {
                                BackendType.CPU -> {
                                    // Empty set = CPU-only (XNNPack)
                                    Log.i("LAMA", "Attempting CPU-only backend (XNNPack)")
                                }
                                BackendType.GPU -> {
                                    // GPU delegate only
                                    enabledDelegates.add(TFLiteHelpers.DelegateType.GPUv2)
                                    Log.i("LAMA", "Attempting GPU backend (GPUv2)")
                                }
                                BackendType.NPU -> {
                                    // NPU delegate only
                                    enabledDelegates.add(TFLiteHelpers.DelegateType.QNN_NPU)
                                    Log.i("LAMA", "Attempting NPU backend (QNN)")
                                }
                            }
                            
                            // Try to create LamaInpainting instance
                            lamaInpainting = LamaInpainting(
                                this,
                                modelFilename,
                                enabledDelegates,
                                normalizeToMinus1To1
                            )
                            
                            initSuccess = true
                            val backendMsg = if (attemptedBackend != selectedBackend) {
                                "Fallback: Model loaded with ${attemptedBackend.name} (${selectedBackend.name} failed)"
                            } else {
                                "Model loaded with ${attemptedBackend.name} backend"
                            }
                            runOnUiThread { status(backendMsg) }
                            Log.i("LAMA", "Model initialized successfully with ${attemptedBackend.name}")
                            
                        } catch (e: Throwable) {
                            Log.e("LAMA", "${attemptedBackend.name} backend failed: ${e.message}", e)
                            
                            // Check if it's an OOM error
                            val isOOM = e is OutOfMemoryError || 
                                       e.cause is OutOfMemoryError ||
                                       e.message?.contains("memory", ignoreCase = true) == true ||
                                       e.message?.contains("OutOfMemory", ignoreCase = true) == true
                            
                            if (isOOM) {
                                Log.e("LAMA", "Out of memory error detected")
                                runOnUiThread { 
                                    status("Out of memory! ${attemptedBackend.name} requires too much RAM. Trying CPU...") 
                                }
                                // Force garbage collection
                                System.gc()
                                Thread.sleep(1000)
                            }
                            
                            // Fallback logic
                            when (attemptedBackend) {
                                BackendType.GPU, BackendType.NPU -> {
                                    // Fallback to CPU
                                    runOnUiThread { 
                                        val reason = if (isOOM) "Out of memory" else "Initialization failed"
                                        status("${attemptedBackend.name} $reason, falling back to CPU...") 
                                    }
                                    attemptedBackend = BackendType.CPU
                                    Log.w("LAMA", "Falling back to CPU backend")
                                    Thread.sleep(500) // Brief delay before retry
                                }
                                BackendType.CPU -> {
                                    // CPU failed - this is fatal
                                    throw RuntimeException("CPU backend failed - cannot proceed", e)
                                }
                            }
                        }
                    }
                }

                runOnUiThread { status("Running inference on ${selectedBackend.name}...") }
                val result = lamaInpainting!!.inpaint(inputBmp, maskBmp)
                val outBitmap = result.first
                val inferenceTime = result.second

                val totalTime = System.currentTimeMillis() - startTime
                
                // Update UI on main thread
                runOnUiThread {
                    outputView.setImageBitmap(outBitmap)
                    status("Done. Inference: ${inferenceTime}ms, Total: ${totalTime}ms")
                }
                
                Log.i("LAMA", "Total time: $totalTime ms, Inference: $inferenceTime ms, Backend: ${selectedBackend.name}")
            } catch (t: Throwable) {
                Log.e("LAMA", "Fatal error on ${selectedBackend.name} backend", t)
                runOnUiThread { 
                    status("Error: ${t.message ?: "Unknown error"}. Try CPU backend.") 
                }
                
                // Clean up failed instance
                try {
                    lamaInpainting?.close()
                } catch (e: Exception) {
                    Log.e("LAMA", "Error closing failed model", e)
                }
                lamaInpainting = null
            }
        }.start()
    }

    // --- Utilities ---

    private fun status(msg: String) {
        statusTv.text = "Status: $msg"
    }

    override fun onDestroy() {
        super.onDestroy()
        lamaInpainting?.close()
    }
}