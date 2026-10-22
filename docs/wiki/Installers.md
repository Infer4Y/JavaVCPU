# Installing applications

The JCTRL bundle contains **JCTRL.COM, TEXTEDIT.COM and PAINT.COM**. VM startup
installs managed copies on internal drive 0. Disk Workshop's **Add JCTRL bundle**
installs the same three programs in its current folder, including Paint. Workshop
works on a clone: a conflict or full disk leaves the working disk unchanged.
Identical copies are accepted; different existing files must be renamed or moved
first. Download the edited disk while the VM is stopped.

## Portable programs from external media

Mount a formatted JVFS image as an external disk at startup:

```powershell
.\gradlew.bat run --args="--disk=1=external.img"
```

In DOS, select the drive and folder containing your program:

```text
DRIVE 1
CD APPS
INSTALL DEMO.COM
```

`INSTALL` copies a binary program to **drive 0's root** and keeps its source.
For a move instead, use `MV DEMO.COM 0:`. The source is deleted only after the
destination has been completely written and committed. A destination conflict
refuses the operation; it never silently replaces an existing program. Failure
aborts the staged destination and keeps the source. The current drive and folder
are restored, so the next command still operates in the external APPS folder.

These commands stream through resident JVFS block I/O rather than loading the
file into RAM. Binary resources can be transferred too; installing a COM does not
execute it. `RUN` still validates and enforces its normal executable-size limit.
`MV` currently accepts the explicit destination `0:`; ordinary `MOVE` retains its
existing same-drive folder/rename behavior. Text entries and directories are
refused by INSTALL/MV. Restart the updated VM once to load kernel revision 24.

## JCTRL setup wizard

Build the distributable installer with:

```powershell
.\gradlew.bat desktopInstaller desktopImage
```

Import `examples/jctrl/JCTRLSET.COM` as Binary / COM into an external JVFS disk
using Workshop. The single COM contains the setup program and all three payloads;
no sidecar files or Internet connection are needed.

Run it from **fullscreen DOS**, either directly on external media or after
`INSTALL JCTRLSET.COM` / `MV JCTRLSET.COM 0:`:

```text
DRIVE 1
RUN JCTRLSET.COM
```

| Choice | Result |
| --- | --- |
| Y | Install missing bundle programs; keep existing programs |
| R | Install missing programs and explicitly replace existing files |
| Esc | Cancel before writing anything |

The destination is drive 0's root. A same-name directory stops setup. Each file
uses a JVFS replacement transaction; a failed write preserves that file's old
contents. Earlier completed files remain installed if a later file fails. Free
space and rerun Y to finish missing components, or R to retry an upgrade. The
source installer and external disk stay untouched. The wizard restores the
source drive and folder when it returns to DOS. It does not modify firmware or
boot ownership records; distributed/manual copies retain the normal conservative
ownership policy. Use fullscreen DOS rather than JCTRL's bounded app/DOS window
for the large bundle installer.

![JCTRL setup wizard before installation](images/jctrl-setup.png)

## Creating another installer

`InstallerImage` is a reusable offline generator. Supply the output COM followed
by `NAME.COM=path` arguments; without arguments it packages the JCTRL bundle:

```powershell
.\gradlew.bat classes
java -cp build/classes/java/main xyz.ignite4inferneo.jvdos.InstallerImage MYSETUP.COM DEMO.COM=examples/dos/HI.COM
```

Names are unique JVFS names of at most 12 characters. Components must contain
valid JavaVCPU COM instructions; raw images/text need a future data-package
format. Payload programs are copied, never run by setup. The generated installer
must fit **61,184 bytes**, reserving the last loader page for its filesystem
mailbox and preserving DOS history. The current desktop installer is 60,672
bytes. `installerRegression` exercises actual guest commands/setup, cancellation,
replacement, external-media preservation, full-disk recovery, partial final blocks
and transfers larger than 64 KiB.
