#!/usr/bin/env bash
set -euo pipefail
adb install -r "$(find test-apks -name 'app-debug.apk' -print -quit)"
adb install -r -t "$(find test-apks -name '*androidTest.apk' -print -quit)"
for fixture in $(find test-apks -name 'fixtures-*-debug.apk'); do adb install -r "$fixture"; done
adb shell am instrument -w -e ciSmoke true dev.lasq.fawri.debug.test/androidx.test.runner.AndroidJUnitRunner | tee /tmp/lasq-tests.txt
if ! grep -q '^OK ([0-9]* tests\?)' /tmp/lasq-tests.txt; then
  python3 -c 'print("::error title=Device tests::"+open("/tmp/lasq-tests.txt").read()[-16000:].replace("%","%25").replace("\n","%0A").replace("\r","%0D"))'; exit 1
fi
# Smoke-test the actual signed, minified release, not just the debug variant.
adb install -r release-apk/lasq-fawri-1.0-preview.apk
adb shell am start -W -n dev.lasq.fawri.preview/dev.lasq.fawri.ui.MainActivity
python3 scripts/release-smoke.py
