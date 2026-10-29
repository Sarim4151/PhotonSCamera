# Handoff 1.28.12

## High Detail repair

The reported phase exception was a mandatory2x sampling condition, not a camera acquisition failure. True2x still requires4 independent phases, both axes and adequate coverage. Missing samples produce an explicitly labelled aligned result at the selected working resolution; unalignable frames preserve the best valid reference. Any source-motion disagreement preserves reference pixels. Metadata records reconstruction type/fallback reason and actual output dimensions.

Only HighDetail source sessions receive advancedPrivateSource=true, captureMode=advanced_source, autosave=false. DAO public queries exclude those rows while direct lookup remains available. No per-source placeholders/events/thumbnails are published. Sources are deleted only with matching private marker+sequence ownership; queued cleanup and late terminal deletion cover cancellation/in-flight sources. Existing old separately saved user photos are not deleted. Deliberate SourceBurst/unfused bracket output remains separate. Process-kill orphan cache handling is a future cleanup consideration; normal terminal sessions drain their owned sources.

HighDetail has zero inter-frame gap even with old saved settings. Private originals retain native RAW/DNG/HDR/DCP/engine work; unused bokeh/gainmap/cache/thumb/export passes are skipped. Recipe processing is preserved for merge; redundant ARGB copy removed. Per-stripe low-frequency prefiltering avoids repeated sampling. Progress now shows processing counts. RAW is still inherently slower than Photo/YUV; no measured CMF speed claim.

## Photo/Pro rule

Preview engine resolver now requires actual Photo+useRaw+RAW-supported Pro state and RAW baseline target. Advanced Photo skips engine waiting and captures Original preview; Pro captures engine output. Source engine plan only exists for Pro. This also handles unsupported-RAW lens preference carry-over. The previous required RAW reference fix remains active for Pro sources.

## New tools

See reference-feature-summary.md for locations. Subject AF shares existing focus callbacks with normalized targets and an opt-in detector; disabling queues detector release and invalidates stale generation. Pose models run on their owner dispatcher with initialization ownership assigned before cancellation handoff. Expression preview handles memory/render errors and preserves original/cache-owned sources. BestMoment/expression save copies validate writtenJPEG and roll back failed allocations. LGGO persists through JSON/import/renderer snapshots and Room51→52; defaults preserve old recipes and null embedded recipes.

## Limits and next work

No native50MP exposure was found in supplied A015 logs. Computational target16/24/50MP integration and PhotoSphere/overlays/manual bracket refinements remain candidates; do not advertise them as added. New grading is encoded RGB, not full Prismatica colour management/video RAW import. Expression edits are subtle geometry, not generative face replacement. Model accuracy, UI and native performance require phone testing.

Preserve debug keystore and package. User authorized continued work without repeated approvals. Finish/validate photos before video phase. Build/test and provenance documents are included; checkpoint Library file PhotonCamera-progress.zip contains an earlier recovery snapshot, while PhotonSCamera-main.zip is the verified deliverable.
