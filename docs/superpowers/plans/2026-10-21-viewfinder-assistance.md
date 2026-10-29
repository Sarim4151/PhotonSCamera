# Viewfinder assistance implementation plan

> For agentic workers: use superpowers:executing-plans to implement task by task after plan review. Native execution in the current session is recommended. Do not spawn agents without a selected delegation workflow.

**Goal:** Deliver configurable manual focus peaking and preview-only exposure zebras without changing saved images or existing colour engines.

**Architecture:** Extend the existing DataStore to ViewModel to Compose to GL surface to LutRenderer path. Keep preview assistance after recording/eye-analysis consumers and before the final screen draw. Reuse the existing peaking shader/pass; add independent zebra controls without requiring manual focus.

**Tech Stack:** Kotlin, Jetpack Compose, Camera2, DataStore and OpenGL ES 3.0.

**Spec:** Approved PhotonCamera_Features_and_Implementation_Plan.docx, sections VF01, VF02, VF05 and camera inspirations. This plan implements the first viewfinder work package, not the entire approved programme.

## Global constraints

- Authoritative source is the extracted PhotonSCamera-main.zip; preserve the original archive.
- Preserve Canon, Leica, Lumix, Hncs and other existing rendering engines.
- Target CMF Phone 1 first; no claim of physical-device validation without device evidence.
- Follow AGENTS.md: localised strings, read GLES compatibility guidance, no incremental installation, no additional unit-test requirement.
- Keep existing peaking enabled default and its current appearance as the migration default.
- Disabled assistance must not add a preview pass; never bake overlays into photos, videos, Motion Photos or exports.
- Build verification is currently blocked by Gradle 8.13 download access; do not claim Kotlin compilation has run.

## Review focus

1. Existing installations without new preferences retain the current red, 0.8-threshold peaking behaviour.
2. Zebra display works with autofocus on and peaking off; changing focus mode does not change zebra preference.
3. NaN, infinities and invalid persisted selections resolve to documented defaults before reaching GL uniforms.
4. Recorded and captured content remains free of overlays, including Motion Photo clips and eye-focus analysis input.
5. Context loss, resize and repeated toggling do not leak FBOs/programs or leave stale uniform values.

## Source map

All paths below are relative to the project root. The shared Java prefix is app/src/main/java/com/hinnka/mycamera/.

- model/PreviewAssistSettings.kt: new bounded settings model and persisted enum values.
- data/UserPreferencesRepository.kt: existing preference data class, keys, reads and writes.
- viewmodel/CameraViewModel.kt: existing peaking flow/setter, plus preview-assist settings updates.
- ui/settings/SettingsScreen.kt: controls adjacent to the current peaking switch.
- ui/camera/CameraScreen.kt: collect and pass settings.
- ui/camera/CameraPreviewGL.kt: pass settings during AndroidView updates.
- ui/camera/CameraGLSurfaceView.kt: hand settings to renderer with the existing GL update convention.
- lut/LutRenderer.kt: uniforms, pass selection, lifecycle and preview-only placement.
- lut/Shaders.kt: existing FRAGMENT_SHADER_FOCUS_PEAKING extension or dedicated assistance shader.
- app/src/main/res/values/strings.xml and supported locale directories: labels and descriptions.
- docs/gles-driver-compatibility.md: read before edits; update only for confirmed driver compatibility findings.

## Task 1 Persisted settings and complete UI wiring

**Interfaces:** Create PeakingColor with stable persisted IDs RED, YELLOW, BLUE, WHITE and explicit RGB values. Create PeakingSensitivity LOW, MEDIUM, HIGH with thresholds 1.2, 0.8, 0.4 respectively. Create PreviewAssistSettings(peakingColor: PeakingColor = RED, peakingSensitivity: PeakingSensitivity = MEDIUM, zebraEnabled: Boolean = false, zebraThresholdPercent: Int = 95). Keep the existing focusPeakingEnabled switch as its current owner. Clamp zebra threshold to 70 through 100. Invalid enum values resolve to defaults.

- [ ] Add the model in model/PreviewAssistSettings.kt with fromPersistedValue helpers for both enums and normalized() for the settings value.
- [ ] Extend UserPreferencesRepository using stable new keys: focus_peaking_color, focus_peaking_sensitivity, zebra_enabled, zebra_threshold_percent. Read missing values with migration defaults; write related settings in one DataStore edit. Never reset other user preferences.
- [ ] Expose previewAssistSettings: Flow<PreviewAssistSettings> and setPreviewAssistSettings(settings: PreviewAssistSettings) from CameraViewModel. Normalise before persistence.
- [ ] Add colour/sensitivity selectors beneath the peaking switch; add a separate zebra switch and threshold selector with 70, 75, 80, 85, 90, 95, 100 percent choices. Show the threshold control only when zebras are enabled. Explain that zebra readings refer to rendered preview brightness and are not a RAW clipping meter.
- [ ] Wire the settings through CameraScreen, CameraPreviewGL and CameraGLSurfaceView.setPreviewAssistSettings(settings) into LutRenderer. Assign an immutable settings snapshot following existing thread-visibility conventions.
- [ ] Add default-language strings and reviewed translations for supported locales; allow normal Android fallback where a translation cannot be verified. Do not invent translations.
- [ ] Verify preference migration, each selector, restart persistence, and an invalid stored enum using a debug device. Verify zebra threshold bounds at the model boundary. Record exact results, including checks not run.
- [ ] Compile with bash ./gradlew compileDefaultDebugKotlin once dependencies are accessible. A download failure is a blocked check, not a code failure.

