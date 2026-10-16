import http.server, os, sys, time, urllib.parse

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "build")
LOG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "status.log")


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        if url.path == "/orvid.apk":
            data = open(os.path.join(ROOT, "orvid.apk"), "rb").read()
            self.send_response(200)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        elif url.path == "/status":
            msg = urllib.parse.parse_qs(url.query).get("m", [""])[0]
            with open(LOG, "a") as f:
                f.write("%d %s\n" % (time.time(), msg))
            self.send_response(204)
            self.end_headers()
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, *args):
        pass


http.server.ThreadingHTTPServer(("127.0.0.1", 8471), Handler).serve_forever()
