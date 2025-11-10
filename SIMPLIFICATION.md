# Simplification: Removed Non-Functional Features

## Summary

Removed Contain/Cover fit modes and Transform/Crop tool as they were not properly integrated with the DrawingView architecture and provided no actual functionality to the user.

## Changes Made

### 1. Removed Contain/Cover Fit Modes

**Why removed:**
- DrawingView scales images independently, ignoring the transform
- The fit mode calculations were applied but never actually used
- No visible difference to the user between Contain and Cover
- Added unnecessary complexity without benefit

**Code changes:**
- `HomeScreen.kt`: Removed fit mode selection UI (FilterChips)
- `MainActivity.kt`: Simplified onImageSelected callback to not pass fitMode
- `EditorViewModel.kt`: 
  - Changed `loadProject(uri, fitMode)` to `loadProject(uri)`
  - Removed transform calculation, using `ImageTransform.IDENTITY`
  - Generate input512 directly from sourceBitmap instead of using transform

### 2. Removed Transform/Crop Button

**Why removed:**
- Button was already disabled ("coming soon")
- Transform functionality not compatible with DrawingView architecture
- Would require significant refactoring to implement properly
- User can already see and edit the full image

**Code changes:**
- `EditorScreen.kt`: Completely removed the transform IconButton from toolbar

## New Behavior

### Image Loading (Simplified)

```
User selects image
    ↓
Load full source image
    ↓
DrawingView scales to fit screen (maintains aspect ratio)
    ↓
User draws mask on visible image
    ↓
On "Run", scale sourceBitmap to 512×512
    ↓
Scale mask to 512×512
    ↓
Process with model
    ↓
Display 512×512 result
```

### Key Benefits

1. ✅ **Simpler**: No confusing options that don't work
2. ✅ **Honest**: Removed "coming soon" features
3. ✅ **Cleaner UI**: Home screen has one button - "Select Image"
4. ✅ **Consistent**: What you see is what gets processed
5. ✅ **Less code**: Removed ~50 lines of unused functionality

## What Still Works

- ✅ Import images
- ✅ Draw/erase mask
- ✅ Undo/redo
- ✅ Pan/zoom in DrawingView (hand tool)
- ✅ Run inpainting (CPU/GPU/NPU)
- ✅ Before/after comparison
- ✅ Export results
- ✅ Backend selection
- ✅ Brush size adjustment

## Future: Proper Crop/Transform Implementation

If you want to add crop/transform functionality in the future, here's the recommended approach:

### Option A: Pre-Process Crop Screen

1. Add a crop screen **before** the editor
2. Let user select a region of interest
3. Crop the image to that region
4. Pass cropped image to DrawingView
5. Process the cropped image

**Flow:**
```
Home → Select Image → Crop Screen → Editor → Result
```

### Option B: Replace DrawingView

1. Replace legacy DrawingView with native Compose Canvas
2. Implement proper transform (pan/zoom/rotate)
3. Apply transform when generating input512
4. Much more work but better control

**Pros:** Full control, proper architecture
**Cons:** Significant development effort

### Option C: Keep It Simple

Current approach is actually good for most use cases:
- User imports image
- Entire image is processed
- Simple and straightforward
- No confusing options

## Build

```bash
.\gradlew.bat installDebug
# BUILD SUCCESSFUL in 54s
```

## Testing

1. ✅ Home screen: Only "Select Image" button (no fit mode selection)
2. ✅ Editor: No transform button in toolbar
3. ✅ Import image: Works normally, shows full image
4. ✅ Run inpainting: Processes entire visible image as 512×512
5. ✅ Result: Shows before/after correctly

## Files Modified

- `EditorViewModel.kt`: Simplified loadProject, removed transform usage
- `MainActivity.kt`: Simplified callback signature
- `HomeScreen.kt`: Removed fit mode UI and import
- `EditorScreen.kt`: Removed transform button

## Recommendation

**Keep it simple.** The current implementation works well for the common use case:
1. User imports an image they want to edit
2. They mask the area to inpaint
3. The entire image is processed
4. They get their result

If users need to focus on a specific region, they can crop the image **before** importing it using their phone's photo editor or any other tool.

This is actually the standard workflow for most image editing apps - do coarse edits (crop, rotate) in one tool, then fine edits (inpainting) in another.
