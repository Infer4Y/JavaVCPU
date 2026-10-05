package xyz.ignite4inferneo;

import java.lang.classfile.Opcode;

public class Main {
    /*
    PROGRAM MEMORY

    ADD 4, 5, A
    OUTPUT A
    STOP

    Memory:

    0: ADD
    1: 4
    2: 5
    3: A

    4: OUTPUT
    5: A

    6: STOP
    */
    static byte[] PROGRAM_DATA = new byte[] {
            OPCode.STORE.code(), 5, Registers.A.address(), // 3
            OPCode.STORE.code(), 5, Registers.B.address(), // 6
            OPCode.STORE.code(), 50, Registers.C.address(), // 9
            OPCode.ADD.code(), Registers.A.address(), Registers.B.address(), Registers.A.address(), // 13
            OPCode.OUTPUT.code(), Registers.A.address(), // 15
            OPCode.COND_JUMP.code(), Registers.A.address(), Registers.C.address(), 21, // 19
            OPCode.JMP.code(), 9, // 20
            OPCode.STOP.code() // 21
        };

    /*
    REGISTER MEMORY

    A, B, C, D = general purpose registers
    PTR = pointer register
    COUNTER = program counter
    */
    static byte[] ADDRESSES = new byte[] {
            0, // A
            0, // B
            0, // C
            0, // D
            0, // PTR
            0 // COUNTER
        };
    static boolean running = true;

    static void main() {
        while (running) {
            int counter = getProgramCounter();
            byte opcode = PROGRAM_DATA[counter];

            switch (opcode) {
                // --------------------------------
                // ADD
                // ADD value, value, register
                // --------------------------------
                case 0 -> {
                    add(
                            PROGRAM_DATA[counter + 1],
                            PROGRAM_DATA[counter + 2],
                            PROGRAM_DATA[counter + 3]
                    );
                    incrementCounter(OPCode.ADD.length());
                }

                // --------------------------------
                // SUB
                // SUB value, value, register
                // --------------------------------
                case 1 -> {
                    sub(
                            PROGRAM_DATA[counter + 1],
                            PROGRAM_DATA[counter + 2],
                            PROGRAM_DATA[counter + 3]
                    );
                    incrementCounter(OPCode.SUB.length());
                }

                // --------------------------------
                // STORE
                // STORE value, register
                // --------------------------------
                case 2 -> {
                    store(
                            PROGRAM_DATA[counter + 1],
                            PROGRAM_DATA[counter + 2]
                    );
                    incrementCounter(OPCode.STORE.length());
                }

                // --------------------------------
                // JMP
                // JMP address
                // --------------------------------
                case 3 -> {
                    jump(
                            PROGRAM_DATA[counter + 1]
                    );
                }

                // --------------------------------
                // STOP
                // --------------------------------
                case 4 -> {
                    running = false;
                    IO.println("EOF");
                }

                // --------------------------------
                // OUTPUT
                // OUTPUT register
                // --------------------------------
                case 5 -> {
                    output(
                            PROGRAM_DATA[counter + 1]
                    );
                    incrementCounter(OPCode.OUTPUT.length());
                }

                case 6 -> {
                    cond_jump(
                        PROGRAM_DATA[counter + 1],
                        PROGRAM_DATA[counter + 2],
                        PROGRAM_DATA[counter + 3]
                    );
                }

                default -> {
                    throw new IllegalStateException(
                            "Unknown opcode: " + opcode +
                                    " at address " + counter
                    );
                }
            }
        }
    }

    // ============================================================
// PROGRAM COUNTER
// ============================================================
    static int getProgramCounter() {
        return Byte.toUnsignedInt(
                ADDRESSES[Registers.COUNTER.address()]
        );
    }

    static void incrementCounter(int amount) {
        int counter = getProgramCounter();
        counter += amount;
        ADDRESSES[Registers.COUNTER.address()] = (byte) counter;
    }

    // ============================================================
    // INSTRUCTIONS
    // ============================================================
    /*
    ADD

    ADD REGISTER 1, REGISTER 2, REGISTER OUTPUT

    REGISTER OUTPUT = REGISTER 1 + REGISTER 2
    */
    public static void add(
            byte a,
            byte b,
            byte register
    ) {
        ADDRESSES[register] = (byte) (ADDRESSES[a] + ADDRESSES[b]);
    }

    /*
    SUB

    SUB REGISTER 1, REGISTER 2, REGISTER OUTPUT

    REGISTER OUTPUT = REGISTER 1 - REGISTER 2
    */
    public static void sub(
            byte a,
            byte b,
            byte register
    ) {
        ADDRESSES[register] = (byte) (ADDRESSES[a] - ADDRESSES[b]);
    }

    /*
    STORE

    STORE 10, A

    A = 10
    */
    public static void store(
            byte value,
            byte register
    ) {
        ADDRESSES[register] = value;
    }

    /*
    JMP

    JMP 10

    COUNTER = 10
    */
    public static void jump(byte address) {
        ADDRESSES[Registers.COUNTER.address()] = address;
    }

    /*
    COND_JMP

    COND_JMP A B 10

    if A == B then COUNTER = 10
    */
    public static void cond_jump(byte A, byte B, byte address) {
        if (ADDRESSES[A] == ADDRESSES[B]) {
            ADDRESSES[Registers.COUNTER.address()] = address;
        } else {
            incrementCounter(OPCode.COND_JUMP.length());
        }
    }

    /*
    OUTPUT

    OUTPUT A
    */
    public static void output(byte register) {
        IO.println(
                ADDRESSES[register]
        );
    }
}


