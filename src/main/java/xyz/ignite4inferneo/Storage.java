/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Storage {
    private static final int STORAGE_SIZE = 1 << 24; // 16 MiB
    private static final byte[] DATA = new byte[STORAGE_SIZE];

    private static final Path IMAGE = Path.of("storage.img");
    private static boolean dirty;

    public static void load() throws IOException {
        if (Files.notExists(IMAGE)) {
            Files.write(IMAGE, DATA);
            return; // New blank storage device.
        }

        if (Files.size(IMAGE) != STORAGE_SIZE) {
            throw new IOException("Invalid storage image size");
        }

        byte[] fileData = Files.readAllBytes(IMAGE);
        System.arraycopy(fileData, 0, DATA, 0, STORAGE_SIZE);
    }

    public static void save() throws IOException {
        if (!dirty) {
            return;
        }

        Files.write(IMAGE, DATA);
        dirty = false;
    }

    private static int offset(byte x, byte y, byte z) {
        return (Byte.toUnsignedInt(x) << 16)
                | (Byte.toUnsignedInt(y) << 8)
                |  Byte.toUnsignedInt(z);
    }

    public static byte readData(byte x, byte y, byte z) {
        return DATA[offset(x, y, z)];
    }

    public static void writeData(byte x, byte y, byte z, byte value) {
        try (FileChannel channel = FileChannel.open(
                    IMAGE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
            )) {

            channel.write(
                    ByteBuffer.wrap(new byte[] { value }),
                    offset(x, y, z)
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        DATA[offset(x, y, z)] = value;
    }

    /** Writes a contiguous block to the disk image and its in-memory cache. */
    public static void writeRange(int offset, byte[] bytes) throws IOException {
        if (offset < 0 || offset + bytes.length > STORAGE_SIZE) {
            throw new IllegalArgumentException("Storage range is outside the disk image");
        }
        try (FileChannel channel = FileChannel.open(IMAGE, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes), offset);
        }
        System.arraycopy(bytes, 0, DATA, offset, bytes.length);
    }

    /** Returns a copy of a contiguous region from the loaded disk image. */
    public static byte[] readRange(int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > STORAGE_SIZE) {
            throw new IllegalArgumentException("Storage range is outside the disk image");
        }
        return java.util.Arrays.copyOfRange(DATA, offset, offset + length);
    }
}
