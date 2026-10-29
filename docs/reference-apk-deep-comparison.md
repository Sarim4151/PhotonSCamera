# Deep APK/source comparison

Scope: decoded reference APK (9,412 smali files, merged resources and packaged assets) versus current PhotonSCamera-work source. Read-only static inspection; no production edits, no device execution. Resource-only candidates are explicitly weaker than traced executable paths. Obfuscated identifiers below are from this exact APK and are not stable source names.

## Findings that correct the earlier inventory

### Subject/person support is three different features

1. **Existing current app: face/eye AF.** `preview/PreviewEyeFocusProcessor.kt` has tracking generation/reset and face-target processing; `viewmodel/CameraViewModel.kt:2140–2190` subscribes to processor state and resets AF on loss/disable/camera changes; `ml/BlazeFaceDetector.kt` names `blaze_face_full_range.tflite`. This must not be presented as absent.
2. **Reference addition: offline general subject AF and naming.** `smali/nz4.smali:3739–3995` initializes MediaPipe ObjectDetector using `efficientdet_lite0_int8.tflite`, including actual initialization and error branches. `ez4.smali:195–552` selects the object detector path, passes its ObjectDetectorResult into `nz4.B(...)`, propagates target results and invokes the naming coordinator `a96`. `a96` constructs `y86`; `y86.smali:891` loads `subject_efficientnet_lite0.tflite` into ImageClassifier. Its executable categories include person, bird, cat, dog, cow, elephant, etc.; result handling logs confidence, margin, confirmed/uncertain and falls back to generic labels if unavailable. Reference resources include Subject AF, subject type/identifying/uncertain/unavailable states. The current source has neither those two model assets nor ObjectDetector-based subject processing. This is a genuine missing subsystem, not merely another name for existing eye AF. Detailed species labeling does not itself demonstrate identity tracking.
3. **Reference addition: video Follow Person / auto-framing.** Stronger than a label: `q70.smali:1550–1554` binds `nextVideoFollowSubject`; `q30.run()` mutates `lb0` video-follow state, updates camera requests through `y40.z(...)` and submits through `y40.e3()`. Capture callback `x40.onCaptureCompleted` calls `y40.h(controller, TotalCaptureResult)` and disables follow on failure. `y40.h` reads `CaptureResult.STATISTICS_FACES` at 10261, checks face scores, uses face bounds throughout 10487–11347, applies 1.5f at 11307, and emits normalized `jo4(FFFF)` crop state at 11724. A rendering shader in `dq0` consumes `uCropRect`. Thus this is hardware-face-driven crop following, distinct from the new offline detector. Resources say next person, holding frame on loss, lens limited, 1.5× crop, unavailable when face data absent. Current source search finds no video-follow state or equivalent. Runtime smoothness, identity retention across occlusion, saved-video integration under every recording path remain unverified; do not call this full-body tracking or guaranteed recognition.

### Adobe Curve is already implemented; additional looks are mostly DCP content

Current `raw/AdobeCurveToneAlgorithm.kt` includes RGB max/mid/min curve interpolation, overrange handling, profile pipeline, resource binding and engine shader; `RawRenderingEngine.kt` has AdobeCurve. Reference `d6` binds its resources and `e6` contains the actual Adobe curve shader. This is not a missing engine.

Both expose exactly the same three named profile tone choices: Default, Embedded, OPPO Master (`RawProfileToneMapMode.kt`, `RawToneMappingGl.kt`, `DngProfileToneCurve.kt`; reference strings 1555–1559). No static evidence for an additional selectable set of Adobe tone-mode enum variants was found. The extra Adobe-style Color/Neutral/Vivid/Portrait/Landscape/Monochrome assets are **different DCP profiles**, potentially containing different tone curves and look tables, not six independently proven new tone algorithms. Reference GLSL includes exposure/profile gain table and overrange code, but current also has substantial profile processing; shader text alone does not establish a missing feature.

### DCP import and per-lens profile handling already exist

Current chain: `CameraViewModel.importRawDcp(s)` → `CustomImportManager.importDcp` (516+) → custom_dcps file/config → contentRepository.availableDcps → settings/preset selection → `UserPreferencesRepository.rawDcpIdForLens` (310+) / unified and lens override preferences → `DcpProfileParser.resolveRenderPlan` → native parse bridge and profile render plan → RAW tone pass. Deletion removes stored preferences; preset package DCP import exists. `DcpProfile.kt` parses matrix1/2, forward matrices, illuminants, hue-sat deltas1/2, look table, tone curve, baseline exposure, black render and overrange; it caches by file content and interpolates by metadata. Reference `u71` has real import/delete/preset-package routines, not just resources. Therefore import, deletion, unified/per-lens DCP and embedded/profile curve support are already present and should not be proposed wholesale again.

The genuine gap is the bundled library: reference has 27 DCP assets, current has 10, all overlapping (exact inventory below). New assets comprise six Adobe-style looks, eight Fujifilm X-Pro3 looks, Sony ST, OnePlus 12 and Apple iPhone18,2. Bundling them is content expansion, requiring provenance/license and actual parse/render evaluation; filenames do not certify faithful camera matching. Existing current profiles include camera brands absent from the reference engine menu, so replacing current libraries would lose capabilities.

### Rendering engines: compare executable enum, not branded descriptions

Reference `g95` instantiates AdobeCurve, HncsCcm, HncsLut, AgX, Spektrafilm, DarktableSigmoid, DarktableFilmic, Fotufilm (HNCS has two color branches). Current `RawRenderingEngine.kt` already has Adobe/AgX/Sigmoid/Filmic/Spektrafilm/Fotufilm/HNCS, plus Lumix/Canon/Leica implementations and assets. Thus reference names are not evidence that these engines need porting. The meaningful candidate is separately selectable HNCS original matrix versus 2D LUT. Current `HncsToneAlgorithm.kt` already implements a 2D color map (`uHncsColorMapTexture`, grid/size and texel interpolation); `RawRenderingEngine.fromPersistedName` maps both legacy HncsCcm/HncsLut names to Hncs. Thus inspect whether the original-CCM branch is actually selectable before proposing additional HNCS work. Native binary existence is not proof of equal algorithms or assets. Reference has only 52 packaged assets versus current 282; reference film support may involve provisioning rather than all profiles bundled. It is unsafe to describe the APK as a superset.

## Broader candidate inventory

This section is a systematic resource delta; presence establishes UI vocabulary, not operation. Every row needs entry-point/callback/output tracing and device validation before a port is called complete.

