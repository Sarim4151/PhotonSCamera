# Video audio and MotionCam-inspired monitoring - build 174

Video settings now contain Microphone, Monitor tools and Shutter angle panels. Audio defaults stay off; background-noise suppression uses Android NoiseSuppressor attached to the actual AudioRecord session, with verified enabled state and honest unavailable/failed/no-audio feedback. Android does not expose hardware NS strength: the four separate wind choices are 0/65/100/160 Hz sample-count-preserving PCM16 stereo high-pass filtering. Stronger filtering reduces bass; it does not synthesize voice isolation or physically cancel surrounding sound.

Monitor tools expose existing peaking/zebras plus the preview histogram in Video, and add display-luminance false colour. These assist overlays stay outside dedicated video encoding and clean capture/metering textures. False colour is explicitly Video-only and uses display levels, not RAW sensor clipping or calibrated IRE. Shutter presets 90/180/270/360 degrees use selected FPS, clamp sensor/frame limits and preserve ISO. Reapply after changing FPS; this is not an automatic shutter-angle lock.

Additional encoded Log options are S-Log3/S-Gamut3.Cine and ACEScct/AP1 through existing implemented transfer/gamut mappings and profile metadata. These encode the camera video stream; they are not RAW-derived DirectLog, manufacturer RAW processing or a full scene-referred ACES pipeline. Existing 10-bit codec selection remains device/profile-dependent.

Recording diagnostics report renderer input/submission FPS, render milliseconds and cumulative latest-frame queue replacements at about 1 Hz. They do not measure sensor/RAW drops. Enhanced-stabilization queued frames are excluded from replacement counts.

## Reliability review
Independent read-only reviews examined audio ownership/lifecycle and the new monitoring/Log/UI paths. Addressed: assign recorder/codec ownership before fallible start, terminate negative microphone reads, stop and join the reader before native release, bounded idempotent AAC EOS and bounded audio-drain wait, and no-audio fallback before muxer track creation. A nonempty encoded audio sample is required before adding its track; later microphone loss can leave shorter audio with continued video. Device NS Off is described as the app request being off because OEM microphone processing can remain.

Seven real signal tests cover Off identity, rumble attenuation, voice-band response, strength ordering, chunk equivalence, channel isolation/partial buffer ownership, DC/full-scale and invalid frame alignment. The complete actual preview-assist fragment shader passed host Mesa GLES compile/link/draw/readback with false colour both disabled and enabled. No Android phone/emulator validation of capture, sound quality, route changes, codecs, sync or interruption has been performed.

## MotionCam feature map and boundaries
Public references: https://motioncamapp.com/ and https://play.google.com/store/apps/details?id=com.motioncam.pro . Relevant features are implemented independently; no proprietary MotionCam assets or MCRAW compatibility are claimed.

| Domain | State here |
|---|---|
| Manual exposure, focus, WB; H.264/H.265, frame rates and bitrate | Existing device-gated video pipeline |
| Monitor LUT vs baked look, broad Log/gamut choices | Existing, with S-Log3 and ACEScct options added |
| False colour, preview histogram, peaking/zebras panel | Implemented, display signal only |
| Shutter angles, renderer FPS/queue diagnostics | Implemented with the boundaries above |
| RAW photo / burst | Existing photo path |
| True continuous Bayer RAW video | Not implemented; current VIDEO session has no RAW reader |
| RAW-derived DirectLog, RAW histogram/channel clipping | Not implemented; cannot be derived from the existing encoded stream |
| Lossless MCRAW, APV/ProRes, CinemaDNG clip export/player, external desktop tool compatibility | Not implemented |
| Full ACES scene pipeline, temporal RAW video denoise, gyro export, separate WAV | Not implemented |

True RAW video requires a dedicated preview+RAW_SENSOR session; exact Image/SENSOR_TIMESTAMP physical-result matching; bounded buffer/backpressure; per-frame calibration/exposure metadata; disk/thermal throughput gates; timestamped clip/audio persistence and recovery; export/player workflow. RAW10 must be explicitly unpacked rather than treated as RAW_SENSOR. Device session support, sustained cadence, colour/AV clocks and editor interoperability require actual phone validation. A 12 MP RAW16 stream is about 24 MB/frame before compression; normal encoded-video FPS capability does not establish RAW throughput.
