# COM programming

COM files contain flat JavaVCPU instructions. They are not x86 executables. DOS verifies opcode numbers, instruction lengths and register operands, copies at most 240 bytes into transient RAM page 248, then enters at byte 0. Verification does not sandbox a program or prove that its branches and memory accesses are safe.

## Stable guest services

| Entry | Inputs | Result |
| --- | --- | --- |
| page 249, offset 0 | D = ASCII character; A = return page; B = return offset | Prints one character and jumps back to A:B |
| page 249, offset 9 | None | Returns to DOS shell |

Call using `CHANGE_PAGE_JUMP` with registers containing service page and offset. The service and renderer may overwrite general registers. Reload arguments for each call; do not rely on register preservation. There is no stack-based call instruction.

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

The guest uses pages 246 for font pointers, 247 for instruction descriptors, 248 for the transient program, 249 for service trampolines, and 250-252 for names, input and kernel variables. The bootstrap uses page 255. Resident kernel/font/image data occupy other pages. Limit a small COM to its transient area and documented services; there is no declared general-purpose free RAM contract.

DOS's validator walks the whole file as instructions, so embedded arbitrary data can be rejected. Instruction operands must fit inside the file and each fetched instruction must fit inside its page. The loader leaves the rest of transient RAM unchanged. Do not branch into stale bytes after the program's end. See [Instruction set](Instruction-set) for byte lengths and operand conventions.