| Category | Reference candidates absent from current resource names | Assessment |
|---|---|---|
| Focus | Subject AF/type naming, video Follow Person, focus pull start/end/duration/run, multi-area AF, subject depth stripes | Subject detector/naming and Follow Person have executable evidence above; remaining tools need exact path review. |
| Capture | separate exposure-bracket JPEGs, Photo Sphere with sensor targets/undo/export, Quick Shot, group selfie, macro lens chooser, consolidated modes | `fz1`/`jz1` carry ExposureBracket/PhotonExposureBracket; `p46` PhotoSphere; labels alone do not prove stitch quality. Macro/group/scene labels may configure existing capture. |
| Video | RAW-derived 10-bit Log, native HLG10, CBR/VBR, fixed FPS/live measured FPS, background recording, Horizon Steady, aspect choices | `pa5` has RAW10 GLES3.1 allocation/shader/FP16 pipeline errors; `az6` validates Main10 and 10-bit SPS and discards invalid output. Current has VideoRecorder and VideoColorMetadata: inspect each current path before asserting all absent. |
| JPEG/processing | baseline JPG/Phantom LUT+recipe, per-camera detail/color/shading settings, max output scale, hardware tone-map preview/capture correction, HDRNet/local-tone selector | Current already has RAW defaults, denoise, multiframe, JPEG4:4:4 and LUT recipes. Compare preference semantics and saved output, not strings. |
| Creative | image overlay import/placement/strength, procedural dust/scratches/leak/gradient; directional diffusion; live color controls | Need output-path verification, including direct Max/RAW exclusions stated in resources. |
| Recipes | Group Portrait, Landscape Detail, Product Detail, Redmi Night/Astro, S26 Night, photo scenes, AUTO/PRO badges | `CameraPreset.smali` actually instantiates new built-in IDs (1836+,2164+,2946+,3087+,3176+). These are starting settings, not guaranteed optics/night quality. Astro explicitly requires RAW/manual limits; S26 recipe says 12MP YUV five-frame, not Samsung algorithm. |
| Guidance | offline shooting assistant/capture coach, framing guides/lens equivalents, film reference catalog | Mostly guidance/reference. `films.json` exists, but library cards explicitly do not activate rendering. Avoid counting these as processing engines. |
| Gallery | Best Moment frame extraction, Motion Freeze, editor preparation | Video-resolution extraction is not full-resolution still burst. Current LivePhoto exists; compare actual UI/export differences. |
| Operations | scoped backup/restore, crash evidence export, explicit encoder/session/resolution failures | Candidate workflow improvements; backup/resource support already substantial in current. |

## Recommended priority

1. Subject detector + category naming and video face-follow are the clearest missing behavior; design separately and test lens capability, camera-coordinate mapping, reacquisition/loss, AF and crop/recording outputs.
2. Expand DCP content with verified parse/render results and appropriate rights. Preserve current import, per-lens overrides and extra engines; don't reimplement them.
3. Trace focus pull, HNCS branch selection and baseline/per-lens processing controls to determine the smallest real deltas.
4. Treat new modes, scenes and presets as configuration/UI work only after tracing capture execution. Validate bracket exposures and actual Photo Sphere output before claiming parity.
5. Handle RAW10, native HLG, horizon/background recording as independent hardware-dependent projects; static presence cannot establish phone compatibility.

## Exhaustive asset comparison

Reference: 52 files; current: 282 files.

### Reference-only asset paths

- `dcp/Adobe-style Color.dcp`
- `dcp/Adobe-style Landscape.dcp`
- `dcp/Adobe-style Monochrome.dcp`
- `dcp/Adobe-style Neutral.dcp`
- `dcp/Adobe-style Portrait.dcp`
- `dcp/Adobe-style Vivid.dcp`
- `dcp/Apple iPhone18,2 back camera Adobe Standard.dcp`
- `dcp/Fujifilm X-Pro3 Camera Astia.dcp`
- `dcp/Fujifilm X-Pro3 Camera Classic Chrome.dcp`
- `dcp/Fujifilm X-Pro3 Camera Eterna.dcp`
- `dcp/Fujifilm X-Pro3 Camera Pro Neg Hi.dcp`
- `dcp/Fujifilm X-Pro3 Camera Pro Neg Std.dcp`
- `dcp/Fujifilm X-Pro3 Camera Provia.dcp`
- `dcp/Fujifilm X-Pro3 Camera Reala Ace.dcp`
- `dcp/Fujifilm X-Pro3 Camera Velvia.dcp`
- `dcp/OnePlus 12 back camera 6.06mm f1.6 Adobe Standard.dcp`
- `dcp/Sony ILCE-7M4 Camera ST.dcp`
- `dexopt/baseline.prof`
- `dexopt/baseline.profm`
- `efficientdet_lite0_int8.tflite`
- `fonts/Inter-OFL.txt`
- `subject_efficientnet_lite0.tflite`

### Shared asset paths whose bytes differ


## Exhaustive app resource-name delta

Excluded Android/Compose/ExoPlayer dependency strings. These are discovery candidates, not a feature-completion list.

