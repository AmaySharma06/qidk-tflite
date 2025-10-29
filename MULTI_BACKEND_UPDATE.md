# Multi-Backend Support Update

## Overview
Updated the qidk-test-lama app to support **individual backend selection** with three dedicated options: **CPU**, **GPU**, and **NPU**.

## Changes Made

### 1. Updated UI Layout ✅
**File:** `app/src/main/res/layout/activity_main.xml`

**Before:**
- Single toggle button: "Toggle Backend (HTP/CPU)"
- Binary choice: CPU vs Default (NPU+GPU+CPU)

**After:**
- RadioGroup with 3 options:
  - **CPU** (XNNPack)
  - **GPU** (GPUv2 delegate)
  - **NPU** (QNN delegate)
- Horizontal layout for easy selection
- CPU selected by default

```xml
<RadioGroup
    android:id="@+id/backendRadioGroup"
    android:orientation="horizontal">
    
    <RadioButton android:id="@+id/radioCpu" android:text="CPU" android:checked="true"/>
    <RadioButton android:id="@+id/radioGpu" android:text="GPU"/>
    <RadioButton android:id="@+id/radioNpu" android:text="NPU"/>
</RadioGroup>
```

### 2. Updated MainActivity Logic ✅
**File:** `app/src/main/java/com/qidk/lamainpaint/MainActivity.kt`

#### Added Backend Enum
```kotlin
private enum class BackendType {
    CPU, GPU, NPU
}
private var selectedBackend = BackendType.CPU
```

#### Updated UI References
```kotlin
// Removed
private lateinit var toggleBackendBtn: Button
private var useCpuOnly = false

// Added
private lateinit var backendRadioGroup: RadioGroup
private var selectedBackend = BackendType.CPU
```

#### Backend Selection Handler
```kotlin
backendRadioGroup.setOnCheckedChangeListener { _, checkedId ->
    selectedBackend = when (checkedId) {
        R.id.radioCpu -> BackendType.CPU
        R.id.radioGpu -> BackendType.GPU
        R.id.radioNpu -> BackendType.NPU
        else -> BackendType.CPU
    }
    
    // Clean up and force reload on next inference
    lamaInpainting?.close()
    lamaInpainting = null
}
```

#### Updated Delegate Selection Logic
```kotlin
val enabledDelegates = HashSet<TFLiteHelpers.DelegateType>()

when (selectedBackend) {
    BackendType.CPU -> {
        // Empty set = CPU-only (XNNPack)
    }
    BackendType.GPU -> {
        enabledDelegates.add(TFLiteHelpers.DelegateType.GPUv2)
    }
    BackendType.NPU -> {
        enabledDelegates.add(TFLiteHelpers.DelegateType.QNN_NPU)
    }
}
```

## Backend Details

### 🔹 CPU Backend (XNNPack)
- **Delegate:** None (uses XNNPack by default)
- **Performance:** Slower but most compatible
- **Use case:** Fallback, debugging, or devices without GPU/NPU
- **Expected time:** 15-30 seconds for 512x512
- **Availability:** ✅ All devices

### 🔹 GPU Backend (GPUv2)
- **Delegate:** `TFLiteHelpers.DelegateType.GPUv2`
- **Performance:** Medium-fast
- **Use case:** Devices with powerful GPUs, FP16 models
- **Expected time:** 5-10 seconds for 512x512
- **Availability:** ✅ Most Android devices with OpenGL ES 3.1+

### 🔹 NPU Backend (QNN)
- **Delegate:** `TFLiteHelpers.DelegateType.QNN_NPU`
- **Performance:** Fastest, most power-efficient
- **Use case:** Qualcomm Snapdragon devices with HTP
- **Expected time:** 1-3 seconds for 512x512
- **Availability:** ⚠️ Qualcomm Snapdragon 8 Gen 1+ (FP16 models)

## Usage Flow

1. **Launch app**
2. **Select backend:**
   - Tap CPU, GPU, or NPU radio button
   - Status shows: "Switched to [BACKEND] backend. Re-run to apply."
3. **Select image and draw mask**
4. **Tap "Run Inpainting"**
5. **Model loads with selected backend:**
   - Status: "Loading TFLite model for [BACKEND]..."
   - Status: "Model loaded with [BACKEND] backend"
6. **Inference runs:**
   - Status: "Running inference on [BACKEND]..."
