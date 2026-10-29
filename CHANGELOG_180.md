# PhotonCamera 180

- Added a paired preview/photo diagnostic command, `tools/preview_photo_diagnostic.py`, that writes a center-crop before/after image and JSON luma/color measurements. It does not automatically calibrate the CMF Phone 1; fixed-exposure, fixed-white-balance pairs are still needed.
- Added a compact photo-viewfinder warning when the ISP preview histogram has at least 1% near-white pixels or the selected lens cannot provide the requested full source resolution. Preview clipping is not necessarily RAW sensor clipping.
- The existing HDR+ inspired RAW burst, engine handoff, Spektrafilm controls, video AE/AWB locks, and volume-key/scopes fixes from build 179 remain present.
- Removed a remaining 4K-specific 90-degree video pixel rotation. The SurfaceTexture matrix and MP4 rotation hint already handle orientation; verify front/rear and portrait/landscape saved clips on the target device.
- See `docs/superpowers/plans/2026-10-27-quality-calibration.md` for quality features that still require implementation and device validation.
