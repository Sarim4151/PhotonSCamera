# Photo feature status — 1.28.12

| Feature | Current status / location |
|---|---|
| High Detail | Advanced Capture: adaptive2x when measured samples support it; otherwise aligned stack at selected input resolution or best valid frame. New sources private; only final exposed/exported. No artificial gap. |
| Advanced rendering engine | Pro mode only. Photo uses ordinary ISP processing without forced RAW engine or engine-plan wait. Creative recipe/LUT controls remain separate. |
| Best Moment | Gallery → More → Best Moment on a Live Photo. Choose timestamp and save a separate video-resolution JPEG. |
| Subject AF | Settings → Focus & Lens → Subject AF; Auto/Person/Animals/Vehicles. Real EfficientDet detection, boundedCPU cadence, category label and autofocus point. Face/eye AF remains. |
| Pose guide | Viewfinder Pose button; Portrait/Full body. Offline33-landmark guidance and overlay, opt-in/off by default. |
| Expression adjustments | Gallery → More → Expression. Select face, Subtle smile/Relax smile, strength and Save copy. Explicit2048/original export. Geometric adjustment; no teeth generation, mouth closure or reliable occlusion handling. |
| Photo grading | Gallery editor → Color recipe → Grade. Actual LGGO RGB controls, split toning, RGB curves, LCH controls, real rendered-preview waveform/vectorscope. Encoded display RGB, not a full ACES/DWG pipeline. |
| DCP library | 27 bundled profiles,17 added from supplied APK. Import/per-lens selection already existed. Camera-specific names are looks, not certified CMF calibration. |
| Settings | Searchable categories, All/Camera/Imaging/System filters, charcoal/gold cards and accessible rows;13 routes preserved. |
| Integrated computational16/24/50MP capture | Still pending. Current High Detail is a separate adaptive reconstruction path. Native50MP is not exposed by the provided A015 capability log. High targets need capability and memory-safe processing work. |
| Photo Sphere / saved overlays / directional diffusion / manual bracket extension | Audited candidates, not ported in this build. Existing unfused auto-EV bracketing remains. |
| Video follow/background/RAW-derived Log | Deferred to requested video phase; not claimed implemented. |

The original deep APK audit is retained for exact implementation evidence. It is historical and must not be read as the current feature-completion list. No Prismatica code was copied; standard grading math/UI was implemented in the existing recipe pipeline.
