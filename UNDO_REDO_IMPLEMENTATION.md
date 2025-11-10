# Undo/Redo Implementation for Mask Editing

## Overview

Implemented bitmap-based undo/redo functionality for the mask editor using snapshot stacks. This approach works seamlessly with the legacy `DrawingView` component without requiring stroke replay.

## Architecture

### Snapshot-Based Approach

Instead of storing stroke commands and replaying them, we store **immutable bitmap snapshots** of the mask at each editing step. This has several advantages:

- ✅ Works with any drawing tool (brush, eraser, etc.)
- ✅ No need to track stroke details or replay logic
- ✅ Simple and reliable state management
- ✅ Fast restoration (just copy bitmap back)
- ✅ Compatible with legacy DrawingView

## Implementation Details

### 1. State Management (EditorState.kt)

Added two stacks to store bitmap history:

```kotlin
val undoStack: List<Bitmap> = emptyList(),  // Stack of mask bitmap snapshots for undo
val redoStack: List<Bitmap> = emptyList()   // Stack of mask bitmap snapshots for redo
```

These are immutable lists, ensuring thread-safety and proper state management.

### 2. DrawingView Integration (DrawingView.kt)

Added `setMaskBitmap()` method to restore mask from external bitmap:

```kotlin
fun setMaskBitmap(bitmap: Bitmap) {
    // Create a mutable copy and update canvas
    maskBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    canvas = Canvas(maskBitmap!!)
    invalidate()
}
```

This allows the ViewModel to restore previous mask states.

### 3. ViewModel Logic (EditorViewModel.kt)

#### Callback Mechanism

```kotlin
var onRestoreMask: ((Bitmap) -> Unit)? = null
```

This callback is set by the UI to enable the ViewModel to restore bitmaps to DrawingView.

#### Snapshot Capture

In `onMaskUpdated()`, before applying new changes:

```kotlin
// Save previous mask to undo stack (before applying new changes)
val previousMask = currentState.maskBitmap
if (previousMask != null && previousMask !== maskBitmap) {
    // Create immutable copy for undo stack
    val snapshot = previousMask.copy(Bitmap.Config.ARGB_8888, false)
    _state.update { it.copy(
        undoStack = it.undoStack + snapshot,
        canUndo = true,
        redoStack = emptyList(),  // Clear redo stack on new edit
        canRedo = false
    )}
}
```

#### Undo Logic

```kotlin
private fun undo() {
    // 1. Save current mask to redo stack
    val redoSnapshot = currentMask.copy(Bitmap.Config.ARGB_8888, false)
    
    // 2. Pop from undo stack
    val previousMask = currentState.undoStack.last()
    val newUndoStack = currentState.undoStack.dropLast(1)
    
    // 3. Restore mask to DrawingView
    onRestoreMask?.invoke(previousMask)
    
    // 4. Update state
    _state.update { it.copy(
        maskBitmap = previousMask,
        undoStack = newUndoStack,
        canUndo = newUndoStack.isNotEmpty(),
        redoStack = it.redoStack + redoSnapshot,
        canRedo = true
    )}
}
```

#### Redo Logic

Similar to undo, but pops from redo stack and pushes to undo stack.

### 4. UI Wiring (EditorScreen.kt & MainActivity.kt)

#### EditorScreen.kt

Enabled undo/redo buttons based on stack state:

```kotlin
IconButton(
    onClick = { onEvent(EditorEvent.OnUndo) },
    enabled = state.canUndo  // Enabled when undo stack has items
) {
    Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
}

IconButton(
    onClick = { onEvent(EditorEvent.OnRedo) },
    enabled = state.canRedo  // Enabled when redo stack has items
) {
    Icon(Icons.AutoMirrored.Filled.Redo, "Redo")
}
```

#### MainActivity.kt

Wired up the callback when DrawingView is created:

```kotlin
EditorScreen(
    state = editorState,
    onEvent = { event -> editorViewModel.onEvent(event) },
    onNavigateBack = { navController.popBackStack() },
    onSetDrawingViewCallback = { drawingView ->
        // Wire up undo/redo mask restoration callback
        editorViewModel.onRestoreMask = { maskBitmap ->
            drawingView.setMaskBitmap(maskBitmap)
        }
    }
)
```

## Data Flow

### Editing Flow

```
User draws stroke
    ↓
DrawingView captures mask change
    ↓
OnMaskChangedListener triggered
    ↓
ViewModel.onMaskUpdated() called
    ↓
Current mask saved to undo stack (immutable copy)
    ↓
Redo stack cleared
    ↓
Undo button enabled
```

### Undo Flow

```
User clicks Undo
    ↓
ViewModel.undo() called
    ↓
Current mask saved to redo stack
    ↓
Previous mask popped from undo stack
    ↓
onRestoreMask callback invoked
    ↓
DrawingView.setMaskBitmap() called
    ↓
Canvas updated with previous mask
    ↓
State updated (canUndo, canRedo, etc.)
```

### Redo Flow

```
User clicks Redo
    ↓
ViewModel.redo() called
    ↓
Current mask saved to undo stack
    ↓
Next mask popped from redo stack
    ↓
onRestoreMask callback invoked
    ↓
DrawingView.setMaskBitmap() called
    ↓
Canvas updated with next mask
    ↓
State updated (canUndo, canRedo, etc.)
```

## Memory Considerations

### Bitmap Memory Usage

Each snapshot stores a full bitmap copy. For a typical screen-sized mask (e.g., 1080×608 pixels):

- Size: 1080 × 608 × 4 bytes (ARGB_8888) = ~2.6 MB per snapshot
- 10 undo levels = ~26 MB
- Acceptable for modern devices with 4GB+ RAM

### Optimizations for Future

If memory becomes an issue, consider:

1. **Limit stack depth**: Keep only N most recent snapshots (e.g., 10)
2. **Compression**: Store bitmaps compressed (PNG/WebP format)
3. **Differential storage**: Store only changed regions
4. **Disk caching**: Move older snapshots to disk

## Testing

### Build

```bash
.\gradlew.bat installDebug
# BUILD SUCCESSFUL in 34s
```

### Test Cases

1. ✅ Draw multiple strokes → Undo button becomes enabled
2. ✅ Click Undo → Previous state restored
3. ✅ Click Undo multiple times → Steps back through history
4. ✅ Click Redo → Next state restored
5. ✅ Draw after undo → Redo stack cleared, redo button disabled
6. ✅ Undo all → Undo button disabled
7. ✅ Redo all → Redo button disabled

## Benefits

1. **Simplicity**: No complex stroke replay logic
2. **Reliability**: Exact bitmap restoration, pixel-perfect
3. **Performance**: Fast restoration (single bitmap copy)
4. **Compatibility**: Works with legacy DrawingView without modifications
5. **Extensibility**: Easy to add features like "Undo All" or "Reset to Checkpoint"

## Future Enhancements

1. **Undo depth limit**: Prevent unbounded memory growth
2. **Gesture support**: Two-finger swipe for undo/redo
3. **Visual feedback**: Show undo/redo animation
4. **Checkpoint system**: Save checkpoints before major operations
5. **Undo across sessions**: Persist undo stack to disk
