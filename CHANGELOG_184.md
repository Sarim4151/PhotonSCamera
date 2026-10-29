# Build 184

- When 2× finish upscale is selected for a new photo, capture uses a 1× intermediate rather than a separate fractional JPG/RAWmax enlargement. This avoids the "conflicts with fractional enlargement" toast for new shots and gives the finish stage the native-sized image.
- Dedicated video recording now calculates its crop from the camera input and encoder raster dimensions. A quarter-turn SurfaceTexture sampling transform no longer makes a 3840×2160 recording crop as if its source were 2160×3840.
- Targeted photo-scale and video-geometry tests added. Both changes still require on-device confirmation, especially with 4K and stabilization.
