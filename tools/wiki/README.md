# Wiki maintenance

Edit pages in `docs/wiki/`. The `wiki/` directory is a Git submodule with origin
`https://github.com/Infer4Y/JavaVCPU.wiki.git`. It carries its own commits and push.

## Guest screenshots

With JDK 26 selected, run from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File tools/wiki/capture.ps1
```

This runs existing main-based guest checks for Paint, TextEdit, setup, storage,
Pong and WEB, then captures shell/desktop views through `WikiScreenshots.java`.
All disk images are disposable files under `build/`; `storage.img` is untouched.
The helper drives `DosStorageRegression` through its existing private methods,
so guest input, instruction execution and palette drawing use the same runner
as the checks. It does not paint mock application screens.

Twenty named PNGs are copied into `docs/wiki/images/`. Each is an exact 3x
enlargement of the 256 by 256 guest framebuffer (768 by 768). Inspect them and
update the gallery's capture date before publishing. Old Workshop browser
captures remain separately labeled; this script does not regenerate those.

Use `-SkipRegressions` only after those checks have just completed successfully
and produced fresh application PNGs. To run the helper alone:

```powershell
.\gradlew.bat compileStorageRegression
java --class-path "$((Resolve-Path build/classes/storageRegression).Path)" tools/wiki/WikiScreenshots.java
```

On macOS/Linux, run the same Gradle regression tasks using `./gradlew`, then use
an absolute classpath with `java --class-path ... tools/wiki/WikiScreenshots.java`
and copy the named PNGs listed in `capture.ps1`.

## Sync and publish

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

`check.py` checks all source-page links and heading anchors, PNG headers/dimensions,
ISA encodings and lengths against OPCode.java, current ABI/firmware references,
and Workshop bundle bytes against the generated COM files. It needs only Python's
standard library. Run it before syncing. Workshop filesystem/converter checks
are separate; open `tools/disk-workshop/test.html` to run them in a browser.

`sync.py` copies all source pages/assets, preserves remote-only files, converts
relative image URLs to the wiki's own raw image URLs, resolves `../../` source
links against the main repository's `master` branch, and removes `.md` from wiki
page links. `--check` is read-only and exits nonzero when an expected file differs.

After review:

```powershell
git -C wiki add --all
git -C wiki commit -m "Update JavaVCPU, JV-DOS, JCTRL and tooling guides"
git -C wiki push origin master
git add .gitmodules wiki docs/wiki tools/wiki
```

Include the staged submodule pointer and documentation/tools in the main
repository's next commit. Push the wiki commit before publishing a main commit
that refers to it. Newly added code linked by the wiki must also be committed
and pushed in the main repository for those source links to resolve online.

In IntelliJ, commit and push the **wiki** repository separately in the Commit/Push
dialogs. Then commit/push the main repository with the updated submodule pointer.
A main-repository push carries that pointer; it does not upload wiki commits.
