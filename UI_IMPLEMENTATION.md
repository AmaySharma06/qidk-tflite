# LamaInpaint UI Implementation

This document describes the implementation of the new UI/UX features for the LamaInpaint mobile image inpainting editor.

## Implementation Overview

The application has been upgraded from XML-based Views to Jetpack Compose with a modern architecture following the instructions document specifications.

## Architecture

### Layer Structure

```
app/
├── ui/                          # Presentation Layer (Compose)
│   ├── home/
│   │   └── HomeScreen.kt       # Image selection and fit mode
│   ├── editor/
│   │   ├── EditorScreen.kt     # Main editor UI
│   │   ├── EditorViewModel.kt  # State management
│   │   ├── EditorState.kt      # UI state model
│   │   └── EditorEvent.kt      # Event system
│   └── settings/
│       └── SettingsScreen.kt   # App settings
│
├── domain/                      # Business Logic Layer
│   ├── model/                  # Data models
│   │   ├── Backend.kt          # CPU/GPU/NPU enum
│   │   ├── ImageTransform.kt   # Transform data
│   │   ├── RunResult.kt        # Inference results
│   │   ├── Project.kt          # Project model
│   │   ├── ProjectSettings.kt  # Settings model
│   │   └── Constants.kt        # App constants
│   └── usecase/                # Use cases
│       ├── GenerateInput512.kt # Input generation
│       ├── RasterizeMask512.kt # Mask rasterization
│       └── RunInpainting.kt    # Inference execution
│
├── data/                        # Data Layer
│   ├── ProjectRepository.kt    # Project persistence
│   ├── FileStore.kt            # File management
│   └── AppPreferences.kt       # DataStore preferences
│
└── core/                        # Core Utilities
    └── graphics/
        ├── MatrixUtils.kt      # Transform calculations
        ├── BicubicResampler.kt # Image upscaling
        └── MaskRasterizer.kt   # Binary mask operations
```

## Key Features Implemented

### 1. **Domain Models** ✅
- `Backend` enum (CPU/GPU/NPU)
- `ImageTransform` for 512×512 workspace transformations
- `RunResult` for tracking inference timing and backend
- `Project` model with full metadata
- `ProjectSettings` with backend, brush, and fit mode preferences

### 2. **Core Graphics Utilities** ✅
- **MatrixUtils**: Transform calculations, coordinate conversions, clamping
- **BicubicResampler**: High-quality image upscaling for export
- **MaskRasterizer**: Binary mask operations with feathering support

### 3. **Data Persistence** ✅
- **AppPreferences**: DataStore-based preferences (backend, brush, theme, etc.)
- **FileStore**: Project file management (masks, results, metadata)
- **ProjectRepository**: JSON-based project persistence with CRUD operations

### 4. **Use Cases** ✅
- **GenerateInput512**: Transform source image to 512×512 workspace
- **RasterizeMask512**: Convert mask to binary format for model
- **RunInpainting**: Execute inference with backend selection and fallback

### 5. **UI Components** ✅

#### HomeScreen
- Large "Select Image" CTA
- Fit mode selection (Contain/Cover)
- Settings navigation
- Material 3 design

#### EditorScreen
- Top app bar with Undo/Redo/Export
- Backend badge and status display
- Legacy DrawingView integration (backward compatible)
- Result display with timing info
- Processing overlay with progress indicator
- Bottom toolbar with:
  - Tool selection (Brush/Eraser/Hand/Transform)
  - Brush size slider
  - Backend selection chips
  - Mask visibility toggle
  - Clear mask button
- Floating Action Button for running inpainting

#### SettingsScreen
- Backend selection (CPU/GPU/NPU)
- Brush defaults (size, feather)
- Default fit mode
- Theme selection (System/Light/Dark)
- Autosave and Haptics toggles

### 6. **State Management** ✅
- **EditorViewModel**: Centralized state management with coroutines
- **EditorState**: Immutable UI state
- **EditorEvent**: Sealed interface for user actions
- Undo/Redo stack for strokes and transforms

### 7. **Event System** ✅
```kotlin
sealed interface EditorEvent {
    OnUndo, OnRedo
    OnBrushSizeChanged(px: Float)
    OnFeatherChanged(px: Float)
    OnPointerStroke(points: List<PointF>, erasing: Boolean)
    OnTransformChanged(transform: ImageTransform)
    OnRunRequested
    OnCompareToggle
    OnBackendSelected(backend: Backend)
    OnClearMask
    OnResetTransform
    OnToggleMaskVisibility
    OnCompareModeChanged(mode: CompareMode)
    OnExport
}
```

