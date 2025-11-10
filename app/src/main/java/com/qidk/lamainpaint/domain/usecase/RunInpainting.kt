package com.qidk.lamainpaint.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Pair
import com.qidk.lamainpaint.LamaInpainting
import com.qidk.lamainpaint.domain.model.Backend
import com.qidk.lamainpaint.domain.model.RunResult
import com.qidk.lamainpaint.tflite.TFLiteHelpers
import java.util.HashSet

/**
 * Use case to run inpainting inference.
 */
class RunInpainting(private val context: Context) {
    
    companion object {
        private const val TAG = "RunInpainting"
        private const val MODEL_FILENAME = "lama.tflite"
        private const val NORMALIZE_TO_MINUS_1_TO_1 = false
    }
    
    private var lamaInpainting: LamaInpainting? = null
    private var currentBackend: Backend? = null
    
    /**
     * Execute inpainting.
     * 
     * @param input512 512×512 input bitmap
     * @param mask512 512×512 mask bitmap
     * @param backend Backend to use (CPU/GPU/NPU)
     * @param runId Unique run identifier
     * @return Pair of (result bitmap, RunResult with timing info)
     */
    fun execute(
        input512: Bitmap,
        mask512: Bitmap,
        backend: Backend,
        runId: String
    ): Pair<Bitmap, RunResult> {
        val startTime = System.currentTimeMillis()
        
        // Initialize model if needed or backend changed
        if (lamaInpainting == null || currentBackend != backend) {
            lamaInpainting?.close()
            lamaInpainting = null
            
            Log.i(TAG, "Initializing model with backend: $backend")
            
            // Determine delegates based on backend
            val enabledDelegates = HashSet<TFLiteHelpers.DelegateType>()
            when (backend) {
                Backend.CPU -> {
                    // Empty set = CPU-only (XNNPack)
                    Log.i(TAG, "Using CPU backend (XNNPack)")
                }
                Backend.GPU -> {
                    enabledDelegates.add(TFLiteHelpers.DelegateType.GPUv2)
                    Log.i(TAG, "Using GPU backend")
                }
                Backend.NPU -> {
                    enabledDelegates.add(TFLiteHelpers.DelegateType.QNN_NPU)
                    Log.i(TAG, "Using NPU backend")
                }
            }
            
            try {
                lamaInpainting = LamaInpainting(
                    context,
                    MODEL_FILENAME,
                    enabledDelegates,
                    NORMALIZE_TO_MINUS_1_TO_1
                )
                currentBackend = backend
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize model with $backend backend", e)
                
                // Fallback to CPU if not already trying CPU
                if (backend != Backend.CPU) {
                    Log.w(TAG, "Falling back to CPU backend")
                    lamaInpainting = LamaInpainting(
                        context,
                        MODEL_FILENAME,
                        HashSet(),  // Empty = CPU
                        NORMALIZE_TO_MINUS_1_TO_1
                    )
                    currentBackend = Backend.CPU
                } else {
                    throw RuntimeException("Failed to initialize CPU backend", e)
                }
            }
        }
        
        // Run inference
        val result = lamaInpainting!!.inpaint(input512, mask512)
        val outputBitmap = result.first
        val inferenceMs = result.second
        
        val totalMs = System.currentTimeMillis() - startTime
        
        Log.i(TAG, "Inference completed: ${inferenceMs}ms inference, ${totalMs}ms total")
        
        val runResult = RunResult(
            id = runId,
            backend = currentBackend ?: backend,
            inferenceMs = inferenceMs,
            totalMs = totalMs,
            outputPath512 = ""  // Will be filled in after saving
        )
        
        return Pair(outputBitmap, runResult)
    }
    
    /**
     * Clean up resources.
     */
    fun cleanup() {
        lamaInpainting?.close()
        lamaInpainting = null
        currentBackend = null
    }
}
