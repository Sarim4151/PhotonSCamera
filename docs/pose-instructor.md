# Offline pose instructor

The opt-in **Pose guide** button in the Photo/Pro viewfinder runs Google's MediaPipe Pose Landmarker Lite. The APK bundles the model; no account, API key or network model download is needed. It measures up to 33 landmarks of one person. Portrait and full-body choices select guidance for framing, head/shoulder alignment and arm separation. Suggestions are approximate photographic suggestions, not medical posture assessment or guarantees. It never activates the shutter.

## Provenance and license

- Android API guide: https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android
- Official models and landmark indices: https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker
- Bundled file: `app/src/main/assets/pose_landmarker_lite.task`
- Versioned source (Google): https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task
- SHA-256: `59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a`
- Model card: https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf
- Google's model card identifies Apache License 2.0. Copyright and author credit: Google / Valentin Bazarevsky, Ivan Grishchenko and Eduard Gabriel Bazavan. See `app/src/main/assets/licenses/mediapipe-model-Apache-2.0.txt`.
- Android runtime pinned to `com.google.mediapipe:tasks-vision:1.1.1`; latest release verified against Google's Maven metadata on 2026-10-24: https://dl.google.com/dl/android/maven2/com/google/mediapipe/tasks-vision/maven-metadata.xml

## Scheduling and coordinates

Inference uses the CPU delegate on a dedicated worker. Only one original-preview readback can be pending, even while the selected guide, camera or lifecycle changes. Readbacks cap the long edge at 640 px; polling waits at least 400 ms between starts (maximum 2.5 Hz). A slow inference lowers the rate instead of creating a backlog. The original-preview renderer already applies its displayed orientation, crop and front-camera mirror, and flips the framebuffer's Y direction before returning a bitmap. Normalized landmark coordinates therefore map to the same viewfinder box directly; applying another sensor rotation or front-camera mirror would be incorrect.

Inference stops and its native task closes on leaving the foreground, switching out of still capture, capturing, changing camera, disabling the tool, leaving the screen or preview transitions. It pauses for preview magnification because original readback and magnified display no longer have the same field of view. Missing/low-confidence/out-of-frame landmarks produce a waiting state. Initialization/inference/readback failure shows an unavailable message. Reopen the camera to retry after a stalled renderer; the tool intentionally does not enqueue repeated readbacks while a callback is outstanding. Source bitmaps and MediaPipe images are released after each inference.

## Verification and limitations

Static validation confirms the exact preview Box placement, source/overlay coordinate mapping (including renderer mirror and framebuffer Y flip), one-pending-request gate, native task disposal, confidence gating and absence of shutter calls. Physical-device checks still required: rear/front cameras, portrait/landscape orientations, all photo aspect ratios, low light, no person, occluded limbs, app background/foreground, and rapid camera/mode switches. CMF Phone 1 power, temperature and pose accuracy have not been measured in this environment. The Lite model trades detail for lower cost and can miss close cropped portraits, people far away or partially hidden; one-person measurements do not support group pose guidance. No numerical confidence is presented as an accuracy guarantee.
