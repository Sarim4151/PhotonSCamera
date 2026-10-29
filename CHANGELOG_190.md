# Build 190 — device-log-guided recording and preview fixes

The CMF Phone 1 log shows sensor orientation 90, an OES transform that exchanges
texture axes, encoder 3840x2160 and MP4 orientation hint 0. Applying this portrait
presentation transform directly to a landscape raster scales the two dimensions
by 1.7778 and 0.5625. The recorder now removes sensor presentation rotation while
preserving crop/texel inset and the OES origin flip; both normal and enhanced YUV
paths encode in sensor-buffer axes. Sensor/device/facing/calibration rotation is
written to the MP4 track using the previously unused sensor-aware helper. Existing
geometry verification now covers the exact logged matrix and four sensor angles.

SurfaceTexture frame callbacks now use a dedicated HandlerThread instead of the
implicit main-looper fallback. GL work stays on the existing recording dispatcher.
Callback dispatch and source timestamp gap diagnostics distinguish frame starvation
from slow rendering. This log shows startup sensor FPS 21.9, then 29.9, one 24.5 FPS
incoming window and zero pending replacements; sustained encoder overload is not
established. Hardware startup and thermal behavior still need phone testing.

The shutter gesture is cancelled on a capture-mode change, including when photo
long press is disabled. Capture callbacks carry their expected mode and source;
stale callbacks are rejected and hardware keys are logged. The old log cannot prove
which input caused the reported automatic start; the patch closes the demonstrated
stale-gesture path and makes future input attribution possible.

Engine preview previously never set TONEMAP_MODE or TONEMAP_CURVE, but treated the
vendor ISP output as invertible scene light. It now requests a capability-checked
sRGB contrast curve for engine preview and restores the original builder settings
when disabled. Only authoritative contrast-curve results are inverted; unsupported
fallback is explicitly logged. No brightness cap or exposure offset was added.
The curve uses up to 64 output-uniform knots to retain 8-bit shadow precision.

The previous 22-LUT pack and all other features are retained. The debug workflow
uses the existing branch push event without a duplicate pull-request build.

Compilation and full unit results are recorded after CI. No physical-device claim
is made for final orientation, recording frame pacing or preview/photo matching.
