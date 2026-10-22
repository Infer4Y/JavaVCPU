package xyz.ignite4inferneo.jvdos;

import java.nio.file.Files;
import java.nio.file.Path;
import static xyz.ignite4inferneo.OPCode.*;
import static xyz.ignite4inferneo.jvdos.DesktopServices.*;

/** PAINT.COM assembler. All interaction, rasterization and file I/O run in the guest. */
public final class PaintImage {
    private static final int A=0,B=1,C=2,D=3,E=4,F=5,I=12,J=13,K=14,L=15,M=16;
    static final int BUFFER=28, STATE=30, MAIL=31, WIDTH=128, HEIGHT=96, TOP=32;
    static final int PHASE=0, DIRTY=1, TOOL=2, COLOR=3, SIZE=4, STATUS=5, PENDING=6,
        DRAWING=7, X=8, Y=9, TX=10, TY=11, BUTTON=12, OLD_BUTTON=13;
    private static final int ROW=14, PASS=15, DIRECTORY=24, KEY=26,
        RETURN=40, FS_RETURN=42;
    static final int[] PALETTE={0,36,64,96,112,124,28,12,3,2,35,67,99,109,73,127};
    private static DesktopImage.Asm s;
    private PaintImage() { }
    private static void get(int slot,int reg){s.emit(LOAD_MEM_ABSOLUTE,1,STATE,slot,reg);}
    private static void put(int slot,int reg){s.emit(STORE_MEM_ABSOLUTE,reg,1,STATE,slot);}
    private static void set(int slot,int value){s.store(value,D);put(slot,D);}
    private static void copy(int from,int to){get(from,A);put(to,A);}
    private static void inc(int slot){get(slot,A);s.emit(INCREMENT_REGISTER,A);put(slot,A);}
    private static void call(String label,int slot){String back=s.unique("paint.return");s.ref(back,true,M);put(slot,M);s.ref(back,false,M);put(slot+1,M);s.jump(label);s.label(back);}
    private static void ret(int slot){get(slot,J);get(slot+1,K);s.emit(CHANGE_PAGE_JUMP,J,K);}
    private static void sys(int op){s.store(op,A);call("system",RETURN);}
    private static void fget(int slot,int reg){s.emit(LOAD_MEM_ABSOLUTE,1,MAIL,slot,reg);}
    private static void fput(int slot,int reg){s.emit(STORE_MEM_ABSOLUTE,reg,1,MAIL,slot);}
    private static void fset(int slot,int value){s.store(value,D);fput(slot,D);}
    private static void fs(int op){fset(FileSystemAbi.OP,op);call("filesystem",FS_RETURN);}
    private static void text(String text){String label=s.strings.computeIfAbsent(text,t->s.unique("paint.text"));s.ref(label,true,B);s.ref(label,false,C);sys(PACKED_TEXT);}
    private static void span(int direction){s.store(BUFFER,B);s.store(0,C);s.store(0,D);get(ROW,E);s.store(128,F);s.store(direction,I);sys(GRAPHICS_SPAN);}
    private static void bufferFill(int color){s.store(1,I);s.store(BUFFER,J);s.store(0,K);s.store(color,D);String loop=s.unique("fill");s.label(loop);s.emit(STORE_MEM_ON_BANK,D,I,J,K);s.emit(INCREMENT_REGISTER,K);s.notEqual(K,0,loop);}
    private static void block(int op,int count){fset(FileSystemAbi.BUFFER_BANK,1);fset(FileSystemAbi.BUFFER_PAGE,BUFFER);fset(FileSystemAbi.BUFFER_OFFSET,0);fset(FileSystemAbi.COUNT,count);fs(op);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"io.abort");fget(FileSystemAbi.COUNT,A);s.notEqual(A,count,"io.abort");}
    private static void filename(){get(DIRECTORY,A);fput(FileSystemAbi.DIR,A);get(DIRECTORY+1,A);fput(FileSystemAbi.DIR+1,A);String name="PAINT.JVI";for(int n=0;n<12;n++)fset(FileSystemAbi.NAME+n,n<name.length()?name.charAt(n):0);}

    public static byte[] program(){byte[] bytes=DesktopImage.assembleOptimized(PaintImage::emit).bytes();if(bytes.length>BUFFER*256)throw new IllegalStateException("Paint code overlaps transfer buffer: "+bytes.length);return bytes;}
    private static void emit(DesktopImage.Asm assembler){
        s=assembler;s.packStrings=true;
        // Fullscreen DOS has no app ABI: probe the existing half-resolution surface first.
        s.store(255,A);s.store(255,B);s.store(0,C);s.emit(PUSH_GRA_MEM,A,B,C);
        s.store(254,A);s.store(254,B);s.store(127,C);s.emit(PUSH_GRA_MEM,A,B,C);
        s.store(255,A);s.store(255,B);s.emit(READ_GRA_MEM,A,B,D);s.notEqual(D,127,"quit");
        sys(QUERY);s.less(D,7,"quit");sys(CONTEXT);put(DIRECTORY,B);put(DIRECTORY+1,C);
        for(int n=0;n<64;n++)fset(n,0);
        // Mouse ports return even scaled coordinates. A RAM lookup halves them
        // in constant time instead of interpreting a repeated-subtraction loop.
        s.store(1,I);s.store(29,J);s.store(0,K);s.store(0,D);s.store(2,E);s.label("mouse.table");s.emit(STORE_MEM_ON_BANK,D,I,J,K);s.emit(ADD,K,E,K);s.emit(INCREMENT_REGISTER,D);s.notEqual(K,0,"mouse.table");
        set(COLOR,0);set(SIZE,1);set(TOOL,0);set(DIRTY,0);set(STATUS,0);set(PENDING,0);set(DRAWING,0);set(OLD_BUTTON,0);
        s.emit(CLEAR_GRA);text("PAINT F6 OPEN F7 SAVE F8 NEW\nB PEN E ERASE I PICK 1/2/3 SIZE");
        // Build the palette once; shared row writes avoid per-pixel interpretation.
        for(int n=0;n<128;n++){s.store(PALETTE[n/8],D);s.emit(STORE_MEM_ABSOLUTE,D,1,BUFFER,n);}
        set(ROW,24);s.label("palette.row");span(1);inc(ROW);s.notEqual(A,32,"palette.row");s.jump("blank");
        emitInput();emitStroke();emitFiles();emitStatus();
        s.label("blank");set(PHASE,0);bufferFill(127);set(ROW,TOP);
        s.label("blank.row");span(1);inc(ROW);s.notEqual(A,128,"blank.row");set(DIRTY,0);set(STATUS,0);set(DRAWING,0);s.jump("status");
        s.label("system");s.store(1,17);s.ref("system.back",true,18);s.ref("system.back",false,19);s.reserve(13);s.store(0,J);s.store(249,K);s.store(ENTRY,L);s.emit(CHANGE_BANK_PAGE_JUMP,J,K,L);s.label("system.back");ret(RETURN);
        s.label("filesystem");fset(FileSystemAbi.HANDLE,3);s.store(1,A);s.store(MAIL,B);s.store(1,17);s.ref("filesystem.back",true,18);s.ref("filesystem.back",false,19);s.reserve(13);s.store(0,J);s.store(249,K);s.store(FileSystemAbi.ENTRY,L);s.emit(CHANGE_BANK_PAGE_JUMP,J,K,L);s.label("filesystem.back");ret(FS_RETURN);
        s.label("quit");s.store(0,A);s.store(249,B);s.store(9,C);s.emit(CHANGE_BANK_PAGE_JUMP,A,B,C);
    }
    private static void emitInput(){
        s.label("wait");set(PHASE,1);s.emit(DISPLAY);set(PHASE,0);s.emit(READ_INPUT,0,D);put(KEY,D);
        get(PENDING,A);s.notEqual(A,0,"confirm");s.equal(D,0,"mouse");
        s.equal(D,117,"open.request");s.equal(D,118,"save");s.equal(D,119,"new.request");
        s.equal(D,'B',"pen");s.equal(D,'E',"eraser");s.equal(D,'I',"picker");
        for(int n=0;n<3;n++)s.equal(D,'1'+n,"size."+n);
        s.label("mouse");s.emit(READ_INPUT,0x32,D);put(BUTTON,D);s.equal(D,0,"released");
        for(int axis=0;axis<2;axis++){
            s.emit(READ_INPUT,axis==0?0x30:0x31,K);s.store(1,I);s.store(29,J);s.emit(LOAD_MEM_ON_BANK,I,J,K,B);put(axis==0?TX:TY,B);
        }
        get(TY,A);s.less(A,24,"released");s.less(A,32,"palette.click");
        get(OLD_BUTTON,A);s.notEqual(A,0,"wait");
        get(BUTTON,A);s.equal(A,4,"pick");s.notEqual(A,1,"released");get(TOOL,A);s.equal(A,2,"pick");s.jump("stroke");
        s.label("released");set(DRAWING,0);set(OLD_BUTTON,0);s.jump("wait");
        s.label("palette.click");get(OLD_BUTTON,A);s.notEqual(A,0,"wait");set(OLD_BUTTON,1);set(DRAWING,0);get(TX,A);s.store(0,B);s.store(8,C);
        s.label("palette.divide");s.less(A,8,"palette.select");s.emit(SUB,A,C,A);s.emit(INCREMENT_REGISTER,B);s.jump("palette.divide");
        s.label("palette.select");for(int n=0;n<16;n++)s.equal(B,n,"palette.color."+n);
        for(int n=0;n<16;n++){s.label("palette.color."+n);set(COLOR,PALETTE[n]);s.jump("pen");}
        s.label("pick");set(OLD_BUTTON,1);get(TX,A);get(TY,B);s.store(2,C);s.emit(MULTIPLY,A,C,A);s.emit(MULTIPLY,B,C,B);s.emit(READ_GRA_MEM,A,B,D);put(COLOR,D);s.jump("pen");
        s.label("pen");set(TOOL,0);set(DRAWING,0);s.jump("status");s.label("eraser");set(TOOL,1);set(DRAWING,0);s.jump("status");s.label("picker");set(TOOL,2);set(DRAWING,0);s.jump("status");
        for(int n=0;n<3;n++){s.label("size."+n);set(SIZE,1<<n);set(DRAWING,0);s.jump("status");}
        s.label("open.request");set(PENDING,1);s.jump("discard.check");s.label("new.request");set(PENDING,2);
        s.label("discard.check");set(DRAWING,0);get(DIRTY,A);s.equal(A,0,"confirmed");s.jump("status");
        // Port 0 includes break events. Releasing F6/F8 must not cancel the
        // confirmation that its make event just opened; modifiers are not answers.
        s.label("confirm");get(KEY,D);s.less(D,128,"confirm.make");s.jump("wait");s.label("confirm.make");
        for(int key:new int[]{0,16,17,18})s.equal(D,key,"wait");s.equal(D,'Y',"confirmed");set(PENDING,0);s.jump("status");
        s.label("confirmed");get(PENDING,A);set(PENDING,0);s.equal(A,1,"open");s.jump("blank");
    }
    private static void emitStroke(){
        s.label("stroke");set(PHASE,0);get(DRAWING,A);s.equal(A,0,"stroke.first");get(X,A);get(TX,B);s.ne(A,B,"stroke.line");get(Y,A);get(TY,B);s.eq(A,B,"wait");
        s.jump("stroke.line");s.label("stroke.first");copy(TX,X);copy(TY,Y);set(DRAWING,1);
        s.label("stroke.line");
        get(X,B);get(Y,C);get(TX,D);get(TY,E);get(COLOR,F);get(TOOL,A);s.notEqual(A,1,"stroke.color");s.store(127,F);s.label("stroke.color");get(SIZE,I);sys(GRAPHICS_LINE);
        copy(TX,X);copy(TY,Y);get(DIRTY,A);s.notEqual(A,0,"wait");set(DIRTY,1);set(STATUS,0);s.jump("status.dirty");
    }
    private static void emitFiles(){
        s.label("save");set(PHASE,0);set(DRAWING,0);filename();fset(FileSystemAbi.TYPE,2);fs(FileSystemAbi.CREATE);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"io.error");
        int[] header={'J','I',WIDTH,HEIGHT};for(int n=0;n<4;n++){s.store(header[n],D);s.emit(STORE_MEM_ABSOLUTE,D,1,BUFFER,n);}block(FileSystemAbi.WRITE_BLOCK,4);set(ROW,TOP);
        s.label("save.row");span(0);block(FileSystemAbi.WRITE_BLOCK,WIDTH);inc(ROW);s.notEqual(A,128,"save.row");fs(FileSystemAbi.CLOSE);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"io.abort");set(DIRTY,0);set(STATUS,1);s.jump("status");
        s.label("open");set(PHASE,0);filename();fs(FileSystemAbi.LOOKUP);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"io.error");
        for(int n=0;n<3;n++){fget(FileSystemAbi.SIZE+n,A);s.notEqual(A,(4+WIDTH*HEIGHT)>>>(n*8)&255,"io.error");}fget(FileSystemAbi.TYPE,A);s.notEqual(A,2,"io.error");
        fs(FileSystemAbi.OPEN);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"io.error");block(FileSystemAbi.READ_BLOCK,4);
        for(int n=0;n<4;n++){s.emit(LOAD_MEM_ABSOLUTE,1,BUFFER,n,A);s.notEqual(A,header[n],"io.abort");}
        set(PASS,0);set(ROW,TOP);
        // Preflight the entire file before replacing any pixel of the current painting.
        s.label("open.row");block(FileSystemAbi.READ_BLOCK,WIDTH);s.store(1,I);s.store(BUFFER,J);s.store(0,K);
        s.label("open.validate");s.emit(LOAD_MEM_ON_BANK,I,J,K,D);s.less(D,128,"open.color");s.jump("io.abort");s.label("open.color");s.emit(INCREMENT_REGISTER,K);s.notEqual(K,128,"open.validate");
        get(PASS,A);s.equal(A,0,"open.next");span(1);s.label("open.next");inc(ROW);s.notEqual(A,128,"open.row");get(PASS,A);s.notEqual(A,0,"open.done");
        fset(FileSystemAbi.POSITION,4);fset(FileSystemAbi.POSITION+1,0);fset(FileSystemAbi.POSITION+2,0);fs(FileSystemAbi.SEEK);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"io.abort");set(PASS,1);set(ROW,TOP);s.jump("open.row");
        s.label("open.done");fs(FileSystemAbi.CLOSE);set(DIRTY,0);set(STATUS,2);s.jump("status");
        s.label("io.abort");fs(FileSystemAbi.ABORT);s.label("io.error");set(STATUS,3);s.jump("status");
    }
    private static void emitStatus(){
        s.label("status");set(PHASE,0);bufferFill(0);set(ROW,12);
        s.label("status.clear");span(1);inc(ROW);s.notEqual(A,18,"status.clear");s.store(0,B);s.store(2,C);sys(GRAPHICS_CURSOR);
        get(PENDING,A);s.notEqual(A,0,"status.confirm");get(STATUS,A);s.equal(A,3,"status.error");get(TOOL,A);s.equal(A,1,"status.eraser");s.equal(A,2,"status.picker");text("PEN ");s.jump("status.size");
        s.label("status.eraser");text("ERASE ");s.jump("status.size");s.label("status.picker");text("PICK ");
        s.label("status.size");get(SIZE,A);s.equal(A,2,"status.size2");s.equal(A,4,"status.size4");text("1 ");s.jump("status.file");s.label("status.size2");text("2 ");s.jump("status.file");s.label("status.size4");text("4 ");
        s.label("status.file");text("PAINT.JVI ");get(DIRTY,A);s.notEqual(A,0,"status.dirty");get(STATUS,A);s.equal(A,1,"status.saved");text("READY");s.jump("status.sample");
        s.label("status.saved");text("SAVED");s.jump("status.sample");s.label("status.dirty");s.store(28,B);s.store(2,C);sys(GRAPHICS_CURSOR);text("*");s.jump("status.sample");
        s.label("status.confirm");text("DISCARD? Y / OTHER KEY CANCELS");s.jump("wait");s.label("status.error");text("FILE ERROR - CANVAS KEPT");s.jump("wait");
        s.label("status.sample");get(COLOR,D);s.store(240,A);s.store(26,B);s.emit(PUSH_GRA_MEM,A,B,D);s.emit(INCREMENT_REGISTER,A);s.emit(INCREMENT_REGISTER,A);s.emit(PUSH_GRA_MEM,A,B,D);s.jump("wait");
    }
    public static void main(String[] args)throws Exception{byte[] bytes=program();Files.write(Path.of(args[0]),bytes);System.out.println("PAINT.COM: "+bytes.length+" bytes");}
}
