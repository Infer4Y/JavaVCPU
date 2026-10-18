package xyz.ignite4inferneo;

import java.nio.file.Path;
import java.util.Arrays;
import xyz.ignite4inferneo.jvdos.DesktopImage;

/** Exercises package installation on a disposable disk, never on the user's storage.img. */
public final class DesktopInstallRegression {
    private DesktopInstallRegression() { }
    public static void run(Path original) throws Exception {
        Path image=original.resolveSibling("desktop-install-regression.img");
        java.nio.file.Files.deleteIfExists(image);Storage.load(image);
        try {
            DiskInstaller.installIfMissing();
            byte[] bundled=DesktopImage.program();check(Arrays.equals(readPackage(),bundled),"fresh OS installs the actual desktop");
            byte[] system=Storage.readRange(0,65536),bank=Storage.readRange(255<<16,65536);
            DiskInstaller.installIfMissing();
            check(Arrays.equals(system,Storage.readRange(0,65536))&&Arrays.equals(bank,Storage.readRange(255<<16,65536)),"repeat installation is byte-for-byte idempotent");
            byte[] old=new byte[501];Arrays.fill(old,(byte)42);
            check(BundledDesktopInstaller.install(old)&&Arrays.equals(readPackage(),old),"managed package upgrade and shrink");
            // An ordinary user file can coexist with the managed package.
            byte[] foreign=new byte[256];foreign[0]=4;foreign[13]=1;
            System.arraycopy("KEEP.TXT".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,foreign,1,8);
            System.arraycopy(new byte[]{1,2,3,4},0,foreign,16,4);Storage.writeRange((255<<16)+(254<<8),foreign);
            byte[] newer=new byte[800];Arrays.fill(newer,(byte)43);
            check(BundledDesktopInstaller.install(newer)&&Arrays.equals(readPackage(),newer),"managed package grows");
            check(Arrays.equals(foreign,Storage.readRange((255<<16)+(254<<8),256)),"update preserves unrelated user file");
            byte[] baseline=Storage.readRange(255<<16,65536),record=Storage.readRange(256,40);
            for(int change=0;change<4;change++) {
                byte[] altered=baseline.clone();
                if(change==0)altered[16]^=1; // Edited content.
                if(change==1)altered[1]='X'; // Renamed file.
                if(change==2)altered[0xff01]=1; // Moved into a folder.
                if(change==3)altered[15]=0; // Truncated/corrupt chain.
                Storage.writeRange(255<<16,altered);
                check(!BundledDesktopInstaller.install(old),"preserve edited/renamed/moved/corrupt managed desktop");
                check(Arrays.equals(altered,Storage.readRange(255<<16,65536))&&Arrays.equals(record,Storage.readRange(256,40)),"rejected update writes no file or ownership record");
            }
            byte[] shared=baseline.clone();shared[254*256]=(byte)800;shared[254*256+14]=(byte)(800>>>8);shared[254*256+15]=1;
            Storage.writeRange(255<<16,shared);
            check(!BundledDesktopInstaller.install(old)&&Arrays.equals(shared,Storage.readRange(255<<16,65536)),"shared/cross-linked chain is preserved");
            byte[] full=baseline.clone();
            for(int slot=4;slot<254;slot++){int at=slot*256;full[at+1]='F';full[at+13]=1;}
            Storage.writeRange(255<<16,full);
            check(!BundledDesktopInstaller.install(new byte[1200])&&Arrays.equals(full,Storage.readRange(255<<16,65536)),"insufficient space preserves complete bank");
            Storage.writeRange(255<<16,baseline);
            // The package updates independently of kernel revision, including already-current kernels.
            DiskInstaller.installIfMissing();check(Arrays.equals(readPackage(),bundled),"current DOS refreshes stale managed package");
            byte[] header=Storage.readRange(0,8);header[5]=17;Storage.writeRange(0,header);
            DiskInstaller.installIfMissing();check(Storage.readRange(0,8)[5]==21&&Arrays.equals(readPackage(),bundled),"DOS revision upgrade includes desktop");
            check(Arrays.equals(foreign,Storage.readRange((255<<16)+(254<<8),256)),"OS upgrade preserves user files beside package");
            // No record: a manual same-name file is not adopted or overwritten.
            byte[] manual=Storage.readRange(255<<16,65536);Storage.writeRange(256,new byte[40]);
            check(BundledDesktopInstaller.install(old)&&(Storage.readRange(256,40)[4]&255)==254,"manual desktop collision installs in another empty bank");
            check(Arrays.equals(manual,Storage.readRange(255<<16,65536)),"manual desktop bank untouched");
            Storage.writeRange(256,new byte[40]);
            for(int b=2;b<255;b++)Storage.writeData((byte)b,(byte)0,(byte)1,(byte)'F');
            check(!BundledDesktopInstaller.install(old)&&Arrays.equals(new byte[40],Storage.readRange(256,40)),"no empty bank leaves ownership unset");
            System.out.println("PASS: bundled desktop fresh install, independent updates, OS upgrades, idempotence, collisions, edited files, corrupt/shared chains and full disks");
        } finally {Storage.save();Storage.load(original);}
    }
    private static byte[] readPackage() {
        byte[] record=Storage.readRange(256,40);int bank=record[4]&255,slot=record[5]&255;
        byte[] head=Storage.readRange((bank<<16)+(slot<<8),256);
        byte[] result=new byte[(head[0]&255)|((head[14]&255)<<8)];
        for(int position=0;position<result.length;position+=240){byte[] block=Storage.readRange((bank<<16)+(slot<<8),256);int count=Math.min(240,result.length-position);System.arraycopy(block,16,result,position,count);slot=block[15]&255;}
        return result;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

