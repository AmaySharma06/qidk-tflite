# Image Import & Fit Mode Fixes

## Issue 1: Old Result Persists After Importing New Image

### Problem
When importing a new image after running inpainting on a previous image, the old result was still displayed instead of showing the new image in the editor.

### Root Cause
In `loadProject()`, we were updating `sourceBitmap` and `maskBitmap` but **not clearing** the old `resultBitmap`, `beforeBitmap`, and `lastRunResult`. 

The UI was checking if `resultBitmap` exists and showing the result view instead of the editor view.

### Solution

Clear all result-related state when loading a new project:

```kotlin
// Update state - clear old results when loading new image
_state.update { it.copy(
    project = project.copy(transform = initialTransform),
    sourceBitmap = sourceBitmap,
    maskBitmap = maskBitmap,
    beforeBitmap = null,  // Clear old result
    resultBitmap = null,  // Clear old result
    transform = initialTransform,
    canRun = false,
    lastRunResult = null  // Clear old run info
)}
```

### Effect
- ✅ New image loads correctly in editor
- ✅ Old result is cleared
- ✅ Shows canvas view, not result view
- ✅ Fresh mask ready for new editing

---

## Issue 2: Contain vs Cover Not Showing Difference

### What These Modes Do

**CONTAIN Mode (Default)**
- Fits the **entire source image** into the 512×512 workspace
- May leave empty space (letterbox) if aspect ratios don't match
- Example: 1920×1080 landscape image → scaled to 512×288, centered with 112px empty top/bottom

**COVER Mode**
- **Fills the entire** 512×512 workspace with the image
- May crop edges if aspect ratios don't match
- Example: 1920×1080 landscape image → scaled to 910×512, crops 199px left/right

### Visual Comparison

```
Source Image: 1920×1080 (landscape)

┌────────────────────────────────┐
│                                │
│      [Full Image Content]      │
│                                │
└────────────────────────────────┘

CONTAIN (512×512 workspace)
┌────────────────┐
│   [empty]      │  ← Letterbox
├────────────────┤
│  [Full Image]  │  ← Entire image visible
├────────────────┤
│   [empty]      │  ← Letterbox
└────────────────┘

COVER (512×512 workspace)
     ┌────────────────┐
[crop]│  [Full Height] │[crop]  ← Sides cropped
     │   Image Fill   │
     │   Workspace    │
     └────────────────┘
```

### Why You Might Not See the Difference

The difference between Contain and Cover is **only visible in the inpainting result**, not in the DrawingView editor!

**Why?**
1. DrawingView scales the source image independently to fit the screen
2. The transform is applied when generating `input512` for the model
3. You'll only see the difference when comparing the **before/after** views after running inpainting

### How to Test Contain vs Cover

1. **Import a landscape image** (e.g., 1920×1080)
2. **Select CONTAIN mode** → Import image
3. Draw a mask → Run inpainting
4. Look at "BEFORE" view → See letterbox (empty space top/bottom)
5. Go back home → **Select COVER mode** → Import same image
6. Draw a mask → Run inpainting  
7. Look at "BEFORE" view → No letterbox, but sides are cropped

### Implementation Details

The fit mode is correctly implemented in `MatrixUtils.calculateInitialFit()`:

```kotlin
val scale = when (fitMode) {
    FitMode.CONTAIN -> minOf(scaleX, scaleY)  // Smaller scale = entire image fits
    FitMode.COVER -> maxOf(scaleX, scaleY)    // Larger scale = workspace filled
}
```

The transform is then applied in `GenerateInput512.execute()`:

```kotlin
val canvas = Canvas(workspaceBitmap)
val matrix = MatrixUtils.createMatrix(transform)
canvas.drawBitmap(sourceBitmap, matrix, null)
```

### When to Use Each Mode

**Use CONTAIN when:**
- You want to process the **entire image**
- You don't mind empty space in the result
- The image aspect ratio differs from square (most photos)

**Use COVER when:**
- You want a **full 512×512 result** with no empty space
- You're okay with edges being cropped
- You want to focus on the center of the image

---

## Code Changes

### EditorViewModel.kt

```kotlin
// In loadProject()
_state.update { it.copy(
    project = project.copy(transform = initialTransform),
    sourceBitmap = sourceBitmap,
    maskBitmap = maskBitmap,
    beforeBitmap = null,      // NEW: Clear old result
    resultBitmap = null,      // NEW: Clear old result
    transform = initialTransform,
    canRun = false,
    lastRunResult = null      // NEW: Clear old run info
)}
```

## Build

```bash
.\gradlew.bat installDebug
# BUILD SUCCESSFUL in 47s
```

## Testing

### Test 1: New Image Import
1. ✅ Import image A → Draw mask → Run inpainting
2. ✅ Go back to home screen
3. ✅ Import image B
4. ✅ **Expected**: Image B shows in editor, not result from image A
5. ✅ **Result**: Old result cleared, new image loads correctly

### Test 2: Contain vs Cover
1. Import landscape photo (e.g., 1920×1080) with **CONTAIN**
2. Draw mask in center → Run inpainting → View result
3. Notice letterbox bars (empty space) in before/after views
4. Go back → Import same photo with **COVER**
5. Draw mask in center → Run inpainting → View result
6. Notice NO letterbox bars, but different framing (cropped sides)

## Summary

- **Image import fix**: Results are now properly cleared when loading new images
- **Contain/Cover**: Already working correctly, but difference only visible in result view after inpainting
- **User education**: Added documentation explaining when each mode should be used
