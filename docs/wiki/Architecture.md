# Architecture

Memory has two banks of 256 pages of 256 bytes, totaling 131,072 bytes (128K × 8-bit cells, or 128 KiB). A 17-bit byte address is `(bank << 16) | (page << 8) | offset`, with bank 0 or 1. A register stores one byte. Arithmetic wraps modulo 256. Page and address bytes are interpreted unsigned; CPU `OUTPUT` prints signed Java byte values (-128 through 127), while JVS `OUTPUT` renders unsigned decimal (0-255) in the guest display.

| Register | Index | Meaning |
| --- | --- | --- |
| A-H | 0-7 | General-purpose registers |
| COUNTER | 8 | Instruction byte offset on active page |
| PAGE | 9 | Active page for instruction fetch and ordinary memory access |
| STORAGE_PAGE | 10 | Reserved; does not extend disk addresses |
| MEMORY_BANK | 11 | Active RAM bank, 0 or 1 |

Boot and built-in program loading select RAM bank 0. Existing page instructions operate within the active RAM bank. `CHANGE_BANK_PAGE_JUMP` changes bank, page and counter together. `LOAD_MEM_ON_BANK`/`STORE_MEM_ON_BANK` and literal-address `LOAD_MEM_ABSOLUTE`/`STORE_MEM_ABSOLUTE` access either bank without changing the execution bank. Bank 1 buffers files and hosts larger COM programs; the DOS kernel and services remain in bank 0. Invalid bank numbers are rejected. Avoid writing MEMORY_BANK directly: the next instruction fetch would immediately switch banks.

The counter advances by instruction length and wraps as a byte. Operand fetches use Java array indexing, so an instruction's opcode and operands must all fit before the end of its page. `CHANGE_PAGE` continues at the old counter plus two on the new page; `CHANGE_PAGE_JUMP` explicitly supplies both page and offset through registers. Registers survive page changes. Writing data over instructions creates self-modifying code, so keep data and code separate unless intentional.

## Display

The framebuffer is 256 by 256 pixels, scaled with nearest-neighbor rendering into an 800 by 800 Swing window. Graphics operands name registers holding x, y and color. Call `DISPLAY` to present a frame; it also sleeps about 33 ms.

Palette bytes use the low seven bits, `RRGGGBB`: red 0-3, green 0-7, blue 0-3. Black is 0, yellow is 124, white is 127. The high bit is masked on writes. `CLEAR_GRA` clears to black; `READ_GRA_MEM` reads a pixel.

## Input

`READ_INPUT` takes a **literal port number**, followed by a destination register index.

| Port | Result |
| --- | --- |
| `0x00` | Next keyboard event, or 0 if queue empty |
| `0x01`-`0x20` | Eight held-key bits per port; first key code is `(port-1)*8` |
| `0x30`, `0x31` | Mouse x/y in framebuffer coordinates |
| `0x32` | Mouse buttons: bit 0 left, bit 1 middle, bit 2 right |
| `0x33` | Signed accumulated wheel movement, clamped to -127..127 and consumed on read |

Keyboard codes are Java `KeyEvent` codes. Press events contain the code; release events OR it with `0x80`. Codes above 127 collide in this event protocol, so the DOS decoder also checks held-key banks for quotes/backtick. The guest translates shifted punctuation and keypad input. It uses uppercase command input; imported text may contain lowercase.

## Audio and storage

`OUTPUT_AUDIO` takes registers containing voice (0-7), MIDI note and control. Gate bit 7 enables sound; waveform bits 0-2 select 0 square, 1 triangle, 2 saw, or 3 noise. Set control to 0 to silence a voice. The mixer produces 44.1 kHz mono audio. Audio initialization requires an available host output device.

Storage has 16 MiB, addressed as `(x << 16) | (y << 8) | z`, where x/y/z come from registers. Byte writes update a memory-mapped host disk image; shutdown flushes pending writes. Its path is `storage.img` relative to the working directory. Stop the VM before exchanging disk files. See [Disk and media formats](Disk-and-media-formats).

JV-DOS 1.3 uses bank-zero pages 253/254 for extended filesystem state and allocation validation. File lengths and cursor/source positions use 16-bit counters. `COMPARE_UNSIGNED` supports the guest arithmetic for these counters without signed-byte ambiguity.

The shell reserves RAM bank-one pages 240-247 for sixteen 128-byte history records, page 248 for the draft line, and pages 249-250 for completion scratch. These pages sit above the maximum 61,200-byte file buffer. Programs must preserve this region to retain shell history. History is cleared at guest reboot.
