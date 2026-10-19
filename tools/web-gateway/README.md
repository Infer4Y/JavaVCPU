# WEB's optional TLS transport

Run `python tools/web-gateway/web_gateway.py` from the repository root, keep
the console running, and press **G** in WEB's page/error view. The guest uses
127.0.0.1:8080. A retained HTTPS redirect can then be retried without retyping it.
The page header shows `[gateway]` while enabled. Press G again to return to direct
HTTP. Ctrl+C stops the helper. Starting WEB does not start a host service.

The standard-library helper relays absolute HTTP(S) GET/HEAD requests. It uses
verified TLS, preserves upstream status and Location, strips hop-by-hop framing,
and streams dechunked bodies. gzip and zlib-wrapped deflate bodies are decoded.
It does not render HTML, run JavaScript, solve site challenges, follow redirects,
or supply browser cookies or authentication. A server's 403 remains a 403.

TLS verification uses Python's default trusted CA store. `--cafile path.pem`
selects a trusted CA bundle for private/local sites; there is no insecure TLS
switch. `--port` is useful for transport tests, but WEB's default is fixed at
8080, so keep that default for normal use.

The guest renderer and TCP controller stay unchanged by this helper. WEB's
redirect/relative-URL resolver follows the [URI reference resolution model in
RFC 3986](https://www.rfc-editor.org/rfc/rfc3986.html#section-5.2). The TLS client
uses Python's [HTTPSConnection](https://docs.python.org/3/library/http.client.html#http.client.HTTPSConnection).

Run `python tools/web-gateway/test_web_gateway.py` for loopback checks covering
HTTP, preserved redirects, chunking, gzip/deflate and real TLS. The TLS test uses
OpenSSL to generate a temporary test certificate and checks rejection of an
untrusted certificate and a mismatched hostname, then acceptance with the test
CA explicitly trusted. All temporary servers and certificates are cleaned up.

After compiling the Java regressions, add
`--guest-classes build/classes/storageRegression` to also run the actual
WEB.COM guest through the gateway into the verified local HTTPS fixture.
