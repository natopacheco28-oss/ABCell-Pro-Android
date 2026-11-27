#!/usr/bin/env bash
# Keep diagnostics visible as check annotations as well as in the full Actions log.
set -uo pipefail
log_file="${RUNNER_TEMP:-/tmp}/abcell-gradle-$$.log"
./gradlew --no-daemon --console=plain "$@" 2>&1 | tee "$log_file"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  python3 - "$log_file" <<'PY'
from pathlib import Path
import sys
text = Path(sys.argv[1]).read_text(errors='replace')[-24000:]
text = text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
print('::error title=Diagnóstico Gradle::' + text)
PY
fi
exit "$status"
