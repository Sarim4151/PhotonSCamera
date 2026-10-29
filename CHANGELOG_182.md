# Build 182

- When 2× finish upscale is on and photo source resolution is Auto, prefer an advertised binned stream whose width and height are each half of another advertised stream. A camera reporting 8000×6000 and 4000×3000 now uses 4000×3000 as source and targets 8000×6000 after 2× processing.
- Manual photo source sizes remain explicit; no unadvertised camera mode is requested. Without a matching pair, Auto retains the largest available size.
- The settings page shows the current source and its projected 2× dimensions.
- Build and 621 unit tests passed (0 failures; 7 skipped). Device output and performance remain unverified.
