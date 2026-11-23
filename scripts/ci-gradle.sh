#!/usr/bin/env bash
set -uo pipefail
log=$(mktemp); trap 'rm -f "$log"' EXIT
./gradlew --console=plain "$@" 2>&1 | tee "$log" && exit 0
python3 - "$log" <<'PY'
import re,sys
lines=open(sys.argv[1],errors='replace').read().splitlines()
errors=[s for s in lines if re.search(r'^(e:|error:|> |Execution failed|What went wrong)|error: | FAILED$|^.+ > .+ FAILED', s)]
msg='\n'.join((errors or lines[-30:])[:100]).replace('%','%25').replace('\n','%0A').replace('\r','%0D')
print('::error title=Gradle diagnostics::'+msg)
PY
exit 1
