package xyz.ignite4inferneo.jvdos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import xyz.ignite4inferneo.OPCode;
import static xyz.ignite4inferneo.OPCode.*;
import static xyz.ignite4inferneo.jvdos.DesktopServices.*;

/** Offline assembler for TEXTEDIT.COM. Editing, lexing and I/O are guest bytecode. */
public final class TextEditImage {
    private static final int A=0,B=1,C=2,D=3,E=4,F=5;
    static final int BUFFER=64, CAPACITY=4096, SCREEN=80, COLORS=85, STATE=90, MAIL=31;
    static final int LENGTH=0, CURSOR=2, TOP=4, MODE=6, DIRTY=7, PHASE=8,
        SHIFT=9, NAME_LEN=10, STATUS=11, NIBBLE=12, PREVIOUS_MODE=13;
    private static final int INDEX=14, END=16, CHAR=18, INK=19, COLUMN=20, ROW=21,
        OUT=22, OUT_H=23, VISIBLE=24, SEEN_CURSOR=25, COMMENT=26, FIRST=27,
        WORD=28, TOKEN_LEN=29, TOKEN_HASH=30, PRINT_LINE=31,
        ROW_START=32, PREV_START=34, TARGET=36, GOAL=38, STEPS=39,
        KEY=40, VALUE=41, TEMP=42, IO_PAGE=43, FILE_TYPE=44,
        DIRECTORY=45, EXISTING=48, CARET_CELL=50, CARET_CHAR=52,
        FAST_VALID=53, FAST_SOURCE=54, FAST_SCREEN=56, FAST_WIDTH=58,
        HEADER_PAGE=84, HEADER_OFFSET=85, HEADER_PART=86, HEADER_OUT=87, HEADER_INK=88, HEADER_OUT_PAGE=89,
        HEADER_RETURN=98, NAV_RETURN=100, SEEK_RETURN=102, LEX_RETURN=104, CELL_RETURN=106,
        HEX_RETURN=108, SYS_RETURN=110, FS_RETURN=112, ROW_RETURN=114, CLEAR_RETURN=116,
        NAME=128;
    private static DesktopImage.Asm s;
    private TextEditImage() { }
    private static void get(int slot,int reg){s.emit(LOAD_MEM_ABSOLUTE,1,STATE,slot,reg);}
    private static void put(int slot,int reg){s.emit(STORE_MEM_ABSOLUTE,reg,1,STATE,slot);}
    private static void set(int slot,int value){s.store(value,D);put(slot,D);}
    private static void copy(int from,int to){get(from,A);put(to,A);}
    private static void pair(int from,int to){copy(from,to);copy(from+1,to+1);}
    private static void inc(int slot){get(slot,A);s.emit(INCREMENT_REGISTER,A);put(slot,A);}
    private static void add(int slot,int n){get(slot,A);s.store(n,B);s.emit(ADD,A,B,A);put(slot,A);}
    private static void inc16(int slot){inc(slot);String done=s.unique("carry");s.notEqual(A,0,done);inc(slot+1);s.label(done);}
    private static void dec16(int slot){get(slot,A);String done=s.unique("borrow");s.notEqual(A,0,done);add(slot+1,255);s.label(done);add(slot,255);}
    private static void equalPair(int left,int right,String target){String done=s.unique("pair");get(left,A);get(right,B);s.ne(A,B,done);get(left+1,A);get(right+1,B);s.eq(A,B,target);s.label(done);}
    private static void zeroPair(int slot,String target){String done=s.unique("zero");get(slot,A);s.notEqual(A,0,done);get(slot+1,A);s.equal(A,0,target);s.label(done);}
    private static void call(String target,int slot){String back=s.unique("return");int temp=slot==SYS_RETURN?F:A;s.ref(back,true,temp);put(slot,temp);s.ref(back,false,temp);put(slot+1,temp);s.jump(target);s.label(back);}
    private static void ret(int slot){int page=slot==SYS_RETURN?12:A,offset=slot==SYS_RETURN?13:B;get(slot,page);get(slot+1,offset);s.emit(CHANGE_PAGE_JUMP,page,offset);}
    private static void syscall(int op){s.store(op,A);call("system",SYS_RETURN);}
    private static void fs(int op){fset(FileSystemAbi.OP,op);call("filesystem",FS_RETURN);}
    private static void fget(int slot,int reg){s.emit(LOAD_MEM_ABSOLUTE,1,MAIL,slot,reg);}
    private static void fput(int slot,int reg){s.emit(STORE_MEM_ABSOLUTE,reg,1,MAIL,slot);}
    private static void fset(int slot,int value){s.store(value,D);fput(slot,D);}
    private static void read(int pointer,int reg){get(pointer,C);get(pointer+1,F);s.store(BUFFER,E);s.emit(ADD,F,E,F);s.store(1,E);s.emit(LOAD_MEM_ON_BANK,E,F,C,reg);}
    private static void write(int pointer,int reg){get(pointer,C);get(pointer+1,F);s.store(BUFFER,E);s.emit(ADD,F,E,F);s.store(1,E);s.emit(STORE_MEM_ON_BANK,reg,E,F,C);}
    private static void writeCaret(){get(CARET_CELL,C);get(CARET_CELL+1,F);s.store(1,E);s.emit(STORE_MEM_ON_BANK,D,E,F,C);}
    private static void header(int row,int col,String text,int color){String label=s.strings.computeIfAbsent(text,t->s.unique("header.literal"));s.ref(label,true,A);put(HEADER_PAGE,A);s.ref(label,false,A);put(HEADER_OFFSET,A);set(HEADER_OUT,(row*48+col)&255);set(HEADER_OUT_PAGE,SCREEN+(row*48+col)/256);set(HEADER_INK,color);call("header",HEADER_RETURN);}
    private static void cell(){call("cell",CELL_RETURN);}
    private static void hex(){call("hex",HEX_RETURN);}
    private static void directory(){for(int i=0;i<2;i++){get(DIRECTORY+i,A);fput(FileSystemAbi.DIR+i,A);}}
    private static void filename(){for(int i=0;i<12;i++){get(NAME+i,A);fput(FileSystemAbi.NAME+i,A);}}

