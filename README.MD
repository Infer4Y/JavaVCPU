# JavaVCPU

> **An experimental virtual CPU, built in Java.**
>
> JavaVCPU executes byte-sized instructions to work with its own paged memory and palette display. It is a small place to experiment with the pieces beneath a computer: registers, opcodes, jumps, memory pages, and pixels.

```
 bytecode program  ──►  JavaVCPU  ──►  memory pages
                         │
                         └────────►  256 × 256 display
```

## See it run

| JV-DOS shell | Highlighted source editor |
| --- | --- |
| ![JV-DOS 1.1 startup and command prompt](docs/wiki/images/dos-shell.png) | ![JV-DOS editor showing an arithmetic JVS program](docs/wiki/images/dos-editor.png) |

The [complete screenshot gallery](docs/wiki/Screenshots.md) shows boot, help, directory, documents, images, music, program execution, graphics demos and Disk Workshop.

The default BIOS boots **JV-DOS 1.1**, implemented using the existing VCPU opcodes. It provides an editable command line, persistent named files, a source editor with highlighting, runnable JVS programs, COM bytecode loading, palette images, a document pager, and a four-voice music player. Type `HELP` or `CODEHELP` in the display window.

Click the JavaVCPU display window before typing.

Open [Disk Workshop](tools/disk-workshop/index.html) locally to import small text/source/binary files, convert images or MIDI clips, and download an updated `storage.img`. Stop the VM before replacing its disk, then restart. The tool preserves the BIOS and OS bytes. [Format documentation and limits](tools/disk-workshop/README.md) explain the 16-file, 240-byte-per-file directory and media conversions.

![Disk Workshop directory and JVS source editor](docs/wiki/images/workshop-text.png)

Use `VIEW FILE.TXT` to page through documents, `IMAGE FILE.IMG` to display a palette image, and `PLAY FILE.JMT` to play imported MIDI-note streams. Arrow keys navigate documents; Esc closes a viewer or stops music. `RUN` also recognizes imported image/music types. Shift supplies punctuation, including symbols, braces and quotes; keypad keys work. `SOUND OFF` disables keyboard clicks.

On first launch, `DiskInstaller` writes the BIOS and MicroDOS bytecode image to `storage.img`. Later launches execute a small bootstrap program that reads the image through the storage instruction, writes it into virtual memory, and begins at its BIOS entry page.

## Get started

JavaVCPU requires **JDK 26**.

```powershell
./gradlew.bat build
```

Then run `xyz.ignite4inferneo.Main` from IntelliJ IDEA with JDK 26 selected. There is currently no Gradle `run` task.

## What is inside?

| Part | What it does |
| --- | --- |
| **CPU** | Executes bytecode through eight general-purpose registers plus a program counter and active-page register. |
| **Memory** | Provides 256 pages of 256 bytes: **64 KiB** in total. |
| **Display** | Renders a 256 × 256 palette framebuffer in an 800 × 800 Swing window. |
| **Programs** | Includes a POST boot flow, text rendering, a snowfall animation, and memory-page examples. |

## Write a program

Programs are byte arrays assembled with the opcode and register helpers:

```java
static final byte[] PROGRAM_EXAMPLE = new byte[] {
        OPCode.STORE.code(), 42, Registers.A.address(),
        OPCode.OUTPUT.code(), Registers.A.address(),
        OPCode.STOP.code()
};
```

Add the bytecode to `Programs`, add a matching `Program` enum value and loader case, then select it in `Main.main()`. Jump targets are byte addresses, so include every opcode and operand when calculating them.

## Included programs

| Program | Demonstrates |
| --- | --- |
| `BIOS_POST` | The default BIOS POST and handoff to the resident monitor. |
| `DEMO_OS` | Legacy enum name for the resident JV-DOS 1.1 bytecode operating system. |
| `PERIPHERAL_DIAGNOSTICS` | Storage read/write, keyboard polling, audio control, multiplication, and increment. |
| `DISPLAY_HELLO_CPU` | Drawing **HELLO CPU** with graphics bytecode. |
| `SNOWFALL` | Pixel reads and writes for falling, accumulating snow. |
| `PAGED_MEMORY` | Independent values at the same address on different pages. |
| `LOOP_127_3` | Register arithmetic and conditional looping. |
| `HELLO_CPU` | Building ASCII values with arithmetic and printing them. |

## Architecture reference

<details>
<summary><strong>Memory and registers</strong></summary>

JavaVCPU has 256 memory pages of 256 bytes each. `PAGE` selects the page used for instruction fetches and memory instructions; changing pages preserves register values. Page numbers, addresses, and register operands are interpreted as unsigned bytes. Arithmetic wraps when stored back into a register.

