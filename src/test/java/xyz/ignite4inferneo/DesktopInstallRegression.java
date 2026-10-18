package xyz.ignite4inferneo;

import java.nio.file.*;
import java.util.Arrays;
import xyz.ignite4inferneo.jvdos.*;

/** Package ownership and failed updates on disposable JVFS media. */
public final class DesktopInstallRegression {
    public static void run(Path original)throws Exception {
        Path image=original.resolveSibling("desktop-install-regression.img");Files.deleteIfExists(image);Storage.load(image);
        try {
            DiskInstaller.installIfMissing();var disk=JvFileSystem.device(0);byte[] bundled=DesktopImage.program();
            check(Arrays.equals(read(),bundled),"fresh package");byte[] before=Storage.readRange(0,Storage.STORAGE_SIZE);
            DiskInstaller.installIfMissing();check(Arrays.equals(before,Storage.readRange(0,Storage.STORAGE_SIZE)),"idempotent install");
            disk.put(JvFileSystem.ROOT,"KEEP.TXT",new byte[]{1,2,3},1);
            byte[] older=new byte[501];Arrays.fill(older,(byte)42);
            check(BundledDesktopInstaller.install(older)&&Arrays.equals(read(),older),"managed replacement");
            DiskInstaller.installIfMissing();check(Arrays.equals(read(),bundled),"package update independent of kernel");
            check(Arrays.equals(disk.read(disk.find(JvFileSystem.ROOT,"KEEP.TXT")),new byte[]{1,2,3}),"unrelated file preserved");
            var entry=disk.find(JvFileSystem.ROOT,"JCTRL.COM");Storage.writeRange(entry.first()*1024,new byte[]{99});
            before=Storage.readRange(0,Storage.STORAGE_SIZE);check(!BundledDesktopInstaller.install(older),"modified package preserved");
            check(Arrays.equals(before,Storage.readRange(0,Storage.STORAGE_SIZE)),"modified package makes no writes");
            disk.put(JvFileSystem.ROOT,"JCTRL.COM",bundled,0);Storage.writeRange(256,new byte[40]);
            check(!BundledDesktopInstaller.install(older),"manual package preserved");
            disk.remove(disk.find(JvFileSystem.ROOT,"JCTRL.COM"));disk.mkdir(JvFileSystem.ROOT,"JCTRL.COM");before=Storage.readRange(0,Storage.STORAGE_SIZE);
            check(!BundledDesktopInstaller.install(older)&&Arrays.equals(before,Storage.readRange(0,Storage.STORAGE_SIZE)),"same-name directory is preserved without aborting startup");
            disk.format();check(BundledDesktopInstaller.install(older),"fresh ownership record");
            disk.put(JvFileSystem.ROOT,"FILL.BIN",new byte[disk.freeClusters()*1024],0);before=Storage.readRange(0,Storage.STORAGE_SIZE);
            check(!BundledDesktopInstaller.install(bundled)&&Arrays.equals(before,Storage.readRange(0,Storage.STORAGE_SIZE)),"full disk preserves old package and metadata");
            var fill=disk.find(JvFileSystem.ROOT,"FILL.BIN");int fat=JvFileSystem.FAT+fill.first()*2;
            Storage.writeRange(fat,new byte[]{(byte)fill.first(),(byte)(fill.first()>>>8)});before=Storage.readRange(0,Storage.STORAGE_SIZE);
            check(!BundledDesktopInstaller.install(bundled)&&Arrays.equals(before,Storage.readRange(0,Storage.STORAGE_SIZE)),"corrupt neighboring chain prevents update");
            System.out.println("PASS: JVFS desktop install, ownership, idempotence, upgrades, manual/edited files, full and corrupt disks");
        }finally{Storage.save();Storage.load(original);}
    }
    private static byte[] read(){var disk=JvFileSystem.device(0);return disk.read(disk.find(JvFileSystem.ROOT,"JCTRL.COM"));}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
