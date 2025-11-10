# Critical Bug Fixes - Run Button, Transform, and Undo/Redo

## Issues Fixed

### 1. ✅ Run Button Not Responding (CRITICAL)

**Problem**: The inpainting run button stopped working even though drawing was successful.

**Root Cause**: 
- DrawingView creates a mask at **screen resolution** (e.g., 1080x2160)
- ViewModel/MaskRasterizer expected a **512x512 mask**
- Size mismatch caused `MaskRasterizer.hasMaskPixels()` to fail
- The function was checking for pixels in a 512x512 array but the bitmap was much larger
- This resulted in `canRun` staying false even with drawn mask

**Solution**:
1. **Added size-agnostic mask checking** - New `hasMaskPixels()` function in ViewModel that works with any bitmap size
2. **Added mask scaling** - Scale DrawingView mask to 512x512 before sending to model
3. **Added logging** - Debug logs show mask size and detection status

**Code Changes**:
```kotlin
// EditorViewModel.kt

private fun hasMaskPixels(mask: Bitmap): Boolean {
    val width = mask.width
    val height = mask.height
    val pixels = IntArray(width * height)
    mask.getPixels(pixels, 0, width, 0, 0, width, height)
    
    for (pixel in pixels) {
        val gray = (0.299f * r + 0.587f * g + 0.114f * b)
        if (gray < 128f) return true  // Found black pixel
    }
    return false
}

private fun runInpainting() {
    // ... 
    
    // Scale mask to 512×512 if needed
    val mask512 = if (maskBitmap.width == 512 && maskBitmap.height == 512) {
        maskBitmap
    } else {
        Bitmap.createScaledBitmap(maskBitmap, 512, 512, true)
    }
    
    runInpainting.execute(input512, mask512, backend, runId)
}
```

### 2. ✅ Transform/Crop Button Disabled

**Problem**: The transform/crop button didn't do anything when clicked.

**Root Cause**: 
- Button called `OnResetTransform` event
- `resetTransform()` function existed but did nothing useful with DrawingView
- DrawingView doesn't have transform/crop functionality implemented
- Image is simply scaled to screen width on load

**Solution**:
- **Disabled the button** with `enabled = false`
- Added tooltip: "Transform (coming soon)"
- Prevents user confusion - button looks disabled as expected

**Code Changes**:
```kotlin
// EditorScreen.kt

IconButton(
    onClick = { onEvent(EditorEvent.OnResetTransform) },
    enabled = false  // Transform not implemented with DrawingView
) {
    Icon(Icons.Default.Transform, "Transform (coming soon)")
}
```

### 3. ✅ Undo/Redo Buttons Status

**Problem**: Undo and redo buttons were crashing the app previously.

**Current Status**: 
- Buttons remain **disabled** (this is correct)
- Functions return early with log warning instead of crashing
- Tooltips show "(coming soon)" to inform users

**Why Still Disabled**:
- Undo/redo requires replaying stroke history
- DrawingView handles touches independently 
- No stroke capture system integrated
- Would need complete rewrite to support properly

**Proper Fix (Future)**:
- Store Bitmap snapshots for undo/redo (memory intensive)
- OR rewrite with Compose Canvas for better integration
- OR implement DrawingView stroke history capture

## Technical Details

### Mask Size Flow

**Before Fix**:
```
DrawingView creates mask: 1080x2160
  ↓
ViewModel checks: MaskRasterizer.hasMaskPixels(mask)
  ↓
MaskRasterizer expects: 512x512
  ↓
Size mismatch → Check fails → canRun = false ❌
```

**After Fix**:
```
DrawingView creates mask: 1080x2160
  ↓
ViewModel checks: hasMaskPixels(mask)  [works with any size]
  ↓
Found black pixels → canRun = true ✅
  ↓
On run: Scale to 512x512
  ↓
Pass to model: mask512
```

### Logging Added

Debug logs now show:
- Mask dimensions when updated
- Whether mask pixels were detected
- Mask scaling operation (from/to dimensions)
- Run button disabled warnings

Example log output:
```
D/EditorViewModel: Mask updated: 1080x2160, hasMask=true
I/EditorViewModel: Scaling mask from 1080x2160 to 512x512
I/RunInpainting: Inference completed: 67ms inference, 81ms total
```

## User Experience

### Before Fixes
- ❌ Draw mask → Run button stays disabled
- ❌ Click transform button → Nothing happens
- ❌ Click undo → App crashes
- 😕 Confusing and broken

### After Fixes
- ✅ Draw mask → Run button enables immediately
- ✅ Transform button grayed out (clear it's not available)
- ✅ Undo/redo buttons grayed out (no crashes)
- ✅ Logging helps debug issues
- 😊 Clear and predictable behavior

## Testing Results

### Verified Working
1. **Import image** ✅
2. **Draw mask with brush** ✅
3. **Run button enables** ✅ (FIXED!)
4. **Run inpainting** ✅
5. **Result displays full screen** ✅
6. **Before/after toggle** ✅
7. **Edit again** ✅
8. **Brush size adjustment** ✅
9. **Tool switching** ✅
10. **Backend selection** ✅

### Correctly Disabled
1. **Undo button** ⏸️ (grayed out, no crash)
2. **Redo button** ⏸️ (grayed out, no crash)
3. **Transform button** ⏸️ (grayed out, clear status)

## Architecture Notes

### DrawingView Limitations
The legacy DrawingView has several constraints:
- Creates screen-sized bitmaps (not 512x512)
- No built-in transform system
- No stroke history capture
- Limited integration with Compose state

### Workarounds Applied
1. **Mask size mismatch**: Scale on-demand before model
2. **Transform disabled**: Hide functionality cleanly
3. **Undo/redo**: Disable rather than crash

### Future Migration Path
To unlock full functionality, consider:
1. **Rewrite with Compose Canvas**
   - Direct state integration
   - Native gesture handling
   - Proper coordinate transforms
   - Easy undo/redo with state snapshots

2. **Or enhance DrawingView**
   - Add stroke history capture
   - Implement transform matrix
   - Better Compose bridge

## Files Modified

1. **EditorViewModel.kt**
   - Added `hasMaskPixels()` function for any bitmap size
   - Updated `onMaskUpdated()` with logging
   - Updated `runInpainting()` to scale mask to 512x512
   - Added debug logging throughout

2. **EditorScreen.kt**
   - Disabled transform button with tooltip

3. **DrawingView.kt**
   - No changes (limitations accepted)

## Build Status
✅ **BUILD SUCCESSFUL** 
✅ **INSTALLED ON DEVICE**
✅ **RUN BUTTON WORKING**

The run button now works reliably after drawing!
