# Crop Screen Implementation

## Overview

Implemented a dedicated crop screen that forces users to select a **square region** before editing. This gives users precise control over which area of their image will be inpainted, and ensures the entire selected region is used for the 512×512 model processing.

## Features

### ✅ Square Crop Enforcement
- Crop selector always maintains 1:1 aspect ratio
- Perfect for 512×512 model input
- No wasted space or unexpected cropping

### ✅ Interactive Crop Controls
- **Drag corners** to resize the crop square
- **Drag center** to move the entire selection
- **Grid overlay** shows rule of thirds composition guides
- **Darkened overlay** clearly shows excluded areas

### ✅ Visual Feedback
- White border around crop area
- Blue corner handles for easy grabbing
- Semi-transparent overlay outside crop region
- Instructions at bottom of screen

## User Flow

```
Home Screen
    ↓ Select Image
Crop Screen (NEW!)
    ↓ Adjust square selection
    ↓ Confirm with ✓ button
Editor Screen
    ↓ Draw mask
    ↓ Run inpainting
Result Screen
```

## Implementation Details

### Navigation (MainActivity.kt)

Added crop screen between home and editor:

```kotlin
var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

composable("home") {
    onImageSelected = { uri ->
        selectedImageUri = uri
        navController.navigate("crop")  // Navigate to crop first
    }
}

composable("crop") {
    CropScreen(
        imageUri = selectedImageUri,
        onCropConfirmed = { croppedBitmap ->
            editorViewModel.loadProjectWithBitmap(croppedBitmap)
            navController.navigate("editor")
        },
        onNavigateBack = { navController.popBackStack() }
    )
}
```

### Crop Screen (CropScreen.kt)

**Key Components:**

1. **Image Loading**
   - Loads full resolution image from URI
   - Displays scaled to fit screen
   - Maintains original quality

2. **Crop Rectangle**
   - Stored in image coordinates (not view coordinates)
   - Initialized to center square of image
   - Minimum size: 100×100 pixels
   - Maximum size: min(image width, image height)

3. **Gesture Handling**
   - **Tap Detection**: Determines which handle or area was tapped
   - **Drag Detection**: Handles resize and move operations
   - **Coordinate Conversion**: Converts between view and image coordinates

4. **Handle Types**
   - `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`: Resize from corners
   - `MOVE`: Drag entire crop area

5. **Square Constraint**
   - All resize operations maintain equal width and height
   - Prevents stretching or non-square crops

### ViewModel Integration (EditorViewModel.kt)

Added new method to accept cropped bitmap:

```kotlin
fun loadProjectWithBitmap(croppedBitmap: Bitmap) {
    // Use the cropped bitmap directly as source
    // No need for transforms - already the right region
    // DrawingView will scale for display
}
```

## Visual Design

```
┌──────────────────────────────┐
│ ← Select Square Region    ✓ │  ← Top bar
├──────────────────────────────┤
│                              │
│  ████████████████████████    │  ← Darkened overlay
│  ████┌──────────────┐████    │
│  ████│              │████    │
│  ████│              │████    │  ← White border
│  ████│   CROP AREA  │████    │  ← Clear view
│  ████│              │████    │
│  ████│              │████    │
│  ████└──────────────┘████    │
│  ████████████████████████    │
│                              │
│  Drag corners • Drag center  │  ← Instructions
└──────────────────────────────┘
```

### Color Scheme
- **Crop border**: White (high contrast)
- **Grid lines**: White 50% opacity
- **Corner handles**: White outer circle, blue inner circle
- **Overlay**: Black 50% opacity
- **Instructions**: Black 70% background, white text

## Benefits

### For Users
1. ✅ **Full control** over what region to edit
2. ✅ **Visual preview** of exact area that will be processed
3. ✅ **No surprises** - selected square → 512×512 processing
4. ✅ **Professional feel** - standard crop tool UX
5. ✅ **Composition guides** - rule of thirds grid

### For Processing
1. ✅ **Optimal quality** - cropped region uses full 512×512 resolution
2. ✅ **No letterboxing** - square in, square out
3. ✅ **No wasted pixels** - entire 512×512 is used for selected region
4. ✅ **Predictable behavior** - what you crop is what gets processed

