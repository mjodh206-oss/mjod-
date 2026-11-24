"""Verify first launch and SQLCipher initialization on a fresh CI emulator, with no user data."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def wait_for(text, attr="text"):
    for _ in range(10):
        subprocess.run(['adb','shell','uiautomator','dump','/data/local/tmp/lasq-ui.xml'],stdout=subprocess.DEVNULL)
        doc=subprocess.run(['adb','shell','cat','/data/local/tmp/lasq-ui.xml'],capture_output=True).stdout
        try:
            for n in ET.fromstring(doc).iter('node'):
                if n.get(attr) == text:
                    x,y,x2,y2=map(int,re.findall(r'\d+',n.get('bounds','')))
                    return str((x+x2)//2),str((y+y2)//2)
        except ET.ParseError:
            pass
        time.sleep(.4)
    raise RuntimeError('Release UI did not reach the expected state: '+text)

pkg = 'dev.lasq.fawri.simple.preview'
activity = pkg + '/dev.lasq.fawri.ui.MainActivity'
try:
    subprocess.check_call(['adb','shell','input','tap',*wait_for('بدء الحفظ')])
    # Copy in the actual external fixture; no user needs to push a Paste/Save button in the app.
    subprocess.check_call(['adb','shell','am','start','-W','-n','dev.lasq.fixture.source/dev.lasq.fixture.Editor'])
    subprocess.check_call(['adb','shell','input','tap',*wait_for('copy-instant','content-desc')])
    subprocess.check_call(['adb','shell','am','start','-W','-n',activity])
    wait_for('LASQ_FIXTURE_instant')
    subprocess.check_call(['adb','shell','am','force-stop',pkg]); subprocess.check_call(['adb','shell','am','start','-W','-n',activity])
    wait_for('LASQ_FIXTURE_instant')
finally:
    subprocess.run(['adb','shell','rm','-f','/data/local/tmp/lasq-ui.xml'])
