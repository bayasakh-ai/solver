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
"just show me the answer" mode: **answers only, no hints**. Hints live on the digital
board. (The design files storyboard hints over a frozen camera frame; reuse their look
and animations, but on the board.)

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
Screens designed in Claude Design (files in `design/`, see `design/README.md`),
implemented in Compose. Design files decide how screens look; this file decides flow,
architecture and rules.
- Portrait, one-handed; keep the screen centre clear for the puzzle. Camera controls:
  the **docked bottom bar** (v2 option "a"). **Material 3 components restyled to the
  Classical design system**: Cormorant Garamond headings over Lora body, no bold
  (semibold at most), outlined primary buttons (never filled), 4 dp corners, amber as
  strokes not fills. Theme: `ui/theme/` (`SudokuLensTheme`, `CameraChrome` for the dark
  chrome over the camera, `SudokuTheme.colors` for non-Material colours).
- **Answer digits:** one fixed hue in **two fixed steps**: deep on paper with a light
  halo, bright on dark screens with a dark halo. The grid's dark-mode vote (camera
  polarity) sets the default step; a single cell switches only when the other step is
  **2x better** against the background behind it (median luma, `AnswerPalette.stepFor`),
  i.e. a real shadow: Ink blue on paper switches at Y <= 109 and comes back at Y >= 114
  (hysteresis + smoothing in `AnswerSteps`). Why not "higher contrast wins": grey
  newsprint (photos/puzzle4, Y 113-147) split 33 pale / 22 deep while both steps were
  only 1.5-2.8:1 there. Measured (2026-11-08): paper conditions and real photos ~100%
  deep (1/223 photo cells switched), dark_mode 100% bright, synth hard shadow switches
  55% of cells (shadow side Y 51-109, where bright is up to 4x better). Answers are
  semibold serif, so they also differ from printed digits by weight and shape.
- **Colorblind-safe by default — never color as the only signal.** Grid outline: dashed
  while reading, dotted on hold steady, solid when solved, double when it needs review.
  The inner 3x3 box lines stay on the live overlay (owner's choice; the design drops
  them): thin and solid in the outline's colour, the pattern only on the outer outline.
  Unsure cells: ring; corrected cells: underline. **Warnings are coral, always paired
  with a shape**; amber is the brand accent (buttons, reading outline, hint strokes).
- **Palettes (measured, `AnswerInkTest`):** *Ink blue* (default) and *High contrast*.
  Ink blue stays distinct from print and all warning colours under protan, deutan and
  tritan simulation, so no per-colour-vision palettes are needed. Green, Magenta and
  Gold were rejected by the same test. Any new palette must pass it.
- Hint explanations in a bottom sheet so the puzzle stays visible.
- Settings toggle for the debug thumbnail of the flattened grid.
- **Still needs design:** the Check screen (after every capture, not only on failure),
  the Choose screen (Show solution / Play with hints / Solve it myself) and the digital
  play board with hints. The design's "Can't solve" grid and digit picker and its hint
  sheet are the starting point for these.

---

## Roadmap

1. **Now:** real-world test session (see Current status), then fix what it reveals.
2. **v1:** scan → auto-capture → check/tap-to-fix → solution / play with progressive,
   animated hints. Live AR mode kept. **Ignore coloured/grey user ink** so a puzzle
   someone has started still scans (see "Ignore user ink" below).
3. **v1.1:** handwriting (check progress, hints from current state, continue on phone);
   puzzle library, daily puzzle, difficulty rating.
4. **v1.2:** monetization.
5. **Later:** iOS (see iOS plan).

### Ignore user ink (v1, built 2026-11-08 for coloured pen; pencil not yet)

Goal: a started puzzle (pen or pencil entries, pencil notes) scans as if blank. Solve
from printed digits only. This is the v1 subset of the handwriting work: no reading
of the user's digits yet. Black pen is out of scope (looks like print by colour and
darkness; needs the v1.1 shape model). **Pencil is out of v1 too** (measured, below).

How it works (`ink.py` <-> `InkClassifier.kt` decide; `grid.py` <-> `GridDetector.kt`
measure; numbers and reasoning in the `ink.py` docstring):
- Each **connected ink piece** in a cell is judged, on the same crop and components as
  `clean_cell`. That handles strokes spilling into a printed cell (puzzle1 r2c7) and
  notes in corners.
- Colour = distance of the piece's median (Cr, Cb) from **neutral ink**
  (`neutral_distance`): neutral ink can show anything between paper offset x
  inkY/paperY (a colour cast MULTIPLIES channels, so black is tinted less than paper)
  and the paper offset itself (thin strokes at half-resolution chroma show the paper's
  colour). Distance from that segment. A plain "distance from paper colour" made black
  print read 7-12 under synthetic colour casts; "paper offset x k" alone made thin
  newsprint read 7-8. The segment keeps printed <= 2.0 in both.
