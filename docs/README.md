# Documentation layout

`wiki/` contains GitHub Wiki-compatible Markdown pages. Begin with [Home](wiki/Home.md). `_Sidebar.md` supplies wiki navigation; page links omit `.md` to match GitHub Wiki routes. When reading directly on disk, open the corresponding `.md` file in that folder.

```text
docs/
  README.md
  wiki/
    Home.md
    _Sidebar.md
    Getting-started.md
    Architecture.md
    Instruction-set.md
    JV-DOS.md
    JVS-programming.md
    COM-programming.md
    Tooling-and-Disk-Workshop.md
    Disk-and-media-formats.md
    Example-programs.md
examples/
  README.md
  build.ps1
  bare-metal/  # commented .hex listings and generated .bin files
  dos/         # .JVS source, HI.hex and generated HI.COM
```

This is a local documentation package; no GitHub wiki has been published. If you later choose to publish, the contents of `docs/wiki/` belong at the root of the repository's separate `.wiki.git` checkout. Keep example programs in the main repository.
