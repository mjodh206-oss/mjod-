#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
خادم تحميل مَحلولة — أقوى من http.server العادي:
- HTTP/1.1 مع Content-Length صحيح
- دعم Range (استئناف التنزيل بعد الانقطاع)
- Content-Disposition: attachment لملفات APK/ZIP
- threaded: أكثر من تنزيل بنفس الوقت
"""
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote

DIST = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dist"))

MIME = {
    ".apk": "application/vnd.android.package-archive",
    ".zip": "application/zip",
    ".html": "text/html; charset=utf-8",
    ".png": "image/png",
    ".txt": "text/plain; charset=utf-8",
}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "MahloulaDL/1.1"

    def _file(self):
        p = unquote(self.path.split("?")[0])
        if p in ("/", ""):
            p = "/index.html"
        full = os.path.normpath(os.path.join(DIST, p.lstrip("/")))
        if not full.startswith(DIST) or not os.path.isfile(full):
            return None
        return full

    def _send(self, body=True):
        f = self._file()
        if not f:
            self.send_error(404, "Not found")
            return
        size = os.path.getsize(f)
        ctype = MIME.get(os.path.splitext(f)[1].lower(), "application/octet-stream")
        start, end = 0, size - 1
        code = 200
        rng = self.headers.get("Range")
        m = re.match(r"bytes=(\d*)-(\d*)", rng or "")
        if m and (m.group(1) or m.group(2)):
            if m.group(1):
                start = int(m.group(1))
                end = min(int(m.group(2)), size - 1) if m.group(2) else size - 1
            else:
                start = max(0, size - int(m.group(2)))
            if start <= end:
                code = 206
            else:
                start, end = 0, size - 1
        length = end - start + 1
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        if code == 206:
            self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
        if f.endswith((".apk", ".zip")):
            self.send_header("Content-Disposition",
                             'attachment; filename="%s"' % os.path.basename(f))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        if not body:
            return
        try:
            with open(f, "rb") as fh:
                fh.seek(start)
                sent = 0
                while sent < length:
                    chunk = fh.read(min(64 * 1024, length - sent))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    sent += len(chunk)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        self._send(True)

    def do_HEAD(self):
        self._send(False)

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    print("Mahloula download server on 0.0.0.0:8080 ->", DIST, flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
