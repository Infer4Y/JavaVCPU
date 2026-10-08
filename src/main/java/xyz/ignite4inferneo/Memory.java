/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

/** Stores the virtual CPU's 128 independently addressable memory pages. */
public class Memory {
    /** Backing storage: 128 pages of 128 bytes each. */
    private static final byte[][] PAGED_MEMORY = new byte[128][128];

    /**
     * Returns the requested memory page.
     *
     * @param page unsigned page number from {@code 0} through {@code 127}
     * @return the mutable 128-byte page backing the supplied page number
     */
    public static byte[] getMemoryPage(byte page){
        return PAGED_MEMORY[page];
    }
}
