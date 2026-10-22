"""Check wiki links, images, ISA/ABI references, and generated bundle consistency."""
import base64
from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parents[2]
WIKI = ROOT / "docs" / "wiki"


def anchors(path):
    text = path.read_text(encoding="utf-8")
    result = set(re.findall(r'<a\s+(?:id|name)="([^"]+)"', text))
    counts = {}
    fenced = False
    for line in text.splitlines():
        if line.startswith(("```", "~~~")):
            fenced = not fenced
        if fenced:
            continue
        match = re.match(r"^#{1,6}\s+(.+)$", line)
        if match:
            slug = re.sub(r"[^\w -]", "", match[1].lower()).replace(" ", "-")
            count = counts.get(slug, 0)
            counts[slug] = count + 1
            result.add(slug + (f"-{count}" if count else ""))
    return result


def main():
    errors = []
    pages = sorted(WIKI.glob("*.md"))
    links = 0
    for page in pages:
        for target in re.findall(r"\]\(([^)]+)\)", page.read_text(encoding="utf-8")):
            if "://" in target or target.startswith("mailto:"):
                continue
            name, _, anchor = target.partition("#")
            path = page.parent / name if name else page
            if not path.exists() and not path.suffix:
                path = path.with_suffix(".md")
            links += 1
            if not path.is_file():
                errors.append(f"{page.name}: missing target {target}")
            elif anchor and path.suffix == ".md" and anchor not in anchors(path):
                errors.append(f"{page.name}: missing heading {target}")

    images = list((WIKI / "images").glob("*.png"))
    for path in images:
        data = path.read_bytes()
        if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
            errors.append(f"Invalid PNG: {path.name}")
        elif min(struct.unpack(">II", data[16:24])) == 0:
            errors.append(f"Empty PNG dimensions: {path.name}")

    source = (ROOT / "src/main/java/xyz/ignite4inferneo/OPCode.java").read_text(encoding="utf-8")
    opcodes = {int(code): (name, int(operands) + 1) for name, code, operands in
               re.findall(r"^\s+([A-Z_]+)\((\d+), (\d+)\)", source, re.M)}
    isa = (WIKI / "Instruction-set.md").read_text(encoding="utf-8")
    rows = re.findall(r"^\| (\d+) / ([0-9A-F]+) \| `([A-Z_]+)[^`]*` \| (\d+) \|", isa, re.M)
    if len(rows) != len(opcodes):
        errors.append("Instruction-set.md: opcode row count differs from OPCode.java")
    for number, hex_code, name, length in rows:
        if int(hex_code, 16) != int(number) or opcodes.get(int(number)) != (name, int(length)):
            errors.append(f"Instruction-set.md: incorrect encoding or length for {name}")

    services = (ROOT / "src/main/java/xyz/ignite4inferneo/jvdos/DesktopServices.java").read_text(encoding="utf-8")
    version = re.search(r"\bVERSION=(\d+)", services)[1]
    if f"JCTRL application ABI v{version}" not in (WIKI / "COM-programming.md").read_text(encoding="utf-8"):
        errors.append("COM-programming.md: application ABI version differs from DesktopServices.java")
    installer = (ROOT / "src/main/java/xyz/ignite4inferneo/DiskInstaller.java").read_text(encoding="utf-8")
    firmware = re.search(r"MAGIC = new byte\[\] \{[^}]*, (\d+)\}", installer)[1]
    if f"revision {firmware}" not in (WIKI / "Disk-and-media-formats.md").read_text(encoding="utf-8"):
        errors.append("Disk-and-media-formats.md: firmware revision differs from DiskInstaller.java")

    asset = (ROOT / "tools/disk-workshop/desktop.js").read_text(encoding="utf-8")
    components = re.findall(r"\{name:'([^']+)',type:0,base64:'([^']+)'\}", asset)
    if [name for name, _ in components] != ["JCTRL.COM", "TEXTEDIT.COM", "PAINT.COM"]:
        errors.append("Workshop desktop bundle component list differs from the documented bundle")
    for name, encoded in components:
        directory = "dos" if name == "JCTRL.COM" else "jctrl"
        if (ROOT / "examples" / directory / name).read_bytes() != base64.b64decode(encoded, validate=True):
            errors.append(f"Workshop bundle is stale relative to examples: {name}")

    for error in errors:
        print(f"ERROR: {error}")
    if not errors:
        print(f"PASS: {len(pages)} wiki pages, {links} local links/anchors, {len(images)} PNGs, "
              f"{len(opcodes)} opcodes, ABI v{version}, firmware {firmware}, and {len(components)} bundle assets")
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
