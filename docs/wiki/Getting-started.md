# Getting started

Use JDK 26. The source uses recent Java features, including module imports and `IO.println`. Select that JDK for both IntelliJ and Gradle. Run commands from the project root so the VM finds the intended `storage.img`.

```powershell
./gradlew.bat build
java -cp build/classes/java/main xyz.ignite4inferneo.Main
```

On macOS/Linux use `./gradlew build`. There is no Gradle `run` task. IntelliJ can run `xyz.ignite4inferneo.Main` directly. Click the display window to focus it, then enter `HELP` or `CODEHELP`.

The normal launch initializes display, audio, input, and storage. A missing disk becomes a blank 16 MiB image. `DiskInstaller` installs the system if its header is absent or outdated, preserving the distant user-file bank. A bytecode bootstrap at RAM page 255 copies system pages from disk offset 512, then jumps to BIOS page 0. BIOS hands off to JV-DOS. The guest seeds `HELLO.COM` on an uninitialized directory.

For bare-metal examples, rebuild the binary listings:

```powershell
powershell -ExecutionPolicy Bypass -File examples/build.ps1
```

The normal launcher always boots DOS. [Example programs](Example-programs) documents a temporary replacement for the boot call that loads raw bytecode into page 0:0. No launcher changes are included in this documentation package. Programs can write other RAM pages themselves.

| Source | Responsibility |
| --- | --- |
| `Main.java` | Register state, CPU loop and opcode methods |
| `OPCode.java`, `Registers.java` | Instruction numbers/lengths and register indices |
| `Memory.java` | 256 memory pages |
| `Monitor.java`, `Input.java`, `Audio.java` | Display and host devices |
| `Storage.java`, `DiskInstaller.java` | Disk persistence and bytecode boot loader |
| `Programs.java` | Built-in demos and BIOS assembly |
| `JvDosImage.java` | Guest kernel assembler, shell, editor, interpreter and services |
| `tools/disk-workshop/` | Offline browser disk editor and converters |
| `examples/` | Commented byte listings, generated binaries and JVS source |

Generate Java API documentation with `./gradlew.bat javadoc`; open `build/docs/javadoc/index.html`. The Workshop has a browser test page at `tools/disk-workshop/test.html`.

See [Tooling and Disk Workshop](Tooling-and-Disk-Workshop) for imports and [Example programs](Example-programs) for expected results.
