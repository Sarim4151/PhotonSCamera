# Camera feature programme — integration update, 22 October 2026

The source changes below are connected to app workflows. This document separates source implementation from actual build evidence and physical camera acceptance. See `build-verification.md` for the historical baseline and the new integration report for current checks. No exact proprietary Apple/Google/Samsung/Fujifilm/Leica/Canon algorithm or colour match is claimed.

## Where to find the features

| Location | Behaviour |
| --- | --- |
| Camera → sequence tools → Advanced capture | Select and configure a mode, press Use mode, then operate the main shutter from the viewfinder. First press starts; another press stops. The live view shows mode, frame count, elapsed time and accumulation preview. Normal photo disarms the advanced mode. Choices are remembered; restoring never starts capture. |
| Advanced → High Detail | Capture multiple preserved sources, register them, reject inadequate phase coverage, reconstruct at twice the explicitly selected working dimensions. Choose 1280/2048/3072/full source; memory limits are checked instead of silently reducing quality. Tiled residual rejection protects moving areas using the reference image. This is measured-image reconstruction, not generated detail. |
| Advanced → Live Composite / Live ND | Full captured-source dimensions, linear-light accumulation, new bright contributions / temporal averaging, duration controls, source recipe rendering once, disk-backed checkpoint, bounded preview and recovery. No optical ND or clipped-highlight recovery claim. |
| Advanced → pre-capture / source burst / HDR / night / astro / trails | Configurable bounded rendered preview buffer with duration/FPS/resolution/memory; original-source burst; bracket count/EV and optional fusion; stability qualification and existing alignment/stack tools. Preview buffer is not sensor RAW. |
| Camera → sequence tools → Guided group photo | Same-window viewfinder overlay and normal shutter for both shots; explicit 2048 px (default) or full-source composite; bounded review and sequential lossless source spooling for full-source Save, originals preserved; alignment ghost, photographer swap, bundled person masks, scene checks, retry/retake, ROI/feather review and independent JPEG with source links. Phone acceptance remains pending. See add-me-repair.md and add-me-full-resolution.md. No face identity matching. |
| Camera → sequence tools → Photographic style | Skin-aware tone/warmth/colour, detail strength/radius/threshold, plus WB intent. The same style module and metadata execute in preview, still, edit/export and video paths. Original sources remain editable. |
| Camera top bar → more menu | Ordered quick controls, saved layouts, flash/timer/live/histogram/grid/level/zebra/peaking and independent exposure/focus locks. Histogram label specifies preview-domain data. |
| Settings → creative presets | Search, favourites, save/rename/duplicate/import/export, schema defaults/migration, selected reference thumbnails, creative before/after and intensity. Original inspired Leica Natural/Vivid/Cinematic/Mono and Canon styles remain adjustable. |
| Filters → import | Strict CUBE/PLUT count/dimension/finite/domain/payload validation; exact non-unit domain coordinates are retained through PLUT v5 and every GPU sampling path. Unsupported/malformed inputs report errors. |
| Gallery → frame tools / composites | Explicit full-quality versus bounded working size, face ranking and deliberate detected-face selection, retained originals, independent output metadata. Derivatives link to preserved original RAW redevelopment where available. |

## Focus peaking and camera performance

The peaking toggle now applies in autofocus and Snap focus; hidden AF/Snap gates are removed. Sobel contrast is normalized and sensitivity retuned so normal fine edges can appear. Assists remain preview-only; clean capture/video references precede overlays. The continuous shutter pulse/spinner, thumbnail shimmer/completion flash and viewfinder size animation were removed; actual progress and recording state stay visible.

## Colour and storage correctness

Live/group inputs render each saved source recipe once before integration; the final baked JPEG uses default output recipe metadata, avoiding dropped styles and double application. Linear averaging uses the sRGB transfer explicitly. Neutral style is an exact bypass, including HDR values. Keep Atmosphere uses sensor WB gains anchored to actual auto-WB results with manual-WB override and periodic illumination probes; Neutral Whites uses the camera's normal full auto-WB adaptation. These are original behaviours, not Canon firmware reproduction.

Motion-photo XMP parsing scans bounded JPEG headers instead of allocating the appended video; container offsets/padding are validated and incomplete exports cleaned up. DNG dates preserve captured wall time and only map sensor timestamps when the camera declares a realtime clock.

## Remaining acceptance limits

- No physical CMF Phone 1 is attached. Focus calibration, Camera2 feature availability, sensor colour measurements, WB transitions, moving-scene quality, memory/thermal performance and lifecycle acceptance require device testing.
- Full sensor 2× output can exceed heap/storage; explicit safe working sizes exist and originals are retained. The method rejects insufficient independent subpixel samples rather than pretending a simple resize adds measured detail.
- Person alignment supports translation and rejects changed backgrounds; it does not infer missing bodies, match identities or solve arbitrary perspective changes.
- Existing preview/still sharpening kernels differ; controls and colour transforms share intent, but identical detail pixels and exact preview/capture colour appearance are not claimed.
- Authoritative Canon DPP golden fixtures are missing from the supplied source. That comparison is explicitly skipped; no golden match has been fabricated.

Final Android build/signature checks passed; unit suite:573 passed,7 skipped,0 failed. See build-verification.md for evidence.
