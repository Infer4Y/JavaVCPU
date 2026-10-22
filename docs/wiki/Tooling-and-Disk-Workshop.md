# Tooling and Disk Workshop

Use **JDK 26** for Java/Gradle commands. The project includes an offline disk editor, guest application generators, headless checks, an optional WEB transport gateway, and wiki maintenance tools.

## Disk Workshop

Open [tools/disk-workshop/index.html](../../tools/disk-workshop/index.html) locally in a modern browser. Keep `disk.js` and `desktop.js` beside it. No server or upload is needed; edits stay in the browser until you download a disk.

1. Run the updated VM once to create a JVFS disk, then stop it. Older experimental formats are reformatted on startup.
2. Open the 16 MiB `storage.img` in Workshop.
3. Browse folders, import files, or create text, images and tunes. **Apply to disk** commits editor changes to the browser's working copy.
4. **Download updated disk**, replace `storage.img` while the VM is stopped, then restart.

JVFS uses one volume per disk with 1 KiB clusters, 24-bit file lengths and growing directories. Workshop preserves firmware bytes, validates chains and directory ownership, and stages replacements before freeing original data. Failed import batches leave the working disk unchanged. Replacing a file needs temporary free space for the entire new file.

Names have up to twelve uppercase ASCII characters; imported names normalize to an eight-character base and three-character extension. Names may repeat in different folders. Duplicate imports can replace a file. Click a folder to enter; **Parent folder** returns.

| Input / feature | Result |
| --- | --- |
| Text / JVS | ASCII with LF newlines; imported tabs become four spaces |
| Binary / COM | Raw guest bytes; editor shows two-digit hex |
| PNG/JPEG and other supported images | JI palette image, resized to at most 240 by 240 |
| MIDI format 0/1, PPQN | Eight-voice JM events; tempo changes/running status supported |
| Short-tune composer | One `MIDI_NOTE FRAME_DURATION` pair per line |
| Add sample files | `WELCOME.TXT`, `SMILE.IMG`, `THEME.JMT` |
| **Add JCTRL bundle** | `JCTRL.COM`, `TEXTEDIT.COM`, `PAINT.COM` together in the current folder |
| Download file | Export the selected file separately |
| Download updated disk | Export the complete edited image |

The bundle operation accepts identical installed files; a conflicting different file or full disk rejects the entire operation without partial installation. Normal startup manages these apps in drive 0's root. Workshop copies are manual and do not acquire automatic-update ownership. [JCTRLSET.COM](Installers) offers an alternative guest setup wizard.

The disk capacity differs from program RAM: fullscreen EDIT/COM/JVS loading is bounded to 61,200 bytes; JCTRL's DOS window and small graphics apps use 8 KiB, while larger document apps can use up to 91 pages. TextEdit's document limit is 4 KiB. TYPE/VIEW, COPY/APPEND and music stream through resident filesystem services; music may exceed 64 KiB.

The following Workshop capture is retained from **October 11, 2026**. It illustrates the layout; current Workshop uses JVFS folders and the **Add JCTRL bundle** action.

![Earlier Workshop layout with directory and text editor](images/workshop-text.png)

Open `tools/disk-workshop/test.html` beside the tool for browser checks covering large files/directories, moves, cycles, transactional failures, media conversion and bundle installation. See [the Workshop README](../../tools/disk-workshop/README.md).

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
python tools/wiki/sync.py
python tools/wiki/sync.py --check
git -C wiki diff --check
git -C wiki diff --stat
```

Sync copies pages and images without deleting remote-only files. It rewrites repository-relative source links and image URLs for GitHub wiki rendering, and strips `.md` from wiki page links. Wiki images are committed into its own `images/` directory and served through `raw.githubusercontent.com/wiki/Infer4Y/JavaVCPU/`.

After reviewing the result, publish the wiki commit, then record its new submodule pointer in the main repository:

```powershell
git -C wiki add --all
git -C wiki commit -m "Update JavaVCPU, JV-DOS, JCTRL and tooling guides"
git -C wiki push origin master
git add .gitmodules wiki docs/wiki tools/wiki
```

Commit those main-repository paths with your normal changes. The wiki and main repository have separate histories: pushing the main repository alone does not publish wiki edits. Since these pages describe the current working tree, source links for newly added implementation files become available after those files are also committed and pushed to the main repository.
