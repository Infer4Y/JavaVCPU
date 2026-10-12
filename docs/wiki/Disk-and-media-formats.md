# Disk and media formats

The storage device is exactly 16,777,216 bytes. Its address is `(x << 16) | (y << 8) | z`. Firmware and user-file areas are distinct.

## Boot image

| Offset | Contents |
| --- | --- |
| 0-4 | ASCII `JVCPM` magic |
| 5 | System image revision 4 |
| 6 | Nonzero number of 256-byte image pages |
| 7 | BIOS entry metadata, currently 1 |
| 512 onward | Flattened system pages |

The bootstrap reads the page count at byte 6, loads pages beginning at offset 512 into RAM, then jumps to RAM page 0:0. Page 255 is reserved for that bootstrap. `DiskInstaller` regenerates firmware if the magic/revision is invalid; the user bank remains outside the rewritten region.

## Directory

Slot n (0-15) starts at `0x20000 + n*256` (bank x=2, y=n). There are no directories or chains.

| Slot byte | Contents |
| --- | --- |
| 0 | Content length, 0-240 |
| 1-12 | ASCII filename, NUL-padded when shorter than 12 bytes |
| 13 | Type: 0 binary, 1 text, 2 image, 3 music |
| 14-15 | Reserved |
| 16-255 | Up to 240 content bytes |

A zero first filename byte marks a free slot; length zero alone does not mean free. A full 12-character name occupies the name field without a terminator inside it. The guest initialization marker is `0x4A` at bank 2, slot 255, byte 0 (`0x2FF00`). Initialization seeds HELLO.COM once. Workshop writes that marker so guest seeding does not overwrite imported data.

## Palette image (JI)

```text
4A 49 width height pixel0 pixel1 ...
```

Width and height are 1-15. Each row-major pixel is one 0-127 `RRGGGBB` palette byte. The largest image is 229 bytes (4 + 15*15), within the file limit. DOS renders a pixel as an 8 by 8 block.

## Music stream (JM)

```text
4A 4D 01 event_count
duration voice note control
duration voice note control
...
```

Up to 59 four-byte events fit in 240 bytes. Duration 0 updates a voice immediately; duration 1-255 waits that many roughly 33 ms frames after the update. Voice is 0-3, note is 0-127, control is 0 for off or 128-131 for gated square/triangle/saw/noise. Multiple duration-0 events can establish simultaneous notes before a waiting event. Esc stops playback and silences all voices.

A host `.mid` file is not directly executable music for DOS; Workshop converts it into a `.JMT` stream. A host image similarly requires conversion into `JI` bytes, rather than simply renaming its extension.
