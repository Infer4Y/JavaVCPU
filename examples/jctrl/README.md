# JCTRL Pong

## JCTRL text editor

`TEXTEDIT.COM` is a windowed guest text/hex editor, like `WEB.COM`. Build it and
the current desktop with JDK 26:

```powershell
.\gradlew.bat desktopImage textEditExample
.\gradlew.bat textEditRegression
```

Start the VM, enter `JCTRL`, and open `TEXTEDIT.COM` from Files. Startup installs
and updates both managed applications on the system disk. It also upgrades the
original standalone TEXTEDIT release. Modified/manual copies and same-name
directories are preserved. Replace manual JCTRL copies with the rebuilt version.
TEXTEDIT requires application services v5 and returns to DOS if launched fullscreen.
The generated COM remains available for manual import through Disk Workshop as
**Binary / COM**, with the VM stopped.

Enter a filename from the current Files folder, then Enter. A missing filename
starts a new document; F7 creates it. Files are limited to **4,096 bytes**;
larger files are refused without writing them.

| Input | Action |
| --- | --- |
| F6 / F8 | Open a filename / start a new document |
| F7 | Save to the virtual disk; clears the `*` modification marker |
| Arrows | Move the caret; Up/Down retain the visual column in text |
| Home / End | Start/end of the current wrapped text row; whole file in COM mode |
| Page Up / Page Down, mouse wheel | Move twenty rows |
| Backspace / Delete | Remove before/at the text caret |
| Enter / Tab | Insert a newline / four spaces |
| Esc / window X | Close; unsaved changes are discarded |

The `|` marker shows the caret and temporarily covers the character being edited.
Shift supplies uppercase and punctuation. JVS highlighting distinguishes
keywords, decimal numbers, registers, REM/semicolon comments and PRINT/ECHO text.
`.HEX` text highlights hexadecimal digits and `#`/semicolon comments.
Binary disk entries (including `.COM`) show twelve bytes per row, coloring
opcodes, operands and invalid opcode bytes. Type two hex digits to replace a
byte; COM mode overwrites bytes and preserves file length. It does not execute
the file or convert binary bytes into text when saving.

Opening another file with changes pending asks for **Y** to discard; any other
key returns to the document. Save before closing with Esc/X. Saves use the
resident JVFS replacement transaction; a failed write aborts the replacement.
Text bytes, including imported CRLF endings and tabs, are preserved unless edited.
Imported tabs display as `>`; new tabs insert four spaces. Lines wrap at 48 cells.
Colors and content survive window moves.

`src/main/java/xyz/ignite4inferneo/jvdos/TextEditImage.java` assembles ordinary
guest bytecode and is included in the application jar for boot installation.
The file buffer uses virtual pages 64-79; the retained character/color frame
uses 80-89, and state uses 90. Page 31 holds the JVFS mailbox in bytes 0-63 and
packed read-only literals above it; execution skips that page.
No CPU opcodes, Java editor hooks,
network controller changes or additional RAM are needed. Regression checks
exercise the actual desktop interpreter, syntax colors, navigation, byte edits,
page boundaries, file creation/replacement, oversized-file refusal and cancellation.

The editor uses shared JCTRL keyboard, frame clearing, rendering and resident
JVFS block I/O. Horizontal movement patches the caret cells; short-line edits
rebuild only the affected row. Overlapping byte moves keep their pointers in
registers. Wrapped lines, newlines, CRLF and tabs use the full redraw path.
The v5 scheduler executes the full bounded 16-slice app budget and yields on
DISPLAY. `textEditPerformanceRegression` checks latency and instruction counts
for movement and insertion in an 800-byte JVS file.

## Pong

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

<a id="jctrl-web-reader"></a>

## JCTRL web browser

`WEB.COM` is a guest HTTP/HTML text browser with numbered links, basic GET forms,
redirects and Back navigation. It requires **JCTRL application services v3**. Rebuild/update
both `examples/dos/JCTRL.COM` and `examples/jctrl/WEB.COM`; import manual copies
through Workshop as **Binary / COM**, then launch WEB from JCTRL Files.
Fullscreen `RUN WEB.COM` returns to DOS.

```powershell
.\gradlew.bat desktopImage browserExample
.\gradlew.bat browserRegression browserNavigationRegression browserFormsRegression desktopServicesRegression storageRegression
python tools/web-gateway/test_web_gateway.py --guest-classes build/classes/storageRegression
```

Enter `host[:port]/path`, `http://host/path`, or `https://host/path`. Letters
normally use lowercase; Shift supplies uppercase and punctuation. Backspace
edits the address, Left/Right move the `|` cursor, End moves to the end, and Home
clears it. Addresses and link/redirect targets are
limited to 120 ASCII characters. Request paths preserve case/query and omit
fragments. Ports must be 1-65535.

| Input | Action |
| --- | --- |
| N / Space / Page Down / Down | Next screen, resuming a paused download |
| P / Page Up / Up / Backspace | Previous saved screen |
| H / Home | First screen of the saved document |
| L / Tab | Open the numbered link chooser |
| Tab / Up / Down in chooser | Select a link; Enter follows it |
| 1-9 in chooser | Follow that link |
| Left / Right in chooser | Previous / next group of nine; refetches and scans the document |
| L in chooser | Return to the current screen |
| B | Back to the previous document (three-entry stack; refetches its URL) |
| R | Reload the current address |
| U | Edit the retained address; also cancels a pending download |
| G in page/error/chooser | Toggle the optional local gateway and retry |
| Esc / window X | Stop WEB and close its connection/file handle |

