package xyz.ignite4inferneo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Installs a managed guest file while the CPU is stopped, using the ordinary DOS disk format. */
final class BundledDesktopInstaller {
    // The boot image starts at byte 512; this package record lives in the unused second sector.
    static final int RECORD=256, RECORD_SIZE=40;
    private static final byte[] MAGIC={'J','C','T',1};
    private static final byte[] NAME="JCTRL.COM".getBytes(StandardCharsets.US_ASCII);
    private BundledDesktopInstaller() { }

    static boolean install(byte[] payload) throws IOException {
        if(payload.length==0||payload.length>61200)throw new IllegalArgumentException("Invalid bundled desktop size");
        byte[] record=Storage.readRange(RECORD,RECORD_SIZE),digest=hash(payload);
        int bank=record[4]&255,head=record[5]&255;
        boolean managed=Arrays.equals(Arrays.copyOf(record,4),MAGIC)&&bank>=2&&head<255;
        if(managed) {
            byte[] bytes=Storage.readRange(bank<<16,65536);
            List<Integer> chain=chain(bytes,head);
            if(chain==null||!namedDesktop(bytes,head)||!Arrays.equals(hash(contents(bytes,chain)),Arrays.copyOfRange(record,8,40))) {
                IO.println("Preserved modified or missing managed JCTRL.COM; automatic desktop update skipped");
                return false;
            }
            if(Arrays.equals(digest,Arrays.copyOfRange(record,8,40)))return false;
            byte[] updated=replace(bytes,chain,payload);
            if(updated==null) {
                IO.println("Preserved desktop bank: corrupt links or insufficient free space for JCTRL.COM update");
                return false;
            }
            Storage.writeRange(bank<<16,updated);
        } else {
            // Start in an unused bank; never take over an existing same-name file.
            bank=255;
            for(;bank>=2;bank--) {
                byte[] bytes=Storage.readRange(bank<<16,65536);
                boolean empty=true;
                for(int slot=0;slot<255;slot++)if(bytes[slot*256+1]!=0){empty=false;break;}
                if(!empty)continue;
                head=0;byte[] updated=replace(bytes,List.of(),payload);
                if(updated==null)continue;
                Storage.writeRange(bank<<16,updated);break;
            }
            if(bank<2) {IO.println("No empty file bank for bundled JCTRL.COM; desktop installation skipped");return false;}
        }
        byte[] next=new byte[RECORD_SIZE];System.arraycopy(MAGIC,0,next,0,4);
        next[4]=(byte)bank;next[5]=(byte)head;System.arraycopy(digest,0,next,8,digest.length);
        Storage.writeRange(RECORD,next);
        IO.println("Installed bundled JCTRL.COM in bank "+bank+"; enter JCTRL to launch");
        return true;
    }

    /** Returns a new bank image; nothing is written until all validation/allocation succeeds. */
    private static byte[] replace(byte[] bytes,List<Integer> owned,byte[] payload) {
        boolean[] used=new boolean[255];
        for(int slot=0;slot<255;slot++) {
            int first=bytes[slot*256+1]&255;
            if(first==0)continue;
            used[slot]=true;
            if(first==255||owned.contains(slot))continue;
            List<Integer> foreign=chain(bytes,slot);
            if(foreign==null)return null;
            for(int block:foreign){if(owned.contains(block))return null;used[block]=true;}
        }
        for(int block:owned)used[block]=false;
        List<Integer> slots=new ArrayList<>();
        // Keep the managed head stable across updates.
        if(!owned.isEmpty()){slots.add(owned.getFirst());used[owned.getFirst()]=true;}
        int needed=(payload.length+239)/240;
        for(int slot=owned.isEmpty()?0:1;slot<255&&slots.size()<needed;slot++)if(!used[slot])slots.add(slot);
        if(slots.size()!=needed)return null;
        byte[] next=bytes.clone();
        for(int block:owned){Arrays.fill(next,block*256,block*256+256,(byte)0);next[0xff00+block+1]=0;}
        for(int n=0;n<slots.size();n++) {
            int slot=slots.get(n),offset=slot*256;
            Arrays.fill(next,offset,offset+256,(byte)0);next[0xff00+slot+1]=0;
            if(n==0) {
                next[offset]=(byte)payload.length;next[offset+14]=(byte)(payload.length>>>8);
                System.arraycopy(NAME,0,next,offset+1,NAME.length);
            } else next[offset+1]=(byte)255;
            next[offset+15]=(byte)(n+1<slots.size()?slots.get(n+1):0);
            int length=Math.min(240,payload.length-n*240);
            System.arraycopy(payload,n*240,next,offset+16,length);
        }
        return next;
    }

    private static boolean namedDesktop(byte[] bank,int head) {
        int offset=head*256;
        if(bank[offset+13]!=0||bank[0xff00+head+1]!=0)return false;
        for(int i=0;i<12;i++)if(bank[offset+1+i]!=(i<NAME.length?NAME[i]:0))return false;
        return true;
    }

    /** Validate the entire bounded chain, including continuation markers and its exact end. */
    private static List<Integer> chain(byte[] bank,int head) {
        int offset=head*256,length=(bank[offset]&255)|((bank[offset+14]&255)<<8);
        if(length>61200)return null;
        int count=Math.max(1,(length+239)/240),slot=head;
        boolean[] seen=new boolean[255];List<Integer> slots=new ArrayList<>();
        for(int n=0;n<count;n++) {
            if(slot>=255||seen[slot]||(n>0&&(slot==0||(bank[slot*256+1]&255)!=255)))return null;
            seen[slot]=true;slots.add(slot);
            int next=bank[slot*256+15]&255;
            if(n+1==count){if(next!=0)return null;}else if(next==0||next==255)return null;
            slot=next;
        }
        return slots;
    }

    private static byte[] contents(byte[] bank,List<Integer> chain) {
        int offset=chain.getFirst()*256,length=(bank[offset]&255)|((bank[offset+14]&255)<<8);
        byte[] data=new byte[length];
        for(int n=0;n<chain.size();n++){int size=Math.min(240,length-n*240);System.arraycopy(bank,chain.get(n)*256+16,data,n*240,size);}
        return data;
    }
    private static byte[] hash(byte[] bytes) {
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
