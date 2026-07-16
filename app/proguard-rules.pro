# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class org.lorus.rummiq.data.local.entity.** { *; }

# ONNX Runtime uses JNI and reflection; stripping/renaming its classes breaks
# native binding in minified release builds.
-keep class ai.onnxruntime.** { *; }
