# Additional rendering engines

Three original pixel-local tone operators join the engine selector and preset serialization: Neutral Tone, Soft Portrait, and Silver Monochrome. These are tone-rendering engines, not proprietary camera-brand replicas or denoise/demosaic algorithms.

Scene-linear sRGB input uses the existing calibrated RAW transform; the existing shared output transform follows the operator. Neutral and Portrait operate on luminance and compress chroma towards the neutral axis to fit the RGB cube. Their neutral axis is unchanged in hue. All three fix scene 18% grey at 18% output linear light and map black to black. Neutral contrast exponent is 1; Portrait is 0.88 with chroma 0.92; Silver converts linear luminance to grey and uses exponent 1.18. Positive highlights approach white monotonically. No LUT assets, neural model or additional render pass is required.

Shared RAW/ISP-restored preview, export, HDR base/reference shader construction and preset names use the existing engine architecture. Camera source calibration and output colour conversion remain in their established stages. DCP tone/look rendering remains specific to Adobe Curve as before; these engines are not certified colourimetric matches. Additional grading, LUTs or white balance can intentionally tint monochrome. Advanced Photo/Pro routing is unchanged.

Implementation: OriginalToneAlgorithms.kt; registration and release in RawEngineTonePass.kt; persistent identifiers in RawRenderingEngine.kt; English/Chinese selector descriptions and preset labels. Device GLES and photographic quality validation remains necessary.

## Verification

The original-operator functions were compiled, linked, drawn and read back in a host surfaceless OpenGL ES 3.2 Mesa llvmpipe context. Black, 18% grey, white, overrange neutral, saturated and negative-channel samples passed. This validates the operators on a software GLES driver; it does not establish Android driver or end-to-end camera equivalence. An independent numerical sampling probe checked 22,001 values per operator for finite in-gamut output, a monotonic neutral ramp and 18% grey preservation. See verification-development/original-engines. The existing NDK shader validator test now includes all five shared shader variants for each added engine.

## Sony-inspired Natural

A fourth engine has its own continuous toe/shoulder operator: below 18% grey, t=0.18*(y/0.18)^1.08; above it, t=0.18+0.82*(1-exp(-1.08*(y-0.18)/0.82)). Value and first derivative agree at the join. Chroma is restrained (0.97) with gradual highlight desaturation to 0.85 between scene luminance 1 and 8; gamut fitting preserves the hue direction. The source camera calibration is retained. It makes no skin detection, sharpening reduction or Sony sensor-calibration substitution.

Sony describes ST as a general natural finish, NT as lower saturation, and PT as softer skin reproduction. Those qualitative goals inform this independent interpretation; the numerical curve and colour decisions above are original and not derived Sony transforms. The UI explicitly labels this as Sony-inspired, not an exact Sony camera match. Reference: https://www.sony.co.in/interchangeable-lens-cameras/creativelook and https://helpguide.sony.net/ilc/2410/v1/en/contents/0411B_creative_look.html .
