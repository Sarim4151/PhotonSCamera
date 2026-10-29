# Advanced capture and Add Me — 1.28.9 (168)

## Capture contract

An advanced source is one camera exposure, not a nested RAWmax/JPGmax sequence or a Live Photo recording. SequenceSourcePolicy creates a shot-local snapshot with those modes disabled; the camera preference state is not changed. The same snapshot is used for reservation, lighting choice, request construction and processing. RAW sources retain the selected RAW engine.

Finite sequences (High Detail, Night, Astro, Star Trails, Source Burst and Motion HDR) now wait for physical exposure completion before allowing the next source. They do not wait for the preceding source to finish rendering/exporting. The processing queue and ImageReader slots bound admission; image delivery lag may add one request before queue backpressure becomes visible. Saved results are awaited in capture order before a merge or successful completion. Failure and cancellation release the deferred registrations. Original advanced sequence sources remain available as before.

The frame counter follows capture completion, not rendering completion. Waiting, exposing, rendering, accumulating and finishing have distinct messages. Elapsed time ticks while a source is exposed, processed or saved. Advanced sources skip ordinary develop animations and duplicate GL thumbnail captures.

Full-source Live ND/Composite must render and commit a source before its disk accumulation; their cadence still depends on that work. The timer starts after the first committed source and automatic runs require at least two sources. Manual save requires two committed sources from the current session, so an old recoverable checkpoint cannot be published before the new session has committed anything. Stopping an insufficient stack leaves already captured originals.

## Engine contract

RAW source originals already contain the selected engine: do not apply it again in merge or accumulation. YUV advanced sources now freeze the current RawEnginePreviewPlan and apply its ISP restoration/engine atlas before original JPEG storage and before recipes. This uses the same RawEnginePreviewGl engine bake path, including Fotufilm; it does not feed ISP sRGB directly into a scene-linear RAW tone pass.

Advanced preview samples now use the engine-only snapshot route. Creative and baseline recipes stay deferred until save, and original/metering snapshots continue to bypass all colour layers. The selected engine is prepared for advanced capture in both Photo and Pro. Motion HDR checks the actual exposure-lock flag, so focus may remain locked while exposure is bracketed. Changing engine/exposure/tone settings ends a sequence rather than mixing looks. An unavailable engine produces an error instead of silently exporting an engine-free source. ISP rendering is an approximation from already processed SDR input; it cannot recover clipped highlights or reproduce missing RAW sensor information.

## Preview integration

Preview average/composite now register small camera translations against the first frame, reject ambiguous/large changes and use per-pixel counts to avoid darkened shifted borders. The estimator uses tiled background consensus so moving subjects/lights can remain in the intended average/trail result. Integer translation is limited to about16 pixels; it does not fix rotation, parallax, rolling shutter, within-exposure blur or lost subpixel detail. A tripod still gives the best ND/trail result. PRE_CAPTURE remains a bounded ring.

Persistent blend state is approximately17.06 bytes per pixel (about38.4MiB for1536²), independent of frame count. Incoming frames and snapshots add their own bounded bitmap storage. Default1280-edge settings use less. Full-source accumulation retains its existing heap/disk guards and atomic checkpoint.

## Add Me transaction

Each Add Me session owns temporary sources by UUID in metadata. Its shots disable auto-export and ordinary gallery registration/thumbnails; saved temporary metadata has captureMode=add_me_source and is excluded from page/latest SQL queries. Direct source access remains available to the merge. On successful merge, the output file is decoded to verify actual dimensions before owned sources are deleted. A failed save retains sources while the overlay is open for retry. Retake/dispose and cancelled in-flight capture completion clean up only session-owned temporary images. Existing unrelated or older gallery photos are not deleted.

The blend excludes people already present in shot one from the donor mask and imports only added people. Registration uses area-averaged coarse luminance followed by source-pixel refinement and validation, fixing rejection of small shifts that fell between the old coarse grid positions. Both2048 review and full-source output use the corrected mask/alignment. Full-source staging still processes one original at a time.

## Action bar

Black/yellow styling is retained. Controls have icons, short single-line localized labels, equal touch targets, correct active states, and a stable settings/overflow area. The available width and font scale determine how many controls fit; remaining controls remain available in the actionable overflow menu alongside customization. There is no mid-word wrapping of Sequence or Peaking.

## Validation and device checks

- Actual preview-buffer JVM adapter: old jittered average error2,488,172; corrected average/composite stationary interior error0. Six known integer shifts recovered, moving light trails retained, unregistrable frame rejected.
- Actual Add Me JVM adapter: existing first-shot people preserved despite changed second-shot people, added person retained, background/ROI/unrelated scene checks pass; small3px shift registration now passes.
- Full-source Add Me adapter: four pre-existing pixel goldens,4000×3000 detail, sequential staging/recycling and cancellation/truncated/mismatched cache rejection pass.
- Actual ISP bridge shader with Mesa ES3.2 and RGBA16F identity atlas: compile/link/binding/readback pass with zero8-bit pixel error; alpha and row orientation preserved. This is not validation of every real engine bake on a handset.
- Camera-readiness adapter: retained image leases, busy capture, stop, camera change and cancellation pass. Camera thread and ImageReader are simulated.
- SQL visibility queries exclude temporary Add Me shots before pagination/latest selection.

No phone/emulator is connected. Camera2 driver acceptance, real-engine GPU/memory behaviour and ML Kit segmentation on the phone remain unverified here. Check Source Burst six photos with zero gap, High Detail and Motion HDR with selected engine, then tripod Live Composite/ND with at least two fully processed frames. For Add Me, capture an unchanged first group and a newly added person, inspect the merge, save, then verify only the final image is visible. If capture stops, preserve the exact status and logcat around Advanced, Capture rejected, Photo capture and CaptureProcessingQueue; these distinguish exposure, image delivery, rendering and save failures.
