# Build 189

- Restore the complete recovered source, matching colour settings, resources, bundled AI models and seven missing DCP assets on the build branch.
- Bundle 22 online creative LUTs: five slay starter looks (CC BY 4.0) and seventeen Striped Purple looks (MIT). Creator names remain visible. Full licences, source hashes and original copyright headers are in assets/licenses. Source manifests and a reproducible conversion tool accompany the pack.
- Preserve original LUT grid dimensions (16, 32 or 33) and red-fastest ordering, with 16-bit output quantisation. Added payload is 3,904,878 bytes. These are display-photo creative looks, not camera-specific Log transforms. Striped Purple input sRGB is an explicit integration assumption, not an upstream calibration claim.
- Register the pack in the existing LUT selector, categories, previews and intensity controls. Existing live preview and export paths load the same assets. No paywall flag is set on the new looks.
- Fix the existing video-geometry JVM harness: Android Size getters/equals were unavailable in the mock Android jar. A test-only immutable Size implementation enables the assertions. Expected dimensions now match the existing round-down-to-16 encoder policy; production recording dimensions were not changed to satisfy tests.

Build/test results are recorded after GitHub Actions completes. No CMF Phone 1 physical camera, 4K orientation, HDR gainmap or photographic quality verification has been performed for this build. Continuous Bayer RAW video and RAW-derived DirectLog remain outstanding.
