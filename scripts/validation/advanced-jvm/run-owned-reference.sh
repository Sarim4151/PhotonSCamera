#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
: "${KOTLIN_LIB:?}"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
CP="$KOTLIN_LIB/kotlin-stdlib-2.0.21.jar:$KOTLIN_LIB/kotlinx-coroutines-core-jvm-1.6.4.jar:$KOTLIN_LIB/annotations-24.0.1.jar"
java -cp "$KOTLIN_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$CP" -d "$WORK/classes" "$ROOT/app/src/main/java/com/hinnka/mycamera/camera/OwnedCaptureReference.kt" "$ROOT/scripts/validation/advanced-jvm/OwnedCaptureReferenceQa.kt"
java -cp "$WORK/classes:$CP" com.hinnka.mycamera.camera.OwnedCaptureReferenceQaKt
