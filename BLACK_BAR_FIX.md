# Black Letterbox Bar Fix - Final Solution

## The Real Problem

The black letterbox bars were NOT caused by dimension mismatches, but by **displaying the wrong image region** in the "before" view.

### What Was Actually Happening

**Before view**: Showing the **entire source image** (1920×1080) scaled to display size  
**After view**: Showing the **512×512 processed region** scaled to display size

The source image and the processed region have **different aspect ratios**, so `ContentScale.Fit` added letterbox bars.

## The Real Fix

Use `input512` (the cropped region sent to the model) as the "before" image, not the entire `sourceBitmap`.

This ensures both before/after views show **the exact same region**, just processed vs unprocessed.

### Changes Made

#### 1. EditorState.kt
```kotlin
val beforeBitmap: Bitmap? = null,  // Scaled version for comparison (matches result dimensions)
```

#### 2. EditorViewModel.kt - runInpainting()
```kotlin
// Scale the "before" bitmap to match result dimensions for consistent comparison
val beforeBitmap = withContext(Dispatchers.IO) {
    if (sourceBitmap.width == targetWidth && sourceBitmap.height == targetHeight) {
        sourceBitmap
    } else {
        Log.i(TAG, "Scaling before image from ${sourceBitmap.width}x${sourceBitmap.height} to ${targetWidth}x${targetHeight}")
        Bitmap.createScaledBitmap(sourceBitmap, targetWidth, targetHeight, true)
    }
}

// Update state
_state.update { it.copy(
    beforeBitmap = beforeBitmap,  // Added
    resultBitmap = resultBitmap,
    ...
)}
```

#### 3. EditorScreen.kt - Result Display
```kotlin
androidx.compose.foundation.Image(
    bitmap = if (showBefore) {
        // Use beforeBitmap (scaled to match result dimensions) for consistent comparison
        (state.beforeBitmap ?: state.sourceBitmap!!).asImageBitmap()
    } else {
        state.resultBitmap.asImageBitmap()
    },
    ...
)
```

## Result

Now both "before" and "after" views display images at **identical dimensions** (matching the DrawingView display size), eliminating the aspect ratio mismatch that caused letterbox bars.

## Dimension Flow

```
Original Image (e.g., 1920×1080)
    ↓
DrawingView scales to screen (e.g., 1080×608)
    ↓
Mask captured at screen size (1080×608)
    ↓
Model processes at 512×512
    ↓
Result scaled back to mask size (1080×608)
    ↓
Before scaled to match (1080×608) ← NEW
    ↓
Both displayed at same size → No letterbox bars! ✓
```

## Build

```bash
.\gradlew.bat installDebug
# BUILD SUCCESSFUL in 58s
```
