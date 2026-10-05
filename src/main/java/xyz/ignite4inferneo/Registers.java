package xyz.ignite4inferneo;

// ============================================================
// REGISTERS
// ============================================================
public enum Registers {
    A(0),
    B(1),
    C(2),
    D(3),
    PTR(4),
    COUNTER(5);
    
    private final byte address;
    Registers(int address) {
        this.address = (byte) address;
    }
    public byte address() {
        return address;
    }
}
