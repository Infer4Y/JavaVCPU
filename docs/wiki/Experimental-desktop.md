# Experimental DOS desktop

The desktop is a separate JavaVCPU guest application, `JCTRL.COM`, launched with the `JCTRL` command (or `RUN JCTRL.COM` for a manual copy). DOS remains resident underneath it. Its windows, file browser, and text renderer run entirely on the guest CPU, using the existing instruction set, 128 KiB RAM, and normal device ports.

## Install and start

Start the VM and enter `JCTRL` at the DOS prompt. DOS updates now install and maintain a bundled `JCTRL.COM` on the system disk (device 0), in the first empty bank found scanning from 255 down to 2. The command selects that bank's root folder and runs the ordinary COM file through DOS's loader. No Workshop import is required.

The installer records the managed file's location and SHA-256 in reserved disk bytes 256..295, outside the boot image and file banks. On startup it checks desktop content independently of the OS revision, replacing an unchanged managed copy when the bundled executable changes. Updates preserve other files. Modified, renamed, moved, missing or corrupt managed copies are left alone; insufficient space or corrupt/cross-linked neighboring files also leave the bank unchanged. A manual same-name copy is preserved and is not adopted as the managed package. If every bank is occupied, automatic initial installation is skipped.

For a manual copy, stop the VM, open `storage.img` in Workshop, select a folder and click **Add JCTRL desktop**, then download and replace the disk while stopped. Alternatively import `examples/dos/JCTRL.COM`. Launch a manual copy with `RUN JCTRL.COM` in its folder. Manual copies are not automatically updated.

The desktop opens the current folder on the currently selected storage device. Browsing and viewing do not modify files. Drag-and-drop and DOS file commands persist changes to the selected disk. Explorer navigation stays separate from DOS's location; commands in the DOS window update the real shell's location.

![Files and text windows in the guest desktop](images/desktop-files.png)

## Windows and applications

| Tool | Supported behavior |
| --- | --- |
| Files (`DIR`) | Lists folders and files, follows child and parent folders, and pages large directories. Up/Down selects a highlighted row; Enter opens it. Page Up retraces directory pages; Home resets paging history. File rows identify COM programs, text, palette images, and music. Click text/JVS files to read them; COM, palette images, and music open directly. BANK- / BANK+ switches the browser bank without changing DOS's selection. The title shows the bank in hexadecimal. |
| Text (`VIEW` / `TYPE`) | Reads linked text/JVS files in a clipped window with next-page and Home navigation. CRLF is accepted; tabs display as a space. Invalid chains stop with an error. |
| System (`VER` / `MEM`) | Shows DOS version, installed RAM, and the desktop's execution model. |
| Help (`HELP`) | Shows desktop controls in the system window. |

## DOS window and file operations

Press **F4** to open or focus the existing DOS shell in the application window. This runs the resident DOS bytecode and its original parser, history, completion, editor, and filesystem routines through the guest interpreter; it is not a replacement command parser. Drag its title bar to move it. Only the focused application window receives keyboard events, including key-state reads. Click Files or press F2 to use the explorer without typing into DOS. Close with X; F4 reopens it. Esc goes to DOS for command cancellation while this window is focused. Use the top toolbar's DOS button to leave JCTRL.

The DOS window shares the single application execution slot with COM/JVS programs and media. Its file/program buffer is limited to the existing 8 KiB guest workspace; oversized loads report a DOS error. Text uses the original font at window geometry rather than scaling a fullscreen console. DOS display geometry lives in bank-zero state page 252, bytes 24..26 (wrap X, scroll trigger Y, post-scroll Y); JCTRL restores fullscreen geometry when returning to DOS. Normal startup updates the managed desktop together with the resident system; reimport manual copies after rebuilding.

In Files, press and drag an entry onto a visible folder in the same bank, then release to move it. A click without movement still opens it. A move updates only the entry's parent byte; names, file contents, and linked blocks remain intact. Drops outside a folder, duplicate destination names, self-drops, and cyclic or corrupt destination ancestry are rejected without writing. Cross-bank drag/drop and modifier-key copying are not supported yet.

Explorer operations prepare commands in the real DOS window:

| Key in Files | Prepared command |
| --- | --- |
| F6 | `REN selected-name ` - type the new name |
| F7 | `MKDIR ` - type the folder name |
| F8 | `COPY selected-name ` - type the destination name |
| Delete | `DEL selected-name ` |

Commands are not executed until Enter is pressed; edit or cancel them first. Operations start a fresh prompt at the explorer's bank/folder and are ignored while the existing DOS window is busy or editing. F2 returns to Files and refreshes the list after a command. The original DOS commands also provide `MOVE`, `DELETE`, `RENAME`, `WRITE`, `EDIT`, `APPEND`, `RD`, and other file operations.

![The resident DOS shell in a movable window](images/jctrl-dos.png)

