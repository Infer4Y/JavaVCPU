/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

import java.io.IOException;

/**
 * Executes JavaVCPU bytecode and owns the virtual CPU state.
 *
 * <p>Instructions use operand bytes as indices into {@link #REGISTERS}, unless their opcode
 * documents an operand as a literal value or address. Memory accesses operate on the page selected
 * by {@link Registers#PAGE}, except instructions that explicitly take a page register.</p>
 */
public class Main {
    /**
     * The CPU register file: A through H are general-purpose registers, followed by the instruction
     * counter and active memory page.
     */
    static byte[] REGISTERS = new byte[] {
            0, // A
            0, // B
            0, // C
            0, // D
            0, // E
            0, // F
            0, // G
            0, // H
            0, // COUNTER
            0, // MEMORY PAGE
            0  // STORAGE PAGE
        };

    /** The currently selected 256-byte memory page. */
    static byte[] MEMORY = new byte[256];

    /** Whether the instruction-dispatch loop should continue executing. */
    static boolean running = true;

    /** Wall-clock time recorded immediately before program execution begins. */
    static long cpuStartTime;

    /** Number of instructions dispatched during the current run. */
    static long intructs;

    /**
     * Initializes the devices, loads the BIOS boot image into virtual memory, and executes it.
     *
     * @throws InterruptedException if interrupted while executing the virtual CPU
     */
    public static void main(String[] args) throws InterruptedException, IOException {
        Monitor.initialize();
        Audio.initialize();
        Input.initialize();
        Storage.load();
        DiskInstaller.installIfMissing();

        cpuStartTime = System.currentTimeMillis();

        DiskInstaller.boot();

        while (running) {
            int counter = getProgramCounter();
            MEMORY = Memory.getMemoryPage(REGISTERS[Registers.PAGE.address()]);
            byte opcode = MEMORY[counter];

            switch (opcode) {
                // ADD registerA, registerB, destinationRegister
                case 0 -> add(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);

                // SUB registerA, registerB, destinationRegister
                case 1 -> sub(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);

                // STORE literalValue, destinationRegister
                case 2 -> store(MEMORY[counter + 1], MEMORY[counter + 2]);

                // JMP literalAddress
                case 3 -> jump(MEMORY[counter + 1]);

                case 4 -> stop();

                // OUTPUT register
                case 5 -> output(MEMORY[counter + 1]);

                // COND_JUMP registerA, registerB, literalAddress
                case 6 -> cond_jump(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);

                // LOAD_MEM destinationRegister, literalAddress
                case 7 -> loadMemory(MEMORY[counter + 1], MEMORY[counter + 2]);

                // STORE_MEM sourceRegister, literalAddress
                case 8 -> storeMemory(MEMORY[counter + 1], MEMORY[counter + 2]);

                // CHANGE_PAGE pageRegister
                case 9 -> changePage(MEMORY[counter + 1]);

                // CHANGE_PAGE_JUMP pageRegister, targetRegister
                case 10 -> changePageJump(MEMORY[counter + 1], MEMORY[counter + 2]);

                // CHANGE_PAGE_COND_JUMP pageRegister, registerA, registerB, literalAddress
                case 11 -> changePageCondJump(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3], MEMORY[counter + 4] );

                // PUSH_GRA_MEM xRegister, yRegister, colorRegister
                case 12 -> pushGraphicsMemory(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);

                case 13 -> clearGraphics();

                case  14 -> display();

                // READ_GRA_MEM xRegister, yRegister, destinationRegister
                case 15 -> pullGraphicsMemory(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);

                // READ_INPUT deviceId, destinationRegister
                case 16 -> readInput(MEMORY[counter + 1], MEMORY[counter + 2]);

                // OUTPUT_AUDIO voiceRegister, midiNoteRegister, controlRegister
                case 17 -> outputAudio(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);

                // READ_BYTE_FROM_STORAGE xRegister, yRegister, zRegister, destinationRegister
                case 18 -> readStorage(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3], MEMORY[counter + 4]);

                case 19 -> writeStorage(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3], MEMORY[counter + 4]);

                // MULTIPLY registerA, registerB, targetRegister
                case 20 -> multiply(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);