- `active_lens_front`: Front
- `active_lens_macro`: Macro
- `active_lens_main`: Main
- `active_lens_status`: %1$s · %2$s · Camera %3$s
- `active_lens_telephoto`: Telephoto
- `active_lens_ultrawide`: Ultra-wide
- `active_lens_virtual`: Virtual
- `adjustment_reset`: Reset
- `adjustment_reset_all`: Reset all
- `assistant_action`: Follow the subject; try 1/500s or faster in good light, and leave space ahead
- `assistant_action_label`: Action guidance
- `assistant_description`: Local guidance from camera exposure, focus and existing face detection. No cloud, downloads or generative AI. Suggestions never change settings automatically.
- `assistant_landscape`: Use foreground, middle and distance; focus on a meaningful detail and protect bright skies
- `assistant_landscape_label`: Landscape guidance
- `assistant_open_gate`: Open Gate: leave headroom for later crops; camera streams may not expose the full sensor
- `assistant_video_light`: High ISO: add light before raising fps; keep important highlights protected
- `assistant_video_movement`: Plan a steady shot, move slowly, and hold before and after each take
- `assistant_video_shutter`: Motion blur: try shutter near 1/(2 × fps), unless you want a crisp action look
- `assistant_video_support`: Telephoto without stabilization: use a tripod or support
- `assistant_xpan`: XPan 65:24: place a clear subject off-centre; keep the horizon level and check both edges
- `astro_raw_unavailable`: Astro requires RAW output on the selected camera. Select a RAW-capable lens.
- `background_recording_channel`: Video recording
- `background_recording_description`: Recording continues while you use other apps. Tap to return.
- `background_recording_failed`: Background recording could not start. Keep the camera open and check camera and microphone permissions.
- `background_recording_notification_required`: Allow notifications to show the recording indicator and Stop control while using other apps.
- `background_recording_stop`: Stop and save
- `background_recording_title`: Camera is recording video
- `backup_choose_export`: Choose what to back up
- `backup_choose_restore`: Choose what to restore
- `backup_scope_custom_luts`: Custom and imported LUT files
- `backup_scope_custom_luts_note`: Your imported or duplicated LUT library and names. Select LUT adjustments too to preserve your edits.
- `backup_scope_lut_adjustments`: LUT adjustments
- `backup_scope_lut_adjustments_note`: Saved color recipes, LUT strength, effects, categories and favorites. Include custom LUT files to move your own looks to another phone.
- `backup_scope_settings`: App settings and resources
- `backup_scope_settings_note`: Camera preferences, presets, custom frames, fonts, logos and compatible sensor profiles.
- `backup_selection_export_note`: Select any combination. Built-in LUT files and photos are not copied.
- `backup_selection_restore_note`: Only selected groups are restored. Matching settings and library lists are replaced by the backup, not merged. Unselected groups stay unchanged. Restart the app afterward.
- `best_moment_frame`: Frame %1$d
- `best_moment_recommended`: Recommended
- `best_moment_save`: Save frame
- `best_moment_save_failed`: Could not save the selected frame
- `best_moment_saved`: Frame saved to Pictures/PhotonCamera
- `best_moment_title`: Best Moment
- `best_moment_unavailable`: No video frames are available for this Live Photo.
- `best_moment_video_limit`: Choose a moment from this Live Photo. Saved frames use video resolution, not the full-resolution still.
- `bracket_camera_failed`: Could not open the bracket camera.
- `bracket_capture`: Capture bracket
- `bracket_capture_failed`: A bracket frame failed. Already saved frames remain in the gallery.
- `bracket_count`: Frames
- `bracket_description`: Capture separate JPEGs at different real exposure settings. Keep the phone steady; moving subjects may not align later. No HDR merge is applied.
- `bracket_ev_spacing`: EV spacing: %1$.1f stops
- `bracket_manual_unavailable`: This camera did not provide manual exposure values for bracketing.
- `bracket_permission`: Camera permission is required.
- `bracket_range_failed`: These EV settings exceed this camera’s exposure range. Use fewer frames or smaller spacing.
- `bracket_save_failed`: A bracket frame could not be saved. Already saved frames remain in the gallery.
- `bracket_saved`: Saved %1$d separate bracket photos to Pictures/PhotonCamera.
- `bracket_size`: Camera JPEG: %1$d × %2$d
- `bracket_title`: Exposure Bracket
- `bracket_waiting`: Opening camera…
- `build_customizer_name`: Dinesh
- `burst_hold_hint`: Hold shutter for burst
- `camera_creative_effects`: Effects
- `camera_modes_title`: Modes
- `capture_coach_brace_shutter`: Slow shutter · brace the camera or add light
- `capture_coach_fixed_front_focus`: Front lens is fixed-focus · move to a clear selfie distance
- `capture_coach_focusing`: Hold steady · focusing
- `capture_coach_group_depth`: Group · move faces to a similar distance from the lens
- `capture_coach_group_edge`: Group · move everyone inside the frame
- `capture_coach_group_plane`: Group · keep faces at similar distance for sharp eyes
- `capture_coach_highlights`: Bright areas clipping · lower exposure
- `capture_coach_low_light`: Low light · hold steady or add light
- `capture_coach_natural_face`: Natural light · face a window and keep eyes in soft light
- `capture_coach_product_detail`: Product · tap the important detail to set focus
- `capture_coach_product_diffuse`: Product · use diffused side light to reveal detail
- `capture_coach_refocus`: Focus missed · tap the subject again
- `capture_coach_shutter_light_limit`: Auto ISO near its limit · add light or slow the shutter
- `capture_coach_steady_zoom`: High zoom · brace the camera
- `capture_coach_studio_highlight`: Studio highlights clipped · dim or diffuse the key light
- `capture_coach_studio_key`: Studio · place soft key light 45° from the subject
- `capture_coach_toggle`: Shooting assistant
- `capture_coach_tripod_shutter`: Long exposure · use a tripod or add light
- `capture_guide_adaptive_auto`: Adaptive exposure and contrast
- `capture_guide_auto`: Auto scene
- `capture_guide_natural`: Natural light
- `capture_guide_product`: Product shoot
- `capture_guide_studio`: Studio lighting
- `capture_guide_title`: Offline shooting assistant
- `capture_mode_quick_shot`: Quick Shot
- `capture_processing_busy`: Finishing the previous photos. Please wait before taking another shot.
- `capture_resolution_failed`: The full-resolution photo could not be saved. No smaller preview was substituted. Please export the app log for diagnosis.
- `capture_resolution_not_ready`: The selected photo resolution is not ready or was rejected by the camera. Choose an available resolution and try again.
- `close_drawer`: Close navigation menu
- `close_sheet`: Close sheet
- `composition_lens_title`: Composition and Lens Presets
- `composition_panel_collapse`: Collapse composition and lens presets
- `composition_panel_expand`: Expand composition and lens presets
- `composition_panel_grid`: Show guide
- `composition_panel_guides`: Guides
- `composition_panel_info`: Show or hide framing information
- `composition_panel_lenses`: Lens
- `composition_panel_title`: Framing
- `crash_diagnostics_description`: Export local crash evidence without opening the camera. Includes available Android process-exit records, native traces and the last saved Java crash. Sharing is your choice; nothing is uploaded by this tool.
- `crash_diagnostics_export`: Export crash evidence
- `crash_diagnostics_failed`: Could not export crash evidence. Try again or use Android’s bug report.
- `crash_diagnostics_title`: Diagnostics
- `default_error_message`: Invalid input
- `default_popup_window_title`: Pop-Up Window
- `depth_model_v2`: Depth Anything V2
- `depth_model_v3`: Depth Anything V3
- `depth_model_version_title`: Depth model version
- `diffusion_angle_short`: Angle
- `diffusion_directional_bloom`: Directional
- `diffusion_off`: Off
- `diffusion_title`: Diffusion
- `film_effects_description`: Choose photo film rendering or live photo/video LUTs.
- `film_effects_live`: Live photo and video looks
- `film_effects_live_help`: Open the camera LUT selector and choose a look. This preserves your current camera mode. Native HLG and RAW-derived recording paths do not apply these creative LUTs. Choosing a reference-library card does not create a LUT.
- `film_effects_open_live`: Open camera LUT selector
- `film_effects_open_photo`: Open photo film controls
- `film_effects_photo`: Fotufilm photo rendering
- `film_effects_photo_help`: Open RAW development settings, choose Fotufilm under Rendering Engine, then choose Film. Shoot RAW/RAWmax for RAW development; ordinary JPEG film processing needs Natural Light enabled in Color and HDR. Available stocks are separate from the reference catalog. Fotufilm develops supported SDR still photos after capture; the viewfinder uses standard rendering. It is not a video or HDR film effect.
- `film_effects_spectral`: Spektrafilm photo rendering
- `film_effects_spectral_help`: Open RAW development settings and choose Spektrafilm under Rendering Engine, then select a film. Negative films use a print paper; slide films ignore print selection. For ordinary JPEG processing enable Natural Light in Color and HDR. Film development is applied to supported still photos, not a live film viewfinder or video effect.
- `film_effects_title`: Film Effects
- `film_library_all`: All makers
- `film_library_count`: %1$d of %2$d reference films
- `film_library_empty`: No matching films. Change the search or maker filter.
- `film_library_retry`: Film references could not load. Tap to retry.
- `film_library_search`: Search name, ISO, type or maker
- `film_reference_description`: Film facts and example photos. These cards do not activate camera effects.
- `film_reference_notice`: Reference only — viewing a film or saving an example does not select its rendering effect. Use Film Effects for available camera looks.
- `film_reference_title`: Film Reference Library
- `flash_assist_not_ready`: Flash assistance could not stabilize. Hold the camera steady and retry, add light, or turn flash off.
- `focus_assist_blur`: Subject blur f/2.8
- `focus_assist_depth`: Depth boost
- `focus_assist_detection_only`: Detection highlights are active. This lens does not expose target autofocus; lens focus remains camera-controlled.
- `focus_assist_eye`: Eye AF
- `focus_assist_face`: Face AF
- `focus_assist_help`: Cyan boxes: detected faces. Green edges: manual-focus detail, not a focus guarantee. Zebra: bright clipping. Depth boost sets hyperfocal focus; subject blur is applied to saved photos using the depth model. Tap the person or product to choose focus.
- `focus_assist_multi`: Multi-area AF
- `focus_assist_no_point_af`: This lens does not expose point AF to other camera apps. Face detection can guide framing, but cannot move a fixed-focus lens.
- `focus_assist_peaking`: Peaking boost
- `focus_assist_subject_af`: Subject AF
- `focus_assist_subject_detected`: Subject AF · %1$s
- `focus_assist_subject_guide`: Subject depth stripes
- `focus_assist_tap`: Tap / auto
- `focus_assist_zebra`: Zebra 95%
- `focus_detector_detected`: Target detected · cyan markers show detection
- `focus_detector_failed`: Detection could not start. Restart the app and share the Photon log if this continues.
- `focus_detector_paused`: Detection paused while manual or hyperfocal focus is active.
- `focus_detector_searching`: Searching for a target…
- `focus_panel_help`: Help
- `focus_panel_less`: Less −
- `focus_panel_title`: Focus
- `focus_panel_tools`: Tools +
- `focus_pull_description`: Use the focus slider below to choose each distance, then save Start and End. Run smoothly moves the lens between them. Closing this panel or switching cameras stops the pull.
- `focus_pull_duration`: Duration · %1$.1f s
- `focus_pull_end_saved`: End saved
- `focus_pull_run`: Run pull
- `focus_pull_set_end`: Set End
- `focus_pull_set_start`: Set Start
- `focus_pull_start_saved`: Start saved
- `focus_pull_stop`: Stop
- `focus_pull_unavailable`: This lens does not expose adjustable manual focus. Peaking is still available.
- `fotufilm_credits`: Fotufilm engine and Photon Android port: Apache 2.0. Film profiles: CC BY-SA 4.0. Original authors and license notices are included in assets/fotufilm.
- `fotufilm_experimental_notice`: Experimental Fotufilm still-photo SDR rendering. The viewfinder uses standard rendering; film development is applied during photo processing. First use may take longer. Video and HDR film output are not supported.
- `fotufilm_invalid_document`: Invalid film settings. Tap to reset Fotufilm settings.
- `fotufilm_load_failed`: Fotufilm could not load. Tap to retry; existing photo originals are unchanged.
- `fotufilm_loading`: Preparing Fotufilm profiles…
- `gallery_preparing_editor`: Preparing photo for editing…
- `group_selfie_short`: Group
- `group_selfie_title`: Group Selfie
- `group_selfie_unavailable`: No front camera is available to the app
- `guide_circle`: Circle
- `guide_diagonal`: Diagonals
- `guide_golden`: Golden ratio
- `guide_horizon`: Horizon
- `guide_quadrant`: Quadrant
- `guide_radiating`: Radiating
- `guide_side_v`: Side V
- `guide_thirds`: Thirds
- `guide_triangle`: Triangle
- `in_progress`: In progress
- `indeterminate`: Partially checked
- `lens_details`: Details
- `lens_equivalent_mm`: %1$d mm eq.
- `lens_landscape`: Landscape
- `lens_portrait`: Portrait
- `lens_presets_hint`: 35 mm-equivalent framing targets, not added optical lenses. Available cameras and zoom limits determine actual framing; digital crops can lose detail. Details does not enable macro hardware. Aperture is controlled separately.
- `lens_processing_apply`: Apply to this lens
- `lens_processing_camera`: %1$s · Camera %2$s · %3$.0f mm
- `lens_processing_front`: Front
- `lens_processing_hardware_edge`: Hardware sharpening
- `lens_processing_help`: Photo mode only. Apply saves an override for this lens; other lenses keep their settings. Unsupported hardware modes use the existing supported fallback. RAW color/noise calibration remains available in RAW settings. Color adjustments appear in the photo preview and are saved with the photo, not written into sensor RAW samples.
- `lens_processing_inherit`: Reset this lens to global settings
- `lens_processing_max_chroma`: RAWmax chroma denoise
- `lens_processing_max_luma`: RAWmax luminance denoise
- `lens_processing_max_sharp`: RAWmax sharpening
- `lens_processing_mode_value`: %1$s: %2$s
- `lens_processing_no_camera`: Open the camera first to discover available lenses.
- `lens_processing_raw_chroma`: RAW chroma denoise
- `lens_processing_raw_luma`: RAW luminance denoise
- `lens_processing_raw_sharp`: RAW sharpening
- `lens_processing_rear`: Rear
- `lens_processing_saturation`: Color saturation multiplier
- `lens_processing_selected`: Camera %1$s
- `lens_processing_shading`: RAW lens-shading correction
- `lens_processing_slider_value`: %1$s: %2$.2f
- `lens_processing_summary`: Separate detail, color and RAW shading settings for each lens.
- `lens_processing_tint`: Color tint offset
- `lens_processing_title`: Per-camera photo processing
- `lens_processing_warmth`: Color warmth offset
- `lens_sports`: Sports
- `lens_wedding`: Wedding
- `live_auto_enhance`: Live Auto Enhance
- `live_color_axis_color`: Color
- `live_color_axis_tint`: Tint
- `live_color_axis_warmth`: Warmth
- `live_color_natural`: Natural
- `live_color_title`: Live Color
- `live_photo_still_only`: Photo saved. Motion was unavailable for this shot.
- `live_soft_light`: Live Soft Light
- `macro_choose_lens`: Choose a lens and move in until the subject is sharp. The minimum focus distance depends on the phone.
- `macro_dedicated`: Dedicated macro lens
- `macro_lens_unavailable`: A dedicated macro lens is not exposed to this app. You can try close-up focus on another lens.
- `macro_main`: Main camera close-up
- `macro_mode_title`: Macro
- `macro_ultrawide`: Ultra-wide close-up
- `modes_desc_astro`: Night-sky capture preset
- `modes_desc_bracket`: Capture different exposures
- `modes_desc_guide`: Live composition and shooting guidance
- `modes_desc_hdr`: Toggle bracketed JPGmax HDR capture
- `modes_desc_macro`: Choose a lens for close-up detail
- `modes_desc_motion`: Toggle the short clip saved with a photo
- `modes_desc_night`: A starting point for darker scenes
- `modes_desc_panorama`: Capture a wider scene
- `modes_desc_scenes`: Portraits, street and creative looks
- `modes_desc_selfie`: Open the group selfie setup
- `modes_desc_vlog`: Choose a camera for video
- `modes_group_guides_video`: Guides & video
- `modes_group_low_light`: Low light
- `modes_group_photo`: Photo
- `modes_group_tools`: Capture tools
- `modes_hdr_title`: Extreme HDR
- `modes_motion_title`: Motion Freeze clip
- `modes_sheet_busy`: Finish capturing or recording before changing modes.
- `modes_sheet_subtitle`: Choose how you want to shoot
- `modes_state_active`: Active
- `modes_state_off`: Off
- `modes_state_on`: On
- `motion_freeze_save`: Motion Freeze
- `motion_freeze_saved`: Motion Freeze photo saved at video resolution
- `natural_light_warning_confirm`: Continue
- `natural_light_warning_message`: Natural Light changes the system tone mapping path so preview and capture can use a more linear color pipeline. Some Camera2 implementations may show abnormal preview color, incorrect brightness, or abnormal captured images. If this happens, open Settings > Color and HDR and enable the preview or capture tone mapping fix as needed.
- `natural_light_warning_title`: Enable Natural Light
- `navigation_menu`: Navigation menu
- `not_selected`: Not selected
- `panorama_alignment_failed`: Photos did not align well enough; try more overlap and a level sweep
- `panorama_bad_input`: Choose 2–5 landscape photos with similar framing
- `panorama_capture`: Capture
- `panorama_capture_failed`: Camera capture failed. Check camera permission and try again.
- `panorama_finish`: Finish
- `panorama_frame_count`: Frames: %1$d / 5
- `panorama_input_resolution`: Camera frames: %1$d × %2$d; saved panorama resolution depends on overlap and available memory.
- `panorama_live_instructions`: Hold the phone in landscape. Tap Start, then sweep slowly in either direction without reversing. Rotate around the camera lens and keep the horizon level. Pause and tap Finish after at least two frames.
- `panorama_memory_failed`: Panorama needs too much memory on this device
- `panorama_pause`: Pause
- `panorama_restart`: Restart
- `panorama_save_failed`: Could not save the panorama
- `panorama_saved`: Panorama saved to Pictures/PhotonCamera
- `panorama_short_description`: Stitch overlapping landscape photos
- `panorama_slow_down`: Slow down or pause briefly for a sharper frame.
- `panorama_start`: Start sweep
- `panorama_sweep_guide`: Sweep slowly and keep the horizon level
- `panorama_sweep_progress`: Captured %1$d / 5 frames
- `panorama_title`: Panorama
- `peaking_cyan`: Cyan
- `peaking_green`: Green
- `peaking_high`: High
- `peaking_low`: Low
- `peaking_medium`: Medium
- `peaking_preview_notice`: Preview only: not saved in the recording. Peaking indicates edge contrast, not guaranteed focus; low-light noise may still produce highlights.
- `peaking_yellow`: Yellow
- `photo_resolution_comp_12_description`: Computational 12 MP, fast three-frame capture from a supported camera output; actual saved dimensions are shown in photo details
- `photo_resolution_comp_12_short`: C12
- `photo_resolution_comp_16_description`: Computational 16 MP, multi-frame super-resolution from the front camera’s native output; 4:3
- `photo_resolution_comp_16_short`: C16
- `photo_resolution_comp_24_description`: Computational 24 MP from aligned native frames; not native 24 MP sensor detail. Ultrawide uses 3–5 frames and needs at least 6 MP after the 4:3 source crop. Unavailable on memory-constrained devices. Best with a steady camera and still subjects; source support and processing limits apply.
- `photo_resolution_comp_24_short`: C24
- `photo_resolution_comp_50_description`: Computational 50 MP, multi-frame super-resolution from the camera’s native output; 4:3
- `photo_resolution_comp_50_short`: C50
- `photo_resolution_native_description`: Native photo resolution
- `photo_scenes`: Scenes
- `photo_scenes_intro`: Professional-inspired starting points, adapted for a fixed-aperture phone. Camera2 keeps exposure, ISO, shutter and white balance automatic; scene metering and EV shape the look. No preset guarantees sharp action in low light.
- `photo_sphere_align`: Turn toward the amber dot
- `photo_sphere_capture_failed`: Capture failed. Keep the phone steady and retry.
- `photo_sphere_continue`: Continue capturing
- `photo_sphere_coverage_failed`: Coverage has gaps. Restart and hold each target centered before capture.
- `photo_sphere_discard`: Discard and leave
- `photo_sphere_discard_description`: Unsaved shots will be removed when you leave. Continue capturing or use Finish & Save first.
- `photo_sphere_discard_title`: Discard this capture?
- `photo_sphere_exposure_match`: Match overlap brightness
- `photo_sphere_hold`: Aligned · hold still
- `photo_sphere_image_alignment`: Refine alignment from overlapping images
- `photo_sphere_instructions`: Turn around the camera lens from one spot. Follow the amber dot into the center circle and hold still for each automatic photo. Capture every target, including above and below, then save.
- `photo_sphere_moved_retry`: Camera moved during capture. Align and hold still to retry.
- `photo_sphere_multiband`: Multiband seam blending
- `photo_sphere_options`: Photo Sphere quality
- `photo_sphere_output_size`: Output · %1$d × %2$d
- `photo_sphere_partial_description`: Uncovered areas will be black. This export is an incomplete panoramic JPEG and will not be marked as a complete 360° sphere. Continue capturing for full coverage.
- `photo_sphere_partial_save`: Save incomplete image
- `photo_sphere_partial_saved`: Incomplete sphere saved: %1$d × %2$d in Pictures/PhotonCamera
- `photo_sphere_partial_title`: Save an incomplete sphere?
- `photo_sphere_processing`: Processing and saving…
- `photo_sphere_processing_progress`: Processing and saving · %1$d%%
- `photo_sphere_progress`: Captured %1$d / %2$d targets
- `photo_sphere_quality_note`: Higher quality needs more processing. Weak image matches keep the sensor pose. Rotate around the camera lens and avoid moving subjects. Output size is never silently reduced; choose a smaller size if memory is insufficient.
- `photo_sphere_ready`: All targets captured · tap Finish & Save
- `photo_sphere_restart_title`: Restart Photo Sphere?
- `photo_sphere_retry_camera`: Retry camera
- `photo_sphere_save`: Finish & Save
- `photo_sphere_save_minimum`: Capture at least 6 shots to enable Finish & Save.
- `photo_sphere_saved`: Photo Sphere saved: %1$d × %2$d in Pictures/PhotonCamera
- `photo_sphere_sensor_required`: Photo Sphere needs a working rotation sensor.
- `photo_sphere_size_k`: %1$dK
- `photo_sphere_start`: Start / resume
- `photo_sphere_title`: Photo Sphere
- `photo_sphere_undo`: Undo last shot / retake target
- `photo_sphere_view`: View saved image
- `photo_sphere_view_failed`: No image viewer is available. Find the image in Pictures/PhotonCamera.
- `preset_builtin_group_portrait`: Group Portrait
- `preset_builtin_landscape_detail`: Landscape Detail
- `preset_builtin_product_detail`: Product Detail
- `preset_builtin_redmi_astro`: Astro · Redmi
- `preset_builtin_redmi_night`: Night · Redmi
- `preset_builtin_s26_night`: Night · S26 Ultra
- `preset_engine_auto_badge`: AUTO
- `preset_engine_pro_badge`: PRO
- `preset_redmi_astro_hint`: Astro: tripod and dark sky. Single-frame RAW, requested 8 s at ISO 800, infinity focus, flash and Motion Photo off. Exposure is limited by the selected camera; infinity may need manual fine-tuning.
- `preset_redmi_night_hint`: Night preset: use the main lens and hold steady until the multi-frame capture finishes.
- `preset_s26_night_hint`: Night preset: 12 MP binned YUV, five-frame stack and automatic exposure. Hold steady; this is not Samsung Nightography.
- `pro_controls_title`: Pro
- `quick_ev_label`: EV
- `quick_ev_reset`: Reset
- `quick_shot_resolution_full`: Full
- `quick_shot_resolution_title`: Quick Shot Resolution
- `quick_wb_auto`: Auto
- `quick_wb_kelvin`: %1$dK
- `quick_wb_label`: WB
- `range_end`: Range end
- `range_start`: Range start
- `scene_action`: Action
- `scene_action_description`: Auto exposure for moving subjects. Bright light helps the camera choose a faster shutter; this preset cannot guarantee 1/1000 s. Use manual Tv when motion must be frozen.
- `scene_bright_snow`: Bright Snow
- `scene_bright_snow_description`: Inspired by Ivor Rackham’s snow-metering advice. Auto exposure and white balance with about +0.7 EV to keep snow bright; reduce EV if highlights clip.
- `scene_environmental_portrait`: Environmental Portrait
- `scene_environmental_portrait_description`: Simulated f/4 blur keeps more setting visible around the person, with restrained color. Requires a depth model; separate subject and background.
- `scene_festival`: Festival Lights
- `scene_festival_description`: Highlight-priority auto exposure, about −0.3 EV and restrained color lift for bright decorations. Based on Sony’s party-light guidance; AWB stays automatic. Moving people may still blur in low light.
- `scene_golden_landscape`: Golden-Hour Landscape
- `scene_golden_landscape_description`: Inspired by Ivor Rackham’s low-angle light and highlight guidance. Highlight-priority auto exposure, mild −0.3 EV and subtle warmth. Watch bright skies.
- `scene_golden_portrait`: Golden Hour Portrait
- `scene_golden_portrait_description`: Gentle warmth, highlight-protecting exposure bias and simulated f/2.5 blur. Requires a depth model; this does not create directional sunlight.
- `scene_manual_unavailable`: This lens does not expose manual sensor control. Choose Natural Portrait or Auto.
- `scene_mono_portrait`: Monochrome Portrait
- `scene_mono_portrait_description`: Black-and-white color recipe, gentle contrast and simulated f/2.8 blur. Requires a depth model; not a background replacement.
- `scene_natural`: Natural Color
- `scene_natural_description`: Neutral color and contrast with average metering, automatic exposure and white balance. A clean starting point when you want the scene rather than a strong effect.
- `scene_natural_portrait`: Natural Portrait
- `scene_natural_portrait_description`: Auto exposure, neutral white balance and gentle color with simulated f/2.8 background blur. Requires an installed depth model; separate the person from the background and tap an eye.
- `scene_night_shot`: Night Shot
- `scene_night_shot_description`: Highlight-priority auto exposure and about −0.3 EV preserve bright signs against dark skies. Auto ISO, shutter and white balance remain active. Hold steady; this single-frame scene is not the dedicated multi-frame Night mode.
- `scene_photo_only`: Switch to Photo to use these scenes.
- `scene_return_auto`: Return to Enhanced Auto
- `scene_stage`: Stage
- `scene_stage_description`: Auto exposure and white balance, highlight-priority metering and about −0.7 EV for spotlit subjects. Check zebra highlights; LED lighting may cause bands.
- `scene_street`: Street
- `scene_street_description`: Single-frame still capture, 12 MP target when available, auto exposure and focus. Tap moving or nearby subjects to focus.
- `scene_street_zone`: Street · Zone Focus
- `scene_street_zone_description`: Single-frame still capture, 12 MP target when available, with lens-based hyperfocal focus. Close subjects may blur.
- `scene_street_zone_unavailable`: Zone focus is unavailable on this lens; autofocus remains active.
- `scene_studio`: Studio Portrait
- `scene_studio_description`: For steady softbox or window lighting: center-weighted auto exposure, neutral skin color and auto white balance. Inspired by John Ricard’s emphasis on soft, controlled portrait light; this is not his fixed ISO/shutter strobe setup.
- `scene_window_portrait`: Window-Light Portrait
- `scene_window_portrait_description`: Inspired by Alex Cooke’s soft, directional window-light guidance. Center-weighted auto exposure, auto white balance and gentle skin color. Place the face toward a window and tap an eye.
- `settings_about_build_credit`: Photon Camera Custom Build %1$s is developed by Dinesh, building on the open-source Photon Camera core project developed by Hinnka and the work of its contributors. Thank you to the original team.
- `settings_about_this_build`: About this build
- `settings_baseline_jpg_description`: Choose the baseline LUT and recipe applied before the regular JPG creative LUT.
- `settings_baseline_jpg_title`: JPG Baseline Color Correction
- `settings_baseline_phantom_description`: Choose the baseline LUT and recipe used before Phantom mode creative LUT processing.
- `settings_baseline_phantom_title`: Phantom Baseline Color Correction
- `settings_dinesh_contact`: Dinesh · Telegram
- `settings_dinesh_donation`: Support Dinesh
- `settings_dinesh_donation_copy`: Copy address
- `settings_dinesh_donation_description`: USDT on TRON (TRC20) · optional contribution
- `settings_dinesh_donation_network`: Bybit deposit address · USDT · TRON (TRC20)
- `settings_dinesh_donation_warning`: Send only USDT using the TRON (TRC20) network. Confirm the address and Bybit deposit requirements before sending; transfers on a different network may be lost.
- `settings_dinesh_telegram`: t.me/Rookie_Trader7
- `settings_fix_tonemap_capture`: Fix tone mapping capture
- `settings_fix_tonemap_capture_description`: Linearize capture input before applying custom tone curves on devices whose captured images already include sRGB gamma.
- `settings_fix_tonemap_preview`: Fix tone mapping preview
- `settings_fix_tonemap_preview_description`: Linearize preview input before applying custom tone curves on devices whose preview already includes sRGB gamma. Capture output is unchanged.
- `settings_hlg_hardware_compatibility`: HLG Compatibility Decoding
- `settings_hlg_hardware_compatibility_description`: Some devices render dark or washed-out images under HLG. Enable this to manually decode HLG in shader if preview is abnormal.
- `settings_jpg_max_output_scale`: JPGmax multi-frame output scale
- `settings_jpg_max_output_scale_description`: 1× keeps native capture dimensions. Higher scales use multi-frame reconstruction for a larger saved photo, not native sensor resolution. Processing and memory use increase; fine detail depends on scene motion.
- `settings_max_frame_count`: Multi-frame denoise frames
- `settings_max_frame_count_description`: Frames captured by JPGmax and RAWmax. More frames can reduce noise in steady scenes, but increase capture time, memory use, and motion artifacts.
- `settings_nr_level_auto`: Auto
- `settings_original_project`: Original Photon Camera project
- `settings_original_project_description`: Source and contributors · github.com/bjzhou/PhotonCamera
- `settings_raw_adaptive_exposure_mode`: HDR+ Algorithm
- `settings_raw_adaptive_exposure_mode_description`: Choose an HDR+ processing method: HDRNet Model or Local Tone Mapping.
- `settings_raw_adaptive_exposure_mode_legacy`: Local Tone Mapping
- `settings_raw_adaptive_exposure_mode_off`: Off
- `settings_raw_adaptive_exposure_mode_photon_hdr`: HDRNet Model
- `settings_raw_color_engine_hncs_ccm`: HNCS (Original color matrix)
- `settings_raw_color_engine_hncs_lut`: HNCS (2D LUT)
- `settings_raw_default_chroma_denoise`: Chroma denoise
- `settings_raw_default_chroma_denoise_description`: Adjusts color noise reduction for RAW photos.
- `settings_raw_default_luma_denoise`: Luma denoise
- `settings_raw_default_luma_denoise_description`: Adjusts luminance noise reduction for RAW photos.
- `settings_raw_default_processing_section`: RAW Defaults
- `settings_raw_default_sharpening`: Sharpness
- `settings_raw_default_sharpening_description`: Sharpening applied by default when developing RAW captures.
- `settings_raw_default_sharpening_section`: Default sharpening
- `settings_raw_hncs_2d_lut`: Hasselblad camera-exclusive 2D LUT
- `settings_raw_hncs_color_branch`: Color reproduction profile
- `settings_raw_hncs_color_branch_ccm`: Original color matrix
- `settings_raw_hncs_color_branch_description`: Choose the RAW original color matrix or the Hasselblad-exclusive Cb/Y–Cr/Y color map.
- `settings_raw_hncs_color_branch_lut`: 2D LUT
- `settings_raw_hncs_profile`: HNCS camera profile
- `settings_raw_hncs_profile_description`: Uses Hasselblad camera-specific Cb/Y-Cr/Y chroma tables.
- `settings_raw_photon_hdr_description`: Preserves more highlight and shadow detail in high-contrast scenes for more natural tonal transitions.
- `settings_section_color_hdr`: Imaging & Color
- `settings_section_multiframe_exposure`: Max & HDR
- `settings_tonemap_mode`: Tone Mapping
- `settings_tonemap_mode_description`: Adjust the hardware tone mapping (contrast/gamma) mode
- `settings_tonemap_mode_natural_light`: Natural Light
- `settings_tonemap_mode_srgb`: sRGB
- `settings_tonemap_mode_system_default`: System Default
- `settings_use_hlg10`: HLG10 HDR
- `settings_use_hlg10_description`: Enable HLG10 dynamic range capture on supported devices. Requires P010 and RAW to be off.
- `smooth_zoom`: Smooth
- `state_empty`: Empty
- `state_off`: Off
- `state_on`: On
- `status_bar_notification_info_overflow`: 999+
- `subject_name_animal`: Animal
- `subject_name_bird`: Bird
- `subject_name_identifying`: %1$s · identifying…
- `subject_name_object`: Object
- `subject_name_person`: Person
- `subject_name_unavailable`: %1$s · naming unavailable
- `subject_name_uncertain`: %1$s · type uncertain
- `switch_role`: Switch
- `tab`: Tab
- `template_percent`: %1$d percent.
- `texture_overlay_bottom_right`: Bottom right
- `texture_overlay_center`: Center
- `texture_overlay_custom`: My image
- `texture_overlay_custom_limit`: Custom images are saved with processed photos only; RAW and direct Max saves are unchanged.
- `texture_overlay_dust`: Dust & Scratches
- `texture_overlay_gradient`: Creative Gradient
- `texture_overlay_import`: Import image…
- `texture_overlay_import_failed`: Could not import this image
- `texture_overlay_light_leak`: Light Leak
- `texture_overlay_placement_done`: Done placing
- `texture_overlay_placement_drag`: Drag on viewfinder to place
- `texture_overlay_placement_tools`: Position / size
- `texture_overlay_position_x`: Horizontal %1$d%%
- `texture_overlay_position_y`: Vertical %1$d%%
- `texture_overlay_randomize`: New variation
- `texture_overlay_remove_custom`: Remove image
- `texture_overlay_remove_notice`: Detaches this image from the current camera recipe. The original image and other presets are kept.
- `texture_overlay_scratches`: Scratches
- `texture_overlay_size`: Size %1$d%%
- `texture_overlay_strength`: Strength · %1$d%%
- `texture_overlay_title`: Image Overlay
- `texture_overlay_top_left`: Top left
- `tooltip_description`: tooltip
- `tooltip_label`: show tooltip
- `video_aspect_1_1`: 1:1
- `video_aspect_239_1`: 2.39:1
- `video_aspect_3_2`: 3:2
- `video_aspect_4_3`: 4:3
- `video_aspect_5_4`: 5:4
- `video_bitrate_mode_unsupported`: The encoder does not support this bitrate mode. Switch CBR/VBR or choose another codec.
- `video_constant_bitrate_description`: On: CBR. Off: VBR, allocating bits according to scene complexity. The selected bitrate remains the target, not an exact file-size guarantee. Unsupported modes stop recording with an explanation.
- `video_constant_bitrate_title`: Constant video bitrate · CBR
- `video_encoder_configuration_unsupported`: No hardware encoder supports this video size, frame rate and bitrate mode. Choose another video configuration.
- `video_fixed_fps_description`: On: request an advertised fixed camera FPS range. Off: allow a variable range up to the selected FPS. Actual delivery can still vary with exposure, temperature or processing; see Live camera FPS.
- `video_fixed_fps_title`: Fixed frame rate
- `video_fixed_fps_unsupported`: This lens does not expose a fixed range for the selected FPS. Choose another FPS or disable Fixed frame rate.
- `video_focus_tools_title`: Video focus tools
- `video_follow_lens_limited`: Lens limited · tap for details
- `video_follow_locked`: Following · 1.5× crop
- `video_follow_lost`: No match · holding frame
- `video_follow_next`: Next person
- `video_follow_start`: Follow person
- `video_follow_stop`: Stop following
- `video_follow_unavailable`: Follow Person needs face detection data from this camera lens
- `video_fps_experimental_warning`: Experimental 60 FPS: not advertised for this camera stream. The driver may reject it or capture fewer frames. Check live FPS and the original video. Return to 30 FPS if preview or recording fails.
- `video_frame_rate_picker_hint`: Available for %1$s on this lens and recording path. Other advertised rates may need a high-speed session.
- `video_frame_rate_picker_title`: Frame rate
- `video_frame_rate_picker_value`: %1$d fps
- `video_hdr_chip_off`: 10-bit Off
- `video_hdr_chip_on`: 10-bit HLG
- `video_hdr_log_explanation`: HLG uses native 10-bit HEVC at 16:9. Enabling it disables custom Log. Creative effects and software crop are bypassed in the saved HDR video.
- `video_hdr_not_exposed`: 10-bit HLG · not exposed by this lens
- `video_hdr_sdr`: Off · SDR video
- `video_hdr_session_failed`: The camera rejected the selected 10-bit recording session. Recording stopped without an 8-bit fallback. Try a different supported configuration or return to normal video.
- `video_hdr_unavailable`: HLG10 recording is unavailable for this camera/session. Use 16:9 on a supported logical camera, or disable HDR to record SDR.
- `video_horizon_steady_hdr_unavailable`: Horizon Steady is unavailable with native 10-bit HDR because that direct recording path bypasses software transforms. Choose SDR or custom Log.
- `video_horizon_steady_help`: Horizon Steady levels saved video within ±20° using motion sensors. Edges are cropped to prevent rotation borders. The viewfinder remains unstabilized. Choose before recording; this is not a 360° horizon lock.
- `video_horizon_steady_off`: STEADY OFF
- `video_horizon_steady_on`: STEADY ON
- `video_horizon_steady_unavailable`: This camera needs a working gyroscope, gravity sensor and realtime frame timestamps for Horizon Steady.
- `video_live_fps`: Live camera · %1$.1f fps
- `video_live_fps_waiting`: Live camera · measuring…
- `video_log_chip_off`: Log Off
- `video_log_chip_on`: Log On
- `video_log_not_raw`: Custom Log is a processed video curve, not sensor RAW and not guaranteed 10-bit. Selecting Log switches native HLG off.
- `video_native_hdr_description`: Record the camera’s original HLG10 output using HEVC, at 16:9. Availability depends on lens, resolution and frame rate. Saved HDR bypasses LUTs, diffusion, overlays, selfie mirroring and software stabilization/crop; camera stabilization remains available. Dolby Vision recording is not enabled.
- `video_native_hdr_not_supported`: This lens does not currently expose HLG10 capture. Select a supported lens or turn this option off to record SDR. Dolby Vision recording is not enabled.
- `video_native_hdr_title`: 10-bit HDR video · HLG
- `video_output_actual_label`: REC
- `video_output_planned_label`: OUT
- `video_peaking_chip`: Peak
- `video_peaking_enabled`: Live focus peaking
- `video_raw10_chip`: RAW10 LOG
- `video_raw10_enable`: Enable RAW10
- `video_raw10_failed`: RAW10 recording stopped: %1$s
- `video_raw10_help`: "Phone-test mode: RAW sensor frames → FP16 processing → 10-bit HEVC. Starts at 1080p / 24 fps; actual speed depends on the phone. Requires RAW access, Bayer colour metadata, GLES 3.1, a 10-bit recording surface and a Main10 encoder—not native HDR support.

