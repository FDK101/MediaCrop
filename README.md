# Media Crop

A lightweight Android app for cropping and trimming videos and images with frame-precise controls and a wallpaper-transparent UI.

---

## Features

### Video Editor
- **Crop overlay** — drag corners, edges, or the entire window to set the crop region. Aspect-locked resizing and a rule-of-thirds grid included.
- **Frame-precise trim** — range slider for IN/OUT points plus ±1 ms / ±100 ms / ±1 s fine-tune buttons for both start and end times.
- **Set IN / Set OUT here** — capture the current playback position as the trim point with a single tap.
- **Default IN time** — persist a default start offset (millisecond precision) that is applied automatically every time you load a new video.
- **Muted export** — output video has audio stripped for clean, shareable clips.
- **Gallery-ready** — exported videos land in `Movies/Cropped` via MediaStore and appear instantly in your gallery app.

### Image Editor
- Same crop overlay as the video editor — corners, edges, move, aspect-locked.
- Saves a full-quality JPEG to `Pictures/Cropped`.

### UI
- Wallpaper-transparent panels (50 % opacity) so your home screen wallpaper shows through the entire app.
- Dark theme with indigo primary (`#6366F1`) and cyan secondary (`#06B6D4`).
- No ads, no analytics, no internet permission.

---

## Screenshots

_Coming soon._

---

## Download

Grab the latest APK from the [Releases](https://github.com/FDK101/MediaCrop/releases) page.

---

## Building from source

**Requirements**
- Android Studio Hedgehog or newer
- JDK 17+
- Android SDK with compile SDK 36

```bash
git clone https://github.com/FDK101/MediaCrop.git
cd MediaCrop
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

For a release build you will need to supply your own signing keystore and update `app/build.gradle.kts` accordingly.

---

## Requirements

| Item | Value |
|---|---|
| Minimum SDK | Android 8.0 (API 26) |
| Target SDK | Android 16 (API 36) |
| Architecture | arm64-v8a, x86_64 |
| Permissions | `READ_MEDIA_VIDEO`, `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` (≤ API 32), `WRITE_EXTERNAL_STORAGE` (≤ API 28) |

---

## Tech stack

| Component | Library |
|---|---|
| UI | Jetpack Compose (Material 3) |
| Video playback | Media3 ExoPlayer |
| Video processing | FFmpegKit (libx264 CRF 20) |
| Architecture | ViewModel + StateFlow |
| Storage | MediaStore API (API 29+), legacy `getExternalStoragePublicDirectory` (API 26–28) |

---

## Project structure

```
app/src/main/kotlin/com/videocrop/
├── MainActivity.kt                  # Single-activity host, screen routing
├── processor/
│   ├── VideoProcessor.kt            # FFmpegKit libx264 crop + trim pipeline
│   └── ImageProcessor.kt            # Bitmap crop + JPEG save
├── ui/
│   ├── HomeScreen.kt                # Launcher: select video or image, default IN time
│   ├── EditorScreen.kt              # Video editor screen
│   ├── ImageEditorScreen.kt         # Image editor screen
│   ├── CropOverlay.kt               # Reusable crop handle overlay (Canvas + gestures)
│   ├── TrimControls.kt              # Trim sliders and time-adjust buttons
│   └── theme/                       # Color, Type, Theme
└── viewmodel/
    ├── EditorViewModel.kt           # Video editor state + SharedPreferences (default IN time)
    └── ImageEditorViewModel.kt      # Image editor state
```

---

## How the crop works

The crop overlay draws directly on a `Canvas` that fills the same space as the video/image. It computes the letterbox/pillarbox bounds from the media's native dimensions and maps touch events to normalised 0–1 crop coordinates. Those fractions are passed to FFmpegKit's `crop` filter (for video) or `Bitmap.createBitmap` (for images) at export time.

Long-press is intentionally disabled on the crop window — the gesture handler cancels any touch that does not exceed the slop threshold within 200 ms, which is well below Android's haptic long-press threshold of ~500 ms.

---

## License

```
MIT License

Copyright (c) 2026 FDK101

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
