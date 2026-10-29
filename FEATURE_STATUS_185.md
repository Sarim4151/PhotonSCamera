# Feature status after build 185

This file distinguishes actual app behavior from requested future work. Hardware-specific behavior needs device testing.

## Available

- Native Spektrafilm stage generation and film/print processing, selected as a RAW rendering engine.
- Live Spektrafilm rendering-engine preview (approximation of the RAW output because Android's preview ISP has its own processing).
- Editable film-process presets including Pro-Mist-style 1/8, 1/4 and new Black Pro-Mist-style 1/8, 1/4. These use the existing GPU Bloom, soft-light, Halation and grain effect stages after color rendering; they are not physical diffusion inside the native Spektrafilm film simulation.
- HDR+ merge selection, multi-frame motion rejection in selected paths, per-lens source resolution selection, 2× finish upscaling, and separate video AE/AWB locks, subject to supported device modes.

## Partial or unverified

- Preview-to-photo color matching: the preview-only luminance ceiling was removed in build 183, but ISP processing means this is not exact; lens/light/engine calibration remains.
- Motion-aware merging, shake-aware low-light exposure/frame selection, white-balance and skin-tone continuity, detail-aware denoising, consistent lens switching and zoom detail recovery.
- Flash color/exposure and Live Photo flash timing; 2× full-resolution export performance and 4K saved framing require physical device checks.

## Missing as end-to-end features

- A native *pre-film* spatial diffusion stage within Spektrafilm. The new named presets are post-render GPU effects, not this feature.
- Motion-adaptive multi-frame temporal noise reduction for video with an intensity control.
- Capability-checked HLG10/HDR10 recording with accurate SDR preview/export.
- A concise, reliable capture-quality indicator for shake, subject motion, clipping and unavailable lens-specific modes.

Do not describe these partial or missing items as complete in release notes.
