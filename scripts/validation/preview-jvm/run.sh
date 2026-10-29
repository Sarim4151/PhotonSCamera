#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
: "${KOTLIN_LIB:?}"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
python3 - "$ROOT" "$WORK" <<'INNER'
from pathlib import Path
import sys
root,work=map(Path,sys.argv[1:])
s=(root/'app/src/main/java/com/hinnka/mycamera/camera/AdvancedCameraState.kt').read_text()
line=next(l for l in s.splitlines() if l.startswith('enum class AdvancedCameraMode'))
(work/'Mode.kt').write_text('package com.hinnka.mycamera.camera\n'+line+'\n')
INNER
CP="$KOTLIN_LIB/kotlin-stdlib-2.0.21.jar:$KOTLIN_LIB/kotlinx-coroutines-core-jvm-1.6.4.jar:$KOTLIN_LIB/annotations-24.0.1.jar"
java -cp "$KOTLIN_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$CP" -d "$WORK/classes" "$ROOT/scripts/validation/preview-jvm/Bitmap.kt" "$WORK/Mode.kt" "$ROOT/app/src/main/java/com/hinnka/mycamera/processor/PreviewFrameRegistration.kt" "$ROOT/app/src/main/java/com/hinnka/mycamera/processor/TemporalPreviewBuffer.kt" "$ROOT/scripts/validation/preview-jvm/RuntimeQa.kt"
java -Xmx512m -cp "$WORK/classes:$CP" com.hinnka.mycamera.processor.RuntimeQaKt
