"""Train a small CNN on digits.npz and export model.tflite for Android.
Output classes 0-8 = digits 1-9."""
import numpy as np
import tensorflow as tf

data = np.load("digits.npz")
X = data["X"].astype("float32")[..., None] / 255.0
y = data["y"]
idx = np.random.permutation(len(X))
X, y = X[idx], y[idx]
split = int(0.9 * len(X))

model = tf.keras.Sequential([
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
model.compile("adam", "sparse_categorical_crossentropy", metrics=["accuracy"])
model.fit(X[:split], y[:split], validation_data=(X[split:], y[split:]),
          epochs=10, batch_size=128)
model.save("digits.keras")

conv = tf.lite.TFLiteConverter.from_keras_model(model)
conv.optimizations = [tf.lite.Optimize.DEFAULT]  # quantize -> smaller, faster
open("model.tflite", "wb").write(conv.convert())
print("exported model.tflite")
