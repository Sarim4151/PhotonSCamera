# PhotonCamera 178

- Added photo-editor film-process preset buttons for Pro-Mist 1/8 and 1/4 style, black diffusion style, CineBloom style, 35 mm and 16 mm grain, and Clean. Presets map to the existing editable grain, bloom, soft-light, and halation settings.
- Added visual push/pull development presets from -2 to +2 in the Effects tab, changing recipe contrast, exposure compensation, tint, and temperature.
- Fixed stuttering edit-section color wheels by keeping pointer positions local to the gesture and committing the recipe once after release. Applies to color grading and Lift/Gamma/Gain/Offset wheels.
- Existing native Spektrafilm stock-specific DIR coupler calculations and negative density gains remain available. The full adjustable native engine, ISO-dependent grain statistics, and physical development controls are not implemented; see docs/issue-88-film-process.md.
- Exposed HDR presets for Sabre detail, Spatial natural, and bracketed range, backed by existing RAW merge algorithms and frame settings.
- Exposed RAW noise reduction presets for detail, balanced, and clean, backed by the calibrated noise model and separate luma/chroma controls.
- Promoted the video rendering-engine selector to the main video settings and kept its sheet available in Video mode even when RAW photos are off.
- Removed a 4K landscape-only 270-degree orientation hint that could rotate encoded videos after saving.
- Removed the Live Coach camera overlay, its enable state, and its otherwise-unneeded scene-detection trigger. Motion-priority and Smart capture internals continue to use scene detection when active.
