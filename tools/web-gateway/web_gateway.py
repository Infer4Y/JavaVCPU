#!/usr/bin/env python3
"""Optional local HTTP/TLS transport for WEB.COM; HTML still runs through the guest.

Run: python tools/web-gateway/web_gateway.py
Then press G in WEB's page/error view. WEB sends absolute HTTP(S) URLs to
127.0.0.1:8080. No VM device or CPU changes and no third-party packages required.
"""
import argparse
import http.client
import ssl
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, urlunsplit


class Gateway(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"
    server_version = "JCTRL-Gateway/0.3"
    sys_version = ""

    def do_GET(self):
        self.forward()

    def do_HEAD(self):
        self.forward()

    def forward(self):
        try:
            uri = urlsplit(self.path)
            if uri.scheme not in ("http", "https") or not uri.hostname:
                raise ValueError("Expected an absolute http:// or https:// URL")
            if uri.username is not None or uri.password is not None:
                raise ValueError("Credentials in URLs are not supported")
            port = uri.port or (443 if uri.scheme == "https" else 80)
            if not 1 <= port <= 65535:
                raise ValueError("Port must be between 1 and 65535")
        except ValueError as failure:
            self.plain_error(400, str(failure))
            return

        connection = None
        started = False
        try:
            if uri.scheme == "https":
                connection = http.client.HTTPSConnection(
                    uri.hostname, port, timeout=20, context=self.server.tls_context)
            else:
                connection = http.client.HTTPConnection(uri.hostname, port, timeout=20)
            target = urlunsplit(("", "", uri.path or "/", uri.query, ""))
            connection.request(self.command, target, headers={
                "User-Agent": "JCTRL-WEB/0.3",
                "Accept": "text/html, text/plain;q=0.9, */*;q=0.1",
                "Accept-Encoding": "identity",
                "Connection": "close",
            })
            response = connection.getresponse()
            encoding = response.getheader("Content-Encoding", "identity").lower().strip()
            if encoding not in ("identity", "gzip", "deflate"):
                raise ValueError("Unsupported server content encoding: " + encoding)
            decoder = (zlib.decompressobj(16 + zlib.MAX_WBITS) if encoding == "gzip"
                       else zlib.decompressobj() if encoding == "deflate" else None)
            hop = {"connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
                   "te", "trailer", "transfer-encoding", "upgrade", "content-length",
                   "content-encoding"}
            hop.update(part.strip().lower() for part in response.getheader("Connection", "").split(","))
            # Do not follow redirects here: the guest resolves Location and keeps
            # the actual target address and Back history visible to the user.
            self.send_response(response.status, response.reason)
            for name, value in response.getheaders():
                if name.lower() not in hop and "\r" not in value and "\n" not in value:
                    self.send_header(name, value)
            self.send_header("Connection", "close")
            self.end_headers()
            started = True
            if self.command != "HEAD":
                while chunk := response.read(16384):
                    if decoder:
                        while chunk:
                            self.wfile.write(decoder.decompress(chunk, 16384))
                            chunk = decoder.unconsumed_tail
                    else:
                        self.wfile.write(chunk)
                if decoder:
                    self.wfile.write(decoder.flush())
                    if not decoder.eof:
                        raise ValueError("Incomplete compressed response")
        except (OSError, ValueError, http.client.HTTPException, zlib.error) as failure:
            if not started:
                self.plain_error(502, "Upstream request failed: " + str(failure))
            else:
                self.log_error("incomplete upstream response: %s", failure)
        finally:
            self.close_connection = True
            if connection:
                connection.close()

    def plain_error(self, status, detail):
        data = ("JCTRL gateway: " + detail + "\n").encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)


def make_server(port=8080, cafile=None):
    server = ThreadingHTTPServer(("127.0.0.1", port), Gateway)
    server.daemon_threads = True
    server.tls_context = ssl.create_default_context(cafile=cafile)
    return server


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--cafile", help="Trusted CA bundle for local/private TLS sites")
    args = parser.parse_args()
    with make_server(args.port, args.cafile) as server:
        print(f"JCTRL gateway on 127.0.0.1:{server.server_port}; TLS certificates verified.", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
