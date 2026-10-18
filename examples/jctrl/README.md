# JCTRL Pong

`PONG.COM` is a guest-bytecode game for the JCTRL application window.
Move the mouse vertically inside the game to control the left paddle. The right
paddle is the computer. First to seven points wins; score pips appear across the
top and the winner's side lights up. Press **R** to restart and **Esc** to return
to JCTRL. Move the pointer away from the title bar when playing.

With the VM stopped, open `storage.img` in `tools/disk-workshop/index.html`.
Import `examples/jctrl/PONG.COM` as Binary / COM into a bank with free space,
download the updated disk and replace `storage.img`. Start the VM, enter
`JCTRL`, browse to that bank using BANK-/BANK+, and click `PONG.COM` in Files.
The generator does not modify or mount your disk image. After rebuilding or
updating Pong, replace the imported `PONG.COM` on your disk to use the new version.

Pong requires JCTRL's scaled application framebuffer. A startup probe checks
that adjacent virtual pixels share a physical pixel. Direct `RUN PONG.COM` in
JV-DOS returns to its prompt without starting the game. This is a compatibility
check, not a security boundary. No VM opcodes or host game logic are added.

Rebuild with JDK 26:

```powershell
.\gradlew.bat jctrlExamples
```

`PongImage.java` assembles the COM using the desktop's existing page-aware
assembler. Code stays below virtual page 31; page 31 stores game state and the
drawing return address. The game uses no storage writes, and yields with
`DISPLAY` so the desktop can move, repaint, or cancel its window. Run
`gradlew.bat storageRegression` for launch, gameplay, restart and disk checks.

Board updates reuse stationary paddles and redraw only the exposed edges of the
computer paddle. Small shapes use unrolled pixel writes, avoiding interpreted
rectangle-loop overhead. The regression check measures twelve ball updates in
34 desktop ticks with a stationary mouse; actual speed depends on VM execution
and desktop repainting.

## JCTRL web reader

`WEB.COM` is an HTTP text browser for JCTRL. This version requires the rebuilt
JCTRL with application services v1. Normal startup updates the managed desktop;
replace manual copies of both `examples/dos/JCTRL.COM` and
`examples/jctrl/WEB.COM` through Workshop as **Binary / COM**. Direct fullscreen
`RUN WEB.COM` returns to DOS; launch WEB from JCTRL Files.

Rebuild and test:

```powershell
.\gradlew.bat desktopImage browserExample
.\gradlew.bat browserRegression desktopServicesRegression
```

Enter `host[:port]/path` or `http://host/path`, then press **Enter**. Letters
normally use lowercase; hold **Shift** for uppercase and punctuation such as
`:`, `?`, `&`, and `#`. The shared decoder also supports numpad input.
**Backspace** removes a character; **Home** clears the address. Addresses hold
120 characters; request paths retain case/query and omit fragments. Ports must
be in 1-65535.

| Input | Action |
| --- | --- |
| N / Space / Page Down / Down | Next screen; resume a response paused at the RAM limit |
| P / Page Up / Up / Backspace | Previous screen |
| H / Home | Beginning of the saved document |
| R | Download the document again |
| U | Edit the retained address and cancel any unfinished download |
| Esc / window X | Stop WEB and close its connection/file handle |

While downloading, WEB keeps a 768-byte window in RAM and pauses at each refill.
The first screen uses up to 512 characters. Previous/Home stay within that
window until the response completes. After completion they can seek throughout
the entire saved document, without another HTTP request.

Completed rendered text is saved as a new `WEB0000.TXT`, `WEB0001.TXT`, etc. in
`WEB.CACHE` under the folder selected when the browser launched. Existing files
are never replaced. Cache files are normal JVFS text files: open them with Files,
`TYPE`, or `VIEW`, and delete old ones when no longer needed. WEB searches up to
256 names. An unavailable cache directory, allocation failure, or exhausted name
range switches the reader to RAM-only operation. Uncommitted downloads are
aborted on address changes, cancellation, or application close; completed files
remain available.

Headings, paragraphs, lists and table rows start new text rows. The reader hides
`head`, `script`, and `style` contents, handles quoted tag attributes, and decodes
common named entities and printable ASCII decimal references. `text/plain`
retains markup characters and line breaks. The header shows the HTTP status.

WEB uses the desktop's shared block-text, packed-literal, keyboard and JVFS spool
services. A block renders in guest code before the desktop repaints, instead of
interpreting and repainting each character separately. The independent service
regression rendered 256 glyphs in about 3.46 million machine instructions versus
113.9 million for individual print calls (about 33x less work). Idle views do not
repaint. The 8 KiB application workspace and VM instruction set are unchanged.

Regression checks cover response bytes across RAM refills, disk history and
cluster seeks, filename collisions, RAM fallback, cancellation, user-file
preservation, register/span bounds and keyboard decoding. They use local HTTP
fixtures and independent guest COM clients.

This remains a small HTTP text reader: no TLS/HTTPS, clickable links, automatic
redirects, JavaScript execution, CSS layout, images, compressed bodies or chunked
transfer decoding. Unknown/non-ASCII entities display `?`; long words wrap at the
window edge. See [the reusable application ABI](../../docs/wiki/COM-programming.md#shared-jctrl-application-services)
for using the same routines in other COM applications.
