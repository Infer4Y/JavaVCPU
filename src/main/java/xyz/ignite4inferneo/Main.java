/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

import java.io.IOException;
import java.math.BigInteger;
import java.text.NumberFormat;

/**
 * Executes JavaVCPU bytecode and owns the virtual CPU state.
 *
 * <p>Instructions use operand bytes as indices into {@link #REGISTERS}, unless their opcode
 * documents an operand as a literal value or address. Memory accesses operate on the page selected
 * by {@link Registers#MEMORY_BANK} and {@link Registers#PAGE}, except instructions that explicitly take a page register.</p>
 */
public class Main {
    /**
     * The CPU register file has sixteen general-purpose registers (A-P) and four control registers.
     * I-P follow the control registers so existing bytecode keeps its original register indices.
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
            0, // STORAGE DEVICE (legacy STORAGE_PAGE name)
            0, // MEMORY BANK
            0, // I
            0, // J
            0, // K
            0, // L
            0, // M
            0, // N
            0, // O
            0  // P
        };

    /** The currently selected 256-byte memory page. */
    static byte[] MEMORY = new byte[256];

    /** Whether the instruction-dispatch loop should continue executing. */
    static boolean running = true;

    /** Wall-clock time recorded immediately before program execution begins. */
    static long cpuStartTime;

    /** Number of instructions dispatched during the current run. */
    static BigInteger intructs = new BigInteger("0");

