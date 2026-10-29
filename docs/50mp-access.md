# Observed A015 capability result — supplied 23 October log

Rear camera0 exposes up to4096x3072 JPEG/YUV (12.6MP) and4000x3000 RAW (12MP). Maximum-resolution pixel-mode request is false; maximum-resolution map has no listed JPEG/YUV/RAW streams. This firmware does not expose native50MP through the inspected public Camera2 path. System-only camera IDs2/3 were denied to the app. Computational upscaling can produce larger output without establishing native50MP readout.

# CMF Phone 1 native 50 MP access

The phone has a marketed 50 MP Sony main camera. This does not establish that its firmware exposes unbinned 50 MP streams to third-party Camera2 clients.

## What this build does

On camera open, the app logs the selected camera characteristics: normal/high-resolution output sizes by format, device model/API level, whether SENSOR_PIXEL_MODE is an available capture request, and maximum-resolution stream-map sizes. Open Settings → Log Viewer and export immediately after opening the rear camera. These are read-only diagnostics; no undocumented vendor switch or fake 50 MP label is enabled.

## What a native path needs

1. A suitable YUV/RAW/JPEG output near 50 million pixels exposed by the device. Check both ordinary/high-resolution sizes and the API 31 maximum-resolution map.
2. If maximum pixel mode is required, matching OutputConfiguration pixel-mode declarations and a still request with SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION. A preview surface available only in default mode must not be targeted by that request.
3. A tested session combination and enough image/memory budget. JPEG-only maximum output cannot be passed into the app's existing YUV/RAW pipeline unchanged.

The current capture size selector uses the ordinary map. Merely finding a maximum-map size is not sufficient to enable it safely. If the firmware exposes only approximately 12 MP to third-party clients, an app cannot guarantee a native 50 MP unlock. Multi-frame super-resolution/upscaling is a different operation and must remain labeled accordingly.

## Primary references

- https://in.nothing.tech/products/cmf-phone-1
- https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap#getHighResolutionOutputSizes(int)
- https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#SENSOR_PIXEL_MODE
- https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION
- https://developer.android.com/reference/android/hardware/camera2/params/OutputConfiguration#addSensorPixelModeUsed(int)
