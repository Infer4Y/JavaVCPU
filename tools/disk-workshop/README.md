# Disk Workshop

Open `tools/disk-workshop/index.html` locally in a modern browser. Keep `disk.js` and `desktop.js` beside it. No server or uploads are needed; edits stay in the browser until a disk download.

1. Run the updated VM once to create a JVFS disk, then stop it. Older experimental disk formats are reformatted on startup.
2. Open the 16 MiB `storage.img` in Workshop.
3. Browse folders, import files, or create text, images and tunes. Apply editor changes to the working copy.
4. Download the updated disk and replace `storage.img` while the VM is stopped, then restart.

JVFS uses one volume per disk with 1 KiB clusters, 24-bit file lengths and growing directories. Files are no longer split between banks. Workshop preserves reserved firmware bytes, validates chains and directory ownership, and stages replacements before freeing the original data. Failed import batches leave the working disk unchanged. Replacement needs temporary free space for the entire new file.

Names have up to 12 uppercase ASCII characters; imports normalize names to an eight-character base and three-character extension. The same name may appear in different folders. Duplicate imports can replace an existing file. Click a folder to enter it and Parent folder to return.

Text/JVS supports ASCII and LF newlines; tabs become four spaces. Binary files use raw bytes. PNG/JPEG and other browser-supported images become JI palette images up to 240x240 pixels. Standard MIDI format 0/1 with PPQN timing becomes eight-voice JM note events, preserving tempo changes and running status. Extended tunes can exceed 64 KiB; available disk space sets the limit. The composer accepts `MIDI_NOTE FRAME_DURATION` per line.

The disk file limit differs from available program RAM: EDIT and fullscreen COM/JVS loading remain bounded to 61,200 bytes; windowed programs use 8 KiB. TYPE/VIEW, COPY/APPEND and music use streaming filesystem services.

Add sample files creates WELCOME.TXT, SMILE.IMG and THEME.JMT. **Add JCTRL bundle** installs JCTRL.COM, TEXTEDIT.COM and PAINT.COM together in the current folder. Identical copies are accepted; a different existing file or a full disk rejects the whole operation without partial changes. Normal VM startup manages these three applications in the system root. Manual copies are not automatically updated.

Import `examples/jctrl/JCTRLSET.COM` as Binary / COM to create installation media.
It is a self-contained DOS setup wizard for the same three-program bundle.
Rebuild it with `gradlew.bat desktopInstaller`; see
[portable installs and setup wizards](../../docs/wiki/Installers.md).

Open `test.html` beside the tool to run browser-only checks for large files, growing directories, moves, cycles, full-disk preservation and media converters. The disk layout and guest ABI are documented in `docs/wiki/Disk-and-media-formats.md`.