Saves flat Photon Log v1 / Rec.709, not Samsung Log, HDR or Dolby Vision. Preview colour and effects are not baked in. Software stabilization, overlays, diffusion, Follow Person and zoom are not supported in this mode. Use 1× on a directly opened camera. No software ProRes encoder is included. Do a short recording test before important use."
- `video_raw10_title`: RAW-derived 10-bit Log
- `video_raw10_unavailable`: RAW10 needs a directly opened Bayer RAW camera, a Main10 encoder, 1080p at 24–30 fps, 16:9 and 1× zoom with Follow Person off. This configuration is unavailable; no 8-bit substitute was recorded.
- `virtual_aperture_auto_viewfinder_hint`: Auto f/%1$s
- `virtual_aperture_decrease`: − 0.1
- `virtual_aperture_increase`: + 0.1
- `virtual_aperture_model_required`: Install the depth model from Settings → Focus & Lens before enabling virtual aperture.
- `virtual_aperture_off`: Virtual aperture off
- `virtual_aperture_panel_hint`: Simulation · not a physical lens aperture
- `virtual_aperture_preset_classic_portrait`: Classic portrait · softer background
- `virtual_aperture_preset_couple`: Couple · keep both faces clearer
- `virtual_aperture_preset_detail`: Detail · product text and edges
- `virtual_aperture_preset_documentary`: Documentary · context stays clearer
- `virtual_aperture_preset_dreamy_portrait`: Dreamy portrait · strongest blur
- `virtual_aperture_preset_group`: Group portrait · more subjects sharp
- `virtual_aperture_preset_natural`: Natural portrait · safer edges
- `virtual_aperture_preset_portrait`: Portrait · recommended
- `virtual_aperture_preset_street`: Street · retain more context
- `virtual_aperture_preset_strong_portrait`: Strong portrait · cinematic blur
- `virtual_aperture_preset_subtle`: Subtle separation · product and detail
- `vlog_dual_probe`: Test front + rear (no recording)
- `vlog_front_camera`: Front
- `vlog_mode_description`: Choose a camera for your video. The current video resolution, frame rate, audio and stabilization settings remain available. Press Record when ready.
- `vlog_mode_title`: Vlog
- `vlog_rear_camera`: Rear
- `vlog_single_camera_notice`: Records one camera at a time. Simultaneous front and rear recording is not available in this build.

