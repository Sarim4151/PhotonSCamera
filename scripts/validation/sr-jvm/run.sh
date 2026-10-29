#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
: "${KOTLIN_LIB:?Set KOTLIN_LIB to a Kotlin compiler distribution lib directory}"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
uv run --with numpy --with scipy python "$ROOT/scripts/validation/sr-jvm/fixtures.py" generate "$WORK"
CP="$KOTLIN_LIB/kotlin-stdlib-2.0.21.jar:$KOTLIN_LIB/kotlinx-coroutines-core-jvm-1.6.4.jar:$KOTLIN_LIB/annotations-24.0.1.jar"
java -cp "$KOTLIN_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$CP" -d "$WORK/classes" "$ROOT/scripts/validation/sr-jvm/Bitmap.kt" "$ROOT/scripts/validation/sr-jvm/RuntimeQa.kt" "$ROOT/app/src/main/java/com/hinnka/mycamera/processor/SuperResolutionProcessor.kt"
java -Xmx2g -cp "$WORK/classes:$CP" com.hinnka.mycamera.processor.RuntimeQaKt "$WORK"
uv run --with numpy --with scipy python "$ROOT/scripts/validation/sr-jvm/fixtures.py" verify "$WORK"
