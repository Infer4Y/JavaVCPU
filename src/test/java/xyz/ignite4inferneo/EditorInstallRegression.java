package xyz.ignite4inferneo;

import java.nio.file.*;
import java.util.Arrays;
import xyz.ignite4inferneo.jvdos.*;

/** Managed editor installation is independent of the desktop's ownership record. */
public final class EditorInstallRegression {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static byte[] read(){var disk=JvFileSystem.device(0);return disk.read(disk.find(JvFileSystem.ROOT,"TEXTEDIT.COM"));}
    public static void main(String[] args)throws Exception {
        Path image=Path.of("build/editor-install-regression.img");Files.deleteIfExists(image);Storage.load(image);
        DiskInstaller.installIfMissing();var disk=JvFileSystem.device(0);byte[] current=TextEditImage.program();
        check(Arrays.equals(read(),current),"startup installs editor");
        byte[] snapshot=Storage.readRange(0,Storage.STORAGE_SIZE);DiskInstaller.installIfMissing();
        check(Arrays.equals(snapshot,Storage.readRange(0,Storage.STORAGE_SIZE)),"repeated boot makes no changes");
        byte[] desktopRecord=Storage.readRange(BundledDesktopInstaller.RECORD,40);
        byte[] old={2,42,0,4};check(BundledDesktopInstaller.installTextEditor(old),"managed editor downgrade fixture");
        DiskInstaller.installIfMissing();check(Arrays.equals(read(),current),"editor upgrades independently of firmware");
        check(Arrays.equals(desktopRecord,Storage.readRange(BundledDesktopInstaller.RECORD,40)),"editor preserves desktop ownership");
        var entry=disk.find(JvFileSystem.ROOT,"TEXTEDIT.COM");Storage.writeRange(entry.first()*1024,new byte[]{99});
        snapshot=Storage.readRange(0,Storage.STORAGE_SIZE);check(!BundledDesktopInstaller.installTextEditor(old),"edited copy preserved");
        check(Arrays.equals(snapshot,Storage.readRange(0,Storage.STORAGE_SIZE)),"edited copy makes no writes");
        disk.put(JvFileSystem.ROOT,"TEXTEDIT.COM",old,0);Storage.writeRange(BundledDesktopInstaller.EDITOR_RECORD,new byte[40]);
        check(!BundledDesktopInstaller.installTextEditor(current)&&Arrays.equals(read(),old),"manual copy preserved");
        disk.remove(disk.find(JvFileSystem.ROOT,"TEXTEDIT.COM"));disk.mkdir(JvFileSystem.ROOT,"TEXTEDIT.COM");
        snapshot=Storage.readRange(0,Storage.STORAGE_SIZE);check(!BundledDesktopInstaller.installTextEditor(current),"same-name directory preserved");
        check(Arrays.equals(snapshot,Storage.readRange(0,Storage.STORAGE_SIZE)),"directory conflict makes no writes");
        disk.format();check(BundledDesktopInstaller.installTextEditor(old),"fresh editor ownership");
        var fill=disk.put(JvFileSystem.ROOT,"FILL.BIN",new byte[disk.freeClusters()*1024],0);snapshot=Storage.readRange(0,Storage.STORAGE_SIZE);
        check(!BundledDesktopInstaller.installTextEditor(current)&&Arrays.equals(snapshot,Storage.readRange(0,Storage.STORAGE_SIZE)),"full disk retains editor and metadata");
        disk.remove(fill);check(BundledDesktopInstaller.installTextEditor(current),"upgrade recovers after freeing disk");
        if(args.length>0){
            disk.put(JvFileSystem.ROOT,"TEXTEDIT.COM",Files.readAllBytes(Path.of(args[0])),0);Storage.writeRange(BundledDesktopInstaller.EDITOR_RECORD,new byte[40]);
            check(BundledDesktopInstaller.installTextEditor(current)&&Arrays.equals(read(),current),"original standalone release adopted and upgraded");
        }
        disk.validate();Storage.save();System.out.println("PASS: bundled TEXTEDIT boot, idempotence, upgrades, ownership, edited/manual files, directory conflict and full disk");
    }
}
