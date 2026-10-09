/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

/** Stores the virtual CPU's 256 independently addressable memory pages. */
public class Memory {
    /** Backing storage: 256 pages of 256 bytes each. */
    private static final byte[][] PAGED_MEMORY = new byte[256][256];

    /**
     * Returns the requested memory page.
     *
     * @param page unsigned page number from {@code 0} through {@code 255}
     * @return the mutable 256-byte page backing the supplied page number
     */
    public static byte[] getMemoryPage(byte page){
        return PAGED_MEMORY[Byte.toUnsignedInt(page)];
    }
}
