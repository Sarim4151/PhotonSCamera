# Reference viewfinder and selective adjustments implementation plan

**Goal:** Restore the user's supplied debug4 viewfinder input conversion and add Phocus-inspired category-selective paste.
**Architecture:** Preserve the current atlas, capture/export, LUTs and video fixes. Reuse existing LogInputGl for debug4 sRGB decode then inverse ACR3; camera template tone mapping stays untouched. Share immutable paste selections across existing editor and stored-metadata paths.
**Tech stack:** Kotlin, Compose, Camera2, GLES3.0.
**Authorization:** User said do it; standing permission to implement/build without approval pauses.

## Tasks
- [ ] Compare debug4 DEX shader constants and LogInputGl with current source; record exact evidence.
- [ ] RawEnginePreviewGl uses LogInputGl, sRGB decode followed by inverseAcr3, as reference. Camera2Controller removes build190 curve override, retains metadata/logs. Never change capture tone pipeline or add output caps.
- [ ] Implement selective paste Color/Detail/Frame/Geometry/Portrait, default Color+Detail; unselected values stay target-owned. Localize UI and preserve RAW compatibility/undo/video behavior.
- [ ] Verify reference shader fragments, host GLES render, source/resource integrity; independent review. Build191/version1.28.32-dev through existing GitHub CI including full unit suite.
- [ ] Validate downloaded APK version, certificate and packaged features; deliver durable APK.

## Review focus
Preview input/output gamma exactly once; no camera tone override; no video regression; selective group isolation, RAW/JPEG/video compatibility; localized selection UI in editor/detail/batch.

## Evidence / decisions
Supplied PhotonSCamera-debug-4.apk SHA256 25bc816270509e22818dad6bbced87d80f8e05748bae72e8fb5b330a52b3942e, embedded version1.28.14-dev. classes11.dex contains srgbToLinear(sampled.rgb) followed by per-channel inverseAcr3, and LogInputGl inverse shader. This is a reference look restoration, not a claim that ACR3 is the physical CMF ISP inverse. Physical preview validation requires user phone.
AGENTS says no new unit-test cases; use existing suite plus focused host probes. Local toolchain absent; remote branch is authoritative and local archive is not git.
