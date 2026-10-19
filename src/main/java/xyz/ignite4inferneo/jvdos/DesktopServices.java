package xyz.ignite4inferneo.jvdos;

/** JCTRL guest application services, dispatched by desktop bytecode at 0:249:42.
 * No CPU instructions, host callbacks, or additional RAM are required.
 * N/O/P hold the return bank/page/offset; other registers are preserved except
 * documented outputs. A is the operation on entry and status on return.
 */
public final class DesktopServices {
    private DesktopServices() { }
    public static final int ENTRY=42, VERSION=5;
    public static final int QUERY=0, TEXT=1, PACKED_TEXT=2, KEYBOARD=3, CONTEXT=4,
        CACHE_BEGIN=5,CACHE_FLUSH=6,CACHE_FINISH=7,CACHE_NAVIGATE=8,CACHE_ABORT=9, DOCUMENT=10, NETWORK_PACKED=11,
        DOCUMENT_FRAME=12;
    public static final int OK=0, BAD_ARGUMENT=1, END=2;
    /** TEXT: B/C = virtual bank-one page/offset; D = length (0 means 256).
     * May cross pages, but must remain within the current app workspace (0..31 or 0..90). No terminator is required.
     * PACKED_TEXT: B/C = Asm's packed literal; stops at NUL, at most 80 chars.
     * NETWORK_PACKED: same packed source as PACKED_TEXT, sent to TCP TX port 5.
     * Does not connect/send the controller or draw. Poll network errors normally.
     * KEYBOARD: B = VK code, C = shift state (0/1); returns D = ASCII or zero,
     * C = updated shift state. Control/navigation keys return zero.
     * CONTEXT: returns B/C = selected JVFS directory, low/high byte.
     * QUERY: returns D = VERSION. Unknown operations return BAD_ARGUMENT.
     *
     * DOCUMENT: opt into a retained 48-column, 23-row window text surface.
     * Clears the surface and cursor. TEXT, PACKED_TEXT and legacy print use it;
     * CLEAR_GRA clears it. Pixels/games retain their default 128x128 surface.
     * Uses existing framebuffer RAM; selected until the application closes.
     * DOCUMENT_FRAME: B = first virtual page of a ten-page frame: five character
     * pages followed by five color pages, one byte per cell, row-major (48 x 23). Retains references until
     * DOCUMENT or another FRAME; caller keeps buffers alive. Marks the view dirty.
     * Since v5, CLEAR_GRA clears both retained frame buffers through shared guest code.
     * The span must fit app RAM and exclude page 31. Palette colors use 7 bits.
     * Suppresses reader controls. Frame clients receive up to 16 x 255 guest
     * instructions per desktop tick; DISPLAY still yields immediately.
     * DOCUMENT restores the standard reader view.
     *
     * CACHE_BEGIN: B/C = first/exclusive-end buffer pages, at least two pages (three in DOCUMENT mode);
     * C must be <= the workspace end; the span must exclude page 31. Reserves bytes 0..63 of virtual page 31 and JVFS handle 3.
     * CACHE_FLUSH: B/C = end page/offset of newly filled buffer; append it once.
     * CACHE_FINISH: commit the spool and reopen for reads.
     * CACHE_NAVIGATE: B = 0 Home / 1 Previous / 2 Next, C = current screen's
     * first RAM page. Returns B/C = loaded text end page/offset, at most 512 bytes (768 in DOCUMENT mode);
     * A = END when there is no next page. Valid only after FINISH succeeds.
     * CACHE_ABORT: discard an unfinished file, or close a finished read handle.
     * All cache operations return D = 0 RAM fallback, 1 writing, or 2 readable.
     * Saved text uses new WEBxxxx.TXT files under WEB.CACHE in the selected folder.
     * Storage failure falls back to RAM; existing files are never replaced.
     */
}
