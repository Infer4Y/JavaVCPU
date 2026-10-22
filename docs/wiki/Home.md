# JavaVCPU wiki

JavaVCPU is an experimental Java virtual CPU with **31 opcodes**, sixteen byte-sized general-purpose registers, **128 KiB RAM**, a 256 by 256 palette display, keyboard/mouse input, synthesized audio, persistent disks and asynchronous TCP networking. **JV-DOS 2.0** is its resident guest operating system. **JCTRL** adds a desktop whose windows, program interpreter and applications also run as guest bytecode.

![JV-DOS 2.0 startup and drive-aware prompt](images/dos-shell.png)

Start with [Getting started](Getting-started), then enter `HELP` at the DOS prompt or `JCTRL` to open the desktop. Startup manages **JCTRL.COM, TEXTEDIT.COM and PAINT.COM** on drive 0. [Disk Workshop](Tooling-and-Disk-Workshop) imports host files and converts images and MIDI without a server.

| Guide | Contents |
| --- | --- |
| [Screenshots](Screenshots) | Current shell, editor, desktop, Paint, TextEdit, WEB and setup captures |
| [Getting started](Getting-started) | JDK 26, build/run, boot installation and project map |
| [JavaVCPU architecture](Architecture) | Registers, memory banks, display, input, audio and storage |
| [Instruction set](Instruction-set) | All 31 opcodes and operand conventions |
| [JV-DOS 2.0](JV-DOS) | Commands, folders/drives, history, editing, media and startup scripts |
| [JCTRL desktop](Experimental-desktop) | Windows, Files, resident DOS, application execution and shared services |
| [JCTRL applications](JCTRL-applications) | Paint, TextEdit, WEB and Pong controls and limits |
| [Installers and setup](Installers) | Managed apps, external media, INSTALL/MV and JCTRLSET.COM |
| [Tooling and Disk Workshop](Tooling-and-Disk-Workshop) | Disk editing, asset generation, checks and wiki publishing |
| [JVS programming](JVS-programming) | Source syntax, arithmetic, loops and limits |
| [COM programming](COM-programming) | Guest bytecode, resident DOS and JCTRL application ABI v7 |
| [Disk and media formats](Disk-and-media-formats) | JVFS 1, transactional replacements, JI images and JM music |
| [Peripheral buses](Peripheral-buses) | Storage addressing, mounted devices and TCP registers |
| [Example programs](Example-programs) | Bare-metal and DOS programs |

These pages describe the current working tree as of **October 22, 2026**. JVFS replaces the old experimental per-bank filesystem; incompatible disks are reformatted on startup. Back up older images before using this version. Valid JVFS files survive firmware updates; unchanged managed desktop apps can be upgraded while modified/manual copies are preserved.

COM files contain JavaVCPU instructions. JVS is a small guest line interpreter. Legacy names such as `JVCPM` and `DEMO_OS` remain in firmware headers and source.

Source: [JavaVCPU repository](https://github.com/Infer4Y/JavaVCPU). Editable documentation lives in `docs/wiki/`; `wiki/` is a Git submodule connected to [the GitHub wiki repository](https://github.com/Infer4Y/JavaVCPU.wiki.git). See [the publishing workflow](Tooling-and-Disk-Workshop#wiki-checkout-and-publishing).
