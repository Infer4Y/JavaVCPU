# JV-DOS 1.1 Disk Workshop

Open `index.html` locally. Select your project's `storage.img`, import or edit files, and download `storage-updated.img`. Stop the VM, replace its `storage.img` with the downloaded image, and restart. The tool never contacts a server or changes your original file automatically.

DOS commands:

- `VIEW NAME.TXT` / `MORE NAME.TXT`: read-only document pager. Up/Down, Page Up/Page Down, Space/Enter and Home navigate; Esc closes.
- `IMAGE NAME.IMG` / `SHOW NAME.IMG`: palette image display. Esc, Enter or Q closes.
- `PLAY NAME.JMT`: four synthesized voices using MIDI note numbers. Esc stops and silences every voice.
- `RUN`: opens imported images/music automatically, executes JVS text source, or loads COM bytecode.

The existing directory holds 16 files of at most 240 bytes each, with names up to 12 characters. Oversized imports are rejected. Text is ASCII; CRLF is normalized to LF, and tabs become four spaces. The VM displays lowercase imported text as well as punctuation; its keyboard remains uppercase DOS-style with shifted punctuation and keypad support.

## On-disk formats

The disk is 16 MiB. The tool preserves the firmware/kernel and modifies only bank 2's directory slots and initialization marker. Directory slot `n` begins at `0x20000 + n*256`: byte 0 is the length, bytes 1–12 are the NUL-terminated name, byte 13 is type (0 binary, 1 text, 2 image, 3 music), bytes 14–15 are reserved, and bytes 16–255 are content. Bank 2 slot 255 byte 0 is the initialization marker `0x4a`.

Images have `JI`, width, height, then one `RRGGGBB` palette byte per pixel. Width and height are 1–15; all colors are 0–127. Imported browser images are resized proportionally and quantized, including alpha composited over black. DOS draws each pixel as an 8×8 block.

Tunes have `JM`, version 1, event count, then four-byte events: frame duration, voice, MIDI note, control. Duration 0 changes voices without waiting; 1–255 waits that many 33 ms frames. Voice is 0–3, note is 0–127, and control is 0 (off) or 128–131 (square, triangle, saw, noise). Up to 59 events fit.

Standard MIDI format 0/1 files with PPQN timing are converted to this note stream. Running status, tempo changes, note-on/off and four-note polyphony are supported. Instrument/program changes and controller effects are not retained; percussion becomes noise. Files with more than four simultaneous notes or more than 59 converted events must be shortened before import. You can also compose a tune directly in the page using MIDI note numbers and frame durations.

The keyboard decoder uses existing make/break events and keyboard-state ports. Quotes and backtick have Java key codes above 127, whose make/release bytes collide in this device protocol; those keys are checked against held-key state. Hold them long enough for the VM to poll (about one frame). No VM/input opcodes were changed.
