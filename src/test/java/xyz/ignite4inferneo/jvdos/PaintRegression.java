package xyz.ignite4inferneo.jvdos;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import xyz.ignite4inferneo.*;

/** Exercises PAINT.COM on the real desktop bytecode interpreter and JVFS services. */
public final class PaintRegression {
    private static final Class<?> VM=DosStorageRegression.class;
    private static Object vm;
    private static Object invoke(String name,Class<?>[] types,Object... args)throws Exception {
        Method m=VM.getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(vm,args);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Error e)throw e;if(failure.getCause() instanceof Exception e)throw e;throw failure;}
    }
    private static byte[][] ram()throws Exception {Field f=VM.getDeclaredField("ram");f.setAccessible(true);return (byte[][])f.get(vm);}
    private static int state(int slot){try{return ram()[384+PaintImage.STATE][slot]&255;}catch(Exception e){throw new RuntimeException(e);}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void press(int key)throws Exception {invoke("press",new Class[]{int[].class},(Object)new int[]{key});}
    private static void frames(int count)throws Exception {for(int n=0;n<count;n++)press(0);}
    private static void until(BooleanSupplier condition,String message)throws Exception {for(int n=0;n<6000;n++){if(condition.getAsBoolean())return;press(0);}throw new AssertionError(message+" phase="+state(PaintImage.PHASE)+" status="+state(PaintImage.STATUS));}
    private static void ready()throws Exception {until(()->state(PaintImage.PHASE)==1,"paint ready");}
    private static void key(int key)throws Exception {
        // Reproduce the actual make/break event stream, including the release
        // arriving while Paint is rendering a multi-frame confirmation prompt.
        press(key);press(key|128);frames(40);ready();
    }
    private static void mouse(int x,int y,int buttons)throws Exception {
        int wx=ram()[480][168]&255,wy=ram()[480][169]&255;
        invoke("mouse",new Class[]{int.class,int.class,int.class},wx+44+x,wy+28+y,buttons);frames(8);ready();
    }
    private static void click(int x,int y)throws Exception {mouse(x,y,0);mouse(x,y,1);mouse(x,y,0);}
    private static int pixel(int x,int y)throws Exception {return ram()[416+(y*128+x)/256][(y*128+x)&255]&255;}
    private static byte[] canvas()throws Exception {byte[] result=new byte[128*96];for(int n=0;n<result.length;n++)result[n]=(byte)pixel(n%128,32+n/128);return result;}
    private static void visibleCanvas()throws Exception {
        Field field=VM.getDeclaredField("pixels");field.setAccessible(true);int[][] screen=(int[][])field.get(vm);
        int wx=(ram()[480][168]&255)+44,wy=(ram()[480][169]&255)+28;
        for(int y=32;y<128;y++)for(int x=0;x<128;x++)check(screen[wy+y][wx+x]==pixel(x,y),"visible canvas/cursor background at "+x+","+y);
    }
    private static long metric(String name)throws Exception {Field f=VM.getDeclaredField(name);f.setAccessible(true);return f.getLong(vm);}
    private static void launch()throws Exception {
        vm=invoke("terminal",new Class[]{});invoke("command",new Class[]{String.class},"JCTRL");
        var list=JvFileSystem.device(0).list(JvFileSystem.ROOT);int row=0;while(!list.get(row).name().equals("PAINT.COM"))row++;
        invoke("click",new Class[]{int.class,int.class},20,53+row*6);frames(12);ready();
        check((ram()[481][1]&255)==1,"paint launches in graphics interpreter");
    }
    public static void main(String[] args)throws Exception {
        Path image=Path.of("build/paint-regression.img");Files.deleteIfExists(image);Storage.load(image);
        var disk=JvFileSystem.device(0);disk.format();DiskInstaller.installIfMissing();
        check(Arrays.equals(disk.read(disk.find(JvFileSystem.ROOT,"PAINT.COM")),PaintImage.program()),"startup bundles PAINT");
        vm=invoke("terminal",new Class[]{});invoke("command",new Class[]{String.class},"RUN PAINT.COM");check((ram()[481][0]&255)==0,"fullscreen launch returns to DOS");
        launch();for(byte p:canvas())check((p&255)==127,"blank white canvas");
        for(int n=0;n<16;n++)check(pixel(n*8+4,27)==PaintImage.PALETTE[n],"palette swatch "+n);
        click(28,27);check(state(PaintImage.COLOR)==96,"palette selects red");
        long firstStart=metric("instructions"),firstPixels=metric("pixelWrites");int firstTicks=1;
        invoke("mouse",new Class[]{int.class,int.class,int.class},(ram()[480][168]&255)+44+8,(ram()[480][169]&255)+28+44,1);
        while(firstTicks<100&&(pixel(8,44)!=96||state(PaintImage.PHASE)!=1)){press(0);firstTicks++;}
        check(firstTicks<=6&&metric("instructions")-firstStart<250_000&&metric("pixelWrites")-firstPixels<64,"first stroke and dirty indicator avoid a full status/desktop repaint");mouse(8,44,0);
        mouse(8,44,1);mouse(38,54,1);mouse(38,54,0);
        check(pixel(8,44)==96&&pixel(38,54)==96&&pixel(23,49)==96,"interpolated straight stroke has no gaps");
        check(state(PaintImage.DIRTY)==1,"stroke marks modified");
        key('2');click(45,55);check(pixel(45,55)==96&&pixel(46,56)==96&&pixel(47,57)==127,"two pixel brush footprint");
        key('E');click(45,55);check(pixel(45,55)==127&&pixel(46,56)==127,"eraser restores white");
        key('I');click(8,44);check(state(PaintImage.COLOR)==96&&state(PaintImage.TOOL)==0,"eyedropper selects exact pixel");
        key('3');click(127,127);check(pixel(127,127)==96,"brush clips at bottom right");
        key('1');mouse(80,100,1);mouse(20,40,1);mouse(20,40,0);check(pixel(50,70)==96&&pixel(20,40)==96,"reverse diagonal stroke");
        mouse(70,90,1);mouse(20,90,1);mouse(20,100,1);mouse(20,100,0);check(pixel(45,90)==96&&pixel(20,95)==96,"horizontal and vertical strokes");
        mouse(110,110,1);long start=metric("instructions"),pixelStart=metric("pixelWrites");int ticks=1;
        invoke("mouse",new Class[]{int.class,int.class,int.class},(ram()[480][168]&255)+44+111,(ram()[480][169]&255)+28+110,1);
        while(ticks<100&&(pixel(111,110)!=96||state(PaintImage.PHASE)!=1)){press(0);ticks++;}
        long cost=metric("instructions")-start,pixelCost=metric("pixelWrites")-pixelStart;check(ticks<=2&&cost<150_000&&pixelCost<32,"incremental high-coordinate brush latency");mouse(111,110,0);
        System.out.println("PAINT one-pixel motion: "+cost+" instructions / "+ticks+" ticks / "+pixelCost+" pixel writes");
        // A full-width connected stroke is one bounded native service, even at the bottom edge.
        mouse(0,127,1);start=metric("instructions");pixelStart=metric("pixelWrites");ticks=1;
        invoke("mouse",new Class[]{int.class,int.class,int.class},(ram()[480][168]&255)+44+127,(ram()[480][169]&255)+28+127,1);
        while(ticks<100&&(pixel(127,127)!=96||state(PaintImage.PHASE)!=1)){press(0);ticks++;}
        cost=metric("instructions")-start;check(ticks<=2&&cost<400_000&&metric("pixelWrites")-pixelStart<256,"bounded full-width stroke without a desktop repaint");
        for(int x=0;x<128;x++)check(pixel(x,127)==96,"full-width stroke stays connected");mouse(127,127,0);
        System.out.println("PAINT 127-pixel motion: "+cost+" instructions / "+ticks+" ticks");
        key('3');mouse(0,32,1);start=metric("instructions");ticks=1;
        invoke("mouse",new Class[]{int.class,int.class,int.class},(ram()[480][168]&255)+44+124,(ram()[480][169]&255)+28+124,1);
        while(ticks<100&&(pixel(124,124)!=96||state(PaintImage.PHASE)!=1)){press(0);ticks++;}
        cost=metric("instructions")-start;check(ticks<=2&&cost<2_000_000,"largest diagonal brush remains bounded");mouse(124,124,0);key('1');
        System.out.println("PAINT four-pixel diagonal: "+cost+" instructions / "+ticks+" ticks");
        // Paint under both saved cursor pixels, then move away; restoring the cursor must keep the new ink.
        mouse(110,110,0);mouse(109,109,1);mouse(112,112,1);mouse(112,112,0);mouse(2,2,0);visibleCanvas();
        byte[] drawing=canvas();key(119);check(state(PaintImage.PENDING)==2,"new asks before discarding");
        key(16);key(17);key(18);check(state(PaintImage.PENDING)==2,"modifiers and releases do not dismiss New");
        key('N');check(Arrays.equals(drawing,canvas()),"cancel preserves painting");
        key(118);check(state(PaintImage.STATUS)==1&&state(PaintImage.DIRTY)==0,"save succeeds");
        byte[] saved=disk.read(disk.find(JvFileSystem.ROOT,"PAINT.JVI"));check(saved.length==12292&&saved[0]=='J'&&saved[1]=='I'&&(saved[2]&255)==128&&saved[3]==96,"native JI file header and size");
        check(disk.find(JvFileSystem.ROOT,"PAINT.JVI").type()==2&&Arrays.equals(Arrays.copyOfRange(saved,4,saved.length),drawing),"save exact row-major palette bytes");
        click(90,34);key(119);check(state(PaintImage.PENDING)==2,"F8 release keeps the dirty New prompt open");key('Y');
        for(byte p:canvas())check((p&255)==127,"confirmed New clears canvas");check(state(PaintImage.DIRTY)==0&&state(PaintImage.PENDING)==0,"New resets dirty and pending state");
        key(117);check(Arrays.equals(drawing,canvas()),"F6 press/release loads saved painting");
        key('I');mouse(8,44,1);frames(20);check(state(PaintImage.DIRTY)==0&&Arrays.equals(drawing,canvas()),"held picker consumes click without drawing");mouse(8,44,0);
        mouse(8,44,4);frames(20);check(state(PaintImage.COLOR)==96&&state(PaintImage.DIRTY)==0,"right mouse picks without painting");mouse(8,44,0);
        key('B');click(70,70);key(117);check(state(PaintImage.PENDING)==1,"open asks before discarding");key('Y');check(Arrays.equals(drawing,canvas()),"confirmed open restores saved painting");
        byte[] invalid=saved.clone();invalid[invalid.length-1]=(byte)128;disk.put(JvFileSystem.ROOT,"PAINT.JVI",invalid,2);key(117);check(state(PaintImage.STATUS)==3&&Arrays.equals(drawing,canvas()),"late invalid pixel leaves entire canvas intact");
        disk.put(JvFileSystem.ROOT,"PAINT.JVI",Arrays.copyOf(saved,saved.length-1),2);key(117);check(state(PaintImage.STATUS)==3&&Arrays.equals(drawing,canvas()),"truncated file refused");
        invalid=saved.clone();invalid[0]='X';disk.put(JvFileSystem.ROOT,"PAINT.JVI",invalid,2);key(117);check(state(PaintImage.STATUS)==3&&Arrays.equals(drawing,canvas()),"invalid header refused");
        disk.put(JvFileSystem.ROOT,"PAINT.JVI",saved,2);key(117);
        key('1');click(80,80);byte[] changed=canvas();var fill=disk.put(JvFileSystem.ROOT,"FILL.BIN",new byte[disk.freeClusters()*1024],0);key(118);
        check(state(PaintImage.STATUS)==3&&state(PaintImage.DIRTY)==1&&Arrays.equals(changed,canvas()),"failed save retains painting and dirty marker");check(Arrays.equals(saved,disk.read(disk.find(JvFileSystem.ROOT,"PAINT.JVI"))),"failed replacement preserves old file");disk.remove(fill);
        key(118);check(state(PaintImage.DIRTY)==0,"save recovers after disk space is freed");
        // Stationary button polling must not repaint the full desktop repeatedly.
        mouse(90,80,1);frames(20);long writes=metric("pixelWrites"),instructions=metric("instructions");frames(20);
        check(metric("pixelWrites")==writes,"stationary brush causes no repaint");System.out.println("PAINT idle held brush: "+(metric("instructions")-instructions)+" instructions, 0 pixel writes / 20 ticks");mouse(90,80,0);
        byte[] before=canvas();invoke("mouse",new Class[]{int.class,int.class,int.class},2,240,1);frames(20);invoke("mouse",new Class[]{int.class,int.class,int.class},2,240,0);frames(8);check(Arrays.equals(before,canvas()),"click outside app cannot paint clamped edges");
        int wx=ram()[480][168]&255,wy=ram()[480][169]&255;
        invoke("mouse",new Class[]{int.class,int.class,int.class},wx+20,wy+5,0);invoke("mouse",new Class[]{int.class,int.class,int.class},wx+20,wy+5,1);invoke("mouse",new Class[]{int.class,int.class,int.class},wx+25,wy+10,1);invoke("mouse",new Class[]{int.class,int.class,int.class},wx+25,wy+10,0);frames(20);check(Arrays.equals(before,canvas()),"window dragging retains painting");visibleCanvas();
        invoke("snapshot",new Class[]{String.class},"jctrl-paint");
        // Continue a row-by-row clear behind another window: the fast path must not draw over it.
        key(119);key('Y');
        // Start another clear directly so it is still running when About is raised.
        press(119);until(()->state(14)>32&&state(14)<128&&state(PaintImage.PHASE)==0,"background clear starts");press(114);
        Field screenField=VM.getDeclaredField("pixels");screenField.setAccessible(true);int[][] screen=(int[][])screenField.get(vm);
        int infoX=ram()[480][176]&255,infoY=ram()[480][177]&255;int[][] info=new int[168][216];
        for(int y=0;y<168;y++)System.arraycopy(screen[infoY+y],infoX,info[y],0,216);
        ready();check((ram()[480][DesktopImage.Z2]&255)==2,"About remains frontmost while Paint updates");
        for(int y=0;y<168;y++)for(int x=0;x<216;x++)check(screen[infoY+y][infoX+x]==info[y][x],"background canvas does not overwrite About");
        invoke("click",new Class[]{int.class,int.class},infoX+210,infoY+5);frames(8);visibleCanvas();
        disk.validate();press(27);frames(20);check((ram()[481][0]&255)==0,"Escape returns to desktop");
        System.out.println("PASS: PAINT launch, palette, connected strokes, sizes, eraser, picker, clipping, dirty guards, JI save/load, malformed files, full disk, idle work and window movement");
    }
}
