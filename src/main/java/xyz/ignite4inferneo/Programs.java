/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

/** Provides the sample bytecode programs that can be loaded into virtual memory. */
public class Programs {
    /** Names the built-in programs supported by {@link #load(Program)}. */
    public enum Program {
        HELLO_CPU,
        LOOP_127_3,
        PAGED_MEMORY,
        SNOWFALL
    }

    /**
     * Uploads a built-in program and resets execution to page {@code 0}, address {@code 0}.
     *
     * @param program program to upload into virtual memory
     */
    public static void load(Program program) {
        switch (program) {
            case HELLO_CPU -> upload(PROGRAM_HELLO_CPU, (byte) 0);
            case LOOP_127_3 -> upload(PROGRAM_LOOP_127_3, (byte) 0);
            case PAGED_MEMORY -> {
                upload(PROGRAM_PAGED_MEMORY_PAGE_0, (byte) 0);
                upload(PROGRAM_PAGED_MEMORY_PAGE_1, (byte) 1);
                upload(PROGRAM_PAGED_MEMORY_PAGE_127, (byte) 127);
            }
            case SNOWFALL -> upload(PROGRAM_SNOWFALL, (byte) 0);
        }
        Main.REGISTERS[Registers.PAGE.address()] = 0;
        Main.REGISTERS[Registers.COUNTER.address()] = 0;
        IO.println(program + " UPLOAD to MEMORY");
    }

    /** Copies a program to the beginning of one memory page. */
    private static void upload(byte[] program, byte page) {
        System.arraycopy(program, 0, Memory.getMemoryPage(page), 0, program.length);
    }

    /**
     * Builds the ASCII byte values for {@code "hello I'm a cpu"} with arithmetic instructions and
     * outputs them as decimal values. The 117-byte program occupies only page {@code 0}.
     */
    static final byte[] PROGRAM_HELLO_CPU = new byte[] {
            OPCode.STORE.code(), 52, Registers.B.address(),
            OPCode.STORE.code(), 3, Registers.C.address(),
            OPCode.STORE.code(), 7, Registers.D.address(),
            OPCode.STORE.code(), 32, Registers.E.address(), // reusable space
            OPCode.STORE.code(), 2, Registers.F.address(),

            OPCode.ADD.code(), Registers.B.address(), Registers.B.address(), Registers.A.address(), // h = 52 + 52
            OPCode.OUTPUT.code(), Registers.A.address(),
            OPCode.SUB.code(), Registers.A.address(), Registers.C.address(), Registers.A.address(), // e = 104 - 3
            OPCode.OUTPUT.code(), Registers.A.address(),
            OPCode.ADD.code(), Registers.A.address(), Registers.D.address(), Registers.A.address(), // l = 101 + 7
            OPCode.OUTPUT.code(), Registers.A.address(),
            OPCode.OUTPUT.code(), Registers.A.address(), // reuse l
            OPCode.ADD.code(), Registers.A.address(), Registers.C.address(), Registers.A.address(), // o = 108 + 3
            OPCode.OUTPUT.code(), Registers.A.address(),
            OPCode.OUTPUT.code(), Registers.E.address(),

            OPCode.SUB.code(), Registers.A.address(), Registers.E.address(), Registers.A.address(),
            OPCode.SUB.code(), Registers.A.address(), Registers.C.address(), Registers.A.address(),
            OPCode.SUB.code(), Registers.A.address(), Registers.C.address(), Registers.A.address(), // I = 111 - 32 - 3 - 3
            OPCode.OUTPUT.code(), Registers.A.address(),
            OPCode.ADD.code(), Registers.E.address(), Registers.D.address(), Registers.A.address(), // apostrophe = 32 + 7
            OPCode.OUTPUT.code(), Registers.A.address(),
            OPCode.ADD.code(), Registers.B.address(), Registers.B.address(), Registers.A.address(),
            OPCode.ADD.code(), Registers.A.address(), Registers.D.address(), Registers.A.address(),
            OPCode.SUB.code(), Registers.A.address(), Registers.F.address(), Registers.A.address(), // m = 52 + 52 + 7 - 2
            OPCode.OUTPUT.code(), Registers.A.address(),
            OPCode.OUTPUT.code(), Registers.E.address(),

            OPCode.ADD.code(), Registers.B.address(), Registers.B.address(), Registers.A.address(),
            OPCode.SUB.code(), Registers.A.address(), Registers.D.address(), Registers.A.address(), // a = 52 + 52 - 7
            OPCode.OUTPUT.code(), Registers.A.address(),
            OPCode.OUTPUT.code(), Registers.E.address(),
            OPCode.ADD.code(), Registers.A.address(), Registers.F.address(), Registers.A.address(), // c = 97 + 2
            OPCode.OUTPUT.code(), Registers.A.address(),
            OPCode.STORE.code(), 13, Registers.B.address(),
            OPCode.ADD.code(), Registers.A.address(), Registers.B.address(), Registers.A.address(), // p = 99 + 13
            OPCode.OUTPUT.code(), Registers.A.address(),
            OPCode.ADD.code(), Registers.A.address(), Registers.D.address(), Registers.A.address(),
            OPCode.SUB.code(), Registers.A.address(), Registers.F.address(), Registers.A.address(), // u = 112 + 7 - 2
            OPCode.OUTPUT.code(), Registers.A.address(),
            OPCode.STOP.code()
    };

