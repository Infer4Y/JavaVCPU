package xyz.ignite4inferneo;

import java.util.Arrays;

/** Exercises the real CPU helpers and RAM mapping without host peripherals. */
public final class MemoryRegression {
    static void run() {
        byte[] saved = Main.REGISTERS.clone();
        byte[] savedPage = Main.MEMORY;
        try {
            Arrays.fill(Main.REGISTERS, (byte) 0);
            Memory.write(0, (byte) 12);
            Memory.write(65535, (byte) 23);
            Memory.write(65536, (byte) 34);
            Memory.write(131071, (byte) 45);
            check(Memory.SIZE == 131072, "128 KiB capacity");
            check(Memory.read(0)==12 && Memory.read(65535)==23, "lower RAM boundaries");
            check(Memory.read(65536)==34 && Memory.read(131071)==45, "upper RAM boundaries");
            check(Memory.getMemoryPage((byte)255)[255]==23, "legacy bank-zero API");
            reject(() -> Memory.read(-1));
            reject(() -> Memory.write(131072, (byte)1));
            reject(() -> Memory.getMemoryPage((byte)2, (byte)0));
            reject(() -> Memory.getMemoryPage((byte)255, (byte)0));

            Main.REGISTERS[0]=1; Main.REGISTERS[1]=(byte)255;
            Main.REGISTERS[2]=(byte)255; Main.REGISTERS[3]=42;
            Main.storeMemoryOnBank((byte)3,(byte)0,(byte)1,(byte)2);
            check(Memory.read(131071)==42 && Memory.read(65535)==23, "far write isolation");
            check(Main.getProgramCounter()==5 && Main.REGISTERS[11]==0, "far write preserves execution bank");
            Main.loadMemoryOnBank((byte)0,(byte)1,(byte)2,(byte)4);
            check(Main.REGISTERS[4]==42 && Main.getProgramCounter()==10, "far read");
            Main.changeBankPageJump((byte)0,(byte)1,(byte)2);
            check(Main.REGISTERS[11]==1 && Main.REGISTERS[9]==(byte)255 && Main.getProgramCounter()==255, "far jump");
            Main.MEMORY=Memory.getMemoryPage(Main.REGISTERS[11], Main.REGISTERS[9]);
            Main.loadMemory((byte)4,(byte)255);
            check(Main.REGISTERS[4]==42, "legacy load in active bank");
            Main.REGISTERS[3]=77;
            Main.storeMemoryOnPage((byte)3,(byte)1,(byte)2);
            check(Memory.read(131071)==77 && Memory.read(65535)==23, "legacy page store in active bank");

            // Page operand aliases COUNTER: the jump must capture it before modifying state.
            Main.REGISTERS[8]=7; Main.REGISTERS[2]=9;
            Main.changeBankPageJump((byte)11,(byte)8,(byte)2);
            check(Main.REGISTERS[11]==1 && Main.REGISTERS[9]==7 && Main.REGISTERS[8]==9, "aliased far-jump operands");
            Main.REGISTERS[0]=2;
            byte[] before=Main.REGISTERS.clone();
            reject(() -> Main.changeBankPageJump((byte)0,(byte)1,(byte)2));
            check(Arrays.equals(before,Main.REGISTERS), "invalid bank leaves CPU state intact");
            Main.REGISTERS[3]=(byte)200;
            Main.storeMemoryAbsolute((byte)3,(byte)1,(byte)128,(byte)255);
            Main.loadMemoryAbsolute((byte)1,(byte)128,(byte)255,(byte)4);
            check((Main.REGISTERS[4]&255)==200,"absolute memory operands");
            Main.REGISTERS[0]=(byte)255;Main.REGISTERS[1]=1;
            Main.compareUnsigned((byte)0,(byte)1,(byte)2);check(Main.REGISTERS[2]==2,"unsigned greater");
            Main.compareUnsigned((byte)1,(byte)0,(byte)2);check(Main.REGISTERS[2]==1,"unsigned less");
            Main.compareUnsigned((byte)0,(byte)0,(byte)0);check(Main.REGISTERS[0]==0,"comparison destination aliases input");
            for (Registers register : new Registers[]{Registers.I, Registers.J, Registers.K,
                    Registers.L, Registers.M, Registers.N, Registers.O, Registers.P}) {
                Main.store((byte)41,register.address());
                Main.incrementRegister(register.address());
                check(Main.REGISTERS[register.address()]==42,"extended CPU register " + register);
            }
            Programs.load(Programs.Program.HELLO_CPU);
            check(Main.REGISTERS[11]==0, "program loading resets bank");
            System.out.println("PASS: 128 KiB RAM boundaries, bank isolation, CPU far reads/writes/jumps, legacy access and operand aliasing");
        } finally {
            System.arraycopy(saved,0,Main.REGISTERS,0,saved.length);
            Main.MEMORY=savedPage;
        }
    }
    private static void check(boolean value,String label) { if(!value)throw new AssertionError(label); }
    private static void reject(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid RAM address accepted");
    }
}