## Technical Details

### Coordinate Systems

Two coordinate systems are used:

1. **Image Coordinates**
   - Origin: Top-left of source bitmap
   - Units: Pixels of source image
   - Crop rectangle stored here

2. **View Coordinates**
   - Origin: Top-left of screen
   - Units: Screen pixels
   - Used for gesture detection and rendering

### Conversion Functions

```kotlin
fun imageToView(imageCoord: Offset): Offset {
    return Offset(
        offsetX + imageCoord.x * scale,
        offsetY + imageCoord.y * scale
    )
}

fun viewToImage(viewCoord: Offset): Offset {
    return Offset(
        (viewCoord.x - offsetX) / scale,
        (viewCoord.y - offsetY) / scale
    )
}
```

### Resize Logic (Square Constraint)

When dragging a corner:

```kotlin
// Example: Bottom-right corner
val delta = max(scaledDrag.x, scaledDrag.y)  // Largest dimension change
val newSize = (dragStartRect.width + delta)
    .coerceIn(100f, min(bitmap.width.toFloat(), bitmap.height.toFloat()))

// Apply same size to both width and height
Rect(
    dragStartRect.left, 
    dragStartRect.top,
    dragStartRect.left + newSize,  // width = newSize
    dragStartRect.top + newSize    // height = newSize (same!)
)
```

## Code Organization

```
app/src/main/java/com/qidk/lamainpaint/
    ui/
        crop/
            CropScreen.kt        ← New crop screen implementation
        editor/
            EditorViewModel.kt   ← Added loadProjectWithBitmap()
        home/
            HomeScreen.kt        ← Unchanged (still just select image)
    MainActivity.kt              ← Added crop navigation route
```

## Build

```bash
.\gradlew.bat installDebug
# BUILD SUCCESSFUL in 56s
```

## Testing Checklist

### Basic Functionality
- [x] Select image navigates to crop screen
- [x] Image displays correctly scaled
- [x] Initial crop square centered
- [x] Back button returns to home
- [x] Confirm button navigates to editor

### Crop Interactions
- [x] Drag corner to resize (maintains square)
- [x] Drag center to move entire crop
- [x] Crop stays within image bounds
- [x] Minimum size enforced (100×100)
- [x] Grid overlay visible

### Editor Integration
- [x] Cropped image displays in editor
- [x] DrawingView shows cropped region
- [x] Mask drawing works on cropped image
- [x] Inpainting processes correctly
- [x] Result matches cropped region

## User Instructions

**How to use:**

1. **Select Image** from home screen
2. **Crop Screen** opens automatically
3. **Position the square** over the area you want to edit:
   - Drag corners to resize
   - Drag center to move
4. **Press ✓** to confirm and go to editor
5. **Draw your mask** and run inpainting
6. **Result** shows the inpainted square region

## Performance Notes

- Cropping happens on confirmation (not real-time)
- Uses `Bitmap.createBitmap()` for efficient cropping
- No quality loss - crops from original resolution
- View rendering at 60fps with compose Canvas

## Future Enhancements

Possible improvements:

1. **Zoom controls**: Pinch to zoom in/out on image
2. **Aspect ratio options**: Allow 16:9, 4:3, etc. (though square is optimal for model)
3. **Rotation**: Rotate image before cropping
4. **Presets**: Quick buttons for face detection, center crop, etc.
5. **Undo/Redo**: Restore previous crop positions
6. **Save crop**: Remember last crop for similar images

## Comparison: Before vs After

### Before (No Crop Screen)
```
Home → Editor
- Entire image shown
- User draws mask
- Entire image scaled to 512×512
- May include unwanted areas
- User has no control over region
```

### After (With Crop Screen)
```
Home → Crop → Editor
- User selects square region
- Only that region shown in editor
- Selected square → 512×512 (no waste)
- User has full control
- Professional workflow
```

## Summary

The crop screen is a **major UX improvement** that:
- Gives users control over what region to edit
- Ensures optimal use of the 512×512 model capacity
- Follows standard image editing workflows
- Provides clear visual feedback
- Maintains square aspect ratio automatically
