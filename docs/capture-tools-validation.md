# Snap focus LUT search and interval shooting

> Build update —21 October2026: Android/Kotlin/native APK build and signature checks now pass. Unit suite:554 passed,14 failed,6 skipped. Earlier environment-blocked notes below describe the checks available at implementation time; the current authoritative result is in build-verification.md. Physical-device acceptance is still outstanding.


## Source implemented

### Snap Focus

The Focus parameter ruler exposes a Snap menu only when the current lens reports a finite positive minimum focus distance. Presets are 1, 1.5, 2, 2.5 and 5 metres plus infinity. Labels explicitly describe approximate distances; reciprocal metres are Camera2 diopters, not a calibrated distance guarantee. Unsupported presets are excluded rather than silently clamped.

Selection passes through the existing retained-capture-settings mechanism and executes on the camera thread. It exits autofocus and hyperfocal focus, and rejects changes during capture/focus freeze. Fixed-focus lenses have no Snap control. The existing Auto button restores autofocus. Lens support is rechecked at execution time.

### LUT search

The Style panel has Search LUTs and Close search controls. Search is case-insensitive within the selected category, including Favourites, and matches all translated LUT names, file names and IDs. Query whitespace is trimmed. Empty input restores that category. Closing search clears the query. Search does not modify LUT selection or favourites. Empty results have a message. Automatic selected-item scrolling is suppressed while filtering. Query/visibility survive Compose saved-state recreation.

### Interval shooting

The photo top bar has an Interval control. The dialog cycles intervals of 2, 5, 10, 30 or 60 seconds and scheduled-slot counts of 10, 30, 60 or 120. The underlying API clamps interval seconds to 1 through 3600 and slot count to 2 through 999. The first slot starts immediately; subsequent deadlines are measured start-to-start using the monotonic clock.

Counts distinguish completed schedule slots, accepted camera requests and skipped slots; they do not claim saved files. Busy/expired slots are skipped instead of issuing catch-up bursts. A processing backlog of two or more shots also skips a slot. Multiple exposure, inactive preview, self-timer countdown, camera/mode changes and video processing prevent incompatible operation. The interval bypasses the ordinary self-timer and uses the existing capture metadata and location paths. Existing restricted LUT entitlement checks remain intact at start and between slots.

Pressing the shutter while running stops future interval shots. A dialog Stop control does the same; accepted shots finish normally. Closing the camera stops the job. Camera-ID changes, leaving photo mode or enabling multiple exposure terminate the loop. The camera-thread admission predicate rejects requests whose slot timed out or whose coroutine was cancelled. Session generations prevent an old cancelled job from marking a new session stopped. Sequences do not resume after process death.

## Checks performed

- git diff --check passed.
- Default resource XML parsed successfully; all 22 new string references resolved and default string IDs were unique.
- Existing preview-assistance GLES pixel probes re-ran successfully: shader compilation, passthrough, zebras, sensitivity, colours and alpha.
- Source review traced Snap menu through ViewModel and serialized camera control; reviewed interval deadlines, accepted-request callback, stale request predicate, cancellation and generation protection.
- Kotlin build attempted with bash ./gradlew compileDefaultDebugKotlin; it stopped before compilation because services.gradle.org could not be reached for Gradle 8.13. No Kotlin compilation, Android integration test or APK success is claimed.

## Pending device validation

- Compile and install on CMF Phone 1; exercise every new dropdown/dialog at portrait and landscape sizes.
- Confirm focus preset availability on each camera, autofocus restoration, hyperfocal exit, focus settling and approximate distances. Physical calibration is not implemented.
- Verify search across installed languages, large libraries, favourites, no matches and screen recreation.
- Interval runs with fast/slow exposures, processing backlog, save failures, camera close, rapid stop/start, lens switch and shutter cancellation. Check accepted counts against camera logs and saved counts separately.
- Verify no overlapping normal self-timer/burst requests; test low storage and camera errors. Current controls report acceptance, not save success.
- Measure CMF memory, heat and processing speed. Desktop static/shader checks do not validate these controls at runtime.

## Scope

These additions extend the previously delivered preview-assistance source. The full approved camera roadmap remains in progress; this is an uncompiled source checkpoint, not a finished camera release.
