# JV-DOS 1.1

JV-DOS runs as guest bytecode. It provides a shell, persistent named files, an editor, JVS interpretation, COM loading and small media viewers. Commands and filenames should be uppercase. Click the VM window before typing.

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

Files occupy 16 slots, have names of up to 12 characters, and contain at most 240 bytes. There are no folders or large-file allocation chains. The CLI and source reader have a 100-character line limit; use Workshop to import longer documents or binary files. `COPY`/`REN` report `DESTINATION EXISTS` if the target name is taken.

## Editor and viewers

`EDIT DEMO.JVS` opens the guest source editor. Arrow keys move; Home/End navigate the line; Backspace and Delete remove characters; Enter inserts a newline; Tab indents. Esc **saves and leaves**. It is not a cancel operation. The editor highlights source text.

The document pager uses Up/Down, Page Up/Page Down, Space/Enter and Home; Esc closes it. Image viewing closes with Esc, Enter or Q. Esc stops music and silences its voices. A running JVS program checks for Esc between statements.

`RUN` dispatches image/music from their file type, interprets type-1 text, and otherwise validates and loads raw bytecode. It also heuristically treats binary content starting with printable bytes 32-90 as source. The supplied COM starts with opcode 2 to avoid that ambiguity. Filename extensions alone do not define the execution format.

See [JVS programming](JVS-programming), [COM programming](COM-programming), and [Tooling and Disk Workshop](Tooling-and-Disk-Workshop).
