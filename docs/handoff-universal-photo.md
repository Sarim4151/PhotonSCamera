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

## Historical build 172 details

# PhotonSCamera universal-photo checkpoint — 2026-10-25

Source version1.28.13-dev(172). Android APK compilation and existing tests now pass; signing matches the prior build. Physical device validation remains required. Preserve app/debug.keystore and all user changes. User authorized work without further approval/continuation prompts. Photo work precedes video work.

## Implemented in the current source

| Area | Behavior and location | Validation limit |
|---|---|---|
| Universal colour | Shared colour panel -> Neutral, Portrait, Landscape recipe-only styles; preserves device calibration, DCP selection, WB/exposure, engines and grading | Creative display-referred starting points; no certified manufacturer match |
| Sensor source resolution | Largest standard regular/high-resolution stream for actual format, RAW uses real RAW dimensions; rejected high-only session retries regular sizes; advanced viewfinder shows actual source | Firmware must expose stream; no native50MP unlock; aspect crops intentional |
| Native burst merge | HighDetail full-source default/migration, disk-backed rendered sources and bounded stripes for source modes, independent Java/native memory budgets | Single-source rendering still needs full bitmap(s); extreme sizes can safely reject |
| HighDetail quality | Independent phase/coverage tests required for genuine2x; honest aligned-native or reference fallback; intermediate sources private | No invented detail or silent downsize |
| Live Composite/ND saving | Full-size checkpoint scanlines -> baseline sRGB4:4:4 JPEG -> encoded-file gallery publication and byte-copy autoexport; bounded thumbnails | Final full-size Bitmap/compressed buffer removed; source-render floor remains |
| Processing reliability | Per-shot OOM no longer kills the only queue consumer; each image release/counter handled; valid earlier frames can finalize | Severe process kill still possible; phone failures not proven resolved |
| Add Me | Full-source default; native/Java preflight corrected; final prepare/save/verify/scoped-cleanup transaction; accepted capture waits actual pipeline completion; explicit camera->queue handoff marker | Alignment/segmentation remain limited by parallax/occlusion; source first/second hidden/private |
| Best Face | Gallery select2–6 images -> Frame tools -> Best Face. Manual per-person choice from real source photos, measured face geometry, background/pose/lighting ambiguity guards, feathered patch, original-size default | Up to6 detectable people; rendered equal-size sources, small pose/motion; no identity recognition/generation |
| Best Moments | Live Photo More -> frame suggestions using detail/clipping/frame-change score, manual time override, save-copy | Video-frame resolution; not full-sensor pre-shutter ring |
| Expression | Gallery More -> expression. ARGB8888 inputs, rotated/multiscale retries, measured MLKit mouth-contour fallback, owned hardware readback, subtle smile/relax warp | No generative teeth/closed-mouth synthesis; visible sufficient mouth detail required |
| Live shooting coach | Viewfinder enable toggle: model target/bounds framing, ISO/shutter/WB/focus suggestions, fresh histogram, per-mode advice | Advisory rules; not learned aesthetics or perfect composition |
| Lightweight AI | Bundled EfficientDet-Lite0INT8 ~4.6MB for subjects, PoseLite ~5.8MB for33 joints, FaceLandmarker ~3.8MB for faces. Existing object inference<=4Hz now also feeds SmartCapture | CPU/background bounded processing; no cloud image upload or chat model |
| AI movement/status | Fresh model-tracked motion relative to frame selects short motion-aware Smart stack; faster-shutter coach hint. Missing-model/allocation/linkage failures surface and retry on coach toggle | Does not distinguish camera movement from subject movement or infer world speed |
| Tracking | Spatial overlap/centre association, lost-target suppression and reacquisition; auto supports model classes, not arbitrary objects | Cannot guarantee identity through crossings/occlusion |
| Selective editor | More -> Selective edit: up to12 brush/linear/radial layers, enable/add/remove, grouped undo/redo, atomic versioned source-associated project, autosave/ON_STOP flush; source-size save-copy | Manual masks; not AI sky/skin segmentation; original is required to fully resume project |
| Document tool | More -> Document scan: draggable convex four-corner perspective, colour/B&W, bundled Latin/Hindi OCR, text copy, image+selectable-text PDF, original-size copy | Manual corners; PDF OCR text on following pages, not invisible overlay; failed new PDF cleaned up |
| Grading/UI | Existing LGGO wheels, RGB curves/LCH, waveform/vectorscope, DCPs, compact dark/gold camera controls, timer amount, searchable settings retained | No full ACES/Log colour management; not every app screen redesigned |

