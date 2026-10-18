package xyz.ignite4inferneo.jvdos;

/** Stable guest entry points in bank zero; addresses do not depend on kernel layout.
 * No host-side execution hooks are involved.
 */
public final class JvDosServices {
    private JvDosServices() { }
    public static final int PAGE=249, PRINT=0, SHELL=9, GLYPH=18;
    public static final int GRAPHICS_SIGNATURE=27, GRAPHICS_VERSION=1;
    /** K/L=x/y, M=palette ink, D=character, E=0 transparent / nonzero opaque.
     * N/O/P=return bank/page/offset. Clobbers A-M; preserves RAM and N-P.
     * Transparent draws 3x5 ink pixels; opaque clears the full 4x6 character cell.
     * Unsupported characters draw '?'. Coordinates wrap as byte values.
     */
    public static final int TRANSPARENT=0, OPAQUE=1;
}
