# Flash capture and cinema controls

## Scope and evidence

Based on codex/camera-188-build at cc16298c. The reported flash hang has not been reproduced on a connected phone in this environment. These changes close specific failure paths found in source, not proof that every flash capture now saves. Local unpushed lifecycle fixes on the user’s PC are not included.

### Capture failure paths

- A submitted capture had no deadline until onCaptureSequenceCompleted. A missing HAL terminal callback could retain isCapturing, reader reservations and the gallery processing placeholder indefinitely. The submitted request now has an exposure-duration-aware deadline; expiry aborts the sequence and runs the normal failure and preview-reset path. It does not fabricate a saved photo. Late terminal callbacks are ignored.
- performCapture exceptions only cleared isCapturing and reset preview. A submitted reservation could remain alive; submission exceptions now explicitly fail and finish the owned capture.
- Registration begins asynchronously after exposure completion. Image delivery failure can race with registration, clearing the gallery marker before registration adds it. Failure cleanup now awaits registration when it has actually started. Preparation failures never await an unstarted registration.

## Cinema controls delivered

- Shutter angles from 1.1° to 360° via slider, applied on release to avoid flooding camera reconfiguration.
- Expanded common angles including 11.25°, 22.5°, 45°, 144°, 172.8°, and 216°.
- Angle and reciprocal shutter-time readout.
- Explicit 50 Hz (1/50, 1/100) and 60 Hz (1/60, 1/120) mains-light presets; incompatible frame rates are disabled. Actual exposure remains clamped to the camera’s manual sensor range. These presets cannot guarantee flicker removal from every LED/PWM light source.
- Existing recording/processing and sensor-capability guards remain. English and Chinese resources included.

## Blackmagic Android feature audit — incomplete parity

Reference: https://www.blackmagicdesign.com/products/blackmagiccamera/techspecs (Android section). These are independent implementations, not Blackmagic image processing or service compatibility.

| Area | Repository status |
| --- | --- |
| ISO, shutter, white balance, focus, exposure locks | Existing controls; expanded shutter UI in this change |
| H.264/H.265, frame rate, bitrate, resolution, open gate | Existing; device-dependent |
| False color, zebras, peaking, histogram | Existing; no new rendering shaders |
| LUTs, log profiles, rendering engines | Existing; simulated log transforms are not proof of native sensor log support |
| Microphone selection, gain, audio processing | Existing; not equivalent to every Blackmagic audio format or monitoring feature |
| Frame/safe-area guides, anamorphic monitoring, rack-focus presets | Not implemented by this change |
| Off-speed/project-FPS separation, timecode metadata, proxy generation, slates | Not implemented by this change |
| Remote multicamera, streaming, Blackmagic Cloud/Resolve synchronization | Not implemented; no compatible service integration established |
| 8K, high-speed capture, HDR and codecs | Must follow advertised sensor/encoder capabilities; no unsupported capability is advertised here |

## Validation

- git diff --check and resource XML parsing run locally.
- Kotlin compilation attempted locally but Gradle distribution download failed: network unreachable; no Android SDK installed in this environment.
- Branch enables the existing GitHub debug-build/test workflow; its result must be checked independently.
- Still required on CMF Phone 1: flash on/off capture with RAW/JPEG and Live Photo, repeated captures, camera background/resume, and confirm actual image files plus cleared processing state.
