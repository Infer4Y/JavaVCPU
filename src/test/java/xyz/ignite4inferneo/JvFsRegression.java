package xyz.ignite4inferneo;

import xyz.ignite4inferneo.jvdos.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Exercises the actual resident JVFS bytecode against the tooling's independent reader. */
public final class JvFsRegression {
    private static Object terminal() throws Exception {return invoke(null,"terminal",new Class<?>[0]);}
    private static Object invoke(Object target,String name,Class<?>[] types,Object... args) throws Exception {
        Method m=DosStorageRegression.class.getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(target,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception ex)throw ex;if(e.getCause() instanceof Error error)throw error;throw e;}
    }
    private static void command(Object vm,String text)throws Exception {invoke(vm,"command",new Class<?>[]{String.class},text);}
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    private static byte[][] ram(Object vm)throws Exception {var f=DosStorageRegression.class.getDeclaredField("ram");f.setAccessible(true);return (byte[][])f.get(vm);}
    private static int service(Object vm,int operation)throws Exception {
        var field=DosStorageRegression.class.getDeclaredField("r");field.setAccessible(true);int[] regs=(int[])field.get(vm),saved=regs.clone();
        byte[][] memory=ram(vm);byte[] sentinel=memory[248].clone();
        memory[245][0]=(byte)operation;memory[248][0]=16;memory[248][1]=0;memory[248][2]=0;
        regs[0]=0;regs[1]=245;regs[11]=0;regs[9]=249;regs[8]=30;regs[17]=0;regs[18]=248;regs[19]=0;
        try{invoke(vm,"run",new Class<?>[0]);return memory[245][1]&255;}
        finally{System.arraycopy(saved,0,regs,0,regs.length);System.arraycopy(sentinel,0,memory[248],0,256);}
    }
    private static void address(Object vm,JvFileSystem.Entry entry)throws Exception {for(int i=0;i<3;i++)ram(vm)[245][14+i]=(byte)(entry.address()>>>((2-i)*8));}
    private static void appChecks()throws Exception {
        var disk=JvFileSystem.device(0);byte[] contents=new byte[17];for(int i=0;i<17;i++)contents[i]=(byte)(i+70);
        disk.put(JvFileSystem.ROOT,"HELLO.COM",contents,0);
        var bytes=new java.io.ByteArrayOutputStream();
        java.util.function.IntConsumer emit=bytes::write;
        java.util.function.BiConsumer<Integer,Integer> field=(offset,value)->{for(int b:new int[]{2,value,3,28,3,1,30,offset})emit.accept(b);};
        field.accept(4,161);String name="HELLO.COM";for(int i=0;i<name.length();i++)field.accept(32+i,(int)name.charAt(i));
        for(int op:new int[]{FileSystemAbi.LOOKUP,FileSystemAbi.OPEN,FileSystemAbi.READ_BLOCK}) {
            if(op==FileSystemAbi.READ_BLOCK){field.accept(20,17);field.accept(25,1);field.accept(26,31);}
            field.accept(0,op);int back=bytes.size()+28;
            for(int b:new int[]{2,1,0,2,30,1,2,0,17,2,248,18,2,back,19,2,0,12,2,249,13,2,30,14,23,12,13,14})emit.accept(b);
        }
        emit.accept(4);check(bytes.size()<=240,"test COM fits one transient page");disk.put(JvFileSystem.ROOT,"FSAPP.COM",bytes.toByteArray(),0);
        Object vm=terminal();command(vm,"JCTRL");int row=0;var list=disk.list(JvFileSystem.ROOT);while(!list.get(row).name().equals("FSAPP.COM"))row++;
        byte[] resident=ram(vm)[256+31].clone();invoke(vm,"click",new Class<?>[]{int.class,int.class},20,53+row*6);
        for(int i=0;i<100;i++)invoke(vm,"press",new Class<?>[]{int[].class},(Object)new int[]{0});
        check((ram(vm)[256+225][23]&255)!=6,"filesystem COM completes without application error");
        check(Arrays.equals(Arrays.copyOf(ram(vm)[256+159],17),contents),"windowed COM block I/O maps its buffer into application RAM");
        check(Arrays.equals(resident,ram(vm)[256+31]),"application block I/O preserves resident desktop code");
        byte[] tune=new byte[65540];tune[0]='J';tune[1]='M';tune[2]=2;for(int i=4;i<tune.length;i+=4){tune[i+1]=7;tune[i+2]=60;tune[i+3]=(byte)129;}
        disk.put(JvFileSystem.ROOT,"LONG.JMT",tune,3);vm=terminal();command(vm,"PLAY LONG.JMT");invoke(vm,"press",new Class<?>[]{int[].class},(Object)new int[]{27});command(vm,"WRITE MUSIC_OK.TXT YES");
        check(disk.find(JvFileSystem.ROOT,"MUSIC_OK.TXT")!=null,"large streaming music remains cancellable");
        var docs=disk.mkdir(JvFileSystem.ROOT,"DOCS");var deep=disk.mkdir(docs.first(),"DEEP");
        disk.put(JvFileSystem.ROOT,"MATH.JVS",Files.readAllBytes(Path.of("examples/dos/MATH.JVS")),1);command(vm,"MATH");
        check((ram(vm)[252][226]&255)==42,"direct JVS launch uses the new loader");
        command(vm,"JCTRL");row=0;list=disk.list(JvFileSystem.ROOT);while(!list.get(row).name().equals("DOCS"))row++;
        invoke(vm,"click",new Class<?>[]{int.class,int.class},20,53+row*6);invoke(vm,"click",new Class<?>[]{int.class,int.class},20,53);
        invoke(vm,"press",new Class<?>[]{int[].class},(Object)new int[]{115});
        check(new String(ram(vm)[256+251],0,10,StandardCharsets.US_ASCII).equals("DOCS\\DEEP\\"),"explorer DOS prompt reconstructs the full directory path");
        command(vm,"WRITE PATH.TXT HERE");for(int i=0;i<250;i++)invoke(vm,"press",new Class<?>[]{int[].class},(Object)new int[]{0});
        check(Arrays.equals(disk.read(disk.find(deep.first(),"PATH.TXT")),"HERE".getBytes(StandardCharsets.US_ASCII)),"windowed DOS writes into the selected nested folder");
    }
    private static void storageChecks(Object vm)throws Exception {
        var disk=JvFileSystem.device(0);int root=JvFileSystem.ROOT;
        command(vm,"MD OUTER");command(vm,"CD OUTER");command(vm,"MD INNER");command(vm,"CD /");
        byte[] before=Storage.readRange(0,Storage.STORAGE_SIZE);command(vm,"MOVE OUTER OUTER");
        check(Arrays.equals(before,Storage.readRange(0,Storage.STORAGE_SIZE)),"directory self-move is rejected");
        command(vm,"CD OUTER");command(vm,"CD .");command(vm,"WRITE STAY.TXT HERE");
        check(disk.find(disk.find(root,"OUTER").first(),"STAY.TXT")!=null,"CD dot stays in current directory");command(vm,"CD /");
        var many=disk.mkdir(root,"MANY");for(int i=0;i<300;i++)disk.put(many.first(),"F"+i+".TXT",new byte[]{65},1);
        command(vm,"CD MANY");command(vm,"COPY F299.TXT LAST.TXT");
        check(disk.find(many.first(),"LAST.TXT")!=null,"guest lookup after entry 255");command(vm,"DIR");command(vm,"CD /");
        var file=disk.put(root,"BLOCK.BIN",new byte[1025],0);address(vm,file);ram(vm)[245][2]=3;
        check(service(vm,FileSystemAbi.OPEN)==0,"ABI open");
        ram(vm)[245][17]=0;ram(vm)[245][18]=4;ram(vm)[245][19]=0;check(service(vm,FileSystemAbi.SEEK)==0,"ABI seek across cluster");
        ram(vm)[245][20]=4;ram(vm)[245][25]=1;ram(vm)[245][26]=(byte)252;ram(vm)[245][27]=0;
        check(service(vm,FileSystemAbi.READ_BLOCK)==FileSystemAbi.EOF&&(ram(vm)[245][20]&255)==1,"partial block read reports actual byte count");
        service(vm,FileSystemAbi.CLOSE);
        int fat=JvFileSystem.FAT+file.first()*2;byte[] old=Storage.readRange(fat,2);
        Storage.writeRange(fat,new byte[]{(byte)file.first(),(byte)(file.first()>>>8)});address(vm,file);
        check(service(vm,FileSystemAbi.OPEN)==FileSystemAbi.CORRUPT,"cyclic file rejected before reading");Storage.writeRange(fat,old);
        var large=disk.find(root,"LARGE.TXT");address(vm,large);check(service(vm,FileSystemAbi.OPEN)==0,"open large file for seek");
        int position=139999;for(int i=0;i<3;i++)ram(vm)[245][17+i]=(byte)(position>>>(i*8));
        check(service(vm,FileSystemAbi.SEEK)==0&&service(vm,FileSystemAbi.READ)==0&&(ram(vm)[245][21]&255)==('A'+position%26),"24-bit seek reads beyond 64 KiB");service(vm,FileSystemAbi.CLOSE);
        disk.validate();
        var fill=disk.put(root,"FILL.BIN",new byte[(disk.freeClusters()-1)*1024],0);
        command(vm,"WRITE EDGE.TXT END");var edge=disk.find(root,"EDGE.TXT");
        check(edge.first()==16383&&Arrays.equals(disk.read(edge),new byte[]{'E','N','D'}),"guest allocation and byte addressing reach the last physical cluster");int free=disk.freeClusters();
        command(vm,"WRITE NO_ROOM.TXT DATA");check(disk.find(root,"NO_ROOM.TXT")==null&&disk.freeClusters()==free,"failed new file releases staged state");
        byte[] original=disk.read(disk.find(root,"TEST.TXT"));command(vm,"APPEND TEST.TXT MORE");
        check(Arrays.equals(original,disk.read(disk.find(root,"TEST.TXT")))&&disk.freeClusters()==free,"failed append retains old file");
        disk.remove(fill);command(vm,"WRITE RECOVER.TXT OK");check(disk.find(root,"RECOVER.TXT")!=null,"shell recovers after full disk");disk.validate();
        Path second=Path.of("build/jvfs-drive-regression.img");Storage.mount(1,second);JvFileSystem.device(1).format();
        command(vm,"DRIVE 1");command(vm,"WRITE SECOND.TXT YES");command(vm,"DRIVE 256");command(vm,"DRIVE 2");command(vm,"WRITE STILL.TXT HERE");
        check(JvFileSystem.device(1).find(root,"SECOND.TXT")!=null&&JvFileSystem.device(1).find(root,"STILL.TXT")!=null&&disk.find(root,"STILL.TXT")==null,"drive selection and invalid IDs preserve device isolation");command(vm,"DRIVE 0");
    }
    public static void main(String[] args)throws Exception {
        Path image=Path.of(args.length==0?"build/jvfs-regression.img":args[0]);Files.createDirectories(image.toAbsolutePath().getParent());
        Storage.load(image);JvFileSystem.device(0).format();DiskInstaller.installIfMissing();
        Object vm=terminal();var disk=JvFileSystem.device(0);
        command(vm,"WRITE TEST.TXT HELLO");var file=disk.find(JvFileSystem.ROOT,"TEST.TXT");
        check(file!=null&&Arrays.equals(disk.read(file),"HELLO".getBytes(StandardCharsets.US_ASCII)),"guest WRITE is readable by tooling");
        command(vm,"APPEND TEST.TXT WORLD");check(new String(disk.read(disk.find(JvFileSystem.ROOT,"TEST.TXT")),StandardCharsets.US_ASCII).equals("HELLOWORLD"),"guest APPEND");
        command(vm,"COPY TEST.TXT COPY.TXT");check(disk.find(JvFileSystem.ROOT,"COPY.TXT")!=null,"guest COPY");
        command(vm,"MD DOCS");var docs=disk.find(JvFileSystem.ROOT,"DOCS");check(docs!=null&&docs.type()==4,"guest MKDIR");
        command(vm,"CD DOCS");command(vm,"WRITE CHILD.TXT INSIDE");check(disk.find(docs.first(),"CHILD.TXT")!=null,"guest subdirectory WRITE");
        command(vm,"CD ..");command(vm,"DIR");command(vm,"DEL COPY.TXT");check(disk.find(JvFileSystem.ROOT,"COPY.TXT")==null,"guest DELETE");
        command(vm,"REN TEST.TXT RENAMED.TXT");check(disk.find(JvFileSystem.ROOT,"RENAMED.TXT")!=null,"guest RENAME");
        command(vm,"MOVE RENAMED.TXT DOCS");check(disk.find(JvFileSystem.ROOT,"RENAMED.TXT")==null&&disk.find(docs.first(),"RENAMED.TXT")!=null,"guest MOVE into directory");
        command(vm,"CD DOCS");command(vm,"MOVE RENAMED.TXT ..");command(vm,"CD ..");check(disk.find(JvFileSystem.ROOT,"RENAMED.TXT")!=null,"guest MOVE to parent");
        command(vm,"RD DOCS");check(disk.find(JvFileSystem.ROOT,"DOCS")!=null,"nonempty directory is retained");command(vm,"CD DOCS");command(vm,"DEL CHILD.TXT");command(vm,"CD ..");command(vm,"RD DOCS");check(disk.find(JvFileSystem.ROOT,"DOCS")==null,"empty directory deletion");
        byte[] large=new byte[140000];for(int i=0;i<large.length;i++)large[i]=(byte)('A'+i%26);
        disk.put(JvFileSystem.ROOT,"LARGE.TXT",large,1);command(vm,"COPY LARGE.TXT LARGE2.TXT");
        check(Arrays.equals(large,disk.read(disk.find(JvFileSystem.ROOT,"LARGE2.TXT"))),"guest COPY crosses banks and 16-bit file offsets");
        command(vm,"VIEW LARGE.TXT");invoke(vm,"press",new Class<?>[]{int[].class},(Object)new int[]{34,33,34,36,27});
        command(vm,"WRITE CHECK.TXT OK");check(Arrays.equals(disk.read(disk.find(JvFileSystem.ROOT,"CHECK.TXT")),new byte[]{'O','K'}),"short write after large viewer has an exact length");
        command(vm,"JCTRL");var regs=DosStorageRegression.class.getDeclaredField("r");regs.setAccessible(true);check(((int[])regs.get(vm))[11]==1,"new filesystem launches JCTRL");
        int row=0;var entries=disk.list(JvFileSystem.ROOT);while(!entries.get(row).name().equals("LARGE.TXT"))row++;
        invoke(vm,"click",new Class<?>[]{int.class,int.class},20,53+row*6);
        check(ram(vm)[480][9]==1&&ram(vm)[480][58]==0,"JCTRL opens JVFS text through shared services");
        invoke(vm,"press",new Class<?>[]{int[].class},(Object)new int[]{34,36});check(ram(vm)[480][58]==0,"desktop repaints and repages an open stream without a handle error");
        invoke(vm,"press",new Class<?>[]{int[].class},(Object)new int[]{115});
        command(vm,"WRITE WINDOW.TXT HELLO");for(int i=0;i<250;i++)invoke(vm,"press",new Class<?>[]{int[].class},(Object)new int[]{0});
        check(Arrays.equals(disk.read(disk.find(JvFileSystem.ROOT,"WINDOW.TXT")),"HELLO".getBytes(StandardCharsets.US_ASCII)),"windowed DOS commits the complete file safely");
        invoke(vm,"snapshot",new Class<?>[]{String.class},"jvfs-desktop");
        Storage.save();vm=terminal();command(vm,"WRITE TEST.TXT HELLO");storageChecks(vm);Storage.save();
        MonitorRegression.run();FramePacerRegression.run();MemoryRegression.run();DesktopAssemblyRegression.run();
        invoke(null,"sharedGraphicsChecks",new Class<?>[0]);invoke(null,"shellNavigationChecks",new Class<?>[0]);invoke(null,"desktopCursorChecks",new Class<?>[0]);
        disk.format();DiskInstaller.installIfMissing();invoke(null,"pongChecks",new Class<?>[0]);appChecks();Storage.save();
        DesktopInstallRegression.run(image);PeripheralRegression.run(image);
        System.out.println("PASS: native guest JVFS, large files/directories, partial I/O, corruption, full-disk recovery, desktop and programs");
    }
}