7. **Results display:**
   - Status: "Done [BACKEND]. Inference: XXXms, Total: XXXms"

## Backend Fallback Behavior

### CPU
- Always works (XNNPack)
- No fallback needed

### GPU
- If GPU delegate fails → Falls back to CPU automatically
- Check logs for: `"GPUv2 delegate failed to initialize"`

### NPU
- If NPU unavailable → Falls back to CPU automatically
- Check logs for: `"QNN with NPU backend is not supported on this device"`

## Testing Each Backend

### Test CPU:
```bash
# Rebuild and install
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# Run app, select CPU, run inference
adb logcat | grep -E "LAMA|LamaInpainting"
```
Expected log: `Using CPU-only backend (XNNPack)`

### Test GPU:
```bash
# Select GPU in app, run inference
adb logcat | grep -E "LAMA|LamaInpainting|GPU"
```
Expected log: `Using GPU backend (GPUv2)`
Expected log: `Active delegates: [GPUv2]`

### Test NPU:
```bash
# Select NPU in app, run inference
adb logcat | grep -E "LAMA|LamaInpainting|QNN"
```
Expected log: `Using NPU backend (QNN)`
Expected log: `Active delegates: [QNN_NPU]`

## Performance Comparison

Run the same image with all three backends and compare:

| Backend | Inference Time | Total Time | Power Usage |
|---------|---------------|------------|-------------|
| CPU     | 15-30s        | 16-31s     | High        |
| GPU     | 5-10s         | 6-11s      | Medium      |
| NPU     | 1-3s          | 2-4s       | Low         |

*(Times are approximate for 512x512 image)*

## Troubleshooting

### GPU Not Working
**Symptoms:** Falls back to CPU, no GPU in active delegates
**Causes:**
- Device doesn't support OpenGL ES 3.1+
- GPU drivers outdated
- Model incompatible with GPU

**Check:**
```bash
adb logcat | grep "GPUv2 delegate failed"
```

### NPU Not Working
**Symptoms:** Falls back to CPU, QNN error in logs
**Causes:**
- Device is not Qualcomm Snapdragon
- Device lacks HTP/DSP support
- Snapdragon version too old for FP16

**Check:**
```bash
adb logcat | grep "QNN with NPU backend is not supported"
```

### All Backends Fail
**Causes:**
- Model file missing or corrupted
- Model format incompatible
- Out of memory

**Fix:**
1. Check `lama.tflite` exists in assets
2. Verify model is valid TFLite format
3. Check device has enough RAM (>2GB free)

## Code Summary

### Files Modified
1. ✅ `activity_main.xml` - UI with RadioGroup
2. ✅ `MainActivity.kt` - Backend selection logic

### Files Unchanged
- `LamaInpainting.java` - Works with all backends
- `TFLiteHelpers.java` - Handles all delegates
- `AIHubDefaults.java` - Still defines default priorities

### Key Benefits
- ✅ **Explicit control** over which hardware to use
- ✅ **Better debugging** - test each backend individually
- ✅ **Performance comparison** - measure each backend
- ✅ **Graceful fallback** - automatic CPU fallback on failure
- ✅ **Clean UI** - simple radio button selection

## Next Steps

1. **Build and install:**
   ```bash
   cd /home/amay/Desktop/ai-hub-apps/apps/android/qidk-test-lama
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Test all backends:**
   - Start with CPU (guaranteed to work)
   - Try GPU (should work on most devices)
   - Try NPU (only on Qualcomm devices)

3. **Compare performance:**
   - Note inference times for each backend
   - Check which gives best results
   - Monitor battery usage

4. **Share results:**
   - Post timing comparisons
   - Report any issues per backend
   - Note your device model for NPU/GPU availability

## Device Compatibility

### ✅ CPU Backend
- All Android devices (API 24+)

### ✅ GPU Backend
- Most devices with Mali, Adreno, or PowerVR GPUs
- Requires OpenGL ES 3.1+

### ⚠️ NPU Backend
- Qualcomm Snapdragon devices:
  - ✅ Snapdragon 8 Gen 1 and newer (FP16)
  - ✅ Snapdragon 865+ (with limitations)
  - ❌ Older Snapdragon models (no HTP)
- ❌ MediaTek, Samsung Exynos, or other chipsets

Enjoy testing the different backends! 🚀
