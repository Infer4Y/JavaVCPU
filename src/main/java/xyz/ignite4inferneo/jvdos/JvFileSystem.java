package xyz.ignite4inferneo.jvdos;

import xyz.ignite4inferneo.Storage;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** JVFS 1 disk format. Host tooling only: the running OS uses GuestFileSystem bytecode. */
public final class JvFileSystem {
    private JvFileSystem() { }
    public static final int SUPER=0x20000, FAT=0x20400, CLUSTER_SIZE=1024,
            CLUSTERS=16384, ROOT=161, ENTRY_SIZE=32, END=65535, RESERVED=65534;
    public static final byte[] SIGNATURE={'J','V','F','S',1,10,(byte)ROOT,0};
    public interface Media { int read(int address); void write(int address,int value); }
    public record Entry(int address,String name,int type,int first,int length) { }
    public static Volume device(int device) {
        return new Volume(new Media() {
            public int read(int a){return Storage.readData(device,(byte)(a>>>16),(byte)(a>>>8),(byte)a)&255;}
            public void write(int a,int v){Storage.writeData(device,(byte)(a>>>16),(byte)(a>>>8),(byte)a,(byte)v);}
        });
    }
    public static Volume bytes(byte[] bytes) {
        if(bytes.length!=Storage.STORAGE_SIZE)throw new IllegalArgumentException("Expected 16 MiB disk");
        return new Volume(new Media() {
            public int read(int a){return bytes[a]&255;}
            public void write(int a,int v){bytes[a]=(byte)v;}
        });
    }
    public static final class Volume {
        private final Media disk;
        private Volume(Media disk){this.disk=disk;}
        public boolean formatted(){for(int i=0;i<SIGNATURE.length;i++)if(disk.read(SUPER+i)!=(SIGNATURE[i]&255))return false;return true;}
        public void format() {
            zero(SUPER,CLUSTER_SIZE+CLUSTERS*2);
            for(int c=0;c<ROOT;c++)fat(c,RESERVED);
            fat(ROOT,END);zero(ROOT*CLUSTER_SIZE,CLUSTER_SIZE);
            dot(ROOT,ROOT);
            // Publish the format only after its allocation table and root are ready.
            for(int i=0;i<SIGNATURE.length;i++)disk.write(SUPER+i,SIGNATURE[i]);
            zero(256,40);
        }
        public int fat(int c){range(c);return word(FAT+c*2);}
        private void fat(int c,int next){range(c);word(FAT+c*2,next);}
        public List<Integer> chain(int first) {
            List<Integer> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();
            for(int c=first;c!=0&&c!=END;c=fat(c)) {
                if(c<ROOT||c>=CLUSTERS||!seen.add(c))throw new IllegalArgumentException("Invalid JVFS cluster chain");
                result.add(c);int next=fat(c);if(next==0||next==RESERVED)throw new IllegalArgumentException("Unallocated JVFS cluster");
            }
            return result;
        }
        public List<Entry> list(int directory) {
            require();List<Entry> entries=new ArrayList<>();
            for(int c:chain(directory))for(int i=0;i<32;i++) {
                int p=c*CLUSTER_SIZE+i*ENTRY_SIZE;
                if(disk.read(p)!=0){Entry entry=entry(p);if(!entry.name.equals(".")&&!entry.name.equals(".."))entries.add(entry);}
            }
            return entries;
        }
        public Entry find(int directory,String name){String n=name.toUpperCase(Locale.ROOT);return list(directory).stream().filter(e->e.name.equals(n)).findFirst().orElse(null);}
        public Entry entry(int p) {
            StringBuilder name=new StringBuilder();for(int i=0;i<12&&disk.read(p+i)!=0;i++)name.append((char)disk.read(p+i));
            int type=disk.read(p+12),first=word(p+14),length=disk.read(p+16)|(disk.read(p+17)<<8)|(disk.read(p+18)<<16);
            if(type>4)throw new IllegalArgumentException("Invalid JVFS type");
            return new Entry(p,name.toString(),type,first,length);
        }
        public byte[] read(Entry entry) {
            if(entry.type==4)throw new IllegalArgumentException("Entry is a directory");
            List<Integer> blocks=chain(entry.first);
            if(blocks.size()!=(entry.length+1023)/1024)throw new IllegalArgumentException("JVFS length/chain mismatch");
            byte[] bytes=new byte[entry.length];int at=0;
            for(int c:blocks)for(int j=0;j<1024&&at<bytes.length;j++)bytes[at++]=(byte)disk.read(c*1024+j);
            return bytes;
        }
        public Entry put(int directory,String name,byte[] bytes,int type) {
            require();name=validName(name);if(type<0||type>3)throw new IllegalArgumentException("Invalid file type");
            Entry old=find(directory,name);if(old!=null&&old.type==4)throw new IllegalArgumentException("Cannot replace a directory");
            List<Integer> oldChain=old==null?List.of():chain(old.first);
            List<Integer> next=free((bytes.length+1023)/1024); // Keep the old file until replacement is complete.
            for(int i=0;i<next.size();i++)fat(next.get(i),i+1<next.size()?next.get(i+1):END);
            int slot;
            try{slot=old==null?slot(directory):old.address;}
            catch(RuntimeException failure){for(int c:next)fat(c,0);throw failure;}
            int at=0;
            for(int i=0;i<next.size();i++) {
                int c=next.get(i);fat(c,i+1<next.size()?next.get(i+1):END);
                for(int j=0;j<1024;j++)disk.write(c*1024+j,at<bytes.length?bytes[at++]:0);
            }
            writeEntry(slot,name,type,next.isEmpty()?0:next.getFirst(),bytes.length);
            for(int c:oldChain)fat(c,0);
            return entry(slot);
        }
        public Entry mkdir(int directory,String name) {
            require();name=validName(name);if(find(directory,name)!=null)throw new IllegalArgumentException("Destination exists");
            int c=free(1).getFirst();fat(c,END);int slot;
            try{slot=slot(directory);}catch(RuntimeException failure){fat(c,0);throw failure;}
            zero(c*1024,1024);dot(c,directory);
            writeEntry(slot,name,4,c,0);return entry(slot);
        }
        public void remove(Entry entry) {
            if(entry.type==4&&!list(entry.first).isEmpty())throw new IllegalArgumentException("Directory is not empty");
            List<Integer> chain=chain(entry.first);disk.write(entry.address,0);for(int c:chain)fat(c,0);
        }
        public int parent(int directory){return word(directory*1024+32+14);}
        public void move(int directory,Entry entry,String name,int target) {
            name=validName(name);if(find(target,name)!=null)throw new IllegalArgumentException("Destination exists");
            if(entry.type==4) {
                Set<Integer> seen=new HashSet<>();for(int c=target;;c=parent(c)) {
                    if(c==entry.first||!seen.add(c))throw new IllegalArgumentException("Directory cycle");if(c==ROOT)break;
                }
            }
            int p=directory==target?entry.address:slot(target);
            writeEntry(p,name,entry.type,entry.first,entry.length);if(p!=entry.address)disk.write(entry.address,0);
            if(entry.type==4)word(entry.first*1024+32+14,target);
        }
        public int freeClusters(){int count=0;for(int c=ROOT;c<CLUSTERS;c++)if(fat(c)==0)count++;return count;}
        /** Checks ownership before host installers modify an existing volume. */
        public void validate() {
            require();for(int c=0;c<ROOT;c++)if(fat(c)!=RESERVED)throw new IllegalArgumentException("Invalid reserved cluster");
            Set<Integer> used=new HashSet<>(),seen=new HashSet<>();
            ArrayDeque<int[]> todo=new ArrayDeque<>();todo.add(new int[]{ROOT,ROOT});
            while(!todo.isEmpty()) {
                int[] dir=todo.removeFirst();if(!seen.add(dir[0]))throw new IllegalArgumentException("Directory cycle");
                claim(chain(dir[0]),used);
                Entry self=entry(dir[0]*1024),parent=entry(dir[0]*1024+32);
                if(!self.name.equals(".")||self.type!=4||self.first!=dir[0]||!parent.name.equals("..")||parent.type!=4||parent.first!=dir[1])throw new IllegalArgumentException("Invalid directory parent");
                Set<String> names=new HashSet<>();
                for(Entry e:list(dir[0])) {
                    validName(e.name);if(!names.add(e.name))throw new IllegalArgumentException("Duplicate filename");
                    if(e.type==4){if(e.length!=0)throw new IllegalArgumentException("Directory has a file length");todo.add(new int[]{e.first,dir[0]});}
                    else{List<Integer> blocks=chain(e.first);if(blocks.size()!=(e.length+1023)/1024)throw new IllegalArgumentException("File length/chain mismatch");claim(blocks,used);}
                }
            }
            for(int c=ROOT;c<CLUSTERS;c++)if(fat(c)!=0&&!used.contains(c))throw new IllegalArgumentException("Orphaned cluster");
        }
        private static void claim(List<Integer> chain,Set<Integer> used){for(int c:chain)if(!used.add(c))throw new IllegalArgumentException("Shared cluster chain");}
        private int slot(int directory) {
            List<Integer> chain=chain(directory);
            for(int c:chain)for(int n=0;n<32;n++)if(disk.read(c*1024+n*32)==0)return c*1024+n*32;
            int next=free(1).getFirst();fat(next,END);zero(next*1024,1024);fat(chain.getLast(),next);return next*1024;
        }
        private List<Integer> free(int count) {
            List<Integer> result=new ArrayList<>();for(int c=ROOT+1;c<CLUSTERS&&result.size()<count;c++)if(fat(c)==0)result.add(c);
            if(result.size()!=count)throw new IllegalArgumentException("Disk full");return result;
        }
        private void dot(int self,int parent){writeEntry(self*1024,".",4,self,0);writeEntry(self*1024+32,"..",4,parent,0);}
        private void writeEntry(int p,String name,int type,int first,int length) {
            byte[] n=name.getBytes(StandardCharsets.US_ASCII);for(int i=1;i<32;i++)disk.write(p+i,i<n.length?n[i]:0);
            disk.write(p+12,type);word(p+14,first);for(int i=0;i<3;i++)disk.write(p+16+i,length>>>(i*8));disk.write(p,n[0]);
        }
        private void require(){if(!formatted())throw new IllegalArgumentException("Disk is not JVFS 1");}
        private int word(int p){return disk.read(p)|(disk.read(p+1)<<8);}
        private void word(int p,int value){disk.write(p,value);disk.write(p+1,value>>>8);}
        private void zero(int p,int length){for(int i=0;i<length;i++)disk.write(p+i,0);}
        private static void range(int c){if(c<0||c>=CLUSTERS)throw new IllegalArgumentException("Invalid cluster");}
    }
    public static String validName(String name) {
        name=name.toUpperCase(Locale.ROOT);
        if(!name.matches("[A-Z0-9_.-]{1,12}")||name.startsWith("."))throw new IllegalArgumentException("Invalid JVFS name");return name;
    }
}
