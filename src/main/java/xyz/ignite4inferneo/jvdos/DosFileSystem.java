package xyz.ignite4inferneo.jvdos;

import static xyz.ignite4inferneo.OPCode.*;
import static xyz.ignite4inferneo.jvdos.JvDosImage.*;

/** DOS-facing JVFS client. Buffers are bounded; file operations stream through handles. */
final class DosFileSystem {
    private DosFileSystem() { }
    static final int CWD_H=289,ENTRY_X=290,ENTRY_Y=291,ENTRY_Z=292,DIR_COOKIE=293,DIR_COOKIE_H=294,DIR_ROW=295,
            LENGTH_TOP=296,IO_POS=297,IO_MID=298,IO_TOP=299,IO_RETURN=300,
            SAVED_ENTRY=302,SAVED_ENTRY_Y=303,SAVED_ENTRY_Z=304,SAVED_CWD_H=305,
            PATH_LENGTH=306,IO_BYTE=307,IO_STATUS=308,VIEW_LINES=309,VIEW_LIMIT=310,VIEW_HISTORY=311,
            VIEW_PAGE=312,VIEW_END=313,VIEW_END_H=314,VIEW_END_TOP=315,
            DEC24=316,DEC24_H=317,DEC24_TOP=318,DEC24_DIGIT=319,DEC24_LEADING=320,DEC24_RETURN=321,
            FS_COOKIE_SAVE=323,FS_COOKIE_SAVE_H=324,FS_ROW_SAVE=325,COPY_LEFT=326,COPY_MID=327,COPY_TOP=328,COPY_COUNT=329,DEST_PARSE_FLAG=330;
    private static void equal(Asm s,int r,int v,String target){s.store(v,B);s.eq(r,B,target);}
    private static void check(Asm s){String ok=s.unique("fs.client.ok");s.fget(A,FileSystemAbi.STATUS);s.store(0,B);s.eq(A,B,ok);s.jump("filesystem.error");s.label(ok);}
    private static void context(Asm s){s.get(A,CWD);s.fput(A,FileSystemAbi.DIR);s.get(A,CWD_H);s.fput(A,FileSystemAbi.DIR+1);}
    private static void name(Asm s){for(int i=0;i<12;i++){s.read(NAME,64+i,D);s.fput(D,FileSystemAbi.NAME+i);}}
    private static void address(Asm s){for(int i=0;i<3;i++){s.get(A,ENTRY_X+i);s.fput(A,FileSystemAbi.ADDRESS+i);}}
    private static void retain(Asm s){for(int i=0;i<3;i++){s.fget(A,FileSystemAbi.ADDRESS+i);s.put(A,ENTRY_X+i);}s.fget(A,FileSystemAbi.TYPE);s.put(A,FILE_TYPE);s.fget(A,FileSystemAbi.SIZE);s.put(A,LENGTH);s.fget(A,FileSystemAbi.SIZE+1);s.put(A,LENGTH_H);s.fget(A,FileSystemAbi.SIZE+2);s.put(A,LENGTH_TOP);}
    static void boot(Asm s) {
        s.set(CWD,JvFileSystem.ROOT);s.set(CWD_H,0);s.set(PATH_LENGTH,0);s.fs(FileSystemAbi.RESET);
        String startup="AUTOEXEC.JVS";for(int i=0;i<12;i++){s.store(i<startup.length()?startup.charAt(i):0,D);s.emit(STORE_MEM_ABSOLUTE,D,0,NAME,64+i);}
        s.call("search",SEARCH_RETURN);s.get(A,FOUND);equal(s,A,255,"shell");s.call("file.load",FILE_RETURN);s.jump("script.start");
    }
    static void prompt(Asm s) {
        s.store(0,A);s.emit(ADD,10,A,D);s.put(D,TEMP);s.call("number",NUMBER_RETURN);s.text(":\\");s.set(INDEX,0);
        s.label("prompt.path");s.get(A,INDEX);s.get(B,PATH_LENGTH);s.eq(A,B,"prompt.end");s.store(1,I);s.store(251,J);s.emit(LOAD_MEM_ON_BANK,I,J,A,D);s.put(D,CH);s.call("print",PRINT_RETURN);s.get(A,INDEX);s.emit(INCREMENT_REGISTER,A);s.put(A,INDEX);s.jump("prompt.path");s.label("prompt.end");s.text("> ");
    }
    static void commands(Asm s) {
        s.label("cmd.RENAME");s.jump("cmd.REN");s.label("cmd.DELETE");s.jump("cmd.DEL");
        s.label("cmd.DRIVE");s.get(A,ARG);s.get(B,COUNT);s.eq(A,B,"drive.show");s.call("drive.number",PARSE_RETURN);s.get(A,NUMBER_VALUE);s.store(0,B);s.emit(ADD,10,B,C);s.put(C,WORK);s.emit(ADD,A,B,10);s.emit(READ_INPUT,0x40,D);equal(s,D,0,"drive.absent");s.set(CWD,JvFileSystem.ROOT);s.set(CWD_H,0);s.set(PATH_LENGTH,0);s.jump("drive.show");s.label("drive.absent");s.get(A,WORK);s.store(0,B);s.emit(ADD,A,B,10);s.text("DRIVE NOT READY\n");s.jump("shell");s.label("drive.show");s.text("DRIVE ");s.store(0,A);s.emit(ADD,10,A,D);s.put(D,TEMP);s.call("number",NUMBER_RETURN);s.text("\n");s.jump("shell");
        s.label("drive.number");s.set(NUMBER_VALUE,0);
        s.label("drive.number.loop");s.get(A,ARG);s.get(B,COUNT);s.eq(A,B,"drive.number.done");s.store(64,B);s.emit(ADD,A,B,C);s.readAt(LINE,C,D);
        s.store('0',B);s.emit(COMPARE_UNSIGNED,D,B,C);equal(s,C,1,"usage");s.store('9',B);s.emit(COMPARE_UNSIGNED,D,B,C);equal(s,C,2,"usage");s.store('0',B);s.emit(SUB,D,B,D);s.put(D,NUMBER_DIGIT);
        s.get(A,NUMBER_VALUE);s.store(25,B);s.emit(COMPARE_UNSIGNED,A,B,C);equal(s,C,2,"usage");equal(s,C,1,"drive.number.add");s.get(A,NUMBER_DIGIT);s.store(5,B);s.emit(COMPARE_UNSIGNED,A,B,C);equal(s,C,2,"usage");
        s.label("drive.number.add");s.get(A,NUMBER_VALUE);s.store(10,B);s.emit(MULTIPLY,A,B,A);s.get(B,NUMBER_DIGIT);s.emit(ADD,A,B,A);s.put(A,NUMBER_VALUE);s.get(A,ARG);s.emit(INCREMENT_REGISTER,A);s.put(A,ARG);s.jump("drive.number.loop");s.label("drive.number.done");s.ret(PARSE_RETURN);
        s.label("cmd.DIR");context(s);for(int i=0;i<3;i++)s.fset(FileSystemAbi.COOKIE+i,0);
        s.label("dir.loop");s.fs(FileSystemAbi.NEXT);s.fget(A,FileSystemAbi.STATUS);equal(s,A,FileSystemAbi.EOF,"shell");check(s);for(int i=0;i<3;i++){s.fget(A,FileSystemAbi.COOKIE+i);s.put(A,DIR_COOKIE+i);}s.set(INDEX,0);
        s.label("dir.name");s.get(A,INDEX);s.store(FileSystemAbi.NAME,B);s.emit(ADD,A,B,C);s.store(0,I);s.store(FileSystemAbi.DOS_PAGE,J);s.emit(LOAD_MEM_ON_BANK,I,J,C,D);equal(s,D,0,"dir.size");s.put(D,CH);s.call("print",PRINT_RETURN);s.get(A,INDEX);s.emit(INCREMENT_REGISTER,A);s.put(A,INDEX);equal(s,A,12,"dir.size");s.jump("dir.name");
        s.label("dir.size");s.fget(A,FileSystemAbi.TYPE);equal(s,A,4,"dir.folder");s.text(" ");for(int i=0;i<3;i++){s.fget(A,FileSystemAbi.SIZE+i);s.put(A,DEC24+i);}s.call("number24",DEC24_RETURN);s.text("\n");s.jump("dir.next");s.label("dir.folder");s.text(" <DIR>\n");s.label("dir.next");context(s);for(int i=0;i<3;i++){s.get(A,DIR_COOKIE+i);s.fput(A,FileSystemAbi.COOKIE+i);}s.jump("dir.loop");
        for(String command:new String[]{"TYPE","CAT","VIEW","MORE","IMAGE","SHOW","PLAY","DEL","ERASE","WRITE","EDIT","APPEND","COPY","REN","RUN","SAVE","MKDIR","MD","CD","CHDIR","RMDIR","RD","MOVE"}) {
            s.label("cmd."+command);
            if(command.equals("CD")||command.equals("CHDIR")){s.jump("folder.change");continue;}
            s.call("parse.name",SEARCH_RETURN);
            if(command.equals("WRITE")||command.equals("SAVE")){s.call("file.empty",FILE_RETURN);s.jump("do."+command);continue;}
            if(command.equals("MKDIR")||command.equals("MD")){context(s);name(s);s.fs(FileSystemAbi.MKDIR);check(s);s.text("OK\n");s.jump("shell");continue;}
            s.call("search",SEARCH_RETURN);
            if(command.equals("EDIT")){s.jump("prepare.EDIT");continue;}
            s.get(A,FOUND);equal(s,A,255,"missing");
            if(command.equals("DEL")||command.equals("ERASE")||command.equals("RMDIR")||command.equals("RD")){s.get(A,FILE_TYPE);s.store(4,B);if(command.equals("RD")||command.equals("RMDIR"))s.ne(A,B,"usage");else s.eq(A,B,"directory.required.file");address(s);s.fs(FileSystemAbi.REMOVE);check(s);s.text("DELETED\n");s.jump("shell");continue;}
            if(command.equals("REN")||command.equals("MOVE")||command.equals("COPY")||command.equals("APPEND")){s.jump("filesystem."+command);continue;}
            if(command.equals("TYPE")||command.equals("CAT")){s.jump("filesystem.type");continue;}
            if(command.equals("VIEW")||command.equals("MORE")){s.jump("filesystem.view");continue;}
            if(command.equals("PLAY")){s.jump("filesystem.music");continue;}
            if(command.equals("RUN")){s.get(A,FILE_TYPE);equal(s,A,3,"filesystem.music");}
            s.call("file.load",FILE_RETURN);s.jump("do."+command);
        }
        // EDIT creates a new file without publishing it until save, or loads a bounded existing file.
        s.label("prepare.EDIT");s.get(A,FOUND);equal(s,A,255,"edit.empty");s.call("file.load",FILE_RETURN);s.jump("do.EDIT");s.label("edit.empty");s.call("file.empty",FILE_RETURN);s.jump("do.EDIT");
        s.label("cmd.JCTRL");s.set(SCRIPT_ACTIVE,0);s.get(A,CWD);s.put(A,DESKTOP_CWD);s.get(A,CWD_H);s.put(A,SAVED_CWD_H);s.store(0,A);s.emit(ADD,10,A,D);s.put(D,DESKTOP_DRIVE);s.store(0,10);s.set(CWD,JvFileSystem.ROOT);s.set(CWD_H,0);s.set(DESKTOP_LOADING,1);
        for(int i=0;i<12;i++){s.store(i<9?"JCTRL.COM".charAt(i):0,D);s.emit(STORE_MEM_ABSOLUTE,D,0,NAME,64+i);}s.call("search",SEARCH_RETURN);s.get(A,FOUND);equal(s,A,255,"missing");s.call("file.load",FILE_RETURN);s.get(A,DESKTOP_CWD);s.put(A,CWD);s.get(A,SAVED_CWD_H);s.put(A,CWD_H);s.get(A,DESKTOP_DRIVE);s.store(0,B);s.emit(ADD,A,B,10);s.set(DESKTOP_LOADING,0);s.jump("do.RUN");
        for(String[] failure:new String[][]{{"missing","FILE NOT FOUND\n"},{"full","DISK FULL\n"},{"usage","BAD FILE NAME OR ARGUMENT\n"},{"exists","DESTINATION EXISTS\n"}}){s.label(failure[0]);s.text(failure[1]);s.jump("shell");}
    }
    static void search(Asm s) {
        s.label("search");context(s);name(s);s.fs(FileSystemAbi.LOOKUP);s.fget(A,FileSystemAbi.STATUS);equal(s,A,FileSystemAbi.NOT_FOUND,"search.missing");check(s);retain(s);s.set(FOUND,0);s.ret(SEARCH_RETURN);s.label("search.missing");s.set(FOUND,255);s.ret(SEARCH_RETURN);
        s.label("name.write");s.jump("filesystem.REN");
    }
    static void io(Asm s) {
        s.label("file.empty");s.set16(LENGTH,LENGTH_H,0);s.set(LENGTH_TOP,0);s.set(FILE_TYPE,1);s.ret(FILE_RETURN);
        s.label("file.load");s.get(A,FILE_TYPE);equal(s,A,4,"directory.required.file");s.get(A,LENGTH_TOP);equal(s,A,0,"file.load.size");s.jump("file.too.large");s.label("file.load.size");s.less16Value(LENGTH,LENGTH_H,61201,"file.load.console");s.jump("file.too.large");s.label("file.load.console");s.emit(LOAD_MEM_ABSOLUTE,0,252,24,A);s.store(124,B);s.ne(A,B,"file.load.ready");s.less16Value(LENGTH,LENGTH_H,8193,"file.load.ready");s.jump("file.too.large");
        s.label("file.load.ready");address(s);s.fset(FileSystemAbi.HANDLE,0);s.fs(FileSystemAbi.OPEN);check(s);s.set16(BUF,BUF_H,0);
        s.label("file.load.byte");s.eq16(BUF,BUF_H,LENGTH,LENGTH_H,"file.load.done");s.fs(FileSystemAbi.READ);check(s);s.fget(D,FileSystemAbi.DATA);s.bufferWrite(BUF,BUF_H,D);s.inc16(BUF,BUF_H);s.jump("file.load.byte");s.label("file.load.done");s.fs(FileSystemAbi.CLOSE);check(s);s.ret(FILE_RETURN);
        s.label("file.save");context(s);name(s);s.get(A,FILE_TYPE);s.fput(A,FileSystemAbi.TYPE);s.fset(FileSystemAbi.HANDLE,0);s.fs(FileSystemAbi.CREATE);check(s);s.set16(BUF,BUF_H,0);
        s.label("file.save.byte");s.eq16(BUF,BUF_H,LENGTH,LENGTH_H,"file.save.done");s.bufferRead(BUF,BUF_H,D);s.fput(D,FileSystemAbi.DATA);s.fs(FileSystemAbi.WRITE);s.fget(A,FileSystemAbi.STATUS);equal(s,A,0,"file.save.next");s.fs(FileSystemAbi.ABORT);s.jump("file.full");s.label("file.save.next");s.inc16(BUF,BUF_H);s.jump("file.save.byte");s.label("file.save.done");s.fs(FileSystemAbi.CLOSE);check(s);s.ret(FILE_RETURN);
        s.label("file.delete");address(s);s.fs(FileSystemAbi.REMOVE);check(s);s.ret(FILE_RETURN);
        s.label("file.too.large");s.text("FILE TOO LARGE FOR RAM BUFFER\n");s.jump("shell");s.label("file.corrupt");s.text("INVALID FILESYSTEM CHAIN\n");s.jump("shell");s.label("file.full");s.fset(FileSystemAbi.HANDLE,1);s.fs(FileSystemAbi.ABORT);s.fset(FileSystemAbi.HANDLE,0);s.fs(FileSystemAbi.ABORT);s.text("DISK FULL - FILE UNCHANGED\n");s.get(A,MODE);equal(s,A,1,"poll");s.jump("shell");s.label("directory.required.file");s.text("IS A DIRECTORY\n");s.jump("shell");
        s.label("filesystem.error");s.fget(A,FileSystemAbi.STATUS);equal(s,A,FileSystemAbi.NOT_FOUND,"missing");equal(s,A,FileSystemAbi.EXISTS,"exists");equal(s,A,FileSystemAbi.FULL,"file.full");equal(s,A,FileSystemAbi.IS_DIRECTORY,"directory.required.file");s.text("FILESYSTEM ERROR ");s.fget(D,FileSystemAbi.STATUS);s.put(D,TEMP);s.call("number",NUMBER_RETURN);s.text("\n");s.jump("shell");
        s.label("filesystem.open");address(s);s.fset(FileSystemAbi.HANDLE,0);s.fs(FileSystemAbi.OPEN);check(s);s.ret(IO_RETURN);
        s.label("filesystem.type");s.call("filesystem.open",IO_RETURN);
        s.label("filesystem.type.next");s.fs(FileSystemAbi.READ);s.fget(A,FileSystemAbi.STATUS);equal(s,A,FileSystemAbi.EOF,"filesystem.type.done");check(s);s.fget(D,FileSystemAbi.DATA);s.put(D,CH);s.call("print",PRINT_RETURN);s.jump("filesystem.type.next");s.label("filesystem.type.done");s.fs(FileSystemAbi.CLOSE);s.text("\n");s.jump("shell");
        emitCopy(s);emitFolders(s);emitView(s);emitMusic(s);emitDecimal(s);
    }
    private static void emitCopy(Asm s) {
        for(String op:new String[]{"COPY","APPEND"}) {
            s.label("filesystem."+op);s.call("filesystem.open",IO_RETURN);s.get(A,LENGTH);s.put(A,COPY_LEFT);s.get(A,LENGTH_H);s.put(A,COPY_MID);s.get(A,LENGTH_TOP);s.put(A,COPY_TOP);
            if(op.equals("COPY")){s.call("parse.name",SEARCH_RETURN);s.call("search",SEARCH_RETURN);s.get(A,FOUND);s.store(255,B);s.ne(A,B,"exists");}
            context(s);name(s);s.get(A,FILE_TYPE);s.fput(A,FileSystemAbi.TYPE);s.fset(FileSystemAbi.HANDLE,1);s.fs(FileSystemAbi.CREATE);check(s);
            s.label("filesystem.copy.next."+op);s.get(A,COPY_LEFT);s.get(B,COPY_MID);s.get(C,COPY_TOP);s.store(0,D);s.ne(A,D,"filesystem.copy.chunk."+op);s.ne(B,D,"filesystem.copy.chunk."+op);s.eq(C,D,"filesystem.copy.end."+op);
            s.label("filesystem.copy.chunk."+op);s.set(COPY_COUNT,0);s.get(A,COPY_MID);equal(s,A,0,"filesystem.copy.high."+op);s.jump("filesystem.copy.block."+op);s.label("filesystem.copy.high."+op);s.get(A,COPY_TOP);s.store(0,B);s.ne(A,B,"filesystem.copy.block."+op);s.get(A,COPY_LEFT);s.put(A,COPY_COUNT);
            s.label("filesystem.copy.block."+op);
            for(int h=0;h<2;h++){s.fset(FileSystemAbi.HANDLE,h);s.get(A,COPY_COUNT);s.fput(A,FileSystemAbi.COUNT);s.fset(FileSystemAbi.BUFFER_BANK,1);s.fset(FileSystemAbi.BUFFER_PAGE,252);s.fset(FileSystemAbi.BUFFER_OFFSET,0);s.fs(h==0?FileSystemAbi.READ_BLOCK:FileSystemAbi.WRITE_BLOCK);s.fget(A,FileSystemAbi.STATUS);String ok=s.unique("copy.block.ok");equal(s,A,0,ok);s.fset(FileSystemAbi.HANDLE,1);s.fs(FileSystemAbi.ABORT);s.jump("file.full");s.label(ok);}
            s.get(A,COPY_COUNT);equal(s,A,0,"filesystem.copy.subtract."+op);s.set(COPY_LEFT,0);s.jump("filesystem.copy.next."+op);s.label("filesystem.copy.subtract."+op);s.get(A,COPY_MID);s.store(1,B);s.emit(SUB,A,B,A);s.put(A,COPY_MID);s.store(255,B);s.ne(A,B,"filesystem.copy.next."+op);s.get(A,COPY_TOP);s.store(1,B);s.emit(SUB,A,B,A);s.put(A,COPY_TOP);s.jump("filesystem.copy.next."+op);
            s.label("filesystem.copy.end."+op);s.fset(FileSystemAbi.HANDLE,0);s.fs(FileSystemAbi.CLOSE);s.fset(FileSystemAbi.HANDLE,1);
            if(op.equals("APPEND")){s.label("filesystem.append.byte");s.get(A,ARG);s.get(B,COUNT);s.eq(A,B,"filesystem.copy.commit."+op);s.store(64,B);s.emit(ADD,A,B,C);s.readAt(LINE,C,D);s.fput(D,FileSystemAbi.DATA);s.fs(FileSystemAbi.WRITE);check(s);s.get(A,ARG);s.emit(INCREMENT_REGISTER,A);s.put(A,ARG);s.jump("filesystem.append.byte");}
            s.label("filesystem.copy.commit."+op);s.fs(FileSystemAbi.CLOSE);check(s);s.text("OK\n");s.jump("shell");
        }
        for(String op:new String[]{"REN","MOVE"}) {
            s.label("filesystem."+op);for(int i=0;i<3;i++){s.get(A,ENTRY_X+i);s.put(A,SAVED_ENTRY+i);}s.set(DEST_PARSE_FLAG,op.equals("MOVE")?1:0);s.call("parse.name",SEARCH_RETURN);s.set(DEST_PARSE_FLAG,0);s.get(A,ARG);s.get(B,COUNT);s.ne(A,B,"usage");name(s);for(int i=0;i<12;i++){s.fget(D,FileSystemAbi.NAME+i);s.fput(D,FileSystemAbi.NEW_NAME+i);}for(int i=0;i<3;i++){s.get(A,SAVED_ENTRY+i);s.fput(A,FileSystemAbi.ADDRESS+i);}context(s);s.fs(op.equals("REN")?FileSystemAbi.RENAME:FileSystemAbi.MOVE);check(s);s.text("OK\n");s.jump("shell");
        }
    }
    private static void emitFolders(Asm s) {
        s.label("folder.change");s.get(A,ARG);s.get(B,COUNT);s.eq(A,B,"shell");s.store(64,B);s.emit(ADD,A,B,C);s.readAt(LINE,C,D);equal(s,D,'/',"folder.special.root");equal(s,D,'\\',"folder.special.root");equal(s,D,'.',"folder.special.dot");s.call("parse.name",SEARCH_RETURN);s.call("search",SEARCH_RETURN);s.get(A,FOUND);equal(s,A,255,"missing");s.get(A,FILE_TYPE);s.store(4,B);s.ne(A,B,"usage");s.get(A,PATH_LENGTH);s.store(100,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.store(2,B);s.eq(C,B,"usage");
        s.fget(A,FileSystemAbi.FIRST);s.put(A,CWD);s.fget(A,FileSystemAbi.FIRST+1);s.put(A,CWD_H);s.set(INDEX,0);
        s.label("folder.path.add");s.get(A,INDEX);s.store(64,B);s.emit(ADD,A,B,C);s.readAt(NAME,C,D);equal(s,D,0,"folder.path.slash");s.get(C,PATH_LENGTH);s.store(1,A);s.store(251,B);s.emit(STORE_MEM_ON_BANK,D,A,B,C);s.get(A,PATH_LENGTH);s.emit(INCREMENT_REGISTER,A);s.put(A,PATH_LENGTH);s.get(A,INDEX);s.emit(INCREMENT_REGISTER,A);s.put(A,INDEX);equal(s,A,12,"folder.path.slash");s.jump("folder.path.add");
        s.label("folder.path.slash");s.get(C,PATH_LENGTH);s.store(1,A);s.store(251,B);s.store('\\',D);s.emit(STORE_MEM_ON_BANK,D,A,B,C);s.emit(INCREMENT_REGISTER,C);s.put(C,PATH_LENGTH);s.jump("shell");
        s.label("folder.special.root");s.get(A,ARG);s.emit(INCREMENT_REGISTER,A);s.get(B,COUNT);s.ne(A,B,"usage");s.jump("folder.root");
        s.label("folder.special.dot");s.get(A,ARG);s.emit(INCREMENT_REGISTER,A);s.get(B,COUNT);s.eq(A,B,"shell");s.store(64,B);s.emit(ADD,A,B,C);s.readAt(LINE,C,D);s.store('.',B);s.ne(D,B,"usage");s.emit(INCREMENT_REGISTER,A);s.get(B,COUNT);s.ne(A,B,"usage");s.jump("folder.parent");
        s.label("folder.parent");context(s);s.fs(FileSystemAbi.PARENT);check(s);s.fget(A,FileSystemAbi.FIRST);s.put(A,CWD);s.fget(A,FileSystemAbi.FIRST+1);s.put(A,CWD_H);s.get(A,PATH_LENGTH);equal(s,A,0,"shell");s.store(1,B);s.emit(SUB,A,B,A);s.put(A,PATH_LENGTH);
        s.label("folder.path.trim");s.get(A,PATH_LENGTH);equal(s,A,0,"shell");s.store(1,B);s.emit(SUB,A,B,C);s.store(1,A);s.store(251,B);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);equal(s,D,'\\',"shell");s.put(C,PATH_LENGTH);s.jump("folder.path.trim");
        s.label("folder.root");s.set(CWD,JvFileSystem.ROOT);s.set(CWD_H,0);s.set(PATH_LENGTH,0);s.jump("shell");
    }
    private static void emitView(Asm s) {
        s.label("filesystem.view");s.call("filesystem.open",IO_RETURN);s.set(VIEW_HISTORY,0);s.set(IO_POS,0);s.set(IO_MID,0);s.set(IO_TOP,0);
        s.label("filesystem.view.page");for(int i=0;i<3;i++){s.get(A,IO_POS+i);s.fput(A,FileSystemAbi.POSITION+i);}s.fs(FileSystemAbi.SEEK);check(s);s.emit(CLEAR_GRA);s.set(X,4);s.set(Y,4);s.set(VIEW_LINES,0);s.set(VIEW_LIMIT,26);s.emit(LOAD_MEM_ABSOLUTE,0,252,24,A);s.store(124,B);s.ne(A,B,"filesystem.view.byte");s.set(VIEW_LIMIT,11);
        s.label("filesystem.view.byte");s.fs(FileSystemAbi.READ);s.fget(A,FileSystemAbi.STATUS);equal(s,A,FileSystemAbi.EOF,"filesystem.view.wait");check(s);s.fget(D,FileSystemAbi.DATA);s.put(D,CH);s.call("print",PRINT_RETURN);s.get(A,X);equal(s,A,4,"filesystem.view.line");s.jump("filesystem.view.byte");s.label("filesystem.view.line");s.get(A,VIEW_LINES);s.emit(INCREMENT_REGISTER,A);s.put(A,VIEW_LINES);s.get(B,VIEW_LIMIT);s.ne(A,B,"filesystem.view.byte");
        s.label("filesystem.view.wait");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);equal(s,D,27,"filesystem.view.close");equal(s,D,36,"filesystem.view.home");equal(s,D,33,"filesystem.view.previous");equal(s,D,38,"filesystem.view.previous");for(int key:new int[]{32,10,34,40})equal(s,D,key,"filesystem.view.next");s.jump("filesystem.view.wait");
        s.label("filesystem.view.home");s.set(VIEW_HISTORY,0);s.set(IO_POS,0);s.set(IO_MID,0);s.set(IO_TOP,0);s.jump("filesystem.view.page");s.label("filesystem.view.next");
        for(int i=0;i<3;i++){s.get(A,IO_POS+i);s.fget(B,FileSystemAbi.POSITION+i);s.ne(A,B,"filesystem.view.push");}s.jump("filesystem.view.wait");
        s.label("filesystem.view.push");s.get(A,VIEW_HISTORY);s.store(85,B);s.ne(A,B,"filesystem.view.save");s.store(1,I);s.store(253,J);s.store(3,K);s.store(0,L);
        s.label("filesystem.view.shift");s.emit(LOAD_MEM_ON_BANK,I,J,K,D);s.emit(STORE_MEM_ON_BANK,D,I,J,L);s.emit(INCREMENT_REGISTER,K);s.emit(INCREMENT_REGISTER,L);s.store(255,B);s.ne(K,B,"filesystem.view.shift");s.set(VIEW_HISTORY,84);
        s.label("filesystem.view.save");s.get(A,VIEW_HISTORY);s.store(3,B);s.emit(MULTIPLY,A,B,C);s.store(1,I);s.store(253,J);for(int i=0;i<3;i++){s.get(D,IO_POS+i);s.emit(STORE_MEM_ON_BANK,D,I,J,C);s.emit(INCREMENT_REGISTER,C);s.fget(A,FileSystemAbi.POSITION+i);s.put(A,IO_POS+i);}s.get(A,VIEW_HISTORY);s.emit(INCREMENT_REGISTER,A);s.put(A,VIEW_HISTORY);s.jump("filesystem.view.page");
        s.label("filesystem.view.previous");s.get(A,VIEW_HISTORY);equal(s,A,0,"filesystem.view.wait");s.store(1,B);s.emit(SUB,A,B,A);s.put(A,VIEW_HISTORY);s.store(3,B);s.emit(MULTIPLY,A,B,C);s.store(1,I);s.store(253,J);for(int i=0;i<3;i++){s.emit(LOAD_MEM_ON_BANK,I,J,C,D);s.put(D,IO_POS+i);s.emit(INCREMENT_REGISTER,C);}s.jump("filesystem.view.page");s.label("filesystem.view.close");s.fs(FileSystemAbi.CLOSE);s.emit(CLEAR_GRA);s.set(X,4);s.set(Y,4);s.jump("shell");
    }
    private static void musicRead(Asm s){s.fs(FileSystemAbi.READ);s.fget(A,FileSystemAbi.STATUS);s.store(0,B);s.ne(A,B,"media.error");s.fget(A,FileSystemAbi.DATA);}
    private static void emitMusic(Asm s) {
        final int VERSION=331,EVENTS=332,ZERO=333;
        s.label("filesystem.music");s.call("filesystem.open",IO_RETURN);
        for(int magic:new int[]{'J','M'}){musicRead(s);s.store(magic,B);s.ne(A,B,"media.error");}
        musicRead(s);s.put(A,VERSION);equal(s,A,1,"filesystem.music.version");s.store(2,B);s.ne(A,B,"media.error");s.label("filesystem.music.version");
        musicRead(s);s.put(A,EVENTS);s.get(A,VERSION);equal(s,A,1,"filesystem.music.v1");s.get(A,EVENTS);s.store(0,B);s.ne(A,B,"media.error");s.jump("filesystem.music.start");s.label("filesystem.music.v1");s.get(A,EVENTS);equal(s,A,0,"media.error");
        s.label("filesystem.music.start");s.set(ZERO,0);
        s.label("filesystem.music.event");s.fs(FileSystemAbi.READ);s.fget(A,FileSystemAbi.STATUS);equal(s,A,FileSystemAbi.EOF,"filesystem.music.end");s.store(0,B);s.ne(A,B,"media.error");s.fget(A,FileSystemAbi.DATA);s.put(A,MUSIC_FRAMES);
        s.get(A,VERSION);equal(s,A,2,"filesystem.music.fields");s.get(A,EVENTS);equal(s,A,0,"media.error");s.store(1,B);s.emit(SUB,A,B,A);s.put(A,EVENTS);
        s.label("filesystem.music.fields");for(int slot:new int[]{MUSIC_VOICE,MUSIC_NOTE,MUSIC_CONTROL}){musicRead(s);s.put(A,slot);}
        s.get(A,MUSIC_VOICE);s.store(8,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.store(1,B);s.ne(C,B,"media.error");s.get(A,MUSIC_NOTE);s.store(128,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.store(1,B);s.ne(C,B,"media.error");
        s.get(A,MUSIC_CONTROL);for(int control:new int[]{0,128,129,130,131})equal(s,A,control,"filesystem.music.output");s.jump("media.error");
        s.label("filesystem.music.output");s.get(A,MUSIC_VOICE);s.get(B,MUSIC_NOTE);s.get(C,MUSIC_CONTROL);s.emit(OUTPUT_AUDIO,A,B,C);
        s.get(A,ZERO);s.emit(INCREMENT_REGISTER,A);s.put(A,ZERO);equal(s,A,64,"filesystem.music.yield");
        s.label("filesystem.music.wait");s.get(A,MUSIC_FRAMES);equal(s,A,0,"filesystem.music.event");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);equal(s,D,27,"filesystem.music.done");s.get(A,MUSIC_FRAMES);s.store(1,B);s.emit(SUB,A,B,A);s.put(A,MUSIC_FRAMES);s.set(ZERO,0);s.jump("filesystem.music.wait");
        s.label("filesystem.music.yield");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);equal(s,D,27,"filesystem.music.done");s.set(ZERO,0);s.jump("filesystem.music.wait");
        s.label("filesystem.music.end");s.get(A,VERSION);equal(s,A,2,"filesystem.music.done");s.get(A,EVENTS);s.store(0,B);s.ne(A,B,"media.error");
        s.label("filesystem.music.done");s.silence();s.fs(FileSystemAbi.CLOSE);s.jump("shell");
    }
    private static void emitDecimal(Asm s) {
        s.label("number24");s.set(DEC24_LEADING,0);
        for(int divisor:new int[]{10000000,1000000,100000,10000,1000,100,10,1}) {
            String loop=s.unique("number24.loop"),digit=s.unique("number24.digit"),skip=s.unique("number24.skip");s.set(DEC24_DIGIT,0);s.label(loop);
            for(int i=2;i>=0;i--){s.get(A,DEC24+i);s.store((divisor>>>(i*8))&255,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.store(1,D);s.eq(C,D,digit);s.store(2,D);String higher="number24.subtract."+divisor;s.eq(C,D,higher);}
            s.label("number24.subtract."+divisor);
            for(int i=0;i<3;i++){s.get(A,DEC24+i);s.store((divisor>>>(i*8))&255,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.put(C,CMP_TEMP);s.emit(SUB,A,B,A);s.put(A,DEC24+i);if(i<2){String noBorrow=s.unique("number24.borrow");s.get(A,CMP_TEMP);s.store(1,B);s.ne(A,B,noBorrow);for(int j=i+1;j<3;j++){s.get(A,DEC24+j);s.store(1,B);s.emit(SUB,A,B,A);s.put(A,DEC24+j);s.store(255,B);s.ne(A,B,noBorrow);}s.label(noBorrow);}}
            s.get(A,DEC24_DIGIT);s.emit(INCREMENT_REGISTER,A);s.put(A,DEC24_DIGIT);s.jump(loop);s.label(digit);s.get(A,DEC24_DIGIT);s.store(0,B);String output=s.unique("number24.output");s.ne(A,B,output);s.get(A,DEC24_LEADING);s.ne(A,B,output);if(divisor!=1)s.jump(skip);s.label(output);s.set(DEC24_LEADING,1);s.get(A,DEC24_DIGIT);s.store('0',B);s.emit(ADD,A,B,D);s.put(D,CH);s.call("print",PRINT_RETURN);s.label(skip);
        }
        s.ret(DEC24_RETURN);
    }
}