## Additional executable verification: computational upscaling and saved effects

### Computational capture upscaling is a genuine additional path

The reference is not merely renaming Lanczos. `fu2` is identified by its GlesYuvStacker logging and constructors; `va2.S(List, rotation, aspect, boolean, outputScale, ColorSpace)` calculates scaled output dimensions (`va2.f0`), constructs `fu2(IIIIILandroid/graphics/ColorSpace;IZF)` and calls `fu2.G(List)` (29911–29928). `fu2.G` uploads the reference and subsequent YUV frames, builds pyramid/alignment/kernel/robustness state, calls `b(IIZ)` for the reference at 5214 and each subsequent aligned frame at 5561, then `F()` at 5640 and reads an output Bitmap through `I()`.

`fu2.b` binds the enlarged output viewport and a dedicated per-frame super-resolution accumulator. Actual shader inputs include `uCurrentY`, `uCurrentCbCr`, `uFlowGrid`, `uRobustness`, `uTileMask`, `uKernel`, `uAccumulatorInput`, `uSrSplatRadius`, frame weight and output-to-reference transforms. The shader samples aligned inputs and weights reconstruction contributions. `fu2.F` runs `normalizeSuperResolutionOutput`, binding `uSrAccumulator`, `uBaseAccumulator`, input/output sizes and minimum-detail weight. `l()` clears the SR accumulator. These are executable GPU multi-frame reconstruction passes, not an SR model asset or an Android vendor-tag guarantee.

