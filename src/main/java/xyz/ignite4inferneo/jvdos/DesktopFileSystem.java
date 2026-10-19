package xyz.ignite4inferneo.jvdos;

import static xyz.ignite4inferneo.OPCode.*;
import static xyz.ignite4inferneo.jvdos.DesktopImage.*;

/** JCTRL is a client of the resident filesystem, with its own mailbox and stream handle. */
final class DesktopFileSystem {
    private DesktopFileSystem() { }
    static final int CWD_H=377,FILE_START_H=378,FILE_START_ROW=379,NEXT_SLOT_H=380,NEXT_SLOT_ROW=381,
            SOURCE_Y=382,SOURCE_Z=383,REMAIN_TOP=384,USED_TOP=385,TARGET_TOP=386,NEXT_TOP=387,
            SKIP_TOP=388,RET_SELECTION=390,APP_MAILBOX=392,APP_BUFFER_BANK=393,APP_BUFFER_PAGE=394,APP_BUFFER_OFFSET=395,
            PATH_CURSOR=396,PATH_CHILD=397,PATH_PARENT=399,PATH_INDEX=401,PATH_START=402,RET_PATH=404;
    static void path(Asm s) {
        s.label("files.path");s.set(PATH_CURSOR,255);s.get(A,CWD);s.put(A,PATH_CHILD);s.get(A,CWD_H);s.put(A,PATH_CHILD+1);
        s.store('\\',D);s.store(1,I);s.store(251,J);s.store(255,K);s.emit(STORE_MEM_ON_BANK,D,I,J,K);s.set(PATH_CURSOR,254);
        s.label("files.path.parent");s.get(A,PATH_CHILD);s.notEqual(A,JvFileSystem.ROOT,"files.path.lookup");s.get(A,PATH_CHILD+1);s.equal(A,0,"files.path.ready");
        s.label("files.path.lookup");s.get(A,PATH_CHILD);s.fput(A,FileSystemAbi.DIR);s.get(A,PATH_CHILD+1);s.fput(A,FileSystemAbi.DIR+1);s.fs(FileSystemAbi.PARENT);s.fget(A,FileSystemAbi.STATUS);s.notEqual(A,0,"files.path.short");
        for(int i=0;i<2;i++){s.fget(A,FileSystemAbi.FIRST+i);s.put(A,PATH_PARENT+i);s.fput(A,FileSystemAbi.DIR+i);}for(int i=0;i<3;i++)s.fset(FileSystemAbi.COOKIE+i,0);
        s.label("files.path.scan");s.fs(FileSystemAbi.NEXT);s.fget(A,FileSystemAbi.STATUS);s.notEqual(A,0,"files.path.short");s.fget(A,FileSystemAbi.TYPE);s.notEqual(A,4,"files.path.scan");for(int i=0;i<2;i++){s.fget(A,FileSystemAbi.FIRST+i);s.get(B,PATH_CHILD+i);s.ne(A,B,"files.path.scan");}
        s.set(PATH_INDEX,0);s.label("files.path.length");s.get(C,PATH_INDEX);s.store(FileSystemAbi.NAME,B);s.emit(ADD,C,B,C);s.store(1,A);s.store(FileSystemAbi.DESKTOP_PAGE,B);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.equal(D,0,"files.path.copy");s.get(A,PATH_INDEX);s.emit(INCREMENT_REGISTER,A);s.put(A,PATH_INDEX);s.notEqual(A,12,"files.path.length");
        s.label("files.path.copy");s.get(A,PATH_CURSOR);s.less(A,142,"files.path.short");s.get(A,PATH_INDEX);s.equal(A,0,"files.path.separator");s.store(1,B);s.emit(SUB,A,B,A);s.put(A,PATH_INDEX);s.store(FileSystemAbi.NAME,B);s.emit(ADD,A,B,C);s.store(1,A);s.store(FileSystemAbi.DESKTOP_PAGE,B);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.store(1,I);s.store(251,J);s.get(K,PATH_CURSOR);s.emit(STORE_MEM_ON_BANK,D,I,J,K);s.store(1,B);s.emit(SUB,K,B,K);s.put(K,PATH_CURSOR);s.jump("files.path.copy");
        s.label("files.path.separator");s.store('\\',D);s.store(1,I);s.store(251,J);s.get(K,PATH_CURSOR);s.emit(STORE_MEM_ON_BANK,D,I,J,K);s.store(1,B);s.emit(SUB,K,B,K);s.put(K,PATH_CURSOR);for(int i=0;i<2;i++){s.get(A,PATH_PARENT+i);s.put(A,PATH_CHILD+i);}s.jump("files.path.parent");
        s.label("files.path.ready");s.get(A,PATH_CURSOR);s.store(2,B);s.emit(ADD,A,B,A);s.put(A,PATH_START);s.store(0,B);s.emit(SUB,B,A,D);s.emit(STORE_MEM_ABSOLUTE,D,0,253,114);s.set(PATH_INDEX,0);
        s.label("files.path.pack");s.get(A,PATH_START);s.equal(A,0,"files.path.done");s.store(1,I);s.store(251,J);s.get(K,PATH_START);s.emit(LOAD_MEM_ON_BANK,I,J,K,D);s.get(K,PATH_INDEX);s.emit(STORE_MEM_ON_BANK,D,I,J,K);s.get(A,PATH_START);s.emit(INCREMENT_REGISTER,A);s.put(A,PATH_START);s.get(A,PATH_INDEX);s.emit(INCREMENT_REGISTER,A);s.put(A,PATH_INDEX);s.jump("files.path.pack");
        s.label("files.path.short");for(int i=0;i<4;i++){s.store("...\\".charAt(i),D);s.emit(STORE_MEM_ABSOLUTE,D,1,251,i);}s.store(4,D);s.emit(STORE_MEM_ABSOLUTE,D,0,253,114);s.label("files.path.done");s.ret(RET_PATH);
    }
    /** Translate application block pointers into its 8 KiB workspace before the native call. */
    static void mapBuffer(Asm s) {
        s.put(B,APP_MAILBOX);s.store(0,C);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.equal(D,FileSystemAbi.RESET,"app.tick.error");
        s.equal(D,FileSystemAbi.READ_BLOCK,"vm.filesystem.buffer");s.notEqual(D,FileSystemAbi.WRITE_BLOCK,"vm.filesystem.call");
        s.label("vm.filesystem.buffer");
        for(int i=0;i<3;i++){s.store(FileSystemAbi.BUFFER_BANK+i,C);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.put(D,APP_BUFFER_BANK+i);}
        s.get(A,APP_BUFFER_BANK);s.equal(A,1,"vm.filesystem.buffer.one");s.notEqual(A,0,"app.tick.error");s.get(A,APP_BUFFER_PAGE);s.notEqual(A,248,"app.tick.error");s.jump("vm.filesystem.buffer.boundary");
        s.label("vm.filesystem.buffer.one");s.get(A,APP_BUFFER_PAGE);s.get(B,APP_LIMIT);s.store(1,C);s.emit(SUB,B,C,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"vm.filesystem.buffer.map");s.notEqual(C,0,"app.tick.error");
        s.label("vm.filesystem.buffer.boundary");s.store(1,A);s.get(B,APP_MAILBOX);s.store(FileSystemAbi.COUNT,C);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.get(A,APP_BUFFER_OFFSET);s.notEqual(D,0,"vm.filesystem.buffer.sum");s.notEqual(A,0,"app.tick.error");s.jump("vm.filesystem.buffer.map");
        s.label("vm.filesystem.buffer.sum");s.emit(ADD,A,D,D);s.equal(D,0,"vm.filesystem.buffer.map");s.emit(COMPARE_UNSIGNED,D,A,C);s.equal(C,1,"app.tick.error");
        s.label("vm.filesystem.buffer.map");s.get(A,APP_BUFFER_BANK);s.equal(A,0,"vm.filesystem.buffer.alias");s.get(D,APP_BUFFER_PAGE);s.store(128,C);s.emit(ADD,D,C,D);s.jump("vm.filesystem.buffer.store");s.label("vm.filesystem.buffer.alias");s.store(128,D);
        s.label("vm.filesystem.buffer.store");s.store(1,A);s.get(B,APP_MAILBOX);s.store(FileSystemAbi.BUFFER_PAGE,C);s.emit(STORE_MEM_ON_BANK,D,A,B,C);s.store(FileSystemAbi.BUFFER_BANK,C);s.store(1,D);s.emit(STORE_MEM_ON_BANK,D,A,B,C);
        s.jump("vm.filesystem.call");
    }
    static void unmapBuffer(Asm s) {
        s.get(A,APP);s.equal(A,5,"vm.filesystem.restored");s.store(1,A);s.get(B,APP_MAILBOX);s.store(FileSystemAbi.OP,C);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.equal(D,FileSystemAbi.READ_BLOCK,"vm.filesystem.buffer.restore");s.notEqual(D,FileSystemAbi.WRITE_BLOCK,"vm.filesystem.restored");
        s.label("vm.filesystem.buffer.restore");s.store(FileSystemAbi.BUFFER_BANK,C);s.get(D,APP_BUFFER_BANK);s.emit(STORE_MEM_ON_BANK,D,A,B,C);s.store(FileSystemAbi.BUFFER_PAGE,C);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.get(A,APP_BUFFER_BANK);s.equal(A,0,"vm.filesystem.buffer.restore.alias");s.store(128,C);s.emit(SUB,D,C,D);s.jump("vm.filesystem.buffer.restore.store");s.label("vm.filesystem.buffer.restore.alias");s.store(120,C);s.emit(ADD,D,C,D);
        s.label("vm.filesystem.buffer.restore.store");s.store(1,A);s.get(B,APP_MAILBOX);s.store(FileSystemAbi.BUFFER_PAGE,C);s.emit(STORE_MEM_ON_BANK,D,A,B,C);s.label("vm.filesystem.restored");
    }
    static void context(Asm s){s.get(A,CWD);s.fput(A,FileSystemAbi.DIR);s.get(A,CWD_H);s.fput(A,FileSystemAbi.DIR+1);}
    private static int source(int n){return n==0?FSOURCE:n==1?SOURCE_Y:SOURCE_Z;}
    private static int start(int n){return n==0?FILE_START:n==1?FILE_START_H:FILE_START_ROW;}
    private static int next(int n){return n==0?NEXT_SLOT:n==1?NEXT_SLOT_H:NEXT_SLOT_ROW;}
    static void sourceAddress(Asm s){for(int i=0;i<3;i++){s.get(A,source(i));s.fput(A,FileSystemAbi.ADDRESS+i);}}
    static void saveSource(Asm s){for(int i=0;i<3;i++){s.fget(A,FileSystemAbi.ADDRESS+i);s.put(A,source(i));}}
    static void navigation(Asm s) {
        s.label("files.home");s.set(HDEPTH,0);s.set(SELECTED,0);for(int i=0;i<3;i++)s.set(start(i),0);s.jump("paint");
        s.label("files.next");s.get(A,NEXT_SLOT_ROW);s.equal(A,255,"mouse");s.get(A,HDEPTH);s.notEqual(A,85,"files.next.save");
        s.store(1,I);s.store(229,J);s.store(3,K);s.store(0,L);s.label("files.history.shift");s.emit(LOAD_MEM_ON_BANK,I,J,K,D);s.emit(STORE_MEM_ON_BANK,D,I,J,L);s.emit(INCREMENT_REGISTER,K);s.emit(INCREMENT_REGISTER,L);s.notEqual(K,255,"files.history.shift");s.set(HDEPTH,84);
        s.label("files.next.save");s.get(C,HDEPTH);s.store(3,B);s.emit(MULTIPLY,C,B,C);s.store(1,I);s.store(229,J);for(int i=0;i<3;i++){s.get(D,start(i));s.emit(STORE_MEM_ON_BANK,D,I,J,C);s.emit(INCREMENT_REGISTER,C);}s.get(A,HDEPTH);s.emit(INCREMENT_REGISTER,A);s.put(A,HDEPTH);for(int i=0;i<3;i++){s.get(A,next(i));s.put(A,start(i));}s.set(SELECTED,0);s.jump("paint");
        s.label("files.previous");s.get(A,HDEPTH);s.equal(A,0,"paint");s.store(1,B);s.emit(SUB,A,B,A);s.put(A,HDEPTH);s.store(3,B);s.emit(MULTIPLY,A,B,C);s.store(1,I);s.store(229,J);for(int i=0;i<3;i++){s.emit(LOAD_MEM_ON_BANK,I,J,C,D);s.put(D,start(i));s.emit(INCREMENT_REGISTER,C);}s.set(SELECTED,0);s.jump("paint");
        s.label("files.select.up");s.get(A,SELECTED);s.equal(A,0,"paint");s.store(1,B);s.emit(SUB,A,B,A);s.put(A,SELECTED);s.jump("paint");s.label("files.select.down");s.get(A,SELECTED);s.emit(INCREMENT_REGISTER,A);s.get(B,FILE_ROW);s.emit(COMPARE_UNSIGNED,A,B,C);s.notEqual(C,1,"paint");s.put(A,SELECTED);s.jump("paint");
        s.label("files.select.open");s.get(C,SELECTED);s.jump("files.map");
        s.label("files.up");context(s);s.fs(FileSystemAbi.PARENT);s.fget(A,FileSystemAbi.STATUS);s.notEqual(A,0,"paint");s.fget(A,FileSystemAbi.FIRST);s.put(A,CWD);s.fget(A,FileSystemAbi.FIRST+1);s.put(A,CWD_H);s.jump("files.home");
        s.label("files.click");s.get(A,HITY);s.less(A,26,"files.buttons");s.less(A,28,"paint");s.store(28,B);s.emit(SUB,A,B,A);s.store(0,C);
        s.label("files.rowhit");s.less(A,6,"files.map");s.store(6,B);s.emit(SUB,A,B,A);s.emit(INCREMENT_REGISTER,C);s.jump("files.rowhit");
        s.label("files.map");s.put(C,SELECTED);s.call("files.selection",RET_SELECTION);saveSource(s);s.get(A,BUTTON);s.equal(A,1,"files.press");s.jump("files.open.selected");
        s.label("files.selection");s.get(A,SELECTED);s.get(B,FILE_ROW);s.emit(COMPARE_UNSIGNED,A,B,C);s.notEqual(C,1,"paint");s.store(3,B);s.emit(MULTIPLY,A,B,C);s.store(1,I);s.store(MAP,J);for(int i=0;i<3;i++){s.emit(LOAD_MEM_ON_BANK,I,J,C,D);s.fput(D,FileSystemAbi.ADDRESS+i);s.emit(INCREMENT_REGISTER,C);}s.fs(FileSystemAbi.STAT);s.fget(A,FileSystemAbi.STATUS);s.notEqual(A,0,"paint");s.fget(A,FileSystemAbi.TYPE);s.put(A,TYPE);s.ret(RET_SELECTION);
        s.label("files.open.selected");sourceAddress(s);s.fs(FileSystemAbi.STAT);s.fget(A,FileSystemAbi.STATUS);s.notEqual(A,0,"paint");s.fget(A,FileSystemAbi.TYPE);s.put(A,TYPE);s.equal(A,4,"files.enter");s.call("app.stop",RET_STEP);s.set(APP,0);s.set(ACTIVE,1);s.store(1,A);s.windowPut(2,A);s.raise();for(int i=0;i<3;i++){s.get(A,source(i));s.put(A,VIEW_HEAD+i);}s.set(TARGET,0);s.set(TARGET_H,0);s.set(TARGET_TOP,0);s.get(A,TYPE);s.notEqual(A,1,"app.open");s.jump("paint");
        s.label("files.enter");s.fget(A,FileSystemAbi.FIRST);s.put(A,CWD);s.fget(A,FileSystemAbi.FIRST+1);s.put(A,CWD_H);s.jump("files.home");
        for(int action=1;action<=4;action++){s.label(new String[]{"files.rename","files.mkdir","files.copy","files.delete"}[action-1]);s.set(DRAFT,action);s.jump("files.operation");}
        s.label("files.operation");s.get(A,APP);s.notEqual(A,5,"files.operation.ready");s.get(A,RUN);s.equal(A,0,"files.operation.ready");s.get(A,SWAIT);s.notEqual(A,1,"files.operation.cancel");s.emit(LOAD_MEM_ABSOLUTE,0,252,139,A);s.notEqual(A,0,"files.operation.cancel");s.emit(LOAD_MEM_ABSOLUTE,0,252,176,A);s.notEqual(A,0,"files.operation.cancel");s.label("files.operation.ready");s.get(A,DRAFT);s.equal(A,2,"shell.open");s.call("files.selection",RET_SELECTION);saveSource(s);s.jump("shell.open");s.label("files.operation.cancel");s.set(DRAFT,0);s.jump("paint");
        s.label("files.press");s.get(A,MX);s.put(A,FSX);s.get(A,MY);s.put(A,FSY);s.set(FDOWN,1);s.set(FDRAG,0);s.jump("paint");
        s.label("files.drag.poll");s.get(A,BUTTON);s.notEqual(A,1,"files.drop");s.get(A,MX);s.get(B,FSX);s.ne(A,B,"files.drag.moved");s.get(A,MY);s.get(B,FSY);s.eq(A,B,"mouse.cursor");s.label("files.drag.moved");s.set(FDRAG,1);s.jump("mouse.cursor");
        s.label("files.drop");s.set(FDOWN,0);s.set(OLD_BUTTON,0);s.get(A,FDRAG);s.equal(A,0,"files.open.selected");s.get(A,MX);s.get(B,160);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"paint");s.emit(SUB,A,B,A);s.less(A,WIDTH,"files.drop.x");s.jump("paint");s.label("files.drop.x");s.get(A,MY);s.get(B,161);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"paint");s.emit(SUB,A,B,A);s.less(A,28,"paint");s.less(A,160,"files.drop.y");s.jump("paint");s.label("files.drop.y");s.store(28,B);s.emit(SUB,A,B,A);s.store(0,C);s.label("files.drop.row");s.less(A,6,"files.drop.map");s.store(6,B);s.emit(SUB,A,B,A);s.emit(INCREMENT_REGISTER,C);s.jump("files.drop.row");s.label("files.drop.map");s.put(C,SELECTED);s.call("files.selection",RET_SELECTION);s.get(A,TYPE);s.notEqual(A,4,"paint");for(int i=0;i<12;i++){s.fget(A,FileSystemAbi.NAME+i);s.fput(A,FileSystemAbi.NEW_NAME+i);}sourceAddress(s);context(s);s.fs(FileSystemAbi.MOVE);s.jump("files.home");
        s.label("files.buttons");s.get(A,HITX);s.less(A,32,"files.up");s.less(A,72,"files.next");s.less(A,112,"files.home");s.less(A,154,"files.bank.minus");s.jump("files.bank.plus");
        for(boolean up:new boolean[]{false,true}) {
            s.label(up?"files.bank.plus":"files.bank.minus");s.call("app.stop",RET_STEP);s.get(A,DRIVE);s.put(A,TEMP);
            s.label("files.drive.scan."+up);s.get(A,TEMP);s.equal(A,up?255:0,"files.drive.restore");if(up)s.emit(INCREMENT_REGISTER,A);else{s.store(1,B);s.emit(SUB,A,B,A);}s.put(A,TEMP);s.store(0,B);s.emit(ADD,A,B,10);s.emit(READ_INPUT,0x40,D);s.notEqual(D,1,"files.drive.scan."+up);s.get(A,TEMP);s.put(A,DRIVE);s.set(CWD,JvFileSystem.ROOT);s.set(CWD_H,0);s.set(APP,0);s.jump("files.home");
        }
        s.label("files.drive.restore");s.get(A,DRIVE);s.store(0,B);s.emit(ADD,A,B,10);s.jump("paint");
    }
    static void files(Asm s) {
        s.label("files.hex");s.get(A,CHAR);String digit=s.unique("hex.digit"),print=s.unique("hex.print");s.less(A,10,digit);s.store(55,B);s.jump(print);s.label(digit);s.store('0',B);s.label(print);s.emit(ADD,A,B,A);s.put(A,CHAR);s.call("glyph",RETURN_GLYPH);s.ret(RET_HEX);
        s.label("files.draw");s.relative(12,16);s.text("UP     NEXT      HOME      DRIVE-      DRIVE+");context(s);for(int i=0;i<3;i++){s.get(A,start(i));s.fput(A,FileSystemAbi.COOKIE+i);}s.set(FILE_ROW,0);s.set(NEXT_SLOT_ROW,255);
        s.label("files.scan");s.fs(FileSystemAbi.NEXT);s.fget(A,FileSystemAbi.STATUS);s.equal(A,FileSystemAbi.EOF,"files.done");s.notEqual(A,0,"files.done");for(int i=0;i<3;i++){s.fget(A,FileSystemAbi.COOKIE+i);s.put(A,next(i));}
        s.get(C,FILE_ROW);s.store(3,B);s.emit(MULTIPLY,C,B,C);s.store(1,I);s.store(MAP,J);for(int i=0;i<3;i++){s.fget(D,FileSystemAbi.ADDRESS+i);s.emit(STORE_MEM_ON_BANK,D,I,J,C);s.emit(INCREMENT_REGISTER,C);}
        s.windowGet(0,A);s.store(6,B);s.emit(ADD,A,B,A);s.put(A,TX);s.get(A,FILE_ROW);s.store(6,B);s.emit(MULTIPLY,A,B,A);s.windowGet(1,B);s.emit(ADD,A,B,A);s.store(28,B);s.emit(ADD,A,B,A);s.put(A,TY);s.set(INK,127);s.get(A,FILE_ROW);s.get(B,SELECTED);String unselected=s.unique("files.unselected");s.ne(A,B,unselected);s.set(INK,96);s.label(unselected);s.set(OFFSET,0);
        s.label("files.name");s.get(C,OFFSET);s.store(FileSystemAbi.NAME,B);s.emit(ADD,C,B,C);s.store(1,A);s.store(FileSystemAbi.DESKTOP_PAGE,B);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.equal(D,0,"files.type");s.put(D,CHAR);s.call("glyph",RETURN_GLYPH);s.get(A,OFFSET);s.emit(INCREMENT_REGISTER,A);s.put(A,OFFSET);s.notEqual(A,12,"files.name");
        s.label("files.type");s.windowGet(0,A);s.store(68,B);s.emit(ADD,A,B,A);s.put(A,TX);s.fget(D,FileSystemAbi.TYPE);s.equal(D,4,"files.dir");s.equal(D,1,"files.txt");s.equal(D,2,"files.image");s.equal(D,3,"files.music");s.text("COM PROGRAM");s.jump("files.rowdone");s.label("files.image");s.text("PALETTE IMAGE");s.jump("files.rowdone");s.label("files.music");s.text("MUSIC");s.jump("files.rowdone");s.label("files.dir");s.text("<DIR>");s.jump("files.rowdone");s.label("files.txt");s.text("TEXT");
        s.label("files.rowdone");s.get(A,FILE_ROW);s.emit(INCREMENT_REGISTER,A);s.put(A,FILE_ROW);s.equal(A,22,"files.done");context(s);for(int i=0;i<3;i++){s.get(A,next(i));s.fput(A,FileSystemAbi.COOKIE+i);}s.jump("files.scan");s.label("files.done");s.ret(RETURN_FILES);
    }
    static void streams(Asm s) {
        s.label("stream.init");s.fset(FileSystemAbi.HANDLE,2);s.fs(FileSystemAbi.CLOSE);for(int i=0;i<3;i++){s.get(A,VIEW_HEAD+i);s.fput(A,FileSystemAbi.ADDRESS+i);}s.fs(FileSystemAbi.OPEN);s.fget(A,FileSystemAbi.STATUS);s.notEqual(A,0,"stream.invalid");s.fget(A,FileSystemAbi.SIZE);s.put(A,REMAIN);s.fget(A,FileSystemAbi.SIZE+1);s.put(A,REMAIN_H);s.fget(A,FileSystemAbi.SIZE+2);s.put(A,REMAIN_TOP);s.set(ERROR,0);s.set(USED,0);s.set(USED_H,0);s.set(USED_TOP,0);s.ret(RET_INIT);s.label("stream.invalid");s.set(ERROR,2);s.ret(RET_INIT);
        s.label("view.read");s.fset(FileSystemAbi.HANDLE,2);s.fs(FileSystemAbi.READ);s.fget(A,FileSystemAbi.STATUS);s.equal(A,FileSystemAbi.EOF,"view.read.end");s.notEqual(A,0,"view.read.bad");s.fget(A,FileSystemAbi.DATA);s.put(A,CHAR);s.dec24(REMAIN,REMAIN_H,REMAIN_TOP);s.inc24(USED,USED_H,USED_TOP);s.ret(RETURN_READ);s.label("view.read.end");s.set(ERROR,1);s.ret(RETURN_READ);s.label("view.read.bad");s.set(ERROR,2);s.ret(RETURN_READ);
    }
}
