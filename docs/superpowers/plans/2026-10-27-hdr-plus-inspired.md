# HDR+ inspired RAW burst and independent Pro switch

The current Pro mode coerces HDR+ on and uses either Sabre or Spatial RAW fusion. This change separates the capture switch from the selected color rendering engine. A new HDR+ inspired preset captures a same-exposure burst (seven frames when first selected), uses the existing sharp-reference selector and motion-aware Spatial RAW merger, then feeds the merged linear image into the usual selected rendering engine. It does not embed or claim Google's proprietary GCam implementation.

1. Extend `MgcRawMaxMode` with `HDR_PLUS_INSPIRED`, no bracket exposures, RGB Spatial output. Test mapping and bracket support.
2. Stop coercing `useRawMax` to the RAW shooting flag in preference loading and feature updates. Add an explicit Pro-mode on/off control backed by `CameraFeatureUpdate` and existing DataStore storage. Ensure disabling leaves single-frame RAW capture and its selected engine active.
3. Expose the HDR+ inspired preset in Professional settings and initialize seven frames while allowing later frame-count adjustment. Record release limitations, especially sensor CFA restrictions and device validation.
4. Compile APK and run unit tests; verify version and signing identity, then package source and APK.

The larger image-quality roadmap (preview calibration, motion-adaptive capture scheduling, temporal video denoise, 10-bit HDR, and lens transitions) needs separate device-tested changes. Existing exposure/WB locks, sensor noise profiles, motion selection, and video color pipeline remain available.
