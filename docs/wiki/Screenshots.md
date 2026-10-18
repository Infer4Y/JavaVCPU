# Screenshots

These captures show the current JavaVCPU display and Disk Workshop using a separate sample disk. VM images capture the actual Swing display content at 800 × 800 pixels, without desktop borders. Workshop images show the local browser interface. Captured October 11, 2026.

## Boot and command line

| BIOS POST | JV-DOS shell |
| --- | --- |
| ![BIOS power-on self-test reporting POST OK](images/bios-post.png) | ![JV-DOS 1.1 startup banner and command prompt](images/dos-shell.png) |
| Normal boot briefly displays POST before entering DOS. | Click the display and type a command at `A>`. |

| Shell help | Programming help |
| --- | --- |
| ![HELP showing shell commands](images/dos-help.png) | ![CODEHELP showing JVS syntax and opcodes](images/dos-codehelp.png) |
| `HELP` lists everyday commands; these captures show an earlier version. | Historical `CODEHELP` screen; revision 22 moves programming reference to the [JVS guide](JVS-programming.md). |

| File directory | Source editor |
| --- | --- |
| ![DIR showing sample filenames and byte lengths](images/dos-directory.png) | ![Highlighted arithmetic source in the JV-DOS editor](images/dos-editor.png) |
| `DIR` lists files on the virtual disk. | `EDIT MATH.JVS`; Esc saves and returns to DOS. |

See [Getting started](Getting-started), [JV-DOS](JV-DOS) and [JVS programming](JVS-programming).

## Documents and media

| Document pager | Palette image viewer |
| --- | --- |
| ![Document pager displaying WELCOME.TXT](images/dos-document.png) | ![Palette image viewer displaying a yellow smile](images/dos-image.png) |
| `VIEW WELCOME.TXT`; arrows navigate, Esc closes. | `IMAGE SMILE.IMG`; Esc, Enter or Q closes. |

`PLAY THEME.JMT` displays playback status while the synthesized voices play. Esc stops playback. This is a text status view; there is no graphical transport or waveform.

![Music playback status with Esc to stop](images/dos-music.png)

See [JV-DOS](JV-DOS) and [Disk and media formats](Disk-and-media-formats).

## Running programs

| JVS source execution | COM bytecode execution |
| --- | --- |
| ![RUN MATH.JVS producing 42 and returning to the prompt](images/dos-jvs-run.png) | ![RUN HELLO.COM printing its greeting](images/dos-com-run.png) |
| The captured arithmetic source multiplies 6 by 7. | The seeded guest executable prints a greeting and returns to DOS. |

| Built-in display greeting | Snowfall animation |
| --- | --- |
| ![DISPLAY_HELLO_CPU drawing its greeting](images/display-hello-cpu.png) | ![SNOWFALL with a falling pixel and settled snow](images/snowfall.png) |
| `Programs.Program.DISPLAY_HELLO_CPU` | `Programs.Program.SNOWFALL`; a still frame of the pixel animation. |

The built-in graphics demos require selecting their program in the loader; they are not DOS commands. Console-only arithmetic, memory and peripheral demos have no separate display view.

See [Example programs](Example-programs) and [COM programming](COM-programming).

## Disk Workshop

Open `tools/disk-workshop/index.html` locally and select a disk. The directory and file editor share one page.

### Directory and text editor

![Disk Workshop with sample directory and MATH.JVS source selected](images/workshop-text.png)

### Palette image preview

![Disk Workshop with SMILE.IMG hex data and palette preview](images/workshop-image.png)

### Tune composer

![Disk Workshop with the short-tune composer expanded](images/workshop-tune.png)

See [Tooling and Disk Workshop](Tooling-and-Disk-Workshop) for importing, applying edits and downloading the updated disk.