## Technical Specifications

### Constants
- **WORKSPACE_SIZE**: 512 (fixed model input/output)
- **MIN_TOUCH_TARGET_DP**: 48 (accessibility)
- **BRUSH_LATENCY_TARGET_MS**: 12 (performance target)

### Coordinate Spaces
- **Workspace**: Fixed 512×512 pixels (model space)
- **View**: Device pixels (UI rendering)
- Transform matrix maps between spaces

### Binary Mask
- Stored as bitmap with threshold applied
- Black pixels (< 128 gray) = 1.0 (inpaint)
- White pixels (>= 128 gray) = 0.0 (keep)
- Feathering applied during stroke, then thresholded to binary

### Backend Selection
- Explicit CPU/GPU/NPU radio options
- Fallback to CPU if selected backend fails
- Backend badge visible during processing
- Timing displayed for inference and total time

## Dependencies Added

```gradle
// Jetpack Compose
androidx.compose:compose-bom:2024.02.00
androidx.compose.ui:ui
androidx.compose.material3:material3
androidx.activity:activity-compose:1.8.2
androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0
androidx.navigation:navigation-compose:2.7.7

// DataStore for preferences
androidx.datastore:datastore-preferences:1.0.0

// Coroutines
kotlinx-coroutines-android:1.7.3

// Image loading
io.coil-kt:coil-compose:2.5.0
```

## Migration Notes

### Backward Compatibility
The implementation maintains backward compatibility with the existing `DrawingView` and `LamaInpainting` classes by wrapping them in Compose via `AndroidView`. This allows:
- Immediate UI improvements without rewriting drawing logic
- Gradual migration to full Compose canvas
- Testing of new architecture with proven inference code

### Future Enhancements
The architecture supports future additions:
1. **Custom Canvas**: Replace DrawingView with Compose Canvas for native gestures
2. **Compare Modes**: Hold-to-compare, split slider, side-by-side views
3. **Advanced Gestures**: Two-finger pan/zoom, rotation, long-press toggle
4. **Recent Projects**: Grid view on home screen with thumbnails
5. **Export Options**: Bicubic upscaling to original dimensions
6. **Auto-save**: Periodic project state persistence

## Usage Flow

1. **Home Screen**: User selects image and fit mode (Contain/Cover)
2. **Editor Loads**: Image transformed into 512×512 workspace
3. **Mask Drawing**: User paints with brush/eraser (adjustable size)
4. **Backend Selection**: Choose CPU/GPU/NPU via chips
5. **Run Inpainting**: FAB or toolbar button (enabled when mask has pixels)
6. **Processing**: Progress overlay with backend badge and status
7. **Result Display**: Output shown with timing info
8. **Undo/Redo**: Non-destructive editing with history
9. **Export**: Save result (currently placeholder for future upscale)

## Performance Targets

- Cold start to editable canvas: < 2.5s (mid-range devices)
- Brush latency: < 12ms at 512×512
- Progress feedback: < 150ms from Run tap
- Inference timing displayed: Inference ms + Total ms

## Testing Recommendations

### Unit Tests
- Transform clamping logic in MatrixUtils
- Binary mask rasterization in MaskRasterizer
- Run button enablement conditions
- Undo/redo stack operations

### UI Tests
- Navigation flow (Home → Editor → Settings)
- Backend selection persists across sessions
- Undo/redo functionality
- Mask clear operation

### Integration Tests
- Full workflow: Select → Transform → Mask → Run → Result
- Backend fallback behavior (GPU → CPU)
- Project persistence and restoration

## Known Limitations

1. **DrawingView Integration**: Still using legacy View system for canvas
   - Future: Replace with Compose Canvas for better gesture handling
   
2. **Compare Modes**: UI structure ready but not fully implemented
   - Requires additional gesture handling and view synchronization
   
3. **Export**: Currently placeholder
   - Needs bicubic upscaling to original dimensions
   
4. **Recent Projects**: Home screen shows CTA only
   - Needs project list with thumbnails and metadata

## Conclusion

This implementation provides a solid foundation following the instructions document specifications:
- ✅ Binary mask operations
- ✅ Fixed 512×512 processing canvas
- ✅ Explicit backend selection (CPU/GPU/NPU)
- ✅ Modern Jetpack Compose UI
- ✅ Clean architecture with separation of concerns
- ✅ Undo/redo system
- ✅ Settings persistence
- ✅ Performance-conscious design

The architecture is extensible and ready for the remaining advanced features while maintaining backward compatibility with existing inference code.
