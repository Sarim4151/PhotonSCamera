# Latest update: full-resolution Add Me — 1.28.7 (166)

See [docs/add-me-full-resolution.md](docs/add-me-full-resolution.md). Review stays bounded to 2048 px; full-quality Save stages original-resolution sources individually and blends lossless rows into a full-size output. It never upscales the review image. Phone full-resolution acceptance is still pending.

# Latest update: Add Me repair and capture-tools UI

See [docs/add-me-repair.md](docs/add-me-repair.md) for the reported failure, source fixes, explicit 2048 px/full-source output choice, and remaining phone acceptance. App version 1.28.6 (165). The original camera sources remain preserved.

# Modified PhotonSCamera project — 22 October 2026

The Android debug APK builds with Kotlin/Java and ARM64 native code. The final unit suite contains580 tests:573 passed,0 failed,7 skipped. APK Signature Scheme v2 verification passed. Hardware testing on the CMF Phone1 remains pending.

## Use the new capture workflow

1. Tap **Sequence → Advanced capture** in the camera top bar.
2. Select a mode, configure its options, then press **Use mode** to return to the viewfinder.
3. Press the main shutter to start; press it again to stop. Stacking modes finish already captured frames and report insufficient frames clearly. The viewfinder shows mode/progress, accumulation preview and saved/error status.
4. Choose **Normal photo** on the viewfinder to disarm the advanced mode.

**High Detail** captures several original photos, aligns independent subpixel samples and reconstructs twice the explicitly selected working dimensions. Select1280/2048/3072 or full-source input. A stationary scene and adequate independent samples are required. Motion is rejected or uses the reference image; unsuitable bursts are rejected. Originals remain saved. Memory checks precede full-size allocations.

Full-source **Live Composite/Live ND** use linear integration with disk checkpoints and recovery. The separately named preview modes and bounded pre-capture buffer store rendered previews. HDR brackets, source bursts, night/astro/trails, interval capture, focus bracketing and retained-source multiple exposure are available alongside them.

## Other additions and fixes

- Focus peaking now respects its toggle during autofocus and Snap focus, with corrected sensitivity and preview-only overlays. Set colour/sensitivity in Settings or add Peaking to the quick controls.
- Continuous decorative shutter pulse/spin, thumbnail shimmer/completion flash and viewfinder-size animation removed; actual state/progress remains visible.
- **Sequence → Photographic style**: skin-aware tone/warmth/colour, detail strength/fineness/threshold and sensor WB intent. Additional original Leica Vivid/Cinematic profiles complement existing adjustable inspired styles.
- **Sequence → Guided group photo**: two full captures, alignment ghost, bundled person segmentation, background checks, mask refinement and explicit review. Gallery compositing includes deliberate detected-face region selection.
- Top-bar more menu configures ordered quick controls and saved layouts. AE and AF locks preserve independent metering placement.
- Preset management includes search/favourites/rename/duplicate/import/export, selected-reference thumbnails, before/after and intensity. Recipe metadata and original RAW redevelopment links are preserved.
- CUBE/PLUT import validates structure, finite values and supported ranges, preserves exact non-unit domains through PLUT v5, and reports unsupported inputs instead of silently clipping them.
- Full-quality gallery processing is explicit and memory checked; source recipes are rendered once before live/group integration, avoiding dropped colour styles or double application.
- Motion-photo headers are scanned without loading appended video, container offsets/padding are validated, failed extracts are cleaned up, and DNG capture dates respect timestamp clock domains.

## Compile in Android Studio

Extract the complete project, open **PhotonSCamera-main**, and let Gradle sync. The app targets Java17 bytecode; the Gradle daemon provisions Amazon Corretto21. Required tools are Android SDK36, NDK29.0.14206865, CMake3.22.1 and Gradle8.13. Use your own local.properties SDK path.

Run `gradlew.bat assembleDefaultDebug` on Windows or `bash ./gradlew assembleDefaultDebug` on Linux/macOS. APK output is app/build/outputs/apk/default/debug/app-default-debug.apk. The repository debug key is retained; release keys and machine-specific configuration are excluded.

See docs/feature-programme-status.md for feature locations/limits, docs/build-verification.md for evidence and docs/unit-test-summary.json for the final result. The source includes reproducible shader and actual-Kotlin stacking probes under docs/validation and scripts/validation/sr-jvm.

## Acceptance limits

Builds and synthetic tests cannot establish real sensor colour accuracy, focus calibration, thermal behaviour or production camera reliability. Exact proprietary brand processing, sensor RAW pre-buffering, optical ND and arbitrary perspective/identity reconstruction are not claimed. Seven tests are explicitly skipped, including the Canon DPP comparison whose authoritative fixtures are absent. Full-source2× output may exceed available memory; explicit working sizes exist and source photos remain intact. Existing non-High-Detail camera merges can still use bounded2048 processing; gallery full-quality choices and source preservation are explicit.
