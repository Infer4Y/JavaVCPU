# Disk and media formats

The storage device is exactly 16,777,216 bytes. Its address is `(x << 16) | (y << 8) | z`. Firmware and user-file areas are distinct. DOS can now store 64,770 files with up to 15,544,800 content bytes across its banks. A file can use up to 61,200 bytes when all 255 blocks in a bank are available. Large files consume blocks that would otherwise hold additional small files. The CPU address remains 24 bits; this change makes use of the existing disk capacity.

## Boot image

| Offset | Contents |
| --- | --- |
| 0-4 | ASCII `JVCPM` magic |
| 5 | System image revision 10 |
| 6 | Nonzero number of 256-byte image pages |
| 7 | BIOS entry metadata, currently 1 |
| 512 onward | Flattened system pages |

The bootstrap reads the page count at byte 6, loads pages beginning at offset 512 into RAM, then jumps to RAM page 0:0. Page 255 is reserved for that bootstrap. `DiskInstaller` regenerates firmware if the magic/revision is invalid; the user bank remains outside the rewritten region.

## Directory

Banks 2 through 255 each provide 255 slots. Slot n (0-254) in bank b starts at `(b << 16) + n*256`. Bank 2 retains all existing file offsets. Slot 255 is reserved in every bank; banks 0 and 1 remain outside the DOS filesystem. Files use linked 240-byte content blocks; folders are not supported. Use `BANK` to show the current bank, `BANK +` to advance, and `BANK -` to go back. Reboot selects bank 2. File names are local to a bank; COPY and REN operate within the current bank.

| Slot byte | Contents |
| --- | --- |
| 0 | Low byte of the total file length |
| 1-12 | ASCII filename, NUL-padded when shorter than 12 bytes |
| 13 | Type: 0 binary, 1 text, 2 image, 3 music |
| 14 | High byte of the total file length |
| 15 | Next block slot (1-254), or 0 for end of chain |
| 16-255 | First 240 content bytes |

Continuation blocks store `0xFF` in byte 1 and are hidden from directory listings. They hold another 240 bytes at offsets 16-255 and a next-block link at byte 15. The head stores the total length as `byte0 | (byte14 << 8)`; continuation length/type/name bytes are not interpreted. Slot 0 can be a head but cannot be linked as a continuation because 0 terminates the chain. Empty files still use one head block.

Existing files with zero reserved bytes keep exactly the same offsets and require no data migration. JV-DOS 1.3/system revision 7 is required to use extended files. The guest validates lengths, cycles, continuation markers and shared ownership before modifying files, plans capacity before writing, and frees unused tail blocks on overwrite/delete.

A zero first filename byte marks a free slot; length zero alone does not mean free. A full 12-character name occupies the name field without a terminator inside it. The guest initialization marker is `0x4A` at bank 2, slot 255, byte 0 (`0x2FF00`). Initialization seeds HELLO.COM once. Workshop writes that marker so guest seeding does not overwrite imported data.

## Palette image (JI)

```text
4A 49 width height pixel0 pixel1 ...
```

Width and height are 1-240. Each row-major pixel is one 0-127 `RRGGGBB` palette byte. The largest image is 57,604 bytes (4 + 240*240). DOS reads the linked file buffer using 16-bit offsets. Images up to 15 by 15 retain 8 by 8 display blocks; larger images render at native resolution below the title. The file length must match its dimensions exactly.

## Music stream (JM)

```text
4A 4D 01 event_count
duration voice note control
duration voice note control
...
```

Version 1 supports up to 255 four-byte events in a 1,024-byte linked file. Version 2 uses header `4A 4D 02 00` and derives the event count from file length, supporting up to 15,299 events in 61,200 bytes. Duration 0 updates a voice immediately; duration 1-255 waits that many roughly 33 ms frames after the update. Voice is 0-7, note is 0-127, control is 0 for off or 128-131 for gated square/triangle/saw/noise. Multiple duration-0 events can establish simultaneous notes before a waiting event. Esc stops playback and silences all voices.

A host `.mid` file is not directly executable music for DOS; Workshop converts it into a `.JMT` stream. A host image similarly requires conversion into `JI` bytes, rather than simply renaming its extension.
