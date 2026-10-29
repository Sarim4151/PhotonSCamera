# Advanced camera and gallery integration validation

> Build update —21 October2026: Android/Kotlin/native APK build and signature checks now pass. Unit suite:554 passed,14 failed,6 skipped. Earlier environment-blocked notes below describe the checks available at implementation time; the current authoritative result is in build-verification.md. Physical-device acceptance is still outstanding.


## Implemented source paths

- Camera → Sequence → Advanced capture connects pre-capture, preview Live Composite/Live ND, and source-backed night/astro/star-trail/HDR sessions. Sessions are mutually exclusive with interval/focus capture and multiple exposure. The screen stays awake during sequences.
- Preview modes request original unfiltered frames at a maximum long edge of1536, rotate to capture orientation, and preserve source timestamps. Pre-capture selects a callback received strictly before shutter from at most8 frames within2 seconds. Live modes accumulate linear-sRGB max or running average and publish an independent preview about once per4 samples. Sampling is best-effort, not a sensor burst guarantee; sessions stop at5 minutes.
- GL callback delivery uses synchronized ownership transfer and a closed-session flag. Worker-created bitmaps are assigned into owned holders before dispatcher return. Buffer ownership cleanup encloses cancellable mutex acquisition. UI-published preview lifetimes use GC to avoid recycling active render nodes.
- Source-backed sessions wait for actual saved JPEG IDs with backpressure. HDR requires unlocked automatic ISO/shutter; controller serializes compensation steps around baseline, waits for two newer readbacks with target compensation and AE convergence, and restores compensation in NonCancellable finally. User capture-setting changes join cleanup before applying.
- Independent JPEG merge runs after focus-bracket lens restoration. Every accepted source is retained. New images carry source IDs and accepted/rejected frame information. Failed publication removes prepared placeholders.
- Advanced processing applies sRGB decode/encode, bounded stripe fusion, conservative registration, exposure normalization and moving-region rejection. Astro permits modest rotation/scale/translation and matches sparse star exposure above background; >=2 reliable frames are required. Star trails intentionally retain tripod coordinates. Reference-only fallback within a merged image is allowed; an entirely failed stack is not reported as successful.
- Conditional super resolution uses full-working-resolution .125-pixel shift refinement and drizzle. It requires four independent phase groups covering both axes, reliable registration and98% interior sample coverage. Inputs over1024 pixels are reduced before2× reconstruction; output remains≤2048. It is experimental and is not a claim of extra detail on arbitrary photographs.
- Gallery three-look selection uses real built-in/saved presets independently, including creative LUT, recipe and effects. Camera RAW engine/aspect/exposure capture settings are not re-captured by a gallery edit.
- Gallery two-photo editor offers one manual rect/ellipse region, donor translation, baseline swap, feather, draggable preview and undo. It creates an independent JPEG. Donor scales to baseline dimensions; no automatic identity/pose matching is claimed.
- Bundled ML Kit face classification reports nullable eye/smile estimates, normalized boxes and photographic/expression score. User chooses a frame. Reports are tied to exact source selection; in-flight tasks drain before input bitmaps are freed on cancellation.
- Settings → Content management → Measured colour calibration uses user-matched sRGB patches, a conditioned affine linear fit, leave-one-out prediction error and33³ encoded-sRGB cube export. Degenerate patches and high measured residuals reject. It cannot establish exact manufacturer equivalence.

## Checks actually available

- `git diff --check`: passed.
- Android values XML parse and new component resource-reference checks: passed.
- `python3 scripts/validation/sequence_sr_qa.py`: synthetic noisy/quantized half-pixel phase registration and reconstruction passed; identical burst rejected. Drizzle MSE3.18e-6 versus bicubic1.22e-5 on that synthetic scene only. This Python numerical probe is not execution of Android Kotlin.
- Numerical review probes: sRGB256-level round trips, exposure normalization, HDR two-frame moving samples, unchanged gray midtones, sparse-star gain matching, known affine calibration/leave-one-out predictions and rank/residual rejection. These are math checks, not device acceptance.
- Desktop Mesa GLES shader/pixel probes: production overlay shaders, threshold/alpha/peaking/magnifier checks passed; this does not validate Android drivers.
- `bash ./gradlew compileDefaultDebugKotlin`: blocked before compilation downloading Gradle8.13 (`java.net.SocketException: Network is unreachable`). No successful Kotlin/native build or phone run is claimed.

## Required local acceptance

Build `assembleDefaultDebug` with SDK36/JDK17/NDK29.0.14206865/CMake3.22.1; install normally. Test cold start, lifecycle stop/resume, sensor capability rejection, shutter during cleanup, cancellation during GL callback/mutex wait/save/ML Kit processing, lens/rotation/preset changes, low-memory operation, unknown colour spaces, long exposure timeouts and saved-output consistency.

Use a tripod focus target, sparse stars, moving subject HDR, deliberately incompatible sequences and shifted fine-detail chart. Inspect ghosting/foreground motion, calibration neutral/skin/highlights on held-out scenes, frame rejection metadata, preserved original JPEG/DNG checksums and actual image dimensions. The new algorithms remain experimental until those physical-device checks pass.
