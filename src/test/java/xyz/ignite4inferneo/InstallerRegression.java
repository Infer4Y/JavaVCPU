package xyz.ignite4inferneo;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import xyz.ignite4inferneo.jvdos.*;

/** External-media portable installs and setup execute through the real resident DOS. */
public final class InstallerRegression {
    private static Object vm;
    private static Object invoke(String name,Class<?>[] types,Object... args)throws Exception {
        Method m=DosStorageRegression.class.getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(vm,args);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Error e)throw e;if(failure.getCause() instanceof Exception e)throw e;throw failure;}
    }
    private static byte[][] ram()throws Exception{Field f=DosStorageRegression.class.getDeclaredField("ram");f.setAccessible(true);return (byte[][])f.get(vm);}
    private static int drive()throws Exception{Field f=DosStorageRegression.class.getDeclaredField("r");f.setAccessible(true);return ((int[])f.get(vm))[10];}
    private static void command(String command)throws Exception {
        var keys=new ArrayList<Integer>();
        for(char c:command.toCharArray())if(c==':'){keys.add(16);keys.add(59);keys.add(144);}else keys.add((int)c);
        keys.add(10);invoke("press",new Class[]{int[].class},(Object)keys.stream().mapToInt(Integer::intValue).toArray());
    }
    private static void key(int key)throws Exception{invoke("press",new Class[]{int[].class},(Object)new int[]{key});}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static byte[] read(JvFileSystem.Volume disk,int folder,String name){return disk.read(disk.find(folder,name));}
    private static int phase()throws Exception{return ram()[248][InstallerImage.PHASE]&255;}
    private static void setup(int answer)throws Exception{command("RUN JCTRLSET.COM");check(phase()==1,"setup asks before writing");if(answer==27)invoke("snapshot",new Class[]{String.class},"jctrl-setup");key(answer);check(drive()==1,"setup restores caller drive");}
    public static void main(String[] args)throws Exception {
        Path image=Path.of("build/installer-regression.img"),external=Path.of("build/installer-external.img");Files.deleteIfExists(image);Files.deleteIfExists(external);Storage.load(image);DiskInstaller.installIfMissing();Storage.mount(1,external);
        var internal=JvFileSystem.device(0);var source=JvFileSystem.device(1);source.format();int media=source.mkdir(JvFileSystem.ROOT,"MEDIA").first();
        byte[] payload=new byte[70003];for(int i=0;i<payload.length;i++)payload[i]=(byte)(i*31);source.put(media,"TOOL.COM",payload,0);source.put(media,"MOVE.COM",new byte[]{2,42,0,4},0);
        vm=invoke("terminal",new Class[]{});command("DRIVE 1");command("CD MEDIA");command("INSTALL TOOL.COM");
        check(Arrays.equals(read(internal,JvFileSystem.ROOT,"TOOL.COM"),payload)&&Arrays.equals(read(source,media,"TOOL.COM"),payload),"portable install streams beyond RAM / 64K and keeps source");check(drive()==1,"portable install restores source drive");
        command("MV MOVE.COM 0:");check(source.find(media,"MOVE.COM")==null&&Arrays.equals(read(internal,JvFileSystem.ROOT,"MOVE.COM"),new byte[]{2,42,0,4}),"cross-drive move commits before source deletion");
        source.put(media,"MOVE.COM",new byte[]{4},0);command("MV MOVE.COM 0:");check(source.find(media,"MOVE.COM")!=null&&read(internal,JvFileSystem.ROOT,"MOVE.COM").length==4,"destination conflict preserves both files");
        source.put(media,"BAD.TXT",new byte[]{4},1);command("INSTALL BAD.TXT");check(internal.find(JvFileSystem.ROOT,"BAD.TXT")==null,"text is not installed as an executable");
        source.put(media,"ARGS.COM",new byte[]{4},0);command("INSTALL ARGS.COM EXTRA");command("MV ARGS.COM 1:");check(internal.find(JvFileSystem.ROOT,"ARGS.COM")==null&&source.find(media,"ARGS.COM")!=null,"invalid arguments do not move files");
        var fill=internal.put(JvFileSystem.ROOT,"FILL.BIN",new byte[internal.freeClusters()*1024],0);command("MV ARGS.COM 0:");check(source.find(media,"ARGS.COM")!=null&&internal.find(JvFileSystem.ROOT,"ARGS.COM")==null&&drive()==1,"failed move retains source and removes staged destination");internal.remove(fill);command("MV ARGS.COM 0:");check(source.find(media,"ARGS.COM")==null,"move recovers after full disk");
        command("WRITE STILL.TXT HERE");check(source.find(media,"STILL.TXT")!=null,"source folder survives install and move");
        byte[] setup=InstallerImage.program(DesktopBundle.components());check(setup.length<=61200,"bundle fits native COM loader");source.put(media,"JCTRLSET.COM",setup,0);Storage.save();byte[] sourceBefore=Files.readAllBytes(external);
        for(var component:DesktopBundle.components())internal.remove(internal.find(JvFileSystem.ROOT,component.name()));
        byte[] before=Storage.readRange(0,Storage.STORAGE_SIZE);setup(27);check(Arrays.equals(before,Storage.readRange(0,Storage.STORAGE_SIZE)),"cancelled setup makes no disk writes");
        setup('Y');check(phase()==2,"setup finishes");for(var component:DesktopBundle.components())check(Arrays.equals(component.bytes(),read(internal,JvFileSystem.ROOT,component.name())),"bundle installs exact "+component.name());
        internal.put(JvFileSystem.ROOT,"PAINT.COM",new byte[]{4},0);setup('Y');check(read(internal,JvFileSystem.ROOT,"PAINT.COM").length==1,"default setup preserves existing programs");setup('R');check(Arrays.equals(PaintImage.program(),read(internal,JvFileSystem.ROOT,"PAINT.COM")),"explicit replacement updates paint");
        fill=internal.put(JvFileSystem.ROOT,"FILL.BIN",new byte[internal.freeClusters()*1024],0);setup('R');check(phase()==3,"full disk stops replacement");for(var component:DesktopBundle.components())check(Arrays.equals(component.bytes(),read(internal,JvFileSystem.ROOT,component.name())),"failed replacement retains "+component.name());internal.remove(fill);
        internal.remove(internal.find(JvFileSystem.ROOT,"PAINT.COM"));fill=internal.put(JvFileSystem.ROOT,"FILL.BIN",new byte[internal.freeClusters()*1024],0);setup('Y');check(phase()==3&&internal.find(JvFileSystem.ROOT,"PAINT.COM")==null,"setup aborts failed new file");internal.remove(fill);setup('Y');check(phase()==2,"setup can resume a partial bundle");
        Storage.save();check(Arrays.equals(sourceBefore,Files.readAllBytes(external)),"wizard leaves external media untouched");
        byte[] tiny=InstallerImage.program(List.of(new DesktopBundle.Component("TINY.COM",new byte[]{2,42,0,4})));source.put(media,"TINYSET.COM",tiny,0);command("RUN TINYSET.COM");key('Y');check(Arrays.equals(read(internal,JvFileSystem.ROOT,"TINY.COM"),new byte[]{2,42,0,4}),"reusable generator writes partial final blocks exactly");
        internal.validate();source.validate();System.out.println("PASS: external-media install/move, collisions, full disk, context, setup consent, bundle fidelity, replacement and resume");
    }
}