                // INCREMENT registerA
                case 21 -> incrementRegister(MEMORY[counter + 1]);

                // STORE_MEM_ON_PAGE sourceRegister, pageRegister, addressRegister
                case 22 -> storeMemoryOnPage(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);

                default -> throw new IllegalStateException(
                        "Unknown opcode: " + opcode +
                                " at address " + counter
                );
            }

            intructs++;
        }
    }

    /** Returns the program counter as an unsigned address in the current page. */
    static int getProgramCounter() {
        return Byte.toUnsignedInt(
                REGISTERS[Registers.COUNTER.address()]
        );
    }

    /** Advances the program counter by an instruction length. */
    static void incrementCounter(int amount) {
        int counter = getProgramCounter();
        counter += amount;

        REGISTERS[Registers.COUNTER.address()] = (byte) counter;
    }

    /** Adds two registers, stores the wrapped byte result, and advances the counter. */
    public static void add(
            byte a,
            byte b,
            byte register
    ) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        int Register = Byte.toUnsignedInt(register);

        REGISTERS[Register] = (byte) (REGISTERS[B] + REGISTERS[A]);

        incrementCounter(OPCode.ADD.length());
    }

    /** Subtracts one register from another, stores the wrapped byte result, and advances the counter. */
    public static void sub(
            byte a,
            byte b,
            byte register
    ) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        int Register = Byte.toUnsignedInt(register);

        REGISTERS[Register] = (byte) (REGISTERS[A] - REGISTERS[B]);

        incrementCounter(OPCode.SUB.length());
    }

    /** Stores a literal byte in a register and advances the counter. */
    public static void store(
            byte value,
            byte register
    ) {
        int Register = Byte.toUnsignedInt(register);

        REGISTERS[Register] = value;

        incrementCounter(OPCode.STORE.length());
    }

    /** Sets the program counter to a literal address. */
    public static void jump(byte address) {
        REGISTERS[Registers.COUNTER.address()] = address;
    }

    /** Jumps to a literal address when two registers contain equal values. */
    public static void cond_jump(byte a, byte b, byte address) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        if (REGISTERS[A] == REGISTERS[B]) {
            REGISTERS[Registers.COUNTER.address()] = address;
        } else {
            incrementCounter(OPCode.COND_JUMP.length());
        }
    }

    /** Stops execution and reports elapsed CPU time and the instruction count. */
    static void stop(){
        running = false;
        Audio.shutdown();
        IO.println("EOF CPU Ran for " + (System.currentTimeMillis() - cpuStartTime) + "ms | " + intructs + " instructions ran");
    }

    /** Prints a register's signed byte value and advances the counter. */
    public static void output(byte register) {
        int Register = Byte.toUnsignedInt(register);

        IO.println(REGISTERS[Register]);

        incrementCounter(OPCode.OUTPUT.length());
    }

    /** Loads a byte from a literal address in the active page into a register. */
    public static void loadMemory(byte a, byte b) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);

        REGISTERS[A] = MEMORY[B];

        incrementCounter(OPCode.LOAD_MEM.length());
    }

    /** Stores a register value at a literal address in the active page. */
    public static void storeMemory(byte a, byte b) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);

        MEMORY[B] = REGISTERS[A];

        incrementCounter(OPCode.STORE_MEM.length());
    }

    /** Stores a register value at a register-selected address on a register-selected page. */
    public static void storeMemoryOnPage(byte value, byte page, byte address) {
        int valueRegister = Byte.toUnsignedInt(value);
        int pageRegister = Byte.toUnsignedInt(page);
        int addressRegister = Byte.toUnsignedInt(address);

        Memory.getMemoryPage(REGISTERS[pageRegister])[Byte.toUnsignedInt(REGISTERS[addressRegister])]
                = REGISTERS[valueRegister];

        incrementCounter(OPCode.STORE_MEM_ON_PAGE.length());
    }

    /** Selects the page stored in a register and advances the counter. */
    public static void changePage(byte a){

        int A = Byte.toUnsignedInt(a);

        REGISTERS[Registers.PAGE.address()] = REGISTERS[A];

        incrementCounter(OPCode.CHANGE_PAGE.length());
    }

    /** Selects a page and sets the counter from a second register. */
    public static void changePageJump(byte a, byte b){
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);

        REGISTERS[Registers.PAGE.address()] = REGISTERS[A];
        REGISTERS[Registers.COUNTER.address()] = REGISTERS[B];
    }

    /** Selects a page and jumps when two registers contain equal values. */
    public static void changePageCondJump(byte a, byte b, byte c, byte address) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        int C = Byte.toUnsignedInt(c);

        if (REGISTERS[B] == REGISTERS[C]) {
            REGISTERS[Registers.PAGE.address()] = REGISTERS[A];
            REGISTERS[Registers.COUNTER.address()] = address;
        } else {
            incrementCounter(OPCode.CHANGE_PAGE_COND_JUMP.length());
        }
    }

    /** Clears the virtual display and advances the counter. */
    public static void clearGraphics() {
        Monitor.clear();

        incrementCounter(OPCode.CLEAR_GRA.length());
    }

    /**
     * Presents the virtual display framebuffer and advances the counter.
     */
    public static void display() throws InterruptedException {
        Monitor.display();

        Thread.sleep(33);

        incrementCounter(OPCode.DISPLAY.length());
    }

    /** Writes a pixel using x, y, and color values from three registers, then advances the counter. */
    public static void pushGraphicsMemory(byte a, byte b, byte color) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        int Color = Byte.toUnsignedInt(color);

        Monitor.pushMemory(REGISTERS[A], REGISTERS[B], REGISTERS[Color]);

        incrementCounter(OPCode.PUSH_GRA_MEM.length());
    }

    /**
     * Reads the palette value at coordinates held in two registers into a destination register,
     * then advances the counter.
     */
    public static void pullGraphicsMemory(byte a, byte b, byte target) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        int Target = Byte.toUnsignedInt(target);

        REGISTERS[Target] = Monitor.fetchMemory(REGISTERS[A], REGISTERS[B]);

        incrementCounter(OPCode.READ_GRA_MEM.length());
    }

    public static void readInput(byte device, byte target) {
        int targetRegister = Byte.toUnsignedInt(target);

        REGISTERS[targetRegister] = Input.read(device);

        incrementCounter(OPCode.READ_INPUT.length());
    }

    public static void outputAudio(byte voice, byte midiNote, byte control) {
        int voiceRegister = Byte.toUnsignedInt(voice);
        int noteRegister = Byte.toUnsignedInt(midiNote);
        int controlRegister = Byte.toUnsignedInt(control);

        Audio.setVoice(
                Byte.toUnsignedInt(REGISTERS[voiceRegister]),
                Byte.toUnsignedInt(REGISTERS[noteRegister]),
                Byte.toUnsignedInt(REGISTERS[controlRegister])
        );

        incrementCounter(OPCode.OUTPUT_AUDIO.length());
    }

    public static void readStorage(byte a, byte b, byte c, byte target) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        int C = Byte.toUnsignedInt(c);
        int Target = Byte.toUnsignedInt(target);

        REGISTERS[Target] = Storage.readData(REGISTERS[A], REGISTERS[B], REGISTERS[C]);

        incrementCounter(OPCode.READ_BYTE_FROM_STORAGE.length());
    }

    public static void writeStorage(byte a, byte b, byte c, byte target) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        int C = Byte.toUnsignedInt(c);
        int Target = Byte.toUnsignedInt(target);

        Storage.writeData(REGISTERS[A], REGISTERS[B], REGISTERS[C], REGISTERS[Target]);

        incrementCounter(OPCode.WRITE_BYTE_TO_STORAGE.length());
    }

    public static void multiply(byte a, byte b, byte target){
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        int Target = Byte.toUnsignedInt(target);

        REGISTERS[Target] = (byte) (REGISTERS[A] * REGISTERS[B]);

        incrementCounter(OPCode.MULTIPLY.length());
    }

    public static void incrementRegister(byte a) {
        int A = Byte.toUnsignedInt(a);

        REGISTERS[A]++;

        incrementCounter(OPCode.INCREMENT_REGISTER.length());
    }

}
