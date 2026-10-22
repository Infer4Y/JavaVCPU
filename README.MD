# JavaVCPU

An experimental virtual CPU built in Java, with a bytecode operating system, paged RAM, persistent disks, a palette display, audio, keyboard/mouse input, and TCP networking.

The default boot starts **JV-DOS 2.0**. The shell, editor, JVS interpreter, media tools, and optional desktop run as guest bytecode on the virtual CPU.

## Get started

Install **JDK 26** and run these commands from the project root:

```powershell
.\gradlew.bat build
.\gradlew.bat run
```

On macOS/Linux, use `./gradlew` instead. You can also launch `xyz.ignite4inferneo.Main` from IntelliJ IDEA with JDK 26 selected.

Click the display window before typing. Enter `HELP` for everyday commands; the [JVS guide](docs/wiki/JVS-programming.md) contains programming syntax. Keyboard clicks are off by default; `SOUND ON` enables them.

The VM uses `storage.img` in its working directory. On first launch, the installer formats JVFS and writes the BIOS and DOS image. JV-DOS 2.0 reformats older experimental disks on startup; there is no migration. Later launches preserve files on valid JVFS volumes while updating firmware. The bootstrap reads the installed image through the storage device and transfers execution to the BIOS.

## See it run

| JV-DOS shell | Highlighted source editor |
| --- | --- |
| ![JV-DOS startup and command prompt](docs/wiki/images/dos-shell.png) | ![JV-DOS editor showing an arithmetic JVS program](docs/wiki/images/dos-editor.png) |

The [screenshot gallery](docs/wiki/Screenshots.md) shows the shell, documents, media, graphics demos, and Disk Workshop. Guest captures were refreshed October 22, 2026; older Workshop captures are labeled in the gallery.

## Use JV-DOS

| Command | Purpose |
| --- | --- |
| `DIR` | List the current folder |
| `MKDIR DEMOS`, `CD DEMOS`, `CD ..` | Create and navigate folders |
| `EDIT DEMO.JVS` | Open the source editor; Esc saves and exits |
| `RUN DEMO.JVS` | Run JVS source or COM bytecode; also opens image/music files |
| `VIEW FILE.TXT` | Page through a document; Esc closes it |
| `IMAGE FILE.IMG` | Display a palette image |
| `PLAY FILE.JMT` | Play a note stream; Esc stops playback |
| `DRIVE 1` | Select a mounted disk |
| `BROWSE example.com/` | Open the guest HTTP text browser |

The prompt keeps 16 commands of history until reboot. Up/Down recall commands; Tab completes command names and filenames in the current folder. In the editor, arrow keys navigate and Tab inserts four spaces.

The prompt shows the drive and directory, for example `0:\DOCS\>`. Launch a program directly with `HI.COM` or `HI`: DOS searches the current folder for the exact name, then `.COM`, then `.JVS`. Built-in commands take priority. `RUN` remains available; direct launches do not accept arguments.

Each 16 MiB disk has one JVFS volume with 1 KiB clusters and a 16-bit allocation table. Files span the disk with 24-bit lengths; directories grow beyond 255 entries. Names contain up to 12 characters. TYPE, VIEW, COPY, APPEND and music stream through resident filesystem services. EDIT and executable loading retain their RAM limits (61,200 bytes fullscreen, 8 KiB in JCTRL). Navigate with `CD` before operating in another folder.

Mount additional disks at startup:

```powershell
.\gradlew.bat run --args="--disk=1=secondary.img"
```

Device 0 uses `storage.img`. See [JV-DOS commands](docs/wiki/JV-DOS.md) and [peripheral buses](docs/wiki/Peripheral-buses.md) for more details.

The text browser supports plain HTTP and custom ports, for example `BROWSE http://localhost:8000/index.html`. It strips response headers and HTML tags and keeps up to 60,000 bytes of text in RAM. Up/Down scroll, Home returns to the top, and Esc exits or cancels. HTTPS, redirects, JavaScript, CSS layout, entity decoding, images, clickable links, and chunked responses are unsupported; servers must close the response connection. Script/style contents may appear as text.

## Disk Workshop and desktop

Open [Disk Workshop](tools/disk-workshop/index.html) locally in a browser to edit a disk, import text/source/binary files, convert images or MIDI clips, and install the experimental JCTRL desktop.

![Disk Workshop directory and JVS source editor](docs/wiki/images/workshop-text.png)

Workshop downloads an updated image and preserves the BIOS/OS bytes. **Stop the VM before replacing `storage.img`, then restart.** See the [Workshop guide](tools/disk-workshop/README.md) for formats and conversion limits.

The DOS installer now includes and updates **JCTRL** automatically. Start the VM and enter `JCTRL` to open the managed desktop on the system disk; no manual import is needed. The installer stores `JCTRL.COM` in the root folder and preserves user-modified or manual desktop copies. JCTRL provides draggable windows, a file explorer, a DOS window, document/media viewing, and guest program execution. JVS source runs through the resident DOS interpreter. Workshop's **Add JCTRL bundle** and [the bundled executable](examples/dos/JCTRL.COM) remain available for manual copies, launched with `RUN JCTRL.COM`. See the [desktop guide](docs/wiki/Experimental-desktop.md) for installation, controls and limits.

Rebuild the desktop executable and Workshop installation asset with:

```powershell
.\gradlew.bat desktopImage
```

