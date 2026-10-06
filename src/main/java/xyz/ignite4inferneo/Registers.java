package xyz.ignite4inferneo;

// ============================================================
// REGISTERS
// ============================================================
public enum Registers {
    A(0),
    B(1),
    C(2),
    D(3),
    E(4),
    F(5),
    COUNTER(6);
    
    private final byte address;
    Registers(int address) {
        this.address = (byte) address;
    }
    public byte address() {
        return address;
    }
}
