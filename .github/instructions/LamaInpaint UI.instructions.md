---
applyTo: '**'
---
# Project Context

A mobile **image in-painting editor**. The ML model is already integrated and **always outputs 512×512**. This spec focuses on **UI/UX and usability only**.

## Hard Constraints

* **Binary mask only.** Pixels sent to the model are strictly 0 or 1 (no opacity/alpha, no colors).
* **Fixed 512×512 processing canvas.** Imported images are transformed (translate/scale/optional rotate/crop) into a 512×512 **workspace** *before* inpainting. All transforms are clamped to workspace bounds.
* **Backend selection is explicit and visible:** **CPU / GPU / NPU** radio options in Settings and a backend badge in the processing HUD.
* Maintain current model API and behavior (no auto-mask, prompts, or new inference logic).

## Primary User Flow

1. **Select image**
2. **Position within 512×512 workspace** (pan/zoom/crop)
3. **Paint binary mask** (brush/eraser)
4. **Run inpainting** (CPU/GPU/NPU)
5. **Compare** (before/after)
6. **Export** (512×512 or optional upscale)

---

# UX Goals

* Fast, obvious, low-friction: import → mask → run → compare → export.
* Canvas-first gestures feel natural (drag, pinch, double-tap to fit).
* Clear progress, timing, and selected backend to inspire confidence.
* Non-destructive edits with undo/redo and autosave.

# Non-Goals

* No new AI features (auto-mask, prompts, multi-res, tiling).
* No network dependence for core tasks.

---

# Screens & Components

## Home

* Large **Select Image** CTA.
* Recent projects grid (thumbnail, last edited).
* System **Light/Dark** theme support.

## Editor

### Top App Bar

* **Back**, Project title
* **Undo / Redo**
* **Export**

### 512×512 Canvas Area

Render layers (bottom → top):

1. Neutral checker/background for contrast.
2. **Transformed source image** (mapped into 512×512 workspace).
3. **Result overlay** (hidden until first run; toggled by compare).
4. **Binary mask** (solid fill preview with dashed edge). *Mask visibility toggle only (no opacity/color controls).*

### Bottom Toolbar (icon + label)

* **Brush** (size, feather)
* **Eraser** (size, feather)
* **Hand** (pan/zoom tool; two-finger pan/zoom always available)
* **Crop/Transform** (enter transform mode; reset)
* **Compare** (modes)
* **Run** (disabled until mask has ≥1 pixel)

### Floating Action Button

* **Run Inpainting** (mirrors toolbar Run; shows spinner/progress when running)

### Processing HUD (overlay, top-right)

* Status: “Processing…” → “Done ✓”
* **Backend badge:** **CPU / GPU / NPU**
* **Timing:** “Inference: {ms}, Total: {ms}”

---

# Canvas & Gestures (512×512 Workspace)

## Coordinate Spaces

* `Workspace`: fixed **512×512** pixels (model input/output space).
* `View`: device pixels for UI rendering.
* Transform matrix maps source image → Workspace.

## Import Behavior

* Default **Fit mode: Contain** (entire image visible inside 512; letterbox allowed).
* Optional **Cover** mode (512 filled; cropping overflow). Persist user selection.
* User can **drag to pan** and **pinch to zoom** the image **within bounds** so the sampled region is exactly 512×512.
* **Double-tap to fit** resets transform to the chosen fit mode.

## Gestures

* **One finger**: draw mask (Brush/Eraser).
* **Two fingers**: pan/zoom (always).
* **Long-press** temporarily toggles Brush↔Eraser.
* Optional: two-finger twist to rotate (snap at 0°/90°; still clamped to keep a valid 512 crop).

## Mask

* Stored as **binary** (e.g., `BooleanArray(512*512)` or `ByteArray` with values {0,1}).
* Brush “feather” only affects stroke footprint calculation; **final stored mask remains binary**.
* **Undo/Redo** records stroke paths (tool, size, feather) and transform edits.
* **Visibility toggle** only; **no opacity or color settings**.

---

# Compare Modes

* **Hold-to-compare**: press & hold shows **Before** (source within workspace), release shows **After**.
* **Split slider**: draggable divider reveals before/after within the same canvas.
* **Side-by-side**: two synchronized views (zoom/pan locked).

---

# Processing Pipeline

## Pre-Run

* Rasterize the transformed source image to **`input512` (Bitmap 512×512)**.
* Rasterize the current mask to **`mask512` (binary 512×512)** aligned to the same workspace.

## Run

* Call the existing model with `input512`, `mask512`, and the selected **backend: CPU / GPU / NPU**.
* Show progress indicator; update **Inference** and **Total** times when done.

## Post-Run

* Display result over the canvas workspace.
* Save a **RunResult** entry to history (backend, timings, path).
* Optional export upscale: **bicubic resample** back to original source dimensions.