- >= 12 -> user ink, removed from the binarized cell before `clean_cell` (binarize
  unchanged, no retraining). 6-12 -> unsure: kept, cell flagged (overlay draws an orange
  ring until the Check screen exists). < 6 -> printed.
- Safety net: fewer than 17 non-empty cells left -> skip removal for that grid.
- Chroma comes at half resolution like YUV_420_888 (Python downsamples Cr/Cb by 2 and
  warps with corners/2; `FrameAnalyzer` reads U/V only on sharp frames that get read).

Measured (640 px frame or full size, chroma at half resolution):

| | printed | red pen (puzzle1) | synthetic pens (red/blue/navy) |
|---|---|---|---|
| distance from neutral ink | 0.0-2.0 (photos + colour-cast synth) | 42+ (69+ full size) | 0/1032 below 6, 14 below 12 |
| darkness vs local paper | puzzle1 0.79-0.88, **puzzle2/3 0.42-0.60**, synth glare down to 0.12 | 0.33-0.43 | - |

**Why darkness is not used (pencil):** the plan's "darkness < ~0.55 = pencil" would
drop printed digits in 2 of 3 blank newspaper photos and in glare/blur/low_res synth
(165/548 glare pieces < 0.55). Relative to the grid's darkest digits it is still
0.21 (glare) / 0.46 (blur). Pencil needs the v1.1 shape model, or a better signal.

Tests (status):
1. Acceptance: `evaluate.py photos/` 75% -> **100%** (puzzle1: 56/56 pen cells ignored,
   25/25 printed kept, margins 5.0 below "unsure" / 57 above "user").
2. `inspect_photo.py`: ignored-ink panel (magenta = removed, orange ring = unsure) and
   printed-kept / pen-ignored counts with chroma margins. Done.
3. `synth.py`: colour `pen_marks` and `pencil` conditions (a third of user digits wrong,
   spills, corner notes, colour casts), appended so the 10 gray conditions keep the same
   images per seed. pen_marks 100%; pencil 0% (expected: not handled in v1).
4. `--synth 20` before/after: all 10 gray conditions identical (gray -> Cr = Cb = 128,
   chroma 0, nothing removed by construction).
5. `InkClassifierTest` (JVM) on pieces from `export_ink_cases.py`: puzzle1 (full + 640 px),
   puzzle2, synthetic pen_marks, threshold/geometry edge cases. Kotlin recomputes chroma
   from the raw medians, so `neutralDistance` is checked too.
6. **Still to do — real-world session on the phone:** red pen, blue pen (thin dark
   ballpoint is the weak case), pencil + pencil corner notes (expected to fail),
   black pen (expected to fail), warm indoor light, a magazine with coloured digits
   (safety net). Also time `warpChroma` + piece stats on sharp frames.

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
  export_polarity_cases.py  flattened grids + dark-mode votes for PolarityParityTest
  ink.py               printed vs user ink decision rules       <-> InkClassifier.kt
  export_ink_cases.py  ink-piece stats + verdicts for InkClassifierTest
  answer_ink.py        answer colour step per cell (deep/bright)  <-> AnswerInk.kt
  export_background_cases.py  grids + per-cell backgrounds/steps for BackgroundParityTest
  inspect_photo.py     stage-by-stage report + side-by-side image for real photos
  photos/              real test photos + truth files (see Commands)
  model.tflite         current model (also copied into android/app/src/main/assets/)
