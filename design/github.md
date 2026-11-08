repo: bayasakh-ai/solver
branch: main

## Last sync
date: 2026-11-06T10:16:10Z

### Updated in this project
- Camera background uses puzzle3.jpg from the repo
- Overlay perspective follows GridOverlay's square-to-quad homography (MainActivity.kt)
- Can't-solve flow mirrors SelfCorrect / PuzzleTracker (5-frame voting, 2nd-choice digits)

## Screen map
| Screen | Repo files |
| --- | --- |
| Scanning / Reading / Hold steady | MainActivity.kt (StatusLine, GridOverlay), FrameAnalyzer.kt, PuzzleReader.kt |
| Solved / Hint mode | MainActivity.kt (GridOverlay), Solver.kt |
| Can't solve + digit picker | PuzzleReader.kt (SelfCorrect) |
| Settings (debug thumbnail) | MainActivity.kt (warped bitmap preview) |
| Camera image | puzzle3.jpg |
