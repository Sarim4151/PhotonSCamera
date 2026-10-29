# Film process controls (PhotonCamera issue #88)

The existing Spektrafilm native film profiles include their stock-specific dye-coupler (DIR) interactions. Their film/print 3D LUT stages and cyan/magenta/yellow negative-density controls remain unchanged; this patch does not claim to port every parameter of the upstream Spektrafilm engine.

In the photo editor, open **Advanced → Effects**. Push/Pull style presets set recipe contrast, exposure compensation, tint, and temperature. They are visual development emulations, not a simulation of altering an exposed negative. The film-process row sets editable spatial effect parameters: grain, bloom, soft-light diffusion, and red halation. Choose Clean, Pro-Mist 1/8 style, Pro-Mist 1/4 style, Black diffusion style, CineBloom style, 35 mm grain, or 16 mm grain, then fine-tune the normal sliders. These effects use the existing photo render and export pipeline, and save with the edited recipe. Filter names describe appearance; the app does not contain the named vendors' optical filter measurements.

This change also keeps color-wheel pointer positions local during dragging. It commits the wheel selection once on release, reducing expensive photo-preview and recipe synchronization work per pointer sample. The same pattern applies to the Lift/Gamma/Gain/Offset wheel. Sliders and taps remain available.

Still outstanding from the literal scope of #88: physically simulated adjustable DIR couplers, grain whose statistical distribution tracks film ISO and format, spectral push/pull development, and all remaining upstream-native controls. Device validation is required for GPU output and interaction smoothness.

Related camera refinements in this revision expose existing HDR merge modes and calibrated-noise controls as presets; they do not add new learned HDR/denoise networks. The video color engine can now be opened from the main video settings. A legacy 4K-specific container rotation has been removed. Verify orientation and mirror behavior on the target front and rear lenses before relying on it for production footage.
