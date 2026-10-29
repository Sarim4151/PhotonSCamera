# Advanced capture reliability — 1.28.8 (167)

## Changes

- Source sequences wait on the camera thread for both an idle capture engine and enough ImageReader slots. Photo saving can finish before the processing queue releases its image leases; the previous one-shot submission could be silently refused in that interval and then time out without a second source. Readiness is bounded to 180 seconds, cancellable, and rejects a changed or closed camera. This does not resubmit accepted requests.
- Live ND and Live Composite no longer consume their entire duration while preparing the first source. Duration starts after its first completed accumulation; an automatically finishing run needs at least two completed source frames. A manual stop still stops the session.
- Publishing or recovering a full-source temporal checkpoint requires two frames. A single original is retained as an original, never presented as a successful stack.
- Night/astro fusion rejects donor pixels which differ too much from the reference instant, even with a two-frame burst or moving majority. A large median absolute deviation previously allowed moving edges to be averaged into ghosts. Static samples still average in linear light.
- The gallery thumbnail is released from the final source preview when a new merged result is published.
- The viewfinder reports waiting for the camera and accumulation separately. Logs identify each admitted capture and saved source. Original files remain available after a failed stack.

## Limits and phone checks

Live ND deliberately blurs moving subjects. Live Composite and star trails deliberately accumulate moving lights; use a tripod. These modes do not register handheld motion. Night and HDR registration cannot repair blur already present within an individual exposure, parallax, large camera movement, or an out-of-focus source. Set focus before starting; use shorter exposures for moving subjects.

Full-source temporal processing still requires enough heap and disk space to render one original and maintain an atomic checkpoint. An insufficient-memory/storage error stops with originals retained. The existing full-resolution memory guard has not been removed or replaced with silent resizing.

No phone or emulator is connected in this environment. The capture-admission race and fusion regression can be checked here; physical camera/driver acceptance remains pending. Do not treat this build as device-verified.

On the phone:

1. Select Source Burst, 6 frames and zero pause. Press the normal shutter once and leave it running. Expect six originals and a counter of six.
2. Select Night, capture a textured stationary scene with a small moving object. Expect a separate stacked JPEG and retained originals; moving edges should retain the reference instant.
3. Mount the phone on a tripod for Live ND/Composite. Wait until the counter reaches at least 2, then press the shutter to save. Try a short automatic duration too: first-source processing must not produce a one-frame stack.
4. Stop while waiting for the next capture; no delayed source should be admitted after stopping. Change camera or leave the screen; the old session must end.
5. If capture still stops, record the exact selected mode, frame count, visible “Capture stopped” message, phone model, and an Android logcat covering “Advanced capture failed”, “Advanced”, and “Capture rejected”. These identify driver failures, memory limits, and changed settings without guessing.

## Desktop regression

`KOTLIN_LIB=<Gradle 8.13 lib directory> bash scripts/validation/advanced-jvm/run.sh`

This executes the actual AdvancedSequenceProcessor using an array-backed Bitmap adapter. The moving-subject case failed before the fix with maximum brightness difference 58 and passes afterward with difference 0. The static-image output is checked pixel-exact. It is a software regression probe, not a camera hardware test.
