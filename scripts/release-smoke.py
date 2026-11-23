"""Verify first launch and SQLCipher initialization on a fresh CI emulator, with no user data."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def wait_for(text):
    for _ in range(10):
        subprocess.run(['adb','shell','uiautomator','dump','/data/local/tmp/lasq-ui.xml'],stdout=subprocess.DEVNULL)
        doc=subprocess.run(['adb','shell','cat','/data/local/tmp/lasq-ui.xml'],capture_output=True).stdout
        try:
            for n in ET.fromstring(doc).iter('node'):
                if n.get('text') == text:
                    x,y,x2,y2=map(int,re.findall(r'\d+',n.get('bounds','')))
                    return str((x+x2)//2),str((y+y2)//2)
        except ET.ParseError:
            pass
        time.sleep(.4)
    raise RuntimeError('Release UI did not reach the expected state: '+text)

try:
    subprocess.check_call(['adb','shell','input','tap',*wait_for('استكشف التطبيق')])
    wait_for('لوحة التحكم')
    # Scroll until the recent-items empty state becomes visible on compact screens.
    w,h=map(int,re.search(r'(\d+)x(\d+)',subprocess.check_output(['adb','shell','wm','size']).decode()).groups())
    subprocess.check_call(['adb','shell','input','swipe',str(w//2),str(8*h//10),str(w//2),str(3*h//10),'430'])
    wait_for('سجلك جاهز لأول عنصر')
finally:
    subprocess.run(['adb','shell','rm','-f','/data/local/tmp/lasq-ui.xml'])
