# JavaVCPU

JavaVCPU is a small Java bytecode virtual machine with paged memory, eight general-purpose byte registers, two control registers, and a palette-based framebuffer rendered in Swing.

## Run

Use JDK 26. In IntelliJ IDEA, select JDK 26 and run `Main.java`.

To build from the command line:

```powershell
./gradlew.bat build
```

On Linux or macOS, run `./gradlew build`. The Gradle project has no `run` task; launch `xyz.ignite4inferneo.Main` from the IDE or with `java` after compilation.

The default program is `DISPLAY_HELLO_CPU`, which writes `HELLO CPU` to the framebuffer with graphics instructions.

## CPU model

The VM has 256 memory pages of 256 bytes each, for 64 KiB total. Pages and addresses range from `0` through `255`. Register and memory values are Java `byte` values, so arithmetic wraps when cast back to a byte.

Each page holds instructions and data. `PAGE` selects the page used for instruction fetches and memory instructions; switching pages preserves all registers. Byte operands that name pages, addresses, or registers are interpreted as unsigned values.

| Register | Index | Purpose |
| --- | --- | --- |
| A-F | 0-5 | General-purpose byte registers |
| G-H | 6-7 | General-purpose byte registers |
| COUNTER | 8 | Address of the next instruction on the active page |
| PAGE | 9 | Active memory page |

`Programs.load` uploads the selected bytecode and resets `PAGE` and `COUNTER` to zero. It does not clear memory pages before uploading, so it replaces only the byte range in the program.

## Instruction set

Every instruction is one opcode byte followed by zero or more operand bytes. In the table, `r`, `a`, `b`, and `out` are register indices; `value` and `address` are literal bytes. `R[r]` means a register value and `M[address]` means an address in the active page.

| Opcode | Instruction | Bytes | Behavior |
| --- | --- | --- | --- |
| 0 | `ADD a, b, out` | 4 | `R[out] = R[a] + R[b]` |
| 1 | `SUB a, b, out` | 4 | `R[out] = R[a] - R[b]` |
| 2 | `STORE value, r` | 3 | `R[r] = value` |
| 3 | `JMP address` | 2 | Set `COUNTER` to `address` |
| 4 | `STOP` | 1 | End execution and print timing information |
| 5 | `OUTPUT r` | 2 | Print the signed value of `R[r]` |
| 6 | `COND_JUMP a, b, address` | 4 | Jump when `R[a] == R[b]`; otherwise continue |
| 7 | `LOAD_MEM r, address` | 3 | `R[r] = M[address]` |
| 8 | `STORE_MEM r, address` | 3 | `M[address] = R[r]` |
| 9 | `CHANGE_PAGE r` | 2 | Set `PAGE = R[r]`, then continue |
| 10 | `CHANGE_PAGE_JUMP r, target` | 3 | Set `PAGE = R[r]` and `COUNTER = R[target]` |
| 11 | `CHANGE_PAGE_COND_JUMP r, a, b, address` | 5 | If equal, select the page and jump; otherwise continue |
| 12 | `PUSH_GRA_MEM x, y, color` | 4 | Write `R[color]` at `(R[x], R[y])` |
| 13 | `CLEAR_GRA` | 1 | Fill the framebuffer with black |
| 14 | `DISPLAY` | 1 | Present the framebuffer and pause for about 33 ms |
| 15 | `READ_GRA_MEM x, y, out` | 4 | Store the palette color at `(R[x], R[y])` in `R[out]` |

`LOAD_MEM` and `STORE_MEM` use literal addresses. The page-jump instructions select an explicit destination address, so programs can occupy different pages without matching layouts.

## Display and colors

`Monitor` stores a 128 by 128 framebuffer and renders it in a 512 by 512 Swing window with nearest-neighbor scaling. Each virtual pixel appears as a 4 by 4 block.

`PUSH_GRA_MEM` and `READ_GRA_MEM` take coordinates from registers. Coordinates must be between `0` and `127`. Call `DISPLAY` after drawing a frame; it also limits animation speed to roughly 30 frames per second.

Colors use the low seven bits of a byte in `RRGGGBB` order:

| Bits | Channel | Levels |
| --- | --- | --- |
| 6-5 | Red | 4 |
| 4-2 | Green | 8 |
| 1-0 | Blue | 4 |

The high bit is ignored on writes. `0b11_111_00` is yellow and `0b11_111_11` is white.

## Included programs

Programs live in `Programs.java` and are selected in `Main.main()`.

- `DISPLAY_HELLO_CPU` uses only existing graphics bytecode instructions to draw `HELLO CPU`.
- `SNOWFALL` drops white flakes until they reach the bottom or an occupied pixel, then starts the next flake in the next column.
- `PAGED_MEMORY` stores `11`, `22`, and `33` at address `120` on pages `0`, `1`, and `127`, showing that each page has independent storage.
- `LOOP_127_3` counts register A from `1` through `127` while register D tracks passes.
- `HELLO_CPU` constructs the ASCII values for `hello I'm a cpu` with arithmetic and prints their decimal values.

## Write a program

Use `OPCode.code()` for instruction bytes and `Registers.address()` for register operands:

```java
static final byte[] PROGRAM_EXAMPLE = new byte[] {
        OPCode.STORE.code(), 42, Registers.A.address(),
        OPCode.OUTPUT.code(), Registers.A.address(),
        OPCode.STOP.code()
};
```

Add the program to `Programs`, add a matching `Program` enum value and `load` case, then select it in `Main.main()`. Calculate jump targets in bytes, including every opcode and operand. Keep instructions and data within the active 256-byte page, avoid overwriting live instructions, and use valid register indices and framebuffer coordinates.

## Source files

| File | Purpose |
| --- | --- |
| `Main.java` | CPU state, instruction dispatch, and instruction implementations |
| `Memory.java` | Backing storage for paged memory |
| `Monitor.java` | Thread-safe framebuffer access and Swing rendering |
| `OPCode.java` | Opcode values and encoded instruction lengths |
| `Programs.java` | Built-in bytecode programs and their loader |
| `Registers.java` | Register-file indices |
