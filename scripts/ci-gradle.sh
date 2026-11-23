#!/usr/bin/env bash
set -uo pipefail
log=$(mktemp); trap 'rm -f "$log"' EXIT
./gradlew --console=plain "$@" 2>&1 | tee "$log" && exit 0
python3 - "$log" <<'PY'
import glob,re,sys,xml.etree.ElementTree as ET
lines=open(sys.argv[1],errors='replace').read().splitlines()
errors=[s for s in lines if re.search(r'^\s*(e:|error:|> (?!Task |Configure)|Execution failed|\* What went wrong)|error: | FAILED$|^.+ > .+ FAILED', s, re.I)]
for path in glob.glob('app/build/**/*lint*result*.xml', recursive=True):
    try:
        for issue in ET.parse(path).getroot().findall('issue'):
            if issue.get('severity') in ('Error','Fatal'):
                loc=issue.find('location')
                errors.append(f"{issue.get('id')} {loc.attrib if loc is not None else ''}: {issue.get('message')}")
    except ET.ParseError:
        pass
msg='\n'.join((errors or lines[-40:])[-100:]).replace('%','%25').replace('\n','%0A').replace('\r','%0D')
print('::error title=Gradle diagnostics::'+msg)
PY
exit 1
