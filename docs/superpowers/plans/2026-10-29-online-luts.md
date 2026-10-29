# Online LUT pack implementation plan

Goal: Bundle 22 creator-published creative LUTs into the existing camera selector, with existing live thumbnails and intensity controls.

Architecture: Use existing PLUT v3 assets and config.json registration; no new renderer or UI. Preserve source grid dimensions and red-fastest ordering, use 16-bit RGB payloads, and declare display sRGB input. These are creative display looks, not camera Log conversion LUTs. Striped Purple does not specify an input profile; sRGB is an integration assumption for its Photoshop-oriented looks, not a calibrated camera match.

Selection: five slay starter looks plus seventeen Striped Purple looks, pinned to source Git object hashes. Retain licences, original copyright headers, provenance, and quantisation reports. No objective worldwide quality ranking is asserted.

- [x] Validate exact source hashes, finite RGB values, domains, and grid counts.
- [x] Convert without resampling, register unique IDs/categories and creator-labelled names, retain licences.
- [x] Check encoded payload against original numerical rows, including endpoints and all entries.
- [x] Repair the existing geometry test harness using a test-only Size implementation; preserve the production policy of rounding down to supported 16-pixel boundaries.
- [x] Build APK, run the complete existing unit suite on GitHub Actions, and verify APK contents and signing certificate.

Physical CMF Phone 1 colour, recording and HDR checks remain necessary. Do not call the APK device-verified.
