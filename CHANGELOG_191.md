# Build191 — debug4 viewfinder reference and Phocus-inspired paste

Version1.28.32-dev (191). Restore reference input transfer from uploaded PhotonSCamera-debug-4.apk, whose SHA256 exactly matches archived build173. All1025 ACR3 samples and inverse shader match APK; sRGB decode then inverse ACR3 before engine, output sRGB once. Retain atlas efficiency and latest capture/video/LUT features. Remove190 camera tone-curve override so engine selection no longer changes camera template tone mapping. Metadata diagnostics identify debug4 reference.

This restores the user-selected look; ACR3 is not claimed to be a calibrated CMF ISP inverse. Newer engine algorithm/settings changes can still differ from old output. Physical CMF viewfinder and recording validation remains pending.

Phocus Mobile1.1.0 APK audit found crop, frames and broad edit controls already present. Adds original selective adjustment paste in editor/detail/batch: Color,Detail,Frame,Geometry,Portrait; defaultsColor+Detail, empty selection blocked. Shares merge model; preserves target capture/video metadata and unselected groups. Detail separates recipe texture controls and respects existing RAW source/target protection. Copies only frame renderer-owned custom properties. Does not copy proprietary Phocus code/assets/models or claim Hasselblad remote-camera/HNNR support.

Host actual GLES transfer draw/readback258samples matched CPU reference exactly atRGBA8 precision; atlas identity37x19,peaking,zebra,magnification checks pass. Full Android build/tests viaCI pending at source commit time.
