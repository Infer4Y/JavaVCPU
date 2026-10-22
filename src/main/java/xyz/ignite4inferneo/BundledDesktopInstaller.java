package xyz.ignite4inferneo;

import xyz.ignite4inferneo.jvdos.JvFileSystem;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Boot-time package installation through the JVFS format used by guest bytecode. */
final class BundledDesktopInstaller {
    static final int RECORD=256,RECORD_SIZE=40,EDITOR_RECORD=RECORD+RECORD_SIZE,PAINT_RECORD=EDITOR_RECORD+RECORD_SIZE;
    private static final byte[] MAGIC={'J','C','T',2};
    private static final byte[] EDITOR_MAGIC={'J','T','E',1};
    // The first standalone release was imported manually, before ownership records existed.
    private static final byte[] LEGACY_EDITOR=java.util.HexFormat.of().parseHex("2bee0574eb069288ce6634485cc9abcd7e8b045b5c08e04a64e0a1eb0b62af58");
    private BundledDesktopInstaller() { }
    static boolean installBundle()throws IOException {
        boolean changed=false;
        for(var component:xyz.ignite4inferneo.jvdos.DesktopBundle.components()) {
            changed|=switch(component.name()) {
                case "JCTRL.COM" -> install(component.bytes());
                case "TEXTEDIT.COM" -> installTextEditor(component.bytes());
                case "PAINT.COM" -> installPaint(component.bytes());
                default -> throw new IllegalArgumentException("Unknown desktop component");
            };
        }
        return changed;
    }
    static boolean install(byte[] payload) throws IOException {
        // 128 base pages plus up to twelve code/literal pages relocated by the guest.
        if(payload.length==0||payload.length>140*256)throw new IllegalArgumentException("Invalid desktop image");
        return installPackage("JCTRL.COM",payload,RECORD,MAGIC,null);
    }
    static boolean installTextEditor(byte[] payload)throws IOException {
        if(payload.length==0||payload.length>91*256)throw new IllegalArgumentException("Invalid editor image");
        return installPackage("TEXTEDIT.COM",payload,EDITOR_RECORD,EDITOR_MAGIC,LEGACY_EDITOR);
    }
    static boolean installPaint(byte[] payload)throws IOException {
        if(payload.length==0||payload.length>28*256)throw new IllegalArgumentException("Invalid paint image");
        return installPackage("PAINT.COM",payload,PAINT_RECORD,new byte[]{'J','P','T',1},null);
    }
    private static boolean installPackage(String name,byte[] payload,int recordAddress,byte[] magic,byte[] legacy)throws IOException {
        var volume=JvFileSystem.device(0);
        try{volume.validate();}catch(IllegalArgumentException corrupt){IO.println(name+" installation skipped: "+corrupt.getMessage());return false;}
        var entry=volume.find(JvFileSystem.ROOT,name);
        if(entry!=null&&entry.type()==4){IO.println("Preserved directory named "+name);return false;}
        byte[] record=Storage.readRange(recordAddress,RECORD_SIZE),digest=hash(payload);
        if(entry!=null) {
            byte[] existing=hash(volume.read(entry));
            if(Arrays.equals(existing,digest))return false;
            int address=(record[4]&255)|((record[5]&255)<<8)|((record[6]&255)<<16);
            boolean owned=Arrays.equals(Arrays.copyOf(record,4),magic)&&address==entry.address()
                    &&Arrays.equals(existing,Arrays.copyOfRange(record,8,40));
            if(!owned&&(legacy==null||!Arrays.equals(existing,legacy))) {
                IO.println("Preserved modified/manual "+name);return false;
            }
        }
        try {entry=volume.put(JvFileSystem.ROOT,name,payload,0);}
        catch(IllegalArgumentException full){IO.println(name+" installation skipped: "+full.getMessage());return false;}
        byte[] next=new byte[RECORD_SIZE];System.arraycopy(magic,0,next,0,4);
        for(int i=0;i<3;i++)next[4+i]=(byte)(entry.address()>>>(8*i));System.arraycopy(digest,0,next,8,32);
        Storage.writeRange(recordAddress,next);IO.println("Installed bundled "+name+" in JVFS root");return true;
    }
    private static byte[] hash(byte[] bytes) {
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
