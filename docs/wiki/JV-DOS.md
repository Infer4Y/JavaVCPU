# JV-DOS 1.3

JV-DOS runs as guest bytecode. It provides a shell, persistent named files, an editor, JVS interpretation, COM loading and small media viewers. Commands and filenames should be uppercase. Click the VM window before typing.

| Command help (`HELP`) | Directory (`DIR`) |
| --- | --- |
| ![JV-DOS shell help](images/dos-help.png) | ![Directory of sample files](images/dos-directory.png) |

| Command | Purpose / example |
| --- | --- |
| `HELP`, `CODEHELP` | Shell or programming help |
| `DIR` | List filenames and byte lengths |
| `TYPE NAME`, `CAT NAME` | Print file contents directly |
| `VIEW NAME`, `MORE NAME` | Read a document with navigation |
| `WRITE NAME TEXT` | Create or replace text |
| `APPEND NAME TEXT` | Append text to an existing file |
| `EDIT NAME` | Create/open a file in the editor |
| `COPY OLD NEW` | Copy to a new name |
| `REN OLD NEW` | Rename to an unused name |
| `DEL NAME`, `ERASE NAME` | Delete a file |
| `SAVE NAME HEX` | Create binary content from uppercase hex byte pairs |
| `RUN NAME` | Interpret text, load bytecode, or open image/music by type |
| `IMAGE NAME`, `SHOW NAME` | Display a palette image |
| `PLAY NAME` | Play a MIDI-note stream |
| `PRINT TEXT`, `ECHO TEXT` | Print literal text |
| `CLS`, `CLEAR` | Clear display |
| `VER`, `MEM` | Version and memory/file limits |
| `SOUND ON`, `SOUND OFF` | Enable/disable keyboard clicks |
| `BEEP` | Short tone |
| `REBOOT` | Restart the resident guest boot routine |
| `EXIT` | Halt the CPU |

Each disk bank provides 255 shared blocks. Names have up to 12 characters. Files use one or more linked blocks and can hold up to 61,200 bytes, subject to available bank space. There are no folders. The CLI and source reader have a 100-character line limit; use Workshop to import longer documents or binary files. `COPY`/`REN` report `DESTINATION EXISTS` if the target name is taken.

## Editor and viewers

`EDIT DEMO.JVS` opens the guest source editor. Arrow keys move; Home/End navigate the line; Backspace and Delete remove characters; Enter inserts a newline; Tab indents. Esc **saves and leaves**. It is not a cancel operation. The editor highlights source text.

The document pager uses Up/Down, Page Up/Page Down, Space/Enter and Home; Esc closes it. Image viewing closes with Esc, Enter or Q. Esc stops music and silences its voices. A running JVS program checks for Esc between statements.

| Source editor | Document pager |
| --- | --- |
| ![Highlighted source in EDIT MATH.JVS](images/dos-editor.png) | ![VIEW WELCOME.TXT in the document pager](images/dos-document.png) |

| Palette image | Music playback status |
| --- | --- |
| ![IMAGE SMILE.IMG palette viewer](images/dos-image.png) | ![PLAY THEME.JMT status](images/dos-music.png) |

The [screenshot gallery](Screenshots) also shows startup, programming help and program execution.

`RUN` dispatches image/music from their file type, interprets type-1 text, and otherwise validates and loads raw bytecode. It also heuristically treats binary content starting with printable bytes 32-90 as source. The supplied COM starts with opcode 2 to avoid that ambiguity. Filename extensions alone do not define the execution format.

See [JVS programming](JVS-programming), [COM programming](COM-programming), and [Tooling and Disk Workshop](Tooling-and-Disk-Workshop).

## Disk banks (JV-DOS 1.3)

The disk has 254 file banks, numbered 2-255, with 255 blocks shared by all files in each bank. `BANK` shows the selected bank. `BANK +` and `BANK -` move one bank at a time and stop at the boundaries. Reboot returns to bank 2. Existing files remain in bank 2; no disk conversion is required. All file commands use the selected bank, and COPY/REN stay within that bank. Larger files consume multiple blocks; small files retain their existing layout.

The backing hard drive remains 16 MiB because the CPU uses a three-byte disk address. DOS now uses most of that space through linked file blocks. The Java DOS image assembler lives in `xyz.ignite4inferneo.jvdos`; CPU and device code remain in `xyz.ignite4inferneo`.

The editor buffers files in RAM bank 1, supports 16-bit cursor positions, and commits on Esc. When the bank is full, the existing disk file stays unchanged and the editor retains its buffer for shortening and retrying. TYPE/VIEW, COPY, APPEND, REN, DEL, JVS RUN and COM RUN support linked files.

## Shell history and completion

At the DOS prompt, Up recalls older commands and Down moves toward newer commands. The last 16 submitted, nonempty interactive lines are kept until reboot. Moving Down past the newest entry restores the line you were drafting, including its cursor position. Recalled commands can be edited and submitted again. JVS statements and editor text are not added to history.

Tab completes the word at the cursor. Repeated Tab cycles matching command names, or filenames in the selected disk bank for file-command arguments (including both COPY/REN filenames). The rest of the input line is preserved. A completed final word receives a trailing space. Unmatched prefixes and replacements that exceed the 100-character line limit leave the line unchanged. Typing or moving the cursor starts a new completion search. In EDIT, Up/Down still navigate the document and Tab inserts four spaces.
