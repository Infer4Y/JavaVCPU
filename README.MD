# JavaVCPU

A small virtual CPU written in Java. Programs are literal byte arrays containing opcodes and operands. The CPU executes them using byte registers, a program counter, and paged memory.

## Running

Use **JDK 26**, matching the project's IntelliJ configuration. The source uses `IO.println` and a compact `static void main()` entry point.

In IntelliJ IDEA, open the project, select JDK 26, and run `Main.java`.

Alternatively, run these commands from the project root with `javac` and `java` on your PATH:

```sh
javac -d build/classes/java/main src/main/java/xyz/ignite4inferneo/*.java
java -cp build/classes/java/main xyz.ignite4inferneo.Main
```

The Gradle wrapper can build the project:

```powershell
.\gradlew.bat build
```

On Linux or macOS, use `./gradlew build`. The project currently uses the Java plugin without a Gradle `run` task.

The current default program is `DISPLAY_DIAGONAL`. It opens a 256-by-256 window and draws a scaled,
one-pixel-wide yellow diagonal from the top-left to the bottom-right.

## CPU and memory

There are **128 memory pages**, each containing **128 bytes**, for a total of **16 KiB**. Page numbers and addresses range from **0 to 127**. Java byte values range from **-128 to 127**; arithmetic wraps when a result is cast back to a byte.

Each page holds both instructions and data. The `PAGE` register selects the page used for instruction fetches, loads, and stores. General-purpose registers retain their values when the page changes.

| Register | Index | Purpose |
| --- | --- | --- |
| A–F | 0–5 | General-purpose byte registers |
| COUNTER | 6 | Address of the next instruction within the active page |
| PAGE | 7 | Active memory page |

Memory is cleared before the selected program is uploaded. Execution starts on page 0 at address 0 and continues until `STOP`.

## Instruction set

Each instruction occupies one opcode byte followed by its operands. In the table, `r`, `a`, `b`, and `out` are **register indices**; `value` and `address` are **literal bytes**. `R[r]` means the value stored in register `r`, and `M[address]` means memory on the active page.

| Opcode | Instruction and operands | Bytes | Behavior |
| --- | --- | --- | --- |
| 0 | `ADD a, b, out` | 4 | `R[out] = R[a] + R[b]` |
| 1 | `SUB a, b, out` | 4 | `R[out] = R[a] - R[b]` |
| 2 | `STORE value, r` | 3 | `R[r] = value` |
| 3 | `JMP address` | 2 | Set `COUNTER` to the literal address |
| 4 | `STOP` | 1 | End execution and print elapsed CPU time |
| 5 | `OUTPUT r` | 2 | Print the signed value of `R[r]` |
| 6 | `COND_JUMP a, b, address` | 4 | Jump when `R[a] == R[b]`; otherwise continue |
| 7 | `LOAD_MEM r, address` | 3 | `R[r] = M[address]` |
| 8 | `STORE_MEM r, address` | 3 | `M[address] = R[r]` |
| 9 | `CHANGE_PAGE r` | 2 | Set `PAGE = R[r]`, then advance `COUNTER` by 2 |
| 10 | `CHANGE_PAGE_JUMP r, target` | 3 | Set `PAGE = R[r]` and `COUNTER = R[target]`; both operands are register indices |
| 11 | `CHANGE_PAGE_COND_JUMP r, a, b, address` | 5 | If `R[a] == R[b]`, set `PAGE = R[r]` and jump to the literal address; otherwise advance by 5 on the current page |
| 12 | `PUSH_GRA_MEM x, y, color` | 4 | Write palette color `R[color]` at framebuffer coordinate `(R[x], R[y])` |
| 13 | `CLEAR_GRA` | 1 | Clear the display framebuffer to black |
| 14 | `DISPLAY` | 1 | Schedule the completed framebuffer for painting in the display window |

`LOAD_MEM` and `STORE_MEM` currently use a **literal memory address**, even though some comments in `Main.java` describe a register-based address.