Saved-output proof: coroutine `cn2` calls `va2.S` at 3475, checks actual bitmap dimensions against the computational target (4001–4172, explicit failure on mismatch), writes the internal original through `in2.E(File,Bitmap,quality,boolean)` at 5482, and exports through `in2.U` at 6772. Its metadata/export labels include computational_50mp, Comp50MP, Comp24MP and Comp16MP (6448–6623). Controller/ViewModel `ql0` logs source dimensions, scale and frame count, derives scale using a square root (target-area to input-area conversion), and records `capture.resolutionMode`, `capture.sourceDimensions`, `capture.superResolutionScale`. Resource descriptions explicitly call 16MP front and 50MP modes multi-frame reconstruction from native input. This establishes implemented capture-to-file enlargement; static analysis cannot establish effective resolved detail, speed or device reliability.

Current comparison: `GlesYuvStacker.kt:253–254,650–659,1969–2042` accumulates frames at the native input grid and selects `lanczosOutputProgram` when outputUpscaleEnabled. It uses an output-resampling transform after native stacking. Separately, the current app **already has actual multi-frame SR**: CameraViewModel HIGH_DETAIL → CaptureSequenceMerge.merge → PhotoSequenceTools SUPER_RESOLUTION → SuperResolutionProcessor.process. The latter requires at least four distinct subpixel phases and registration confidence; CaptureSequenceMerge produces a separately saved 2× result using GalleryManager.savePhoto and checks its original file. Existing RAW RAISR/Lanczos choices are another output stage. Therefore the real reference addition is **integrated YUV capture reconstruction at the selected computational target resolution**, distinct from current native-grid stack + Lanczos and current separate High Detail sequence merge. Porting only 16/24/50MP labels or raising outputScale would not reproduce it. No evidence of a neural upscaler model in the reference assets.

