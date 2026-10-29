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
s=(root/'app/src/main/java/com/hinnka/mycamera/camera/Camera2Controller.kt').read_text()
a=s.index('    suspend fun awaitPhotoCaptureReady(')
b=s.index('    private fun captureImageRequestCount(',a)
template=(root/'scripts/validation/advanced-jvm/AdmissionQa.kt.in').read_text()
(work/'AdmissionQa.kt').write_text(template.replace('// PRODUCTION_READINESS_METHOD',s[a:b]))
INNER
CP="$KOTLIN_LIB/kotlin-stdlib-2.0.21.jar:$KOTLIN_LIB/kotlinx-coroutines-core-jvm-1.6.4.jar:$KOTLIN_LIB/annotations-24.0.1.jar"
java -cp "$KOTLIN_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$CP" -d "$WORK/classes" "$WORK/AdmissionQa.kt"
java -cp "$WORK/classes:$CP" AdmissionQaKt
