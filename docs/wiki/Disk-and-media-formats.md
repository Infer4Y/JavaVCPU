# Disk and media formats

JV-DOS 2.0 uses **JVFS 1**, one volume per mounted 16 MiB device. The hardware address is still `(x << 16) | (y << 8) | z`; register 10 selects the device. Storage banks are address bytes, not separate filesystems. The machine still has two RAM banks (128 KiB total).

The installer reformats older experimental disk formats on startup. There is no migration. An existing JVFS signature prevents reformatting. All running filesystem operations execute as guest bytecode; Java's format reader/writer is used only for installation and tooling.

## Layout

| Byte offset | Contents |
| --- | --- |
| `0..0x1FFFF` | Reserved firmware area |
| `0..7` | `JVCPM`, revision 24, image page count, entry metadata |
| `256..295` | Managed desktop ownership record |
| `296..335` | Managed TEXTEDIT ownership record |
| `336..375` | Managed PAINT ownership record |
| `512` onward | Flattened BIOS/resident DOS pages |
| `0x20000` | Superblock: `4A 56 46 53 01 0A A1 00` (`JVFS`, version 1, cluster shift 10, root cluster 161) |
| `0x20400..0x283FF` | 16,384 little-endian 16-bit FAT entries |
| `0x28400` | Root directory (cluster 161) |
| `0x28800` onward | File and additional directory clusters |

Clusters are 1,024 bytes. FAT indices are physical cluster numbers: byte address `cluster * 1024`. Clusters 0..160 contain reserved areas and use FAT value `0xFFFE`; root cluster 161 is allocated. FAT value 0 means free, `0xFFFF` terminates a chain; other allocated values identify the next cluster. A freshly formatted volume has 16,222 free data clusters (16,611,328 bytes). Growing directories consume clusters from the same pool.

## Directory entries

Directories are cluster chains, with 32 entries per cluster. Their first two entries are `.` and `..`, both type 4, pointing to self and parent. Root's parent is itself. Entries are unique within a directory. Zero first name byte marks a free entry.

| Entry byte | Meaning |
| --- | --- |
| 0..11 | Uppercase ASCII name, zero padded; full 12-byte names need no terminator |
| 12 | Type: 0 binary, 1 text/JVS, 2 image, 3 music, 4 directory |
| 13 | Reserved flags (0) |
| 14..15 | First cluster, little endian; empty files use 0 |
| 16..18 | Length, little-endian 24-bit bytes; directories use 0 |
| 19..31 | Reserved (0 in new entries) |

Ordinary names contain 1..12 letters, digits, `_`, `-`, or `.`, and cannot begin with a dot. File chains contain exactly `ceil(length/1024)` clusters. No file is confined to a 64 KiB address bank. The maximum practical size is the space left on the volume, rather than the old 61,200-byte bank limit.

COPY/APPEND and replacement writes stage a new chain and publish it on CLOSE, then free the old chain. Failure/ABORT discards the staged data and keeps the old file. Replacing a file therefore needs temporary free space for its entire replacement. New empty entries may exist while a writer is active. This is a small cooperative filesystem, without journaling or power-loss recovery. Guest code validates chain lengths and bounded traversal; Workshop and the host installer additionally validate whole-volume ownership, directory ancestry and orphan allocations.

## Resident filesystem ABI

Call bank 0, page 249, offset 30. A/B select the RAM bank/page of a 64-byte request/reply mailbox; N/O/P specify the return bank/page/offset. Working registers are scratch. Signature at 249:39 is `J`, `F`, 1. Requests finish atomically without display yields or host execution hooks.

| Mailbox offset | Meaning |
| --- | --- |
| 0 / 1 | Operation / returned status |
| 2 / 3 | Handle / file type |
| 4..5 | Current directory cluster (little endian) |
| 6..8 | Enumeration cookie (cluster low/high, row); zero starts enumeration |
| 9..11 | File length (little endian) |
| 12..13 | First cluster (little endian) |
| 14..16 | Directory entry's physical address, **bank/page/offset order** |
| 17..19 | File position (little endian) |
| 20 / 21 | Block byte count / single-byte data |
| 25..27 | Block RAM buffer bank/page/offset |
| 32..43 / 44..55 | Name / new name |

Operations: RESET=0, LOOKUP=1, NEXT=2, OPEN=3, READ=4, SEEK=5, CREATE=6, WRITE=7, CLOSE=8, ABORT=9, MKDIR=10, REMOVE=11, RENAME=12, MOVE=13, PARENT=14, READ_BLOCK=15, WRITE_BLOCK=16, STAT=17. NEXT returns EOF at the end; preserve its cookie for the next call. OPEN/STAT/REMOVE use an entry address returned by LOOKUP/NEXT. CREATE uses directory/name/type. Writes are sequential; SEEK applies to readers.

Block requests transfer 1..256 bytes (COUNT=0 requests 256). COUNT returns the number transferred, with 0 representing a complete 256-byte transfer on OK; EOF/error distinguishes a zero-byte failure. Buffer pointers and position advance by the transferred amount. Call CLOSE or ABORT before reusing a handle. Handles 0/1 belong to DOS, 2 to desktop streams, 3 to guest applications. DOS uses mailbox 0:245, JCTRL uses 1:233. Private driver state is at 0:254 and cache at 0:244. Do not overwrite those pages.

Statuses: OK=0, NOT_FOUND=1, EXISTS=2, FULL=3, CORRUPT=4, BAD_ARGUMENT=5, IS_DIRECTORY=6, NOT_EMPTY=7, EOF=8, BAD_HANDLE=9, UNFORMATTED=10. RESET clears handle state and is intended for boot, not while writers are open.

## Palette image (JI)

```text
4A 49 width height pixel0 pixel1 ...
```

Width and height are 1-240. Each row-major pixel is one 0-127 `RRGGGBB` palette byte. The largest image is 57,604 bytes (4 + 240*240). DOS reads the RAM file buffer using 16-bit offsets. Images up to 15 by 15 retain 8 by 8 display blocks; larger images render at native resolution below the title. The file length must match its dimensions exactly.

## Music stream (JM)

```text
4A 4D 01 event_count
duration voice note control
duration voice note control
...
```

Version 1 supports up to 255 four-byte events in a 1,024-byte file. Version 2 uses header `4A 4D 02 00` and derives the event count from file length, allowing streams larger than 64 KiB, limited by available disk space. Duration 0 updates a voice immediately; duration 1-255 waits that many roughly 33 ms frames after the update. Voice is 0-7, note is 0-127, control is 0 for off or 128-131 for gated square/triangle/saw/noise. Multiple duration-0 events can establish simultaneous notes before a waiting event. Esc stops playback and silences all voices.

A host `.mid` file is not directly executable music for DOS; Workshop converts it into a `.JMT` stream. A host image similarly requires conversion into `JI` bytes, rather than simply renaming its extension.
