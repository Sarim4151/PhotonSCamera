# Integration verification ledger

Original source version 6 was restored before this pass. The prior 574-test result (14 failures) is historical, not evidence for these changes.

## Executed checks so far

- All default/localized resource XML parsed successfully; camera screen string references resolved.
- Production focus-assist GLSL compiled, linked, rendered and read back with Mesa GLES: peaking colours/sensitivities, zebra, magnification, alpha and clean pass-through passed.
- Production photographic style GLSL Mesa probes passed: neutral identity, bounded extremes, neutral hue/endpoints and skin protection.
- Production domain-sampling GLSL Mesa probes passed unit and asymmetric/non-unit domains, including a size-2 identity table with domain .25–.75.
- Actual MotionPhotoWriter and duration reader compiled with narrow JVM stubs; 9 fixtures passed, including padding, following resources, malformed headers and source-overwrite protection. Capture-time policy cases passed separately.
- First combined Kotlin compilation exposed changing resource IDs and an unqualified resource import; those source issues were corrected. Full Android compilation, packaging and the complete unit suite are running next; no pass is claimed here until results are recorded below.

## Review corrections

1. Non-unit LUT domain transforms moved from approximate rebaking to coordinates before sampling.
2. Live/group inputs apply stored recipes once; baked outputs do not reapply them.
3. Checkpoint replacement preserves the previous recoverable session until a new frame commits; unlisted large archive growth removed.
4. Tiled reconstruction uses full-frame residual/reference fallback outside the central registration area.
5. Source decoding and publication have bounds/RAM preflight before full bitmap allocation.
6. Main shutter gestures read current callbacks and busy state, so stopping is not blocked by stale Compose closures.

Physical camera acceptance remains separate from build and software-GPU checks.

## 22 October verification continuation

Restored saved source version 7 after the transient build workspace reset. Re-ran style/domain/focus Mesa render probes and the actual Kotlin super-resolution harness: all passed. Production reconstruction accepted both four-phase fixtures, rejected the identical burst, and matched reference fallback exactly across 2,736 moving-edge pixels outside its central registration window. The source now preserves separate AE positioning when toggling AF lock, includes WB intent/AE lock in capture consistency checks, exposes sequence tools in the default quick layout, and keeps completed/error status visible in the viewfinder. Android build and unit-suite results will be recorded after the current commands finish.

The first restored Android build produced the APK, compiled native code, and ran 579 unit tests: 571 passed, one failed, seven skipped. The remaining failure exposed an invalid identity-fixture assumption in the unchanged Local Laplacian mapper: detail alpha=1 did not imply edge beta=1 at a 10^6 target range with exact black. The test now explicitly establishes identity preconditions at 10^20 without weakening its pixel tolerance, and separately checks the original edge-compression formula. Its eight focused Kotlin/JUnit tests passed. A complete offline APK/unit-suite rebuild is in progress for the final source, including graceful stop-before-next-frame handling and a localized insufficient-frame error.

## Final result,22 October2026

Full offline APK/native build and unit-suite command exited0:573 passed,0 failed,7 skipped out of580. Signaturev2 and APK CRC verified. APK SHA-256:8999915682e7a280c89572949cc2cdc64e21ea35ceeef07c8eb49899a3a17512. Device acceptance remains pending.
