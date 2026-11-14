"""Train the printed-vs-handwritten classifier and export hand.tflite.

Input: the same 28x28 cleaned cell the digit model reads. Output: P(handwritten).
Printed cells come from digits.npz (make_digits.py, system fonts), handwritten ones
from hand.npz (make_handwritten.py, MNIST redrawn as cell entries).
A separate model on purpose: the digit model, its parity tests and model.tflite stay
untouched.

Usage: python train_hand.py   (after make_digits.py and make_handwritten.py)"""
import numpy as np
import tensorflow as tf


def load():
    printed, hand = np.load("digits.npz"), np.load("hand.npz")
    X = np.concatenate([printed["X"], hand["X"]]).astype("float32")[..., None] / 255.0
    y = np.concatenate([np.zeros(len(printed["X"])), np.ones(len(hand["X"]))]).astype("float32")
    digit = np.concatenate([printed["y"], hand["y"]])
    return X, y, digit


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
        tf.keras.layers.Dense(1, activation="sigmoid"),
    ])


def main():
    np.random.seed(0)
    tf.random.set_seed(0)
    X, y, digit = load()
    idx = np.random.permutation(len(X))
    X, y, digit = X[idx], y[idx], digit[idx]
    split = int(0.9 * len(X))
    model = build()
    model.compile("adam", "binary_crossentropy", metrics=["accuracy"])
    model.fit(X[:split], y[:split], validation_data=(X[split:], y[split:]), epochs=10, batch_size=128, verbose=2)
    model.save("hand.keras")

    p = model.predict(X[split:], verbose=0)[:, 0]
    yv, dv = y[split:], digit[split:]
    print(f"validation: printed called handwritten {np.mean(p[yv == 0] >= 0.5):.2%}, "
          f"handwritten called printed {np.mean(p[yv == 1] < 0.5):.2%}")
    for d in range(9):
        m = dv == d
        print(f"  digit {d + 1}: printed wrong {np.mean(p[m & (yv == 0)] >= 0.5):.2%}, "
              f"handwritten wrong {np.mean(p[m & (yv == 1)] < 0.5):.2%}")

    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    open("hand.tflite", "wb").write(conv.convert())
    print("exported hand.tflite")


if __name__ == "__main__":
    main()