Startup also installs and updates the bundled windowed `TEXTEDIT.COM` editor.
Launch it from JCTRL Files, enter a filename, and use F7 to save. It highlights
JVS and hex source and edits binary COM bytes in hex (4 KiB file limit).
See [editor controls and installation](examples/jctrl/README.md#jctrl-text-editor).
Rebuild its standalone COM with `gradlew.bat textEditExample`.

Startup also bundles `PAINT.COM`: open it from JCTRL Files to draw on a 128 by
96 pixel canvas with a palette, brush, eraser, and color picker. F7 saves
`PAINT.JVI`; F6 opens it and F8 starts a blank painting. It reuses JCTRL's shared
graphics/text and JVFS block routines. See [Paint controls](examples/jctrl/README.md#jctrl-paint).
Rebuild with `gradlew.bat desktopImage paintExample`.

The Workshop **Add JCTRL bundle** action includes JCTRL, TextEdit and Paint.
For external-media installs, `INSTALL FILE.COM` copies a portable program to
drive 0; `MV FILE.COM 0:` moves it after a successful copy. Build the single-file
DOS setup wizard with `gradlew.bat desktopInstaller`, then run `JCTRLSET.COM`.
See [installers and setup](docs/wiki/Installers.md).

## Virtual hardware

| Part | Details |
| --- | --- |
| CPU | 31 opcodes (0-30), sixteen byte-sized general-purpose registers, and four control registers |
| RAM | Two banks of 256 pages of 256 bytes: **128 KiB** total |
| Display | 256 × 256 framebuffer, 128 palette colors, scaled to an 800 × 800 Swing window |
| Storage | Separately addressed 16 MiB disk images, selectable by device ID |
| Input | Keyboard events/state and mouse input |
| Audio | 16 synthesized hardware voices |
| Network | Asynchronous TCP controller on a separate byte-addressed register bus |

General-purpose registers A-H use indices 0-7, and I-P use 12-19. Control registers are `COUNTER` (8), `PAGE` (9), `STORAGE_PAGE` (10, the legacy name for the storage device selector), and `MEMORY_BANK` (11).

Instruction fetches and ordinary memory operations use `MEMORY_BANK:PAGE`. Addresses and register values are unsigned bytes where used for addressing; arithmetic wraps to the low byte. Far memory instructions access another bank/page without changing the execution bank, while far jumps replace bank, page, and counter together.

Colors use the low seven bits in `RRGGGBB` order: four red levels, eight green levels, and four blue levels. The high bit is ignored on pixel writes. `DISPLAY` presents the completed framebuffer with pacing at roughly 30 frames per second.

See [architecture](docs/wiki/Architecture.md), the [complete instruction set](docs/wiki/Instruction-set.md), and [peripheral protocols](docs/wiki/Peripheral-buses.md).

## Write a program

Use `EDIT DEMO.JVS` and `RUN DEMO.JVS` for guest source programs. The [JVS guide](docs/wiki/JVS-programming.md) describes the language; the [COM guide](docs/wiki/COM-programming.md) covers bytecode programs and DOS conventions.

Raw bytecode can also be assembled with the Java opcode and register helpers:

```java
static final byte[] PROGRAM_EXAMPLE = new byte[] {
        OPCode.STORE.code(), 42, Registers.A.address(),
        OPCode.OUTPUT.code(), Registers.A.address(),
        OPCode.STOP.code()
};
```

This stores 42 in A, prints its signed value to the host console, then halts. Built-in examples live in `Programs.java` and load through `Programs.load(Program)`. The normal entry point boots the disk; to experiment with a built-in program, replace the `DiskInstaller.boot()` call in `Main` with the corresponding loader call.

Jump targets count every opcode and operand byte. Keep instructions within a 256-byte page. There is no CPU call/return instruction or hardware stack. The kernel and desktop have Java-based assemblers; [examples](docs/wiki/Example-programs.md) include commented hex that can be converted with `examples/build.ps1`.

Built-in programs include `BIOS_POST`, `DEMO_OS` (the legacy name for JV-DOS), `PERIPHERAL_DIAGNOSTICS`, `HELLO_CPU`, `DISPLAY_HELLO_CPU`, `LOOP_127_3`, `PAGED_MEMORY`, and `SNOWFALL`.

## Development

```powershell
# Build and run checks (including the headless storage regressions)
.\gradlew.bat build

# Run the headless RAM/filesystem regression suite directly
.\gradlew.bat storageRegression
```

The `storageRegression` task compiles and runs without external test-library dependencies. Its disk image is written under `build/`.

| Source / directory | Purpose |
| --- | --- |
| `src/main/java/xyz/ignite4inferneo/Main.java` | CPU state and instruction dispatch |
| `Memory.java`, `Registers.java`, `OPCode.java` | RAM, register indices, instruction encodings |
| `Monitor.java`, `FramePacer.java` | Framebuffer presentation and frame pacing |
| `Input.java`, `Audio.java`, `Storage.java`, `Network.java` | Host-backed devices |
| `DiskInstaller.java`, `Programs.java` | Boot installation and built-in bytecode |
| `jvdos/JvDosImage.java` | Guest DOS kernel, editor, interpreter, and media tools |
| `jvdos/DesktopImage.java` | Guest desktop assembler |
| `src/test/java/` | Headless regression checks |
| `examples/` | Guest source, bytecode, and sample media |
| `tools/disk-workshop/` | Browser-local disk editor and media converters |
| `docs/wiki/`, `wiki/` | Editable guides and the linked GitHub wiki submodule |

## GitHub wiki

The [GitHub wiki](https://github.com/Infer4Y/JavaVCPU/wiki) covers JavaVCPU, JV-DOS 2.0, JCTRL applications and tooling. `wiki/` is a submodule linked to `https://github.com/Infer4Y/JavaVCPU.wiki.git`. Initialize it with `git submodule update --init wiki`. Edit `docs/wiki/`, then run `python tools/wiki/sync.py` to update the checkout. [Wiki maintenance](tools/wiki/README.md) documents screenshot regeneration, validation and the separate wiki commit/push.