## Programs and media

Click a COM file to run it. For JVS source, open it in the text reader and click **Run / Open** or press **F5**. JCTRL opens its DOS console, queues `RUN filename` and executes it through the resident DOS parser, linked-file loader and JVS interpreter. Output remains in the console; run again from its prompt or reopen the source. COM output appears in the application window. Existing `HI.COM`, COUNT, MATH, REGISTERS, and linked LONG JVS examples are covered by regression checks.

![JVS program output inside JCTRL](images/jctrl-source.png)

The program runner is an interpreter **written in guest bytecode**. It executes inside the original machine, with an explicit 8 KiB code/data workspace and 128x128 pixel buffer allocated from guest RAM. It does not modify the Java CPU, install VM hooks, snapshot host memory, add opcodes, or provide native preemptive multitasking. One program or music stream runs at a time, yielding to JCTRL's event loop. Its output survives window dragging because it lives in guest RAM. Esc stops interpreted applications and returns to the desktop; X closes their window. The focused DOS window handles Esc itself. Stopping an interpreted STOP instruction finishes that application; the VM's STOP instruction itself is unchanged.

COM compatibility includes all current opcode forms, with bounds checks on virtual registers, instruction fetches, and application RAM accesses. Small COM files start at virtual bank 0 page 248; larger files start at virtual bank 1 page 0. Virtual bank-one pages 0..31 provide the 8 KiB workspace; legacy bank-zero page 248 aliases its first page. Branches and code fetches must remain within the loaded file. Bank-zero DOS entries 249:0 and 249:9 are handled by the guest interpreter for print-and-return and application completion. Other DOS internals and memory outside the workspace are unavailable. Graphics coordinates are reduced from 256x256 into the 128x128 buffer, while text uses a 32-column console. Storage/network/audio operations use the existing guest device instructions; normal application disk writes are persistent.

JVS uses the full resident DOS language, including comments and `CLEAR`; JCTRL no longer embeds a second source interpreter, tokenizer, number parser, register parser or line scanner. The DOS window keeps the existing 8 KiB file-buffer limit and checks Esc between statements. Source remains cooperative: the desktop can repaint, move windows and deliver cancellation while the resident interpreter runs.

Palette images (`JI`, type 2) stream across linked blocks directly into the guest pixel buffer. Images up to 128x128 use their original pixels; larger images, up to 240x240, reduce both dimensions by two. The image does not require a full-size second framebuffer or a complete file buffer.

![Palette media in the guest application window](images/jctrl-image.png)

Music (`JM`, type 3) supports versions 1 and 2, eight voices, and the existing frame/note/control event format. Playback advances through the guest event loop so windows remain usable. Esc, X, opening another file, or the end of a tune silences all voices. Zero-duration events are processed in bounded batches to retain keyboard/mouse responsiveness. Unchanged windows are retained between ticks: music waits and programs that have no visual updates skip desktop repainting. Pixel writes only mark the surface dirty when its color changes; completion and errors refresh the status. The framebuffer blit keeps its traversal in CPU registers to reduce memory traffic when repainting is required.

`DISPLAY` atomically swaps a completed drawing buffer into the presenter; Swing paints only that front buffer. The next drawing buffer retains the previous pixels so incremental graphics continue working. Pixel writes and clears remain invisible until `DISPLAY`. This isolates guest rendering from presentation, but does not provide hardware vsync.

`DISPLAY` uses a monotonic 33 ms target interval. Guest execution consumes that budget, so presentation waits only for the remaining time. Overruns rebase the next deadline without catch-up bursts. Sustained-note regression checks require fewer than 1,000 guest instructions per idle playback tick and zero pixel writes. They also verify note durations and zero-duration event grouping. Window dragging and other expensive UI operations can still delay playback; music still counts display intervals rather than reading an independent clock.

Ordinary pointer motion saves and restores the two pixels beneath the cursor, using four pixel writes per move instead of repainting the desktop. Window dragging and changed application surfaces still repaint normally. Regression checks cover overlapping cursor positions and wrapping at screen edges.

JCTRL occupies 109 of a bank's 255 blocks. Use **BANK- / BANK+** to browse another bank for large media files; this is important for images that cannot share a bank with the desktop executable. Switching banks stops the current application and starts at that bank's root folder. No swap file or hidden cache is used.

Drag a title bar to move its window; click any visible part of a window to bring it forward. X closes the window. The toolbar reopens Files, About, and Help; DOS exits the desktop. There is one instance of each tool window.

