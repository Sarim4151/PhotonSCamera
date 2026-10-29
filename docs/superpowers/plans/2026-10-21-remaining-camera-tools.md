# Remaining camera tools implementation plan

> **For agentic workers:** Use superpowers:executing-plans for root integration and dispatching-parallel-agents for independent components.

**Goal:** Connect temporal capture, source-backed stacking, user-chosen recipes, face-aware ranking/composites and measured colour calibration to the existing Android app.

**Architecture:** Keep bounded processing in focused helpers; camera/gallery view models own job lifetimes and publish verified independent gallery images. UI dialogs select parameters and show actual output/progress.

**Tech Stack:** Kotlin, Camera2, Compose, Android Bitmap, coroutines, bundled ML Kit face classification.

**Spec:** docs/superpowers/specs/2026-10-21-remaining-camera-tools.md

## Global constraints

- minSdk30, no source photo destruction, no release signing key changes, localized resources.
- Standalone processed gallery outputs bounded to 2048 pixels; temporal preview outputs explicitly labelled and bounded to1536.
- No unsupported exact-brand/AI identity/super-resolution quality claims.
- Respect standing user authorization; do not introduce new approval gates.

## Review focus

- Cancellation while camera callback/save is pending: late bitmaps recycled, accepted source jobs finish, no orphan empty outputs.
- Mode/lens/orientation changes: buffered frames invalidated and old jobs cannot mix sessions.
- Untextured/moving/nonmatching sequences: reject or fallback to reference, never silently report successful alignment.
- Missing face probabilities: neutral expression score and visible unsupported classification.
- Degenerate/unrelated calibration patches: conditioning/residual rejection and explicit domain.

## Tasks

- [x] Add advanced sequence processing with fractional translation/scale/astro rotation, robust moving-region fallback and tripod trails. Inspect math and fresh source checks.
- [x] Add bundled on-device face classification with safe Task cancellation and explicit nullable probabilities. Integrate expression ranking and guided region editor.
- [x] Add custom three-preset chooser and full independent preset metadata rendering, preserved source IDs and verified publication.
- [x] Add paired patch colour calibration, hold-out error, independent preview and CreateDocument33-cube export. Wire settings entry.
- [x] Connect focus bracket saved IDs to auto merge after lens restoration; show merge stage, result/failure.
- [x] Add bounded preview temporal accumulator/ring with exclusive session state, foreground lifecycle cleanup, live dialog preview and save/stop.
- [x] Add night/astro/HDR/trail source capture with backpressure, timeouts, saved-ID correlation, cancel and optional merge preview; retain originals.
- [x] Review integration, parse resources, run available math/shader checks and attempt real Gradle compilation. Update feature status honestly and package/verify/persist whole source ZIP.

Verification note: the Gradle attempt was made and blocked before compilation; completing the packaging step does not mean Android build/device acceptance passed.

Subsequent verification: environment proxy/certificate configuration was corrected; Kotlin and native APK build/signature checks passed. Unit run:554 passed,14 failed,6 skipped. Device acceptance remains outstanding. See docs/build-verification.md.
