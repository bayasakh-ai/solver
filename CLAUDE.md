# Live Sudoku Solver

Android app that solves sudoku puzzles live through the camera: point the phone at a
puzzle and the answers appear in the empty cells, drawn in perspective on the preview.
The vision pipeline was prototyped in Python, then ported to Kotlin. Both halves live
in this repo and must stay in sync.

Owner: software engineer moving toward data engineering / ML. Explain the reasoning
behind changes, not just the code.

## Repo layout

```
/                      Python prototype + training (Python 3.12, venv in .venv/)
  grid.py              find grid, warp, extract cells          <-> GridDetector.kt
  solver.py            bitmask backtracking solver             <-> Solver.kt
  run.py               full pipeline: process(), self_correct  <-> FrameAnalyzer.kt + PuzzleReader.kt
  make_digits.py       synthetic printed-digit dataset (system fonts)
  font_check.py        filters fonts -> good_fonts.txt (machine-specific, gitignored)
  train_digits.py      small CNN -> digits.keras + model.tflite
  diagnose.py          confusion matrix, worst fonts, mistakes.png, --prune
  evaluate.py          solve-rate report: --synth N, or a folder of real photos + .txt truths
  synth.py             synthetic puzzle photos under 10 conditions (used by evaluate.py)
  export_test_cells.py sample cells + TFLite outputs for the Android model test
  model.tflite         current model (also copied into android/app/src/main/assets/)
android/               Android Studio project (Kotlin, Compose, minSdk 24)
  app/src/main/java/com/bay/sudokusolver/
    MainActivity.kt    camera setup, permission, Compose UI, GridOverlay (draws outline + answers)
    FrameAnalyzer.kt   per-frame pipeline on the analysis thread, emits FrameResult
    GridDetector.kt    Kotlin port of grid.py
    DigitClassifier.kt TFLite wrapper, FIXED batch of 81
    PuzzleReader.kt    PuzzleTracker (frame voting) + SelfCorrect
    Solver.kt          Kotlin port of solver.py
  app/src/test/        SolverTest, SelfCorrectTest (plain JVM, no phone)
  app/src/androidTest/ DigitClassifierTest (phone; assets: test_cells.bin, test_expected.txt)
```

Package: `com.bay.sudokusolver`. Screen locked to portrait.
Dependencies: CameraX 1.6.2, OpenCV `org.opencv:opencv:4.12.0`, LiteRT
`com.google.ai.edge.litert:litert:1.4.0` (classic `org.tensorflow.lite.Interpreter` API).

## Pipeline

1. Frame (Y plane, 640x480) -> rotate upright by `rotationDegrees`.
2. `preprocess`: GaussianBlur 7x7 + adaptiveThreshold(GAUSSIAN, INV, 11, 2).
3. `findGrid`: largest convex 4-point contour (border method). If that fails ->
   cell-lattice fallback: square holes of similar size, BFS to assign (col,row),
   homography fit -> rough corners -> `refineEdges` snaps each edge to the strongest
   line via projection profiles. Corners ordered TL, TR, BR, BL.
4. `warp` to 450x450 (50 px cells).
5. `extractCells`: `binarize` (adaptiveThreshold 21, 10) -> `cleanCell` -> 28x28
   centred digit or null (empty).
6. Model -> 9 probabilities per filled cell.
7. Live only: sharp frames only (Laplacian variance of the WARPED grid > SHARP_ENOUGH=100);
   `PuzzleTracker` keeps last 5 readings, a cell is trusted when >= 3 agree.
8. `SelfCorrect`: if no unique solution, try 2nd-4th model choices or "empty" for the
   8 least confident cells, 1 change then 2; keep the most probable fix with exactly
   one solution.
9. Solve once per new consensus, cache; overlay redraws cached answers every frame.
   Grid lost > 0.3 s resets everything.

## Hard-won decisions (don't undo without measuring)

- **Training and inference must binarize identically.** `binarize()` is shared by real
  cells and `make_digits.py`. C=2 turned newspaper paper grain into fake digits; C=10
  fixed it. Changing it requires retraining.