| Key / input | Action |
| --- | --- |
| F1 / F2 / F3 | Help / Files / system information |
| Esc | Cancel in focused DOS; stop/back from another application; return to DOS from the desktop |
| F4 | Open/focus the resident DOS window |
| F6 / F7 / F8 / Delete in Files | Prepare rename / new folder / copy / delete in DOS |
| F5 / Run / Open | Execute the opened program/source file |
| Home | First page in the focused file or text window |
| Space / Page Down | Next page in the focused file or text window |
| Up / Down in Files | Select the previous / next visible entry |
| Enter in Files | Open the selected entry |
| Page Up in Files | Return to the previous directory page |
| Backspace in Files | Parent folder |
| BANK- / BANK+ | Browse another storage bank |
| Wheel down / up | Next page / first page in the focused tool |

## Resident DOS services

System image revision 21 includes the compact shell and desktop. JCTRL requires graphics ABI v1, introduced in revision 17. Normal VM startup upgrades the system image and its managed desktop copy together. If the graphics signature is missing or incompatible, JCTRL returns to DOS before initializing the desktop.

DOS and JCTRL now share one resident glyph renderer through bank-zero service `249:18`. Both use the existing font table; DOS requests opaque 4x6 character cells and JCTRL requests transparent 3x5 glyphs. The renderer keeps its scratch values in registers and writes no RAM, so desktop drawing cannot corrupt a paused DOS-window operation. The existing print and shell service addresses remain unchanged. See [COM programming](COM-programming.md) for the service contract.

JCTRL is 26,112 bytes (102 load pages), down from 28,928 bytes before the shared operand decoder. Each opcode uses one common operand-fetch loop and the resident DOS instruction-length table. The glyph renderer and full JVS interpreter also remain shared with DOS. The machine still has 128 KiB RAM and the application workspace remains 8 KiB. COM virtualization, window management and streamed media remain in JCTRL to provide bounded, cooperative execution and window surfaces.

## Application layout and rebuilding

`DesktopImage.java` emits a flat executable using the existing instruction set. The ordinary COM loader validates it and runs it in RAM bank one at page 0, offset 0. Executable instructions stay below page 128. If literal pages overflow into 128..135, startup copies only the loaded overflow into reserved pages 234..239 and 231..232 before the application workspace is used. The current compact image has no overflow and skips copying. All literal references are relocated by the assembler; this uses only ordinary guest loads and stores. Strings are packed into literal operands of nonexecuted data instructions so the existing validator can check every instruction, including the data pages.

The application memory layout is entirely within the original 128 KiB:

| RAM bank one pages | Ownership |
| --- | --- |
| 0..127 | Resident JCTRL code and literal data |
| 128..159 | 8 KiB interpreted program code/data or JVS source |
| 160..223 | 16 KiB, 128x128 palette application framebuffer |
| 224 | Desktop/window state |
| 225 | Guest interpreter and media state |
| 226 | Visited file-block map |
| 227 | Directory row-to-slot map |
| 228 | Virtual application registers |
| 229 | Explorer directory-page history (up to 255 page starts) |
| 230 | Focused DOS keyboard queue |
| 231..232, 234..239 | Relocated immutable literals |
| 233 | Reserved (former source token scratch) |
| 240..250 | Existing DOS history/draft buffers, preserved |

Bank-zero DOS and its font table remain resident; the renderer reads the font through ordinary bank-zero loads. Mouse input uses existing ports 0x30..0x33. The bytecode interpreter implements a dynamic READ_INPUT port by patching one literal operand in its own guest code, using normal RAM writes.

On exit it clears the screen, resets the shell's text position, and uses `CHANGE_BANK_PAGE_JUMP` to return to the stable DOS entry at bank 0, page 249, offset 9. These desktop and DOS changes do not add VM opcodes or host memory snapshots; `Memory.java` is unchanged by this work.

Run `gradlew.bat desktopImage` to regenerate `examples/dos/JCTRL.COM` and `tools/disk-workshop/desktop.js`. `storageRegression` runs the real guest instructions through the normal COM loader and checks dragging, bounds, stacking, closing, folder navigation, linked-file paging, corrupt-chain handling, disk preservation, and return to DOS. It also checks guest COM/JVS execution, virtual memory bounds, storage selector restoration, infinite-loop cancellation, maximum linked image streaming, music shutdown, bank switching, and pixels surviving window moves. Framebuffer previews are written under `build/desktop-*.png` and `build/jctrl-*.png`.

The assembler performs branch relaxation before writing the executable: same-page unconditional and equality branches use `JMP` (2 bytes) and `COND_JUMP` (4 bytes). Cross-page targets retain `CHANGE_PAGE_JUMP` and `CHANGE_PAGE_COND_JUMP` sequences. It recomputes labels and page layout after each change; any shortened branch that crosses a page boundary in a later pass is pinned to its far form. This also relocates string pointers, return addresses, and the self-modifying input operand. The build prints baseline/optimized byte counts and selected branch counts. The current image is 26,112 bytes (102 loaded pages). The assembler also checks that executable instructions remain below page 128 and relocates overflow literal pages into their reserved guest RAM area.
