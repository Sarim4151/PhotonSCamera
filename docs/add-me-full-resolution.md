# Full-resolution Add Me repair — 22 October 2026

The phone owner confirmed that the 2048 px Add Me flow works in 1.28.6, but the full-resolution option does not. Its exact phone error/stage has not yet been supplied. Source investigation nevertheless reproduced a definite blocker: the old full-source recipe preflight rejects 4000×3000 even on an otherwise empty 512 MiB JVM heap. The required budget was 513,554,432 bytes, compared against only 70% of available heap. Full-size inputs and composite previews also stayed alive throughout alignment/review. Inference and ROI changes operated on full-size bitmaps unnecessarily.

Version 1.28.7 (166) separates review dimensions from saved dimensions. Both output modes capture and preserve the same original files. Alignment, person segmentation and interactive review always use a maximum-2048-pixel recipe-rendered preview. This retains the path the phone owner has confirmed works.

When Full resolution is selected, Save processes the original sources at their original dimensions. One recipe-rendered source at a time is written to a private, lossless sRGB ARGB row cache and recycled before the next source is decoded. The final compositor reads two rows and writes one full-size output bitmap. It does not enlarge the 2048 px review image. Normalized alignment, probability mask, ROI and feathering use the same pixel mathematics as the previous renderer. Existing source files and RAW links are preserved; output metadata records full_source only for this full-original path.

The source recipe guard now budgets one source operation (20 bytes/pixel plus reserve, matching the existing source temporal processing pattern) rather than assuming a retained full-size pair. A separate output guard budgets one ARGB output plus reserve. Actual memory and storage limits are still enforced. Extremely large sources, low memory or GPU limits may still prevent a full-source recipe; no silent downgrade or upscale is performed. Full-source dimensions, available heap and processing stages are logged under AddMe for device diagnosis.

The two-layer Bitmap LUT path also releases its privately owned baseline intermediate after the creative result is produced. It preserves caller-owned input and returned-result aliases. Shader operations and colour mathematics are unchanged.

The save UI shows source 1, source 2, row blending and final saving stages. Output quality can be switched in review, so a failed full-source save can be retried at 2048 px without recapturing. Partial row files and session caches are removed on success, cancellation and failure; an interrupted process may leave a temporary app-cache directory for the OS to reclaim.

## Software probes

`scripts/validation/addme-jvm/run-full-resolution.sh` compiles the production source preflight, disk staging and row compositor, using an array-backed Bitmap adapter and supplied synthetic masks. It runs with -Xmx512m:

- The old preflight fails the 12 MP test; the new isolated-source guard passes with 96 MiB of retained simulated camera/preview heap.
- Four outputs match SHA-256 pixel goldens captured from the pre-change v1.28.6 compositor, covering translation, probability gradients, ROI clipping and feathering.
- A true 4000×3000 output retains an independently checked one-pixel background pattern, the first-shot person and the added second-shot person.
- Loader assertions require the previous full source to be recycled before loading the next source.
- Success removes its cache. Partial-write cancellation, source-size mismatch and truncated row data are checked.

The existing bounded compositing probe also passes. These are JVM/row-math checks, not Android decoder, GPU, actual ML Kit, JPEG or Camera2 acceptance. Android build/signature/unit results are recorded separately after execution.

## Phone acceptance

Install 1.28.7 over the existing debug app. Select Full resolution, capture both shots, inspect and confirm the review, then save. The review remains 2048 px by design. Check the saved composite dimensions in the app gallery against the original shots (for example, 4000×3000), and inspect fine detail. If a stage fails, report the visible status/error and export the AddMe log. Both originals should remain. No physical device was connected during this repair.
