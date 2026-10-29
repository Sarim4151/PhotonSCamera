# Remaining camera tools design

The user wants the previously listed missing features connected to the app and another complete source ZIP, compiling locally. Standing instructions explicitly authorize the full work without further approval/continuation requests. This extends an existing Camera2/Compose app; preserve RAW/JPEG sources and existing rendering engines.

Capture: add a bounded pre-shutter preview ring and continuous preview-resolution linear-average/max live accumulators. Expose progress, preview, save/stop, cancellation, resolution and tripod requirements. Reset on camera/mode/orientation changes. Do not label preview buffers full-resolution RAW. Add source-backed photo sequences for night/astro/HDR/trails, save real source IDs before stacking, and connect optional focus-bracket auto-merge after focus restoration.

Processing: use explicit translation/scale/rotation confidence, frame rejection and reference fallback; use robust linear-light fusion with local moving-region rejection. Astro registration permits modest rotation; star trails deliberately retain tripod motion. No equatorial mount control, guaranteed deep-sky tracking or new detail without measurements.

Gallery: select three actual saved/built-in presets, independently render preserved source through their LUT/recipe/effects. Add a two-photo guided region composite editor with donor translation, feather and preview. Add on-device face classification for open-eye/smile scoring and region assistance, with missing confidence neutral and no identity claims. Human chooses donor/region before saving independent output.

Colour: add paired corresponding colour-patch calibration with explicit sRGB input/output, least-squares fit, conditioning and held-out error reporting. Export reusable 33-cube via document picker. Exact manufacturer reproduction cannot be asserted without representative reference pairs and validation.

All UI labels use resources, expensive work runs off main, jobs own copies and cancel safely, publication checks nonempty output, partial successful outputs survive cancellation. Android build verification remains conditional on available Gradle/SDK; report actual results and remaining scientific/device limitations.
