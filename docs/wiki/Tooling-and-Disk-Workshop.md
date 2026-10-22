# Tooling and Disk Workshop

Use **JDK 26** for Java/Gradle commands. The project includes an offline disk editor, guest application generators, headless checks, an optional WEB transport gateway, and wiki maintenance tools.

## Disk Workshop

Workshop is an offline browser editor for **JVFS 1** disk images. Open [tools/disk-workshop/index.html](../../tools/disk-workshop/index.html) from a local checkout, with `disk.js` and `desktop.js` beside it. The GitHub file link displays source; download/clone the repository to run the tool. Nothing is uploaded, and the original disk file is never changed directly.

### Open, edit, and download

1. Start the updated VM once to create/format a JVFS disk, then stop it. Older experimental disk formats are reformatted on startup; back them up first.
2. Click **Open storage.img** and select the 16 MiB disk. Other mounted-drive images can be edited the same way.
3. Click a directory row to enter a folder; **Parent folder** returns. The folder path and used/free cluster counts show the current location and volume capacity.
4. Select a file to edit its Name, Format, and Contents, or choose **New text file**, **New folder**, **Import files**, or a sample/bundle action.
5. Click **Apply to disk** after editing. This changes only the browser's working copy.
6. Click **Download updated disk**. It downloads `storage-updated.img`; rename/replace the intended disk file while the VM is stopped, then restart.

**Download file** exports the selected file's last applied bytes, not unsaved text in the Contents editor. **Download updated disk** likewise includes only applied/imported changes. Closing/reloading the page loses changes that have not been downloaded.

| Control | Current behavior |
| --- | --- |
| Name + Apply to disk | Create a named file, or rename the selected file; an existing different destination name is refused |
| Format | Choose Text / JVS source, Binary / COM, Palette image, or MIDI note stream |
| Contents | Plain text for type 1; two-digit hex bytes separated by whitespace for other types |
| Import files | Import one or more files into the current folder; replace matching files without a separate confirmation |
| New folder | Create a child folder using the name prompt |
| Delete file | Remove the selected file from the working copy |
| Add sample files | Create/replace WELCOME.TXT, SMILE.IMG and THEME.JMT |
| Add JCTRL bundle | Install JCTRL.COM, TEXTEDIT.COM and PAINT.COM together in the current folder |
| Create a short tune | Expand the note composer; Create THEME.JMT applies its result immediately |
| Download file | Export the selected native file; images export JI bytes, not PNG |

There is no folder move/rename or drag-and-drop UI in Workshop. Use DOS/JCTRL for those operations; the JavaScript disk API has additional operations exercised by its tests.

### Names and import formats

JVFS names have up to twelve uppercase letters, digits, dots, underscores or hyphens and cannot start with a dot. The same name may appear in different folders. Imported host names become an eight-character base plus a three-character extension; unsupported base characters become underscores. For manually created/renamed files, the Name field allows any valid twelve-character JVFS name.

| Import | Classification / conversion |
| --- | --- |
| `.txt`, `.jvs`, `.bat`, `.asm`, `.md` | UTF-8 decoded, then restricted to ASCII text; CRLF/CR becomes LF and tabs become four spaces |
| `.com` and unrecognized extensions | Raw binary; no assembly or executable validation during import |
| `.img` beginning with `JI` | Native Palette image; dimensions, length and palette bytes are validated |
| `.jmt` | Native MIDI note stream; JM header/events are validated |
| `.mid`, `.midi` | Converted from Standard MIDI to JM |
| `.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`, `.bmp` | Browser-decoded image converted to JI; browser format support applies |
| `.jvi` from Paint | Currently imported as binary; set Format to **Palette image** and Apply to disk |

Imported text files are not automatically valid JVS programs. Import generated COM bytes rather than a `.hex` listing; [examples/build.ps1](../../examples/build.ps1) converts the commented hex examples first. The guest COM loader validates instructions when executing them.

Images retain their aspect ratio and are reduced to fit **240 by 240**; smaller images are not enlarged during import. Transparency is composited against black, and colors are quantized to the 128-color `RRGGGBB` palette. The editor previews palette images after selection, format changes, or applying edits.

MIDI conversion accepts format 0/1, up to 64 tracks, and PPQN timing. SMPTE timing is unsupported. Tempo changes and running status are recognized. The converter uses eight synthesized voices: triangle waves for pitched channels and noise for percussion channel 10. More than eight overlapping notes steal the oldest active voice; original instruments and velocity dynamics are not reproduced.

