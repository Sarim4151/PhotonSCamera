# Camera Quality Calibration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Match the CMF Phone 1 preview to rendered photos and add measured quality improvements across photo and video without promising unsupported capabilities.

**Architecture:** First establish paired preview/output measurements per camera, rendering engine, exposure and illuminant. Feed validated transforms into the preview pipeline, not the saved RAW processor. Subsequent video and capture changes use camera/encoder capability checks and motion-aware processing, retaining per-lens behavior and graceful fallbacks.

**Tech Stack:** Android Camera2, Jetpack Compose, GLES, MediaCodec, Kotlin; Python/Pillow/NumPy for offline paired-image diagnostics.

**Spec:** `docs/quality-roadmap-status-179.md` and the user's ten-item quality list.

## Global Constraints

- Camera2, minimum Android API 30.
- Keep selected RAW rendering engine after HDR+ merge.
- Require paired CMF Phone 1 captures before claiming calibrated preview colors.
- Do not expose 10-bit HDR video unless camera dynamic range, stream and encoder all support the same configuration.

## Review Focus

- Different ISP tone mapping at high ISO must not blow out the preview.
- Moving subjects must not be averaged into ghosts.
- Captures must save exactly one intended result per shutter sequence.
- Lens switches must not discard video or silently change color profile.
- Unsupported 10-bit combinations must remain hidden and preserve SDR recording.

## Tasks

- [x] Add a paired preview/photo diagnostic measuring center-crop luma and color, with before/after output.
- [x] Add conservative preview-highlight and requested-resolution warnings; avoid claiming sensor clipping from an ISP preview.
- [ ] Collect paired fixed-exposure/fixed-WB images for every engine/lens and representative daylight, tungsten and mixed scenes on CMF Phone 1.
- [ ] Fit and validate per-engine/lens/illuminant preview calibration from the paired data; integrate into `RawEnginePreviewPlan` and test against held-out pairs.
- [ ] Connect scene-motion estimates to RAW burst frame count and shutter limits; validate moving and static low-light scenes.
- [ ] Add skin-aware chroma/denoise gates based on reliable segmentation and test mixed lighting.
- [ ] Add temporal video denoising with motion rejection, bounded history and adjustable strength; profile 4K thermals and memory.
- [ ] Implement smoothed AE/AWB result-following requests without conflicting with manual locks.
- [ ] Query Camera2 dynamic-range profiles and encoder 10-bit support; construct HLG/PQ capture, preview and container metadata only for a verified compatible path.
- [ ] Measure paired lens color/exposure at overlapping focal lengths, then tune transitions without violating recording lens lock.
- [ ] Expand quality indicator to measured shake and subject motion once sensor/frame motion signals are validated.

The unchecked tasks require device data and runtime verification; this document does not imply they are delivered by the current APK.