The footer labels are clickable, and the wheel changes screens. The light
48-column document surface fills the JCTRL window and retains text when the
window moves. The address/status stay visible; `[gateway]` identifies relayed
connections. Nine nonempty, bounded links/forms are collected per group. Their
`[1]`-`[9]` markers match the chooser. Left/Right in the chooser refetches the
current URL and scans the whole document for another group, using the shared
disk spool. Up to 255 candidate destinations can be scanned. Group changes need
a network connection; they can also change results on a changing page.
Quoted/unquoted, case-insensitive `href` attributes are recognized; `&amp;`
inside a destination is decoded. Unsupported schemes are rejected explicitly.

Basic GET forms appear in the same chooser. Selecting one opens its resolved
address/query for editing; Enter submits it. Text/search inputs, hidden defaults,
a named submit button, and single-select defaults are encoded using `+` and
`%HH`. Disabled inputs and checkbox/radio controls are omitted.
POST forms, password fields, files, textareas and interactive control widgets
are not supported. Edit encoded query values directly (`+` for spaces); the
whole resolved address still has the 120-character limit. Oversized or unfinished
forms are refused instead of sending a truncated query.

For **theoldnet.com/lite**, open L and choose 1 for the archived URL/year query,
2 for archive search, or 3 for Wikipedia search. Change the query with the cursor
keys, then Enter. The live homepage and archive search have been exercised in
the actual guest, both directly over HTTP and through the verified HTTPS gateway.

HTTP 301, 302, 303, 307 and 308 responses with a Location header are followed,
up to five hops. Absolute, scheme-relative, root-relative, directory-relative,
query and fragment references share one bounded resolver; path dot segments are
removed without changing query case or data. Missing Location leaves the server
response readable. Loops, oversized targets and invalid schemes stop with a
message. The address and Back history use the final destination.

Direct HTTP handles chunked bodies, chunk extensions and trailers, and rejects
truncated chunk framing. Queued response bytes are drained when EOF arrives
between receive-count/status reads. HTML formatting includes block boundaries, quoted
attributes, head/script/style suppression, common named entities and printable
ASCII decimal references. Plain text preserves markup characters and line breaks.

### Optional HTTPS gateway

The VM's TCP device does not implement TLS. This optional companion provides
verified TLS without modifying the CPU, network device or HTML renderer:

```powershell
python tools/web-gateway/web_gateway.py
```

Leave it running on **127.0.0.1:8080**, then press **G** in WEB's page/error view.
For an HTTPS redirect, the target is retained so G can retry it immediately.
The helper performs HTTP(S) transport, dechunking and gzip/deflate decompression;
the guest still parses HTML, resolves redirects, formats pages and handles links.
TLS certificates and hostnames are verified. Redirects are returned to the guest,
not followed by the helper. Stop the helper with Ctrl+C. See
[the gateway notes](../../tools/web-gateway/README.md).

A 403 remains a refusal by the server. This browser does not bypass login,
JavaScript checks or other site requirements. Cookies/authentication,
CSS layout, images and JavaScript are not implemented; it is a small text
browser, rather than an Internet Explorer-compatible engine. Unsupported UTF-8
characters/entities are not rendered as a complete Unicode font. Direct compressed
HTTP bodies suggest enabling the gateway instead of displaying compressed bytes.

### Workspace and saved pages

The 19.5 KiB COM uses JCTRL's larger document-app layout: up to 91 virtual pages
of existing RAM, borrowed from the pixel buffer. Regular graphics COMs keep
32 pages and their 128 by 128 pixel surface. Page 31 remains the shared JVFS
mailbox and the assembler branches around it. Browser text uses virtual pages
78-80 (768 bytes); each screen holds 16 rows. State slots and pairs of bounded
URL/link/history records are packed into the remaining document RAM. Literal
strings share the unused end of the lookup-table page and use first-fit packing.
CPU/ISA and total RAM are
unchanged. Clearing the native text surface touches five pages.

Completed rendered text is saved as new `WEB0000.TXT`, `WEB0001.TXT`, etc. under
`WEB.CACHE` in the selected folder. Existing files are never replaced. Cache
files remain ordinary JVFS text readable through Files/TYPE/VIEW. Downloading
pauses at each 768-byte refill; after completion Previous/Home can seek through
the whole saved document without refetching it. Before completion they cover the
current RAM window. An unavailable cache directory, allocation failure or
exhausted 256-name range falls back to RAM. Cancelled/redirected unfinished
spools are aborted; completed pages remain available.

The browser reuses shared document, block-text, packed network output, keyboard
and JVFS routines. Request-header literals run through the shared guest loop
rather than a per-byte interpreted loop.
Regression fixtures cover navigation/framing, transport, rendering, large-page
refills, cache collisions/fallback, cancellation, register/span bounds, execution
above page 31, GET submission/editing, empty select defaults, link groups across
streamed pages and refusal of reads outside app RAM. See
[the application ABI](../../docs/wiki/COM-programming.md#shared-jctrl-application-services).

An opt-in live check (excluded from offline checks) loads the requested site in
the guest and saves previews under `build/web-oldnet*.png`:

```powershell
python tools/web-gateway/test_live_browser.py --guest-classes build/classes/storageRegression
python tools/web-gateway/test_live_browser.py --guest-classes build/classes/storageRegression --direct http://theoldnet.com/lite
```