    /**
     * Initializes the devices, loads the BIOS boot image into virtual memory, and executes it.
     *
     * @throws InterruptedException if interrupted while executing the virtual CPU
     */
    static void main(String[] args) throws InterruptedException, IOException {
        Monitor.initialize();
        Audio.initialize();
        Input.initialize();
        Storage.load();
        for (String arg : args) {
            if (!arg.startsWith("--disk=")) throw new IllegalArgumentException("Expected --disk=ID=PATH");
            String[] disk = arg.substring(7).split("=", 2);
            if (disk.length != 2) throw new IllegalArgumentException("Expected --disk=ID=PATH");
            int id = Integer.parseInt(disk[0]);
            if (id == 0) throw new IllegalArgumentException("Device 0 uses storage.img");
            Storage.mount(id, java.nio.file.Path.of(disk[1]));
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Network.close();
            try { Storage.save(); } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        }, "storage-flush"));
        // Install/update the managed JCTRL and TEXTEDIT packages before booting.
        DiskInstaller.installIfMissing();

        cpuStartTime = System.currentTimeMillis();

        DiskInstaller.boot();

        while (running) {
            int counter = getProgramCounter();
            MEMORY = Memory.getMemoryPage(REGISTERS[Registers.MEMORY_BANK.address()],
                    REGISTERS[Registers.PAGE.address()]);
            byte opcode = MEMORY[counter];

            assert (Byte.toUnsignedInt(opcode) < OPCode.values().length);
            assert (opcode >= 0);

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

                case 23 -> changeBankPageJump(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);
                case 24 -> loadMemoryOnBank(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3], MEMORY[counter + 4]);
                case 25 -> storeMemoryOnBank(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3], MEMORY[counter + 4]);

                case 26 -> compareUnsigned(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3]);

                case 27 -> loadMemoryAbsolute(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3], MEMORY[counter + 4]);
                case 28 -> storeMemoryAbsolute(MEMORY[counter + 1], MEMORY[counter + 2], MEMORY[counter + 3], MEMORY[counter + 4]);

                case 29 -> readNetwork(MEMORY[counter + 1], MEMORY[counter + 2]);
                case 30 -> writeNetwork(MEMORY[counter + 1], MEMORY[counter + 2]);

                default -> throw new IllegalStateException(
                        "Unknown opcode: " + opcode +
                                " at address " + counter
                );
            }

            //if (opcode != OPCode.DISPLAY.code()) Thread.sleep(0,1000);

            intructs = intructs.add(BigInteger.ONE);
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
    static void add(
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
    static void sub(
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
    static void store(
            byte value,
            byte register
    ) {
        int Register = Byte.toUnsignedInt(register);

        REGISTERS[Register] = value;

        incrementCounter(OPCode.STORE.length());
    }

    /** Sets the program counter to a literal address. */
    static void jump(byte address) {
        REGISTERS[Registers.COUNTER.address()] = address;
    }

    /** Jumps to a literal address when two registers contain equal values. */
    static void cond_jump(byte a, byte b, byte address) {
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
        IO.println("EOF CPU Ran for " + (System.currentTimeMillis() - cpuStartTime) + "ms | " + NumberFormat.getInstance().format(intructs) + " instructions ran");
    }

    /** Prints a register's signed byte value and advances the counter. */
    static void output(byte register) {
        int Register = Byte.toUnsignedInt(register);

        IO.println(REGISTERS[Register]);

        incrementCounter(OPCode.OUTPUT.length());
    }

    /** Loads a byte from a literal address in the active page into a register. */
    static void loadMemory(byte a, byte b) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);

        REGISTERS[A] = MEMORY[B];

        incrementCounter(OPCode.LOAD_MEM.length());
    }

    /** Stores a register value at a literal address in the active page. */
    static void storeMemory(byte a, byte b) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);

        MEMORY[B] = REGISTERS[A];

        incrementCounter(OPCode.STORE_MEM.length());
    }

    /** Stores a register value at a register-selected address on a register-selected page. */
    static void storeMemoryOnPage(byte value, byte page, byte address) {
        int valueRegister = Byte.toUnsignedInt(value);
        int pageRegister = Byte.toUnsignedInt(page);
        int addressRegister = Byte.toUnsignedInt(address);

        Memory.getMemoryPage(REGISTERS[Registers.MEMORY_BANK.address()], REGISTERS[pageRegister])[Byte.toUnsignedInt(REGISTERS[addressRegister])]
                = REGISTERS[valueRegister];

        incrementCounter(OPCode.STORE_MEM_ON_PAGE.length());
    }

    static void loadMemoryAbsolute(byte bank, byte page, byte offset, byte destination) {
        REGISTERS[Byte.toUnsignedInt(destination)] = Memory.getMemoryPage(bank, page)[Byte.toUnsignedInt(offset)];
        incrementCounter(OPCode.LOAD_MEM_ABSOLUTE.length());
    }

    static void storeMemoryAbsolute(byte value, byte bank, byte page, byte offset) {
        Memory.getMemoryPage(bank, page)[Byte.toUnsignedInt(offset)] = REGISTERS[Byte.toUnsignedInt(value)];
        incrementCounter(OPCode.STORE_MEM_ABSOLUTE.length());
    }

    static void compareUnsigned(byte left, byte right, byte destination) {
        int comparison = Integer.compare(Byte.toUnsignedInt(REGISTERS[Byte.toUnsignedInt(left)]),
                Byte.toUnsignedInt(REGISTERS[Byte.toUnsignedInt(right)]));
        REGISTERS[Byte.toUnsignedInt(destination)] = (byte) (comparison == 0 ? 0 : comparison < 0 ? 1 : 2);
        incrementCounter(OPCode.COMPARE_UNSIGNED.length());
    }

    /** Atomically selects bank, page and instruction offset from operand registers. */
    static void changeBankPageJump(byte bank, byte page, byte address) {
        // Capture all operands before modifying control registers (operands can alias them).
        byte bankValue = REGISTERS[Byte.toUnsignedInt(bank)];
        byte pageValue = REGISTERS[Byte.toUnsignedInt(page)];
        byte addressValue = REGISTERS[Byte.toUnsignedInt(address)];
        Memory.getMemoryPage(bankValue, pageValue); // Validate before changing CPU state.
        REGISTERS[Registers.MEMORY_BANK.address()] = bankValue;
        REGISTERS[Registers.PAGE.address()] = pageValue;
        REGISTERS[Registers.COUNTER.address()] = addressValue;
    }

    /** Reads either RAM bank while execution stays in the current bank. */
    static void loadMemoryOnBank(byte bank, byte page, byte address, byte destination) {
        byte[] target = Memory.getMemoryPage(REGISTERS[Byte.toUnsignedInt(bank)], REGISTERS[Byte.toUnsignedInt(page)]);
        byte value = target[Byte.toUnsignedInt(REGISTERS[Byte.toUnsignedInt(address)])];
        REGISTERS[Byte.toUnsignedInt(destination)] = value;
        incrementCounter(OPCode.LOAD_MEM_ON_BANK.length());
    }

    /** Writes either RAM bank while execution stays in the current bank. */
    static void storeMemoryOnBank(byte value, byte bank, byte page, byte address) {
        byte[] target = Memory.getMemoryPage(REGISTERS[Byte.toUnsignedInt(bank)], REGISTERS[Byte.toUnsignedInt(page)]);
        target[Byte.toUnsignedInt(REGISTERS[Byte.toUnsignedInt(address)])] = REGISTERS[Byte.toUnsignedInt(value)];
        incrementCounter(OPCode.STORE_MEM_ON_BANK.length());
    }

    /** Selects the page stored in a register and advances the counter. */
    static void changePage(byte a){

        int A = Byte.toUnsignedInt(a);

        REGISTERS[Registers.PAGE.address()] = REGISTERS[A];

        incrementCounter(OPCode.CHANGE_PAGE.length());
    }

    /** Selects a page and sets the counter from a second register. */
    static void changePageJump(byte a, byte b){
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);

        REGISTERS[Registers.PAGE.address()] = REGISTERS[A];
        REGISTERS[Registers.COUNTER.address()] = REGISTERS[B];
    }

    /** Selects a page and jumps when two registers contain equal values. */
    static void changePageCondJump(byte a, byte b, byte c, byte address) {
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
    static void clearGraphics() {
        Monitor.clear();

        incrementCounter(OPCode.CLEAR_GRA.length());
    }

    /**
     * Presents the virtual display framebuffer and advances the counter.
     */
    private static final FramePacer DISPLAY_PACER = new FramePacer(33_000_000L);

    static void display() throws InterruptedException {
        DISPLAY_PACER.await();
        Monitor.display();

        incrementCounter(OPCode.DISPLAY.length());
    }

    /** Writes a pixel using x, y, and color values from three registers, then advances the counter. */
    static void pushGraphicsMemory(byte a, byte b, byte color) {
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
    static void pullGraphicsMemory(byte a, byte b, byte target) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        int Target = Byte.toUnsignedInt(target);

        REGISTERS[Target] = Monitor.fetchMemory(REGISTERS[A], REGISTERS[B]);

        incrementCounter(OPCode.READ_GRA_MEM.length());
    }

    static void readInput(byte device, byte target) {
        int targetRegister = Byte.toUnsignedInt(target);

        REGISTERS[targetRegister] = Byte.toUnsignedInt(device) == 0x40
                ? (byte)(Storage.isMounted(Byte.toUnsignedInt(REGISTERS[Registers.STORAGE_PAGE.address()])) ? 1 : 0) : Input.read(device);

        incrementCounter(OPCode.READ_INPUT.length());
    }

    static void outputAudio(byte voice, byte midiNote, byte control) {
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

    static void readNetwork(byte address, byte target) {
        int port=Byte.toUnsignedInt(REGISTERS[Byte.toUnsignedInt(address)]);
        REGISTERS[Byte.toUnsignedInt(target)]=Network.read(port);
        incrementCounter(OPCode.READ_NETWORK.length());
    }

    static void writeNetwork(byte address, byte source) {
        Network.write(Byte.toUnsignedInt(REGISTERS[Byte.toUnsignedInt(address)]),
                REGISTERS[Byte.toUnsignedInt(source)]);
        incrementCounter(OPCode.WRITE_NETWORK.length());
    }

    static void readStorage(byte a, byte b, byte c, byte target) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        int C = Byte.toUnsignedInt(c);
        int Target = Byte.toUnsignedInt(target);

        REGISTERS[Target] = Storage.readData(Byte.toUnsignedInt(REGISTERS[Registers.STORAGE_PAGE.address()]), REGISTERS[A], REGISTERS[B], REGISTERS[C]);

        incrementCounter(OPCode.READ_BYTE_FROM_STORAGE.length());
    }

    static void writeStorage(byte a, byte b, byte c, byte target) {
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        int C = Byte.toUnsignedInt(c);
        int Target = Byte.toUnsignedInt(target);

        Storage.writeData(Byte.toUnsignedInt(REGISTERS[Registers.STORAGE_PAGE.address()]), REGISTERS[A], REGISTERS[B], REGISTERS[C], REGISTERS[Target]);

        incrementCounter(OPCode.WRITE_BYTE_TO_STORAGE.length());
    }

    static void multiply(byte a, byte b, byte target){
        int A = Byte.toUnsignedInt(a);
        int B = Byte.toUnsignedInt(b);
        int Target = Byte.toUnsignedInt(target);

        REGISTERS[Target] = (byte) (REGISTERS[A] * REGISTERS[B]);

        incrementCounter(OPCode.MULTIPLY.length());
    }

    static void incrementRegister(byte a) {
        int A = Byte.toUnsignedInt(a);

        REGISTERS[A]++;

        incrementCounter(OPCode.INCREMENT_REGISTER.length());
    }

}
