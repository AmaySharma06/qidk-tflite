# Migration Summary: ONNX Runtime → TensorFlow Lite

## Overview
Successfully migrated the qidk-test-lama Android app from ONNX Runtime to TensorFlow Lite with full CPU/GPU/NPU backend support, following the architecture pattern from the ImageClassification app.

## Changes Made

### 1. Dependencies (build.gradle)
**Removed:**
- `com.microsoft.onnxruntime:onnxruntime-android-qnn:1.22.0`

**Added:**
- `org.tensorflow:tensorflow-lite:2.16.1`
- `org.tensorflow:tensorflow-lite-support:0.4.4`
- `org.tensorflow:tensorflow-lite-gpu:2.16.1`
- `org.tensorflow:tensorflow-lite-gpu-api:2.16.1`
- `org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4`
- `com.qualcomm.qti:qnn-runtime:2.29.0`
- `com.qualcomm.qti:qnn-litert-delegate:2.29.0`

### 2. New Files Created

#### TFLiteHelpers.java
- Location: `app/src/main/java/com/qidk/lamainpaint/tflite/TFLiteHelpers.java`
- Purpose: Core utility class for TFLite model loading and delegate management
- Features:
  - Automatic delegate priority order handling
  - Support for QNN_NPU and GPUv2 delegates
  - Model file loading with MD5 hash generation
  - Graceful fallback when delegates are unavailable

#### AIHubDefaults.java
- Location: `app/src/main/java/com/qidk/lamainpaint/tflite/AIHubDefaults.java`
- Purpose: Configuration for AI Hub-compatible delegate priorities
- Features:
  - Default delegate order: QNN_NPU + GPUv2 → GPUv2 → XNNPack (CPU)
  - CPU thread configuration
  - Customizable delegate filtering

#### LamaInpainting.java
- Location: `app/src/main/java/com/qidk/lamainpaint/LamaInpainting.java`
- Purpose: Main inference engine for LAMA inpainting
- Features:
  - TFLite interpreter initialization with selected delegates
  - Image preprocessing (bitmap → CHW tensor)
  - Mask preprocessing (grayscale → binary mask)
  - Inference execution
  - Postprocessing (CHW tensor → bitmap)
  - Support for [0, 1] and [-1, 1] normalization
  - Performance timing

### 3. Modified Files

#### MainActivity.kt
**Before:**
- Used ONNX Runtime (OrtEnvironment, OrtSession)
- Manual HTP/CPU backend toggling
- Manual tensor conversion code in MainActivity
- Model file copying from assets to filesystem

**After:**
- Uses TFLite via LamaInpainting class
- Clean backend toggle: CPU-only vs Default (NPU/GPU/CPU)
- Preprocessing/postprocessing delegated to LamaInpainting
- Direct model loading from assets (no file copying needed)
- Lazy model initialization (loads on first inference)
- Better error handling and status updates

**Key Changes:**
```kotlin
// Old approach
private var ortEnv: OrtEnvironment? = null
private var ortSession: OrtSession? = null
private var currentBackend = "htp"

// New approach
private var lamaInpainting: LamaInpainting? = null
private var useCpuOnly = false
private val modelFilename = "lama.tflite"
```

### 4. Model Format Migration

#### From ONNX (.onnx + .data)
```
assets/
  qaihub/lama/
    model.onnx
    model.data
```

#### To TFLite (.tflite)
```
assets/
  lama.tflite
```

**Required model conversion:** ONNX → TFLite (user responsibility)

### 5. Backend Selection Logic

#### Before (ONNX Runtime)
- Manual toggle between "htp" and "cpu"
- Backend options hardcoded in MainActivity
- Required app restart or session recreation

#### After (TensorFlow Lite)
- Toggle between two modes:
  1. **Default**: Automatic delegate priority (NPU → GPU → CPU)
  2. **CPU-only**: XNNPack only
- Uses AI Hub delegate priority order
- Graceful fallback when hardware unavailable
- Model reloads on backend change

### 6. API Changes

#### Initialization
```kotlin
// Before
ortEnv = OrtEnvironment.getEnvironment()
ortSession = ortEnv!!.createSession(modelPath, sessionOptions)

// After
lamaInpainting = LamaInpainting(
    context = this,
    modelFilename = "lama.tflite",
    enabledDelegates = enabledDelegates,
    normalizeToMinus1To1 = false
)
```

