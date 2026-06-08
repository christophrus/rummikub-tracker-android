Place the rummikub_yolo.onnx model file here.

Export it with:
  from ultralytics import YOLO
  model = YOLO("backend/models/rummikub_yolo.pt")
  model.export(format="onnx", imgsz=640, simplify=True)
