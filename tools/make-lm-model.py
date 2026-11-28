#!/usr/bin/env python3
"""Makes the language model for TsBoard "Smart next-word prediction".

Downloads a small GPT-2 style model, converts it to ONNX, shrinks it (int8) and writes
lm-model/model.onnx and lm-model/tokenizer.json. Import both files in
Settings > Text correction > Language model.

  pip install "optimum[onnxruntime]" transformers torch
  python3 tools/make-lm-model.py            # distilgpt2 (default)
  python3 tools/make-lm-model.py gpt2       # bigger and better, slower
"""
import os
import shutil
import subprocess
import sys

model = sys.argv[1] if len(sys.argv) > 1 else "distilgpt2"
out = "lm-model"
raw = os.path.join(out, "raw")
shutil.rmtree(out, ignore_errors=True)
os.makedirs(raw)

subprocess.check_call(["optimum-cli", "export", "onnx", "--model", model, "--task", "text-generation", raw])

from onnxruntime.quantization import QuantType, quantize_dynamic

src = os.path.join(raw, "model.onnx")
dst = os.path.join(out, "model.onnx")
try:
    quantize_dynamic(src, dst, weight_type=QuantType.QInt8)
except Exception as e:  # fall back to the unquantized model
    print("quantization failed, using the full size model:", e)
    shutil.copy(src, dst)

tok = os.path.join(raw, "tokenizer.json")
if not os.path.isfile(tok):
    sys.exit("tokenizer.json was not written, cannot continue")
shutil.copy(tok, os.path.join(out, "tokenizer.json"))
shutil.rmtree(raw)

for name in ("model.onnx", "tokenizer.json"):
    print(f"{name}: {os.path.getsize(os.path.join(out, name)) / 1024 / 1024:.1f} MB")
print("done: import both files from", os.path.abspath(out))
