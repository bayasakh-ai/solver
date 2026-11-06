# Sudoku Scanner & Solver

Android app that reads a sudoku from the camera — newspaper, book or screen — and
then solves it, hints at it, or checks the user's own progress. The vision pipeline was
prototyped in Python, then ported to Kotlin. Both halves live in this repo and must
stay in sync.

Owner: software engineer moving toward data engineering / ML. Explain the reasoning
behind changes, not just the code. Prefer measured decisions (evaluation numbers) over
guesses.

---

## Product vision

**Scanning is what makes this app special.** Everything else (hints, checking, a puzzle
library) builds on a reliable scan. Sudoku apps are a crowded market; don't let the
library or monetization pull focus from the scanner.

### Core flow (v1)
1. **Scan** — point the camera at a puzzle. Capture happens automatically once the
   reading is stable and sharp (PuzzleTracker consensus = the capture trigger). No
   shutter button needed; holding the phone over the paper for long is uncomfortable.
2. **Check** — the digital grid is shown next to the photo; the user taps any misread
   cell to fix it (1–9 picker, low-confidence cells marked).
3. **Choose** — *Show solution*, *Play with hints*, or *Solve it myself*. Playing
   happens on a digital board on the phone, so the app always knows the user's progress.

Live AR solving (answers drawn on the camera view) stays as a quick
"just show me the answer" mode.

### Hints
Hints follow real solving techniques; the explanation and animation come from the
technique, not from just revealing a cell.

Techniques, easiest first:
1. **Last empty cell** in a row/column/box — "Only 6 is missing in this row."
2. **Hidden single** — "7 can only go in one place in this box."
3. **Naked single** — "This cell sees 1,2,3,4,5,6,8,9 — only 7 is left."
4. **Advanced** (pairs, pointing, etc.) — later. Until supported, say "This step needs
   an advanced technique" and offer to reveal the cell.

Hints are **progressive**: (1) *Nudge* — highlight a region only ("Look at this box");
(2) *Technique* — name the reason; (3) *Show me* — animated explanation ending with
the digit filled in. Controls: next level, replay, next hint, back.

Animations (2–3 s, dim everything outside the relevant area):
- Hidden single: all 7s pulse → lines sweep along their rows/columns crossing out cells
  in the target box → the remaining cell glows → the 7 writes in.
- Naked single: row, column and box highlight → the 8 blocking digits light up one by
  one → the missing digit appears.
- Last empty cell: the unit highlights → the digit fills in.

**A hint is data, not UI.** The hint engine returns a description such as
`technique=HIDDEN_SINGLE, digit=7, unit=BOX(5), target=cell, eliminators=[cells],
crossedOut=[cells], text=...`; the UI only plays it. The same engine rates puzzle
difficulty (the hardest technique a puzzle needs).

