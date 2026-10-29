# Add Me capture and UI repair — 22 October 2026

User report: after the first photo no alignment image appeared, the camera shutter did nothing, and the visible result remained the first capture.

## Confirmed source defects

- A separate full-screen Dialog consumed the normal camera shutter. The normal CameraScreen capture handler had no route into the group session.
- A 4000×3000 input required 513,554,432 bytes under the recipe-processing preflight, exceeding even 70% of an empty 512 MiB heap. The original was already saved, but `firstId` was assigned only after decoding/recipe processing, so a loading failure left the UI at its original step. Retrying captured again.
- There was no loading/segmentation/alignment indicator. Repeated ROI changes retained obsolete output bitmaps.
- Source-shot capture thumbnails could remain above the new composite thumbnail, and the group save callback did not refresh the latest photo.
- A desktop probe of the unchanged registration code found that equal residuals could select a nonzero negative offset for identical backgrounds because candidates were visited in that order.

## Changed behaviour

`GroupCaptureOverlay` now lives in the existing viewfinder, in the same window as the camera shutter. The shutter routes to this session before advanced/normal capture and takes shot one, then shot two. Capture long-press and mode/rear-front switching are disabled during the group session. The first rendered photo is a transparent alignment guide while the live preview continues beneath it. Steps and capture/save, load, segmentation, alignment and blending progress are explicit. Panels use the existing toolbar and shutter-area dimensions, and a separate full-screen review allows unobstructed inspection of the combined photo. Retry processes the retained source ID, and Retake shot 2 requests a replacement without overwriting an original. All capture/render/save state guards are separate.

Output size is explicitly selectable before loading the first photo: 2048 px maximum edge (default, lower memory) or full source resolution. Both camera originals remain at their captured resolution, including RAW links. This changes the previous full-source-only composite default; it does not silently label resized output as full source. Result metadata records `max_edge_2048` or `full_source`. Full source still requires enough memory and is not guaranteed on every phone. Sources are decoded as sRGB software bitmaps and the recipe is applied once before person segmentation/blending.

Saving requires two processed sources, an up-to-date composite preview and explicit review confirmation. After confirmation, either the normal shutter or Save photo publishes the composite. The source-shot thumbnail is acknowledged and gallery/widgets refreshed. Obsolete rendered/retaken bitmaps are released after replacement frames. Allocation failures show a recoverable message. Registration ties prefer the smallest translation.

The capture-tools menu is now a scrollable Material bottom sheet with large labelled cards for Add Me, advanced capture, focus bracketing and photographic style. Interval and shot-count choices are visible chips instead of hidden tap-to-cycle values. New text has English and Simplified Chinese resources. No decorative animation loops were added.

## Validation boundaries

- Desktop `scripts/validation/addme-jvm/run.sh` compiles the production mask sampler, registration and compositing implementations against an array-backed Bitmap adapter. ML Kit segmentation is deliberately excluded; supplied probability masks are synthetic. It verifies that shot-two people appear, shot-one people/background remain, ROI excludes donor pixels, source bitmaps remain alive, and unrelated scenes are rejected. The original registration failed the stationary-background assertion; the tie-break fix passes.
- Android compilation, APK packaging/signature verification and the existing unit suite are recorded in the build verification document for this delivery.
- No physical Android device is connected. Actual live viewfinder, Camera2 capture, bundled ML Kit inference, thermal/memory behaviour and layout acceptance on CMF Phone 1 still require phone testing. This is not a claim of end-to-end device validation.

## Phone acceptance

1. Install the updated signed APK over the existing debug app (do not clear its data).
2. Open capture tools → Guided group photo / Add Me. Use 2048 px first.
3. Press the normal camera shutter once. Wait for saving/loading to finish. Confirm step 2 and the transparent first-shot guide appear.
4. Keep the scene/lens/zoom steady, swap photographers, leave the added person in the empty area, then press the same shutter again.
5. Confirm step 3, a combined preview and both people. Refine the added-person area if necessary. Check the review box, then press the shutter or Save photo.
6. Confirm the latest thumbnail and gallery open the composite and that both originals are retained.
7. Repeat retaking shot two and processing retry. Try an unrelated scene and confirm it is rejected rather than published as a false composite.
8. If a device-only issue remains, export the app log containing `AddMe` stage messages and include which step/status was last visible. Do not erase originals.
