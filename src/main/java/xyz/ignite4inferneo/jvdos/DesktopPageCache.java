package xyz.ignite4inferneo.jvdos;

import static xyz.ignite4inferneo.OPCode.*;
import static xyz.ignite4inferneo.jvdos.DesktopImage.*;

/** A reusable JVFS-backed text spool for applications. All work is guest bytecode.
 * The app's virtual page 31 mailbox and filesystem handle 3 are owned until ABORT.
 * Failed/unavailable storage falls back to the caller's RAM cache.
 */
final class DesktopPageCache {
    private DesktopPageCache() { }
    static final int MODE=415;
    private static final int TOTAL=416,BASE=419,POSITION=421,FLUSH=423,TRIES=424,
        ENTRY=425,FIRST_PAGE=428,END_PAGE=429,SIZE=430,SIZE_H=431,TEMP_LO=432,TEMP_HI=433,FS_RETURN=434, STEP=443;
    private static final int MAILBOX=159;
    private static Asm s;
    private static void get(int slot,int reg){s.get(reg,slot);}
    private static void put(int slot,int reg){s.put(reg,slot);}
    private static void set(int slot,int value){s.set(slot,value);}
    private static void copy(int from,int to){get(from,A);put(to,A);}
    private static void inc(int slot){get(slot,A);s.emit(INCREMENT_REGISTER,A);put(slot,A);}
    private static void fget(int slot,int reg){s.emit(LOAD_MEM_ABSOLUTE,1,MAILBOX,slot,reg);}
    private static void fput(int slot,int reg){s.emit(STORE_MEM_ABSOLUTE,reg,1,MAILBOX,slot);}
    private static void fset(int slot,int value){s.store(value,D);fput(slot,D);}
    private static void fs(int operation){fset(FileSystemAbi.OP,operation);s.call("app.cache.fs",FS_RETURN);}
    private static void name(String value){for(int i=0;i<12;i++)fset(FileSystemAbi.NAME+i,i<value.length()?value.charAt(i):0);}
    static void emit(Asm assembler){
        s=assembler;
        s.label("app.cache.fs");fset(FileSystemAbi.HANDLE,3);s.vreadLiteral(10,A);s.store(0,B);s.emit(ADD,A,B,10);s.store(1,A);s.store(MAILBOX,B);s.nativeFs();s.ret(FS_RETURN);
        s.label("app.cache.begin");get(MODE,A);s.equal(A,0,"app.cache.begin.ready");fs(FileSystemAbi.ABORT);
        s.label("app.cache.begin.ready");set(MODE,0);s.vreadLiteral(1,A);s.get(B,APP_LIMIT);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"app.cache.first");s.jump("app.service.bad");
        s.label("app.cache.first");put(FIRST_PAGE,A);s.vreadLiteral(2,B);s.get(C,APP_LIMIT);s.emit(COMPARE_UNSIGNED,B,C,D);s.notEqual(D,2,"app.cache.end.valid");s.jump("app.service.bad");
        s.label("app.cache.end.valid");s.less(A,32,"app.cache.mailbox.check");s.jump("app.cache.span");s.label("app.cache.mailbox.check");s.less(B,32,"app.cache.span");s.jump("app.service.bad");s.label("app.cache.span");s.emit(COMPARE_UNSIGNED,A,B,C);s.notEqual(C,1,"app.service.bad");put(END_PAGE,B);s.emit(SUB,B,A,D);s.less(D,2,"app.service.bad");
        set(STEP,2);s.get(A,DesktopDocument.MODE);s.equal(A,0,"app.cache.step.ready");set(STEP,3);s.label("app.cache.step.ready");get(END_PAGE,D);get(FIRST_PAGE,A);s.emit(SUB,D,A,D);get(STEP,A);s.emit(COMPARE_UNSIGNED,D,A,C);s.equal(C,1,"app.service.bad");
        for(int i=0;i<3;i++)set(TOTAL+i,0);set(BASE,0);set(BASE+1,0);
        s.store(0,C);s.store(0,D);s.store(1,E);s.store(MAILBOX,B);
        s.label("app.cache.clear");s.emit(STORE_MEM_ON_BANK,D,E,B,C);s.emit(INCREMENT_REGISTER,C);s.notEqual(C,64,"app.cache.clear");
        s.get(A,CWD);fput(FileSystemAbi.DIR,A);s.get(A,DesktopFileSystem.CWD_H);fput(FileSystemAbi.DIR+1,A);
        name("WEB.CACHE");fs(FileSystemAbi.LOOKUP);fget(FileSystemAbi.STATUS,A);s.equal(A,FileSystemAbi.NOT_FOUND,"app.cache.mkdir");s.notEqual(A,0,"app.cache.return");s.jump("app.cache.folder");
        s.label("app.cache.mkdir");fs(FileSystemAbi.MKDIR);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"app.cache.return");
        s.label("app.cache.folder");fget(FileSystemAbi.TYPE,A);s.notEqual(A,4,"app.cache.return");for(int i=0;i<2;i++){fget(FileSystemAbi.FIRST+i,A);fput(FileSystemAbi.DIR+i,A);}name("WEB0000.TXT");set(TRIES,0);
        s.label("app.cache.unique");fs(FileSystemAbi.LOOKUP);fget(FileSystemAbi.STATUS,A);s.equal(A,FileSystemAbi.NOT_FOUND,"app.cache.create");s.notEqual(A,0,"app.cache.return");inc(TRIES);get(TRIES,A);s.equal(A,0,"app.cache.return");
        for(int i=6;i>=5;i--){String digit="app.cache.name."+i;s.label(digit);fget(FileSystemAbi.NAME+i,A);s.equal(A,'F',digit+".carry");s.equal(A,'9',digit+".hex");s.emit(INCREMENT_REGISTER,A);fput(FileSystemAbi.NAME+i,A);s.jump("app.cache.unique");s.label(digit+".hex");fset(FileSystemAbi.NAME+i,'A');s.jump("app.cache.unique");s.label(digit+".carry");fset(FileSystemAbi.NAME+i,'0');}s.jump("app.cache.return");
        s.label("app.cache.create");fset(FileSystemAbi.TYPE,1);fs(FileSystemAbi.CREATE);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"app.cache.return");for(int i=0;i<3;i++){fget(FileSystemAbi.ADDRESS+i,A);put(ENTRY+i,A);}set(MODE,1);s.jump("app.cache.return");

        s.label("app.cache.flush");get(MODE,A);s.notEqual(A,1,"app.cache.return");s.vreadLiteral(1,A);put(SIZE_H,A);s.vreadLiteral(2,A);put(SIZE,A);
        get(SIZE_H,A);get(FIRST_PAGE,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"app.service.bad");get(END_PAGE,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,2,"app.service.bad");s.notEqual(C,0,"app.cache.flush.valid");get(SIZE,A);s.notEqual(A,0,"app.service.bad");
        s.label("app.cache.flush.valid");copy(TOTAL+1,BASE);copy(TOTAL+2,BASE+1);copy(FIRST_PAGE,FLUSH);
        s.label("app.cache.flush.block");get(FLUSH,A);get(SIZE_H,B);s.ne(A,B,"app.cache.flush.full");get(SIZE,A);s.equal(A,0,"app.cache.flushed");fput(FileSystemAbi.COUNT,A);s.jump("app.cache.flush.write");
        s.label("app.cache.flush.full");fset(FileSystemAbi.COUNT,0);
        s.label("app.cache.flush.write");fset(FileSystemAbi.BUFFER_BANK,1);get(FLUSH,A);s.appPage(A);fput(FileSystemAbi.BUFFER_PAGE,A);fset(FileSystemAbi.BUFFER_OFFSET,0);fs(FileSystemAbi.WRITE_BLOCK);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"app.cache.failed");inc(FLUSH);get(FLUSH,A);get(SIZE_H,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,2,"app.cache.flushed");s.jump("app.cache.flush.block");
        // SIZE is published on CLOSE; POSITION is the live committed-to-handle cursor.
        s.label("app.cache.flushed");for(int i=0;i<3;i++){fget(FileSystemAbi.POSITION+i,A);put(TOTAL+i,A);}s.jump("app.cache.return");
        s.label("app.cache.finish");get(MODE,A);s.notEqual(A,1,"app.cache.return");fs(FileSystemAbi.CLOSE);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"app.cache.failed");for(int i=0;i<3;i++){get(ENTRY+i,A);fput(FileSystemAbi.ADDRESS+i,A);}fs(FileSystemAbi.OPEN);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"app.cache.failed");set(MODE,2);s.jump("app.cache.return");
        s.label("app.cache.abort");get(MODE,A);s.equal(A,0,"app.cache.return");s.label("app.cache.failed");fs(FileSystemAbi.ABORT);set(MODE,0);s.jump("app.cache.return");

        s.label("app.cache.navigate");get(MODE,A);s.notEqual(A,2,"app.service.bad");s.vreadLiteral(2,A);get(END_PAGE,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.notEqual(C,1,"app.service.bad");get(FIRST_PAGE,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"app.service.bad");s.emit(SUB,A,B,B);get(BASE,A);s.emit(ADD,A,B,C);put(POSITION,C);s.emit(COMPARE_UNSIGNED,C,A,D);get(BASE+1,A);s.notEqual(D,1,"app.cache.position.high");s.emit(INCREMENT_REGISTER,A);s.label("app.cache.position.high");put(POSITION+1,A);
        s.vreadLiteral(1,A);s.equal(A,0,"app.cache.home");s.equal(A,1,"app.cache.previous");s.equal(A,2,"app.cache.next");s.jump("app.service.bad");
        s.label("app.cache.home");set(POSITION,0);set(POSITION+1,0);s.jump("app.cache.load");
        s.label("app.cache.previous");get(POSITION+1,A);s.notEqual(A,0,"app.cache.previous.subtract");get(POSITION,A);get(STEP,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"app.cache.home");
        s.label("app.cache.previous.subtract");get(POSITION,A);get(STEP,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.emit(SUB,A,B,A);put(POSITION,A);s.notEqual(C,1,"app.cache.load");get(POSITION+1,A);s.store(1,B);s.emit(SUB,A,B,A);put(POSITION+1,A);s.jump("app.cache.load");
        s.label("app.cache.next");get(POSITION,A);get(STEP,B);s.emit(ADD,A,B,C);put(TEMP_LO,C);s.emit(COMPARE_UNSIGNED,C,A,D);get(POSITION+1,A);s.notEqual(D,1,"app.cache.next.high");s.emit(INCREMENT_REGISTER,A);s.label("app.cache.next.high");put(TEMP_HI,A);
        get(TOTAL+2,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"app.cache.next.valid");s.equal(C,2,"app.cache.end");get(TEMP_LO,A);get(TOTAL+1,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"app.cache.next.valid");s.equal(C,2,"app.cache.end");get(TOTAL,A);s.equal(A,0,"app.cache.end");
        s.label("app.cache.next.valid");copy(TEMP_LO,POSITION);copy(TEMP_HI,POSITION+1);
        s.label("app.cache.load");fset(FileSystemAbi.POSITION,0);for(int i=0;i<2;i++){get(POSITION+i,A);fput(FileSystemAbi.POSITION+1+i,A);put(BASE+i,A);}fs(FileSystemAbi.SEEK);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"app.cache.failed");set(SIZE,0);copy(FIRST_PAGE,SIZE_H);
        s.label("app.cache.load.block");fset(FileSystemAbi.BUFFER_BANK,1);get(SIZE_H,A);s.appPage(A);fput(FileSystemAbi.BUFFER_PAGE,A);fset(FileSystemAbi.BUFFER_OFFSET,0);fset(FileSystemAbi.COUNT,0);fs(FileSystemAbi.READ_BLOCK);fget(FileSystemAbi.STATUS,A);s.equal(A,FileSystemAbi.EOF,"app.cache.load.partial");s.notEqual(A,0,"app.cache.failed");inc(SIZE_H);get(SIZE_H,A);get(FIRST_PAGE,B);get(STEP,C);s.emit(ADD,B,C,B);s.ne(A,B,"app.cache.load.block");s.jump("app.cache.loaded");
        s.label("app.cache.load.partial");fget(FileSystemAbi.COUNT,A);put(SIZE,A);
        s.label("app.cache.loaded");get(SIZE_H,A);s.vwriteLiteral(1,A);get(SIZE,A);s.vwriteLiteral(2,A);s.jump("app.cache.return");
        s.label("app.cache.end");s.set(DesktopAppServices.STATUS,DesktopServices.END);s.jump("app.cache.return");
        s.label("app.cache.return");get(MODE,A);s.vwriteLiteral(3,A);s.get(A,DRIVE);s.store(0,B);s.emit(ADD,A,B,10);s.jump("app.service.return");
    }
}
