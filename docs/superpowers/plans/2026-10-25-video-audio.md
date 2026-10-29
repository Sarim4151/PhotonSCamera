# Video audio cleanup Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. User has explicitly opted out of further approval and continuation questions.

**Goal:** Add genuine device-backed background-noise suppression plus an independent adjustable wind filter to recorded video audio.

**Architecture:** Attach Android NoiseSuppressor to the owned AudioRecord session, verify its enabled state, and release before the recorder on every cleanup path. A stereo-safe PCM16 second-order Butterworth high-pass filters wind/rumble before AAC, preserving frame count and timestamps. Existing camera/video encoding and pause timing remain in place.

**Tech Stack:** Kotlin, Camera2/AudioRecord/MediaCodec, Android NoiseSuppressor, Compose, DataStore.

**Spec:** User-authorized video phase and audio noise reduction; this plan records the concrete design. Android NS exposes availability and enable control, not a strength API. Do not invent a hardware NS strength control.

## Global Constraints
- Android minSdk30; no added model downloads or native dependencies.
- New controls default off; Off bypasses app processing, not firmware processing.
- Wind options OFF/LOW/MEDIUM/HIGH use 0/65/100/160 Hz cutoffs at 48 kHz.
- Snapshot settings at recording start; lock edits during recording/finalization. Stereo is preserved; no sample count or timestamp changes.
- English/Chinese strings; device absence/failure does not prevent video recording.
- Build normalized-time copy with existing signing key and bounded workers. No phone/emulator validation claim.

## Review Focus
- Unsupported or failed device NS: show truthful state and retain audio/video capture.
- Missing microphone permission: preserve existing silent-video behaviour and report no audio.
- Prepare failure, stop, restart and release: effect detached/released before AudioRecord, late callbacks ignored.
- Stereo and arbitrary read chunks: frame-aligned, no cross-channel state, equal coefficients and unchanged bytes/frame count.
- Pause/resume: keep filtering captured PCM even while paused; existing timeline trimming unchanged.

### Task 1: Audio components
- [x] Add VideoWindReduction persisted enum, pure VideoWindFilter with process(ByteArray,Int), and DeviceVideoNoiseReduction with attach(sessionId,enabled), noAudio(), release() and StateFlow status.
- [x] Verify Off identity, chunk-boundary equivalence, stereo isolation, DC/wind attenuation, voice-band response and extreme sample handling with executable JVM tests.

### Task 2: Recording and settings integration
- [x] Add two VideoConfig/DataStore settings and controller/VM setters, restore them from preferences, snapshot at startRecording.
- [x] Attach effect before startRecording, create owned wind processor per session, process frame-aligned reads before existing active-range trimming. Release effects on all error/stop paths; noAudio status on initialization failure.
- [x] Add microphone-panel noise switch, wind strength choices, explanations, actual recording status and a locked-while-recording notice. Persist next-recording settings and expose status through VM.

### Task 3: Verify and package
- [x] Run assembleDefaultDebug and testDefaultDebugUnitTest; verify signing, package version, source/build hashes and archive integrity.
- [x] Save updated APK/ZIP/source and handoff, report hardware-only limitations accurately.

## MotionCam scope extension
User additionally requested MotionCam Pro features. Added a preview-only false-colour pass via the existing assist shader, Video monitor controls, existing preview histogram exposure, S-Log3/S-Gamut3.Cine and ACEScct/AP1 encoding choices, shutter-angle presets and renderer diagnostics. Dedicated RAW video, MCRAW/DirectLog and RAW sensor monitoring remain explicitly unimplemented rather than being aliases for ISP-derived video. Independent source reviews and host shader readback completed; final packaged build verification is tracked in build-verification-174.json.
