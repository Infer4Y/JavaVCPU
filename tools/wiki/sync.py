"""Copy reviewed docs/wiki sources into the GitHub wiki checkout, or check parity."""
import argparse
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs" / "wiki"
DESTINATION = ROOT / "wiki"
REPO = "https://github.com/Infer4Y/JavaVCPU"
IMAGE_BASE = "https://raw.githubusercontent.com/wiki/Infer4Y/JavaVCPU/"


def publish_markdown(text):
    def replace_link(match):
        target = match.group(1)
        if target.startswith("images/"):
            target = IMAGE_BASE + target
        elif target.startswith("../../"):
            target = REPO + "/blob/master/" + target[6:]
        elif re.match(r"^[\w-]+\.md(?:#.*)?$", target):
            target = target.replace(".md", "", 1)
        return "](" + target + ")"
    return re.sub(r"\]\(([^)]+)\)", replace_link, text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail if the checkout differs; do not write")
    args = parser.parse_args()
    if not (DESTINATION / ".git").exists():
        parser.error("Initialize the wiki first: git submodule update --init wiki")
    remote = subprocess.check_output(["git", "-C", str(DESTINATION), "remote", "get-url", "origin"], text=True).strip()
    if remote != REPO + ".wiki.git":
        parser.error("wiki/origin is not the expected JavaVCPU wiki repository")
    changed = []
    for source in sorted(SOURCE.rglob("*")):
        if not source.is_file():
            continue
        target = DESTINATION / source.relative_to(SOURCE)
        content = (publish_markdown(source.read_text(encoding="utf-8")).encode("utf-8")
                   if source.suffix == ".md" else source.read_bytes())
        # Git checkouts may use CRLF on Windows; compare Markdown as text.
        matches = target.exists() and (
            target.read_text(encoding="utf-8") == content.decode("utf-8")
            if source.suffix == ".md" else target.read_bytes() == content
        )
        if not matches:
            changed.append(str(source.relative_to(SOURCE)))
            if not args.check:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(content)
    print(("Out of sync: " if args.check else "Updated: ") + ", ".join(changed) if changed else "Wiki sources and checkout match.")
    return int(args.check and bool(changed))


if __name__ == "__main__":
    raise SystemExit(main())
