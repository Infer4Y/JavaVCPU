/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

/** Identifies the sixteen general-purpose and four control positions in the virtual CPU register file. */
public enum Registers {
    A(0),
    B(1),
    C(2),
    D(3),
    E(4),
    F(5),
    G(6),
    H(7),
    COUNTER(8),
    PAGE(9),
    STORAGE_PAGE(10), // Storage device ID (0..255); legacy enum name retained.
    MEMORY_BANK(11),
    I(12),
    J(13),
    K(14),
    L(15),
    M(16),
    N(17),
    O(18),
    P(19);
    
    /** Zero-based index into {@link Main#REGISTERS}. */
    private final byte address;

    Registers(int address) {
        this.address = (byte) address;
    }

    /** Returns this register's zero-based index in the register file. */
    public byte address() {
        return address;
    }
}
