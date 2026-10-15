# JavaVCPU example programs

Run `powershell -ExecutionPolicy Bypass -File examples/build.ps1` from the repository root to rebuild the included binaries from commented hex listings. No assembler or extra dependency is required. The hex sources describe opcode bytes, not JVS source.

## Bare metal

The normal launcher always boots DOS. To experiment with these binaries, temporarily replace `DiskInstaller.boot();` in `Main.main()` with the following loading code, then build/run Main with JDK 26:

```java
byte[] program = java.nio.file.Files.readAllBytes(
        java.nio.file.Path.of("examples/bare-metal/ANSWER.bin"));
System.arraycopy(program, 0, Memory.getMemoryPage((byte) 0), 0, program.length);
REGISTERS[Registers.PAGE.address()] = 0;
REGISTERS[Registers.COUNTER.address()] = 0;
```

Set IntelliJ's Main working directory to the project root. Change the path to select another example. Restore `DiskInstaller.boot();` afterward to resume normal DOS booting. This is a documented experiment; the project launcher has not been changed.

| Binary | Expected result |
| --- | --- |
| `ANSWER.bin` | Prints 42 to the host console. |
| `COUNT.bin` | Prints 0, 1, 2, 3, 4 on separate console lines. |
| `PIXEL.bin` | Displays a white pixel at (128, 128). |
| `BANKED-RAM.bin` | Writes/reads RAM address 0x1FFFF in bank 1 and prints 42. |
| `MEMORY.bin` | Writes data and executable code to page 1, jumps there, and prints 42. |

These programs start at page 0, byte 0 and bypass execution of BIOS and DOS. The normal device and disk initialization still runs. They use `STOP` to halt execution; close the display window afterward. Keep the uploaded program within one 256-byte page. These examples do not access storage.

## JV-DOS

Start the normal VM at least once to create and initialize `storage.img`, then stop it. Open [Disk Workshop](../tools/disk-workshop/index.html), select the disk, and import files from `dos/`:

| File | Command | Expected result |
| --- | --- | --- |
| `HELLO.JVS` | `RUN HELLO.JVS` | Greeting and beep, then DOS prompt. |
| `LONG.JVS` | `RUN LONG.JVS` | Multi-block source summing 1 through 10 and printing 55. |
| `MATH.JVS` | `RUN MATH.JVS` | 42 and 0, demonstrating arithmetic and byte wrap. |
| `COUNT.JVS` | `RUN COUNT.JVS` | 0 through 4, then DONE. |
| `HI.COM` | `RUN HI.COM` | HI and a newline, then DOS prompt. |

Import `.JVS` and `.COM`, not their `.hex` source listings. Download the updated disk, replace the project's `storage.img` while the VM is stopped, and restart with the normal DOS boot code. These small examples retain the bank-zero COM convention; larger linked COM programs run in RAM bank 1. `EDIT COUNT.JVS` opens the guest editor; Esc saves. Changing physical line positions changes `GOTO`/`IF` targets.

The COM example runs at page 248:0 and calls DOS services at 249:0 (print D, return to A:B) and 249:9 (prompt). Registers can be clobbered by services. JVS `END` returns to DOS; bytecode `STOP` halts the CPU.

See [the wiki sources](../docs/wiki/Home.md) for architecture, the full instruction set, language syntax, device ports, and disk format.
