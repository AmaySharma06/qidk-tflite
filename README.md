# LAMA Inpainting Android App with TFLite

This Android application performs image inpainting using the LAMA (LArge Mask inpainting) model with TensorFlow Lite. It supports multiple hardware backends for inference: CPU, GPU, and NPU (Qualcomm Neural Processing Unit).

## Features

- **Multi-Backend Support**: Run inference on:
  - **CPU-only mode**: Uses XNNPack for optimized CPU inference
  - **Default mode**: Automatically selects best available backend (NPU → GPU → CPU)
- **Interactive Mask Drawing**: Draw masks directly on images to specify areas to inpaint
- **Real-time Performance Metrics**: View inference time and total processing time

## Requirements

### Hardware
- Android device with:
  - Minimum SDK: 24 (Android 7.0)
  - Target SDK: 36
  - For NPU acceleration: Qualcomm Snapdragon chipset with HTP/DSP support

### Software
- Android Studio (2023.1.1 or newer)
- Gradle 8.13
- TensorFlow Lite model: `lama.tflite`

## Model Setup

### Step 1: Obtain the TFLite Model

You need to convert your LAMA model to TensorFlow Lite format. The model should:
- Accept two inputs:
  1. **Image**: Shape `[1, 3, H, W]` (CHW format, normalized to [0, 1] or [-1, 1])
  2. **Mask**: Shape `[1, 1, H, W]` (single channel, 1.0 = inpaint, 0.0 = keep)
- Output one tensor: Shape `[1, 3, H, W]` (CHW format)

### Step 2: Place the Model

Copy your TFLite model to the assets folder:
```bash
cp your-lama-model.tflite qidk-test-lama/app/src/main/assets/lama.tflite
```

If your model has a different filename, update `modelFilename` in `MainActivity.kt`:
```kotlin
private val modelFilename = "your-model-name.tflite"
```

### Step 3: Adjust Normalization (if needed)

If your model expects inputs normalized to [-1, 1] instead of [0, 1], change this flag in `MainActivity.kt`:
```kotlin
private val normalizeToMinus1To1 = true  // Change from false to true
```

## Building the App

### Option 1: Android Studio
1. Open the **parent `android` directory** in Android Studio (not just the `qidk-test-lama` folder)
2. Select the `qidk-test-lama` module
3. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**
4. Or click **Run** to build and install directly to a connected device

### Option 2: Command Line
```bash
cd /path/to/ai-hub-apps/apps/android/qidk-test-lama
./gradlew assembleDebug
```

The APK will be in: `app/build/outputs/apk/debug/app-debug.apk`

## Installation

Connect your Android device via USB and install:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Usage

1. **Launch the app**
2. **Select Backend**:
   - Click "Backend: Default (NPU/GPU/CPU)" to toggle between default and CPU-only modes
3. **Select Image**:
   - Tap "Select Image" to choose an image from your gallery
4. **Draw Mask**:
   - Draw with your finger on areas you want to remove/inpaint
   - Black strokes = areas to inpaint
   - White background = areas to keep
5. **Run Inpainting**:
   - Tap "Run Model" to perform inference
   - View timing metrics in the status bar
6. **Clear & Retry**:
   - "Clear Mask": Reset the mask drawing
   - "Clear Result": Remove the output image

## Architecture

### Key Components

1. **TFLiteHelpers.java**: Utility class for loading TFLite models and creating hardware delegates
2. **AIHubDefaults.java**: Configuration for Qualcomm AI Hub default delegate priorities
3. **LamaInpainting.java**: Main inference class handling:
   - Model loading with delegate selection
   - Preprocessing (resize, CHW conversion, normalization)
   - Inference execution
   - Postprocessing (tensor to bitmap conversion)
4. **MainActivity.kt**: UI controller and app lifecycle management
5. **DrawingView.kt**: Custom view for interactive mask drawing

### Delegate Priority Order

The app tries delegates in this order (based on AI Hub defaults):
1. **QNN_NPU + GPUv2 + XNNPack**: Best performance on Qualcomm devices
2. **GPUv2 + XNNPack**: GPU acceleration (fallback if NPU unavailable)
3. **XNNPack only**: CPU-only mode (final fallback or explicit selection)

## Performance Tips

- **NPU Mode**: Best for battery life and performance on supported Qualcomm devices
- **GPU Mode**: Good balance for non-NPU devices or FP16 models
- **CPU Mode**: Most compatible, works on all devices
- **Model Caching**: Compiled models are cached automatically for faster subsequent loads

## Troubleshooting

### Model Not Found Error
- Ensure `lama.tflite` exists in `app/src/main/assets/`
- Check the filename matches `modelFilename` in MainActivity

### Wrong Colors in Output
- Toggle `normalizeToMinus1To1` flag (your model may expect different normalization)

### NPU Not Working
- Check if your device supports QNN/HTP (Snapdragon 8 Gen 1+ for FP16)
- App will automatically fallback to GPU or CPU
- Check logs: `adb logcat | grep LamaInpainting`

### Build Errors
- Ensure you open the **parent `android` directory**, not just `qidk-test-lama`
- Sync Gradle files
- Check that all dependencies are downloaded

## Dependencies

- TensorFlow Lite: 2.16.1
- TensorFlow Lite GPU: 2.16.1
- Qualcomm QNN Runtime: 2.29.0
- Qualcomm QNN LiteRT Delegate: 2.29.0

## Migration from ONNX Runtime

This app was migrated from ONNX Runtime to TensorFlow Lite:
- **Before**: `onnxruntime-android-qnn` with manual backend selection
- **After**: TFLite with automatic delegate selection (NPU/GPU/CPU)
- **Benefit**: Better hardware utilization and AI Hub compatibility

## References

- [Qualcomm AI Hub](https://aihub.qualcomm.com/)
- [TensorFlow Lite Documentation](https://www.tensorflow.org/lite)
- [QNN Delegate Documentation](https://docs.qualcomm.com/bundle/publicresource/topics/80-63442-50/introduction.html)
- [ImageClassification Sample](../ImageClassification) - Reference implementation

## License

Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
SPDX-License-Identifier: BSD-3-Clause
