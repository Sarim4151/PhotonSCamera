# Motion-photo and RAW timestamp validation

Motion-photo JPEG metadata inspection reads bounded JPEG headers through RandomAccessFile, stopping before compressed scan data. It allocates at most one 65,533-byte APP1 payload, with a 16 MiB / 4,096-segment header traversal limit. It does not load the appended video to inspect metadata.

Supported embedded payloads begin with a normal ISO-BMFF `ftyp` box. Container resources are located backwards from EOF using the selected video's length plus all following resource lengths. Length and padding values must be nonnegative and fit the file; only primary-item padding is supported, and shared zero-length video resources are rejected. Malformed directories do not fall back to obsolete offsets. Google legacy MicroVideoOffset, legacy LIVE trailer, and existing Oppo block extraction remain supported. Oppo's declared motion-photo block may contain its private tail; this change does not rewrite that vendor payload.

Extraction rejects the same canonical source/destination file and deletes a partially written regular output on failure. Vivo sidecar creation/extraction still requires device verification.

DNG date/time serialization prefers the retained `CaptureInfo.captureTime` wall clock. If unavailable, sensor-time conversion requires Camera2's REALTIME timestamp source and a nonfuture sensor timestamp. UNKNOWN sensor clocks are never mapped to elapsed realtime; current wall time is the explicit final fallback.

Direct Kotlin/JVM verification (without Gradle): actual MotionPhotoWriter and Mp4DurationReader sources compiled with small Android/creator stubs; nine JUnit fixtures passed. The fixtures cover Google, Oppo, legacy, following resources with primary padding, malformed/overflow lengths, truncated APP1, SOS stopping and same-source protection. The pure capture-time helper and its four cases compiled and passed separately. These checks do not establish full Android build success, live-photo capture/playback compatibility, RAW device metadata correctness, or exception-path storage behavior on a phone.

Reference: https://developer.android.com/media/platform/motion-photo-format
