# Image and video quality status (build 179)

This is an implementation status, not a device-performance guarantee. The CMF Phone 1 still needs paired preview/capture and video test clips.

| Requested feature | Current status | Next verification or implementation |
| --- | --- | --- |
| Preview-to-photo color matching by engine/lens/light | Not calibrated | Capture paired viewfinder screenshots and output RAW/JPEG with fixed exposure and white balance; compare linearized brightness, white point and tone curves. Then fit per-engine preview transform and add a before/after diagnostic. |
| Motion-aware photo merging | Existing Spatial RGB RAW fusion and sharp reference selection; HDR+ inspired preset uses them | Test moving subject and handheld burst on device; inspect ghosting and texture. |
| Adaptive low-light shutter/frame decisions | Motion-priority exposure controls exist, but no validated joint frame-count policy | Measure gyro/scene motion and capture latency; cap exposure and frame count in moving scenes. |
| Stable AWB and skin tones | Video AWB lock exposed in build 179; no mixed-light skin calibration | Measure preview/capture color matrices across illuminants and skin patches; validate face-preserving color behavior without false color. |
| Detail-aware denoising | Existing RAW noise/sharpening controls and merge denoise; no verified semantic hair/face/fabric model | Benchmark edge retention and smooth-area noise on real RAW samples. |
| Video temporal denoising | Not implemented | Motion-compensated frame history, GPU memory budget, intensity UI and ghosting tests. |
| Smooth video AE/AWB transitions | AE/AWB locks exposed; smoothing policy not implemented | Check camera request/result cadence and device ISP behavior on brightness/color changes. |
| Capability-checked 10-bit HLG/HDR10 | Not implemented | Query Camera2 dynamic range and encoder capabilities together, build compatible capture/encode/preview path and test metadata/playback. |
| Consistent lens switching/zoom | Existing zoom controls, no calibrated inter-lens matching | Capture lens-paired charts/scene samples and measure exposure/white balance discontinuities. |
| Live capture-quality indicator | Not implemented | Use measured blur/motion and histogram clipping, with a compact UI and lens-specific capability explanations. |
| Spektrafilm / issue #88 | Native stock LUT/DIR stages and editor film-process appearance controls exist | Physically adjustable couplers, spectral push/pull and ISO/format dependent grain remain outstanding; see issue-88-film-process.md. |

The HDR+ inspired preset is original work using this app's RAW merger. It is not Google's GCam binary or an exact implementation of its algorithms. Engine color development runs after RAW fusion, including Spektrafilm when selected. Full multi-frame results depend on camera RAW burst capabilities.
