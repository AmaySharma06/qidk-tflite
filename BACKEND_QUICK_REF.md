# Quick Reference: Backend Selection

## UI Changes

### Before:
```
[Toggle Backend (HTP/CPU)] button
```

### After:
```
Backend Selection:
( ) CPU    ( ) GPU    (●) NPU
```

## Code Changes Summary

### MainActivity.kt
```kotlin
// OLD
private var useCpuOnly = false
toggleBackendBtn.setOnClickListener { ... }

// NEW
private enum class BackendType { CPU, GPU, NPU }
private var selectedBackend = BackendType.CPU

backendRadioGroup.setOnCheckedChangeListener { ... }
```

### Delegate Selection
```kotlin
when (selectedBackend) {
    BackendType.CPU -> { /* Empty = XNNPack */ }
    BackendType.GPU -> { enabledDelegates.add(GPUv2) }
    BackendType.NPU -> { enabledDelegates.add(QNN_NPU) }
}
```

## Quick Test Commands

```bash
# Build
./gradlew assembleDebug

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Monitor logs
adb logcat | grep -E "LAMA|Active delegates"
```

## Expected Log Outputs

### CPU Selected:
```
LAMA: Using CPU-only backend (XNNPack)
LamaInpainting: Active delegates: []
```

### GPU Selected:
```
LAMA: Using GPU backend (GPUv2)
LamaInpainting: Active delegates: [GPUv2]
```

### NPU Selected:
```
LAMA: Using NPU backend (QNN)
LamaInpainting: Active delegates: [QNN_NPU]
```

## Performance Comparison Table

Fill this in after testing on your device:

| Backend | Load Time | Inference Time | Total Time | Works? |
|---------|-----------|----------------|------------|--------|
| CPU     | ___ ms    | ___ ms         | ___ ms     | ✅/❌  |
| GPU     | ___ ms    | ___ ms         | ___ ms     | ✅/❌  |
| NPU     | ___ ms    | ___ ms         | ___ ms     | ✅/❌  |

Device Model: _______________
Android Version: _______________
Chipset: _______________

## Common Issues

### Issue: GPU shows "Active delegates: []"
**Solution:** GPU not supported on device, fallback to CPU

### Issue: NPU shows "QNN with NPU backend is not supported"
**Solution:** Device doesn't have Qualcomm NPU or model incompatible

### Issue: All backends fail
**Solution:** Check model file exists at `app/src/main/assets/lama.tflite`

## Status Messages

- "Switched to [BACKEND] backend. Re-run to apply." → Backend selected
- "Loading TFLite model for [BACKEND]..." → Model loading
- "Model loaded with [BACKEND] backend" → Ready to infer
- "Running inference on [BACKEND]..." → Inference in progress
- "Done [BACKEND]. Inference: XXms, Total: XXms" → Complete!
- "Error [BACKEND]: ..." → Something went wrong

---

**Everything is ready! Build, install, and test all three backends.** 🚀