### Photo Sphere reaches JPEG and MediaStore output

`va2.d0(Context,List,boolean,vq4,bi2,continuation)` constructs `p46(frames)` at 31866, calls its registration/preparation `c(...)` at 31958 and bitmap rendering `d(Bitmap,x46,...)` at 32195. `p46.c` computes matching/registration with tested/accepted pairs, inlier counts and angular error; `p46.d` renders the result. `va2.d0` then JPEG-compresses the bitmap at 32427, inserts a MediaStore URI at 32924, opens its output stream at 33013 and updates the item at 33178; failure branches delete provisional entries. `pq4` is a save coroutine and reports PhotoSphere save failures. This is substantially stronger than the menu label, although stitching quality and sensor coverage remain untested. Current source does not have Photo Sphere or corresponding frame registration/export UI.

### Saved image overlays are actually composited

`ef5.m(Bitmap,ColorRecipeParams,boolean)` is a real overlay renderer: reads textureOverlayStyle/imagePath, ensures mutable output and creates a Canvas; its drawing routine reads strength, variant, image position and size and draws procedural gradient/scratches/leak/dust or decoded custom images. Custom image placement ends with Canvas.drawBitmap at 4338. `tp4` calls the GLES recipe renderer `tq3.e(...)`, then `aq4.i(...)`, then `ef5.m(...)` at 3279, and passes the resulting Bitmap into `aq4.a(...)`, the downstream photo processing/export coroutine. `sp4` and `jc` call the same compositor. Recipe overlay fields are stored in the gallery schema (`kh2`/`ln2`), so this is not just a viewfinder widget. Current source has no textureOverlay fields or renderer. Scope limitations stated by the APK still apply: processed-photo output, not arbitrary RAW/direct-Max sensor rewriting.

