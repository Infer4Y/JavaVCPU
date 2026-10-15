/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */
package xyz.ignite4inferneo;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** The VCPU's 24-bit, memory-mapped hard drive. */
public final class Storage {
    public static final int STORAGE_SIZE = 1 << 24;
    private static MappedByteBuffer data;

    private Storage() { }

    public static void load() throws IOException {
        load(Path.of("storage.img"));
    }

    /** Opens a disk image without allocating a second 16 MiB copy of it. */
    public static synchronized void load(Path image) throws IOException {
        if (data != null) save();
        boolean existing = Files.exists(image);
        if (existing && Files.size(image) != STORAGE_SIZE) {
            throw new IOException("Invalid storage image size: expected " + STORAGE_SIZE + " bytes");
        }
        try (FileChannel channel = FileChannel.open(image, StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            if (!existing) {
                channel.position(STORAGE_SIZE - 1L);
                channel.write(java.nio.ByteBuffer.wrap(new byte[1]));
            }
            data = channel.map(FileChannel.MapMode.READ_WRITE, 0, STORAGE_SIZE);
        }
    }

    /** Flushes pending mapped writes to the hard drive. */
    public static void save() throws IOException {
        if (data != null) data.force();
    }

    private static MappedByteBuffer disk() {
        if (data == null) throw new IllegalStateException("Storage has not been loaded");
        return data;
    }

    private static int offset(byte x, byte y, byte z) {
        return (Byte.toUnsignedInt(x) << 16) | (Byte.toUnsignedInt(y) << 8) | Byte.toUnsignedInt(z);
    }

    public static byte readData(byte x, byte y, byte z) {
        return disk().get(offset(x, y, z));
    }

    /** Updates the mapped image without opening a host file for every guest byte. */
    public static void writeData(byte x, byte y, byte z, byte value) {
        disk().put(offset(x, y, z), value);
    }

    public static void writeRange(int offset, byte[] bytes) throws IOException {
        checkRange(offset, bytes.length);
        disk().put(offset, bytes);
    }

    public static byte[] readRange(int offset, int length) {
        checkRange(offset, length);
        byte[] bytes = new byte[length];
        disk().get(offset, bytes);
        return bytes;
    }

    private static void checkRange(int offset, int length) {
        if (offset < 0 || length < 0 || offset > STORAGE_SIZE || length > STORAGE_SIZE - offset) {
            throw new IllegalArgumentException("Storage range is outside the disk image");
        }
    }
}