android/               Android Studio project (Kotlin, Compose, minSdk 24)
  app/src/main/java/com/bay/sudokusolver/
    MainActivity.kt    camera setup, permission, freeze/torch/reset, hosts the ui/camera screen
    FrameAnalyzer.kt   per-frame pipeline on the analysis thread, emits FrameResult
    GridDetector.kt    Kotlin port of grid.py
    DigitClassifier.kt TFLite wrapper, FIXED batch of 81
    PuzzleReader.kt    PuzzleTracker (frame voting) + SelfCorrect
    Polarity.kt        dark-mode vote (looks_dark) + GridPolarity search policy
    InkClassifier.kt   printed / user / unsure ink pieces, safety net (pure Kotlin)
    Solver.kt          Kotlin port of solver.py
    AnswerInk.kt       answer palettes, CellBackground medians, AnswerSteps (smoothing +
                       hysteresis), contrast + colour-vision maths (pure Kotlin)
    ScanState.kt       Searching / Reading / HoldSteady / Solved / NeedsReview (pure Kotlin)
    ui/theme/          Compose theme: Color, Type (bundled variable fonts), Shape, Outline,
                       Theme (SudokuLensTheme, CameraChrome, AnswerDigit), Icons (Lucide), ThemePreview
    ui/camera/         live AR screen: GridOverlay (outline per state, answers, viewfinder),
                       ScanChrome (status chip, docked bar, Freeze/Reset/flash), previews
    ui/settings/       SettingsScreen (answer palette, debug thumbnail), SettingsStore
                       (SharedPreferences; debug off by default)
  app/src/main/res/font/  Cormorant Garamond + Lora variable TTFs from google/fonts, unmodified
                       (OFL; Lora reserves its name, so no static cuts). Licences (fonts, Lucide) in assets/licenses/
  app/src/test/        SolverTest, SelfCorrectTest, PolarityTest, PolarityParityTest,
                       InkClassifierTest, AnswerInkTest, ScanStateTest, BackgroundParityTest
                       (plain JVM, no phone; resources/polarity/, resources/ink/, resources/background/
                       from export_polarity_cases.py / export_ink_cases.py / export_background_cases.py)
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
4. `warp` to 450x450 (50 px cells). If `looksDark` (per-cell vote), invert it.
5. `extractCells`: `binarize` (adaptiveThreshold 21, 10) -> remove user-ink pieces
   (colour, sharp frames only; see "Ignore user ink") -> `cleanCell` -> 28x28 centred
   digit or null (empty).
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
- **Dark mode = per-cell vote on the warped grid** (`looks_dark` / `Polarity.looksDark`):
  each cell asks whether its ink is the dark or bright tail of its pixels; inverted if
  most say bright. A whole-image median < 110 was wrong on 12/20 hard-shadow photos;
  the vote was 0/164 wrong across all conditions. Grid search polarity is separate:
  Python tries image then inversion; Kotlin (`GridPolarity`) searches ONE polarity per
  frame (last that worked, flip after a miss) to keep the 5 ms budget. Bonus: blurred
  paper grids are found far more often in the inverted image (19/20 vs 9/20), so blur
  went 30% -> 70% once the cells were flipped back.
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
python inspect_photo.py photos/x.jpg      # which stage fails -> eval_failures/x_inspect.png
python export_test_cells.py
python export_polarity_cases.py           # after changing looks_dark / cell_votes
python export_ink_cases.py                # after changing ink.py / piece stats in grid.py
python export_background_cases.py         # after changing cell_backgrounds / answer_ink.py
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
Handwritten entries go in a second file, `<name>.user.txt` (same format, `.` for printed
and empty cells); `<name>.txt` stays printed digits only, so evaluate.py still judges
the printed reading. inspect_photo.py reads both.

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
- Synthetic (Windows, seed 0, after per-cell dark-mode vote): 95% total. clean/rotate/
  glare/jpeg/dark_mode/touching_lines 100%, tilt 95%, shadow 95%, low_res 90%,
  **blur 70%** (6 no_grid). Before the vote on the same machine: 91%, blur 30%.
  With ignore-user-ink (2026-11-08): those 10 unchanged, plus pen_marks 100%,
  pencil 0% (expected, v1.1) -> 88% total over 240.
  rotate_steep (page at 45-90 deg, added 2026-11-08, appended so the others keep their
  images): **0%** (19 unsolvable, 1 too_few) -> 81% over 260. Cause measured with an
  angle sweep: the grid is found at every angle and digits read 99-100% once the warp is
  turned upright, but `order_corners` labels corners by screen position, so past 45 deg
  the warp is a quarter turn off and digits are read sideways (2-5% right). Not a model
  problem: an orientation problem. Confidence of the model picks the right quarter turn
  in 149/150 samples (0-360 deg, 7 conditions), but margins are thin (>= 0.010).
- Real photos: **4 of 4 solve (100%)** since ignore-user-ink: puzzle1's 56 red-pen
  cells are ignored and its 25 printed digits solve it. Before that (75%), **3 of 4
  solved:** puzzle2, puzzle3 and puzzle4 are blank newspaper puzzles (3 and 4 are two
  shots of the same puzzle) and all solve, puzzle2 with one self-correction. These
  are the three behind the earlier "3/3". puzzle1 is a *completed* puzzle — 25
  printed digits + 56 in red pen — and was unsolvable. Stage by stage (checked 2026-11-08 against a hand
  transcription): grid, warp and polarity correct; printed digits 25/25 at confidence
  1.00; pen digits 36/56 right, 19 misread, 1 missed (the r2c7 "7" crosses into the
  next cell). That's 20 errors; self_correct allows 2. The model has only ever seen
  printed fonts, and it is confidently wrong on pen (3 misreads at 100%: 1->7, 4->9).
  Reading only the printed cells solves it and matches the pen answers. So puzzle1
  is not a scanner bug: it is the acceptance test for "Ignore user ink" (v1) and the
  first handwriting (v1.1) test case.
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
