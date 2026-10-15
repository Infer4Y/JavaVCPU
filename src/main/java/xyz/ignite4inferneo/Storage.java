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

/** Independent storage media, each with a 24-bit address bus and byte-wide data transfers. */
public final class Storage {
    public static final int STORAGE_SIZE = 1 << 24;
    private static final MappedByteBuffer[] devices = new MappedByteBuffer[256];
    private static final Path[] images = new Path[256];

    private Storage() { }

    public static void load() throws IOException {
        load(Path.of("storage.img"));
    }

    /** Opens a disk image without allocating a second 16 MiB copy of it. */
    public static synchronized void load(Path image) throws IOException {
        mount(0, image);
    }

    /** Mount a distinct 16 MiB medium while execution is stopped. */
    public static synchronized void mount(int device, Path image) throws IOException {
        checkDevice(device);
        Path resolved = image.toAbsolutePath().normalize();
        for (int i=0; i<images.length; i++) {
            if (i!=device && images[i]!=null && (resolved.equals(images[i])
                    || (Files.exists(resolved) && Files.exists(images[i]) && Files.isSameFile(resolved,images[i]))))
                throw new IOException("Image already mounted as device " + i);
        }
        save();
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
            devices[device] = channel.map(FileChannel.MapMode.READ_WRITE, 0, STORAGE_SIZE);
            images[device] = resolved;
        }
    }

    /** Flushes pending mapped writes to the hard drive. */
    public static void save() throws IOException {
        for (MappedByteBuffer disk : devices) if (disk != null) disk.force();
    }

    private static MappedByteBuffer disk() {
        return disk(0);
    }

    private static void checkDevice(int device) {
        if (device < 0 || device > 255) throw new IllegalArgumentException("Invalid storage device: " + device);
    }

    public static boolean isMounted(int device) {
        checkDevice(device);
        return devices[device] != null;
    }

    private static MappedByteBuffer disk(int device) {
        if (!isMounted(device)) throw new IllegalStateException("Storage device is not mounted: " + device);
        return devices[device];
    }

    private static int offset(byte x, byte y, byte z) {
        return (Byte.toUnsignedInt(x) << 16) | (Byte.toUnsignedInt(y) << 8) | Byte.toUnsignedInt(z);
    }

    public static byte readData(byte x, byte y, byte z) {
        return readData(0, x, y, z);
    }

    public static byte readData(int device, byte x, byte y, byte z) {
        return disk(device).get(offset(x, y, z));
    }

    /** Updates the mapped image without opening a host file for every guest byte. */
    public static void writeData(byte x, byte y, byte z, byte value) {
        writeData(0, x, y, z, value);
    }

    public static void writeData(int device, byte x, byte y, byte z, byte value) {
        disk(device).put(offset(x, y, z), value);
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
