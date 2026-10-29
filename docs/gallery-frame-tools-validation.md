# Gallery frame tools and inspired recipes

> Build update —21 October2026: Android/Kotlin/native APK build and signature checks now pass. Unit suite:554 passed,14 failed,6 skipped. Earlier environment-blocked notes below describe the checks available at implementation time; the current authoritative result is in build-verification.md. Physical-device acceptance is still outstanding.


## Source checks

- Stable recipe IDs extend the existing built-in preset path; existing selection/defaults are unchanged.
- Names use Android string resources. Existing custom preset naming falls back unchanged.
- Single-photo recipe rendering passes the override into final metadata before cache lookup; the creative LUT/frame is cleared while source baseline/calibration remains; each variant starts from the same source, not the preceding output.
- Cache-returned bitmaps are copied before passing to processing and are never recycled by the new caller.
- Sequence operations own independent input copies, bounded to about 64 MiB of input pixels; cached previews and processing allocations are additional memory.
- Alignment models translation with residual/ambiguity checks. Focus fusion uses local Laplacian detail with feathering. Average/max use linear sRGB. Best frame uses detail and clipping, without requiring alignment.
- Outputs use fresh metadata with no LUT/recipe applied twice, record source IDs, and are checked for a nonempty internal image after saving. Failed prepared outputs are removed. Publication finishes before cancellation is honoured.
- Originals are never passed to a deletion method. Progress/error/cancel state is visible in the dialog.

## Device acceptance still required

Compile Kotlin and native code; validate preset sliders and visual neutral baseline, one-photo variants with/without an existing LUT, equal/mixed dimension rejection, flat/repetitive scene rejection, translated focus sequences, moving light trails, sharp/blurred frame ranking, cancellation during load/fusion/save, output gallery refresh and low-memory behaviour. Check that original JPEG/RAW files remain unchanged.

Only XML parsing, whitespace/source checks and the separate desktop GLES overlay probe were available here. No Android compilation or device test result is claimed.
