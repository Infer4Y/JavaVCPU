# Screenshots

Current guest captures were regenerated on **October 22, 2026** from the working-tree JavaVCPU/JV-DOS bytecode on disposable disks. They show the actual 256 by 256 palette framebuffer, enlarged exactly 3x to **768 by 768** by the headless regression runner. No host window borders are included. The normal Swing display scales to 800 by 800.

## JV-DOS 2.0

| Startup | Command help |
| --- | --- |
| ![JV-DOS 2.0 banner and drive-aware prompt](images/dos-shell.png) | ![Current HELP command list](images/dos-help.png) |
| Click the display before typing; prompt starts at `0:\>`. | HELP covers everyday commands; programming syntax lives in the [JVS guide](JVS-programming.md). |

| Directory | Source editor |
| --- | --- |
| ![DIR listing bundled applications and sample files](images/dos-directory.png) | ![DOS EDIT with highlighted arithmetic JVS](images/dos-editor.png) |
| One JVFS volume per disk, with folders and 24-bit file sizes. | `EDIT MATH.JVS`; Esc saves and exits. |

| JVS source execution | COM execution |
| --- | --- |
| ![MATH printing 42 then zero after byte overflow](images/dos-jvs-run.png) | ![HELLO COM output and return to DOS](images/dos-com-run.png) |
| Direct `MATH` resolves MATH.JVS. | Direct `HELLO` resolves HELLO.COM. |

See [Getting started](Getting-started.md), [JV-DOS](JV-DOS.md) and [COM programming](COM-programming.md).

## Documents and media

| Document pager | Palette image |
| --- | --- |
| ![VIEW WELCOME.TXT](images/dos-document.png) | ![IMAGE SMILE.IMG](images/dos-image.png) |
| Arrows/Page Up/Page Down navigate; Esc closes. | Esc, Enter or Q closes the image viewer. |

![PLAY THEME.JMT active command](images/dos-music.png)

The PLAY command remains visible while music runs; there is no graphical transport or waveform. Esc stops and silences playback. See [media formats](Disk-and-media-formats.md).

## JCTRL desktop

| Files | Resident DOS window |
| --- | --- |
| ![JCTRL Files with bundled JCTRL, TextEdit and Paint](images/desktop-files.png) | ![Resident DOS directory listing in a movable window](images/jctrl-dos.png) |

| JVS source reader | Palette viewer |
| --- | --- |
| ![JCTRL source viewer for MATH.JVS](images/jctrl-source.png) | ![JCTRL viewing SMILE.IMG](images/jctrl-image.png) |

See [JCTRL desktop](Experimental-desktop.md) for window controls, file moves, DOS commands and application memory.

## Bundled apps and setup

| Paint | TextEdit |
| --- | --- |
| ![Paint palette and connected red brush strokes](images/jctrl-paint.png) | ![TextEdit highlighted JVS source and caret](images/textedit-jvs.png) |
| 128 by 96 canvas; F7 saves PAINT.JVI. | 4 KiB text/hex editor; F7 saves. |

![Self-contained JCTRLSET.COM setup wizard in DOS](images/jctrl-setup.png)

The setup capture comes from `installerRegression`, before answering its install prompt. See [JCTRL applications](JCTRL-applications.md) and [Installers](Installers.md).

## Optional applications

| WEB reader | Link chooser |
| --- | --- |
| ![WEB rendering a local test page](images/web-browser.png) | ![WEB numbered links from a local fixture](images/web-links.png) |

| GET-form address editing | Pong |
| --- | --- |
| ![Editable GET form query in WEB](images/web-form.png) | ![Pong running in the graphics application window](images/jctrl-pong.png) |

WEB captures use local regression-server fixtures, not live websites. See [application controls](JCTRL-applications.md) and [the gateway helper](Tooling-and-Disk-Workshop.md#optional-web-https-gateway).

## Earlier Disk Workshop captures

These are **archival screenshots**, not a capture of the current Workshop. The following browser captures are retained from **October 11, 2026**. They show the tool's editor/preview/composer layout, before the current JVFS folder and bundle-install updates. Current controls and limits are documented in [Tooling and Disk Workshop](Tooling-and-Disk-Workshop.md).

![Earlier Workshop directory and text editor](images/workshop-text.png)

![Earlier Workshop palette preview](images/workshop-image.png)

![Earlier Workshop tune composer](images/workshop-tune.png)

Guest capture regeneration and wiki-image publishing are documented in [tools/wiki](../../tools/wiki/README.md).
