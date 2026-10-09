/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Provides the sample bytecode programs that can be loaded into virtual memory. */
public class Programs {
    /** Names the built-in programs supported by {@link #load(Program)}. */
    public enum Program {
        BOS_POST,
        HELLO_CPU,
        DISPLAY_HELLO_CPU,
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
            case BOS_POST -> uploadPages(PROGRAM_BOS_POST);
            case HELLO_CPU -> upload(PROGRAM_HELLO_CPU, (byte) 0);
            case DISPLAY_HELLO_CPU -> uploadPages(PROGRAM_DISPLAY_HELLO_CPU);
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

    /** Copies consecutive program pages into virtual memory starting at page zero. */
    private static void uploadPages(byte[][] pages) {
        for (int page = 0; page < pages.length; page++) {
            upload(pages[page], (byte) page);
        }
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

    private static byte[][] createBosPostProgram() {
        int payloadFirstPage = 0;
        int snowfallFirstPage = 0;
        byte[][] bosPages;
        byte[][] payloadPages;
        do {
            bosPages = createBosPostPages(payloadFirstPage);
            int requiredPayloadFirstPage = bosPages.length;
            payloadPages = createDisplayHelloCpuProgram(requiredPayloadFirstPage, snowfallFirstPage);
            int requiredSnowfallFirstPage = requiredPayloadFirstPage + payloadPages.length;
            if (requiredPayloadFirstPage == payloadFirstPage
                    && requiredSnowfallFirstPage == snowfallFirstPage) {
                break;
            }
            payloadFirstPage = requiredPayloadFirstPage;
            snowfallFirstPage = requiredSnowfallFirstPage;
        } while (true);

        byte[][] image = Arrays.copyOf(bosPages, bosPages.length + payloadPages.length + 1);
        System.arraycopy(payloadPages, 0, image, bosPages.length, payloadPages.length);
        image[snowfallFirstPage] = PROGRAM_SNOWFALL;
        return image;
    }

    private static byte[][] createBosPostPages(int payloadFirstPage) {
        TextProgramBuilder program = new TextProgramBuilder(0);
        program.instruction(OPCode.CLEAR_GRA.code());
        program.instruction(OPCode.STORE.code(), 0, Registers.H.address());

        // Exercise the processor and graphics readback before reporting POST. Page transitions are
        // exercised by the generated display code itself as it crosses the BOS page boundaries.
        program.instruction(OPCode.STORE.code(), 0x55, Registers.A.address());
        program.instruction(OPCode.STORE.code(), 0x55, Registers.B.address());
        program.instruction(OPCode.ADD.code(), Registers.A.address(), Registers.B.address(), Registers.C.address());
        program.instruction(OPCode.STORE.code(), 126, Registers.A.address());
        program.instruction(OPCode.STORE.code(), 126, Registers.B.address());
        program.instruction(OPCode.STORE.code(), 0b00_111_00, Registers.C.address());
        program.instruction(OPCode.PUSH_GRA_MEM.code(), Registers.A.address(), Registers.B.address(), Registers.C.address());
        program.instruction(OPCode.READ_GRA_MEM.code(), Registers.A.address(), Registers.B.address(), Registers.D.address());

        drawText(program, "JAVAVCPU BOS", 20, 8, 2, 0b00_111_11);
        drawText(program, "POWER ON SELF TEST", 8, 26, 1, 0b11_111_00);
        drawText(program, "CPU OK", 18, 43, 1, 0b00_111_00);
        drawText(program, "PAGE MAP OK", 18, 55, 1, 0b00_111_00);
        drawText(program, "DISPLAY OK", 18, 67, 1, 0b00_111_00);
        drawText(program, "POST PASSED", 20, 84, 2, 0b00_111_00);
        drawText(program, "LOADING PROGRAM", 31, 106, 1, 0b00_111_11);
        // Keep the presented POST frame on screen for about one second before booting the payload.
        for (int frame = 0; frame < 1_000; frame++) {
            program.instruction(OPCode.DISPLAY.code());
        }
        program.instruction(OPCode.STORE.code(), 0, Registers.H.address());
        program.instruction(OPCode.STORE.code(), payloadFirstPage, Registers.G.address());
        program.instruction(OPCode.CHANGE_PAGE_JUMP.code(), Registers.G.address(), Registers.H.address());
        return program.finish();
    }

    /** Computes {@code "hello I'm a cpu"}, then draws it with existing graphics bytecode. */
    static final byte[][] PROGRAM_DISPLAY_HELLO_CPU = createDisplayHelloCpuProgram(0, -1);

    private static byte[][] createDisplayHelloCpuProgram(int firstPage, int snowfallFirstPage) {
        List<byte[]> pages = new ArrayList<>();
        pages.add(computeHelloCpuPage(firstPage + 1));

        TextProgramBuilder program = new TextProgramBuilder(firstPage + 1);
        program.instruction(OPCode.CLEAR_GRA.code());
        program.instruction(OPCode.STORE.code(), 0b11_111_11, Registers.C.address()); // white
        program.instruction(OPCode.STORE.code(), 0, Registers.H.address()); // page-zero address

        int x = 17;
        for (char character : "hello I'm a cpu".toCharArray()) {
            String glyph = glyph(character);
            if (glyph == null) {
                x += 2;
                continue;
            }
            String[] rows = glyph.split("/");
            for (int row = 0; row < rows.length; row++) {
                program.instruction(OPCode.STORE.code(), 55 + row, Registers.B.address());
                for (int column = 0; column < rows[row].length(); column++) {
                    if (rows[row].charAt(column) == '1') {
                        program.instruction(OPCode.STORE.code(), x + column, Registers.A.address());
                        program.instruction(OPCode.PUSH_GRA_MEM.code(), Registers.A.address(),
                                Registers.B.address(), Registers.C.address());
                    }
                }
            }
            x += 4;
        }
        if (snowfallFirstPage < 0) {
            program.instruction(OPCode.DISPLAY.code());
            program.instruction(OPCode.STOP.code());
        } else {
            // Leave the completed greeting visible before transferring to the animation page.
            for (int frame = 0; frame < 1_000; frame++) {
                program.instruction(OPCode.DISPLAY.code());
            }
            program.instruction(OPCode.STORE.code(), 0, Registers.H.address());
            program.instruction(OPCode.STORE.code(), snowfallFirstPage, Registers.G.address());
            program.instruction(OPCode.CHANGE_PAGE_JUMP.code(), Registers.G.address(), Registers.H.address());
        }
        pages.addAll(Arrays.asList(program.finish()));
        return pages.toArray(byte[][]::new);
    }

    /** Replaces HELLO_CPU's stop instruction with a jump to address zero on the display page. */
    private static byte[] computeHelloCpuPage(int displayFirstPage) {
        int stopAddress = PROGRAM_HELLO_CPU.length - 1;
        byte[] page = Arrays.copyOf(PROGRAM_HELLO_CPU, PROGRAM_HELLO_CPU.length + 8);
        page[stopAddress] = OPCode.STORE.code();
        page[stopAddress + 1] = 0;
        page[stopAddress + 2] = Registers.H.address();
        page[stopAddress + 3] = OPCode.STORE.code();
        page[stopAddress + 4] = (byte) displayFirstPage;
        page[stopAddress + 5] = Registers.G.address();
        page[stopAddress + 6] = OPCode.CHANGE_PAGE_JUMP.code();
        page[stopAddress + 7] = Registers.G.address();
        page[stopAddress + 8] = Registers.H.address();
        return page;
    }

    /** Returns a three-by-five glyph layout for the bytecode pixel writer. */
    private static String glyph(char character) {
        return switch (Character.toUpperCase(character)) {
            case 'A' -> "010/101/111/101/101";
            case 'C' -> "111/100/100/100/111";
            case 'E' -> "111/100/110/100/111";
            case 'G' -> "111/100/101/101/111";
            case 'H' -> "101/101/111/101/101";
            case 'I' -> "111/010/010/010/111";
            case 'K' -> "101/101/110/101/101";
            case 'L' -> "100/100/100/100/111";
            case 'M' -> "101/111/111/101/101";
            case 'O' -> "111/101/101/101/111";
            case 'P' -> "110/101/110/100/100";
            case 'U' -> "101/101/101/101/111";
            case 'W' -> "101/101/111/111/101";
            case '\'' -> "010/010/000/000/000";
            case ' ' -> null;
            case 'B' -> "110/101/110/101/110";
            case 'D' -> "110/101/101/101/110";
            case 'F' -> "111/100/110/100/100";
            case 'J' -> "001/001/001/101/010";
            case 'N' -> "101/111/111/111/101";
            case 'R' -> "110/101/110/101/101";
            case 'S' -> "111/100/111/001/111";
            case 'T' -> "111/010/010/010/010";
            case 'V' -> "101/101/101/101/010";
            case 'Y' -> "101/101/010/010/010";
            case '0' -> "111/101/101/101/111";
            case '1' -> "010/110/010/010/111";
            case '2' -> "110/001/010/100/111";
            case '3' -> "110/001/010/001/110";
            case '4' -> "101/101/111/001/001";
            case '5' -> "111/100/110/001/110";
            case '6' -> "011/100/111/101/111";
            case '7' -> "111/001/010/010/010";
            case '8' -> "111/101/111/101/111";
            case '9' -> "111/101/111/001/110";
            default -> throw new IllegalArgumentException("No BOS glyph for: " + character);
        };
    }

    /** Emits pixel-writing bytecode for a three-by-five text string. */
    private static void drawText(
            TextProgramBuilder program,
            String text,
            int x,
            int y,
            int scale,
            int color
    ) {
        program.instruction(OPCode.STORE.code(), color, Registers.C.address());
        for (char character : text.toCharArray()) {
            String glyph = glyph(character);
            if (glyph != null) {
                String[] rows = glyph.split("/");
                for (int row = 0; row < rows.length; row++) {
                    for (int column = 0; column < rows[row].length(); column++) {
                        if (rows[row].charAt(column) == '1') {
                            for (int yOffset = 0; yOffset < scale; yOffset++) {
                                for (int xOffset = 0; xOffset < scale; xOffset++) {
                                    program.instruction(OPCode.STORE.code(), x + column * scale + xOffset,
                                            Registers.A.address());
                                    program.instruction(OPCode.STORE.code(), y + row * scale + yOffset,
                                            Registers.B.address());
                                    program.instruction(OPCode.PUSH_GRA_MEM.code(), Registers.A.address(),
                                            Registers.B.address(), Registers.C.address());
                                }
                            }
                        }
                    }
                }
            }
            x += 4 * scale;
        }
    }

    /** Builds fixed-size pages of existing bytecode instructions for the display-text program. */
    private static final class TextProgramBuilder {
        private static final int PAGE_SIZE = 256;
        private static final int PAGE_JUMP_SIZE = 6;
        private final int firstPage;
        private final List<byte[]> pages = new ArrayList<>();
        private ByteArrayOutputStream page = new ByteArrayOutputStream(PAGE_SIZE);

        TextProgramBuilder(int firstPage) {
            this.firstPage = firstPage;
        }

        void instruction(int... bytes) {
            if (page.size() + bytes.length + PAGE_JUMP_SIZE > PAGE_SIZE) {
                page.write(OPCode.STORE.code());
                page.write(firstPage + pages.size() + 1);
                page.write(Registers.G.address());
                page.write(OPCode.CHANGE_PAGE_JUMP.code());
                page.write(Registers.G.address());
                page.write(Registers.H.address());
                pages.add(page.toByteArray());
                page = new ByteArrayOutputStream(PAGE_SIZE);
            }
            for (int value : bytes) {
                page.write(value);
            }
        }

        byte[][] finish() {
            pages.add(page.toByteArray());
            return pages.toArray(byte[][]::new);
        }
    }

    /**
     * Animates a white snowflake that falls until it reaches the bottom or an occupied pixel.
     * Settled flakes remain in the framebuffer, building a snowbank across all 128 columns.
     */
    static final byte[] PROGRAM_SNOWFALL = new byte[] {
            OPCode.CLEAR_GRA.code(),                                                   // 0
            OPCode.STORE.code(), 0, Registers.A.address(),                             // 1: current row
            OPCode.STORE.code(), 0, Registers.B.address(),                             // 4: current column
            OPCode.STORE.code(), 1, Registers.C.address(),                             // 7: increment
            OPCode.STORE.code(), 0b11_111_11, Registers.D.address(),                   // 10: white
            OPCode.STORE.code(), 127, Registers.G.address(),                           // 13: final coordinate
            OPCode.STORE.code(), 0, Registers.E.address(),                             // 16: black

            OPCode.STORE.code(), 0b11_111_11, Registers.D.address(),                   // 19: restore white
            OPCode.PUSH_GRA_MEM.code(), Registers.B.address(), Registers.A.address(),
                    Registers.D.address(),                                              // 22: draw falling flake
            OPCode.COND_JUMP.code(), Registers.A.address(), Registers.G.address(), 42, // 26: bottom row
            OPCode.ADD.code(), Registers.A.address(), Registers.C.address(), Registers.F.address(), // 30: row below
            OPCode.READ_GRA_MEM.code(), Registers.B.address(), Registers.F.address(),
                    Registers.D.address(),                                              // 34: color below
            OPCode.COND_JUMP.code(), Registers.D.address(), Registers.E.address(), 61, // 38: fall if black

            OPCode.STORE.code(), 0, Registers.A.address(),                             // 42: settle and respawn
            OPCode.COND_JUMP.code(), Registers.B.address(), Registers.G.address(), 55, // 45: reset column after 127
            OPCode.ADD.code(), Registers.B.address(), Registers.C.address(), Registers.B.address(), // 49
            OPCode.JMP.code(), 58,                                                      // 53
            OPCode.STORE.code(), 0, Registers.B.address(),                             // 55
            OPCode.DISPLAY.code(),                                                      // 58
            OPCode.JMP.code(), 19,                                                      // 59

            OPCode.SUB.code(), Registers.F.address(), Registers.C.address(), Registers.A.address(), // 61: restore current row
            OPCode.PUSH_GRA_MEM.code(), Registers.B.address(), Registers.A.address(),
                    Registers.E.address(),                                              // 65: erase previous flake position
            OPCode.ADD.code(), Registers.A.address(), Registers.C.address(), Registers.A.address(), // 69: move down
            OPCode.STORE.code(), 0b11_111_11, Registers.D.address(),                   // 73
            OPCode.PUSH_GRA_MEM.code(), Registers.B.address(), Registers.A.address(),
                    Registers.D.address(),                                              // 76: draw moved flake
            OPCode.JMP.code(), 58                                                       // 80
    };

    /**
     * Boot program loaded at page zero. Its POST screen occupies consecutive pages and transfers
     * to the display demo, which in turn starts the snowfall program on its own page.
     *
     * <p>This declaration follows {@link #PROGRAM_SNOWFALL} so the boot image can include the
     * fully initialized snowfall bytecode.</p>
     */
    static final byte[][] PROGRAM_BOS_POST = createBosPostProgram();

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
