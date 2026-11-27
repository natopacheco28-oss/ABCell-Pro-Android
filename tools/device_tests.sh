#!/usr/bin/env bash
# Run inside android-emulator-runner; only synthetic receipts from instrumentation tests are exported.
set -uo pipefail
result=0
./tools/ci_gradle.sh connectedDebugAndroidTest || result=$?
mkdir -p app/build/receipt-previews
adb pull /sdcard/Android/data/com.abcell.movil/files/receipt-previews/. app/build/receipt-previews/ || true
if [ -f docs/references/nota-pc.png ]; then cp docs/references/nota-pc.png app/build/receipt-previews/referencia-nota-PC.png; fi
exit "$result"