    public static byte[] program(){
        byte[] image=DesktopImage.assembleOptimized(TextEditImage::emit).bytes();
        if(image.length>BUFFER*256)throw new IllegalStateException("Editor code overlaps file buffer: "+image.length);
        // An extended COM receives JCTRL's 91-page document workspace.
        int length=Math.max(33*256,image.length);byte[] result=Arrays.copyOf(image,length);
        Arrays.fill(result,image.length,length,STOP.code());return result;
    }
    private static void emit(DesktopImage.Asm asm){
        s=asm;s.mailboxHole=true;s.packStrings=true;s.mailboxLiterals=true;
        // Native DOS cannot execute this application. Check services before touching RAM.
        // Same scaled-framebuffer probe as PONG; fullscreen DOS has no desktop services.
        s.store(255,A);s.store(255,B);s.store(0,C);s.emit(PUSH_GRA_MEM,A,B,C);
        s.store(254,A);s.store(254,B);s.store(127,C);s.emit(PUSH_GRA_MEM,A,B,C);
        s.store(255,A);s.store(255,B);s.emit(READ_GRA_MEM,A,B,D);s.notEqual(D,127,"quit");
        syscall(QUERY);s.less(D,5,"quit");syscall(DOCUMENT);
        syscall(CONTEXT);put(DIRECTORY,B);put(DIRECTORY+1,C);
        for(int i=0;i<64;i++)fset(i,0);
        for(int i=0;i<50;i++)if(i!=DIRECTORY&&i!=DIRECTORY+1)set(i,0);
        for(int i=0;i<12;i++)set(NAME+i,0);
        s.store(SCREEN,B);syscall(DOCUMENT_FRAME);s.jump("prompt");
        emitInput();emitFiles();emitNavigation();emitDraw();emitRow();emitLex();emitHelpers();
        s.label("quit");s.store(0,A);s.store(249,B);s.store(9,C);s.emit(CHANGE_BANK_PAGE_JUMP,A,B,C);
    }
    private static void emitInput(){
        s.label("prompt");set(PHASE,1);set(NAME_LEN,0);set(STATUS,0);
        for(int i=0;i<12;i++)set(NAME+i,0);
        s.jump("draw");
        s.label("wait");set(PHASE,2);s.label("wait.key");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.equal(D,0,"wait.key");put(KEY,D);
        // F6/F7/F8 avoid JCTRL's global F1..F5 shortcuts.
        s.equal(D,117,"open.request");s.equal(D,118,"save.request");s.equal(D,119,"new.request");
        get(MODE,A);s.equal(A,3,"discard.key");s.equal(A,4,"name.key");
        get(KEY,D);s.equal(D,37,"left");s.equal(D,39,"right");s.equal(D,38,"up");s.equal(D,40,"down");s.equal(D,36,"home");s.equal(D,35,"end");s.equal(D,33,"page.up");s.equal(D,34,"page.down");
        get(MODE,A);s.equal(A,2,"binary.key");get(KEY,D);s.equal(D,8,"backspace");s.equal(D,127,"delete");s.equal(D,10,"newline");s.equal(D,9,"tab");
        call("translate",NAV_RETURN);s.equal(D,0,"wait.key");put(VALUE,D);set(STEPS,1);s.jump("insert.prepare");
        s.label("newline");set(VALUE,10);set(STEPS,1);s.jump("insert.prepare");
        s.label("tab");set(VALUE,32);set(STEPS,4);
        s.label("insert.prepare");call("prepare.row",ROW_RETURN);
        s.label("insert");get(LENGTH+1,A);s.equal(A,CAPACITY/256,"full");
        // Keep overlapping copy pointers in registers instead of reloading state per byte.
        get(LENGTH,15);get(LENGTH+1,14);s.store(BUFFER,A);s.emit(ADD,14,A,14);
        s.store(0,A);s.emit(ADD,14,A,12);s.emit(ADD,15,A,13);
        get(CURSOR,F);get(CURSOR+1,16);s.store(BUFFER,A);s.emit(ADD,16,A,16);s.store(1,E);
        decPointer(12,13,"insert.source");
        s.label("insert.shift");s.ne(15,F,"insert.copy");s.eq(14,16,"insert.byte");
        s.label("insert.copy");s.emit(LOAD_MEM_ON_BANK,E,12,13,D);s.emit(STORE_MEM_ON_BANK,D,E,14,15);
        decPointer(12,13,"insert.source.next");decPointer(14,15,"insert.destination.next");s.jump("insert.shift");
        s.label("insert.byte");get(VALUE,D);write(CURSOR,D);inc16(CURSOR);inc16(LENGTH);set(DIRTY,1);set(STATUS,0);add(STEPS,255);get(STEPS,A);s.notEqual(A,0,"insert");get(FAST_VALID,A);s.equal(A,1,"draw.row");s.jump("draw");
        s.label("backspace");zeroPair(CURSOR,"wait.key");set(VALUE,0);set(STEPS,0);call("prepare.row",ROW_RETURN);dec16(CURSOR);s.jump("delete.prepared");
        s.label("delete");equalPair(CURSOR,LENGTH,"wait.key");set(VALUE,0);set(STEPS,0);call("prepare.row",ROW_RETURN);
        s.label("delete.prepared");read(CURSOR,D);s.less(D,32,"delete.full");s.less(D,127,"delete.start");s.label("delete.full");set(FAST_VALID,0);
        s.label("delete.start");get(CURSOR,15);get(CURSOR+1,14);s.store(BUFFER,A);s.emit(ADD,14,A,14);
        s.store(0,A);s.emit(ADD,14,A,12);s.emit(ADD,15,A,13);incPointer(12,13,"delete.source");
        get(LENGTH,F);get(LENGTH+1,16);s.store(BUFFER,A);s.emit(ADD,16,A,16);s.store(1,E);
        s.label("delete.shift");s.ne(13,F,"delete.copy");s.eq(12,16,"delete.done");s.label("delete.copy");s.emit(LOAD_MEM_ON_BANK,E,12,13,D);s.emit(STORE_MEM_ON_BANK,D,E,14,15);incPointer(12,13,"delete.source.next");incPointer(14,15,"delete.destination.next");s.jump("delete.shift");
        s.label("delete.done");dec16(LENGTH);set(DIRTY,1);set(STATUS,0);get(FAST_VALID,A);s.equal(A,1,"draw.row");s.jump("draw");
        emitHorizontal(false);emitHorizontal(true);
        s.label("open.request");get(DIRTY,A);s.notEqual(A,0,"discard.prompt");s.jump("prompt");
        s.label("new.request");get(DIRTY,A);s.notEqual(A,0,"discard.prompt");s.jump("prompt");
        s.label("discard.prompt");copy(MODE,PREVIOUS_MODE);set(MODE,3);s.jump("draw");
        s.label("discard.key");get(KEY,D);s.equal(D,89,"prompt");copy(PREVIOUS_MODE,MODE);s.jump("draw");
        s.label("name.key");get(KEY,D);s.equal(D,10,"open");s.equal(D,8,"name.erase");call("translate",NAV_RETURN);s.equal(D,0,"wait.key");s.less(D,33,"wait.key");s.less(D,'a',"name.upper");s.less(D,'{',"name.lower");s.jump("name.upper");s.label("name.lower");s.store(32,A);s.emit(SUB,D,A,D);
        s.label("name.upper");for(int ch:new int[]{'/', '\\', ':', '*', '?', '"', '<', '>', '|'})s.equal(D,ch,"wait.key");
        get(NAME_LEN,C);s.equal(C,12,"wait.key");s.store(NAME,F);s.emit(ADD,C,F,C);s.store(STATE,F);s.store(1,E);s.emit(STORE_MEM_ON_BANK,D,E,F,C);inc(NAME_LEN);s.jump("draw");
        s.label("name.erase");get(NAME_LEN,A);s.equal(A,0,"wait.key");add(NAME_LEN,255);get(NAME_LEN,C);s.store(NAME,F);s.emit(ADD,C,F,C);s.store(STATE,F);s.store(1,E);s.store(0,D);s.emit(STORE_MEM_ON_BANK,D,E,F,C);s.jump("draw");
        s.label("full");set(STATUS,3);s.jump("draw");
        s.label("binary.key");call("translate",NAV_RETURN);call("lower",SEEK_RETURN);s.less(D,'0',"wait.key");s.less(D,':',"binary.decimal");s.less(D,'a',"wait.key");s.less(D,'g',"binary.alpha");s.jump("wait.key");
        s.label("binary.alpha");s.store('a'-10,A);s.emit(SUB,D,A,D);s.jump("binary.value");s.label("binary.decimal");s.store('0',A);s.emit(SUB,D,A,D);
        s.label("binary.value");put(VALUE,D);equalPair(CURSOR,LENGTH,"wait.key");read(CURSOR,D);s.store(0,B);
        s.label("binary.divide");s.less(D,16,"binary.parts");s.store(16,A);s.emit(SUB,D,A,D);s.emit(INCREMENT_REGISTER,B);s.jump("binary.divide");
        s.label("binary.parts");get(NIBBLE,A);s.equal(A,1,"binary.low");get(VALUE,B);s.store(16,A);s.emit(MULTIPLY,B,A,B);s.emit(ADD,B,D,D);write(CURSOR,D);set(NIBBLE,1);s.jump("binary.changed");
        s.label("binary.low");s.store(16,A);s.emit(MULTIPLY,B,A,B);get(VALUE,D);s.emit(ADD,B,D,D);write(CURSOR,D);set(NIBBLE,0);inc16(CURSOR);
        s.label("binary.changed");set(DIRTY,1);set(STATUS,0);s.jump("draw");
    }
    private static void decPointer(int page,int offset,String label){
        s.notEqual(offset,0,label);s.store(1,A);s.emit(SUB,page,A,page);s.label(label);s.store(1,A);s.emit(SUB,offset,A,offset);
    }
    private static void incPointer(int page,int offset,String label){s.emit(INCREMENT_REGISTER,offset);s.notEqual(offset,0,label);s.emit(INCREMENT_REGISTER,page);s.label(label);}
    private static void emitHorizontal(boolean right){
        String name=right?"right":"left";s.label(name);set(NIBBLE,0);
        if(right)equalPair(CURSOR,LENGTH,"wait.key");else zeroPair(CURSOR,"wait.key");
        get(MODE,A);s.equal(A,2,name+".slow");equalPair(CURSOR,LENGTH,name+".slow");
        read(CURSOR,D);s.less(D,32,name+".slow");s.less(D,127,name+".text");s.jump(name+".slow");
        s.label(name+".text");put(CARET_CHAR,D);set(PHASE,0);
        if(right)inc16(CURSOR);else dec16(CURSOR);
        equalPair(CURSOR,LENGTH,"draw");read(CURSOR,D);s.less(D,32,"draw");s.less(D,127,name+".visible");s.jump("draw");
        s.label(name+".visible");get(CARET_CELL+1,A);s.notEqual(A,right?SCREEN+4:SCREEN,name+".patch");get(CARET_CELL,A);
        if(right){s.less(A,79,name+".patch");s.jump("draw");}else s.equal(A,144,"draw");
        s.label(name+".patch");get(CARET_CHAR,D);writeCaret();if(right)inc16(CARET_CELL);else dec16(CARET_CELL);s.store('|',D);writeCaret();s.jump("publish");
        s.label(name+".slow");if(right)inc16(CURSOR);else dec16(CURSOR);s.jump("draw");
    }
    private static void emitFiles(){
        s.label("open");get(NAME_LEN,A);s.equal(A,0,"wait.key");directory();filename();fs(FileSystemAbi.LOOKUP);fget(FileSystemAbi.STATUS,A);s.equal(A,FileSystemAbi.NOT_FOUND,"open.new");s.notEqual(A,0,"io.error");
        fget(FileSystemAbi.TYPE,A);s.equal(A,4,"io.error");put(FILE_TYPE,A);fget(FileSystemAbi.SIZE+2,A);s.notEqual(A,0,"too.large");fget(FileSystemAbi.SIZE+1,A);s.less(A,16,"open.size.ok");s.notEqual(A,16,"too.large");fget(FileSystemAbi.SIZE,A);s.notEqual(A,0,"too.large");
        s.label("open.size.ok");for(int i=0;i<2;i++){fget(FileSystemAbi.SIZE+i,A);put(END+i,A);}fs(FileSystemAbi.OPEN);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"io.error");
        set(IO_PAGE,BUFFER);set(LENGTH,0);set(LENGTH+1,0);
        s.label("open.block");equalPair(LENGTH,END,"open.close");get(LENGTH+1,A);get(END+1,B);s.ne(A,B,"open.full");get(END,A);fput(FileSystemAbi.COUNT,A);s.jump("open.read");s.label("open.full");fset(FileSystemAbi.COUNT,0);
        s.label("open.read");fset(FileSystemAbi.BUFFER_BANK,1);get(IO_PAGE,A);fput(FileSystemAbi.BUFFER_PAGE,A);fset(FileSystemAbi.BUFFER_OFFSET,0);fs(FileSystemAbi.READ_BLOCK);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"io.abort");get(LENGTH+1,A);get(END+1,B);s.eq(A,B,"open.partial");inc(LENGTH+1);inc(IO_PAGE);s.jump("open.block");
        s.label("open.partial");copy(END,LENGTH);s.label("open.close");fs(FileSystemAbi.CLOSE);s.jump("open.ready");
        s.label("open.new");set(FILE_TYPE,1);set(LENGTH,0);set(LENGTH+1,0);set(STATUS,2);
        // Extension determines source mode; existing COM/binary entries retain binary type.
        s.label("open.ready");set(MODE,0);get(FILE_TYPE,A);s.notEqual(A,1,"open.binary");
        for(int i=0;i<9;i++){String next="extension."+i;get(NAME_LEN,A);s.notEqual(A,i+4,next);for(int n=0;n<4;n++){s.emit(LOAD_MEM_ABSOLUTE,1,STATE,NAME+i+n,A);s.notEqual(A,".HEX".charAt(n),next);}set(MODE,1);s.label(next);}
        s.jump("open.reset");s.label("open.binary");set(MODE,2);
        s.label("open.reset");set(CURSOR,0);set(CURSOR+1,0);set(TOP,0);set(TOP+1,0);set(DIRTY,0);set(NIBBLE,0);s.jump("draw");
        s.label("too.large");set(STATUS,4);s.jump("draw");
        s.label("io.abort");fs(FileSystemAbi.ABORT);set(LENGTH,0);set(LENGTH+1,0);s.label("io.error");set(STATUS,5);s.jump("draw");
        s.label("save.request");get(MODE,A);s.equal(A,4,"wait.key");s.equal(A,3,"wait.key");get(NAME_LEN,A);s.equal(A,0,"prompt");directory();filename();get(FILE_TYPE,A);fput(FileSystemAbi.TYPE,A);fs(FileSystemAbi.CREATE);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"save.error");set(IO_PAGE,BUFFER);set(INDEX,0);set(INDEX+1,0);
        s.label("save.block");equalPair(INDEX,LENGTH,"save.close");get(INDEX+1,A);get(LENGTH+1,B);s.ne(A,B,"save.full");get(LENGTH,A);fput(FileSystemAbi.COUNT,A);s.jump("save.write");s.label("save.full");fset(FileSystemAbi.COUNT,0);
        s.label("save.write");fset(FileSystemAbi.BUFFER_BANK,1);get(IO_PAGE,A);fput(FileSystemAbi.BUFFER_PAGE,A);fset(FileSystemAbi.BUFFER_OFFSET,0);fs(FileSystemAbi.WRITE_BLOCK);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"save.abort");get(INDEX+1,A);get(LENGTH+1,B);s.eq(A,B,"save.partial");inc(INDEX+1);inc(IO_PAGE);s.jump("save.block");
        s.label("save.partial");copy(LENGTH,INDEX);s.jump("save.block");s.label("save.close");fs(FileSystemAbi.CLOSE);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"save.error");set(DIRTY,0);set(STATUS,1);s.jump("draw");
        s.label("save.abort");fs(FileSystemAbi.ABORT);s.label("save.error");set(STATUS,5);s.jump("draw");
    }
    private static void emitNavigation(){
        s.label("home");get(MODE,A);s.equal(A,2,"home.binary");call("locate",NAV_RETURN);pair(ROW_START,CURSOR);s.jump("draw");s.label("home.binary");set(CURSOR,0);set(CURSOR+1,0);s.jump("draw");
        s.label("end");get(MODE,A);s.equal(A,2,"end.binary");call("locate",NAV_RETURN);pair(ROW_START,INDEX);set(GOAL,48);call("seek.column",SEEK_RETURN);pair(INDEX,CURSOR);s.jump("draw");s.label("end.binary");pair(LENGTH,CURSOR);s.jump("draw");
        s.label("up");set(STEPS,1);s.jump("up.step");s.label("page.up");set(STEPS,20);
        s.label("up.step");get(MODE,A);s.equal(A,2,"binary.up");call("locate",NAV_RETURN);copy(COLUMN,GOAL);pair(PREV_START,INDEX);call("seek.column",SEEK_RETURN);pair(INDEX,CURSOR);s.jump("up.next");
        s.label("binary.up");set(TEMP,12);s.label("binary.up.byte");zeroPair(CURSOR,"up.next");dec16(CURSOR);add(TEMP,255);get(TEMP,A);s.notEqual(A,0,"binary.up.byte");
        s.label("up.next");add(STEPS,255);get(STEPS,A);s.notEqual(A,0,"up.step");set(NIBBLE,0);s.jump("draw");
        s.label("down");set(STEPS,1);s.jump("down.step");s.label("page.down");set(STEPS,20);
        s.label("down.step");get(MODE,A);s.equal(A,2,"binary.down");call("locate",NAV_RETURN);copy(COLUMN,GOAL);pair(ROW_START,INDEX);copy(GOAL,TEMP);set(GOAL,48);call("seek.column",SEEK_RETURN);equalPair(INDEX,LENGTH,"down.next");read(INDEX,D);s.notEqual(D,10,"down.start");inc16(INDEX);s.label("down.start");copy(TEMP,GOAL);call("seek.column",SEEK_RETURN);pair(INDEX,CURSOR);s.jump("down.next");
        s.label("binary.down");set(TEMP,12);s.label("binary.down.byte");equalPair(CURSOR,LENGTH,"down.next");inc16(CURSOR);add(TEMP,255);get(TEMP,A);s.notEqual(A,0,"binary.down.byte");
        s.label("down.next");add(STEPS,255);get(STEPS,A);s.notEqual(A,0,"down.step");set(NIBBLE,0);s.jump("draw");
        s.label("locate");set(INDEX,0);set(INDEX+1,0);set(ROW_START,0);set(ROW_START+1,0);set(PREV_START,0);set(PREV_START+1,0);set(COLUMN,0);
        s.label("locate.loop");equalPair(INDEX,CURSOR,"locate.done");read(INDEX,D);inc16(INDEX);s.equal(D,13,"locate.loop");s.equal(D,10,"locate.row");inc(COLUMN);s.equal(A,48,"locate.row");s.jump("locate.loop");
        s.label("locate.row");pair(ROW_START,PREV_START);pair(INDEX,ROW_START);set(COLUMN,0);s.jump("locate.loop");s.label("locate.done");ret(NAV_RETURN);
        s.label("seek.column");set(WORD,0);s.label("seek.loop");equalPair(INDEX,LENGTH,"seek.done");get(WORD,A);get(GOAL,B);s.eq(A,B,"seek.done");read(INDEX,D);s.equal(D,10,"seek.done");inc16(INDEX);s.equal(D,13,"seek.loop");inc(WORD);s.jump("seek.loop");s.label("seek.done");ret(SEEK_RETURN);
    }
    private static void emitDraw(){
        s.label("draw");set(PHASE,0);
        // The shared guest clear routine handles the complete retained frame.
        s.emit(CLEAR_GRA);
        header(0,0,"TEXTEDIT",9);header(1,0,"F6 Open  F7 Save  Esc Close  PgUp/PgDn",9);
        get(MODE,A);s.equal(A,4,"draw.prompt");get(NAME_LEN,A);s.notEqual(A,0,"draw.header");set(MODE,4);s.jump("draw.prompt");
        s.label("draw.header");for(int i=0;i<12;i++){get(NAME+i,D);s.emit(STORE_MEM_ABSOLUTE,D,1,SCREEN,10+i);}
        get(DIRTY,A);s.equal(A,0,"draw.clean");header(0,23,"*",96);s.label("draw.clean");
        get(MODE,A);s.equal(A,3,"draw.discard");s.equal(A,2,"draw.binary.title");s.equal(A,1,"draw.hex.title");header(0,26,"JVS/TEXT",9);s.jump("draw.status");s.label("draw.hex.title");header(0,26,"HEX",9);s.jump("draw.status");s.label("draw.binary.title");header(0,26,"COM HEX",9);
        s.label("draw.status");get(STATUS,A);for(int n=1;n<=5;n++)s.equal(A,n,"status."+n);s.jump("draw.body");
        String[] messages={"Saved", "New file - F7 saves", "Buffer full (4096 bytes)", "File exceeds 4096 bytes", "Disk error - operation failed"};
        for(int n=1;n<=5;n++){s.label("status."+n);header(2,0,messages[n-1],n==1?12:96);s.jump("draw.body");}
        s.label("draw.prompt");set(MODE,4);header(2,0,"File in current folder (12 chars), Enter opens:",0);for(int i=0;i<12;i++){get(NAME+i,D);s.emit(STORE_MEM_ABSOLUTE,D,1,SCREEN,144+i);}get(STATUS,A);s.equal(A,4,"prompt.large");s.equal(A,5,"prompt.error");s.jump("publish");
        s.label("prompt.large");header(5,0,"File exceeds 4096 bytes",96);s.jump("publish");s.label("prompt.error");header(5,0,"Disk error - operation failed",96);s.jump("publish");
        s.label("draw.discard");header(2,0,"Unsaved changes: Y discards, any other cancels",96);s.jump("publish");
        s.label("draw.body");set(INDEX,0);set(INDEX+1,0);set(OUT,144);set(OUT_H,SCREEN);set(VISIBLE,0);set(SEEN_CURSOR,0);set(COLUMN,0);set(ROW,0);set(COMMENT,0);set(PRINT_LINE,0);set(FIRST,1);set(WORD,0);set(EXISTING,0);
        // If the caret moved above the viewport, bring its row into view.
        get(CURSOR+1,A);get(TOP+1,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"draw.top.reset");s.notEqual(C,0,"draw.scan");get(CURSOR,A);get(TOP,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.notEqual(C,1,"draw.scan");
        s.label("draw.top.reset");set(TOP,0);set(TOP+1,0);
        s.label("draw.scan");equalPair(INDEX,TOP,"draw.visible");s.jump("draw.cursor");s.label("draw.visible");set(VISIBLE,1);set(COLUMN,0);
        s.label("draw.cursor");equalPair(INDEX,CURSOR,"draw.caret");s.jump("draw.byte");s.label("draw.caret");get(VISIBLE,A);s.equal(A,0,"draw.byte");set(SEEN_CURSOR,1);
        s.label("draw.byte");equalPair(INDEX,LENGTH,"draw.eof");read(INDEX,D);put(CHAR,D);get(MODE,A);s.equal(A,2,"draw.binary");call("lex",LEX_RETURN);get(CHAR,D);s.equal(D,13,"draw.advance");s.equal(D,10,"draw.newline");s.equal(D,9,"draw.tab");s.less(D,32,"draw.unknown");s.less(D,127,"draw.text");s.label("draw.unknown");set(CHAR,'?');
        s.label("draw.text");get(CHAR,D);get(VISIBLE,A);s.equal(A,0,"draw.text.next");equalPair(INDEX,CURSOR,"draw.text.caret");s.jump("draw.text.cell");s.label("draw.text.caret");pair(OUT,CARET_CELL);s.store('|',D);s.label("draw.text.cell");cell();
        s.label("draw.text.next");inc(COLUMN);s.equal(A,48,"draw.wrap");s.jump("draw.advance");
        s.label("draw.tab");set(CHAR,'>');s.jump("draw.text");
        s.label("draw.newline");get(VISIBLE,A);s.equal(A,0,"draw.line.end");equalPair(INDEX,CURSOR,"draw.newline.caret");s.jump("draw.pad");s.label("draw.newline.caret");pair(OUT,CARET_CELL);set(INK,9);s.store('|',D);cell();inc(COLUMN);
        // Cleared cells already represent spaces; skip to the next row in one step.
        s.label("draw.pad");get(COLUMN,A);s.store(48,B);s.emit(SUB,B,A,B);get(OUT,A);s.emit(ADD,A,B,C);put(OUT,C);s.emit(COMPARE_UNSIGNED,C,A,B);s.notEqual(B,1,"draw.line.end");inc(OUT_H);s.jump("draw.line.end");
        s.label("draw.line.end");set(COMMENT,0);set(PRINT_LINE,0);set(FIRST,1);set(WORD,0);
        s.label("draw.wrap");set(COLUMN,0);get(VISIBLE,A);s.equal(A,0,"draw.advance");inc(ROW);s.equal(A,20,"draw.screen.end");
        s.label("draw.advance");inc16(INDEX);s.jump("draw.scan");
        s.label("draw.screen.end");get(SEEN_CURSOR,A);s.notEqual(A,0,"publish");get(MODE,A);s.equal(A,2,"draw.binary.scroll");call("locate",NAV_RETURN);pair(ROW_START,TOP);s.jump("draw");s.label("draw.binary.scroll");pair(CURSOR,TOP);s.jump("draw");
        s.label("draw.eof");get(SEEN_CURSOR,A);s.equal(A,0,"draw.screen.end");equalPair(INDEX,CURSOR,"draw.eof.caret");s.jump("publish");s.label("draw.eof.caret");pair(OUT,CARET_CELL);set(INK,9);s.store('|',D);cell();s.jump("publish");
        s.label("draw.binary");get(EXISTING,A);s.notEqual(A,0,"draw.operand");get(CHAR,D);s.less(D,OPCode.values().length,"draw.opcode");set(INK,96);s.jump("draw.binary.color");
        s.label("draw.opcode");s.store(5,A);s.emit(MULTIPLY,D,A,C);s.ref("lengths",false,A);s.emit(ADD,C,A,C);s.ref("lengths",true,F);s.store(1,E);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);put(EXISTING,D);set(INK,9);s.jump("draw.binary.color");
        s.label("draw.operand");add(EXISTING,255);set(INK,65);
        s.label("draw.binary.color");get(VISIBLE,A);s.equal(A,0,"draw.binary.next");get(CHAR,D);hex();s.store(32,D);cell();
        s.label("draw.binary.next");inc(COLUMN);s.equal(A,12,"draw.wrap");s.jump("draw.advance");
        s.label("publish");s.store(SCREEN,B);s.store(COLORS,C);syscall(DOCUMENT_FRAME);s.jump("wait");
    }
    private static void emitRow(){
        // A short physical line occupies one row. Edits to it cannot move later rows.
        s.label("prepare.row");set(FAST_VALID,0);get(MODE,A);s.equal(A,2,"prepare.done");get(VALUE,A);s.equal(A,10,"prepare.done");
        pair(CURSOR,INDEX);set(FAST_WIDTH,0);
        s.label("prepare.back");zeroPair(INDEX,"prepare.start");dec16(INDEX);read(INDEX,D);s.equal(D,10,"prepare.after.newline");s.less(D,32,"prepare.done");s.less(D,127,"prepare.back.char");s.jump("prepare.done");
        s.label("prepare.back.char");inc(FAST_WIDTH);s.equal(A,48,"prepare.done");s.jump("prepare.back");
        s.label("prepare.after.newline");inc16(INDEX);
        s.label("prepare.start");pair(INDEX,FAST_SOURCE);pair(CARET_CELL,FAST_SCREEN);get(FAST_SCREEN,A);get(FAST_WIDTH,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.emit(SUB,A,B,A);put(FAST_SCREEN,A);s.notEqual(C,1,"prepare.forward");add(FAST_SCREEN+1,255);
        s.label("prepare.forward");set(FAST_WIDTH,0);
        s.label("prepare.scan");equalPair(INDEX,LENGTH,"prepare.end");read(INDEX,D);s.equal(D,10,"prepare.end");s.less(D,32,"prepare.done");s.less(D,127,"prepare.char");s.jump("prepare.done");
        s.label("prepare.char");inc(FAST_WIDTH);s.equal(A,48,"prepare.done");inc16(INDEX);s.jump("prepare.scan");
        s.label("prepare.end");get(FAST_WIDTH,A);get(STEPS,B);s.emit(ADD,A,B,A);s.less(A,48,"prepare.valid");s.jump("prepare.done");
        s.label("prepare.valid");set(FAST_VALID,1);s.label("prepare.done");ret(ROW_RETURN);

        s.label("draw.row");set(PHASE,0);
        s.store('*',D);s.emit(STORE_MEM_ABSOLUTE,D,1,SCREEN,23);s.store(96,D);s.emit(STORE_MEM_ABSOLUTE,D,1,COLORS,23);
        set(OUT,96);set(OUT_H,SCREEN);call("clear.row",CLEAR_RETURN);
        pair(FAST_SCREEN,OUT);call("clear.row",CLEAR_RETURN);pair(FAST_SOURCE,INDEX);
        set(COMMENT,0);set(PRINT_LINE,0);set(FIRST,1);set(WORD,0);
        s.label("row.byte");equalPair(INDEX,LENGTH,"row.end");read(INDEX,D);s.equal(D,10,"row.end");put(CHAR,D);call("lex",LEX_RETURN);get(CHAR,D);
        equalPair(INDEX,CURSOR,"row.caret");s.jump("row.cell");s.label("row.caret");pair(OUT,CARET_CELL);s.store('|',D);
        s.label("row.cell");cell();inc16(INDEX);s.jump("row.byte");
        s.label("row.end");equalPair(INDEX,CURSOR,"row.end.caret");s.jump("publish");s.label("row.end.caret");pair(OUT,CARET_CELL);set(INK,9);s.store('|',D);cell();s.jump("publish");

        s.label("clear.row");get(OUT,C);get(OUT_H,F);s.store(1,E);s.store(0,D);s.store(48,16);
        s.label("clear.row.byte");s.emit(STORE_MEM_ON_BANK,D,E,F,C);s.store(5,A);s.emit(ADD,F,A,F);s.emit(STORE_MEM_ON_BANK,D,E,F,C);s.emit(SUB,F,A,F);
        s.emit(INCREMENT_REGISTER,C);s.notEqual(C,0,"clear.row.next");s.emit(INCREMENT_REGISTER,F);s.label("clear.row.next");s.store(1,A);s.emit(SUB,16,A,16);s.notEqual(16,0,"clear.row.byte");ret(CLEAR_RETURN);
    }
    private static void emitLex(){
        s.label("lex");set(INK,0);get(COMMENT,A);s.notEqual(A,0,"lex.comment");get(PRINT_LINE,A);s.equal(A,1,"lex.string");get(CHAR,D);s.equal(D,';',"lex.comment.start");get(MODE,A);s.equal(A,1,"lex.hex");s.less(D,33,"lex.space");get(WORD,A);s.notEqual(A,0,"lex.word");get(FIRST,A);s.equal(A,0,"lex.argument");
        // Classify a complete first token (case-insensitively), never keyword prefixes.
        pair(INDEX,TARGET);set(TOKEN_LEN,0);set(TOKEN_HASH,0);
        s.label("lex.token");equalPair(TARGET,LENGTH,"lex.token.end");read(TARGET,D);s.less(D,33,"lex.token.end");call("lower",SEEK_RETURN);get(TOKEN_HASH,A);s.store(31,B);s.emit(MULTIPLY,A,B,A);s.emit(ADD,A,D,A);put(TOKEN_HASH,A);inc(TOKEN_LEN);inc16(TARGET);s.jump("lex.token");
        s.label("lex.token.end");set(FIRST,0);set(WORD,1);
        String[] keywords={"print","echo","store","add","sub","multiply","inc","output","goto","if","rem","beep","clear","end","stop"};
        for(String keyword:keywords){String next=s.unique("keyword");get(TOKEN_LEN,A);s.notEqual(A,keyword.length(),next);get(TOKEN_HASH,A);s.notEqual(A,hash(keyword),next);set(WORD,2);if(keyword.equals("rem"))s.jump("lex.comment.start");if(keyword.equals("print")||keyword.equals("echo"))set(PRINT_LINE,2);s.jump("lex.word");s.label(next);}
        s.label("lex.word");get(WORD,A);s.equal(A,2,"lex.keyword");ret(LEX_RETURN);
        s.label("lex.keyword");set(INK,9);ret(LEX_RETURN);
        s.label("lex.space");set(WORD,0);get(PRINT_LINE,A);s.notEqual(A,2,"lex.return");set(PRINT_LINE,1);s.jump("lex.return");
        s.label("lex.argument");get(CHAR,D);s.less(D,'0',"lex.register");s.less(D,':',"lex.number");
        s.label("lex.register");s.less(D,'A',"lex.return");s.less(D,'Q',"lex.reg.color");s.less(D,'a',"lex.return");s.less(D,'q',"lex.reg.color");s.jump("lex.return");
        s.label("lex.reg.color");set(INK,64);s.jump("lex.return");s.label("lex.number");set(INK,65);s.jump("lex.return");
        s.label("lex.hex");get(CHAR,D);s.equal(D,'#',"lex.comment.start");s.less(D,'0',"lex.return");s.less(D,':',"lex.number");s.less(D,'A',"lex.return");s.less(D,'G',"lex.number");s.less(D,'a',"lex.return");s.less(D,'g',"lex.number");s.jump("lex.return");
        s.label("lex.comment.start");set(COMMENT,1);s.label("lex.comment");set(INK,12);s.jump("lex.return");s.label("lex.string");s.equal(A,2,"lex.word");set(INK,64);s.label("lex.return");ret(LEX_RETURN);
    }
    private static int hash(String text){int h=0;for(char c:text.toCharArray())h=(h*31+c)&255;return h;}
    private static void emitHelpers(){
        s.label("header");set(HEADER_PART,0);s.label("header.loop");get(HEADER_PAGE,F);get(HEADER_OFFSET,C);s.store(1,E);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);s.equal(D,0,"header.done");get(HEADER_OUT_PAGE,F);get(HEADER_OUT,C);s.emit(STORE_MEM_ON_BANK,D,E,F,C);s.store(COLORS-SCREEN,A);s.emit(ADD,F,A,F);get(HEADER_INK,D);s.emit(STORE_MEM_ON_BANK,D,E,F,C);inc(HEADER_OUT);s.notEqual(A,0,"header.out.next");inc(HEADER_OUT_PAGE);s.label("header.out.next");inc(HEADER_OFFSET);inc(HEADER_PART);s.notEqual(A,3,"header.loop");set(HEADER_PART,0);add(HEADER_OFFSET,2);s.jump("header.loop");s.label("header.done");ret(HEADER_RETURN);
        s.label("lower");s.less(D,'A',"lower.done");s.less(D,'[',"lower.add");s.jump("lower.done");s.label("lower.add");s.store(32,A);s.emit(ADD,D,A,D);s.label("lower.done");ret(SEEK_RETURN);
        s.label("translate");get(KEY,B);get(SHIFT,C);syscall(KEYBOARD);put(SHIFT,C);ret(NAV_RETURN);
        s.label("cell");get(OUT,C);get(OUT_H,F);s.store(1,E);s.emit(STORE_MEM_ON_BANK,D,E,F,C);s.store(COLORS-SCREEN,A);s.emit(ADD,F,A,F);get(INK,D);s.emit(STORE_MEM_ON_BANK,D,E,F,C);inc16(OUT);ret(CELL_RETURN);
        s.label("hex");s.store(0,B);s.label("hex.divide");s.less(D,16,"hex.digits");s.store(16,A);s.emit(SUB,D,A,D);s.emit(INCREMENT_REGISTER,B);s.jump("hex.divide");
        s.label("hex.digits");put(TOKEN_HASH,D);put(TOKEN_LEN,B);
        for(int n=0;n<2;n++){get(n==0?TOKEN_LEN:TOKEN_HASH,D);String decimal=s.unique("hex.decimal"),digit=s.unique("hex.digit");s.less(D,10,decimal);s.store(7,A);s.emit(ADD,D,A,D);s.label(decimal);s.store('0',A);s.emit(ADD,D,A,D);equalPair(INDEX,CURSOR,digit+".caret");s.jump(digit);s.label(digit+".caret");get(NIBBLE,A);s.notEqual(A,n,digit);s.store('|',D);s.label(digit);cell();}
        ret(HEX_RETURN);
        s.label("system");s.store(1,17);s.ref("system.back",true,18);s.ref("system.back",false,19);s.reserve(13);s.store(0,12);s.store(249,13);s.store(ENTRY,14);s.emit(CHANGE_BANK_PAGE_JUMP,12,13,14);s.label("system.back");ret(SYS_RETURN);
        s.label("filesystem");fset(FileSystemAbi.HANDLE,3);s.store(1,A);s.store(MAIL,B);s.store(1,17);s.ref("filesystem.back",true,18);s.ref("filesystem.back",false,19);s.reserve(13);s.store(0,12);s.store(249,13);s.store(FileSystemAbi.ENTRY,14);s.emit(CHANGE_BANK_PAGE_JUMP,12,13,14);s.label("filesystem.back");ret(FS_RETURN);
        s.reserve(160);s.labels.put("lengths",s.page*256+s.pos+2);for(OPCode op:OPCode.values())s.emit(STORE_MEM_ABSOLUTE,D,op.inputLength(),0,0);
    }
    public static void main(String[] args)throws Exception {byte[] bytes=program();Files.write(Path.of(args[0]),bytes);System.out.println("TEXTEDIT.COM: "+bytes.length+" bytes");}
}
