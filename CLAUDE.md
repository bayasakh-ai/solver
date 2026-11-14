# Sudofolio — Sudoku Scanner & Solver

**Name:** Sudofolio (launcher name `app_name`, was "Sudoku solver"; working name "Sudoku Lens"; renamed 2026-11-11).
Play Store title: **"Sudofolio: Sudoku Scan & Hints"** (30 characters, the store limit).
The package stays `com.bay.sudokusolver`. "Sudoku Lens" in `design/` files means Sudofolio.

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
board. **How the two connect (owner's choice "A", 2026-11-09):** the camera stays in
AR after a solve; the docked bar's **Play** tile (v2's Hint slot) hands the settled
reading (`Capture`) to Check -> Choose -> Play, and *Needs review* offers **Check the
reading** instead of v2's in-camera review card. The camera freezes underneath, so Back
returns to the same answers. (The design files storyboard hints over a frozen camera frame; reuse their look
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
Built-in puzzles using the same play screen as scanned ones. The generator is ported
to Kotlin (`Generator.kt`, from `synth.random_puzzle`) and already powers "Play a
puzzle" (Stage 4); difficulty comes from the hint engine. Still to build: daily puzzle,
streaks, a saved library. Pencil notes, undo, timer exist on the board.

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
  strokes not fills. Theme: `ui/theme/` (`SudofolioTheme`, `CameraChrome` for the dark
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
  Unsure cells: **coral** ring (`#c2412d` paper, `#FF9B85` camera; amber until
  2026-11-09); corrected cells: underline. **Warnings are coral, always paired
  with a shape**; amber is the brand accent (buttons, reading outline, hint strokes).
- **Palettes (measured, `AnswerInkTest`):** *Ink blue* (default) and *High contrast*.
  Ink blue stays distinct from print and all warning colours under protan, deutan and
  tritan simulation, so no per-colour-vision palettes are needed. Green, Magenta and
  Gold were rejected by the same test. Any new palette must pass it.
- Hint explanations in a bottom sheet so the puzzle stays visible.
- Settings toggle for the debug thumbnail of the flattened grid.
- **Core-flow screens are designed** in `design/screens/flow/` (see its README): Check,
  fixing a cell, Choose, Play board, progressive hints and the advanced-technique
  fallback. In them printed digits are regular sans, the player's digits semibold serif
  (Cormorant in the design; **Lora semibold in the app** since Stage 4, see below).
  Still to design: finished puzzle and mistakes-count states (built simply for now, see
  below). Next hint at level 3 and the naked-single / last-empty-cell Show me were
  built without a design (2026-11-14, see "Hints: the three stories" below).
- **Core flow built (Stage 3, 2026-11-09)**: Check (Reading/Photo, tap-to-fix picker),
  Choose, Play with progressive hints. Decisions taken without a design:
  - **Low-confidence ring: top probability < 0.95** (`CheckSession.LOW_CONFIDENCE`,
    measurement in its comment: real photos' right readings >= 0.976, wrong 0.944/0.721;
    synthetic 0.95 rings 7/7 wrong and 0.21% of right ones). Re-check after the
    real-world session. Corrected cells are underlined, never also ringed.
  - Picker's dashed key: the second guess (low confidence), Empty (ink may be the
    user's), or the original reading (self-corrected).
  - After the user edits a cell, Looks right solves the grid **exactly**: no more
    self-correction once a person has looked at it. No unique solution -> stays on
    Check and says why.
  - **Difficulty** (4 dots) = hardest step singles need: Easy (last empty cell, hidden
    single in a box), Medium (hidden single in a row/column), Tricky (naked single),
    Hard (advanced). puzzle3 = Hard.
  - **Show solution** opens the board read-only: printed digits ink sans, answers in the
    answer colour, semibold serif, with a legend. It doesn't touch a game in progress.
  - **Game board settings** (Settings -> Game board, `BoardSettings`): error counter
    (default **off**: it reveals that a digit is wrong), timer, highlight conflicts
    (rules only: a repeated digit gets a coral inset frame, never the answer), row and
    column, box, identical numbers (all on by default).
  - Mistakes = wrong digits when entered; undo/erase don't remove them. Placing a
    digit clears that note from its peers. Finished = header "Solved", keypad off.
  - Hints use printed + correct entries and never target a filled cell; if only a wrong
    entry blocks them: "A digit you entered doesn't fit" with the count, not the place.
  - Level 3 -> Keep playing writes the digit. Animations off -> still frame.
  - **Hints: the three stories (2026-11-14, `HintOverlay.kt` / `HintKeys`, JVM-tested
    in `HintKeysTest`; not seen on the phone yet).** Same marks and colours, one 5 s
    loop, own timeline each. Hidden single: the design's keyframes, unchanged. Naked
    single: row, column and box light up, the eight digits the cell sees are ringed
    **one by one in digit order** (`Hint.eliminators` is in digit order for this
    technique), glow at 46%, then the digit. Last empty cell: the unit's eight digits
    are ticked off one by one (a short amber stroke under each), glow at 30%: less to
    show, so it fills sooner. Level 2 and animations-off show everything at once.
  - **Next hint at level 3:** Replay shrinks to its icon; *Next hint* writes this
    hint's digit and opens the following hint (counted as a new hint), *Keep playing*
    writes it and closes. Three buttons in one row: check the fit on a narrow phone
    with a large font.
  - Frame analysis pauses while a full-screen page covers the camera; the game clock
    runs only while the app is in front.
- **Stage 4 (2026-11-09, fixes from the owner's phone test):**
  - **Player digits, keypad and hint digits: Lora semibold** (`SudokuType.PlayerDigit`),
    not Cormorant: Cormorant's hairlines and small x-height read badly at board size.
    Printed digits stay regular sans; answers on the camera were already Lora.
  - **Solved pop-up**: "Solved!" with time, hints, mistakes (`FinishSummary`; shown once
    per game, survives rotation/process death); *Scan another* (live camera, scan
    forgotten) / *Play another* (difficulty picker). Closing it leaves the solved board.
  - **Play a puzzle** (`Route.NewPuzzle`, `ui/newpuzzle/`): a tile on the camera bar
    while Searching (and on the no-camera-permission screen). Pick a difficulty ->
    `Generator.generate` digs a random grid and **refills any hole that would make
    HintEngine rate it harder** than chosen, then retries until the rating is exact.
    So the rating is the hint engine's by construction (GeneratorTest: 40/40 seeds).
    Givens floor: Easy 36, Medium 30, Tricky 26, Hard none (ends at 23-26). Desktop
    JVM ~2-53 ms per puzzle; runs off the main thread. A generated game in progress is
    offered as Carry on. Header kicker: "New puzzle" vs "Scanned puzzle".
  - **Mark as mine** (Check, fix sheet): the user says a digit is their writing (pencil,
    black pen: colour can't tell). It stays on the Check grid in the player's face,
    leaves the puzzle (solved from printed digits only) and starts the board as the
    player's entry, **right or wrong, never fixed, not counted as a mistake, not
    undoable**. A self-corrected cell marked as mine goes back to what the camera read
    (self-correction is for printed digits only). `Capture.userEntries` is the hand-off
    slot for the v1.1 handwriting model; the scanner leaves it empty for now.
  - **Duplicates in Check**: printed digits that repeat get the coral inset frame at
    once (same mark as Play's conflicts) and the summary says so. The user's own digits
    don't count. Picker: a key that would repeat a printed digit gets a **coral corner
    notch** (shape + colour), still tappable.
  - **Solving suggestion**: while the printed digits don't have exactly one solution,
    the picker tries 1-9 and Empty in the cell and dashes the choice that makes them
    unique. Several work: the scan's value (undoes a bad edit), else the second guess,
    else the only digit. None/ambiguous: the old dashed rules.
  - **Remaining counts** on the keypad: Settings -> Game board, on by default.
  - **Kept game** (`GameStore` = SharedPreferences "game"; rules in `SavedSession` +
    `GameState.encode`, plain text): written after **every** change, so it survives
    process death, swiping the app away and a phone restart. Board, notes, mistakes,
    hints, time, mode (Hints/Myself), last 100 undo steps (~33 KB max). A clock tick
    writes only the time (`SavedSession.change`), not the whole game every second. One
    slot: a new game replaces it; a finished game is deleted. **Continue** = first tile
    on the camera bar while Searching (and on the no-permission screen). If Android
    kills the app in the background with the board open, the Bundle's `board_open`
    flag reopens the board itself. An unreadable save is never deleted (a format bug
    must not wipe a game). Check isn't kept (photo too big; rescanning is quick).
    Tested on the phone (2026-11-09): force-stop (= Android Studio's Terminate), swipe
    from Recents, background `am kill`, phone restart (owner): board, notes, hints, mistakes, timer, undo all
    back. Found on the way: this Samsung's SharedPreferences returns the string with
    trailing spaces, so `decode` trims lines.

- **Welcome + launcher icon (2026-11-13). Not in the design files.** Built and JVM-tested
  2026-11-14 (122 tests); the owner's phone test the same day: welcome, camera permission
  and icon good. The detailed phone list below was not ticked off item by item.
  - **Welcome** (`Welcome.kt` decides, `ui/welcome/WelcomeScreen.kt` draws,
    `Route.Welcome`): Hello -> How it works (Scan, Check, Play) -> Allow the camera.
    Shown once (`SettingsStore.welcomeSeen`), again from Settings -> About -> How
    scanning works (replay: closes back to Settings, ends on Done).
  - **The app no longer asks for the camera at start.** The last welcome page says why,
    then *Allow camera* opens Android's dialog; *Not now* goes on without it. Camera
    already allowed (an update from a build without a welcome): that page is left out and
    the flow ends on *Start scanning*. *Skip* (first page only) stops on the camera page,
    never past it. Back on the first page of a first run leaves the app.
  - **No-camera screen** (`NoCameraScreen`, replaces the bare text): Continue (a kept
    game), **Allow camera**, Play a puzzle. Android shows its dialog only twice; after
    that a request is refused silently, so `CameraAsk.dialogWasShown` (rationale flag
    before/after + time to answer) decides whether to open the app's page in the system
    settings instead. Its 450 ms limit is a guess. The permission is re-read in onResume.
  - **The camera page promises "no picture is saved, the app has no internet access".**
    True while the manifest asks for CAMERA only and Check's photo stays in memory.
    **Rewrite it before adding INTERNET** (ads, opt-in upload of corrections, v1.2).
  - **Phone checks:** fresh install (three pages, Allow -> scanner); Not now -> no-camera
    screen -> Allow camera; refuse twice, then Allow camera opens system settings, and
    allowing there shows the scanner on return; dismiss the dialog without choosing
    (must NOT open settings); large font size on a small screen (pages scroll, buttons
    stay put); TalkBack reads "1 of 3" and skips the picture.
  - **Launcher icon: direction "scan" on the dark chrome** (`scan_dark`; owner
    confirmed on the phone, 2026-11-14). Amber viewfinder corners, the stubs of a 3x3 box, a Lora
    semibold 9 in the bright answer blue. Generated by `design/icon/make_icon.py`
    (glyph outline from the bundled font; adaptive + themed layers, API 24-25 bitmaps,
    `design/icon/play_store_512.png`). Other directions drawn there: `scan` (same mark
    on paper; it fades on the Play Store's white page), `folio` (digit too small at
    48 dp), `monogram` (says nothing about scanning). Switch: `--apply <name>`.

---

## Roadmap

1. **Now:** real-world test session (see Current status), then fix what it reveals.
2. **v1:** scan → auto-capture → check/tap-to-fix → solution / play with progressive,
   animated hints. Live AR mode kept. **Ignore coloured/grey user ink** so a puzzle
   someone has started still scans (see "Ignore user ink" below).
3. **v1.1:** handwriting (check progress, hints from current state, continue on phone);
   puzzle library, daily puzzle, difficulty rating.
   **Pulled into v1 (owner, 2026-11-14): tell printed from handwritten** (pencil, black
   pen) so a started book puzzle scans; see "Printed vs handwritten" below. *Reading*
   the handwritten digits (check progress, hints from where I am) stays v1.1.
   **Postponed to v2 (owner, 2026-11-14):** the 247 site's bamboo frame
   (screen_angle_247 / screen_glare_247, snap to the 9x9 lattice).
4. **v1.2:** monetization; continuous learning for the digit model (see below, not now).
5. **Later:** iOS (see iOS plan).

### Continuous learning for the digit model (v1.2, not now)

- **Data:** Check corrections (cell crop + corrected digit) and "Mark as mine" cells
  (handwritten labels). Only keep a correction if the final puzzle has exactly one
  solution, so sudoku rules validate the labels.
- **Approach:** opt-in upload of 28x28 crops + labels; retrain offline with the Python
  pipeline, mixing real and synthetic data; ship a new model only if it beats the
  current one on the fixed real-photo set and passes the parity tests
  (`DigitClassifierTest`, `test_cells.bin`); deliver via app update or a downloadable
  model.
- **Optional:** on-device personal memory (reuse a user's earlier correction when a new
  cell looks almost identical).
- **Not allowed:** automatic on-device training without an evaluation gate.
- **Requirements:** consent, privacy policy, Play data-safety form (shared with
  monetization).
- **Note:** local-only collection of corrections could move to v1.1 alongside the
  handwriting model, since "Mark as mine" feeds both.

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
  unchanged, no retraining). 6-12 -> unsure: kept, cell flagged (overlay draws a coral
  ring; Check asks about it). < 6 -> printed.
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

### Printed vs handwritten (v1, started 2026-11-14; Python only so far)

Why now: the owner's phone test on a sudoku book. Pencil and black pen can't be told
from print by colour or darkness (see above), so the signal is **shape**.

- **Owner's decision (2026-11-14): don't ignore handwriting, carry it over.** Printed
  digits = the puzzle (givens, not editable); handwritten digits are read and start the
  board as the player's entries (editable, right or wrong, never fixed: the Mark as mine
  rules, `Capture.userEntries`). The puzzle is still solved from printed digits only.
  Checking progress / hints from where I am on paper stay v1.1.
- **A separate small model** (`hand.tflite`, same 28x28 cleaned cell as the digit
  model), so `model.tflite` and its parity tests stay untouched.
  `make_handwritten.py`: MNIST digits redrawn as cell entries (size, stroke thinned to
  1.6-4.2 px, slant, offset, grey ink, grid lines, blur, noise) through the same
  `binarize` + `clean_cell`. `train_hand.py`: printed = digits.npz, handwritten =
  hand.npz, 27,000 each.
- **First numbers (2026-11-14, 640 px, gray only, threshold 0.5):** synthetic validation
  97.9%. Real cells: printed called handwritten 27/725 (3.7%; 14 of them in
  screen_glare_247, a bad warp that fails anyway; 13/709 = 1.8% without it; book,
  puzzle1/3/4 0), puzzle1's red-pen digits called handwritten 52/55 (94.5%).
  Typed entries on screens (screen_filled_*) look printed: 6/61 flagged, as expected.
- **Geometry alone is not enough** (puzzle1): printed height 25-31 px vs pen 21-38,
  width 11-22 vs 6-38, stroke 3.8-4.7 vs 2.7-3.8: overlapping. Possible second signal
  later (printed digits of one puzzle share a size and baseline).
- **Owner's book photos (2026-11-14): `book_pen_01-02`, `book_pencil_01-04`** (one
  writer, thin cursive digits; truths drafted by Claude from the photos, the three
  finished ones confirmed by the solver; owner to confirm). 640 px, gray only:
  | | printed called hand | hand called hand (CNN >= 0.5) | hand digit read right (print model) |
  |---|---|---|---|
  | book pen | 2/58 | 87/104 (84%) | 91/104 (88%) |
  | book pencil | 0/120 | 83/101 (82%) | 90/101 (89%) |
  | puzzle1 red pen | 0/25 | 52/55 | 36/55 (65%) |
  Pencil is not lost by `binarize` (0 handwritten cells missed).
- **Better signal than the CNN: stroke width and ink darkness relative to the puzzle's
  own printed digits** (reference = median over cells the CNN is sure are printed,
  P < 0.1). Stroke = 2 x mean of the top fifth of the distance transform; darkness =
  (paper - ink p25) / paper. Printed (713 cells, 24 photos): stroke 0.89-1.20,
  darkness 0.77-1.13 (p1-p99). Handwritten (260): stroke 0.61-0.91, darkness 0.32-0.85.
  Rule **stroke < 0.92 AND darkness < 0.88**: printed flagged 0/713, handwritten missed
  2/260 (CNN alone: 8 / 38). Thresholds were picked on these same cells (two writers,
  one book + one newspaper): not a held-out number. Fails by design when the print is
  thin and the pen is thick; the CNN is the fallback there (disagreement -> unsure).
- **Three of the six book puzzles have several solutions** from their printed digits
  (book_pencil_02: 8, _03: 9, _04: 16; adding the handwritten digits doesn't change the
  count). Either the book is like that or a given was misread in the truth: owner to
  check against the book's answer key. If real, "exactly one solution" rejects genuine
  puzzles and needs a decision.
- **Built (2026-11-14), Python and Kotlin. Owner's phone test the same day: works with pencil and pen** (the detailed phone checks and the timing below were not ticked off one by one).
  `hand.py` <-> `Hand.kt` decide, `grid.cell_shape` <-> `Hand.cellShapes` measure (pure
  Kotlin: components, 5x5 chamfer distance, histograms), `run.split_hand` <->
  `FrameAnalyzer.read` apply. Rule and numbers in the `hand.py` docstring: HAND = thin
  AND pale against the puzzle's own print; a stroke only just thinner (>= 0.85) needs
  the shape model's P >= 0.9; other mixed signs -> UNSURE (stays in the puzzle, Check
  asks); < 5 sure-printed cells or < 17 printed left -> no split. The first version
  (two of three signs, height as a vote) moved 7 synthetic printed digits out (glare
  100 -> 85%, tilt 95 -> 90%, moire 90 -> 85%) and lost screen_glare_sudokucom.
  Split on vs off (`evaluate.py --no-hand`):
  - photos at 640: 63% -> **71%** of 41, no photo lost (book_pen 0/2 -> 2/2,
    book_pencil 0/4 -> 1/4: the other three are the several-solutions puzzles above).
    Printed called handwritten 0/329, handwritten found 201/205 on paper (4 unsure).
  - `--synth 20` (Windows): the 13 other conditions identical, 90% over 280.
  - **Synthetic `pencil` stays 0% and is not a test of this**: its user digits are
    Hershey-font digits as thick as the print (stroke median 0.96 of the print, shape
    model P median 0.01), so the split calls them printed 221 / unsure 182 / hand 67.
- **Reader for handwritten digits: `hand_digits.tflite`** (`train_hand_digits.py`:
  hand.npz + digits.npz, same CNN). Only cells called HAND are read with it. On 260
  real handwritten digits (book 205 + puzzle1's red pen 55): print model 217 (83%),
  the reader 237-246 over three seeds (91-95%), the shipped one 242. In the pipeline
  the book's found handwriting is read right 193/201. Commonest miss: a 1 with a flag
  read as 7. More synthetic data (9,000 per digit) or averaging with the print model
  did not help. The quantized tflite files agree with Keras (hand.tflite: 0 of 1,209
  verdicts differ, max |dP| 0.022; hand_digits.tflite: 0 of 260 readings differ).
- **In the app:** each sharp frame measures shapes, runs hand.tflite, and moves HAND
  cells out of the reading into a separate vote (`PuzzleTracker.userEntries`: a cell
  counts when 3 of 5 frames call it handwritten, digit = the commonest reading; looser
  than the printed consensus so a wobbly handwritten digit can't hold up the puzzle).
  `Capture.userEntries` carries them: Check shows them as yours (Mark as mine rules:
  the board starts with them as the player's entries, never fixed), the puzzle is
  solved from printed digits only. UNSURE -> `Capture.handUnsure` -> coral ring,
  `UnsureReason.HAND`: "it looks a little like handwriting ... Mark as mine". Live AR
  draws answers over handwritten cells too (as it already did over ignored pen).
  - **Not measured: time on the phone.** Desktop JVM: `Hand.cellShapes` 2.3 ms per
    frame (14 ms before histograms replaced sorting), plus two more model runs of 81,
    on sharp frames only. `PipelineTimingTest` now logs it ("printed/handwritten ...
    ms"); push the book photos too.
  - **Size sweep (14 photos at 640, grid at 100 / 75 / 55% of that, sharp frames only,
    Python):** printed called handwritten 0/386, 0/257, 0/91. Handwriting found
    199/205, 181/205, 71/89 (the rest unsure: ringed, kept in the puzzle) and read
    right 192, 169, 51. So a small grid loses handwriting to "unsure" and misreads
    more of it, but never moves print out. Gaussian blur sigma >= 1 made every frame
    fail the sharpness gate, so blurred frames are not read at all.
  - **Phone checks:** the book in pencil and in ballpoint (entries arrive on Check as
    yours, printed digits stay, puzzle solves); a blank puzzle (nothing marked as
    yours, no extra coral rings); glare on print (rings at most, never "yours"); thick
    black pen (expected: stays printed or ringed); does the vote settle as fast as
    before.
- **The three several-solutions truths were re-checked against the flattened photos
  (2026-11-14):** this book prints its givens on grey cells, so print and handwriting
  can't be confused in the truth, and every given matches. The printed digits really
  leave 8 / 9 / 16 solutions that differ in 15-22 cells, and the user's entries fit
  all of them. So the book's puzzles are not all proper, or need a rule we don't know
  (not diagonals: book_pencil_02 has two 4s on the main diagonal). **Owner's decision
  still needed:** today such a scan ends on Check with "more than one solution".
- **Still to do:** phone test (above); corner notes, crossed-out digits (book_pen_02
  r4c1); low-confidence handwritten readings ("Is this a 4 or a 9?" before judging,
  v1.1); typed entries on screens (screen_filled_*: look printed, 0/61 found); a
  thick handwriting next to thin print (see `handwritten` below).
- **Synthetic `handwritten` condition** (`synth.py`, appended last; MNIST digits redrawn
  thin in pencil grey / ballpoint dark via `make_handwritten.glyph`, a third of them
  wrong): 0% without the split, **35%** with it (Windows, --synth 20; the 14 earlier
  conditions identical, 86% over 300). Over 30 more images: printed kept 833/839
  (6 unsure, 0 moved), written digits 404 moved / 372 unsure / 7 printed. The unsure
  ones are pale with a sure shape model but as thick as a thin printed FONT (307);
  one wrong digit left in the puzzle makes it unsolvable, hence 35%. Accepting
  "pale + sure model" as handwriting moved glare-damaged print out (numbers in the
  `hand.py` docstring), so it is not done. The shape model was trained on the same
  generator: this condition checks the pipeline around it, not the model.

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
  make_handwritten.py  synthetic handwritten cells from MNIST -> hand.npz
  train_hand.py        printed-vs-handwritten CNN -> hand.keras + hand.tflite
  train_hand_digits.py reader for handwritten digits -> hand_digits.keras + hand_digits.tflite
  diagnose.py          confusion matrix, worst fonts, mistakes.png, --prune
  evaluate.py          solve-rate report: --synth N, or a folder of real photos + .txt truths
  synth.py             synthetic puzzle photos under 15 conditions (used by evaluate.py)
  export_test_cells.py sample cells + TFLite outputs for the Android model test
  export_polarity_cases.py  flattened grids + dark-mode votes for PolarityParityTest
  export_grid_check_cases.py  binarized quads (grids and not) + box-line scores for GridCheckParityTest
  ink.py               printed vs user ink decision rules       <-> InkClassifier.kt
  hand.py              printed vs handwritten decision rules    <-> Hand.kt
  export_hand_cases.py flattened grids + cell shapes + verdicts for HandParityTest
  export_ink_cases.py  ink-piece stats + verdicts for InkClassifierTest
  answer_ink.py        answer colour step per cell (deep/bright)  <-> AnswerInk.kt
  orientation.py       which way is up: turn chosen by the reading  <-> Orientation.kt
  export_background_cases.py  grids + per-cell backgrounds/steps for BackgroundParityTest
  inspect_photo.py     stage-by-stage report + side-by-side image for real photos
  photos/              real test photos + truth files (see Commands)
  model.tflite         current model (also copied into android/app/src/main/assets/)
android/               Android Studio project (Kotlin, Compose, minSdk 24)
  app/src/main/java/com/bay/sudokusolver/
    MainActivity.kt    camera setup, permission, freeze/torch/reset, hosts the ui/camera screen
    FrameAnalyzer.kt   per-frame pipeline on the analysis thread, emits FrameResult
    GridDetector.kt    Kotlin port of grid.py
    DigitClassifier.kt TFLite wrapper, FIXED batch of 81 (model.tflite, hand.tflite, hand_digits.tflite)
    PuzzleReader.kt    PuzzleTracker (frame voting, the user's handwritten digits apart) + SelfCorrect
    Polarity.kt        dark-mode vote (looks_dark) + GridPolarity search policy
    GridCheck.kt       is a found quad a sudoku? box-line score (pure Kotlin)   <-> grid.box_line_score
    InkClassifier.kt   printed / user / unsure ink pieces, safety net (pure Kotlin)
    Hand.kt            printed / handwritten / unsure by stroke, darkness, height against the
                       puzzle's own print; cell shapes (pure Kotlin)   <-> hand.py + grid.cell_shape
    Solver.kt          Kotlin port of solver.py
    AnswerInk.kt       answer palettes, CellBackground medians, AnswerSteps (smoothing +
                       hysteresis), contrast + colour-vision maths (pure Kotlin)
    ScanState.kt       Searching / Reading / HoldSteady / Solved / NeedsReview (pure Kotlin)
    Capture.kt         hand-off from the scan to Check: read vs fixed digits, confidence,
                       second guess, unsure ink, pen count, solution (pure Kotlin)
    Route.kt           Route (Camera / Settings / Welcome / Check / Choose / Play) + NavStack, camera always root (pure Kotlin)
    Welcome.kt         welcome pages (which exist, what the last button does), CameraAsk (pure Kotlin)
    Units.kt           rows / columns / boxes, peers, candidates (pure Kotlin)
    CheckSession.kt    Check: marks, why unsure, dashed suggestion, user edits, confirm (pure Kotlin)
    GameStore.kt       the kept game in SharedPreferences, written after every change (Android side)
    Generator.kt       puzzles rated exactly as chosen (port of synth.random_puzzle + HintEngine) (pure Kotlin)
    HintEngine.kt      hints as data (last empty cell, hidden/naked single, advanced fallback),
                       difficulty rating (pure Kotlin)
    GameState.kt       Puzzle, BoardSettings, the board: entries, notes, undo, mistakes,
                       conflicts, hints used, time (pure Kotlin)
    Orientation.kt     corner order, quarter-turn choice, corner tracking, lock (pure Kotlin)
    ui/theme/          Compose theme: Color, Type (bundled variable fonts), Shape, Outline,
                       Theme (SudofolioTheme, CameraChrome, AnswerDigit), Icons (Lucide), ThemePreview
    ui/camera/         live AR screen: GridOverlay (outline per state, answers, viewfinder),
                       ScanChrome (status chip, docked bar, Play/Freeze/Reset/flash), previews
    ui/board/          shared SudokuGrid, flow header, outlined buttons
    ui/check/          CheckScreen (Reading/Photo, fix sheet), GridPhoto (flattened colour still, OpenCV)
    ui/choose/         ChooseScreen (difficulty, three ways to play)
    ui/newpuzzle/      NewPuzzleScreen (Play a puzzle: pick a difficulty, Carry on)
    ui/play/           PlayScreen (board, keypad, hint sheet, Show solution), HintOverlay (hint marks
                       + Show me keyframes)
    ui/settings/       SettingsScreen (answer palette, game board, debug thumbnail, welcome again),
                       SettingsStore (SharedPreferences; debug off by default, welcomeSeen)
    ui/welcome/        WelcomeScreen (Hello, How it works, Allow the camera), NoCameraScreen
  app/src/main/res/font/  Cormorant Garamond + Lora variable TTFs from google/fonts, unmodified
                       (OFL; Lora reserves its name, so no static cuts). Licences (fonts, Lucide) in assets/licenses/
  app/src/test/        SolverTest, SelfCorrectTest, PolarityTest, PolarityParityTest,
                       InkClassifierTest, AnswerInkTest, ScanStateTest, BackgroundParityTest,
                       OrientationTest, CaptureTest, RouteTest, CheckSessionTest, HintEngineTest,
                       GameStateTest, GeneratorTest, WelcomeTest, GridCheckParityTest, HandParityTest
                       (plain JVM, no phone; resources/polarity/, resources/ink/, resources/background/, resources/gridcheck/,
                       resources/hand/ from export_hand_cases.py;
                       from export_polarity_cases.py / export_ink_cases.py / export_background_cases.py /
                       export_grid_check_cases.py)
  app/src/androidTest/ DigitClassifierTest (phone; assets: test_cells.bin, test_expected.txt),
                       PipelineTimingTest (phone, measurement only: find/read time at 640x480,
                       1280x960, find-at-640/read-at-1280; photos pushed with adb, see its doc)
```

Package: `com.bay.sudokusolver`. Screen locked to portrait.
Dependencies: CameraX 1.6.2, OpenCV `org.opencv:opencv:4.12.0`, LiteRT
`com.google.ai.edge.litert:litert:1.4.0` (classic `org.tensorflow.lite.Interpreter` API).

Planned pure-logic file (no Android imports): `ProgressChecker.kt` (compare the user's
handwritten digits to the solution, confidence rules; v1.1). `HintEngine.kt` and
`GameState.kt` exist since Stage 3.

Viewing a screen without a camera: the debug build includes Compose's PreviewActivity,
`adb shell am start -n com.bay.sudokusolver/androidx.compose.ui.tooling.PreviewActivity
--es composable com.bay.sudokusolver.ui.play.PlayScreenKt.PPlayInteractive` (force-stop
the app between previews; it reuses the running one).

---

## Pipeline

1. Frame (Y plane, 640x480) -> rotate upright by `rotationDegrees`.
2. `preprocess`: GaussianBlur 7x7 + adaptiveThreshold(GAUSSIAN, INV, 11, 2).
3. `findGrid`: largest convex 4-point contour (border method). If that fails ->
   cell-lattice fallback: square holes of similar size, BFS to assign (col,row),
   homography fit -> rough corners -> `refineEdges` snaps each edge to the strongest
   line via projection profiles. Then (2026-11-11) the convex hull of the biggest
   contour as 4 rough corners + `refineEdges` (decorated frames: the 247 site's bamboo),
   then a last-resort border >= 3% of the frame (small grids; their thin cell lines blur
   away). Grids found by the earlier methods are found the same way. Lowering the 10%
   border floor or the cell floor instead broke puzzle1. Corners ordered TL, TR, BR, BL.
   **Every candidate must pass the grid check** (`looks_like_grid` / `GridCheck`, see
   Hard-won decisions); a rejected one is skipped and the next method gets its turn.
4. `warp` to 450x450 (50 px cells). If `looksDark` (per-cell vote), invert it.
4b. **Orientation** (orientation.py / Orientation.kt): corners are ordered clockwise from
   the one nearest the top-left on screen, which is a quarter turn off past 45 deg. On a
   new grid (first sharp frame; Python: every photo) the warp is read at all 4 quarter
   turns; the most confident turn whose reading is a consistent sudoku wins, and the
   corners are relabelled to match. Live: each later frame keeps every corner on the
   nearest corner of the last frame (bestRoll), so turning the phone mid-scan is fine;
   the decision locks once consistent (else after 5 sharp frames). Turn changes reset
   the vote. Cell backgrounds are measured after this, on the upright warp.
5. `extractCells`: `binarize` (adaptiveThreshold 21, 10) -> remove user-ink pieces
   (colour, sharp frames only; see "Ignore user ink") -> `cleanCell` -> 28x28 centred
   digit or null (empty).
6. Model -> 9 probabilities per filled cell.
6b. Printed or handwritten (sharp frames only; see "Printed vs handwritten"): cell
   shapes + hand.tflite -> handwritten cells leave the reading and are read by
   hand_digits.tflite as the user's entries; unsure ones stay and are flagged.
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
- **Up is decided by the reading, not by screen position.** Corner labels from
  find_grid are only a starting point; confidence alone picks the right quarter turn
  149/150 with margins down to 0.010 (the model is confidently wrong on sideways
  digits), so sudoku consistency confirms it. Live frames track corners instead of
  re-deciding, which keeps the cost to one 4-way reading per grid.
- **A quad is a grid only if it shows its box lines** (2026-11-14, `grid.box_line_score`
  <-> `GridCheck.boxLineScore`). The finder returned any four-cornered shape, so the
  outline flashed onto a book cover, a title block and a keyboard (owner's phone test;
  `photos/nogrid_*`: the 10% border, the hull fallback and the 3% border all did it).
  Check, on the flattened dark-on-light quad: `line_ink` (adaptive threshold 21, **C=2**,
  for this check only), then **3 of the 4 inner box lines** must be ink over >= 0.93 of
  their length (best column/row within 9 px, ink within 5 px sideways counts).
  - **Only the box lines**: the thin cell lines of several screen sudokus are too faint
    (2-6 of 16 inner lines found on solved screen_web / sudokucom photos).
  - **Not `binarize` (C=10), not all 4 lines.** The first version (C=10, weakest line
    >= 0.6) separated sharp photos perfectly (270 solved grids >= 0.77, false <= 0.41)
    but on the phone the grid was found late and only up close: with real grids at
    30-100% size and blur sigma 0-2 it rejected 147 of 716. Now 23 of 716 (sharp set:
    0/270, min 0.976); false quads 0/376 (5 no-grid photos jittered, shrunk, blurred:
    <= 0.77, one mirrored-title artefact at 0.907). Line prominence (line minus
    background share) was worse: moire raises the background.
  - **Live: judged once** (`GridCheck.sameQuad`, Kotlin only): a quad whose corners are
    within 15% of a side of the grid already on screen is kept without the check, so a
    blurred frame doesn't drop the outline and restart the vote.
  Result: nogrid 0/5 -> 5/5 rejected, --synth 20 identical (90% over 280, Windows),
  photos at 640 identical for the 28 with truths, book 2/2; size x blur sweep (11 photos,
  15 settings): 0 solvable frames lost (first version: 6 of 55). Not measured: curved
  pages, phone timing (a new grid is warped + voted twice; `PipelineTimingTest` includes
  the check).
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
python export_grid_check_cases.py         # after changing box_line_score / BOX_LINES
python export_ink_cases.py                # after changing ink.py / piece stats in grid.py
python export_hand_cases.py               # after changing hand.py / cell_shape in grid.py
python make_handwritten.py && python train_hand.py && python train_hand_digits.py   # handwriting models
python export_background_cases.py         # after changing cell_backgrounds / answer_ink.py
python design/icon/make_icon.py [--apply scan_dark]   # launcher icon (pip install fonttools cairosvg pillow)
```

After retraining, ALWAYS update the app:
```
cp model.tflite android/app/src/main/assets/
python export_test_cells.py
cp test_cells.bin test_expected.txt android/app/src/androidTest/assets/
```

After retraining a handwriting model: `cp hand.tflite hand_digits.tflite
android/app/src/main/assets/`, re-run `evaluate.py photos/ --frame 640` (the split table)
and `export_hand_cases.py`.

Android (from `android/`): `./gradlew testDebugUnitTest` (JVM tests),
`./gradlew connectedDebugAndroidTest` (phone tests), or run from Android Studio.

`photos/nogrid_*.jpg` (no truth file): scenes without a puzzle; evaluate.py counts a
reported grid there as `false_grid`. `--no-grid-check` measures without the check.

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
  **Fixed** (orientation step + corner order by angle): rotate_steep **100%**, the other
  12 conditions identical -> 88% over 260; photos 4/4. The last failure was a grid at
  exactly 45 deg where the old min/max corner tests picked one corner twice (blank warp).
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
- **Screen session (2026-11-11, `photos/screen_*`, 24 photos of 5 web sudoku sites;
  plain/angle/glare/small/dark/filled).** Judge with `evaluate.py photos/ --frame 640`
  (the app's frame size). Baseline 11/24 -> **15/24** after: `solved_extra` (an extra
  digit equal to the solution counts as solved, listed separately; never in a user
  cell), last-resort small border, hull fallback. Left: filled 0/5 (user digits on a
  screen are read as printed: black/bold entries look like print, blue entries are only
  2/10 removed), small_expert / small_web (3.6% / 2.8% of the frame), angle_247 /
  glare_247 (an edge snaps onto the bamboo, not the grid line: next, snap to the 9x9
  lattice). --synth 20 (Mac): 90% over 260 -> 91% over 280 (blur 80 -> 90%, low_res 95 -> 100%,
  new `moire` condition 85%, 100% with the gated descreen below).
  - **Small grids at 640x480 can't be read below ~6% of the frame** (synthetic sweep,
    found/solved of 10: 10% 10/10, 6% 10/9, 4% 6/2, 3% 1/0). 1280x960 reads to 3%
    (10/10). Hence "Move closer" (`ScanState.MOVE_CLOSER_SHARE` = 0.06): advice, reading
    carries on. **Timed on the phone** (PipelineTimingTest, Galaxy A35, 24 screen photos,
    2026-11-12; median / p90):
    | mode | grid found | find | warp+cells+model |
    |---|---|---|---|
    | 640x480 | 22/24 | 5.2 / 23.2 ms | 21.9 / 23.9 ms |
    | 1280x960 | 23/24 | 34.1 / 106.2 ms | 29.1 / 31.8 ms |
    | hybrid (find 640, read 1280) | 22/24 | 5.3 / 18.7 ms | 25.6 / 29.6 ms |
    Finding at 1280 is ~6.5x over the 5 ms budget; the hybrid keeps finding at 640 cost
    and adds ~3.7 ms per read (reads run on sharp frames only). The hybrid is the
    affordable way to read small grids; it needs the descreen step ported first.
  - **Moire at full resolution breaks the dark-mode vote**: empty screen cells get
    enough contrast to vote at random (screen_web 36/34, sudokucom 30/46 the wrong way).
    At 640 the frame's area downscale averages it out (dark votes <= 7 on every screen
    photo). Any higher analysis resolution needs a descreen step: `grid.descreen`
    ("gated", 5, 20) = Gaussian 5x5 on the warp only when `cell_texture` (lower quartile
    of cell p2-p98 spread; paper 7-13, moire 20-50) >= 20. Measured: synth moire 85 ->
    100%, the other 13 conditions identical, full-res screen 12 -> 13/24, 640 unchanged.
    Blanket blur 3/5 or downscale 0.5 cost shadow/blur images; 0.33 broke everything.
    **Off by default** (`DESCREEN = None`), not ported: no gain at 640; port it with a
    higher-resolution mode. No moire training augmentation needed so far.
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