---

# Settings

* **Performance Backend**: **CPU / GPU / NPU** (radio group).
* **Brush Defaults**: size, feather.
* **Default Fit Mode**: Contain / Cover.
* **Theme**: System / Light / Dark.
* **Autosave** and **Haptics** toggles.

---

# Data Model (Kotlin)

```kotlin
enum class Backend { CPU, GPU, NPU }

data class ImageTransform(
  val scale: Float,
  val translateX: Float,
  val translateY: Float,
  val rotationDeg: Float
)

data class RunResult(
  val id: String,
  val backend: Backend,
  val inferenceMs: Long,
  val totalMs: Long,
  val outputPath512: String
)

data class Project(
  val id: String,
  val createdAt: Long,
  val updatedAt: Long,
  val srcUri: Uri,
  val transform: ImageTransform,
  val maskBinaryPath: String,     // 512×512 binary mask
  val runs: List<RunResult>,
  val settings: ProjectSettings
)
```

---

# State & Events

```kotlin
sealed interface EditorEvent {
  data object OnUndo : EditorEvent
  data object OnRedo : EditorEvent
  data class OnBrushSizeChanged(val px: Float) : EditorEvent
  data class OnFeatherChanged(val px: Float) : EditorEvent
  data class OnPointerStroke(val points: List<PointF>, val erasing: Boolean) : EditorEvent
  data class OnTransformChanged(val matrix: ImageTransform) : EditorEvent
  data object OnRunRequested : EditorEvent
  data object OnCompareToggle : EditorEvent
  data class OnBackendSelected(val backend: Backend) : EditorEvent
}
```

**Enablement rules**

* **Run** enabled iff mask has ≥1 pixel **and** current transform yields a valid 512×512 crop (no uncovered workspace pixels in **Cover** mode).

---

# File/Module Structure (Android, Jetpack Compose)

```
app/
  ui/
    home/
      HomeScreen.kt
    editor/
      EditorScreen.kt
      CanvasView.kt            // Compose Canvas + gestures
      Toolbars.kt
      CompareViews.kt
      Hud.kt
    settings/
      SettingsScreen.kt
  domain/
    model/
      Project.kt
      ImageTransform.kt
      RunResult.kt
      Backend.kt
    usecase/
      GenerateInput512.kt
      RasterizeMask512.kt
      RunInpainting.kt
  data/
    ProjectRepository.kt
    FileStore.kt
    Preferences.kt
  core/
    graphics/
      MatrixUtils.kt
      BicubicResampler.kt
      MaskRasterizer.kt        // binary mask rasterization
    util/
      Timeit.kt
      Result.kt
```

---

# Rendering & Performance Guidelines

* Cache bitmaps for: **source@512**, **mask preview**, **last result**. Avoid reallocations in draw loop.
* Use Compose `Canvas` with batched draws; keep brush preview lightweight.
* Stream strokes; commit to binary mask bitmap on pointer up (live preview during move).
* Target **60 fps** for pan/zoom/draw.

---

# Accessibility

* Touch targets ≥ **48dp**.
* Content descriptions, e.g., “Run inpainting — backend GPU”.
* Respect system font scale.
* High-contrast dashed outline for mask preview.

---

# Error Handling

* If selected backend is unavailable: fall back to **CPU** and show non-blocking toast:
  “GPU unavailable — switched to CPU.”
* Invalid transform (unfilled workspace in **Cover**): show inline warning + **Reset Transform** action.

---

# Testing & Acceptance

## Unit

* Transform clamping & matrix math.
* Binary mask rasterization (feathered stroke → binary result).
* Run button enablement logic.

## UI

* Gesture tests: pan/zoom/draw/erase.
* Compare modes: hold, slider, side-by-side.
* Settings: backend radio persists and shows in HUD.

## Performance Targets

* Cold start to editable canvas < **2.5 s** on mid-range devices.
* Brush latency < **12 ms** at 512×512.
* Progress feedback appears within **150 ms** of Run tap.

## Definition of Done

* Import → transform → mask → run → compare → export works on **CPU, GPU, and NPU**.
* Model I/O is exactly **512×512**; optional upscale matches expected dimensions.
* Undo/Redo functional for strokes **and** transforms.
* Mask stored and exported as **binary** (no alpha).

---

# Coding Guidelines

* Prefer pure, testable functions in `core/` and `domain/`.
* One immutable UI state source in `EditorViewModel`.
* Use explicit enums for **Backend (CPU/GPU/NPU)** — avoid stringly-typed values.
* Centralize constants: `WORKSPACE_SIZE = 512`.
* Provide helpers for **view↔workspace** coordinate conversion.
* Document public functions with KDoc (include units: px, ms).
* Persist user settings (backend, brush, fit mode) and last project autosave.