**Deliverable:** Preferences and controls are fully connected to the renderer; no placeholder UI is shipped separately from Task 2.

## Task 2 Preview assistance rendering

**Consumes:** The settings model and end-to-end wiring from Task 1. Existing focusPeakingEnabled and isAutoFocus determine peaking activation.

**Produces:** A preview-assistance pass with independent peaking and zebra activation and safe GL lifecycle behaviour.

- [ ] Read docs/gles-driver-compatibility.md and the full existing peaking pass, FBO allocation, cleanup, context reset and recording flow.
- [ ] Replace the fixed threshold 0.8 and colour (1.0, 0.1, 0.1) with the selected sensitivity and colour. Preserve current red RGB as the RED value. Other colours are YELLOW (1.0, 1.0, 0.0), BLUE (0.0, 0.5, 1.0), WHITE (1.0, 1.0, 1.0).
- [ ] Extend the existing screen-only pass to take independent peaking/zebra activation uniforms and a normalised zebra threshold. Skip Sobel neighbour sampling when peaking is inactive. Compute zebra luminance from the original input sample using 0.2126, 0.7152, 0.0722 weights; compare against thresholdPercent / 100.0.
- [ ] Use static diagonal black/white stripes based on gl_FragCoord with a 12-pixel period, blended at 0.65 opacity. Zebra highlighting has precedence over peaking where both apply; neither changes alpha. Document this display behaviour.
- [ ] Request the existing offscreen preview route when zebras alone are active. Never require manual focus to enable zebras. When both are inactive, preserve the existing fast route.
- [ ] Keep recording and eye-analysis consumers ahead of assistance. Trace every capture/export call path to verify the assistance pass is never invoked there. Do not reuse overlay-marked textures for persistent output.
- [ ] Explicitly bind all uniforms, samplers and framebuffer state for every draw. Reuse or release the existing FBO correctly on resize and context loss; reset all new locations/program state alongside existing ones.
- [ ] Compile Kotlin and package the default debug variant when available. If a shader compiler or GLES harness exists, validate shader syntax too; report that syntax checks do not prove Mali compatibility.

**Verification scenes:** Flat dark frame (no stripes), flat white frame (stripes), a luminance ramp at 70/95/100 percent thresholds, fine edges at each peaking sensitivity, autofocus with zebras on, and both overlays disabled. Compare saved images and video frames to overlay-disabled captures of a static test input. Verify overlay toggles do not affect recording pixels.

## Task 3 Integration evidence and review

- [ ] Exercise manual/auto focus switching, camera lens switching, app background/resume, display rotation and repeated toggles on CMF Phone 1.
- [ ] Measure preview frame time with assistance off, peaking only, zebras only and both. Record resolution and temperature; use results to decide whether further optimisation is needed.
- [ ] Inspect GL errors and resource counts across repeated resize/context recreation. Fix the cause of any errors rather than silently disabling assistance.
- [ ] Review all changed files against the five Review Focus cases. Do not claim saved-output isolation until those consumers have been traced and tested.
- [ ] Add docs/preview-assistance-validation.md with implemented behaviour, verification commands/results and remaining device checks. Package source changes and an APK only if a verified build succeeds.

## Remaining approved programme

The following remain required but need their own source-level plans after auditing their existing implementations. This plan does not silently remove them.

1. Colour and profiles: IQ01 through IQ07, natural skin, highlight handling, iPhone/Galaxy styles, existing Leica/Canon engine improvements, Canon sharpening and white-balance priorities.
2. Recipes: LR01 through LR05, thumbnails/favourites/search, intensity, comparison, reprocessing, versioned presets and three-recipe bracketing.
3. Remaining viewfinder: magnifier, independent AE/AF placement, quick controls, capability reporting and highlight-weighted metering; validate current histogram/level implementations.
4. Capture: configurable bursts, exposure/focus brackets, focus stacking, intervals, Snap Focus and multiple-exposure improvements.
5. Computational modes: HDR/night/super-resolution improvements, Live Composite, Live ND, star trails and astrophotography.
6. Temporal modes: pre-capture, best-shot selection, Motion Photos and guided group/optional face compositing.
7. Integration: RAW/JPEG preservation, bokeh quality, system-camera integration, resource budgets, localisation, licences and release verification.

## Review outcome

The plan is scoped to a working end-to-end viewfinder change. No product code has been modified. The full approved feature programme remains tracked above. Before implementation, review the concrete settings, stripe behaviour and scope; native execution in this session is recommended because these tasks share the same UI and rendering state.
