# Build175 source checkpoint

Status: source recovery checkpoint, preview/UI refinements added; compilation and review still in progress. Do not claim this checkpoint is a verified APK.

Restored video stage/identity gate/sample validation, OpenGate actual encoder+camera eligibility, real photo ImageReader resolution chooser/reopen, RAISR2x final rendered colour-preserving export with HDR/fractional/memory guards and buffered background error UI, Detail native ROI+streamed fullsize export with neutral encoded-source bypass, PHOTO Portrait bundled-mask fallback and optional depth download/import/status. User reports ownV3 works; no model bytes supplied.

Detail graded-source preparation still needs fullsize renderer and can hit RAM/GPU limits; direct encoded bypass only for proven neutral app-streamed sRGBJPEGs. No physical device validation. Preserve app/debug.keystore. User authorizes allwork without confirmation questions.

Preview now consumes validated reported Camera2 RGB tone curves, with existing ACR fallback, cached inverse texture and bounded metadata update. Quick actions have visible localized labels, selected semantics and48dp targets. Full photo framebuffers reject unsupported sizes/incomplete targets rather than continuing blank. Device parity unverified.
