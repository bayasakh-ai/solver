"""Train the reader for HANDWRITTEN digits and export hand_digits.tflite.

Which digit did the user write? Only cells hand.py calls handwritten are read with this
model; printed digits keep model.tflite, which stays untouched with its parity tests.
Same 28x28 cleaned cell, same small CNN, classes 0-8 = digits 1-9. Trained on hand.npz
(make_handwritten.py: MNIST redrawn as cell entries) plus digits.npz (printed fonts).

Measured 2026-11-14 on 260 real handwritten digits at 640 px (205 from the owner's book
in ballpoint and pencil, 55 red-pen digits of puzzle1 read without colour removal):
  the print model (digits.keras)           217 (83%): book 181/205, puzzle1 36/55
  hand.npz only, 3,000 per digit           217, 220 (two seeds)
  hand.npz + digits.npz                    237-246 (three seeds, 91-95%)
  the same with 9,000 handwritten / digit  228, 238: no better, so 3,000 it stays
  averaged with the print model            239-243: no better
The commonest misread is a 1 written with a flag read as 7 (17 of 58 misreads over
three seeds), then 9 as 3 and 6 as 5. One book writer and one newspaper writer: the
model was compared on these cells, not tuned on them, but it is not a held-out number.

Usage: python train_hand_digits.py   (after make_digits.py and make_handwritten.py)"""
import numpy as np
import tensorflow as tf


def build():
    return tf.keras.Sequential([
        tf.keras.layers.Input((28, 28, 1)),
        tf.keras.layers.Conv2D(32, 3, activation="relu"),
        tf.keras.layers.MaxPooling2D(),
        tf.keras.layers.Conv2D(64, 3, activation="relu"),
        tf.keras.layers.MaxPooling2D(),
        tf.keras.layers.Flatten(),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(128, activation="relu"),
        tf.keras.layers.Dense(9, activation="softmax"),
    ])


def main():
    np.random.seed(0)
    tf.random.set_seed(0)
    printed, hand = np.load("digits.npz"), np.load("hand.npz")
    X = np.concatenate([hand["X"], printed["X"]]).astype("float32")[..., None] / 255.0
    y = np.concatenate([hand["y"], printed["y"]])
    written = np.concatenate([np.ones(len(hand["X"]), bool), np.zeros(len(printed["X"]), bool)])
    idx = np.random.permutation(len(X))
    X, y, written = X[idx], y[idx], written[idx]
    split = int(0.9 * len(X))
    model = build()
    model.compile("adam", "sparse_categorical_crossentropy", metrics=["accuracy"])
    model.fit(X[:split], y[:split], validation_data=(X[split:], y[split:]), epochs=10, batch_size=128, verbose=2)
    model.save("hand_digits.keras")

    ok = model.predict(X[split:], verbose=0).argmax(1) == y[split:]
    w = written[split:]
    print(f"validation: handwritten {ok[w].mean():.2%}, printed {ok[~w].mean():.2%}")

    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    open("hand_digits.tflite", "wb").write(conv.convert())
    print("exported hand_digits.tflite")


if __name__ == "__main__":
    main()
