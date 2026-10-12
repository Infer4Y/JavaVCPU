# Instruction set

Each instruction is one opcode byte followed by operands. `a`, `b`, `r`, `out`, `x`, `y`, `color`, `page` and `target` below are **register indices**. `value`, `address` and `port` are literal bytes. Operands are positional in the order shown. R is the register file; M is memory on PAGE.

| Decimal / hex | Instruction | Bytes | Effect |
| --- | --- | --- | --- |
| 0 / 00 | `ADD a b out` | 4 | R[out] = R[a] + R[b] |
| 1 / 01 | `SUB a b out` | 4 | R[out] = R[a] - R[b] |
| 2 / 02 | `STORE value r` | 3 | R[r] = literal value |
| 3 / 03 | `JMP address` | 2 | COUNTER = literal address |
| 4 / 04 | `STOP` | 1 | Halt CPU, print execution statistics |
| 5 / 05 | `OUTPUT r` | 2 | Print signed register value to host console |
| 6 / 06 | `COND_JUMP a b address` | 4 | Jump when R[a] == R[b] |
| 7 / 07 | `LOAD_MEM out address` | 3 | R[out] = M[literal address] |
| 8 / 08 | `STORE_MEM r address` | 3 | M[literal address] = R[r] |
| 9 / 09 | `CHANGE_PAGE r` | 2 | PAGE = R[r], then advance counter |
| 10 / 0A | `CHANGE_PAGE_JUMP page target` | 3 | PAGE = R[page], COUNTER = R[target] |
| 11 / 0B | `CHANGE_PAGE_COND_JUMP page a b address` | 5 | On equality, PAGE = R[page], jump to literal address |
| 12 / 0C | `PUSH_GRA_MEM x y color` | 4 | Draw pixel from three registers |
| 13 / 0D | `CLEAR_GRA` | 1 | Clear framebuffer |
| 14 / 0E | `DISPLAY` | 1 | Present frame and sleep about 33 ms |
| 15 / 0F | `READ_GRA_MEM x y out` | 4 | Read pixel into R[out] |
| 16 / 10 | `READ_INPUT port out` | 3 | Read literal device port |
| 17 / 11 | `OUTPUT_AUDIO voice note control` | 4 | Set audio voice from three registers |
| 18 / 12 | `READ_BYTE_FROM_STORAGE x y z out` | 5 | Read disk at register-derived address |
| 19 / 13 | `WRITE_BYTE_TO_STORAGE x y z r` | 5 | Write register value to disk |
| 20 / 14 | `MULTIPLY a b out` | 4 | R[out] = R[a] * R[b] |
| 21 / 15 | `INCREMENT_REGISTER r` | 2 | R[r] = R[r] + 1 |
| 22 / 16 | `STORE_MEM_ON_PAGE r page target` | 4 | Memory[R[page]][R[target]] = R[r] |

Arithmetic stores the low byte. Branch addresses count **every opcode and operand byte**, not instruction lines. Keep each instruction within a page. There is no CPU call/return opcode or stack; programs can implement calls using explicit page/offset registers or memory. The DOS print service provides a small guest convention.

For example, `02 2A 00 05 00 04` means STORE 42 A, OUTPUT A, STOP. `10 00 03` means read keyboard port 0 into D, not read a port whose number is held in A.

These mnemonic listings explain encodings; the project does not provide a general standalone mnemonic assembler. `examples/build.ps1` converts commented hex to bytes. The guest kernel has a private Java assembler in `JvDosImage` that handles its own labels and page transitions.
