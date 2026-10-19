"""Opt-in public-site smoke test using the actual WEB.COM/JCTRL guest bytecode.

python tools/web-gateway/test_live_browser.py --guest-classes build/classes/storageRegression
Requires network access; deliberately excluded from the offline regression suite.
"""
import argparse
from pathlib import Path
import subprocess
import threading

from web_gateway import make_server


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--guest-classes", required=True)
    parser.add_argument("--direct", action="store_true", help="Use guest TCP directly, without the TLS gateway")
    parser.add_argument("urls", nargs="*", default=["https://theoldnet.com/lite"])
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    with make_server(0) as server:
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            for url in args.urls:
                subprocess.run(["java", "--class-path", str(Path(args.guest_classes).resolve()),
                                "xyz.ignite4inferneo.BrowserRegression", "live",
                                "0" if args.direct else str(server.server_port), url],
                               cwd=root, check=True, timeout=300)
        finally:
            server.shutdown()


if __name__ == "__main__":
    main()
