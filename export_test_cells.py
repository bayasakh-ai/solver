"""Export sample digit cells + the TFLite model's predictions, so the Android
app can check it gives the same answers (DigitClassifierTest).
Writes test_cells.bin (N x 784 bytes) and test_expected.txt (N lines x 9 probs).
Copy both into android/app/src/androidTest/assets/."""
import random

import numpy as np
import tensorflow as tf

from make_digits import load_fonts, render

random.seed(42)
np.random.seed(42)
fonts = load_fonts()
cells = []
while len(cells) < 45:  # 5 samples of each digit
    d = len(cells) % 9 + 1
    img = render(d, random.choice(fonts))
    if img is not None:
        cells.append(img)
X = np.array(cells, np.uint8)

interp = tf.lite.Interpreter(model_path="model.tflite")
inp = interp.get_input_details()[0]["index"]
interp.resize_tensor_input(inp, [len(X), 28, 28, 1])
interp.allocate_tensors()
interp.set_tensor(inp, X[..., None].astype(np.float32) / 255.0)
interp.invoke()
probs = interp.get_tensor(interp.get_output_details()[0]["index"])

X.tofile("test_cells.bin")
np.savetxt("test_expected.txt", probs, fmt="%.6f")
acc = (probs.argmax(1) + 1 == np.arange(len(X)) % 9 + 1).mean()
print(f"exported {len(X)} cells (TFLite accuracy on them: {acc:.0%})")
