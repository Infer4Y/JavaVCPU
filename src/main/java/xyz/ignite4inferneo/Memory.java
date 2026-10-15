/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */
package xyz.ignite4inferneo;

/** 128 KiB of byte-addressable RAM, divided into two compatible 64 KiB banks. */
public final class Memory {
    public static final int BANK_COUNT = 2;
    public static final int PAGES_PER_BANK = 256;
    public static final int PAGE_SIZE = 256;
    public static final int SIZE = BANK_COUNT * PAGES_PER_BANK * PAGE_SIZE;
    private static final byte[][][] RAM = new byte[BANK_COUNT][PAGES_PER_BANK][PAGE_SIZE];

    private Memory() { }

    /** Legacy host API: a page in bank zero. */
    public static byte[] getMemoryPage(byte page) {
        return getMemoryPage((byte) 0, page);
    }

    /** Returns a page in RAM bank 0 or 1; invalid banks are rejected, never wrapped. */
    public static byte[] getMemoryPage(byte bank, byte page) {
        int index = Byte.toUnsignedInt(bank);
        if (index >= BANK_COUNT) throw new IllegalArgumentException("RAM bank must be 0 or 1: " + index);
        return RAM[index][Byte.toUnsignedInt(page)];
    }

    /** Maps a 17-bit byte address to bank:page:offset. */
    public static byte read(int address) {
        checkAddress(address);
        return RAM[address >>> 16][(address >>> 8) & 255][address & 255];
    }

    public static void write(int address, byte value) {
        checkAddress(address);
        RAM[address >>> 16][(address >>> 8) & 255][address & 255] = value;
    }

    private static void checkAddress(int address) {
        if (address < 0 || address >= SIZE) throw new IllegalArgumentException("RAM address outside 128 KiB: " + address);
    }
}