    /**
     * Animates a white snowflake that falls until it reaches the bottom or an occupied pixel.
     * Settled flakes remain in the framebuffer, building a snowbank across all 128 columns.
     */
    static final byte[] PROGRAM_SNOWFALL = new byte[] {
            OPCode.CLEAR_GRA.code(),                                                   // 0
            OPCode.STORE.code(), 0, Registers.A.address(),                             // 1: current row
            OPCode.STORE.code(), 0, Registers.B.address(),                             // 4: current column
            OPCode.STORE.code(), 1, Registers.C.address(),                             // 7: increment
            OPCode.STORE.code(), 0b11_111_11, Registers.D.address(),                   // 10: white and final coordinate
            OPCode.STORE.code(), 0, Registers.E.address(),                             // 13: black

            OPCode.STORE.code(), 0b11_111_11, Registers.D.address(),                   // 16: restore white
            OPCode.PUSH_GRA_MEM.code(), Registers.B.address(), Registers.A.address(),
                    Registers.D.address(),                                              // 19: draw falling flake
            OPCode.COND_JUMP.code(), Registers.A.address(), Registers.D.address(), 39, // 23: bottom row
            OPCode.ADD.code(), Registers.A.address(), Registers.C.address(), Registers.F.address(), // 27: row below
            OPCode.READ_GRA_MEM.code(), Registers.B.address(), Registers.F.address(),
                    Registers.D.address(),                                              // 31: color below
            OPCode.COND_JUMP.code(), Registers.D.address(), Registers.E.address(), 58, // 35: fall if black

            OPCode.STORE.code(), 0, Registers.A.address(),                             // 39: settle and respawn
            OPCode.COND_JUMP.code(), Registers.B.address(), Registers.D.address(), 52, // 42: reset column after 127
            OPCode.ADD.code(), Registers.B.address(), Registers.C.address(), Registers.B.address(), // 46
            OPCode.JMP.code(), 55,                                                      // 50
            OPCode.STORE.code(), 0, Registers.B.address(),                            // 52
            OPCode.DISPLAY.code(),                                                      // 55
            OPCode.JMP.code(), 16,                                                      // 56

            OPCode.SUB.code(), Registers.F.address(), Registers.C.address(), Registers.A.address(), // 58: restore current row
            OPCode.PUSH_GRA_MEM.code(), Registers.B.address(), Registers.A.address(),
                    Registers.E.address(),                                              // 62: erase previous flake position
            OPCode.ADD.code(), Registers.A.address(), Registers.C.address(), Registers.A.address(), // 66: move down
            OPCode.STORE.code(), 0b11_111_11, Registers.D.address(),                   // 70
            OPCode.PUSH_GRA_MEM.code(), Registers.B.address(), Registers.A.address(),
                    Registers.D.address(),                                              // 73: draw moved flake
            OPCode.JMP.code(), 55                                                       // 77
    };

    /**
     * First page of the paged-memory example. It stores {@code 11} at address {@code 120}, then
     * changes to page {@code 1}.
     */
    static final byte[] PROGRAM_PAGED_MEMORY_PAGE_0 = new byte[] {
            OPCode.STORE.code(), 11, Registers.A.address(),                         // 0
            OPCode.STORE_MEM.code(), Registers.A.address(), 120,                  // 3
            OPCode.STORE.code(), 1, Registers.F.address(),                      // 6
            OPCode.STORE.code(), 0, Registers.E.address(),                       // 9
            OPCode.CHANGE_PAGE_JUMP.code(), Registers.F.address(), Registers.E.address(), // 12
            OPCode.LOAD_MEM.code(), Registers.A.address(), 120,                   // 15
            OPCode.OUTPUT.code(), Registers.A.address(),                          // 18
            OPCode.STORE.code(), 1, Registers.F.address(),                        // 20
            OPCode.STORE.code(), 15, Registers.E.address(),                       // 23
            OPCode.CHANGE_PAGE_JUMP.code(), Registers.F.address(), Registers.E.address() // 26
    };

