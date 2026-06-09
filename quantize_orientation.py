"""Quantize orientation_cnn.onnx from FP32 to INT8 (dynamic quantization)."""
import os
import onnx
from onnxruntime.quantization import quantize_dynamic, QuantType

assets = r"d:\Github\rummikub-tracker-android\app\src\main\assets"
input_model = os.path.join(assets, "orientation_cnn.onnx")
slim_model = os.path.join(assets, "orientation_cnn_slim.onnx")
output_model = os.path.join(assets, "orientation_cnn_q8.onnx")

# Step 1: Slim the model (fixes shape inference issues)
print("Slimming model...")
import onnxslim
model = onnx.load(input_model)
slimmed = onnxslim.slim(model)
onnx.save(slimmed, slim_model)
print(f"Slimmed saved to {slim_model}")

# Step 2: Quantize
print("Quantizing...")
quantize_dynamic(
    model_input=slim_model,
    model_output=output_model,
    weight_type=QuantType.QInt8,
)

orig_mb = os.path.getsize(input_model) / 1e6
quant_mb = os.path.getsize(output_model) / 1e6
print(f"\nOriginal:  {orig_mb:.1f} MB")
print(f"Quantized: {quant_mb:.1f} MB")
print(f"Reduction: {(1 - quant_mb/orig_mb)*100:.0f}%")

# Clean up intermediate file
os.remove(slim_model)
print("Done.")
