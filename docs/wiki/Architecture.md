# Architecture

Memory has two banks of 256 pages of 256 bytes, totaling 131,072 bytes (128K × 8-bit cells, or 128 KiB). A 17-bit byte address is `(bank << 16) | (page << 8) | offset`, with bank 0 or 1. A register stores one byte. Arithmetic wraps modulo 256. Page and address bytes are interpreted unsigned; CPU `OUTPUT` prints signed Java byte values (-128 through 127), while JVS `OUTPUT` renders unsigned decimal (0-255) in the guest display.

| Register | Index | Meaning |
| --- | --- | --- |
| A-H | 0-7 | General-purpose registers |
| COUNTER | 8 | Instruction byte offset on active page |
| PAGE | 9 | Active page for instruction fetch and ordinary memory access |
| STORAGE_PAGE | 10 | Storage device selector, 0-255 (legacy register name) |
| MEMORY_BANK | 11 | Active RAM bank, 0 or 1 |
| I-P | 12-19 | Additional general-purpose registers |

Boot and built-in program loading select RAM bank 0. Existing page instructions operate within the active RAM bank. `CHANGE_BANK_PAGE_JUMP` changes bank, page and counter together. `LOAD_MEM_ON_BANK`/`STORE_MEM_ON_BANK` and literal-address `LOAD_MEM_ABSOLUTE`/`STORE_MEM_ABSOLUTE` access either bank without changing the execution bank. Bank 1 buffers files and hosts larger COM programs; the DOS kernel and services remain in bank 0. Invalid bank numbers are rejected. Avoid writing MEMORY_BANK directly: the next instruction fetch would immediately switch banks.

The counter advances by instruction length and wraps as a byte. Operand fetches use Java array indexing, so an instruction's opcode and operands must all fit before the end of its page. `CHANGE_PAGE` continues at the old counter plus two on the new page; `CHANGE_PAGE_JUMP` explicitly supplies both page and offset through registers. Registers survive page changes. Writing data over instructions creates self-modifying code, so keep data and code separate unless intentional.

## Display

The framebuffer is 256 by 256 pixels, scaled with nearest-neighbor rendering into an 800 by 800 Swing window. Graphics operands name registers holding x, y and color. Drawing updates the back buffer; `DISPLAY` publishes it atomically and paces presentation toward a 33 ms interval. Guest execution consumes that budget; missed deadlines rebase without catch-up bursts. The next drawing buffer retains the previous pixels for incremental rendering.

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
| `0x40` | 1 if the storage device selected by register 10 is mounted, otherwise 0 |

Keyboard codes are Java `KeyEvent` codes. Press events contain the code; release events OR it with `0x80`. Codes above 127 collide in this event protocol, so the DOS decoder also checks held-key banks for quotes/backtick. The guest translates shifted punctuation and keypad input. It uses uppercase command input; imported text may contain lowercase.

## Audio and storage

`OUTPUT_AUDIO` takes registers containing hardware voice (0-15), MIDI note and control. Gate bit 7 enables sound; waveform bits 0-2 select 0 square, 1 triangle, 2 saw, or 3 noise. Set control to 0 to silence a voice. The mixer produces 44.1 kHz mono audio. JV-DOS/JCTRL music files use eight of the sixteen hardware voices; their JM format accepts voice numbers 0-7. Audio initialization requires an available host output device.

Each mounted storage device has 16 MiB, addressed as `(x << 16) | (y << 8) | z`, where x/y/z come from registers. Register 10 selects the device independently of its 24-bit address. Byte writes update that device's mapped disk image; shutdown flushes all mounted images. Device 0 uses `storage.img` relative to the working directory. Additional images are mounted with program arguments such as `--disk=1=secondary.img`. Stop the VM before exchanging disk files. See [Disk and media formats](Disk-and-media-formats.md).

JV-DOS 2.0 uses a resident guest filesystem driver: bank-zero page 244 is its cache, 245 the DOS mailbox, 253 DOS extended state and 254 private driver state/handles. Files use 24-bit lengths and positions; the editor and executable buffers retain 16-bit offsets. `COMPARE_UNSIGNED` supports carry and bounds checks.

The shell reserves RAM bank-one pages 240-247 for sixteen 128-byte history records, page 248 for the draft line, and pages 249-250 for completion scratch. These pages sit above the maximum 61,200-byte file buffer. Pages 251, 252 and 253 hold the DOS path, block-copy buffer and document-page history. Programs must preserve this region to retain shell history. History is cleared at guest reboot.

## Separate device buses

RAM, storage and networking have separate address spaces and instruction controls. Each access transfers one byte through CPU registers, modeling a shared 8-bit data path without mapping devices into RAM. Storage retains its three-byte address plus device select; networking has its own 8-bit register address. Network operations run asynchronously so the CPU can poll status while other instructions execute.

The network controller uses the host's existing internet connection (Wi-Fi or Ethernet). It does not emulate radio association, SSIDs or Wi-Fi authentication. It currently supports one outbound raw TCP connection; HTTP can be implemented by guest software over TCP. The device has no TLS, UDP or listening socket. Guest software supplies DOS BROWSE and JCTRL WEB; WEB can use the optional host gateway for verified HTTPS transport. See [Peripheral buses](Peripheral-buses.md) for the complete guest protocol and example.
