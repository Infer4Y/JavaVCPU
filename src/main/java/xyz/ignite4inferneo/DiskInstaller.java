/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

import java.io.IOException;

/** Installs and loads the bytecode-only JVCPM disk image. */
public final class DiskInstaller {
    private static final int HEADER_OFFSET = 0;
    private static final int IMAGE_OFFSET = 512;
    private static final byte BOOTSTRAP_PAGE = (byte) 255;
    // Bump this when the resident opcode image changes so an earlier boot image is replaced.
    // User files live well beyond IMAGE_OFFSET and are therefore preserved during that update.
    private static final byte[] MAGIC = new byte[] {'J', 'V', 'C', 'P', 'M', 20};

    private DiskInstaller() {
    }

    /** Updates firmware, the resident OS and its managed desktop before guest execution. */
    public static void installIfMissing() throws IOException {
        boolean changed=false;
        if (!isInstalled()) {
            byte[][] pages=Programs.bootImage();
            if(pages.length>255)throw new IllegalStateException("Boot image exceeds the available page count");
            byte[] header=new byte[8];System.arraycopy(MAGIC,0,header,0,MAGIC.length);
            header[6]=(byte)pages.length;header[7]=1;
            Storage.writeRange(IMAGE_OFFSET,flatten(pages));Storage.writeRange(HEADER_OFFSET,header);
            IO.println("Installed JVCPM system disk");changed=true;
        }
        // Desktop content is checked even when the kernel revision is already current.
        changed|=BundledDesktopInstaller.install(xyz.ignite4inferneo.jvdos.DesktopImage.program());
        if(changed)Storage.save();
    }

    /**
     * Installs the fixed bootstrap ROM in its reserved page and begins executing it.
     *
     * <p>The bootstrap uses VCPU storage and memory instructions to copy the disk image into
     * pages {@code 0} through {@code 254}, then transfers control to the BIOS at page zero.</p>
     */
    public static void boot() {
        if (!isInstalled()) {
            throw new IllegalStateException("No bootable JVCPM system disk");
        }
        System.arraycopy(BOOTSTRAP, 0, Memory.getMemoryPage(BOOTSTRAP_PAGE), 0, BOOTSTRAP.length);
        Main.REGISTERS[Registers.PAGE.address()] = BOOTSTRAP_PAGE;
        Main.REGISTERS[Registers.COUNTER.address()] = 0;
        Main.REGISTERS[Registers.MEMORY_BANK.address()] = 0;
        Main.REGISTERS[Registers.STORAGE_PAGE.address()] = 0;
        IO.println("Booting JVCPM from storage");
    }

    /*
     * A: storage x, B: storage y, C: storage z, D: byte read,
     * E: destination address, F: destination page, G: image page count, H: zero.
     */
    private static final byte[] BOOTSTRAP = new byte[] {
            OPCode.STORE.code(), 0, Registers.A.address(),
            OPCode.STORE.code(), 0, Registers.B.address(),
            OPCode.STORE.code(), 6, Registers.C.address(),
            OPCode.READ_BYTE_FROM_STORAGE.code(), Registers.A.address(), Registers.B.address(),
                    Registers.C.address(), Registers.G.address(),
            OPCode.STORE.code(), 2, Registers.B.address(),
            OPCode.STORE.code(), 0, Registers.C.address(),
            OPCode.STORE.code(), 0, Registers.E.address(),
            OPCode.STORE.code(), 0, Registers.F.address(),
            OPCode.STORE.code(), 0, Registers.H.address(),

            // Loop: read storage[A:B:C] and write it to memory[F:E].
            OPCode.READ_BYTE_FROM_STORAGE.code(), Registers.A.address(), Registers.B.address(),
                    Registers.C.address(), Registers.D.address(),
            OPCode.STORE_MEM_ON_PAGE.code(), Registers.D.address(), Registers.F.address(), Registers.E.address(),
            OPCode.INCREMENT_REGISTER.code(), Registers.C.address(),
            OPCode.INCREMENT_REGISTER.code(), Registers.E.address(),
            OPCode.COND_JUMP.code(), Registers.E.address(), Registers.H.address(), 48,
            OPCode.JMP.code(), 29,

            // Each 256-byte page advances storage's middle address and the target page.
            OPCode.INCREMENT_REGISTER.code(), Registers.B.address(),
            OPCode.INCREMENT_REGISTER.code(), Registers.F.address(),
            OPCode.COND_JUMP.code(), Registers.F.address(), Registers.G.address(), 58,
            OPCode.JMP.code(), 29,

            // All image pages are loaded; execute the BIOS entry point.
            OPCode.CHANGE_PAGE_JUMP.code(), Registers.H.address(), Registers.H.address()
    };

    private static boolean isInstalled() {
        byte[] header = Storage.readRange(HEADER_OFFSET, 8);
        for (int index = 0; index < MAGIC.length; index++) {
            if (header[index] != MAGIC[index]) {
                return false;
            }
        }
        return Byte.toUnsignedInt(header[6]) > 0;
    }

    private static byte[] flatten(byte[][] pages) {
        byte[] image = new byte[pages.length * 256];
        for (int page = 0; page < pages.length; page++) {
            if (pages[page].length > 256) {
                throw new IllegalStateException("Boot image page is too large: " + page);
            }
            System.arraycopy(pages[page], 0, image, page * 256, pages[page].length);
        }
        return image;
    }
}
