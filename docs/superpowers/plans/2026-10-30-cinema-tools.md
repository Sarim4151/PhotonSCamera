# Cinema tools implementation plan

**Goal:** Implement the requested cinema controls in the existing Android app, with real preview/record/save wiring and truthful device limitations.
**Authorization:** User explicitly requested direct implementation without prompts and asked for local APK build.
**Architecture:** Keep camera capture ownership unchanged. Isolated persisted cinema preferences feed preview overlays, recording timing/audio policy and post-save production helpers. Compose controls are grouped in a Cinema tools sheet reachable from video mode.
**Tech:** Kotlin, Compose, Camera2, MediaCodec, Media3 Transformer.

- [x] Frame/safe-area guides and preview-only anamorphic transform, record-run/time-of-day display.
- [x] Three persisted per-lens focus marks with timed rack-focus and lifecycle cancellation.
- [x] Slate metadata, JSON sidecars, optional actual proxy encoding through Media3.
- [x] Off-speed capture/playback time scaling with audio disabled when speeds differ; headphone-only audio monitoring; renderer gap warning/stop option without mislabeling sensor drops.
- [x] Integrate components in video-mode sheet, preview transform and recording lifecycle; preserve capture output geometry.
- [x] Audit existing Phocus selective paste UI and document exact navigation and limitations.
- [ ] Attempt local SDK/Gradle build, use GitHub CI for full verification if toolchain cannot be provisioned. Run existing tests, XML/diff checks. Report physical-device verification as pending.

Constraints: respect AGENTS.md; no new unit tests; no fake unsupported hardware profiles, cloud service, native sensor log or SMPTE timecode claims. Do not overwrite unpushed work on user PC. Monitoring transform never modifies saved video.

- [x] Implement bounded sensor RAW DNG sequence capture with live request settings, timing metadata, and explicit camera-session ownership.
