# Build 183

- The live RAW rendering-engine preview no longer imposes an additional ISP-luminance ceiling after the engine color transform. This ceiling changed brightness and color independently of the saved render. The preview still uses Camera2's processed SurfaceTexture, so vendor ISP tone mapping, RAW demosaic, local HDR, and motion processing cannot be made pixel-identical by this change alone.
- Added a regression test for this preview-only adjustment; the test failed before the change and passed after it.
- Build 182's automatic 4000×3000 source selection for 8000×6000 2× output remains.

The on-device preview-to-photo comparison and camera-dependent color calibration still require physical device testing. No claim is made that the outstanding video/photo feature list is complete.