### Directional diffusion has an export shader path

`tq3` reads ColorRecipeParams.getBloom/getDirectionalBloom/getDirectionalBloomAngle/getSoftLight at 13522–13561, runs diffusion/effect passes and logs DIFFUSION_EXPORT including directional strength/angle and bloomApplied at 16205+, then performs GPU readback and output bitmap copy at 16451–16522. This renderer feeds the saved-photo path above. `wy6` separately implements VideoDiffusionGl with explicit unavailable/failure handling. Current already has soft-light/bloom shader logic; only the additional directional controls/algorithm should be compared as a gap, not diffusion wholesale.

### Baseline JPG/Phantom is an extension of existing RAW baseline stacking

Current `lut/BaselineColorCorrection.kt` has target enum RAW only; resolveFromMetadata explicitly accepts baseline IDs/recipes only for RAW. Current preset package import recognizes legacy JPG/Phantom keys, but this is compatibility parsing, not active output support. Reference `lq3` has BASELINE_JPG, BASELINE_RAW and BASELINE_PHANTOM, preferences `cu6` persist all three IDs, and `pt6`/`j50` expose all three. Reference render config `ko5` carries baselineLutConfig and baselineColorRecipeParams; `ad5` explicitly enforces baseline+creative layers in realtime video. Thus JPG/Phantom target selection is a credible real extension; exact per-mode capture branches still need review before stating complete parity. RAW baseline pipeline already exists and must be preserved.

### HNCS selection: actual settings branch, output distinction still needs closure

Reference settings `xw4` checks `g95.e()` for HNCS and presents the color branch field at 18972; checks enum name HncsCcm at 18994 and uses HncsLut enum field `g95.p` at 19089; callback `t50` is constructed for selection. This proves a selectable setting beyond resources. Current maps both saved names to its sole Hncs and already renders a 2D map. The original-CCM-specific output behavior still needs tracing into engine plan/shader to distinguish a real alternative from naming. Do not mark this fully verified yet.

Focus pull/multi-area AF and per-lens processing remain partially traced: the APK has focus-run/stop stateful buttons, actual per-lens preference key and settings callbacks, but timer-to-lens-request and per-lens preference-to-output chains have not yet been established in this pass. They remain discovery candidates rather than claimed complete functions.
