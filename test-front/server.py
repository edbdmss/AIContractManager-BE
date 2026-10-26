from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).parent


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split("?", 1)[0]
        filename = "wireframe.html" if path == "/wireframe.html" else "index.html"
        if path not in ("/", "/oauth/callback", "/wireframe.html"):
            self.send_error(404)
            return
        page = (ROOT / filename).read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(page)))
        self.end_headers()
        self.wfile.write(page)

    def log_message(self, *_):
        return


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 3000), Handler)
    print("Test frontend: http://localhost:3000", flush=True)
    server.serve_forever()
