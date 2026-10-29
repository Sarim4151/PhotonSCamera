# Photon Camera 1.28.18-dev (177)

- UHD video uses a landscape encoder canvas (3840×2160 at 16:9; 3840×1632 at 21:9), with a rotated GL render and container orientation metadata. Camera input and encoder capability checks still guard unavailable resolutions; no camera stream is invented.
- Video capability discovery checks each installed surface encoder for the requested size and frame rate, prefers hardware acceleration, and recording opens that same encoder.
- Video Monitor offers an opt-in Rendering engine control and the existing engine settings sheet. The engine LUT is frozen at recording start and baked into SDR footage. Log-profile video bypasses the engine look.
- Light Graffiti is a separate advanced capture mode backed by the full-source light-composite path with duration and new-light intensity controls. It is not Huawei's proprietary implementation.
- Gallery photo actions include Paint on photo: six brush colors, size control, stroke layers, undo, and a new JPEG copy. The canvas is capped at 2048 px for bounded memory. This is an independent GoPaint-inspired editing tool, not Huawei GoPaint.

Build: `assembleDefaultDebug testDefaultDebugUnitTest` on JDK 21, Android SDK 36, NDK 29.0.14206865, CMake 3.22.1. Camera and encoder behavior requires testing on the target phone.
