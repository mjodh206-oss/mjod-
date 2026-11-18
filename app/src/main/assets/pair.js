'use strict';
const grant=location.hash.slice(1);
const button=document.getElementById('open');
if(location.protocol==='https:'&&/^v1\.\d{9,11}\.[A-Za-z0-9_-]{24}\.[A-Za-z0-9_-]{43}$/.test(grant)){
  const relay=`wss://${location.host}/relay`;
  // Keep the signed grant in a URI fragment so it never reaches HTTP request logs.
  const arg=`relay=${encodeURIComponent(relay)}&grant=${encodeURIComponent(grant)}`;
  button.href=/Chrome|Chromium/i.test(navigator.userAgent)
    ?`intent://connect/?${arg}#Intent;scheme=instant-interpreter;package=app.amjad.voice;end`
    :`instant-interpreter://connect/?relay=${encodeURIComponent(relay)}#${grant}`;
} else {button.removeAttribute('href');button.setAttribute('aria-disabled','true');}
