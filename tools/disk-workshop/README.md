# Disk Workshop

Open `tools/disk-workshop/index.html` from a local checkout in a modern browser. Keep `disk.js` and `desktop.js` beside it. GitHub displays the HTML source rather than running the tool. No server or upload is needed; the original disk is never overwritten directly.

1. Start the updated VM once to create/format JVFS, then stop it. Back up older images first: incompatible experimental formats are reformatted on startup.
2. Use **Open storage.img** for a complete 16 MiB disk. Click folder rows to enter; **Parent folder** returns.
3. Import files, create text/folders, or edit the selected file's Name, Format and Contents.
4. Click **Apply to disk** to commit editor changes to the browser's working copy.
5. **Download updated disk** saves `storage-updated.img`. Replace the intended disk file with that download while the VM is stopped, then restart.

Download file exports the selected file's last applied bytes. Downloads omit pending editor edits. Reloading/closing loses changes not downloaded.

Names allow twelve uppercase JVFS characters; imported names normalize to an eight-character base and three-character extension. Ordinary imports replace same-name files in the current folder. Multi-file imports stage a whole clone, so a failed batch leaves the working disk intact.

Text imports (`.txt/.jvs/.bat/.asm/.md`) decode UTF-8, require ASCII, normalize CRLF/CR to LF, and expand tabs to four spaces. Other files use hex in the editor. COM import preserves raw bytes; it does not assemble source or validate instructions. Build commented hex listings before importing their generated COM files.

PNG/JPEG/GIF/WebP/BMP imports become JI images up to 240x240 with aspect ratio preserved. Alpha composites against black. Native `.img` files with JI headers are recognized as images. **Paint's `.jvi` extension currently defaults to binary**: select the imported file, set Format to Palette image, then Apply to disk.

MIDI format 0/1, at most 64 tracks, and PPQN timing become eight-voice JM events. Tempo changes and running status are supported; SMPTE timing is not. Pitched channels use triangle waves, percussion uses noise, and a ninth overlapping note steals the oldest voice. Instruments and velocity dynamics are not reproduced.

The composer accepts `MIDI_NOTE FRAME_DURATION`: note 0-127 and duration 1-255 roughly 33 ms frames. Compositions automatically use JM v2 when more than 255 events are required. Compositions can exceed 64 KiB; disk space and browser memory determine the practical size.

JVFS provides one volume per disk, 1 KiB clusters, 24-bit file lengths and growing directories. Firmware bytes are preserved and whole-volume ownership is validated. Replacement files need temporary space for the complete new chain. The file capacity differs from execution RAM:

| Consumer | Limit |
| --- | --- |
| Fullscreen DOS EDIT / COM / JVS loading | 61,200 bytes |
| JCTRL DOS window / small graphics app workspace | 8,192 bytes |
| Larger document COM workspace | 91 pages / 23,296 bytes |
| TextEdit document | 4,096 bytes |
| Streaming files/music and Workshop editing | Available disk space |

**Add sample files** creates/replaces WELCOME.TXT, SMILE.IMG and THEME.JMT. **Add JCTRL bundle** installs JCTRL.COM, TEXTEDIT.COM and PAINT.COM together. Identical copies are accepted; a different existing file, directory conflict or full disk rejects the whole bundle without partial changes. Startup manages these three apps in drive 0's root; Workshop copies are manual.

Import `examples/jctrl/JCTRLSET.COM` to prepare guest installation media. Build it with `gradlew.bat desktopInstaller`; it is a self-contained fullscreen DOS setup wizard. See [installers](../../docs/wiki/Installers.md).

Workshop has file renaming through Name + Apply and folder creation/navigation, but no folder move/rename or drag-and-drop interface. DOS/JCTRL provides those operations.

Open `test.html` beside the tool to run browser checks for large files/directories, moves, cycles, transaction failures, image/MIDI conversion and bundle installation. The same `runDiskTests()` function can run in a JavaScript engine with typed arrays and `atob`, without a browser DOM.

See [the full guide and troubleshooting](../../docs/wiki/Tooling-and-Disk-Workshop.md) and [disk/media formats](../../docs/wiki/Disk-and-media-formats.md).
