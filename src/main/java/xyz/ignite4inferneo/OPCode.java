/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

/** Defines the byte values and operand counts for every JavaVCPU instruction. */
public enum OPCode {
    ADD(0, 3),
    SUB(1, 3),
    STORE(2, 2),
    JMP(3, 1),
    STOP(4, 0),
    OUTPUT(5, 1),
    COND_JUMP(6, 3),
    LOAD_MEM(7, 2),
    STORE_MEM(8, 2),
    CHANGE_PAGE(9, 1),
    CHANGE_PAGE_JUMP(10, 2),
    CHANGE_PAGE_COND_JUMP(11, 4),
    PUSH_GRA_MEM(12, 3),
    CLEAR_GRA(13, 0),
    DISPLAY(14, 0);

    /** Encoded instruction byte. */
    private final byte code;

    /** Number of operand bytes following {@link #code}. */
    private final byte inputLength;

    OPCode(int code, int inputLength) {
        this.code = (byte) code;
        this.inputLength = (byte) inputLength;
    }
    
    /** Returns this instruction's opcode byte. */
    public byte code() {
        return code;
    }

    /** Returns the number of operand bytes following this instruction's opcode. */
    public byte inputLength() {
        return inputLength;
    }

    /** Returns the total encoded instruction length: one opcode byte plus its operands. */
    public int length() {
        return inputLength + 1;
    }
}
