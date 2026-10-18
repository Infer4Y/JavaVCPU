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

`WEB.COM` is an HTTP text browser that runs inside JCTRL's existing application
window. Import `examples/jctrl/WEB.COM` through Workshop as **Binary / COM**, then
click it in JCTRL Files. Replace the imported file after rebuilding. Direct
fullscreen `RUN WEB.COM` returns to DOS; launch it through JCTRL.

Rebuild and test with:

```powershell
.\gradlew.bat browserExample
.\gradlew.bat browserRegression
```

Enter `host[:port]/path` or `http://host/path`, then press **Enter**. Letters
normally use lowercase; hold **Shift** for uppercase path letters and URL
punctuation such as `:`, `?`, `&`, and `#`. **Backspace** removes a character;
**Home** clears the address. Addresses hold up to 120 characters. Request paths
keep their case and query; fragments (`#section`) stay out of the HTTP request.
Ports are checked for the range 1–65535.

| Input | Action |
| --- | --- |
| N / Space / Page Down / Down | Next page; continue downloading when the cache is exhausted |
| P / Page Up / Up / Backspace | Previous page in the current cache |
| H / Home | First page in the current cache |
| R | Reload the document from its beginning |
| U | Edit the retained address; cancel any pending connection |
| Esc / window X | Stop the browser and close its connection |

The reader lays out headings, paragraphs, lists and table rows on separate text
rows. It suppresses `head`, `script`, and `style` content and decodes `&amp;`,
`&lt;`, `&gt;`, `&quot;`, `&apos;`, `&nbsp;`, and printable ASCII decimal references
such as `&#39;`. Quoted `>` characters in tag attributes do not end a tag.
`Content-Type: text/plain` preserves literal markup characters and line breaks.
The page header shows the HTTP status code, including error responses.

Documents can exceed the application RAM: WEB reads into a **768-byte cache**,
pauses when full, and resumes the same response as you request more pages. The
first screen uses up to 512 characters; the rest of the cache is another screen.
Previous/Home stay within that cache; use Reload to revisit earlier content
after a refill. No disk cache, scratch files, or storage writes are used.

The executable, tables, URL and state fit JCTRL's unchanged 8 KiB workspace.
Fixed messages and lookup tables replace repeated print/keyboard/tag code.
Receive pointers live in guest registers, and network input is polled in batches
of 64 bytes instead of yielding after every byte. Idle screens do not repaint.
The regression measures receive work through the real guest interpreter and
checks a response larger than RAM, cache boundaries, previous/next/reload,
HTML/plain-text rendering, URL keyboard mapping, invalid ports, and preservation
of every disk byte. It uses only a local HTTP server.

This remains a small HTTP reader: no TLS/HTTPS, clickable links, redirects,
JavaScript execution, CSS layout, images, compressed bodies or chunked transfer
decoding. Unknown/non-ASCII entities display `?`; long words wrap at the window
edge. It is intended for simple HTTP pages and local servers, not modern web
applications. `Storage.java`, networking, the CPU and JCTRL are unchanged.
