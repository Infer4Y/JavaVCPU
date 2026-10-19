package xyz.ignite4inferneo;

import xyz.ignite4inferneo.jvdos.JvFileSystem;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Boot-time package installation through the JVFS format used by guest bytecode. */
final class BundledDesktopInstaller {
    static final int RECORD=256,RECORD_SIZE=40;
    private static final byte[] MAGIC={'J','C','T',2};
    private BundledDesktopInstaller() { }
    static boolean install(byte[] payload) throws IOException {
        // 128 executable pages plus eight literal pages relocated by the guest.
        if(payload.length==0||payload.length>34816)throw new IllegalArgumentException("Invalid desktop image");
        var volume=JvFileSystem.device(0);
        try{volume.validate();}catch(IllegalArgumentException corrupt){IO.println("Desktop installation skipped: "+corrupt.getMessage());return false;}
        var entry=volume.find(JvFileSystem.ROOT,"JCTRL.COM");
        if(entry!=null&&entry.type()==4){IO.println("Preserved directory named JCTRL.COM");return false;}
        byte[] record=Storage.readRange(RECORD,RECORD_SIZE),digest=hash(payload);
        if(entry!=null) {
            if(Arrays.equals(hash(volume.read(entry)),digest))return false;
            int address=(record[4]&255)|((record[5]&255)<<8)|((record[6]&255)<<16);
            if(!Arrays.equals(Arrays.copyOf(record,4),MAGIC)||address!=entry.address()
                    ||!Arrays.equals(hash(volume.read(entry)),Arrays.copyOfRange(record,8,40))) {
                IO.println("Preserved modified/manual JCTRL.COM");return false;
            }
        }
        try {entry=volume.put(JvFileSystem.ROOT,"JCTRL.COM",payload,0);}
        catch(IllegalArgumentException full){IO.println("Desktop installation skipped: "+full.getMessage());return false;}
        byte[] next=new byte[RECORD_SIZE];System.arraycopy(MAGIC,0,next,0,4);
        for(int i=0;i<3;i++)next[4+i]=(byte)(entry.address()>>>(8*i));System.arraycopy(digest,0,next,8,32);
        Storage.writeRange(RECORD,next);IO.println("Installed bundled JCTRL.COM in JVFS root");return true;
    }
    private static byte[] hash(byte[] bytes) {
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