## Important code fixes from review
- BestFace bilinear sampling uses pixel-centre convention (q-.5) so identity mapping no longer blurs four adjacent pixels.
- Stable dialog cleanup closes late-assigned sessions/owned previews even if dismissal precedes recomposition; render leases retain active readers.
- Stream publication commits JPEG+metadata before optional thumbnail/export work. Export/notification failure must not roll back a valid photo; export URI receipt retained if bookkeeping fails. Viewfinder distinguishes private-save/export failure.
- Normal, shutter-stop and recovery temporal saves pass autoexport preferences correctly; out-of-scope variable reference corrected.
- AddMe missing-terminal monitor checks camera-thread handoff marker, avoiding the pending-remove/enqueue gap.
- Scene tracking mutable state changes run onMain; inference callbacks update thread-safe flows or dispatch toMain.

## Verification actually performed
Resource XML parse/duplicate/default-string references; model file magic/container/hash checks; independent static integration/ownership review; binary checkpoint sampling and size/truncation layout probes; BestFace identity-sampling probes; motion policy boundaries; memory budget scenarios. No new test suite added perAGENTS.

Actual streaming JNI encoder host execution passed via OpenJDK17 and installed matching libjpeg-turbo2.1.5 headers/library: dimensions1x1/17x19/129x73/65500x1; RGB within3; ordered reusable scanlines;100 callback exceptions preserved with stable FD count; disk-full/invalid inputs rejected;48MP4000x12000 encoded with-Xmx16m and verified baseline4:4:4/EOI. Original /tmp probes were pruned; reconstructed harness/commands and explicit executed-result report are in docs/verification-development/streaming-jpeg. Reconstructed files were not rerun after pruning.

Android toolchain restored on2026-10-25: SDK36, NDK29.0.14206865, CMake3.22.1, Corretto21. Built from normalized-mtime /tmp/photon-build-172 copy. First live-source attempt hit timestamp-related Ninja regeneration and resources added during compilation; final frozen copy builds. Shader-validator test failures were caused by an incomplete duplicate NDK directory with extracted symlinks as text; official links restored and incomplete duplicate moved outside SDK discovery. Final assembleDefaultDebug + testDefaultDebugUnitTest is BUILD SUCCESSFUL,588passed/3skipped/0failed. Signature certificate matches prior APK. See build-verification-172.json and docs/verification-development/build-172-final.log. All2762 production/build input hashes match source and build copy. No physical phone/emulator validation claimed.

## Historical checkpoint 172 gaps (superseded above)
Semantic skin/sky masks (People/Background now implemented); automatic moving-subject shutter application (coach suggestions exist); true full-quality sensor pre-shutter ring (preview buffer exists); true360 PhotoSphere (horizontal translation panorama now implemented); dedicated detail/denoise workspace (existing zoomable editor controls available); named custom camera workspace layouts (existing configurable controls/presets available); full tile-based source rendering for every engine/operation. Existing focus bracket/fusion, preview pre-capture, astro alignment, LUT/grade controls and preset systems are related but do not complete these domains. Do not claim all requested features are finished.

## Resume workflow
Build assembleDefaultDebug and existing testDefaultDebugUnitTest when toolchain access returns. Use global Gradle home, bounded workers/native pools; build a normalized-mtime /tmp snapshot, not future-dated source. Source sync may restore older content unless edits immediately set mtime now+172800 in same tool call; independently verify after another call. Never replace working user source with upstream. Preserve signature. Validate device capture/save/rotation/UI/models, then remaining feature domains.


## Additional features in this built APK
- Gallery More -> Action Pan: real bundled person segmentation, adjustable directional background blur, protected person boundary, original-size or explicit2048 output. Person subjects only; uncertain masks reject.
- Selective edit -> People/Background AI masks: normalized128px confidence grids persist in schema2 project JSON with schema1 compatibility; up to12 layers within1MiB. Native/Java budget fixes and stable owned-preview cleanup. No sky/skin semantic classifier claim.
- Gallery select2–8 overlapping images -> Frame tools -> Panorama: measured multiscale NCC translation, exposure balancing, feathered full-source canvas, guarded output and save-copy. Ordered same-size distant/static horizontal scenes; rotation, parallax, homography and360sphere unsupported.
- Advanced Capture -> Astro -> Preserve reference foreground: manual horizon slider and yellow viewfinder line; measured sky peaks drive registration/exposure above line, first-frame ground below with feather above boundary. Reliable stars/steady tripod required; insufficient correspondence rejects safely.
- Semantic model inputs always bounded readable sRGB ARGB pixels, including F16/hardware-derived previews.
