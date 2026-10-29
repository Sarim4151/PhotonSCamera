# Advanced capture implementation and remaining blockers

> Build update —21 October2026: Android/Kotlin/native APK build and signature checks now pass. Unit suite:554 passed,14 failed,6 skipped. Earlier environment-blocked notes below describe the checks available at implementation time; the current authoritative result is in build-verification.md. Physical-device acceptance is still outstanding.


## Source additions

### Focus magnifier

The Focus ruler has a 1x, 2x, 4x centre-preview magnifier. This display-only transform runs after clean recording/capture textures are retained. Changing lens or capture mode resets magnification; leaving the focus ruler or returning to autofocus resets it too. Tapping or long-pressing a magnified preview exits magnification instead of placing an incorrectly mapped focus/exposure point. Point handles are hidden while magnified. The saved image is not intentionally cropped by this transform.

Actual shader probes on desktop Mesa GLES verified centre sampling at all three factors, identity at 1x, alpha preservation, and continued peaking/zebra behaviour. This is not Android device proof.

### Multiple exposure blend modes

The existing mode now offers Average, Lighten, Darken, Screen and Additive blends. Preview and full-resolution composition share the chosen session mode. The existing Average implementation is preserved as the default. Other modes are conventional display-referred JPEG blends, not RAW sensor fusion or a claim of OM System Live ND/Live Composite equivalence.

Source JPEGs and blend.json are retained under the final private gallery-photo directory in multiple_exposure_sources. They are not separately exported to the system gallery and are not original sensor RAW files. Blend changes are disabled during a live/processing session. Finalization snapshots the source file list and mode, protects its session against deletion while saving, rejects missing/empty source sets and checks that a non-empty final display image exists before source preservation. Cleanup is scoped to the matching session so an old finalizer cannot reset a replacement session. Failed preservation leaves the session for retry.

### Focus bracket capture

The Sequence control links to a Focus bracketing dialog. Near/far endpoints use Camera2 diopters, with zero denoting infinity. Frame counts are 3, 5, 9 or 15. Targets step linearly in diopters. Each target is applied on the camera thread and waits for two newer lens-result samples, a stationary/not-moving lens state and reported distance within 0.02 diopters. Missing/inconsistent readback aborts instead of guessing a delay.

Each frame uses the existing photo pipeline, waits for capture-ID-specific save success and remains a separate gallery image. Metadata records group ID, index, count and requested focus distance. This feature does not yet perform focus-stack alignment/merge.

The sequence restores original manual/auto/hyperfocal state and independent point-metering state for the same active camera. Manual focus and point-control changes stop the bracket, wait for restoration and then apply the requested user change. Admission also checks autofocus/target state. Camera close, shutter cancellation and mode changes stop future requests; already accepted frames complete. Rapid restart is blocked until the old focus-restoration job finishes. Source capture timeouts, missing saved images and explicit camera failures abort the bracket.

### Interval admission fix

A fresh source review found that timeout/cancellation could miscount a shot admitted by the camera. Counts now update from the actual admission callback. A locked per-slot outcome reconciles a late accepted shot against an earlier skipped outcome. New sessions use generations; a cancelled predecessor cannot alter the new session tally. This fix has source review, not Android concurrency-test evidence.

## Review findings addressed

- Interval admission counts no longer depend solely on the acknowledgement awaiter.
- Composite source list/mode are frozen, deletion is guarded and cleanup is session-specific.
- Bracket completion uses capture-ID-specific saved-file evidence rather than merely isCapturing becoming false.
- Bracket manual focus avoids clearing independent point metering; restoration retains focus points and mapping aspect.
- Conflicting manual focus controls stop and await bracket cleanup; admission revalidates requested target state.

## Verification evidence

- git diff --check passed.
- Default XML parsed with unique string IDs; all 55 new resource references resolved.
- Production preview-assistance shaders compiled in desktop EGL/GLES; pixel probes passed for pass-through, thresholds, alpha, four peaking colours, sensitivity and 1x/2x/4x centre magnification.
- Independent source review covered capture cancellation, output isolation, interval counting, composite preservation and focus bracketing; important findings were addressed in source.
- bash ./gradlew compileDefaultDebugKotlin was attempted again. Gradle 8.13 download failed with java.net.SocketException: Network is unreachable. No Android compiler, Android tests or APK build ran successfully.

## Not complete

The project is not a verified release. Android type checking and installation are still blocked, and no CMF Phone 1 is attached for lens, lifecycle, performance or photo-quality acceptance. The following requested features are still unimplemented or unaudited end to end: focus-stack merge, three-recipe capture outputs, pre-capture, best-shot selection, guided group/face compositing, dedicated astrophotography, automated Live Composite/Live ND, and the full reference-based colour/HDR/night improvements. Existing Canon/Leica and other engines have been preserved, not newly validated as exact brand matches.

Do not label this source checkpoint as the whole project completed. Resume from the programme status, obtain working Gradle/Android dependencies in the build environment, run the compiler and resolve actual failures before packaging an APK. Programme-wide approval is already granted; no additional feature approval is required.
