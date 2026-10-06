package xyz.ignite4inferneo;

import jdk.jfr.Unsigned;

import java.lang.classfile.Opcode;

public class Main {
    /*
    PROGRAM MEMORY
    Each line represents the multi length op codes
    We start at counter 0 then inc by lenth of op code randing from 8 bits to 32 bits of data the cpu will grab.
    */
    static byte[] PROGRAM_DATA = new byte[] {
            OPCode.STORE.code(), 0, Registers.A.address(), // 3, 2, 24bits
            OPCode.STORE.code(), 1, Registers.B.address(), // 3, 5, 24bits
            OPCode.STORE.code(), 127, Registers.C.address(), // 3, 8, 24bits
            OPCode.STORE.code(), 0, Registers.D.address(), // 3, 11, 24bits
            OPCode.STORE.code(), 3, Registers.E.address(), // 3, 14, 24bits
            OPCode.ADD.code(), Registers.A.address(), Registers.B.address(), Registers.A.address(), // 4, 18, 32bits
            OPCode.OUTPUT.code(), Registers.A.address(), // 2, 20, 16bits
            OPCode.COND_JUMP.code(), Registers.A.address(), Registers.C.address(), 32, // 4, 24, 32bits
            OPCode.COND_JUMP.code(), Registers.D.address(), Registers.E.address(), 31, // 4, 28, 32bits
            OPCode.JMP.code(), 15, // 2, 30, 16bits
            OPCode.STOP.code(), // 1, 31, 8bits
            OPCode.ADD.code(), Registers.D.address(), Registers.B.address(), Registers.D.address(), // 4, 35, 32bits
            OPCode.STORE.code(), 0, Registers.A.address(), // 3, 38, 24bits
            OPCode.JMP.code(), 15 // 2, 40, 16bits
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
            0, // E
            0, // F
            0 // COUNTER
        };
    static boolean running = true;

    static long cpuStartTime;

    static void main() {
        cpuStartTime = System.currentTimeMillis();
        while (running) {
            int counter = getProgramCounter();
            byte opcode = PROGRAM_DATA[counter];

            switch (opcode) {
                // --------------------------------
                // ADD
                // ADD value, value, register
                // --------------------------------
                case 0 -> add(PROGRAM_DATA[counter + 1], PROGRAM_DATA[counter + 2], PROGRAM_DATA[counter + 3]);

                // --------------------------------
                // SUB
                // SUB value, value, register
                // --------------------------------
                case 1 -> sub(PROGRAM_DATA[counter + 1], PROGRAM_DATA[counter + 2], PROGRAM_DATA[counter + 3]);

                // --------------------------------
                // STORE
                // STORE value, register
                // --------------------------------
                case 2 -> store(PROGRAM_DATA[counter + 1], PROGRAM_DATA[counter + 2]);

                // --------------------------------
                // JMP
                // JMP address
                // --------------------------------
                case 3 -> jump(PROGRAM_DATA[counter + 1]);


                // --------------------------------
                // STOP
                // --------------------------------
                case 4 -> stop();

                // --------------------------------
                // OUTPUT
                // OUTPUT register
                // --------------------------------
                case 5 -> output(PROGRAM_DATA[counter + 1]);


                // --------------------------------
                // COND_JUMP
                // COND_JUMP register A register B address
                // --------------------------------
                case 6 -> cond_jump(PROGRAM_DATA[counter + 1], PROGRAM_DATA[counter + 2], PROGRAM_DATA[counter + 3]);

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

        incrementCounter(OPCode.ADD.length());
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

        incrementCounter(OPCode.SUB.length());
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

        incrementCounter(OPCode.STORE.length());
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
        incrementCounter(OPCode.OUTPUT.length());
    }

    /*
    STOP

    STOP
     */
    static void stop(){
        running = false;
        IO.println("EOF CPU Ran for " + (System.currentTimeMillis() - cpuStartTime) + "ms");
    }
}


