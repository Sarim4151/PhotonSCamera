# Build 192 — cinema tools and sensor RAW frame recording

## Where to find the controls

Switch to Video, then tap **Cinema tools** at the lower-left of the viewfinder.

- Frame guides: 9:16, 4:5, 1:1, 4:3, 14:9, 16:9 and cinema aspect ratios; safe area 5–100%.
- Anamorphic preview de-squeeze: display-only; does not resample recorded video.
- Record-run monitor clock uses controller elapsed time, excludes pauses; time-of-day display. Slates export descriptive timecode in JSON, not embedded SMPTE tracks or synchronization.
- Three saved focus positions per lens, 0.5–10-second rack focus with cancellation on pause/lens change/close.
- Separate capture/playback FPS. Off-speed output retimes video timestamps and disables sound; camera capture FPS stays in ordinary video settings.
- Persistent frame-gap warning; optional stop-and-save routed through normal camera lifecycle. Detects renderer timestamps/queue replacements, not guaranteed sensor-drop diagnosis.
- Headphone audio monitoring taps existing filtered microphone PCM, uses a bounded separate output queue, refuses speaker monitoring and closes on route removal. Actual routing/latency remains Android/device-dependent.
- Slate scene/take/reel/camera/good-take and timecode-start JSON in Downloads/PhotonCamera/Slates.
- Optional genuine H.264/AAC proxy export in Movies/PhotonCamera/Proxies. Keep app open until export completes; no durable background worker is claimed.

## Sensor RAW frame recording

Cinema tools → **RAW video (DNG sequence)**. Start at 1 fps and inspect the saved/dropped counts before increasing the rate. Frames are sensor RAW_SENSOR DNGs; no processed MP4 is relabeled RAW. This is a DNG frame sequence, not a proprietary RAW video container, and does not record audio.

Outputs: Downloads/PhotonCamera/RawVideo/<session>/, with DNG files, frames.jsonl sensor/exposure timing and manifest.json including requested, device-limited target and measured saved FPS. Frame writing is bounded; storage backpressure drops and closes frames instead of exhausting memory. Uses the current camera's shutter/ISO/WB/focus/zoom controls. Session-changing controls finish RAW before preview reconstruction. Explicit physical-lens overrides are rejected until properly routed RAW output is available.

## Phocus Mobile-inspired feature, already included since build 191

Gallery → open source photo → More → Copy Settings. Open target photo → More → Paste Settings. The category dialog offers Color, Detail, Frame, Geometry and Portrait; unselected groups remain target-owned. Also connected to GalleryEditScreen and batch GalleryScreen paste. This is selective adjustment paste, not the entire Phocus Mobile app or Hasselblad tethering/HNNR. The existing HNCS engine is separate.

## Existing PR fixes retained

Exposure-aware capture sequence failure deadline, explicit submission failure cleanup, and gallery-registration/failure ordering. These prevent specific indefinite failure states but do not prove successful flash exposure on the user's device. Expanded shutter angles and 50/60 Hz mains presets remain.

## Limits

No claimed Blackmagic Cloud compatibility, embedded SMPTE/genlock, unsupported sensor rates/codecs, or extra model-specific Leica/Canon calibration. Existing engines remain model-specific. Device tests are still required for RAW stream compatibility, sustained disk rate, flash saving, headphone routing and preview geometry.
