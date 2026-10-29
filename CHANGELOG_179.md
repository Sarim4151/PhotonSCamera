# PhotonCamera 179

- Professional RAW capture has an independent HDR+ on/off switch in the top sheet. When off, a single RAW frame is developed by the selected rendering engine.
- HDR+ inspired burst appears alongside Sabre and Spatial in Professional settings. Its initial seven same-exposure frames use the existing sharp reference selection, motion-aware Spatial RAW merger, and the selected color rendering engine. Frame count remains adjustable.
- HDR+ enabled state now persists independently of RAW/Pro mode. Bracket exposure remains unavailable for same-exposure modes.

This is an original HDR+ inspired mode built on this app's pipeline, not Google's proprietary GCam HDR+ engine. It requires RAW multi-frame support from the selected lens and on-device validation. Additional image-quality features from the roadmap remain separate work.
- Added AE and white-balance lock controls to Video settings, using existing camera request handling to keep exposure and color stable during a shot.
- Ignore repeated volume-key DOWN events during a long press so a single press cannot repeatedly start and stop capture.
- Keep the last valid waveform and vectorscope visible while an updated grading preview is analyzed, avoiding blank flashes during edits.
- Spektrafilm remains available as a RAW color engine after HDR+ burst merge. Existing issue #88 film-process presets and grain/bloom/diffusion/halation controls remain available in the photo editor; physical spectral development and ISO-dependent grain are still outstanding.
