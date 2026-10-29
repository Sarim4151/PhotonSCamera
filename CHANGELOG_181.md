# PhotonCamera 181

- A forced single flash now captures one AE-controlled still, even when RAWmax/JPGmax and Live Photo are enabled. It preserves the multi-frame preference for subsequent shots. A multi-frame burst previously used continuous torch lighting instead of a short flash.
- Flash preparation now waits for reported auto white-balance convergence alongside AE and AF, with the existing timeout fallback. Absent AWB-state metadata does not block capture.
- Ordinary photo preview stays in normal AE mode until the explicit flash preparation and still request. An omitted optional flash/AF/AE result key no longer makes the flash-session confirmation time out.
- 2× finish upscale of large sources now uses bounded RAISR tiles and a scanline JPEG writer. An 8000×6000 source targets 16000×12000 without allocating that full output Bitmap; smaller sources continue to use the ordinary bitmap RAISR path. JPEG is used for streamed output even if HEIC export is preferred. If streaming fails, a native-resolution photo is exported and the failure is reported.
- Release the processed bitmap after successful native-resolution export; repeated exports no longer retain that full-size buffer.

The phone's flash strength, skin tone and Live Photo video effect still require on-device validation. Manual ISO/shutter settings can override the camera's flash metering; auto exposure and auto white balance are recommended for a balanced flash photo.