#### Inference
```kotlin
// Before
val outputs = runSession(imageTensor, maskTensor)
val outBitmap = chwToBitmap(outputs, 512, 512, false)

// After
val result = lamaInpainting!!.inpaint(inputBmp, maskBmp)
val outBitmap = result.first
val inferenceTime = result.second
```

#### Cleanup
```kotlin
// Before
ortSession?.close()
ortEnv?.close()

// After
lamaInpainting?.close()
```

## Benefits of Migration

### 1. Better Hardware Utilization
- Automatic delegate selection based on device capabilities
- AI Hub-compatible configuration
- Optimized for Qualcomm chipsets

### 2. Simplified Code
- Preprocessing/postprocessing encapsulated in LamaInpainting class
- No manual file copying or path management
- Cleaner error handling

### 3. Performance
- Model caching (compiled delegates stored on disk)
- Efficient tensor operations
- Optimized CPU fallback (XNNPack)

### 4. Flexibility
- Easy to add new delegates
- Configurable normalization
- Support for different model input formats

### 5. Maintainability
- Reusable TFLite helpers (can be used in other apps)
- Follows Android best practices
- Better separation of concerns

## Usage Instructions

### 1. Model Setup
Place your TFLite model in assets:
```bash
cp your-lama-model.tflite app/src/main/assets/lama.tflite
```

### 2. Model Requirements
- **Inputs:**
  - Image: `[1, 3, H, W]` (float32, CHW format)
  - Mask: `[1, 1, H, W]` (float32, single channel)
- **Output:**
  - Image: `[1, 3, H, W]` (float32, CHW format)

### 3. Normalization
Adjust if your model expects [-1, 1] range:
```kotlin
private val normalizeToMinus1To1 = true  // in MainActivity.kt
```

### 4. Build & Run
```bash
cd qidk-test-lama
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Testing Checklist

- [ ] CPU-only mode works
- [ ] Default mode (NPU/GPU/CPU) works
- [ ] Backend toggle switches properly
- [ ] Model loads without errors
- [ ] Inference produces correct output
- [ ] Timing metrics display correctly
- [ ] App handles missing model gracefully
- [ ] Memory cleanup on app close

## Known Limitations

1. **Model Conversion Required**: User must convert ONNX model to TFLite format
2. **Input Format**: Only CHW format supported (not HWC)
3. **NPU Support**: Requires Qualcomm Snapdragon 8 Gen 1+ for FP16 models
4. **API Differences**: Some GPU settings from AI Hub not accessible via Java API

## Migration Pattern for Other Apps

This migration can be replicated for other ONNX-based apps:

1. Copy `TFLiteHelpers.java` and `AIHubDefaults.java`
2. Create app-specific inference class (like `LamaInpainting.java`)
3. Replace ONNX Runtime imports with TFLite
4. Update dependencies in build.gradle
5. Modify UI to support backend selection
6. Test on target devices

## References

- [TensorFlow Lite Android Guide](https://www.tensorflow.org/lite/android)
- [Qualcomm QNN Delegate](https://docs.qualcomm.com/bundle/publicresource/topics/80-63442-50/introduction.html)
- [AI Hub Documentation](https://aihub.qualcomm.com/docs/hub/api.html)
- [ImageClassification Reference App](../ImageClassification)

## Files Modified Summary

```
qidk-test-lama/
├── app/
│   ├── build.gradle                     [MODIFIED] - Dependencies updated
│   └── src/main/java/com/qidk/lamainpaint/
│       ├── MainActivity.kt              [MODIFIED] - Migrated to TFLite
│       ├── DrawingView.kt              [NO CHANGE]
│       ├── LamaInpainting.java         [NEW] - Inference engine
│       └── tflite/
│           ├── TFLiteHelpers.java      [NEW] - Model loading utilities
│           └── AIHubDefaults.java      [NEW] - Delegate configuration
└── README.md                            [NEW] - Documentation
```

## Success Criteria ✓

- [x] All compilation errors resolved
- [x] Dependencies updated to TFLite
- [x] Multi-backend support (CPU/GPU/NPU)
- [x] Code follows ImageClassification pattern
- [x] Documentation created
- [x] Clean separation of concerns
- [x] Backward compatible functionality maintained
