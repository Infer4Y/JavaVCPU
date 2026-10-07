package xyz.ignite4inferneo;

// ============================================================
// OPCODES
// ============================================================
public enum OPCode {
    /*
    code, operand count
    */
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
    CHANGE_PAGE_COND_JUMP(11, 4);

    private final byte code;
    private final byte inputLength;

    OPCode(int code, int inputLength) {
        this.code = (byte) code;
        this.inputLength = (byte) inputLength;
    }
    
    public byte code() {
        return code;
    }

    public byte inputLength() {
        return inputLength;
    }

    /*
    Total instruction size.
    
    Every instruction contains:
    
    1 byte opcode
    N bytes operands
    */
    public int length() {
        return inputLength + 1;
    }
}
