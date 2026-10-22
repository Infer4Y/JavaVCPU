package xyz.ignite4inferneo.jvdos;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import xyz.ignite4inferneo.*;
import static xyz.ignite4inferneo.OPCode.*;
import static xyz.ignite4inferneo.jvdos.DesktopServices.*;

/** Exercises reusable services from independent COM clients, not browser internals. */
public final class DesktopServicesRegression {
    private static DesktopImage.Asm s;
    private static final int A=0,B=1,C=2,D=3,E=4,F=5;
    private static Object vm;
    private static final Class<?> VM=DosStorageRegression.class;
    private static Object invoke(String name,Class<?>[] types,Object... args)throws Exception {
        Method method=VM.getDeclaredMethod(name,types);method.setAccessible(true);
        try{return method.invoke(vm,args);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Error e)throw e;if(failure.getCause() instanceof Exception e)throw e;throw failure;}
    }
    private static Object field(String name)throws Exception {Field f=VM.getDeclaredField(name);f.setAccessible(true);return f.get(vm);}
    private static byte[][] ram()throws Exception{return (byte[][])field("ram");}
    private static void press(int key)throws Exception {invoke("press",new Class[]{int[].class},(Object)new int[]{key});}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static void call(int operation){
        s.store(operation,A);String back=s.unique("api.return");s.store(1,17);s.ref(back,true,18);s.ref(back,false,19);
        // F is a GRAPHICS_LINE input; preserve K while using it for the entry address.
        s.emit(STORE_MEM_ABSOLUTE,14,1,31,119);s.reserve(13);s.store(0,6);s.store(249,7);s.store(ENTRY,14);s.emit(CHANGE_BANK_PAGE_JUMP,6,7,14);s.label(back);s.emit(LOAD_MEM_ABSOLUTE,1,31,119,14);
    }
    private static void result(int index,int register){s.emit(STORE_MEM_ABSOLUTE,register,1,31,64+index);}
    private static void span(int page,int offset,int count,int result){s.store(page,B);s.store(offset,C);s.store(count,D);call(TEXT);result(result,A);}
    private static byte[] probe(){return DesktopImage.assembleOptimized(asm->{
        s=asm;
        s.store(1,E);s.store(30,F);s.store(0,C);s.store('X',D);s.label("fill");s.emit(STORE_MEM_ON_BANK,D,E,F,C);s.emit(INCREMENT_REGISTER,C);s.notEqual(C,0,"fill");
        s.store(31,F);s.store(240,C);s.store('Y',D);s.label("tail");s.emit(STORE_MEM_ON_BANK,D,E,F,C);s.emit(INCREMENT_REGISTER,C);s.notEqual(C,0,"tail");
        for(int r:new int[]{4,12,13,14,15,16})s.store(190+r,r);
        call(QUERY);result(0,D);
        span(30,0,0,1);span(31,240,16,2);span(31,240,17,3);span(31,1,0,4);span(32,0,1,5);
        String literal=s.strings.computeIfAbsent("SHARED",t->s.unique("packed"));s.ref(literal,true,B);s.ref(literal,false,C);call(PACKED_TEXT);result(6,A);
        s.store(16,B);s.store(0,C);call(KEYBOARD);result(7,C);result(8,D);
        s.store(59,B);call(KEYBOARD);result(9,D);
        s.store(144,B);call(KEYBOARD);result(10,C);
        s.store(97,B);call(KEYBOARD);result(11,D);
        s.store(27,B);call(KEYBOARD);result(12,D);
        call(255);result(13,A);
        s.store(31,B);s.store(31,C);call(CACHE_BEGIN);result(14,A);
        s.store(30,B);s.store(31,C);call(CACHE_BEGIN);result(15,A);
        s.store(0,B);s.store(27,C);call(CACHE_NAVIGATE);result(16,A);
        s.store(27,B);s.store(30,C);call(CACHE_BEGIN);result(17,D);
        s.store(31,B);s.store(1,C);call(CACHE_FLUSH);result(18,A);
        call(CACHE_ABORT);result(19,D);
        int i=20;for(int r:new int[]{4,12,13,14,15,16})result(i++,r);
        s.emit(STOP);
    }).bytes();}
    private static byte[] benchmark(boolean bulk){return DesktopImage.assembleOptimized(asm->{
        s=asm;s.jump("start");s.label("print.return");s.store(1,A);s.emit(CHANGE_BANK_PAGE_JUMP,A,12,13);s.label("start");
        s.store(1,E);s.store(30,F);s.store(0,C);s.store('X',D);s.label("fill");s.emit(STORE_MEM_ON_BANK,D,E,F,C);s.emit(INCREMENT_REGISTER,C);s.notEqual(C,0,"fill");
        s.store(1,D);s.emit(STORE_MEM_ABSOLUTE,D,1,31,120);
        s.label("wait");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.notEqual(D,'G',"wait");
        if(bulk){s.store(30,B);s.store(0,C);s.store(0,D);call(TEXT);}
        else {
            s.store(0,D);s.emit(STORE_MEM_ABSOLUTE,D,1,31,122);s.label("character");s.store('X',D);
            s.ref("printed",true,12);s.ref("printed",false,13);s.store(248,A);s.ref("print.return",false,B);
            s.reserve(13);s.store(0,6);s.store(249,7);s.store(0,F);s.emit(CHANGE_BANK_PAGE_JUMP,6,7,F);s.label("printed");
            s.emit(LOAD_MEM_ABSOLUTE,1,31,122,A);s.emit(INCREMENT_REGISTER,A);s.emit(STORE_MEM_ABSOLUTE,A,1,31,122);s.notEqual(A,0,"character");
        }
        s.store(1,D);s.emit(STORE_MEM_ABSOLUTE,D,1,31,121);s.label("finished");s.emit(DISPLAY);s.jump("finished");
    }).bytes();}

    private static byte[] extended(boolean invalid){return DesktopImage.assembleOptimized(asm->{
        s=asm;s.mailboxHole=true;s.jump("extended.code");
        while(s.page<40){s.reserve(247);s.store(0,A);}
        s.label("extended.code");s.store(1,E);
        if(invalid){s.emit(LOAD_MEM_ABSOLUTE,1,96,250,D);s.emit(STOP);return;}
        call(DOCUMENT);s.store('A',D);s.emit(STORE_MEM_ABSOLUTE,D,1,90,255);
        span(90,255,1,0);span(90,255,2,1);span(91,0,1,2);
        s.emit(LOAD_MEM_ABSOLUTE,1,90,255,D);result(3,D);String wire=s.strings.computeIfAbsent("PROBE",t->s.unique("wire"));s.ref(wire,true,B);s.ref(wire,false,C);call(NETWORK_PACKED);result(4,A);
        s.store(82,B);call(DOCUMENT_FRAME);result(5,A);
        s.store(27,B);call(DOCUMENT_FRAME);result(6,A);
        s.store(81,B);call(DOCUMENT_FRAME);result(7,A);s.emit(STOP);
    }).bytes();}
    private static void graphics(int page,int offset,int row,int direction,int index){
        s.store(page,B);s.store(offset,C);s.store(row,E);s.store(direction,12);call(GRAPHICS_SPAN);result(index,A);
    }
    private static void line(int x,int y,int endX,int endY,int color,int size,int index){
        s.store(x,B);s.store(y,C);s.store(endX,D);s.store(endY,E);s.store(color,F);s.store(size,12);call(GRAPHICS_LINE);result(index,A);
    }
    private static byte[] graphicsProbe(){return DesktopImage.assembleOptimized(asm->{
        s=asm;s.emit(CLEAR_GRA);
        s.store(1,13);s.store(30,14);s.store(0,15);s.store(37,D);s.label("graphics.fill");s.emit(STORE_MEM_ON_BANK,D,13,14,15);s.emit(INCREMENT_REGISTER,15);s.notEqual(15,0,"graphics.fill");
        graphics(30,128,127,1,0);graphics(29,128,127,0,1);
        graphics(31,0,0,1,2);graphics(32,0,0,0,3);graphics(30,129,0,1,4);graphics(30,0,128,1,5);graphics(30,0,0,2,6);
        s.store(128,D);s.emit(STORE_MEM_ABSOLUTE,D,1,30,127);graphics(30,0,64,1,7);
        s.store(31,B);s.store(20,C);call(GRAPHICS_CURSOR);result(8,A);
        s.store(32,B);call(GRAPHICS_CURSOR);result(9,A);s.store(0,B);s.store(21,C);call(GRAPHICS_CURSOR);result(10,A);
        // Virtual registers outside the documented outputs survive the native loop.
        s.store(204,13);s.store(205,14);s.store(206,15);s.store(207,16);graphics(29,0,127,0,11);
        for(int n=0;n<4;n++)result(12+n,13+n);
        graphics(29,0,64,0,18);
        line(7,40,13,42,96,2,19);
        line(128,40,13,42,36,2,20);line(7,128,13,42,36,2,21);line(7,40,128,42,36,2,22);line(7,40,13,128,36,2,23);
        line(7,40,13,42,128,2,24);line(7,40,13,42,36,0,25);line(7,40,13,42,36,3,26);line(7,40,13,42,36,255,27);
        line(7,40,13,42,96,2,28);int[] preserved={1,2,3,4,5,12,13,14,15,16};for(int n=0;n<preserved.length;n++)result(29+n,preserved[n]);
        call(DOCUMENT);graphics(30,0,0,0,16);s.store(0,B);s.store(0,C);call(GRAPHICS_CURSOR);result(17,A);line(7,40,13,42,36,2,39);s.emit(STOP);
    }).bytes();}

    private static byte[] graphicsBenchmark(boolean bulk){return DesktopImage.assembleOptimized(asm->{
        // G avoids the desktop's Space/Enter navigation shortcuts and their repaint.
        s=asm;s.store(1,D);s.emit(STORE_MEM_ABSOLUTE,D,1,31,120);s.label("graphics.wait");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.notEqual(D,'G',"graphics.wait");
        if(bulk)graphics(30,0,127,0,0);
        else {
            s.store(0,12);s.store(254,13);s.store(2,14);s.store(1,15);s.store(30,16);s.store(0,C);
            s.label("graphics.legacy");s.emit(READ_GRA_MEM,12,13,D);s.emit(STORE_MEM_ON_BANK,D,15,16,C);s.emit(ADD,12,14,12);s.emit(INCREMENT_REGISTER,C);s.notEqual(C,128,"graphics.legacy");
        }
        s.store(1,D);s.emit(STORE_MEM_ABSOLUTE,D,1,31,121);s.label("graphics.finished");s.emit(DISPLAY);s.jump("graphics.finished");
    }).bytes();}
    private static long measureGraphics(String name)throws Exception {
        launch(name);for(int i=0;i<3000&&ram()[415][120]!=1;i++)press(0);check(ram()[415][120]==1,"pixel benchmark ready");
        long before=(long)field("instructions");press('G');
        for(int i=0;i<3000&&ram()[415][121]!=1;i++)press(0);check(ram()[415][121]==1,"pixel benchmark completes");
        long cost=(long)field("instructions")-before;press(27);return cost;
    }
    private static void launch(String name)throws Exception {
        vm=invoke("terminal",new Class[]{});invoke("command",new Class[]{String.class},"JCTRL");
        var entries=JvFileSystem.device(0).list(JvFileSystem.ROOT);int row=0;while(!entries.get(row).name().equals(name))row++;
        invoke("click",new Class[]{int.class,int.class},20,53+row*6);
    }
    private static long[] measure(String name)throws Exception {
        launch(name);for(int i=0;i<3000&&(ram()[415][120]&255)!=1;i++)press(0);check((ram()[415][120]&255)==1,"benchmark ready");
        long instructions=(long)field("instructions"),pixels=(long)field("pixelWrites");press('G');
        for(int i=0;i<3000&&ram()[415][121]!=1;i++)press(0);check(ram()[415][121]==1,"benchmark finishes");
        long[] cost={(long)field("instructions")-instructions,(long)field("pixelWrites")-pixels};press(27);return cost;
    }
    public static void main(String[] args)throws Exception {
        Path image=Path.of("build/desktop-services-regression.img");Files.createDirectories(image.getParent());
        Storage.load(image);JvFileSystem.device(0).format();DiskInstaller.installIfMissing();
        var disk=JvFileSystem.device(0);disk.put(JvFileSystem.ROOT,"APISVC.COM",probe(),0);
        launch("APISVC.COM");for(int i=0;i<3000&&(ram()[481][0]&255)!=0;i++)press(0);
        check((ram()[481][0]&255)==0&&(ram()[481][1]&255)==1,"service probe completes normally");
        int[] expected={VERSION,0,0,1,1,1,0,1,0,':',0,'1',0,1,1,1,1,1,1,0,194,202,203,204,205,206};
        for(int i=0;i<expected.length;i++)check((ram()[415][64+i]&255)==expected[i],"service result "+i+": "+(ram()[415][64+i]&255)+" expected "+expected[i]);
        check((ram()[481][28]&255)==22&&(ram()[481][29]&255)==8,"invalid text spans draw no partial output");
        var folder=disk.find(JvFileSystem.ROOT,"WEB.CACHE");check(folder!=null&&disk.list(folder.first()).isEmpty(),"abort removes uncommitted cache entry");
        disk.put(JvFileSystem.ROOT,"EXTEND.COM",extended(false),0);launch("EXTEND.COM");
        for(int i=0;i<3000&&(ram()[481][0]&255)!=0;i++)press(0);
        check((ram()[481][1]&255)==1,"extended document code executes beyond virtual page 31");
        int[] extendedExpected={0,1,1,'A',0,1,1,0};for(int i=0;i<extendedExpected.length;i++)check((ram()[415][64+i]&255)==extendedExpected[i],"extended span validation "+i);
        check((ram()[481][28]&255)==1,"network packed helper does not draw text");
        check((ram()[481][445-256]&255)==91,"extended client receives bounded document RAM");
        disk.put(JvFileSystem.ROOT,"BADPAGE.COM",extended(true),0);launch("BADPAGE.COM");ram()[480][250]=(byte)182;
        for(int i=0;i<3000&&(ram()[481][0]&255)!=0;i++)press(0);
        check((ram()[481][1]&255)==6&&(ram()[480][250]&255)==182,"extended clients cannot address desktop RAM");
        disk.put(JvFileSystem.ROOT,"CHAR.COM",benchmark(false),0);disk.put(JvFileSystem.ROOT,"BLOCK.COM",benchmark(true),0);
        long[] legacy=measure("CHAR.COM"),bulk=measure("BLOCK.COM");
        System.out.println("256 glyphs: legacy="+legacy[0]+" instructions / "+legacy[1]+" pixel writes; shared="+bulk[0]+" / "+bulk[1]);
        check(bulk[0]*3<legacy[0],"shared renderer reduces execution overhead by at least 3x");
        check(bulk[1]<=legacy[1],"shared renderer introduces no extra pixel writes");
        disk.put(JvFileSystem.ROOT,"GFXSVC.COM",graphicsProbe(),0);launch("GFXSVC.COM");
        for(int i=0;i<3000&&(ram()[481][0]&255)!=0;i++)press(0);
        int[] graphicsExpected={0,0,1,1,1,1,1,1,0,1,1,0,204,205,206,207,1,1,0};
        for(int i=0;i<graphicsExpected.length;i++)check((ram()[415][64+i]&255)==graphicsExpected[i],"graphics result "+i);
        for(int i=0;i<128;i++)check((ram()[413][128+i]&255)==37&&ram()[413][i]==0,"row read boundary and no partial invalid write");
        int[] lineExpected={0,1,1,1,1,1,1,1,1,0,7,40,13,42,96,2,204,205,206,207,1};
        for(int i=0;i<lineExpected.length;i++)check((ram()[415][83+i]&255)==lineExpected[i],"line argument validation / preservation "+i);
        int[][] expectedPixels=new int[118][128];
        for(int[] point:new int[][]{{7,40},{8,40},{9,40},{10,41},{11,41},{12,41},{13,42}})
            for(int y=point[1];y<point[1]+2;y++)for(int x=point[0];x<point[0]+2;x++)expectedPixels[y][x]=96;
        for(int y=0;y<118;y++)for(int x=0;x<128;x++)check((ram()[416+(y*128+x)/256][(y*128+x)&255]&255)==expectedPixels[y][x],"connected line and atomic invalid arguments at "+x+","+y);
        disk.put(JvFileSystem.ROOT,"BADREG.COM",new byte[]{2,1,20,4},0);launch("BADREG.COM");
        for(int i=0;i<100&&(ram()[481][0]&255)!=0;i++)press(0);check((ram()[481][1]&255)==6,"shared register write still rejects register 20");
        disk.put(JvFileSystem.ROOT,"BADSRC.COM",new byte[]{0,20,0,0,4},0);launch("BADSRC.COM");
        for(int i=0;i<100&&(ram()[481][0]&255)!=0;i++)press(0);check((ram()[481][1]&255)==6,"shared register read still rejects register 20");
        disk.put(JvFileSystem.ROOT,"TRUNC.COM",new byte[]{2,42},0);launch("TRUNC.COM");
        for(int i=0;i<100&&(ram()[481][0]&255)!=0;i++)press(0);check((ram()[481][1]&255)==6,"operand fetch rejects the end of the loaded file");
        byte[] page=new byte[512];Arrays.fill(page,(byte)4);byte[] jump={2,1,0,2,0,1,10,0,1};System.arraycopy(jump,0,page,0,jump.length);
        System.arraycopy(new byte[]{2,99,0,4},0,page,256,4);disk.put(JvFileSystem.ROOT,"PAGE.COM",page,0);launch("PAGE.COM");
        for(int i=0;i<100&&(ram()[481][0]&255)!=0;i++)press(0);check((ram()[481][1]&255)==1&&(ram()[484][0]&255)==99,"instruction fetch remaps after a page jump");
        Arrays.fill(page,(byte)4);jump=new byte[]{2,0,0,2,(byte)255,1,10,0,1};System.arraycopy(jump,0,page,0,jump.length);page[255]=2;
        disk.put(JvFileSystem.ROOT,"WRAP.COM",page,0);launch("WRAP.COM");
        for(int i=0;i<100&&(ram()[481][0]&255)!=0;i++)press(0);check((ram()[481][1]&255)==6,"operand fetch rejects a page boundary crossing");
        disk.put(JvFileSystem.ROOT,"GFXOLD.COM",graphicsBenchmark(false),0);disk.put(JvFileSystem.ROOT,"GFXNEW.COM",graphicsBenchmark(true),0);
        long oldPixels=measureGraphics("GFXOLD.COM"),newPixels=measureGraphics("GFXNEW.COM");
        check(newPixels*3<oldPixels,"shared pixel row read reduces guest execution by at least 3x");
        System.out.println("128 pixels: interpreted="+oldPixels+" instructions; shared="+newPixels);
        System.out.println("PASS: shared ABI bounds, keyboard, register preservation, cache abort and bulk rendering benchmark");
    }
}