The composer accepts one `MIDI_NOTE FRAME_DURATION` pair per line: note **0-127**, duration **1-255**. A frame is roughly 33 ms. Each line produces note-on and note-off events. JM version 1 stores up to 255 events; larger compositions automatically use version 2. Compositions can exceed 64 KiB; disk space and browser memory determine the practical size.

### Capacity, transactions, and the desktop bundle

Each disk has one JVFS volume with 1 KiB clusters, 24-bit file lengths, and growing directories. The browser validates chains, directory ownership and ancestry and preserves the reserved firmware area. Data shares the volume rather than being split into filesystem banks.

Ordinary file replacements stage their complete new chain before freeing the old one, so they need temporary free space for the replacement. Import batches and sample/bundle actions use a clone: failure leaves the previous working disk intact. Same-name files are replaced during ordinary imports, while the bundle action accepts only missing or byte-identical components and rejects conflicting contents or directories.

Normal VM startup manages JCTRL, TextEdit and Paint on drive 0. Workshop installs manual copies and does not establish automatic-update ownership. WEB and Pong are separate imports. [JCTRLSET.COM](Installers.md) is a single-file fullscreen DOS installer for the same three-program bundle.

| Consumer | RAM / file limit |
| --- | --- |
| Workshop working files and JM streams | Available JVFS space and browser memory; not the old 61,200-byte bank limit |
| Fullscreen DOS EDIT / COM / JVS loading | 61,200 bytes |
| JCTRL resident DOS window and small graphics COMs | 8,192-byte workspace |
| Larger JCTRL document COMs | Up to 91 pages / 23,296 bytes |
| TextEdit document contents | 4,096 bytes |
| Paint image | Fixed 128 by 96 JI / 12,292 bytes |
| JCTRLSET.COM | At most 61,184 bytes, reserving its mailbox page |

TYPE/VIEW, COPY/APPEND, INSTALL/MV and music stream through resident filesystem services; program/editor RAM limits do not cap those transfers.

### Troubleshooting and checks

| Symptom | Action |
| --- | --- |
| Select a 16 MiB JavaVCPU disk | Use Open storage.img for a complete disk, not Import files or a JI palette image |
| This disk needs JVFS 1 | Back up the old image, then let the updated VM format it before editing |
| Disk full / file unchanged | Free enough clusters for the entire replacement and any directory growth |
| Bundle file already exists with different contents | Rename/move the conflicting copy with DOS/JCTRL, or use the setup wizard's explicit replacement mode |
| DOS text supports ASCII | Remove non-ASCII characters; text import accepts UTF-8 encoding but the guest text format is ASCII |
| Binary contents use two-digit hex bytes | Use byte pairs such as `02 2A 00 04`, or select Text / JVS for source |
| A painting imports as BIN | Select it, change Format to Palette image, and Apply to disk |
| Downloaded file/disk misses edits | Apply editor changes before downloading |

Open `tools/disk-workshop/test.html` beside the scripts for the browser test runner. Its underlying `runDiskTests()` checks large files/directories, navigation, moves/cycles, corruption, transaction failures, images, MIDI and bundle installation. The tests use disposable in-memory images. See [the Workshop README](../../tools/disk-workshop/README.md).

