# JV-DOS 1.3 Disk Workshop

Open `index.html` locally. Select your project's `storage.img`, import or edit files, and download `storage-updated.img`. Stop the VM, replace its `storage.img` with the downloaded image, and restart. The tool never contacts a server or changes your original file automatically.

![Disk Workshop with a sample directory and JVS source editor](../../docs/wiki/images/workshop-text.png)

The [screenshot gallery](../../docs/wiki/Screenshots.md#disk-workshop) also shows the palette image preview and expanded tune composer.

DOS commands:

- `VIEW NAME.TXT` / `MORE NAME.TXT`: read-only document pager. Up/Down, Page Up/Page Down, Space/Enter and Home navigate; Esc closes.
- `IMAGE NAME.IMG` / `SHOW NAME.IMG`: palette image display. Esc, Enter or Q closes.
- `PLAY NAME.JMT`: eight synthesized voices using MIDI note numbers. Esc stops and silences every voice.
- `RUN`: opens imported images/music automatically, executes JVS text source, or loads COM bytecode.

Each selected bank holds 255 shared blocks with linked files up to 61,200 bytes, with names up to 12 characters. Oversized imports are rejected. Text is ASCII; CRLF is normalized to LF, and tabs become four spaces. The VM displays lowercase imported text as well as punctuation; its keyboard remains uppercase DOS-style with shifted punctuation and keypad support.

## On-disk formats

The disk is 16 MiB. The tool preserves the firmware/kernel and modifies only the selected bank's directory slots and initialization marker. Directory slot `n` begins at `(bank << 16) + n*256`: byte 0 is the length low byte, bytes 1–12 are the NUL-terminated name, byte 13 is type (0 binary, 1 text, 2 image, 3 music), byte 14 is the length high byte and byte 15 links the next block, and bytes 16–255 are content. Bank 2 slot 255 byte 0 is the initialization marker `0x4a`.

Images have `JI`, width, height, then one `RRGGGBB` palette byte per pixel. Width and height are 1-240; all colors are 0-127. The largest image is 57,604 bytes. Imported browser images are resized proportionally to fit 240 by 240 pixels and quantized, including alpha composited over black. DOS reads pixels across linked blocks. Images with both dimensions at most 15 retain 8 by 8 display blocks; larger images render one screen pixel per image pixel below the title.

Tunes have `JM`, version 1, event count, then four-byte events: frame duration, voice, MIDI note, control. Duration 0 changes voices without waiting; 1–255 waits that many 33 ms frames. Voice is 0–3, note is 0–127, and control is 0 (off) or 128–131 (square, triangle, saw, noise). Version 1 stores up to 255 events in a 1,024-byte file. Version 2 uses header bytes `JM, 2, 0` and derives its event count from the file length, supporting up to 15,299 events in 61,200 bytes. The importer writes version 2 for longer tunes; playback requires the updated DOS image.

Standard MIDI format 0/1 files with PPQN timing are converted to this note stream. Running status, tempo changes, note-on/off and eight-note polyphony are supported. Instrument/program changes and controller effects are not retained; percussion becomes noise. When more than eight notes overlap, each new note replaces the oldest sounding note. Files with more than 15,299 converted events must be shortened before import. You can also compose a tune directly in the page using MIDI note numbers and frame durations.

The keyboard decoder uses existing make/break events and keyboard-state ports. Quotes and backtick have Java key codes above 127, whose make/release bytes collide in this device protocol; those keys are checked against held-key state. Hold them long enough for the VM to poll (about one frame). No VM/input opcodes were changed.

### JV-DOS 1.2 banks

Use the Disk bank selector (2-255) to edit another bank. Each bank contains 255 slots; slot 255 is reserved. In DOS, `BANK +` and `BANK -` select the corresponding bank, and `BANK` reports the current bank. Existing images with system revisions 4, 5, 6, 7, 8, 9 and 10 can be opened; the VM updates their system image to revision 10 while preserving user files. Files use 240-byte content chunks; a continuation has byte 1 set to 255 and byte 15 as its next slot. Large-file writes require JV-DOS 1.3. Capacity is checked before any file changes are committed. Cloning/importing preserves the selected bank and all other disk bytes.
