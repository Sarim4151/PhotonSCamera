# Add Me desktop probes

Set KOTLIN_LIB to a Kotlin compiler distribution lib directory containing the Kotlin stdlib 2.0.21, coroutines 1.6.4 and annotations 24.0.1 (the Gradle 8.13 lib directory provides these).

- `bash scripts/validation/addme-jvm/run.sh`: bounded bitmap registration/compositing checks.
- `bash scripts/validation/addme-jvm/run-full-resolution.sh`: full-source staging, pixel goldens, 12 MP detail, memory preflight and cleanup checks with a 512 MiB JVM heap.

The code under test is the production Kotlin implementation. Only Android Bitmap storage is adapted to a desktop array. ML Kit segmentation is omitted because it requires Android; supplied masks are synthetic. File/row staging is real. The four fixture hashes were produced by executing the v1.28.6 renderer before refactoring. A successful probe does not establish live Camera2, Android GPU/decoder, phone memory or actual person-segmentation acceptance.
