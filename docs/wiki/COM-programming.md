# COM programming

COM files contain flat JavaVCPU instructions. They are not x86 executables. DOS verifies opcode numbers, instruction lengths and register operands, runs files up to 240 bytes in bank 0 page 248, and runs larger linked executables in bank 1 starting at page 0:0. All instructions must stay within a 256-byte code page. Verification does not sandbox a program or prove that its branches and memory accesses are safe.

Register operands use CPU indices: A-H are 0-7 and I-P are 12-19. Indices 8-11 remain control registers. DOS accepts all 20 register slots.

## Stable guest services

The seeded `HELLO.COM` uses guest services to print a greeting and return to the shell:

![HELLO.COM running in JV-DOS and returning to the prompt](images/dos-com-run.png)

| Entry | Inputs | Result |
| --- | --- | --- |
| page 249, offset 0 | D = ASCII character; A = return page; B = return offset | Prints one character and jumps back to A:B |
| page 249, offset 9 | None | Returns to DOS shell |

From bank 0, call using `CHANGE_PAGE_JUMP` with registers containing service page and offset. A bank-one caller must enter bank 0 with `CHANGE_BANK_PAGE_JUMP`. The character-print entry returns to page A, offset B **in bank 0**; a bank-one program needs a bank-zero return trampoline to resume its own code. `InstallerImage.java` demonstrates this. The service and renderer may overwrite general registers. Reload arguments for each call; do not rely on register preservation. There is no stack-based call instruction.

The current system exposes a shared graphics entry at bank zero, page 249, offset 18. Before using it, read bytes 27..29 of that page and require ASCII `J`, ASCII `G`, and version `1`. The entry takes K/L = X/Y, M = palette color, D = character, and E = rendering mode (0 = transparent 3x5 glyph, nonzero = opaque 4x6 cell with a black background). N/O/P specify the return bank/page/offset. Enter with `CHANGE_BANK_PAGE_JUMP`; the routine preserves N-P and all RAM but clobbers A-M. Coordinates wrap as bytes and unsupported characters draw `?`. It draws into the current framebuffer without calling `DISPLAY` or changing storage selection. The mode and register constants are documented in `JvDosServices.java`.

This graphics entry is available to native guest code. JCTRL's bounded COM interpreter exposes virtual print/completion entries, resident filesystem entry `0:249:30`, and shared application entry `0:249:42`. It does not expose native glyph entry `0:249:18`; interpreted apps use TEXT/PACKED_TEXT and GRAPHICS_CURSOR instead. Raw storage instructions still access mounted disks, so application RAM bounds do not provide a filesystem security sandbox.

To print H and return to program byte 18:

```text
02 48 03    # STORE 72 D
02 F8 00    # STORE 248 A (return page)
02 12 01    # STORE 18 B (return byte offset)
02 F9 06    # STORE 249 G (service page)
02 00 07    # STORE 0 H (print entry)
0A 06 07    # CHANGE_PAGE_JUMP G H
```

Return to DOS using:

```text
02 F9 06 02 09 07 0A 06 07
```

CPU opcode `STOP` halts the machine; it is not a DOS return. JVS `STOP` is interpreted differently and returns to the shell.

`examples/dos/HI.hex` contains three print calls (H, I, newline) and a prompt return. `examples/build.ps1` creates `HI.COM`; import the COM as Binary / COM through Workshop, then `RUN HI.COM`. Do not import the commented hex as executable content.

## Memory ownership

In bank 0, the guest uses page 246 for font pointers, 247 for instruction descriptors, 248 for the transient program, 249 for service trampolines, and 250-252 for names, input and kernel variables. Page 244 holds the filesystem cache, 245 the DOS mailbox, 253 DOS state and 254 private filesystem state; the bootstrap uses page 255. Resident kernel/font/image data occupy other pages. Limit a small COM to its transient area and documented services; bank 1 is the file buffer and becomes the code area when a larger COM executes.

DOS's validator walks the whole file as instructions, so embedded arbitrary data can be rejected. Instruction operands must fit inside the file and each fetched instruction must fit inside its page. The loader leaves the rest of transient RAM unchanged. Do not branch into stale bytes after the program's end. See [Instruction set](Instruction-set.md) for byte lengths and operand conventions.

## Extended RAM

Bank 1 provides a separate 64 KiB for program data and code. Use opcodes 24/25 to read/write it from bank 0, and opcode 23 for cross-bank jumps. All DOS services live in bank 0; return from bank 1 with `CHANGE_BANK_PAGE_JUMP` specifying bank 0, page 249, offset 9. Ordinary page jumps retain the bank. The COM file limit is 61,200 bytes, subject to free clusters. Larger COM files begin in bank 1; they must explicitly select bank 0 to call DOS services or return to its prompt. Small COM files keep the original ABI. The counter still wraps within a page: use explicit page jumps to continue code across pages.

RAM bank-one pages 240-253 are reserved for the shell history, draft line, and completion buffers. Preserve them when using bank-one RAM outside the loaded COM/file region.

## Storage and networking

