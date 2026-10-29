# Advanced capture regression probes

Set `KOTLIN_LIB` to the Gradle 8.13 `lib` directory, then run:

- `bash scripts/validation/advanced-jvm/run.sh`: executes the real night/astro/HDR stacking implementation with an array-backed Bitmap adapter; verifies reference-preserving moving-subject rejection and exact static output. The moving-subject assertion failed before the fix (brightness difference 58); afterward difference is 0.
- `bash scripts/validation/advanced-jvm/run-admission.sh`: extracts the real camera readiness method into a desktop owner adapter, exercising image leases, busy capture, finish requests, camera identity and cancellation. The camera-thread runner and readiness fields are simulated. This checks waiting logic; it does not exercise Android Camera2, driver callbacks or real ImageReader ownership.

Neither probe proves physical phone behavior. See docs/advanced-capture-reliability.md for device acceptance steps and retained limits.