[The gallery](Screenshots.md#earlier-disk-workshop-captures) retains explicitly dated older Workshop screenshots. The controls and limits above describe the current source; those older captures do not show the new bundle/folder behavior.

## Build guest assets

Run from the repository root:

| Gradle task | Generated asset |
| --- | --- |
| `desktopImage` | `examples/dos/JCTRL.COM` and Workshop's `desktop.js` bundle |
| `textEditExample` | `examples/jctrl/TEXTEDIT.COM` |
| `paintExample` | `examples/jctrl/PAINT.COM` |
| `desktopInstaller` | Self-contained `examples/jctrl/JCTRLSET.COM` |
| `browserExample` | `examples/jctrl/WEB.COM` |
| `jctrlExamples` | `examples/jctrl/PONG.COM` |
| `javadoc` | `build/docs/javadoc/index.html` |

```powershell
.\gradlew.bat desktopImage textEditExample paintExample desktopInstaller browserExample jctrlExamples
```

These generators assemble ordinary JavaVCPU instructions. Rebuild the desktop together with apps that require newer shared services. Stop the VM before replacing manual disk copies. The normal installer generates managed bundle content from the Java assemblers, independently of manually imported copies.

`powershell -ExecutionPolicy Bypass -File examples/build.ps1` converts commented hex examples into raw binary files. It is not a general mnemonic assembler.

## Run checks

```powershell
.\gradlew.bat build
```

For focused checks:

| Task | Coverage |
| --- | --- |
| `storageRegression` | Real resident JVFS bytecode, large files/directories, RAM, display, desktop, ownership and Pong |
| `desktopServicesRegression` | Independent COM clients, shared application ABI and rendering cost |
| `paintRegression`, `paintInstallRegression` | Paint input/drawing, validation, file failure, compositing and managed installation |
| `textEditRegression`, `textEditPerformanceRegression`, `editorInstallRegression` | Text/hex editing, fidelity, interaction cost and managed installation |
| `installerRegression` | INSTALL/MV, external-media setup, collisions, replacement, full disks and resume |
| `browserRegression` | WEB rendering, streaming, persistent cache, cancellation and bounds |
| `browserNavigationRegression` | Redirects, URLs, links, Back, chunked framing and gateway requests |
| `browserFormsRegression` | GET forms, query editing, select defaults and later link groups |

The main-based regression compiler does not require external test libraries. Disposable disk images and captures go under `build/`. Network regressions use local fixtures; opt-in live-site checks are separate.

## Optional WEB HTTPS gateway

```powershell
python tools/web-gateway/web_gateway.py
```

Leave it on **127.0.0.1:8080** and press G in WEB's page/error view. The Python standard-library helper verifies TLS certificates and hostnames, decompresses gzip/deflate, and returns redirects for the guest to resolve. It does not render HTML or provide JavaScript, authentication or cookies. DOS `BROWSE` is a separate basic HTTP reader.

`python tools/web-gateway/test_web_gateway.py` runs transport checks. Add `--guest-classes build/classes/storageRegression` to include actual WEB guest execution against a local HTTPS fixture; OpenSSL is needed to create its temporary certificates. See [gateway options and tests](../../tools/web-gateway/README.md).

## Refresh screenshots

Use [the capture workflow](../../tools/wiki/README.md) to regenerate current guest pixels on disposable disks:

```powershell
powershell -ExecutionPolicy Bypass -File tools/wiki/capture.ps1
```

It runs the guest application regressions, captures shell/desktop views through `WikiScreenshots.java`, and copies reviewed output names into `docs/wiki/images/`. Captures are 768 by 768, an exact 3x enlargement of the 256 by 256 palette framebuffer. They are headless guest-rendered pixels, not Swing-window screenshots. The gallery labels older Workshop/browser UI captures separately.

## Wiki checkout and publishing

`docs/wiki/` is the editable source; **`wiki/` is a Git submodule** with origin `https://github.com/Infer4Y/JavaVCPU.wiki.git`. Initialize it after cloning:

```powershell
git submodule update --init wiki
git -C wiki checkout master
git -C wiki pull --ff-only
python tools/wiki/check.py
python tools/wiki/sync.py
python tools/wiki/sync.py --check
git -C wiki diff --check
git -C wiki diff --stat
```

The checker validates source links/anchors, PNGs, all opcode encodings/lengths, current firmware/application ABI references, and Workshop bundle bytes against generated COM files. Sync copies pages and images without deleting remote-only files. It rewrites repository-relative source links and image URLs for GitHub wiki rendering, and strips `.md` from wiki page links. Wiki images are committed into its own `images/` directory and served through `raw.githubusercontent.com/wiki/Infer4Y/JavaVCPU/`.

After reviewing the result, publish the wiki commit, then record its new submodule pointer in the main repository:

```powershell
git -C wiki add --all
git -C wiki commit -m "Update JavaVCPU, JV-DOS, JCTRL and tooling guides"
git -C wiki push origin master
git add .gitmodules wiki docs/wiki tools/wiki
```

Commit those main-repository paths with your normal changes. The wiki and main repository have separate histories: pushing the main repository alone does not publish wiki edits. Since these pages describe the current working tree, source links for newly added implementation files become available after those files are also committed and pushed to the main repository.

In IntelliJ, commit and push the **wiki** repository separately, then commit and push the main repository with the new submodule pointer. Selecting only the main repository in Push does not upload commits from `wiki/`.