Control register 10 selects the storage medium for opcodes 18/19; preserve the caller's value when returning to DOS. Opcodes 29/30 provide byte access to a separate TCP controller register bus. See [Peripheral buses](Peripheral-buses.md) for the protocol and `NETECHO.COM` example.

## JCTRL guest program runner

JCTRL interprets COM programs inside its application window without changes to the Java CPU or ISA. Programs up to 8192 bytes keep a 32-page virtual workspace and a 128 by 128 RAM-backed pixel buffer. Larger COMs automatically use document mode: up to 23,296 bytes (91 virtual pages), mapped to physical bank-one pages 128-218. Their retained text surface occupies 219-223. Document programs use shared text services; pixel operations address RAM reused by their code/data and should not be used for drawing. Instruction fetches remain limited to the loaded file; data reads/writes and text/filesystem buffers are bounded to the current app workspace. The native desktop and its state remain inaccessible. Programs of at most 240 bytes start at virtual 0:248; larger files start at virtual 1:0. Both layouts use the 32-page workspace until file size exceeds 8 KiB. Guest-handled DOS entries 0:249:0 and 0:249:9 provide character output and completion. STOP finishes the interpreted application while the real VM's STOP behavior remains unchanged. Interpreted OUTPUT prints unsigned decimal in the application window; native CPU OUTPUT prints signed byte values to the host console. See [the desktop guide](Experimental-desktop.md) for supported JVS commands, memory limits, graphics scaling, and media playback.


## Shared JCTRL application services

JCTRL application ABI v7 exposes **virtual** `0:249:42` to interpreted COM clients.
It is implemented by ordinary desktop guest bytecode; it is not a CPU opcode,
host callback, or a fullscreen DOS trampoline. Rebuild/update JCTRL before using
it. Existing character-print, completion and filesystem entries remain valid.
`DesktopServices.java` defines operation/status constants, and
`DesktopServicesRegression.java` contains independent assembled clients.

Set **A** to the operation and **N/O/P** to the return bank/page/offset, then use
`CHANGE_BANK_PAGE_JUMP` to virtual bank 0, page 249, offset 42. A returns status
(`OK=0`, `BAD_ARGUMENT=1`, `END=2`). Other virtual registers are preserved except
outputs listed below and the return registers supplied by the caller. Each text
request is bounded and yields to the desktop after returning.

| Operation | Inputs | Outputs |
| --- | --- | --- |
| QUERY (0) | None | D = version (7) |
| TEXT (1) | B/C = virtual bank-one page/offset; D = count, 0 means 256 | Status in A |
| PACKED_TEXT (2) | B/C = `DesktopImage.Asm` packed literal pointer | Status in A |
| KEYBOARD (3) | B = VK code; C = shift flag 0/1 | C = updated shift flag; D = ASCII, or 0 for no printable character |
| CONTEXT (4) | None | B/C = selected directory, low/high byte |
| CACHE_BEGIN (5) | B = first buffer page; C = exclusive end page | D = cache mode |
| CACHE_FLUSH (6) | B/C = exclusive end page/offset of new text in the buffer | D = cache mode |
| CACHE_FINISH (7) | None | D = cache mode |
| CACHE_NAVIGATE (8) | B = 0 Home, 1 Previous, 2 Next; C = current screen's first RAM page | B/C = loaded text's exclusive end page/offset; D = mode; A = END when Next has no further text |
| CACHE_ABORT (9) | None | D = 0 |
| DOCUMENT (10) | None | Clear and select the retained 48-column, 23-row reader surface |
| NETWORK_PACKED (11) | B/C = packed literal pointer | Append up to 80 literal bytes to TCP TX port 5; status in A |
| DOCUMENT_FRAME (12) | B = first page of a ten-page character/color frame | Retain the frame, select editor chrome and request repaint; status in A |
| GRAPHICS_SPAN (13) | B/C = buffer page/offset; E = native row 0..127; I = 0 read / 1 write | Copy 128 palette pixels between the buffer and retained pixel surface |
| GRAPHICS_CURSOR (14) | B/C = text column 0..31 / row 0..20 | Position the shared pixel text renderer without clearing pixels |
| GRAPHICS_LINE (15, v7) | B/C = start x/y; D/E = end x/y, all 0..127; F = color 0..127; I = brush size 1, 2 or 4 | Connected square-brush stroke, clipped at the surface edges; status in A |

