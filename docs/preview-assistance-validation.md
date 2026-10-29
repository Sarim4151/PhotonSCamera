# Preview assistance implementation and validation

> Build update —21 October2026: Android/Kotlin/native APK build and signature checks now pass. Unit suite:554 passed,14 failed,6 skipped. Earlier environment-blocked notes below describe the checks available at implementation time; the current authoritative result is in build-verification.md. Physical-device acceptance is still outstanding.


## Delivered source changes

- Persisted peaking colour: red, yellow, blue, white. Existing red remains the default.
- Persisted peaking sensitivity: low 1.2, medium 0.8, high 0.4 Sobel thresholds. Medium retains the old default.
- Independent zebra switch, disabled by default, and threshold choices 70 to 100 percent in 5 percent increments; default 95.
- Thresholds are integer percentages clamped at persistence read/write and GL uniform boundaries. Invalid enum names fall back to defaults.
- Zebras measure display RGB luminance from the rendered preview; they are not a sensor RAW clipping measurement. Static diagonal stripes use a 12-pixel period and 0.65 opacity, overriding peaking in marked areas while preserving alpha.
- Single immutable settings snapshot per frame. Both assistance controls disabled retains the previous fast route; zebra-only mode bypasses Sobel neighbour sampling.
- Recorder input is retained before assistance. Preview-capture callbacks and metering now also retain a clean texture before assistance. Eye focus samples the Original preview source. Saved-output isolation has source-level tracing, not Android device proof.
- No duplicate Canon or Leica engines were added or changed.

## Checks performed

1. git diff --check: passed.
2. Default strings XML parses; all 13 new UI resource references resolve; no duplicate default string IDs.
3. Desktop Mesa EGL 1.5 GLES context: production focus-assistance fragment and simple vertex shaders compile. Linked render probe passes disabled pass-through, dark/mid/below-threshold identity, white zebra stripes, alpha preservation, thresholds 70/95/100, sensitivity response on a low-contrast edge, and all four peaking colours.
4. Source review: recorder consumes clean outputTexId; finalDisplayTextureId is retained before overlay and passed to capture/metering. Original eye-focus source bypasses overlay.
5. bash ./gradlew compileDefaultDebugKotlin: blocked before compilation because Gradle 8.13 download from services.gradle.org reports Network is unreachable. No successful Android build, Kotlin type check or APK packaging is claimed.

Desktop GLES checks do not prove CMF Phone 1 Mali compatibility or UI behaviour. Runtime persistence and lifecycle behaviour are unverified until the Android app can be built and run.

## Remaining device checks

- Build compileDefaultDebugKotlin and assembleDefaultDebug with Gradle, Android SDK and NDK installed.
- Existing installation migration, app restart, invalid persisted IDs, all dropdowns and each threshold.
- Autofocus plus zebras, manual focus plus peaking, both active, neither active, and hyperfocal focus switching.
- Still images, preview-derived captures, video, Motion Photos and exports contain no overlays; metering does not react to stripes.
- Lens switches, orientation, background/resume, repeated toggles, resizing and context recreation; inspect GL errors and resource use.
- Preview frame time and thermal measurements with both off, peaking only, zebras only and both.
- Post-effects on/off combinations including grain and highlight diffusion.

## Scope and remaining programme

This source package implements only the approved viewfinder-assistance work package. The full camera feature programme remains listed in docs/superpowers/plans/2026-10-21-viewfinder-assistance.md. It is not complete.

New strings use Android resources with English fallback for locales without reviewed translations. No speculative translations or proprietary profiles are introduced.
