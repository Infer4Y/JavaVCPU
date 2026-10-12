# Architecture

Memory is 256 pages of 256 bytes, totaling 65,536 bytes. A register stores one byte. Arithmetic wraps modulo 256. Page and address bytes are interpreted unsigned; CPU `OUTPUT` prints signed Java byte values (-128 through 127), while JVS `OUTPUT` renders unsigned decimal (0-255) in the guest display.

| Register | Index | Meaning |
| --- | --- | --- |
| A-H | 0-7 | General-purpose registers |
| COUNTER | 8 | Instruction byte offset on active page |
| PAGE | 9 | Active page for instruction fetch and ordinary memory access |

The implementation allocates an extra register slot at index 10 and the COM validator currently accepts it, but `Registers` exposes no supported programming contract for that slot. Use A-H for data and the documented control registers.

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

`OUTPUT_AUDIO` takes registers containing voice (0-3), MIDI note and control. Gate bit 7 enables sound; waveform bits 0-2 select 0 square, 1 triangle, 2 saw, or 3 noise. Set control to 0 to silence a voice. The mixer produces 44.1 kHz mono audio. Audio initialization requires an available host output device.

Storage has 16 MiB, addressed as `(x << 16) | (y << 8) | z`, where x/y/z come from registers. Byte writes immediately update the host disk file and in-memory cache. Its path is `storage.img` relative to the working directory. Stop the VM before exchanging disk files. See [Disk and media formats](Disk-and-media-formats).
