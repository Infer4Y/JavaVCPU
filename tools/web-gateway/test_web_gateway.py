"""Loopback transport checks; no public sites or third-party Python packages."""
from contextlib import closing
import argparse
import sys
import gzip
import http.client
import shutil
import ssl
import subprocess
import tempfile
import threading
import unittest
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from web_gateway import make_server

GUEST_CLASSES = None


class Origin(BaseHTTPRequestHandler):
    def do_GET(self):
        data = b"Hello from the origin"
        self.send_response(302 if self.path == "/redirect" else 200)
        self.send_header("Content-Type", "text/plain")
        if self.path == "/redirect":
            self.send_header("Location", "/next?case=Keep")
        if self.path == "/gzip":
            data = gzip.compress(data)
            self.send_header("Content-Encoding", "gzip")
        if self.path == "/deflate":
            data = zlib.compress(data)
            self.send_header("Content-Encoding", "deflate")
        if self.path == "/chunk":
            self.send_header("Transfer-Encoding", "chunked")
            self.end_headers()
            for part in (data[:4], data[4:]):
                self.wfile.write(f"{len(part):x}\r\n".encode() + part + b"\r\n")
            self.wfile.write(b"0\r\n\r\n")
        else:
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

    def log_message(self, *args):
        pass


class GatewayChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.origin = ThreadingHTTPServer(("127.0.0.1", 0), Origin)
        cls.gateway = make_server(0)
        for server in (cls.origin, cls.gateway):
            threading.Thread(target=server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        for server in (cls.origin, cls.gateway):
            server.shutdown()
            server.server_close()

    def request(self, target):
        with closing(http.client.HTTPConnection("127.0.0.1", self.gateway.server_port, timeout=10)) as connection:
            connection.request("GET", target)
            response = connection.getresponse()
            return response.status, dict((k.lower(), v) for k, v in response.getheaders()), response.read()

    def test_streams_dechunks_and_decompresses(self):
        for path in ("/", "/chunk", "/gzip", "/deflate"):
            with self.subTest(path=path):
                status, headers, data = self.request(f"http://127.0.0.1:{self.origin.server_port}{path}")
                self.assertEqual(200, status)
                self.assertEqual(b"Hello from the origin", data)
                self.assertEqual("close", headers["connection"])
                self.assertNotIn("transfer-encoding", headers)
                self.assertNotIn("content-encoding", headers)

    def test_redirect_is_left_for_the_guest(self):
        status, headers, _ = self.request(f"http://127.0.0.1:{self.origin.server_port}/redirect")
        self.assertEqual(302, status)
        self.assertEqual("/next?case=Keep", headers["location"])

    def test_rejects_unsupported_urls(self):
        for target in ("file:///etc/passwd", "ftp://localhost/a", "http://user:password@localhost/a"):
            self.assertEqual(400, self.request(target)[0])
        self.assertEqual("127.0.0.1", self.gateway.server_address[0])
        self.assertEqual(ssl.CERT_REQUIRED, self.gateway.tls_context.verify_mode)
        self.assertTrue(self.gateway.tls_context.check_hostname)

    def test_real_tls_checks_certificate_and_hostname(self):
        openssl = shutil.which("openssl")
        if not openssl:
            self.skipTest("OpenSSL is needed to generate the temporary loopback certificate")
        with tempfile.TemporaryDirectory(prefix="jctrl-tls-") as folder:
            folder = Path(folder)
            cert, key, config = folder / "cert.pem", folder / "key.pem", folder / "openssl.cnf"
            config.write_text("[req]\nprompt=no\ndistinguished_name=dn\nx509_extensions=ext\n"
                              "[dn]\nCN=localhost\n[ext]\nsubjectAltName=DNS:localhost\n"
                              "basicConstraints=critical,CA:TRUE\n")
            subprocess.run([openssl, "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                            "-keyout", str(key), "-out", str(cert), "-days", "1", "-config", str(config)],
                           check=True, capture_output=True)
            tls = ThreadingHTTPServer(("127.0.0.1", 0), Origin)
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            context.load_cert_chain(cert, key)
            tls.socket = context.wrap_socket(tls.socket, server_side=True)
            threading.Thread(target=tls.serve_forever, daemon=True).start()
            try:
                target = f"https://localhost:{tls.server_port}/"
                status, _, data = self.request(target)
                self.assertEqual(502, status)
                self.assertIn(b"certificate", data.lower())
                self.gateway.tls_context.load_verify_locations(cafile=str(cert))
                trusted = self.request(target)
                self.assertEqual((200, b"Hello from the origin"), (trusted[0], trusted[2]))
                if GUEST_CLASSES:
                    result = subprocess.run(["java", "--class-path", GUEST_CLASSES,
                        "xyz.ignite4inferneo.BrowserRegression", "gateway", str(self.gateway.server_port), target],
                        cwd=Path(__file__).resolve().parents[2], capture_output=True, text=True, timeout=120)
                    self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                    self.assertIn("PASS: guest WEB.COM through the real verified-TLS gateway", result.stdout)
                self.assertEqual(502, self.request(f"https://127.0.0.1:{tls.server_port}/")[0])
            finally:
                tls.shutdown()
                tls.server_close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--guest-classes", help="Also run the compiled WEB guest through the actual TLS gateway")
    options, remaining = parser.parse_known_args()
    GUEST_CLASSES = options.guest_classes
    unittest.main(argv=[sys.argv[0], *remaining], verbosity=2)
