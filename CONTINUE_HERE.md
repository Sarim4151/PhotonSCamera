# Current checkpoint: 1.28.17-dev (176, APK built)

The supplied screenshot showed a washed-out Pro rendering-engine viewfinder and overlapping controls. Preview fallback now decodes display sRGB when Camera2 omits an authoritative contrast curve; engine preview highlights are capped relative to the camera feed. Live Capture Coach and Pose guide occupy the upper preview, away from zoom; Pose guide hides during advanced capture. Normal-ratio video resolution discovery uses the SurfaceTexture camera stream in the GL/MediaCodec recording path instead of the separate MediaRecorder list, exposing 2160p only where stream and selected encoder support it. Optional 2x RAISR finish uses available native memory above system headroom rather than a fixed 512 MiB ceiling; the original remains if real memory is insufficient.

The 176 APK assembled successfully with SDK 36, NDK 29.0.14206865, CMake 3.22.1 and JDK 21. The unit suite ran 619 tests: 616 passed, 0 failed, 3 skipped. APK Signature Scheme v2 verifies with the preserved certificate SHA-256 `7e97d1e40d726a8afd789d0afe4581afe070b2bc90c000004604172f637f1379`. APK SHA-256: `342fa6bb32dc5c073d3042ca9798a5f4a251bbed02ba06e5c4698d8bb604eee1`. Physical camera validation on CMF Phone 1 remains pending.

---

# Current checkpoint: 1.28.15-dev (174)

Video audio cleanup and MotionCam-inspired monitoring are added. See docs/video-174-handoff.md for exact features, validation and remaining true-RAW/DirectLog work. Preserve all earlier source features and app/debug.keystore. No phone validation has been performed.

---

# Current checkpoint: 1.28.14-dev (173)

User authorizes completion without further approval/continuation prompts. Preserve source changes and app/debug.keystore. New work is an extension of the existing camera app, not a reset to upstream. No physical camera/emulator validation has been performed here.

## Added in 173
- Four original rendering engines: Neutral Tone, Soft Portrait, Silver Monochrome, Sony-inspired Natural. Shared preview/RAW/export/HDR shader routing, persisted engine names, English/Chinese labels and preset labels. Sony is an independent qualitative interpretation, not proprietary Sony processing, an exact camera match or a substituted Sony sensor matrix. See docs/original-rendering-engines.md.
- Full-source pre-capture: sequential real camera captures retained in a disk ring (10 seconds / 12 frames / 96 MiB), shutter start/stop and review; private session-scoped cleanup. It is not a zero-latency hardware burst, and actual cadence is displayed.
- Guided Photo Sphere: rotation-vector capture targets, native rear 1x/4:3 admission, calibrated spherical projection, bounded decoder tiles, streamed 2:1 JPEG and GPano metadata preservation. Full coverage required; no feature-bundle adjustment or parallax repair. Optical-centre rotation and static/distant scene required. Camera/size/display changes close the active session safely.
- Motion Priority: supported Camera2 manual-sensor/AE-off devices, shutter/auto-ISO ownership and restoration, manual override, debounced frame-relative motion. No world-speed estimation or guaranteed tracking through occlusion.
- Detail workspace: owned source clone, original-size guarded output, native ROI, luma/chroma denoise, sharpening and before/after inspection.
- Camera workspace templates: Simple, Pro, Street, Portrait, Astro quick-slot layouts, preserving separately saved custom presets.

## Verification and limits
The current build report is docs/build-verification-173.json. Existing tests and shader validation are run before packaging. Added shader validation covers 5 shared variants x 4 new engines. Actual original-operator GLES compile/link/draw/readback on host Mesa llvmpipe passed black, grey, white, overrange, saturated and negative-channel samples. Independent numerical probes checked 22,001 samples per engine with explicit float32 tolerance; reports and reproducible scripts are in docs/verification-development/original-engines. This is not phone driver or photographic quality validation.

Keep the final APK/source hashes and signing certificate in the build report. Native 50 MP remains inaccessible if Camera2 does not expose it; computational enlargement is distinct. Remaining work includes semantic sky/skin classifiers, full tiled source rendering for every operation, and device capture/save/orientation/memory validation. The video phase remains separate.

## Build workflow
Source recovery/build-env/env.sh before Gradle from this session workspace. Global Gradle home, --max-workers=2 and in-process Kotlin; native compile pool 2. Compile a normalized-time copy, since source future mtimes can cause Ninja regeneration loops. Source writes need immediate future mtime to survive this workspace sync; do not normalize authoritative source. Preserve signature and avoid incremental install.

---

## Previous continuation notes

# PhotonSCamera 1.28.13-dev (172): APK BUILT AND TESTS PASS

Read docs/handoff-universal-photo.md and docs/build-verification-172.json. Actual APK +588passing/3skipped tests, signature preserved. Source/build production inputs match. No phone/emulator test. New ActionPan, People/Background masks, horizontal panorama and manual astro foreground preservation included alongside all prior universal-colour, streamed temporal saving, AddMe/queue fixes, BestFace, projects, document OCR/PDF and lite-AI coach/SmartCapture work. Remaining domains explicitly recorded; do not claim every requested feature done. User authorized work without more approval/continuation prompts. Preserve app/debug.keystore. Source future mtimes protect sync; compile normalized /tmp copy. Toolchain env recovery/build-env/env.sh, globalGradlehome, nativepool2.