### Handwriting: check progress on paper
The app distinguishes **printed** digits (the puzzle) from **handwritten** ones (the
user's entries). This unlocks:
- **Check my progress** — compare the user's digits with the solution. Show the count
  first ("2 mistakes"), positions only if asked — don't spoil.
- **Stuck? Hint from where I am** — hints use printed + the user's *correct* entries.
- **Continue on the phone** — scan a half-solved paper puzzle and keep playing digitally.

How: the model outputs both digit and type (printed/handwritten). Signals: shape
(printed from the font generator; handwritten from MNIST/EMNIST through the same
`clean_cell`, augmented to look like pencil/pen), ink (pencil is grayer; pen is often
blue — add color, the pipeline is currently grayscale only), and sudoku logic (printed
digits must form a valid puzzle with exactly one solution).

**Rules (must hold):**
- **Never "fix" the user's digits.** Self-correction applies to printed digits only. The
  app solves from printed digits, then compares handwriting to the answer. A user's
  wrong digit is real, not a misread.
- **Confidence before accusing.** Only report a mistake when the handwriting reading is
  confident; otherwise ask ("Is this a 4 or a 9?").
- **Ignore pencil notes.** Small candidate numbers in cell corners must be detected and
  skipped, not read as answers. Crossed-out/erased digits are uncertain → ask.

### Puzzle library (v1.1)
Built-in puzzles using the same play screen as scanned ones. The generator exists
(`synth.random_puzzle`); difficulty comes from the hint engine. Features: difficulty
levels, daily puzzle, pencil notes, undo, timer, streaks.

### Monetization (v1.2, only once there are users)
- Never cover the camera view or the puzzle during play with ads.
- Candidates: banner on home/library screens; rewarded ad for extra hints; one-time
  "remove ads" purchase or premium tier (unlimited hints). Combine as needed.
- Requirements before shipping ads: privacy policy, Google Play data-safety form,
  consent screen for EU users (camera app + ads = extra scrutiny).

### Design direction
Screens designed in Claude Design (now inside Claude as artifacts), implemented in Compose.
- Portrait, one-handed; controls in a bottom bar; keep the screen centre clear for the
  puzzle. Material 3.
- **Answer digits:** one fixed hue; lightness adapts per cell to the background (deep on
  paper, bright on dark screens — measured from the empty cells of the warped grid) plus
  a thin contrasting halo for shadows/glare. Answers also differ from printed digits by
  font weight/style, not only by color.
- **Colorblind-safe by default — never color as the only signal.** Grid outline: solid
  when ready, dashed while reading. Low-confidence / wrong cells: ring or dot marker.
  Default palette: blue answers, orange warnings. Settings: palette choice (default,
  high contrast, alternatives for different color vision types).
- Hint explanations in a bottom sheet so the puzzle stays visible.
- Settings toggle for the debug thumbnail of the flattened grid.

---

## Roadmap

1. **Now:** real-world test session (see Current status), then fix what it reveals;
   port dark-mode inversion to Kotlin.
2. **v1:** scan → auto-capture → check/tap-to-fix → solution / play with progressive,
   animated hints. Live AR mode kept.
3. **v1.1:** handwriting (check progress, hints from current state, continue on phone);
   puzzle library, daily puzzle, difficulty rating.
4. **v1.2:** monetization.
5. **Later:** iOS (see iOS plan).

---

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
    MainActivity.kt    camera setup, permission, Compose UI, GridOverlay (outline + answers)
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

Planned new pure-logic files (no Android imports): `HintEngine.kt` (techniques, hint
data, difficulty), `GameState.kt` (board, user entries, notes, undo), `ProgressChecker.kt`
(compare user digits to solution, confidence rules).

---

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
   one solution. (Printed digits only — see handwriting rules.)
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
For handwriting later, extend the truth format to mark user entries separately.

## Working rules

- **Keep logic platform-independent (future iOS port).** New rules and features (hint
  engine, game state, progress checking, solving/voting changes) go in plain Kotlin
  files with NO Android, CameraX, OpenCV or Compose imports — like `Solver.kt` and
  `PuzzleReader.kt`. Only camera, image processing, model runtime and UI code may be
  platform-specific. `MainActivity`/`FrameAnalyzer` call into that logic, not contain it.
- Any change to `grid.py` logic must be mirrored in `GridDetector.kt` (and vice versa).
- Before and after any pipeline or model change, run `python evaluate.py --synth 20`
  and the real-photo set; report the per-condition numbers. Don't trade one condition
  for another silently.
- New pure logic gets JVM unit tests (like `SolverTest`, `SelfCorrectTest`); run them
  after touching Solver/PuzzleReader or any logic file.
- Keep FrameAnalyzer's work per frame small; current budget ~5 ms for grid finding at
  30 fps on the owner's phone.
- Never commit: .venv/, digits.npz, digits.keras, good_fonts*.txt, eval_failures/,
  root-level test_cells.bin / test_expected.txt, android/.idea/, build folders.

## Current status (baseline)

- Synthetic (Mac, 200 images): 92% total. clean/tilt/rotate/glare/jpeg/dark_mode/
  touching_lines ~100%, shadow 95%, low_res 95%, **blur 30%** (grid not found).
- Real photos: 3/3 newspaper photos solve.
- Android: 30 fps, ~5 ms grid finding, answers appear in ~1-2 s, self-correction fixes
  misreads live. Tested mostly on a computer screen so far.
- Next real-world test session: paper, bad light/shadow/glare, steep angles, small
  puzzle in frame, curved book page, pencil marks, dark-mode screens. Record how each
  fails ("no grid", "stuck reading", "wrong answer").

## iOS plan (later — not before Android v1)

Start only when Android passes the real-world tests, has the v1 features and the design
is final. Until then, follow the platform-independence rule so the port stays cheap.

Approach: Kotlin Multiplatform.
- Shared module: `Solver.kt`, `PuzzleReader.kt`, `HintEngine.kt`, `GameState.kt`,
  `ProgressChecker.kt` and any other pure-logic files.
- iOS-specific: camera (AVFoundation), grid detection (port `GridDetector` using OpenCV
  for iOS), model (LiteRT iOS with the same `model.tflite`, or convert to Core ML),
  UI (SwiftUI or Compose Multiplatform).
- Parity: reuse `test_cells.bin` / `test_expected.txt` to prove iOS predictions match
  Python and Android, the same way `DigitClassifierTest` does.
- Needs: Xcode on the owner's iMac, an iPhone, Apple Developer account (free for own
  device, paid for App Store).
