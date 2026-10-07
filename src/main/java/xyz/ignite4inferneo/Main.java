/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

import java.util.Arrays;


public class Main {
    /*
    REGISTER MEMORY

    A, B, C, D = general purpose registers
    PTR = pointer register
    COUNTER = program counter
    */
    static byte[] REGISTERS = new byte[] {
            0, // A
            0, // B
            0, // C
            0, // D
            0, // E
            0, // F
            0, // COUNTER
            0  // MEMORY PAGE
        };

    static byte[] MEMORY = new byte[128];

    static boolean running = true;

    static long cpuStartTime;

    static void main() {
        Memory.initializeMemoryPages();
        IO.println("MEMORY CLEAR");

        if (false) {
            System.arraycopy(
                    Programs.PROGRAM_LOOP_127_3,
                    0,
                    Memory.getMemoryPage(REGISTERS[Registers.PAGE.address()]),
                    0,
                    Programs.PROGRAM_LOOP_127_3.length);

            IO.println("PROGRAM_LOOP_127_3 UPLOAD to MEMORY");
        } else {
            System.arraycopy(Programs.PROGRAM_PAGED_MEMORY_PAGE_0, 0,
                    Memory.getMemoryPage((byte) 0), 0, Programs.PROGRAM_PAGED_MEMORY_PAGE_0.length);
            System.arraycopy(Programs.PROGRAM_PAGED_MEMORY_PAGE_1, 0,
                    Memory.getMemoryPage((byte) 1), 0, Programs.PROGRAM_PAGED_MEMORY_PAGE_1.length);
            System.arraycopy(Programs.PROGRAM_PAGED_MEMORY_PAGE_127, 0,
                    Memory.getMemoryPage((byte) 127), 0, Programs.PROGRAM_PAGED_MEMORY_PAGE_127.length);
            IO.println("PROGRAM_PAGED_MEMORY UPLOAD to PAGES 0, 1, 127");
        }

        cpuStartTime = System.currentTimeMillis();


        while (running) {
            int counter = getProgramCounter();
            MEMORY = Memory.getMemoryPage(REGISTERS[Registers.PAGE.address()]);
            byte opcode = MEMORY[counter];

            switch (opcode) {
                // --------------------------------
                // ADD
                // ADD value, value, register
                // --------------------------------
                case 0 -> add(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);

                // --------------------------------
                // SUB
                // SUB value, value, register
                // --------------------------------
                case 1 -> sub(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);

                // --------------------------------
                // STORE
                // STORE value, register
                // --------------------------------
                case 2 -> store(MEMORY[counter + 1], MEMORY[counter + 2]);

                // --------------------------------
                // JMP
                // JMP address
                // --------------------------------
                case 3 -> jump(MEMORY[counter + 1]);


                // --------------------------------
                // STOP
                // --------------------------------
                case 4 -> stop();

                // --------------------------------
                // OUTPUT
                // OUTPUT register
                // --------------------------------
                case 5 -> output(MEMORY[counter + 1]);

                // --------------------------------
                // COND_JUMP
                // COND_JUMP register A register B address
                // --------------------------------
                case 6 -> cond_jump(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);

                // --------------------------------
                // LOAD_MEM
                // LOAD_MEM value, register
                // --------------------------------

                case 7 -> loadMemory(MEMORY[counter + 1], MEMORY[counter + 2]);

                // --------------------------------
                // STORE_MEM
                // STORE_MEM value, register
                // --------------------------------

                case 8 -> storeMemory(MEMORY[counter + 1], MEMORY[counter + 2]);

                // --------------------------------
                // CHANGE_PAGE
                // CHANGE_PAGE register
                // --------------------------------
                case 9 -> changePage(MEMORY[counter + 1]);

                // --------------------------------
                // CHANGE_PAGE_JUMP
                // CHANGE_PAGE_JUMP register target
                // --------------------------------
                case 10 -> changePageJump(MEMORY[counter + 1], MEMORY[counter + 2]);

                case 11 -> changePageCondJump(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3], MEMORY[counter + 4] );

                default -> throw new IllegalStateException(
                        "Unknown opcode: " + opcode +
                                " at address " + counter
                );
            }
        }
    }

    // ============================================================
    // PROGRAM COUNTER
    // ============================================================
    static int getProgramCounter() {
        return Byte.toUnsignedInt(
                REGISTERS[Registers.COUNTER.address()]
        );
    }

    static void incrementCounter(int amount) {
        int counter = getProgramCounter();
        counter += amount;
        REGISTERS[Registers.COUNTER.address()] = (byte) counter;
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
        REGISTERS[register] = (byte) (REGISTERS[a] + REGISTERS[b]);

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
        REGISTERS[register] = (byte) (REGISTERS[a] - REGISTERS[b]);

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
        REGISTERS[register] = value;

        incrementCounter(OPCode.STORE.length());
    }

    /*
    JMP

    JMP 10

    COUNTER = 10
    */
    public static void jump(byte address) {
        REGISTERS[Registers.COUNTER.address()] = address;
    }

    /*
    COND_JMP

    COND_JMP A B 10

    if A == B then COUNTER = 10
    */
    public static void cond_jump(byte A, byte B, byte address) {
        if (REGISTERS[A] == REGISTERS[B]) {
            REGISTERS[Registers.COUNTER.address()] = address;
        } else {
            incrementCounter(OPCode.COND_JUMP.length());
        }
    }

    /*
    STOP

    STOP
     */
    static void stop(){
        running = false;
        IO.println("EOF CPU Ran for " + (System.currentTimeMillis() - cpuStartTime) + "ms");
    }

    /*
    OUTPUT

    OUTPUT A
    */
    public static void output(byte register) {
        IO.println(
                REGISTERS[register]
        );
        incrementCounter(OPCode.OUTPUT.length());
    }

    /*
    LOAD_MEM

    LOAD_MEM REGISTER A REGISTER B

    REGISTER A = MEMORY[REGISTER B]
     */
    public static void loadMemory(byte A, byte B) {
        REGISTERS[A] = MEMORY[B];

        incrementCounter(OPCode.LOAD_MEM.length());
    }

    /*
    STORE_MEM

    STORE_MEM REGISTER A REGISTER B

    MEMORY[REGISTER B] = REGISTER A
     */
    public static void storeMemory(byte A, byte B) {
        MEMORY[B] = REGISTERS[A];

        incrementCounter(OPCode.STORE_MEM.length());
    }

    /*
    CHANGE_PAGE

    CHANGE_PAGE REGISTER A

    points to which page the page register
     */
    public static void changePage(byte A){
        REGISTERS[Registers.PAGE.address()] = REGISTERS[A];
        incrementCounter(OPCode.CHANGE_PAGE.length());
    }

    /*
    CHANGE_PAGE_JUMP

    CHANGE_PAGE_JUMP REGISTER A REGISTER B

    points to which page the page register and jumps to register
     */
    public static void changePageJump(byte A, byte B){
        REGISTERS[Registers.PAGE.address()] = REGISTERS[A];
        REGISTERS[Registers.COUNTER.address()] = REGISTERS[B];
    }

    /*
    COND_JMP

    COND_JMP A B 10

    if A == B then COUNTER = 10
    */
    public static void changePageCondJump(byte A, byte B, byte C, byte address) {
        if (REGISTERS[B] == REGISTERS[C]) {
            REGISTERS[Registers.PAGE.address()] = REGISTERS[A];
            REGISTERS[Registers.COUNTER.address()] = address;
        } else {
            incrementCounter(OPCode.CHANGE_PAGE_COND_JUMP.length());
        }
    }
}


