# Universal Photo Implementation Plan

Goal: device-independent professional colour, honest full-source advanced processing, and the requested photo tools.
Architecture: preserve Camera2 calibration and existing RAW/Photo separation; bound multi-frame memory with disk-backed sources; reuse preview/export recipes and save-copy editors.
User has explicitly opted out of further approval and continuation prompts. Preserve signing key and all existing work. No connected phone; device validation remains required.

## Tasks
Source checkmarks mean implemented code, not verified Android/device behavior.
- [x] Capture capability: actual format-specific regular + high-resolution stream sizes, largest source, session fallback, actual source dimensions visible.
- [x] Native advanced merge: disk-backed frame reads, full source default, no hidden 2048 cap; honest native fallback when true 2x cannot fit.
- [x] Universal styles: neutral/portrait/landscape recipe-only changes preserving calibration and WB.
- [x] Every advanced mode: live subject-aware shooting coach (user clarification superseded static guide).
- [x] Best Moments: bounded automatic quality ranking and manual override.
- [x] Expression detection: diagnose input/model path; multiscale/rotation attempts with correctly mapped mouth landmarks; honest failure feedback.
- [x] Subject tracking: spatial association/hysteresis and bounded reacquisition; no perfect or identity recognition claim.
- [x] Selective editing: brush/linear/radial local adjustments, full-source save-copy, bounded preview, verified publication.
- [x] Adaptive capture: use measured exposure/clipping and thermal state to choose available merge and frame budget; preserve manual modes.
- [x] Capture recovery: retain valid completed frames where safe; show source/merge/save stages distinctly.
- [x] Photo diagnostics: dimensions, EXIF, clipping/detail estimates with uncertainty stated.
- [x] Android build and existing regression suite:588pass/3skip; signed APK and source packaged. Physical phone validation remains pending.

## Follow-on domains in authorized scope
Best-face burst merging; semantic skin styles/masks; subject-motion shutter selection; high-quality pre-shutter buffer; action pan; advanced astro foreground separation; Photo Sphere/panorama; document perspective/OCR/PDF; editor project history/masks; detail workspace; camera workspaces. Existing implementations must be audited before duplication. Do not mark these complete based on a menu or label. Native 50MP is only available if firmware exposes a usable capture stream; computational output is distinct.

## Review focus
No silent resolution changes; no arbitrary DCP across cameras; frame/disk/bitmap ownership on cancellation; corrupt or absent model errors surfaced; private intermediate photos hidden; preview/export consistency; session rejection falls back with visible actual size.
