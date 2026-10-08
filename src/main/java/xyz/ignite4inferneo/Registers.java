/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

/** Identifies the eight positions in the virtual CPU register file. */
public enum Registers {
    A(0),
    B(1),
    C(2),
    D(3),
    E(4),
    F(5),
    COUNTER(6),
    PAGE(7);
    
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
