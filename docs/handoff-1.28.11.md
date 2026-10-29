# PhotonSCamera 1.28.11 (170)

## Logged failure and fix

The supplied Nothing A015 log shows a successful RAW source capture and completed gallery registration, then:

- `RAW_ADAPTIVE_EXPOSURE ... mode=PHOTON_HDR ... capturePreview=false`
- `HDRNet PGTM unavailable: complete viewfinder target, ML AE HDR ratio, or final-short gain is missing`
- ML AE had valid ratio=1.0 and final-short gain=1.6695484, so the missing viewfinder target is the failing precondition.
- `GPU RAW profile preparation failed`, followed by an incorrect `Image saved` message and `terminal ... saved=false`.

Advanced/AddMe source submission deliberately skipped ordinary thumbnails. That also removed the original preview required by the RAW HDR profile coordinator. RAW save returns Unit and can exit early, so the caller had marked the photo saved without a written original.

RAW sequence sources now capture an original viewfinder reference (bounded to 640 pixels long edge) and await it before RAW processing. The same original-preview source and rotation used by normal photos are preserved. Each capture owns its bitmap until processing completes; rejected/failed/timed-out captures close the owner and late callbacks recycle their images. No reference is shown in the AddMe gallery or used as the output photo. HDR, DCP and the selected engine remain active.

The sequence save path checks that a nonempty rendered original exists before marking success. Failure is explicit. The ownership probe covers callback ordering, pending failure, cancellation, duplicates, late values, idempotent cleanup and 500 offer/close races.

## Toolbar

An enabled timer now shows its localized duration beneath the icon and in its accessibility description. Compact five-control layout, 48dp targets and safe insets are retained.

## APK comparison and next steps

Read reference-feature-summary.md and reference-apk-deep-comparison.md. Computational YUV super-resolution, general subject AF, video face-follow, extra DCP content and additional photo tools were identified; they have not been copied into this repair build. Existing Adobe Curve/DCP handling and High Detail SR must not be counted as missing wholesale. Complete and test photos before the video phase.

## Device validation

The logged RAW missing-reference cause is addressed in code, with source review and host checks. No phone is connected here, so confirm on the CMF Phone1. Expected logs: `RAW source reference captured`, `RAW source reference ready for processing`, `capturePreview=true`, successful RAW profile preparation and `terminal ... saved=true`. Test both AddMe shots/final merge and RAW advanced sources. This does not establish a fix for every possible YUV/driver failure without its logs.

The capability log exposes max4096x3072 JPEG/YUV and4000x3000 RAW, no maximum-resolution request/map on the current firmware. Computational50MP is a reconstructed output mode, not evidence of native sensor access.