`CHANGE_PAGE` continues at the next instruction address on the destination page. The two page-jump instructions explicitly select a destination address, allowing different programs to occupy each page without padding.

## Display

`Monitor` owns a 128-by-128 RGB framebuffer. `PUSH_GRA_MEM` writes one pixel using register values
for its x coordinate, y coordinate, and color. Coordinates must be in the range `0` through `127`.
Call `DISPLAY` after a group of writes to show the completed frame; `CLEAR_GRA` resets it to black.

The display window scales the framebuffer to 256-by-256 pixels with nearest-neighbor interpolation,
so each virtual pixel appears as a 2-by-2 square.

Colors use the seven low bits of one byte in `RRGGGBB` order:

| Bits | Channel | Levels |
| --- | --- | --- |
| 6-5 | Red | 4 |
| 4-2 | Green | 8 |
| 1-0 | Blue | 4 |

The high bit is ignored. For example, `0b11_111_00` is full red plus full green, producing yellow.

## Included programs

Programs are defined in [Programs.java](src/main/java/xyz/ignite4inferneo/Programs.java) and uploaded by [Main.java](src/main/java/xyz/ignite4inferneo/Main.java).

### Display diagonal

`DISPLAY_DIAGONAL` is the default sample. It clears the framebuffer, writes yellow pixels from
`(0, 0)` through `(127, 127)`, presents the frame, and stops. Select another member of
`Programs.Program` in `Main.main()` to run one of the CPU-only examples.

### Paged memory demo

`PAGED_MEMORY` uses three separate byte arrays for pages **0**, **1**, and **127**:

1. Write **11** to address 120 on page 0.
2. Jump to page 1 and write **22** at the same address.
3. Jump to page 127 and write **33** at the same address.
4. Revisit each page at address 15 and print its stored value.

Page 1 also executes a conditional page jump that is false, followed by one that is true. A successful run prints:

```text
MEMORY CLEAR
PROGRAM_PAGED_MEMORY UPLOAD to PAGES 0, 1, 127
11
22
33
EOF CPU Ran for 0ms
```

Elapsed time varies. The three values show that the same address can retain different data on different pages.

### Counter loop

`PROGRAM_LOOP_127_3` uses arithmetic, output, and conditional jumps to count in register A, with register D tracking repetitions. Select `LOOP_127_3` in `Main.main()` to run it.

The current bytecode checks the repetition limit after incrementing and printing A. It prints 1 through 127 twice, then prints 1 and stops; the `_3` name does not currently represent three complete passes.

## Writing a program

Use `OPCode.code()` for instructions and `Registers.address()` for register operands:

```java
static final byte[] PROGRAM_EXAMPLE = new byte[] {
        OPCode.STORE.code(), 42, Registers.A.address(), // Address 0
        OPCode.OUTPUT.code(), Registers.A.address(),    // Address 3
        OPCode.STOP.code()                             // Address 5
};
```

Upload it to page 0 in `Main.main()` in place of the existing program uploads:

```java
System.arraycopy(Programs.PROGRAM_EXAMPLE, 0,
        Memory.getMemoryPage((byte) 0), 0, Programs.PROGRAM_EXAMPLE.length);
```

Calculate jump targets by counting **bytes**, including each opcode and its operands. Keep instructions and data within addresses 0–127, avoid placing data over live instructions, and use valid register indices. The CPU has no assembler or automatic address validation; invalid operands or instruction boundaries can cause Java exceptions.

## Source files

| File | Purpose |
| --- | --- |
| `Main.java` | Program loading, instruction dispatch, and CPU operations |
| `Memory.java` | Page storage and memory initialization |
| `OPCode.java` | Opcode values and instruction lengths |
| `Registers.java` | Register indices |
| `Programs.java` | Literal bytecode programs |
| `Monitor.java` | Swing display window, framebuffer, and palette conversion |
