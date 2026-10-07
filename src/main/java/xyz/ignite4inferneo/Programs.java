/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

public class Programs {
    public enum Program {
        HELLO_CPU,
        LOOP_127_3,
        PAGED_MEMORY
    }

    public static void load(Program program) {
        switch (program) {
            case HELLO_CPU -> upload(PROGRAM_HELLO_CPU, (byte) 0);
            case LOOP_127_3 -> upload(PROGRAM_LOOP_127_3, (byte) 0);
            case PAGED_MEMORY -> {
                upload(PROGRAM_PAGED_MEMORY_PAGE_0, (byte) 0);
                upload(PROGRAM_PAGED_MEMORY_PAGE_1, (byte) 1);
                upload(PROGRAM_PAGED_MEMORY_PAGE_127, (byte) 127);
            }
        }
        Main.REGISTERS[Registers.PAGE.address()] = 0;
        Main.REGISTERS[Registers.COUNTER.address()] = 0;
        IO.println(program + " UPLOAD to MEMORY");
    }

    private static void upload(byte[] program, byte page) {
        System.arraycopy(program, 0, Memory.getMemoryPage(page), 0, program.length);
    }

    // Calculate ASCII values with ADD/SUB; OUTPUT prints "hello I'm a cpu"
    // as decimal bytes. The 117-byte program fits in one 128-byte page.
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

    /*
    Writes 11, 22, 33 at address 120 on pages 0, 1, 127, then revisits
    each page and prints its value. All values and addresses fit signed bytes.
    CHANGE_PAGE_JUMP takes page and target REGISTERS (F and E).
    CHANGE_PAGE_COND_JUMP takes a page register, two comparison registers,
    and a literal target address. Page 1 exercises both false and true cases.
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

    /*
    This Program is for counting to 127 on Register A looping x times with Register D and the stops execution.
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
