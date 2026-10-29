# Production Kotlin super-resolution runtime validation

Executed 2026-10-22 on a desktop JVM. This compiles and executes the **actual** `app/src/main/java/com/hinnka/mycamera/processor/SuperResolutionProcessor.kt`; it does not execute a Kotlin or Python reconstruction mirror. The only substitutes are a minimal in-memory `android.graphics.Bitmap` and the result data carrier. Kotlin 2.0.21 and real kotlinx-coroutines-core-jvm 1.6.4 were used. No Android device, Android Bitmap implementation, Camera2 acquisition, gallery export, or UI was exercised.

Reproduce from repository root:

```bash
KOTLIN_LIB=/path/to/gradle-8.13/lib bash scripts/validation/sr-jvm/run.sh
```

The script uses `uv` with numpy/scipy for deterministic forward fixtures and reference metrics, and `java` for compilation/execution. Compiler dependencies are expected with the versioned filenames in the script. Temporary binary fixtures and compiled classes are removed after each run.

- Static 128×128 burst: four independent half-pixel phases of one blurred random continuous scene, encoded to 8-bit sRGB. Production returned 256×256, accepted all four frames, and preserved caller-owned inputs. Linear-light MSE over an inset region was 2.945258670e-6 against 1.201048978e-5 for reference-frame bicubic: ratio 0.245224.
- Identical burst: production rejected four references to the same input with `Insufficient independent subpixel phases for super resolution`.
- Moving 1152×96 burst: four independent phases; a bright rectangle moves six source pixels per frame entirely left of the central registration window (which begins at x=64). Production returned 2304×192 and accepted all four frames. Across 2,736 output pixels in the swept edge strip, output exactly matched the sRGB-quantized linear reference interpolation (zero mismatches). This exercises the production local inconsistency mask and reference fallback outside the registration crop, across multiple reconstruction stripes.

These are two accepted synthetic bursts, one identical-burst rejection, and one swept-edge region check; they are not broad real-scene quality or performance measurements. The fallback comparison includes the same final sRGB quantization as production. Comparing against unquantized interpolation would incorrectly count expected encoding differences as failures.

Raw output: `sr-jvm-runtime-results.txt`.