The v6 graphics operations require pixel mode. GRAPHICS_SPAN buffers must fit
one app page 0..30, with offset 0..128; page 31 is excluded to protect the JVFS
mailbox. Writes validate all 128 colors before changing any pixel and request a
repaint only when pixels change. Reads do not request repaint. These operations
run through shared desktop guest bytecode and preserve other virtual registers.
GRAPHICS_CURSOR changes only the text cursor; subsequent TEXT/PACKED_TEXT calls
use that position. Invalid requests leave pixels, buffers and cursor unchanged.
See [Paint](../../examples/jctrl/README.md#jctrl-paint) for an image-file client.

GRAPHICS_LINE validates every argument before drawing and preserves the caller's
other virtual registers. It stamps both endpoints and interpolates intermediate
pixels in shared guest code. At most 128 stamps of a 4-by-4 brush execute per
request, after which the desktop handles input again. Ordinary graphics clients
receive up to 128 instructions per tick; DISPLAY and shared services yield sooner.
Pixel writes and row transfers to the foremost graphics window update changed
screen pixels directly, preserving the cursor's saved background. Covered windows,
window raises and moves use the normal full compositor.

Mouse ports 0x30/0x31 retain their clamped even coordinates (0..254) on the
scaled graphics surface. Button port 0x32 reports zero outside the focused app
surface or while dragging window chrome. This prevents an outside click from
drawing at a clamped canvas edge. Button masks remain 1 left, 2 middle, 4 right.

JCTRL's linked image allows up to 140 pages while its executable instructions
remain below linked page 134. Overflow code uses physical bank-one pages
234..239; literals use 231..232, free pages 254..255, and the unused tails of
register page 228 (bytes 20..255) and mailbox page 233 (bytes 64..255). The
assembler skips those live prefixes and can pack literals after page-link
jumps. App/framebuffer bounds and shell history pages 240..253 are preserved.

TEXT validates its entire span before drawing; it may cross pages but cannot
leave the current application workspace (0-31, or 0-90 for larger document COMs). PACKED_TEXT reads the assembler's three-byte
literal groups, skips their two instruction bytes, and stops at NUL or 80
characters. Both reuse JCTRL's existing framebuffer/font renderer. NETWORK_PACKED
uses the same packed bounds but appends to the network TX queue without drawing.
The caller configures/connects the TCP device, issues its send command and checks
its error register; no host callback or network device change is involved. KEYBOARD only
decodes supplied events; clients still read their focused input through the
normal keyboard port. It handles Shift press/release and numpad punctuation.

DOCUMENT opts into a light window-wide text surface using the existing framebuffer
RAM. Print, TEXT and PACKED_TEXT write retained character cells; CLEAR_GRA clears
the cells and cursor. JCTRL draws them with its shared resident font when needed,
including after dragging or uncovering the window. Clearing touches five pages
instead of 64 pixel pages. The footer also sends L/B for links/Back. Closing the application restores the normal
128 by 128 pixel surface. The reader footer sends P/N/H/R/U through the focused
key mailbox, and the wheel sends Page Up/Down. This operation requires version 2;
version-3 extended document memory is selected by COM file size; smaller clients keep their existing bounds. Older text and pixel services retain their calling convention.

DOCUMENT_FRAME (v4) retains a reference to ten contiguous app pages. The first
five hold row-major character bytes (48 columns by 23 rows); the following five
hold matching palette colors. Both buffers remain owned by the application and
must stay alive until DOCUMENT or a later FRAME call. Each FRAME requests a
repaint. Frame clients receive a bounded burst of up to 4,080 guest instructions
per desktop tick for editing/lexing; DISPLAY yields immediately. The full ten-page span must fit app RAM and exclude page 31; invalid
requests return BAD_ARGUMENT without changing the retained frame. The reader
footer is hidden; clients draw their controls in the frame. Wheel events still send Page Up/Down.
DOCUMENT restores the standard reader surface. See
[TEXTEDIT](../../examples/jctrl/README.md#jctrl-text-editor) for a working client.
Since v5, CLEAR_GRA clears the referenced character and color buffers through
shared guest code, without publishing the cleared intermediate frame. Call
DOCUMENT_FRAME after completing changes to request repaint. Frame clients receive up to sixteen 255-instruction slices (4,080 instructions)
per desktop tick. The resident DOS window receives up to eight such slices
(2,040 instructions). DISPLAY yields immediately in either case.

The spool reserves **bytes 0-63 of virtual page 31** as its JVFS mailbox and
**filesystem handle 3** until ABORT/close. Give it at least two buffer pages (three in DOCUMENT mode);
the span must stay inside the app workspace and must not contain page 31,
so neither reads nor writes touch the mailbox or resident desktop. Cache modes are 0 (RAM fallback), 1 (writing), and
2 (committed and open for readback). BEGIN creates a new text file under
`WEB.CACHE` in the selected directory, searches 256 candidate filenames without
replacing existing ones, and uses the app's storage-device register. FLUSH
appends only the newly filled buffer, and tracks the write position independently
of the size committed on CLOSE. FINISH commits and reopens the file for reads.
NAVIGATE loads up to 512 bytes (768 when begun in DOCUMENT mode) through the resident seek/block routines, including
across cluster boundaries. It is available in mode 2; before commit the client
can still page through its RAM window.

Storage errors abort an unfinished spool and return mode 0. ABORT discards an
uncommitted new file; it closes a committed read handle without deleting the
file. JCTRL also aborts the handle when an application stops. Clients should not
use filesystem handle 3 or modify the mailbox while the spool owns them. Cache
policy lives in `DesktopPageCache`, block/keyboard services in
`DesktopAppServices`, and filesystem mechanics remain in the resident JVFS
driver shared with DOS. No extra RAM bank or storage format is introduced.
