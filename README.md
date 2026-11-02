# Live Sudoku Solver — Phase 1 prototype (Python) + Android setup

## Phase 1: prototype on photos

```bash
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install opencv-python numpy pillow tensorflow

python make_digits.py --per-digit 3000   # -> digits.npz (synthetic printed digits)
python train_digits.py                   # -> digits.keras + model.tflite
python run.py my_puzzle.jpg              # -> my_puzzle_solved.jpg
```

Files:
- `grid.py`: preprocess → find grid → perspective warp → 81 cells → 28×28 digits
- `solver.py`: bitmask backtracking; returns 0/1/2 solutions (invalid/unique/ambiguous)
- `make_digits.py`: renders digits in many fonts, then runs them through `clean_cell` so training data matches camera data
- `train_digits.py`: small CNN, exports quantized `model.tflite`
- `run.py`: end-to-end on one photo, draws answers back onto the original image

Done when it works on 20–30 of your own photos (newspaper, book, screen). Every failure is a lesson: save the bad photos as a test set.

Tips:
- Add a few fonts that match the puzzles you solve into `./fonts/` (picked up automatically).
- If a digit is misread, `solve()` usually returns 0 solutions. That is your signal to ask the user to fix a cell.

## Phase 2+: Android native (Kotlin)

1. Android Studio → New Project → Empty Activity (Compose), Kotlin, minSdk 24.
2. Dependencies in `app/build.gradle.kts` (check for latest stable versions):
   - CameraX: `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`
   - OpenCV: `org.opencv:opencv:4.x` (official Maven artifact)
   - TFLite / LiteRT: `org.tensorflow:tensorflow-lite` (or the newer `com.google.ai.edge.litert`)
3. Copy `model.tflite` into `app/src/main/assets/`.
4. Add `<uses-permission android:name="android.permission.CAMERA"/>` and request it at runtime.
5. Call `OpenCVLoader.initLocal()` once at startup.

Porting map:
| Python | Android |
|---|---|
| `grid.py` | `GridDetector.kt` (same OpenCV calls: `Imgproc.adaptiveThreshold`, `findContours`, `approxPolyDP`, `getPerspectiveTransform`, `warpPerspective`) |
| `solver.py` | `Solver.kt` (straight port, IntArray bitmasks) |
| model inference | `DigitClassifier.kt` (TFLite `Interpreter`, batch of 28×28 float inputs) |
| `run.py` overlay | Custom `View`/Compose `Canvas` over `PreviewView`, positions mapped with the inverse homography |

Live loop (CameraX `ImageAnalysis`, `STRATEGY_KEEP_ONLY_LATEST`):
Y plane → Mat → find grid → if the same digits are read in 3–5 consecutive frames → solve once and cache → every frame, only re-find corners and redraw the cached solution.
