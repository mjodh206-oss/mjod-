#!/usr/bin/env python3
"""Static server + landing page so the video can be watched / downloaded."""
import http.server
import os
import socketserver

PORT = int(os.environ.get('PORT', '8000'))
DIR = os.path.dirname(os.path.abspath(__file__))
VID = 'yt_short_7tips.mp4'
VID_SMALL = 'yt_short_7tips_720p.mp4'

HTML = """<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>7 نصائح بالذكاء الاصطناعي — Dify + Stable Diffusion</title>
<style>
  *{box-sizing:border-box}
  body{margin:0;background:#0a0714;color:#fff;font-family:system-ui,Segoe UI,Tahoma,sans-serif;
       display:flex;flex-direction:column;align-items:center;padding:28px 16px;gap:18px}
  h1{font-size:clamp(20px,4.5vw,34px);margin:0;text-align:center}
  p.sub{color:#b9b3d6;margin:0;text-align:center;max-width:640px}
  video{width:min(360px,92vw);border-radius:18px;box-shadow:0 20px 60px rgba(139,92,246,.35);background:#000}
  .row{display:flex;gap:12px;flex-wrap:wrap;justify-content:center}
  a.btn{display:inline-block;padding:14px 26px;border-radius:999px;text-decoration:none;font-weight:700;
        background:linear-gradient(135deg,#8b5cf6,#22d3ee);color:#fff}
  a.btn.ghost{background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.2)}
  img.thumb{width:min(560px,94vw);border-radius:14px}
  .meta{color:#8f89ad;font-size:13px}
</style>
</head>
<body>
  <h1>7 نصائح بالذكاء الاصطناعي — Dify + Stable Diffusion</h1>
  <p class="sub">شورت جاهز للنشر (1080×1920 — 9:16) · المدة 1:39</p>
  <video controls autoplay muted loop playsinline src="/__video__"></video>
  <div class="row">
    <a class="btn" href="/download">⬇ تحميل 1080p (25MB)</a>
    <a class="btn ghost" href="/download_small">تحميل 720p (6MB)</a>
    <a class="btn ghost" href="/__kit__">كت النشر</a>
  </div>
  <img class="thumb" src="/__thumb__" alt="thumbnail">
  <div class="meta">_DIRECT: /__video__ · /download</div>
</body>
</html>
"""


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def _send(self, code, body, ctype, extra=None):
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Cache-Control', 'no-store')
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(body)

    def _file(self, path, attachment=None):
        try:
            with open(path, 'rb') as f:
                body = f.read()
        except OSError:
            self._send(404, b'not found', 'text/plain; charset=utf-8')
            return
        ctype = 'application/octet-stream'
        if path.endswith('.mp4'):
            ctype = 'video/mp4'
        elif path.endswith('.png'):
            ctype = 'image/png'
        elif path.endswith('.md'):
            ctype = 'text/markdown; charset=utf-8'
        extra = {}
        if attachment:
            extra['Content-Disposition'] = f'attachment; filename="{attachment}"'
        self._send(200, body, ctype, extra)

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        path = self.path.split('?')[0]
        if path in ('/', '/index.html'):
            self._send(200, HTML.replace('__video__', VID)
                              .replace('__kit__', 'publishing_kit.md')
                              .replace('__thumb__', 'thumbnail_1280x720.png')
                              .encode('utf-8'),
                       'text/html; charset=utf-8')
        elif path == f'/{VID}':
            self._file(os.path.join(DIR, VID))
        elif path == f'/{VID_SMALL}':
            self._file(os.path.join(DIR, VID_SMALL))
        elif path == '/download':
            self._file(os.path.join(DIR, VID), VID)
        elif path == '/download_small':
            self._file(os.path.join(DIR, VID_SMALL), VID_SMALL)
        elif path == '/publishing_kit.md':
            self._file(os.path.join(DIR, 'publishing_kit.md'))
        elif path == '/thumbnail_1280x720.png':
            self._file(os.path.join(DIR, 'thumbnail_1280x720.png'))
        else:
            self._send(404, b'not found', 'text/plain; charset=utf-8')

    def log_message(self, *args):
        pass


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == '__main__':
    with Server(('0.0.0.0', PORT), Handler) as httpd:
        print(f'serving {DIR} on 0.0.0.0:{PORT}', flush=True)
        httpd.serve_forever()