    /**
     * Second page of the paged-memory example. It stores {@code 22} and exercises both outcomes
     * of the conditional page-jump instruction.
     */
    static final byte[] PROGRAM_PAGED_MEMORY_PAGE_1 = new byte[] {
            OPCode.STORE.code(), 22, Registers.A.address(),                         // 0
            OPCode.STORE_MEM.code(), Registers.A.address(), 120,                  // 3
            OPCode.STORE.code(), 127, Registers.F.address(),                      // 6
            OPCode.STORE.code(), 0, Registers.E.address(),                       // 9
            OPCode.CHANGE_PAGE_JUMP.code(), Registers.F.address(), Registers.E.address(), // 12
            OPCode.LOAD_MEM.code(), Registers.A.address(), 120,                   // 15
            OPCode.OUTPUT.code(), Registers.A.address(),                          // 18
            OPCode.STORE.code(), 0, Registers.B.address(),                        // 20
            OPCode.STORE.code(), 127, Registers.F.address(),                      // 23
            OPCode.CHANGE_PAGE_COND_JUMP.code(), Registers.F.address(),
                    Registers.A.address(), Registers.B.address(), 15,            // 26: false, continue at 31
            OPCode.STORE.code(), 22, Registers.B.address(),                       // 31
            OPCode.CHANGE_PAGE_COND_JUMP.code(), Registers.F.address(),
                    Registers.A.address(), Registers.B.address(), 15,            // 34: true, page 127 address 15
            OPCode.STOP.code()                                                   // 39: stop if value was incorrect
    };

    /**
     * Final page of the paged-memory example. It stores {@code 33}, prints the values stored on
     * this page, and stops execution after the earlier pages have printed their values.
     */
    static final byte[] PROGRAM_PAGED_MEMORY_PAGE_127 = new byte[] {
            OPCode.STORE.code(), 33, Registers.A.address(),                         // 0
            OPCode.STORE_MEM.code(), Registers.A.address(), 120,                  // 3
            OPCode.STORE.code(), 0, Registers.F.address(),                      // 6
            OPCode.STORE.code(), 15, Registers.E.address(),                       // 9
            OPCode.CHANGE_PAGE_JUMP.code(), Registers.F.address(), Registers.E.address(), // 12
            OPCode.LOAD_MEM.code(), Registers.A.address(), 120,                   // 15
            OPCode.OUTPUT.code(), Registers.A.address(),                          // 18
            OPCode.STOP.code()                                                   // 20
    };

    /**
     * Counts register A from {@code 1} through {@code 127}; register D tracks the loop count.
     * The program currently performs two full passes and prints {@code 1} once more before stopping.
     */
    static byte[] PROGRAM_LOOP_127_3 = new byte[] {
            OPCode.STORE.code(), 0, Registers.A.address(),                                          // 3, 2, 24bits  | Line 1
            OPCode.STORE.code(), 1, Registers.B.address(),                                          // 3, 5, 24bits  | Line 2
            OPCode.STORE.code(), 127, Registers.C.address(),                                        // 3, 8, 24bits  | Line 3
            OPCode.STORE.code(), 0, Registers.D.address(),                                          // 3, 11, 24bits | Line 4
            OPCode.STORE.code(), 2, Registers.E.address(),                                          // 3, 14, 24bits | Line 5
            OPCode.ADD.code(), Registers.A.address(), Registers.B.address(), Registers.A.address(), // 4, 18, 32bits | Line 6
            OPCode.OUTPUT.code(), Registers.A.address(),                                            // 2, 20, 16bits | Line 7
            OPCode.COND_JUMP.code(), Registers.A.address(), Registers.C.address(), 32,              // 4, 24, 32bits | Line 8
            OPCode.COND_JUMP.code(), Registers.D.address(), Registers.E.address(), 31,              // 4, 28, 32bits | Line 9
            OPCode.JMP.code(), 15,                                                                  // 2, 30, 16bits | Line 10
            OPCode.STOP.code(),                                                                     // 1, 31, 8bits  | Line 11
            OPCode.ADD.code(), Registers.D.address(), Registers.B.address(), Registers.D.address(), // 4, 35, 32bits | Line 12
            OPCode.STORE.code(), 0, Registers.A.address(),                                          // 3, 38, 24bits | Line 13
            OPCode.JMP.code(), 15                                                                   // 2, 40, 16bits | Line 14
    };
}
