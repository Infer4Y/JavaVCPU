package xyz.ignite4inferneo.jvdos;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import xyz.ignite4inferneo.*;

/** Runs TEXTEDIT.COM through the real desktop interpreter and resident JVFS ABI. */
public final class TextEditRegression {
    private static final Class<?> VM=DosStorageRegression.class;
    private static Object vm;
    private static Object invoke(String name,Class<?>[] types,Object... args)throws Exception {
        Method m=VM.getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(vm,args);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Error e)throw e;if(failure.getCause() instanceof Exception e)throw e;throw failure;}
    }
    private static byte[][] ram()throws Exception {Field f=VM.getDeclaredField("ram");f.setAccessible(true);return (byte[][])f.get(vm);}
    private static int state(int slot){try{return ram()[384+TextEditImage.STATE][slot]&255;}catch(Exception e){throw new RuntimeException(e);}}
    private static int pair(int slot){return state(slot)|(state(slot+1)<<8);}
    private static void press(int key)throws Exception {invoke("press",new Class[]{int[].class},(Object)new int[]{key});}
    private static void frames(int count)throws Exception {for(int i=0;i<count;i++)press(0);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void until(BooleanSupplier condition,String message)throws Exception {
        for(int n=0;n<6000;n++){if(condition.getAsBoolean())return;press(0);}
        throw new AssertionError(message+" mode="+state(TextEditImage.MODE)+" phase="+state(TextEditImage.PHASE)+" length="+pair(TextEditImage.LENGTH));
    }
    private static void ready()throws Exception {until(()->state(TextEditImage.PHASE)==2,"editor ready");frames(4);}
    private static void key(int key)throws Exception {press(key);frames(160);ready();}
    private static void type(String text)throws Exception {
        for(char c:text.toCharArray()) {
            if(c>='A'&&c<='Z'){key(16);key(c);key(144);}
            else if(c>='a'&&c<='z')key(Character.toUpperCase(c));
            else if(c=='!'){key(16);key(49);key(144);}
            else key(c=='\n'?10:c);
        }
    }
    private static void open(String filename)throws Exception {key(117);type(filename.toLowerCase());key(10);}
    private static byte[] buffer()throws Exception {byte[] bytes=new byte[pair(TextEditImage.LENGTH)];for(int i=0;i<bytes.length;i++)bytes[i]=ram()[384+TextEditImage.BUFFER+i/256][i&255];return bytes;}
    private static String text()throws Exception{return new String(buffer(),StandardCharsets.US_ASCII);}
    private static int color(int cell)throws Exception {return ram()[384+TextEditImage.COLORS+cell/256][cell&255]&255;}
    private static byte[] body()throws Exception {
        byte[] bytes=new byte[1920];for(int i=0;i<960;i++){int cell=144+i;bytes[i]=ram()[384+TextEditImage.SCREEN+cell/256][cell&255];bytes[960+i]=ram()[384+TextEditImage.COLORS+cell/256][cell&255];}return bytes;
    }
    private static long metric(String name)throws Exception {Field field=VM.getDeclaredField(name);field.setAccessible(true);return field.getLong(vm);}
    private static long[] measure(int key,int length)throws Exception {
        long instructions=metric("instructions"),pixels=metric("pixelWrites"),displays=metric("displayFrames");int ticks=0;
        press(key);
        while(++ticks<6000) {
            if(state(TextEditImage.PHASE)==2&&pair(TextEditImage.CURSOR)==1&&pair(TextEditImage.LENGTH)==length
                    &&ram()[384+TextEditImage.SCREEN][145]=='|')break;
            press(0);
        }
        check(ticks<6000,"measured edit completes");
        return new long[]{metric("instructions")-instructions,metric("pixelWrites")-pixels,metric("displayFrames")-displays,ticks};
    }
    private static void performance(JvFileSystem.Volume disk)throws Exception {
        byte[] source="STORE 6 A\n".repeat(80).getBytes(StandardCharsets.US_ASCII);
        disk.put(JvFileSystem.ROOT,"BENCH.JVS",source,1);launch();type("bench.jvs");key(10);
        long[] motion=measure(39,source.length);key(37);long[] insertion=measure('X',source.length+1);
        check(buffer()[0]=='x',"benchmark inserts at beginning");
        check(motion[3]<=2&&insertion[3]<=8,"bounded interactive latency");
        check(motion[0]<4_000_000&&insertion[0]<15_000_000,"cursor cache and line redraw reduce CPU work");
        System.out.println("TEXTEDIT motion / insert [instructions, pixels, displays, ticks]: "+Arrays.toString(motion)+" / "+Arrays.toString(insertion));
    }
    private static void launch()throws Exception {
        vm=invoke("terminal",new Class[]{});invoke("command",new Class[]{String.class},"JCTRL");
        var entries=JvFileSystem.device(0).list(JvFileSystem.ROOT);int row=0;while(!entries.get(row).name().equals("TEXTEDIT.COM"))row++;
        invoke("click",new Class[]{int.class,int.class},20,53+row*6);ready();
        check(state(TextEditImage.MODE)==4,"startup filename prompt");
    }
    public static void main(String[] args)throws Exception {
        Path image=Path.of("build/textedit-regression.img");Files.createDirectories(image.getParent());Storage.load(image);
        var disk=JvFileSystem.device(0);disk.format();DiskInstaller.installIfMissing();
        check(Arrays.equals(disk.read(disk.find(JvFileSystem.ROOT,"TEXTEDIT.COM")),TextEditImage.program()),"boot bundles the editor");
        byte[] original="STORE 6 A\nREM comment\nPRINT hello\nEND\n".getBytes(StandardCharsets.US_ASCII);
        disk.put(JvFileSystem.ROOT,"SOURCE.JVS",original,1);
        disk.put(JvFileSystem.ROOT,"BYTES.HEX","02 2A 00 # comment\n".getBytes(StandardCharsets.US_ASCII),1);
        byte[] binary={2,42,0,5,0,4,(byte)255};disk.put(JvFileSystem.ROOT,"TINY.COM",binary,0);
        if(args.length>0&&args[0].equals("performance")){performance(disk);return;}
        launch();type("source.jvs");key(10);
        check(Arrays.equals(buffer(),original),"opens exact source bytes");
        check(color(144)==9&&color(150)==65&&color(152)==64,"JVS keyword/number/register colors");
        check(color(192)==12&&color(240)==9&&color(246)==64,"REM and PRINT highlighting");
        check(ram()[384+TextEditImage.SCREEN+336/256][336&255]==0,"only the active caret is drawn");
        invoke("snapshot",new Class[]{String.class},"textedit-jvs");
        byte[][] before=ram();int wx=before[480][168]&255,wy=before[480][169]&255;
        invoke("mouse",new Class[]{int.class,int.class,int.class},wx+20,wy+5,0);
        invoke("mouse",new Class[]{int.class,int.class,int.class},wx+20,wy+5,1);
        invoke("mouse",new Class[]{int.class,int.class,int.class},wx+30,wy+15,1);
        invoke("mouse",new Class[]{int.class,int.class,int.class},wx+30,wy+15,0);frames(8);
        check(color(144)==9&&color(192)==12,"window move retains highlighting");
        invoke("snapshot",new Class[]{String.class},"textedit-moved");
        key(35);type("!");check(text().startsWith("STORE 6 A!\n"),"End inserts before newline");
        byte[] incremental=body();key(35);check(Arrays.equals(incremental,body()),"incremental line frame matches full redraw");
        key(8);check(Arrays.equals(buffer(),original),"backspace restores bytes");
        key(36);key(127);check(text().startsWith("TORE"),"Delete removes at cursor");type("S");
        key(40);check(pair(TextEditImage.CURSOR)==11,"Down retains visual column");key(38);check(pair(TextEditImage.CURSOR)==1,"Up retains visual column");
        key(118);check(state(TextEditImage.DIRTY)==0&&state(TextEditImage.STATUS)==1,"save reports success");check(Arrays.equals(disk.read(disk.find(JvFileSystem.ROOT,"SOURCE.JVS")),original),"source save round trip");
        open("BYTES.HEX");check(state(TextEditImage.MODE)==1,"hex source mode");check(color(144)==65&&color(156)==12,"hex digits/comments highlighted");
        key(35);type("!");key(117);check(state(TextEditImage.MODE)==3,"unsaved open asks before discarding");key('N');check(state(TextEditImage.DIRTY)==1,"cancel preserves changes");key(117);key('Y');
        type("tiny.com");key(10);check(state(TextEditImage.MODE)==2,"binary COM mode");check(Arrays.equals(buffer(),binary),"binary load preserves all bytes");
        check(color(144)==9&&color(147)==65&&color(153)==9,"COM opcode and operand colors");
        type("af");key(118);byte[] expected=binary.clone();expected[0]=(byte)0xaf;check(Arrays.equals(disk.read(disk.find(JvFileSystem.ROOT,"TINY.COM")),expected),"COM nibble editing writes raw bytes");
        open("NEW.JVS");check(pair(TextEditImage.LENGTH)==0,"missing file opens empty buffer");type("PRINT hi\nEND\n");key(118);check(new String(disk.read(disk.find(JvFileSystem.ROOT,"NEW.JVS")),StandardCharsets.US_ASCII).equals("PRINT hi\nEND\n"),"creates source file");
        byte[] boundary=("A".repeat(254)+"\r\n\t"+"B".repeat(80)).getBytes(StandardCharsets.US_ASCII);disk.put(JvFileSystem.ROOT,"BOUND.TXT",boundary,1);open("BOUND.TXT");check(Arrays.equals(buffer(),boundary),"load crosses page boundary");key(118);check(Arrays.equals(disk.read(disk.find(JvFileSystem.ROOT,"BOUND.TXT")),boundary),"CRLF/tab bytes survive save");key(35);type("x");key(118);check(disk.read(disk.find(JvFileSystem.ROOT,"BOUND.TXT")).length==boundary.length+1,"multi-page save");
        disk.put(JvFileSystem.ROOT,"LIMIT.TXT",new byte[4096],1);open("LIMIT.TXT");type("x");check(pair(TextEditImage.LENGTH)==4096&&state(TextEditImage.STATUS)==3,"full buffer refuses insertion");
        disk.put(JvFileSystem.ROOT,"TOOBIG.TXT",new byte[4097],1);open("TOOBIG.TXT");check(state(TextEditImage.STATUS)==4,"oversized file refused");check(disk.read(disk.find(JvFileSystem.ROOT,"TOOBIG.TXT")).length==4097,"oversized file untouched");
        open("SOURCE.JVS");type("x");var fill=disk.put(JvFileSystem.ROOT,"FILL.BIN",new byte[disk.freeClusters()*1024],0);key(118);
        check(state(TextEditImage.STATUS)==5&&state(TextEditImage.DIRTY)==1,"failed save retains dirty buffer");
        check(Arrays.equals(disk.read(disk.find(JvFileSystem.ROOT,"SOURCE.JVS")),original),"full-disk save preserves original file");disk.remove(fill);
        disk.validate();press(27);frames(32);check((ram()[481][0]&255)==0,"Escape returns to desktop");
        System.out.println("PASS: TEXTEDIT guest editing, navigation, highlighting, unsaved guard, binary fidelity, page boundaries and JVFS save/create");
    }
}
