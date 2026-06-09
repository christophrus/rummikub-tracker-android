"""Convert orientation_cnn.onnx from FP32 to FP16 (half precision)."""
import os
import onnx
from onnxruntime.transformers.float16 import convert_float_to_float16

assets = r"d:\Github\rummikub-tracker-android\app\src\main\assets"
input_model = os.path.join(assets, "orientation_cnn.onnx")
output_model = os.path.join(assets, "orientation_cnn_fp16.onnx")

print(f"Converting {input_model} to FP16 ...")
model = onnx.load(input_model)
fp16_model = convert_float_to_float16(model, keep_io_types=True)
onnx.save(fp16_model, output_model)

orig_mb = os.path.getsize(input_model) / 1e6
quant_mb = os.path.getsize(output_model) / 1e6
print(f"Original (FP32): {orig_mb:.1f} MB")
print(f"FP16:            {quant_mb:.1f} MB")
print(f"Reduction:       {(1 - quant_mb/orig_mb)*100:.0f}%")
print("Done.")
