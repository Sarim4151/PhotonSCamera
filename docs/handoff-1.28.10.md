# PhotonSCamera 1.28.10 (169) continuation notes

## Base and scope

Authoritative starting point was Library source version 13, app 1.28.9 (168). The user also supplied Photon Camera_1.28.3.35.zip; it contains an APK, not source. No decompiled binary content was copied into this app. See reference-apk-comparison.md for evidence and candidate feature priorities. See 50mp-access.md for the native-resolution decision.

## Implemented changes

- Five compact photo toolbar targets: three user-configurable quick controls, More, Settings. More retains every quick action. Icon accessibility descriptions use localized resources.
- Full status-bar/cutout inset handling with a compact 56 dp toolbar. Mode strip moved above an enlarged white/gold shutter; actual zoom choices receive gold selection. Existing advanced/AddMe shutter routing, gallery, switching and manual controls remain connected.
- No realtime background blur; mode selector knob/color animation removed. This is a source/UI change, not a measured device performance claim.
- Camera capture admission refusal is now reported through an explicit optional callback, including a stopped handler. Advanced/AddMe callers stop waiting for an acceptance callback that cannot arrive.
- Source render/storage exceptions are propagated to the waiting sequence instead of becoming a generic null result. Cancellation semantics and queue ownership of image closure are preserved.
- Distinct admission/exposure/save failure text and lifecycle/GL stage logs make the failing boundary observable. Optional capability diagnostics cannot interrupt camera opening.

## Important unresolved device evidence

The user reports advanced modes save nothing and AddMe's first shot times out. Static investigation verified an admission-rejection hole and error suppression, but did NOT reproduce the reported phone failure. No connected Android device/emulator exists here. Do not mark the entire saving bug fixed solely because the build/tests pass. Rendering engines were not bypassed and no native 50 MP unlock was fabricated.

On the CMF Phone 1: open the rear camera, reproduce one failing advanced capture and one failing AddMe shot, then immediately open Settings → Log Viewer and export logs. Keep the chosen mode, engine, RAW/YUV setting, approximate elapsed time and visible error with the report. The bounded log buffer can be overwritten by later activity.

Read logs in order: Photo pipeline accepted → exposure complete → images ready → registered → processing → ISP engine capture start/draw/readback/complete (YUV engine sources) → terminal saved=true. Camera admission rejection means no shot was accepted. This distinguishes a camera/HAL boundary from queue, engine and storage failure without changing exposure timeouts.

## Verification

See build-verification.md for the final compiled artifact and tests. Existing desktop probes cover readiness, preview registration/stacking and person compositing; they do not execute Camera2, Android MediaStore, ML Kit segmentation or the phone GPU. Reference APK claims are static bytecode evidence, not runtime certification.

## Continue safely

Preserve app/debug.keystore and applicationId/debug suffix to update the existing debug app. Do not ship local.properties or release signing material. Use the current source as base; do not replace it with upstream. Any native maximum-pixel-mode implementation needs coherent stream/session/request handling and a real capability report first.
