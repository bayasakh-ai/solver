# Core flow screens (canvas "Sudoku Lens — Check, Choose, Play")

Main.dc.html              Check · after capture (Reading/Photo switch, unsure = coral ring,
                          corrected = underline, "N pen entries ignored")
CheckFix.dc.html          Check · fixing a cell (photo crop, explanation, 1–9 + Empty picker,
                          model's second guess dashed)
Choose.dc.html            Choose: preview, difficulty, Play with hints / Solve it myself / Show solution
Play.dc.html              Play board: select cell, digits, undo, erase, notes, remaining counts, Hint
PlayHint1/2/3.dc.html     Progressive hint: Nudge, Technique, Show me (animated) — hidden single
PlayHintAdvanced.dc.html  Fallback: "needs an advanced technique", Keep trying / Reveal this cell

Example puzzle: photos/puzzle3 (= puzzle4.txt). Hint data in the screens' code is the shape
HintEngine.kt should return: cells 0–80, boxes 0–8 (0-based).
Verified: singles place 21 digits then stick with 34 empty; r1c7 candidates {2,7}, answer 2;
after revealing it, singles finish the puzzle.

Not designed yet: Next hint at level 3, naked-single and last-empty-cell examples,
finished puzzle / mistakes-count states.
support.js is only needed to open these pages in a browser.