| Register | Index | Purpose |
| --- | --- | --- |
| A–H | 0–7 | General-purpose byte registers |
| COUNTER | 8 | Next instruction address on the active page |
| PAGE | 9 | Active memory page |

`Programs.load` uploads a program and resets `PAGE` and `COUNTER` to zero. It replaces only the uploaded byte range; it does not clear the rest of memory.

</details>

<details>
<summary><strong>Instruction set</strong></summary>

Each instruction is one opcode byte followed by zero or more operands. In the table, `r`, `a`, `b`, and `out` are register indices; `value` and `address` are literal bytes. `R[r]` is a register value and `M[address]` is an address on the active page.

| Opcode | Instruction | Bytes | Behavior |
| --- | --- | --- | --- |
| 0 | `ADD a, b, out` | 4 | `R[out] = R[a] + R[b]` |
| 1 | `SUB a, b, out` | 4 | `R[out] = R[a] - R[b]` |
| 2 | `STORE value, r` | 3 | `R[r] = value` |
| 3 | `JMP address` | 2 | Set `COUNTER` to `address` |
| 4 | `STOP` | 1 | End execution and print timing information |
| 5 | `OUTPUT r` | 2 | Print the signed value of `R[r]` |
| 6 | `COND_JUMP a, b, address` | 4 | Jump when `R[a] == R[b]` |
| 7 | `LOAD_MEM r, address` | 3 | `R[r] = M[address]` |
| 8 | `STORE_MEM r, address` | 3 | `M[address] = R[r]` |
| 9 | `CHANGE_PAGE r` | 2 | Set `PAGE = R[r]` |
| 10 | `CHANGE_PAGE_JUMP r, target` | 3 | Set `PAGE = R[r]`, then `COUNTER = R[target]` |
| 11 | `CHANGE_PAGE_COND_JUMP r, a, b, address` | 5 | On equality, select the page and jump |
| 12 | `PUSH_GRA_MEM x, y, color` | 4 | Write `R[color]` at `(R[x], R[y])` |
| 13 | `CLEAR_GRA` | 1 | Fill the framebuffer with black |
| 14 | `DISPLAY` | 1 | Present the framebuffer and limit execution to roughly 30 frames/second |
| 15 | `READ_GRA_MEM x, y, out` | 4 | Store the palette color at `(R[x], R[y])` in `R[out]` |
| 16 | `READ_INPUT device, out` | 3 | Read a device port into `R[out]` |
| 17 | `OUTPUT_AUDIO voice, note, control` | 4 | Configure an audio voice from registers |
| 18 | `READ_BYTE_FROM_STORAGE x, y, z, out` | 5 | Read storage byte `(R[x], R[y], R[z])` into `R[out]` |
| 19 | `WRITE_BYTE_TO_STORAGE x, y, z, value` | 5 | Write `R[value]` to storage byte `(R[x], R[y], R[z])` |
| 20 | `MULTIPLY a, b, out` | 4 | `R[out] = R[a] * R[b]` |
| 21 | `INCREMENT_REGISTER r` | 2 | Increment `R[r]` |
| 22 | `STORE_MEM_ON_PAGE value, page, address` | 4 | `Memory[R[page]][R[address]] = R[value]` |

</details>

<details>
<summary><strong>Display and colors</strong></summary>

`Monitor` stores a 256 × 256 framebuffer and uses nearest-neighbor scaling for an 800 × 800 Swing window. Call `DISPLAY` after drawing a frame.

`PUSH_GRA_MEM` and `READ_GRA_MEM` read their coordinates from registers. Valid coordinates are `0` through `255`; coordinates are interpreted as unsigned bytes.

Colors use the low seven bits of a byte in `RRGGGBB` order:

| Bits | Channel | Levels |
| --- | --- | --- |
| 6–5 | Red | 4 |
| 4–2 | Green | 8 |
| 1–0 | Blue | 4 |

The high bit is ignored on writes. `0b11_111_00` is yellow and `0b11_111_11` is white.

</details>

## Project map

| File | Purpose |
| --- | --- |
| `Main.java` | CPU state, instruction dispatch, and implementations |
| `Memory.java` | Paged-memory storage |
| `Monitor.java` | Framebuffer access and Swing rendering |
| `OPCode.java` | Opcode values and instruction lengths |
| `Programs.java` | Built-in bytecode and its loader |
| `JvDosImage.java` | Assembles the guest DOS kernel, editor, source interpreter, and media tools |
| `tools/disk-workshop/index.html` | Browser-local disk importer/editor and image/MIDI converters |
| `Registers.java` | Register indices |