- **`clean_cell` keeps ALL digit pieces**, not the largest component. Keeping only the
  largest made thin-font digits look like "1" (accuracy 95.9% -> 99.75%).
- **Fonts are filtered**: decorative/script/symbol fonts poison training. Run
  `font_check.py`, then `diagnose.py --prune 0.05`. good_fonts.txt is per machine.
- **Cell-lattice fallback + edge snapping** exists because page rules and text often
  touch the grid border (real newspaper photo). Dilate 3x3 first so thin grid lines
  with tiny gaps don't merge cells.
- **Dark mode**: Python `process()` retries with the inverted image. NOT yet ported to
  Kotlin.
- **TFLite batch is fixed at 81.** Resizing the input per frame desynced the output
  shape and crashed (`[24, 9]` vs `[23, 9]`). Never call `resizeInput` per frame.
- **Sharpness is measured inside the warped grid only** — a blank wall scores ~11
  while in focus, so full-frame sharpness is meaningless.
- Overlay uses true perspective (square-to-quad homography), not bilinear
  interpolation — bilinear put the box lines off when tilted.
- Preview and analysis both use 4:3; the overlay maps frame -> view with FILL_CENTER
  math (scale = max ratio, centred).

## Commands

Python (from repo root, venv active: `source .venv/bin/activate` / `.venv\Scripts\activate`):
```
python font_check.py                      # once per machine
python make_digits.py --per-digit 3000
python train_digits.py                    # check val_accuracy (~0.996)
python diagnose.py [--prune 0.05]
python evaluate.py --synth 20             # synthetic stress test
python evaluate.py photos/                # real photos with .txt truths
python run.py photo.jpg                   # single photo -> photo_solved.jpg
python export_test_cells.py
```

After retraining, ALWAYS update the app:
```
cp model.tflite android/app/src/main/assets/
python export_test_cells.py
cp test_cells.bin test_expected.txt android/app/src/androidTest/assets/
```

Android (from `android/`): `./gradlew testDebugUnitTest` (JVM tests),
`./gradlew connectedDebugAndroidTest` (phone tests), or run from Android Studio.

Truth file format (same as run.py prints): 9 lines like `. . 5 . 3 1 . 4 .`.
Name real photos by condition: `news_shadow_01.jpg` + `news_shadow_01.txt`.

## Working rules

- Any change to `grid.py` logic must be mirrored in `GridDetector.kt` (and vice versa).
- Before and after any pipeline change, run `python evaluate.py --synth 20` and the
  real-photo set; report the per-condition numbers. Don't trade one condition for another
  silently.
- Run the JVM tests after touching Solver/PuzzleReader.
- Never commit: .venv/, digits.npz, digits.keras, good_fonts*.txt, eval_failures/,
  root-level test_cells.bin / test_expected.txt, android/.idea/, build folders.
- Keep FrameAnalyzer's work per frame small; current budget ~5 ms for grid finding at
  30 fps on the owner's phone.

## Current status (baseline)

- Synthetic (Mac, 200 images): 92% total. clean/tilt/rotate/glare/jpeg/dark_mode/
  touching_lines ~100%, shadow 95%, low_res 95%, **blur 30%** (grid not found).
- Real photos: 3/3 newspaper photos solve.
- Android: 30 fps, ~5 ms grid finding, answers appear in ~1-2 s, self-correction fixes
  misreads live. Tested mostly on a computer screen so far.

## Next steps (owner's backlog)

1. Real-world test session: paper, bad light/shadow/glare, steep angles, small puzzle
   in frame, curved book page, pencil marks, dark-mode screens.
2. Port dark-mode inversion to Kotlin.
3. Tap-to-fix: let the user correct a cell when the app can't solve.
4. Hint mode: reveal one cell at a time.
5. Handwritten pencil digits: decide whether to ignore or read them (model only knows
   printed digits).
6. UI design (screens designed in Claude Design, then implemented in Compose).
