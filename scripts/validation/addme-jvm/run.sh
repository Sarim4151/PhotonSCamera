#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
: "${KOTLIN_LIB:?Set KOTLIN_LIB to a Kotlin compiler distribution lib directory}"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
# Keep production mask sampling, alignment and compositing unchanged. Android ML Kit
# segmentation cannot run on the desktop and is deliberately outside this probe.
python3 - "$ROOT" "$WORK" <<'PY'
from pathlib import Path
import sys
root, work=map(Path,sys.argv[1:])
s=(root/'app/src/main/java/com/hinnka/mycamera/processor/PersonCompositeTools.kt').read_text()
s='\n'.join(l for l in s.splitlines() if not l.startswith('import com.google.'))
a=s.index('    suspend fun segment(')
b=s.index('    /** Translation fit',a)
(work/'PersonCompositeTools.kt').write_text(s[:a]+s[b:])
s=(root/'scripts/validation/sr-jvm/Bitmap.kt').read_text()
s=s.replace(' fun recycle()', ' fun getPixel(x:Int,y:Int):Int {check(!isRecycled);return pixels[y*width+x]}\n fun copy(config:Config,mutable:Boolean):Bitmap=Bitmap(width,height,pixels.copyOf())\n fun recycle()')
(work/'Bitmap.kt').write_text(s)
PY
CP="$KOTLIN_LIB/kotlin-stdlib-2.0.21.jar:$KOTLIN_LIB/kotlinx-coroutines-core-jvm-1.6.4.jar:$KOTLIN_LIB/annotations-24.0.1.jar"
java -cp "$KOTLIN_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$CP" -d "$WORK/classes" "$WORK/Bitmap.kt" "$WORK/PersonCompositeTools.kt" "$ROOT/app/src/main/java/com/hinnka/mycamera/processor/RegionCompositeTools.kt" "$ROOT/scripts/validation/addme-jvm/RuntimeQa.kt"
java -cp "$WORK/classes:$CP" com.hinnka.mycamera.processor.RuntimeQaKt
