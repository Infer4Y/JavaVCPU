package xyz.ignite4inferneo;

import java.nio.file.*;
import java.util.Arrays;
import xyz.ignite4inferneo.jvdos.*;

/** Paint's managed package must not overwrite manual or edited applications. */
public final class PaintInstallRegression {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static byte[] read(){var disk=JvFileSystem.device(0);return disk.read(disk.find(JvFileSystem.ROOT,"PAINT.COM"));}
    public static void main(String[] args)throws Exception {
        Path image=Path.of("build/paint-install-regression.img");Files.deleteIfExists(image);Storage.load(image);
        DiskInstaller.installIfMissing();var disk=JvFileSystem.device(0);byte[] current=PaintImage.program();
        check(Arrays.equals(read(),current),"boot installs paint");byte[] before=Storage.readRange(0,Storage.STORAGE_SIZE);DiskInstaller.installIfMissing();
        check(Arrays.equals(before,Storage.readRange(0,Storage.STORAGE_SIZE)),"boot is idempotent");
        byte[] otherRecords=Storage.readRange(BundledDesktopInstaller.RECORD,80),old={2,42,0,4};
        check(BundledDesktopInstaller.installPaint(old),"managed downgrade fixture");DiskInstaller.installIfMissing();
        check(Arrays.equals(read(),current),"paint upgrades independently");check(Arrays.equals(otherRecords,Storage.readRange(BundledDesktopInstaller.RECORD,80)),"paint preserves other app ownership");
        var entry=disk.find(JvFileSystem.ROOT,"PAINT.COM");Storage.writeRange(entry.first()*1024,new byte[]{99});before=Storage.readRange(0,Storage.STORAGE_SIZE);
        check(!BundledDesktopInstaller.installPaint(old)&&Arrays.equals(before,Storage.readRange(0,Storage.STORAGE_SIZE)),"modified paint is preserved without writes");
        disk.put(JvFileSystem.ROOT,"PAINT.COM",old,0);Storage.writeRange(BundledDesktopInstaller.PAINT_RECORD,new byte[40]);
        check(!BundledDesktopInstaller.installPaint(current)&&Arrays.equals(read(),old),"manual paint is preserved");
        disk.remove(disk.find(JvFileSystem.ROOT,"PAINT.COM"));disk.mkdir(JvFileSystem.ROOT,"PAINT.COM");before=Storage.readRange(0,Storage.STORAGE_SIZE);
        check(!BundledDesktopInstaller.installPaint(current)&&Arrays.equals(before,Storage.readRange(0,Storage.STORAGE_SIZE)),"directory conflict is preserved");
        disk.format();check(BundledDesktopInstaller.installPaint(old),"fresh ownership");var fill=disk.put(JvFileSystem.ROOT,"FILL.BIN",new byte[disk.freeClusters()*1024],0);before=Storage.readRange(0,Storage.STORAGE_SIZE);
        check(!BundledDesktopInstaller.installPaint(current)&&Arrays.equals(before,Storage.readRange(0,Storage.STORAGE_SIZE)),"full disk preserves managed app and record");
        disk.remove(fill);check(BundledDesktopInstaller.installPaint(current),"upgrade recovers after freeing space");disk.validate();
        System.out.println("PASS: PAINT boot, idempotence, upgrades, independent ownership, manual/modified copies, directory conflicts and full disk");
    }
}
