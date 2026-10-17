import http.server, os, re, sys, threading, time, urllib.parse

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "build")
LOG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "status.log")
SHOTS = "/storage/emulated/0/Claude/tmp"
QUEUE = []
READY = threading.Condition()


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        if url.path == "/orvid.apk":
            data = open(os.path.join(ROOT, "orvid.apk"), "rb").read()
            self.send_response(200)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        elif url.path == "/cmd":
            with READY:
                READY.wait_for(lambda: QUEUE, timeout=10)
                cmd = QUEUE.pop(0) if QUEUE else None
            if cmd is None:
                self.send_response(204)
                self.end_headers()
            else:
                body = cmd.encode()
                self.send_response(200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
        elif url.path == "/status":
            msg = urllib.parse.parse_qs(url.query).get("m", [""])[0]
            with open(LOG, "a") as f:
                f.write("%d %s\n" % (time.time(), msg))
            self.send_response(204)
            self.end_headers()
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        url = urllib.parse.urlparse(self.path)
        kind = url.path.strip("/")
        name = re.sub(r"[^A-Za-z0-9_\-]", "_", urllib.parse.parse_qs(url.query).get("name", ["shot"])[0])
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        if kind == "clear":
            with READY:
                QUEUE.clear()
            self.send_response(204)
            self.end_headers()
            return
        if kind == "push":
            with READY:
                QUEUE.append(body.decode())
                READY.notify_all()
            self.send_response(204)
            self.end_headers()
            return
        if kind in ("shot", "tree"):
            os.makedirs(SHOTS, exist_ok=True)
            path = os.path.join(SHOTS, "dev_%s.%s" % (name, "png" if kind == "shot" else "txt"))
            with open(path + ".part", "wb") as f:
                f.write(body)
            os.replace(path + ".part", path)
            self.send_response(204)
        else:
            self.send_response(404)
        self.end_headers()

    def log_message(self, *args):
        pass


http.server.ThreadingHTTPServer(("127.0.0.1", 8471), Handler).serve_forever()
