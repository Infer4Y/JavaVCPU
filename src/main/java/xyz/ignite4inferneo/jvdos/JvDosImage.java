package xyz.ignite4inferneo.jvdos;

import xyz.ignite4inferneo.Programs;
import xyz.ignite4inferneo.OPCode;
import xyz.ignite4inferneo.Registers;

import static xyz.ignite4inferneo.OPCode.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Assembly source for the resident DOS image. Java only emits bytes and resolves addresses.
 * Every keyboard, filesystem, display and executable-loader operation runs on the guest CPU.
 * Disk banks 2..255 each contain 255 linked 256-byte blocks. Head length uses bytes 0/14; byte 15 links continuation blocks.
 * RAM 246 holds font pointers and 247 instruction descriptors. RAM 248 is the transient
 * program area; 249 is the COM service page; 250/251/252 hold
 * names, input and kernel variables. COM services: 249:0 prints D and returns to A:B;
 * 249:9 returns to the command prompt. These entry addresses survive kernel reassembly.
 * RAM bank one buffers file contents; pages 240..250 reserve shell history and completion scratch.
 * Bank-zero pages 253/254 hold extended state and allocation/validation scratch.
 */
public final class JvDosImage {
    // A-D: working values; E/F: legacy addressing; G/H: far branches (COM ABI).
    // I/J: indirect reads; K/L: font coordinates; M/N/O/P remain available to routines.
    private static final byte A=Registers.A.address(), B=Registers.B.address(),
            C=Registers.C.address(), D=Registers.D.address(), E=Registers.E.address(),
            F=Registers.F.address(), G=Registers.G.address(), H=Registers.H.address(),
            I=Registers.I.address(), J=Registers.J.address(), K=Registers.K.address(),
            L=Registers.L.address();
    private static final int SOURCE_REGISTER_COUNT = 16;
    private static final int SOURCE_REGISTER_BASE = 224;
    private static final String[] COMMANDS={"BROWSE","MKDIR","MD","CD","CHDIR","RMDIR","RD","HELP","CODEHELP","DRIVE","BANK","DIR","TYPE","CAT","VIEW","MORE","IMAGE","SHOW","PLAY","DEL","ERASE","WRITE","EDIT","APPEND","COPY","REN","RENAME","MOVE","DELETE","ECHO","PRINT","CLS","CLEAR","VER","MEM","RUN","SAVE","STORE","ADD","SUB","MULTIPLY","INC","OUTPUT","GOTO","IF","BEEP","SOUND","JCTRL","REM","END","STOP","REBOOT","EXIT"};
    private static final int NAME=250, LINE=251, STATE=252, TPA=248;
    private static final int CH=64,X=65,Y=66,COUNT=67,ARG=68,SLOT=69,INDEX=70,
            LENGTH=71,FOUND=72,FREE=73,TEMP=74,MODE=75,SECOND=76,SOURCE=77,
            PRINT_RETURN=80,GLYPH_RETURN=82,SEARCH_RETURN=84,NUMBER_RETURN=86,
            MESSAGE_PAGE=88,MESSAGE_ADDRESS=89,MESSAGE_RETURN=90,
            REMAINING=94,DESCRIPTOR=95,CURSOR=96,CURSOR_X=97,CURSOR_Y=98,
            EDIT_INDEX=99,COLUMN=100,LINE_START=101,TARGET=102,
            BASE_X=103,BASE_Y=104,READ_RETURN=106,RENDERING=108,ENTER_PENDING=109,
            SCRIPT_SLOT=110,SCRIPT_INDEX=111,SCRIPT_ACTIVE=112,SCRIPT_SIZE=113,
            PARSE_RETURN=114,USER_REG=116,USER_LEFT=117,USER_RIGHT=118,
            KEY_SOUND=119,INK=120,HILITE=121,FONT_PAGE=122,FONT_OFFSET=123,
            FONT_ROW=124,FONT_COL=125,FONT_PIXEL=126,PIXEL_X=127,PIXEL_Y=128,
            NUMBER_VALUE=129,NUMBER_DIGIT=130,RUN_LINE=131,HILITE_RETURN=132,TAB_PENDING=134,KEY_SYNTH=135,
            MEDIA_POS=136,MEDIA_LINES=137,MEDIA_NEXT=138,MEDIA_PAGE=139,
            IMAGE_WIDTH=140,IMAGE_HEIGHT=141,IMAGE_ROW=142,IMAGE_COL=143,
            TILE_X=144,TILE_Y=145,IMAGE_COLOR=146,IMAGE_X=147,IMAGE_Y=148,
            KEY_RAW=150,SHIFT=151,MUSIC_FRAMES=152,MUSIC_VOICE=153,MUSIC_NOTE=154,MUSIC_CONTROL=155,DISK_BANK=156;
    // Extended state occupies bank-zero page 253; bank one holds the file buffer.
    private static final int LENGTH_H=192,INDEX_H=193,CURSOR_H=194,SCRIPT_INDEX_H=195,SCRIPT_SIZE_H=196,
            MEDIA_POS_H=197,MEDIA_NEXT_H=198,LINE_START_H=199,TARGET_H=200,EDIT_SIZE=201,EDIT_SIZE_H=202,
            FILE_TYPE=203,ROOT_SLOT=204,BLOCK=205,BLOCK_COUNT=206,SEGMENT=207,
            FILE_RETURN=208,BUF=210,BUF_H=211,NEEDED=212,PLAN_COUNT=213,PLAN_INDEX=214,
            OLD_TAIL=215,SCAN=216,WORK=217,WORK_H=218,NUM_H=219,NUMBER16_RETURN=220,
            DECIMAL=222,DECIMAL_H=223,DIGIT=224,LEADING=225,RUN_LINE_H=226,CMP_TEMP=227;
    // Shell history occupies bank-one pages 240..247; draft/completion scratch uses 248..250.
    private static final int H_COUNT=228,H_NEXT=229,H_VIEW=230,H_SLOT=231,H_PAGE=232,H_OFFSET=233,
            H_INDEX=234,H_RETURN=235,H_OLD_COUNT=237,
            C_ACTIVE=238,C_NEXT=239,C_START=240,C_END=241,C_COUNT=242,C_CURSOR=243,C_PREFIX=244,
            C_TYPE=245,C_WRAP=246,C_INDEX=247,C_MATCH=248,C_BANK=249,C_PAGE=250,C_OFFSET=251,
            C_CHAR=252,C_OUT=253,C_TOTAL=254,C_NEW_CURSOR=255,C_RETURN=256,C_SPACE=258;
    private static final int IMAGE_SCALE=259,IMAGE_LEFT=260,IMAGE_TOP=261, CWD=262, DIR_TARGET=263, DESKTOP_LOADING=273, DESKTOP_BANK=274, DESKTOP_CWD=275, DESKTOP_DRIVE=276;

    /** Exports the assembled boot sector and byte arrays for the unchanged disk installer. */
    public static void main(String[] args) throws java.io.IOException {
        if(args.length!=1) throw new IllegalArgumentException("Supply an output image path");
        byte[][] pages=Programs.bootImage(); byte[] image=new byte[65536];
        byte[] header={'J','V','C','P','M',20,(byte)pages.length,1};
        System.arraycopy(header,0,image,0,header.length);
        for(int page=0;page<pages.length;page++) System.arraycopy(pages[page],0,image,512+page*256,pages[page].length);
        java.nio.file.Files.write(java.nio.file.Path.of(args[0]),image);
    }

    public static byte[][] assemble(int firstPage) {
        java.util.Set<Integer> local=new java.util.HashSet<>(), pinned=new java.util.HashSet<>();
        for(int pass=0;pass<4096;pass++) {
            Asm s=new Asm(firstPage,local);byte[][] result=assembleInto(s);
            var next=new java.util.HashSet<>(local);
            for(var branch:s.branches) {
                boolean same=s.labels.get(branch.target())/256==branch.page();
                if(branch.local()&&!same){pinned.add(branch.id());next.remove(branch.id());}
                else if(same&&!pinned.contains(branch.id()))next.add(branch.id());
            }
            if(next.equals(local)){if(s.codeEndPage>=246)throw new IllegalStateException("Kernel overlaps font pointers: "+s.codeEndPage);return result;}
            local=next;
        }
        throw new IllegalStateException("DOS branch layout did not converge");
    }

    private static byte[][] assembleInto(Asm s) {
        s.label("boot");
        // Display geometry used by the same shell in fullscreen and JCTRL.
        s.store(252,A);s.emit(STORE_MEM_ABSOLUTE,A,0,252,24);
        s.store(244,A);s.emit(STORE_MEM_ABSOLUTE,A,0,252,25);
        s.store(236,A);s.emit(STORE_MEM_ABSOLUTE,A,0,252,26);
        s.store(0,Registers.STORAGE_PAGE.address());
        s.emit(CLEAR_GRA);
        s.set(X,4);
        s.set(Y,4);
        s.set(MODE,0);
        s.set(RENDERING,0);
        s.set(ENTER_PENDING,0);
        s.set(SCRIPT_ACTIVE,0);
        s.set(KEY_SOUND,1);
        s.set(INK,28);
        s.set(TAB_PENDING,0);
        s.set(KEY_SYNTH,0);
        s.set(H_COUNT,0);
        s.set(H_NEXT,0);
        s.set(H_VIEW,0);
        s.set(C_ACTIVE,0);
        s.set(DESKTOP_LOADING,0);
        s.set(DISK_BANK,2);
        s.set(CWD,0);
        s.set(SHIFT,0);
        s.text("JV-DOS 1.4\n128K RAM  16M DISK\nHELP FOR COMMANDS\nJCTRL FOR DESKTOP\n");
        // Seed a real executable once; later boots preserve the user's directory and data.
        s.store(2,A);
        s.store(255,B);
        s.store(0,C);
        s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);
        s.store(0x4a,A);
        s.eq(D,A,"boot.autoexec");
        byte[] hello=helloProgram();
        s.store(2,A);s.store(0,B);
        s.store(0,C);s.store(hello.length,D);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);
        String filename="HELLO.COM";
        for(int i=0;i<12;i++) { s.store(i+1,C);s.store(i<filename.length()?filename.charAt(i):0,D);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D); }
        for(int i=0;i<hello.length;i++) { s.store(16+i,C);s.store(hello[i]&255,D);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D); }
        s.diskLiteral(255,0,0x4a);
        s.label("boot.autoexec");
        // Optional startup source on the system disk, bank 2 root. Never create or replace it.
        String autoexec="AUTOEXEC.JVS";
        for(int n=0;n<12;n++) {
            s.store(autoexec.charAt(n),D);
            s.emit(STORE_MEM_ABSOLUTE,D,0,NAME,64+n);
        }
        s.call("search",SEARCH_RETURN);
        s.get(A,FOUND);s.store(255,B);s.eq(A,B,"shell");
        s.put(A,SLOT);
        s.call("file.load",FILE_RETURN);
        s.jump("script.start");

        s.label("shell");
        s.get(A,DESKTOP_LOADING);s.store(0,B);s.eq(A,B,"shell.desktop.restored");
        s.get(A,DESKTOP_BANK);s.put(A,DISK_BANK);s.get(A,DESKTOP_CWD);s.put(A,CWD);
        s.get(A,DESKTOP_DRIVE);s.store(0,B);s.emit(ADD,A,B,Registers.STORAGE_PAGE.address());
        s.set(DESKTOP_LOADING,0);s.label("shell.desktop.restored");
        s.get(A,SCRIPT_ACTIVE);
        s.store(1,B);
        s.eq(A,B,"script.next");
        s.set(INK,28);
        s.set(COUNT,0);
        s.set16(CURSOR,CURSOR_H,0);
        s.set(INDEX_H,0);
        s.set(MODE,0);
        s.set(H_VIEW,0);
        s.set(C_ACTIVE,0);
        s.text("DOS> ");
        s.get(A,X);
        s.put(A,BASE_X);
        s.get(A,Y);
        s.put(A,BASE_Y);
        s.label("poll");
        s.cursor(28);
        s.emit(DISPLAY);
        s.get(A,TAB_PENDING);
        s.store(0,B);
        s.eq(A,B,"poll.hardware");
        s.store(1,B);
        s.emit(SUB,A,B,A);
        s.put(A,TAB_PENDING);
        s.set(KEY_SYNTH,1);
        s.store(32,D);
        s.jump("poll.input");
        s.label("poll.hardware");
        s.set(KEY_SYNTH,0);
        s.emit(READ_INPUT,0,D);
        s.label("poll.input");
        s.store(0,A);
        s.eq(D,A,"poll");
        s.cursor(0);
        s.store(16,A);
        s.eq(D,A,"shift.on");
        s.store(144,A);
        s.eq(D,A,"shift.off");
        for(int scan:new int[]{192,222}) { s.store(scan,A);
            s.eq(D,A,"extended."+scan); }
        String dispatch="key.dispatch";
        for(int key:new int[]{9,27,8,10,37,38,39,40,36,35,127}) { s.store(key,A);
            s.eq(D,A,"key.click"); }
        for(int key=32;key<=111;key++) if(printable(key)) { s.store(key,A);
            s.eq(D,A,"key.click"); }
        s.jump("poll");
        s.label("key.click");
        s.get(A,KEY_SYNTH);
        s.store(1,B);
        s.eq(A,B,dispatch);
        s.click();
        s.label(dispatch);
        s.store(9,A);
        s.eq(D,A,"key.tab");
        s.put(D,KEY_RAW);
        s.set(C_ACTIVE,0);
        s.get(D,KEY_RAW);
        s.store(27,A);
        s.eq(D,A,"cancel");
        s.store(8,A);
        s.eq(D,A,"backspace");
        s.store(10,A);
        s.eq(D,A,"enter");
        for(int key:new int[]{37,38,39,40,36,35,127}) { s.store(key,A);
            s.eq(D,A,"move."+key); }
        // Printable make events only; navigation has separate handlers and releases are ignored.
        s.jump("keyboard.translate");
        s.label("shift.on");
        s.set(SHIFT,1);
        s.jump("poll");
        s.label("shift.off");
        s.set(SHIFT,0);
        s.jump("poll");
        for(int scan:new int[]{192,222}) {
            s.label("extended."+scan);
            s.put(D,KEY_RAW);
            s.emit(READ_INPUT,1+scan/8,A);
            s.store(1<<(7-scan%8),B);
            s.emit(MULTIPLY,A,B,A);
            s.store(128,B);
            s.store(0,C);
            String bit=s.unique("keyboard.bit"), down=s.unique("keyboard.down");
            s.label(bit);
            s.eq(B,C,down);
            s.eq(A,C,"poll");
            s.store(1,C);
            s.emit(SUB,A,C,A);
            s.emit(SUB,B,C,B);
            s.store(0,C);
            s.jump(bit);
            s.label(down);
            s.get(D,KEY_RAW);
            s.jump("key.click");
        }
        s.label("key.tab");
        s.get(A,MODE);
        s.store(0,B);
        s.eq(A,B,"complete.start");
        s.set(TAB_PENDING,4);
        s.jump("poll");
        s.label("key");
        s.put(D,CH);
        s.get(A,MODE);
        s.store(1,B);
        s.eq(A,B,"edit.key");
        s.jump("line.insert");

        s.label("backspace");
        s.get(A,MODE);
        s.store(1,B);
        s.eq(A,B,"edit.backspace");
        s.jump("line.backspace");
        s.label("erase");
        s.get(A,X);
        s.store(4,B);
        s.eq(A,B,"erase.wrap");
        s.emit(SUB,A,B,A);
        s.put(A,X);
        s.jump("erase.cell");
        s.label("erase.wrap");
        s.set(X,248);
        s.get(A,Y);
        s.store(8,B);
        s.emit(SUB,A,B,A);
        s.put(A,Y);
        s.label("erase.cell");
        s.store(32,D);
        s.call("glyph",GLYPH_RETURN);
        s.jump("poll");
        s.label("cancel");
        s.get(A,MODE);
        s.store(1,B);
        s.eq(A,B,"edit.save");
        s.text("\n");
        s.jump("shell");
        s.label("enter");
        s.get(A,MODE);
        s.store(1,B);
        s.eq(A,B,"edit.newline");
        s.get(A,COUNT);
        s.put(A,CURSOR);
        s.set(ENTER_PENDING,1);
        s.jump("redraw");
        s.label("enter.command");
        s.set(ENTER_PENDING,0);
        s.call("history.save",H_RETURN);
        s.get(A,COUNT);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.store(0,D);
        s.store(LINE,E);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        // Interactive Enter moves past the prompt; source statements should not add blank lines.
        s.get(A,SCRIPT_ACTIVE);s.store(1,B);s.eq(A,B,"command.dispatch");
        s.text("\n");
        s.label("command.dispatch");
        s.get(A,COUNT);
        s.store(0,B);
        s.eq(A,B,"shell");
        for(String command:COMMANDS) {
            String next=s.unique("command");
            if(!java.util.Set.of("PRINT","ECHO","CLS","CLEAR","STORE","ADD","SUB","MULTIPLY","INC","OUTPUT","GOTO","IF","BEEP","REM","END","STOP","JCTRL").contains(command)) {
                s.get(A,SCRIPT_ACTIVE);
                s.store(1,B);
                s.eq(A,B,next);
            }
            for(int i=0;i<command.length();i++) {
                s.read(LINE,64+i,D);
                s.store(command.charAt(i),A);
                s.ne(D,A,next);
            }
            s.read(LINE,64+command.length(),D);
            s.store(0,A); String empty=s.unique("empty.argument");
            s.eq(D,A,empty);
            s.store(32,A);
            s.ne(D,A,next);
            s.set(ARG,command.length()+1);
            s.jump("cmd."+command);
            s.label(empty);
            s.set(ARG,command.length());
            s.jump("cmd."+command);
            s.label(next);
        }
        s.get(A,SCRIPT_ACTIVE);
        s.store(1,B);
        s.eq(A,B,"script.error");
        s.text("BAD COMMAND - CODEHELP\n");
        s.jump("shell");

        s.label("cmd.HELP");
        s.text("BROWSE HOST[:PORT]/PATH - HTTP\n");
        s.text("MKDIR NAME  CD NAME  CD ..\nCD /  RMDIR NAME\nMOVE FILE FOLDER OR NEWNAME\nMOVE FILE .. OR /\nDELETE = DEL; RENAME = REN\n");
        s.text("DIR  TYPE FILE  DEL FILE\nWRITE FILE TEXT\nAPPEND FILE TEXT\nEDIT FILE - ESC SAVES\nCOPY FILE NEW  REN FILE NEW\nRUN FILE.COM  ECHO TEXT\nSAVE FILE HEX BYTE PAIRS\nCLS VER MEM REBOOT EXIT\nBACKSPACE EDITS INPUT\n");
        s.text("DRIVE N - SELECT DISK 0-255\nDRIVE SHOWS CURRENT DISK\nJCTRL - OPEN BUNDLED DESKTOP\n");
        s.text("UP DOWN HISTORY  TAB COMPLETE\nBANK + / BANK -\nBANK SHOWS CURRENT BANK\nCODEHELP FOR SOURCE PROGRAMS\nSOUND ON OR SOUND OFF\nVIEW FILE  IMAGE FILE  PLAY FILE\n");
        s.jump("shell");
        s.label("cmd.CLEAR");
        s.label("cmd.CLS");
        s.emit(CLEAR_GRA);
        s.set(X,4);
        s.set(Y,4);
        s.jump("shell");
        s.label("cmd.VER");
        s.text("JV-DOS 1.4\nGUEST BYTECODE KERNEL\n");
        s.jump("shell");
        s.label("cmd.MEM");
        s.text("131072 BYTES RAM\n256 BYTE TRANSIENT AREA\n254 BANKS X 255 FILES\nUP TO 61200 BYTES PER FILE\n");
        s.jump("shell");
        s.label("cmd.REBOOT");
        s.jump("boot");
        s.label("cmd.EXIT");
        s.text("SYSTEM HALTED\n");
        s.emit(DISPLAY);
        s.emit(STOP);
        s.label("cmd.PRINT");
        s.label("cmd.ECHO");
        s.label("echo.loop");
        s.get(A,ARG);
        s.get(B,COUNT);
        s.eq(A,B,"echo.end");
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.put(D,CH);
        s.call("print",PRINT_RETURN);
        s.get(A,ARG);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,ARG);
        s.jump("echo.loop");
        s.label("echo.end");
        s.text("\n");
        s.jump("shell");

        emitDrive(s);
        s.label("cmd.BANK");
        s.get(A,ARG);
        s.get(B,COUNT);
        s.eq(A,B,"bank.show");
        s.store(1,C);
        s.emit(ADD,A,C,C);
        s.ne(C,B,"bank.bad");
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.get(A,DISK_BANK);
        s.store('+',B);
        s.eq(D,B,"bank.next");
        s.store('-',B);
        s.ne(D,B,"bank.bad");
        s.store(2,B);
        s.eq(A,B,"bank.show");
        s.store(1,B);
        s.emit(SUB,A,B,A);
        s.jump("bank.set");
        s.label("bank.next");
        s.store(255,B);
        s.eq(A,B,"bank.show");
        s.emit(INCREMENT_REGISTER,A);
        s.label("bank.set");
        s.put(A,DISK_BANK);
        s.set(CWD,0);
        s.jump("bank.show");
        s.label("bank.bad");
        s.text("USE BANK + OR BANK -\n");
        s.jump("shell");
        s.label("bank.show");
        s.text("DISK BANK ");
        s.get(D,DISK_BANK);
        s.put(D,TEMP);
        s.call("number",NUMBER_RETURN);
        s.text("\n");
        s.jump("shell");
        emitDirectories(s);
        s.label("cmd.DIR");
        s.set(SLOT,0);
        s.label("dir.loop");
        s.diskRead(1,D);
        s.store(0,A);
        s.eq(D,A,"dir.next");
        s.store(255,A);
        s.eq(D,A,"dir.next");
        s.parentFilter(SLOT,"dir.next");
        s.set(INDEX,1);
        s.label("dir.name");
        s.get(C,INDEX);
        s.diskReadAt(C,D);
        s.store(0,A);
        s.eq(D,A,"dir.size");
        s.put(D,CH);
        s.call("print",PRINT_RETURN);
        s.get(A,INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,INDEX);
        s.store(13,B);
        s.eq(A,B,"dir.size");
        s.jump("dir.name");
        s.label("dir.size");
        s.diskRead(13,D);
        s.store(4,A);
        String regular=s.unique("dir.regular");
        s.ne(D,A,regular);
        s.text(" <DIR>\n");
        s.jump("dir.next");
        s.label(regular);
        s.text(" ");
        s.diskRead(0,D);
        s.put(D,TEMP);
        s.diskRead(14,D);
        s.put(D,NUM_H);
        s.call("number16",NUMBER16_RETURN);
        s.text(" BYTES\n");
        s.label("dir.next");
        s.get(A,SLOT);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,SLOT);
        s.store(255,B);
        s.ne(A,B,"dir.loop");
        s.jump("shell");

        // All commands below share guest-side argument scanning and directory lookup.
        for(String command:new String[]{"TYPE","CAT","VIEW","MORE","IMAGE","SHOW","PLAY","DEL","ERASE","WRITE","EDIT","APPEND","COPY","REN","RUN","SAVE"}) {
            s.label("cmd."+command);
            s.call("parse.name",SEARCH_RETURN);
            s.call("search",SEARCH_RETURN);
            if(command.equals("WRITE")||command.equals("EDIT")||command.equals("SAVE")) s.jump("prepare."+command);
            else {
                s.get(A,FOUND);
                s.store(255,B);
                s.eq(A,B,"missing");
                s.put(A,SLOT);
                s.call("file.load",FILE_RETURN);
                s.jump("do."+command);
            }
        }
        s.label("cmd.JCTRL");
        // Desktop takes over the source buffer; launching it ends the current script.
        s.set(SCRIPT_ACTIVE,0);
        // The managed desktop lives on the system disk; the record is outside the boot image.
        s.store(0,A);s.emit(ADD,Registers.STORAGE_PAGE.address(),A,A);s.put(A,WORK);s.store(0,Registers.STORAGE_PAGE.address());
        int[] packageMagic={'J','C','T',1};
        for(int n=0;n<packageMagic.length;n++) {
            s.store(0,A);s.store(1,B);s.store(n,C);s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);s.store(packageMagic[n],A);s.ne(D,A,"desktop.missing");
        }
        s.store(0,A);s.store(1,B);s.store(4,C);s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);s.store(2,A);s.emit(COMPARE_UNSIGNED,D,A,C);s.store(1,A);s.eq(C,A,"desktop.missing");
        // Save the file-operation context while loading the executable from the system disk.
        s.get(A,DISK_BANK);s.put(A,DESKTOP_BANK);s.get(A,CWD);s.put(A,DESKTOP_CWD);
        s.get(A,WORK);s.put(A,DESKTOP_DRIVE);s.put(D,DISK_BANK);
        s.set(DESKTOP_LOADING,1);s.set(CWD,0);
        String desktopCommand="RUN JCTRL.COM";
        for(int n=0;n<desktopCommand.length();n++){s.store(desktopCommand.charAt(n),D);s.emit(STORE_MEM_ABSOLUTE,D,0,LINE,64+n);}
        s.set(COUNT,desktopCommand.length());s.set(ARG,4);
        s.call("parse.name",SEARCH_RETURN);s.call("search",SEARCH_RETURN);
        s.get(A,FOUND);s.store(255,B);s.eq(A,B,"missing");s.put(A,SLOT);
        s.call("file.load",FILE_RETURN);
        s.get(A,DESKTOP_LOADING);s.store(0,B);s.eq(A,B,"desktop.context.restored");
        s.get(A,DESKTOP_BANK);s.put(A,DISK_BANK);s.get(A,DESKTOP_CWD);s.put(A,CWD);
        s.get(A,DESKTOP_DRIVE);s.store(0,B);s.emit(ADD,A,B,Registers.STORAGE_PAGE.address());
        s.set(DESKTOP_LOADING,0);s.label("desktop.context.restored");
        s.jump("do.RUN");
        s.label("desktop.missing");s.get(A,WORK);s.store(0,B);s.emit(ADD,A,B,Registers.STORAGE_PAGE.address());s.text("BUNDLED DESKTOP NOT INSTALLED\n");s.jump("shell");
        s.label("missing");
        s.text("FILE NOT FOUND\n");
        s.jump("shell");
        s.label("full");
        s.text("DIRECTORY FULL\n");
        s.jump("shell");
        s.label("usage");
        s.text("BAD OR MISSING FILE NAME\n");
        s.jump("shell");
        s.label("exists");
        s.text("DESTINATION EXISTS\n");
        s.jump("shell");
        for(String command:new String[]{"WRITE","EDIT","SAVE"}) {
            s.label("prepare."+command);
            s.get(A,FOUND);
            s.store(255,B); String found=s.unique("found"), ready=s.unique("prepared");
            s.ne(A,B,found);
            s.get(A,FREE);
            s.eq(A,B,"full");
            s.put(A,SLOT);
            s.call("file.empty",FILE_RETURN);
            s.jump(ready);
            s.label(found);
            s.put(A,SLOT);
            s.call("file.load",FILE_RETURN);
            s.label(ready);
            if(!command.equals("EDIT")) s.set16(LENGTH,LENGTH_H,0);
            s.jump("do."+command);
        }
        s.label("do.APPEND");
        s.set(FILE_TYPE,1);
        s.jump("write.text");
        s.label("do.WRITE");
        s.set(FILE_TYPE,1);
        s.label("write.text");
        s.get(A,ARG);
        s.get(B,COUNT);
        s.eq(A,B,"write.done");
        s.eq16Value(LENGTH,LENGTH_H,61200,"file.full");
        s.get(A,ARG);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.bufferWrite(LENGTH,LENGTH_H,D);
        s.inc16(LENGTH,LENGTH_H);
        s.get(A,ARG);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,ARG);
        s.jump("write.text");
        s.label("write.done");
        s.call("file.save",FILE_RETURN);
        s.text("SAVED\n");
        s.jump("shell");
        s.label("do.SAVE");
        s.set(FILE_TYPE,0);
        s.set(SECOND,0);
        s.label("hex.loop");
        s.get(A,ARG);
        s.get(B,COUNT);
        s.eq(A,B,"hex.end");
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.store(32,A);
        s.eq(D,A,"hex.next");
        for(int digit=0;digit<16;digit++) { s.store(digit<10?'0'+digit:'A'+digit-10,A);
            s.eq(D,A,"hex."+digit); }
        s.jump("hex.bad");
        for(int digit=0;digit<16;digit++) { s.label("hex."+digit);
            s.set(TEMP,digit);
            s.jump("hex.value"); }
        s.label("hex.value");
        s.get(A,SECOND);
        s.store(0,B);
        s.ne(A,B,"hex.low");
        s.get(A,TEMP);
        s.store(16,B);
        s.emit(MULTIPLY,A,B,A);
        s.put(A,SOURCE);
        s.set(SECOND,1);
        s.jump("hex.next");
        s.label("hex.low");
        s.get(A,SOURCE);
        s.get(B,TEMP);
        s.emit(ADD,A,B,D);
        s.bufferWrite(LENGTH,LENGTH_H,D);
        s.inc16(LENGTH,LENGTH_H);
        s.set(SECOND,0);
        s.label("hex.next");
        s.get(A,ARG);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,ARG);
        s.jump("hex.loop");
        s.label("hex.end");
        s.get(A,SECOND);
        s.store(0,B);
        s.eq(A,B,"write.done");
        s.label("hex.bad");
        s.text("INVALID HEX BYTE PAIRS\n");
        s.jump("shell");
        s.label("do.EDIT");
        s.set(FILE_TYPE,1);
        s.set(MODE,1);
        s.set16(CURSOR,CURSOR_H,0);
        s.jump("edit.redraw");
        s.label("edit.newline");
        s.set(CH,10);
        s.jump("edit.store");
        s.label("edit.key");
        s.label("edit.store");
        s.jump("edit.insert");
        s.label("edit.backspace");
        s.jump("line.backspace");
        s.label("edit.save");
        s.call("file.save",FILE_RETURN);
        s.set(INK,28);
        s.text("\nSAVED\n");
        s.jump("shell");
        s.label("do.DEL");
        s.label("do.ERASE");
        s.call("file.delete",FILE_RETURN);
        s.text("DELETED\n");
        s.jump("shell");
        s.label("do.TYPE");
        s.label("do.CAT");
        s.set16(INDEX,INDEX_H,0);
        s.label("type.loop");
        s.eq16(INDEX,INDEX_H,LENGTH,LENGTH_H,"type.end");
        s.bufferRead(INDEX,INDEX_H,D);
        s.put(D,CH);
        s.call("print",PRINT_RETURN);
        s.inc16(INDEX,INDEX_H);
        s.jump("type.loop");
        s.label("type.end");
        s.text("\n");
        s.jump("shell");

        for(String command:new String[]{"COPY","REN"}) {
            s.label("do."+command);
            s.get(A,SLOT);
            s.put(A,SOURCE);
            s.call("parse.name",SEARCH_RETURN);
            s.call("search",SEARCH_RETURN);
            s.get(A,FOUND);
            s.store(255,B);
            s.ne(A,B,"exists");
            if(command.equals("COPY")) { s.get(A,FREE);
                s.eq(A,B,"full");
                s.put(A,SLOT);
                s.call("file.save",FILE_RETURN); }
            else { s.get(A,SOURCE);
                s.put(A,SLOT);
                s.call("name.write",SEARCH_RETURN); }
            s.text("OK\n");
            s.jump("shell");
        }
        s.label("do.RUN");
        s.get(A,SCRIPT_ACTIVE);
        s.store(1,B);
        s.eq(A,B,"script.error");
        s.get(D,FILE_TYPE);
        s.store(2,A);
        s.eq(D,A,"do.IMAGE");
        s.store(3,A);
        s.eq(D,A,"do.PLAY");
        s.store(1,A);
        s.eq(D,A,"script.start");
        s.set16(INDEX,INDEX_H,0);
        s.bufferRead(INDEX,INDEX_H,D);
        for(int c=32;c<=90;c++) if(printable(c)) { s.store(c,A);
            s.eq(D,A,"script.start"); }
        s.eq16Value(LENGTH,LENGTH_H,0,"invalid.program");
        // Validate all bytes, including instruction boundaries at each 256-byte code page.
        s.label("run.verify");
        s.eq16(INDEX,INDEX_H,LENGTH,LENGTH_H,"run.verified");
        s.bufferRead(INDEX,INDEX_H,D);
        for(OPCode opcode:OPCode.values()) { s.store(opcode.code()&255,A);
            s.eq(D,A,"run.descriptor"); }
        s.jump("invalid.program");
        s.label("run.descriptor");
        s.store(5,A);
        s.emit(MULTIPLY,D,A,C);
        s.store(64,A);
        s.emit(ADD,C,A,C);
        s.put(C,DESCRIPTOR);
        s.readAt(247,C,D);
        s.put(D,REMAINING);
        // A page can end after an instruction, but an operand cannot cross to another page.
        s.get(A,INDEX);
        s.store(0,B);
        s.emit(SUB,B,D,B);
        s.emit(COMPARE_UNSIGNED,A,B,C);
        s.store(2,D);
        s.eq(C,D,"invalid.program");
        s.label("run.consume");
        s.inc16(INDEX,INDEX_H);
        s.get(A,DESCRIPTOR);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,DESCRIPTOR);
        s.get(A,REMAINING);
        s.store(1,B);
        s.emit(SUB,A,B,A);
        s.put(A,REMAINING);
        s.store(0,B);
        s.eq(A,B,"run.verify");
        s.eq16(INDEX,INDEX_H,LENGTH,LENGTH_H,"invalid.program");
        s.get(C,DESCRIPTOR);
        s.readAt(247,C,D);
        s.store(0,A);
        s.eq(D,A,"run.consume");
        s.bufferRead(INDEX,INDEX_H,D);
        for(int register=0;register<Registers.values().length;register++) { s.store(register,A);
            s.eq(D,A,"run.consume"); }
        s.jump("invalid.program");
        s.label("invalid.program");
        s.text("INVALID EXECUTABLE\nCHECK OPCODES AND PAGE BOUNDARIES\n");
        s.jump("shell");
        s.label("run.verified"); String small=s.unique("run.small");
        s.less16Value(LENGTH,LENGTH_H,241,small);
        s.store(1,A);
        s.store(0,B);
        s.store(0,C);
        s.emit(CHANGE_BANK_PAGE_JUMP,A,B,C);
        s.label(small);
        s.set16(INDEX,INDEX_H,0);
        s.label("run.load");
        s.eq16(INDEX,INDEX_H,LENGTH,LENGTH_H,"run.start");
        s.bufferRead(INDEX,INDEX_H,D);
        s.get(C,INDEX);
        s.store(TPA,E);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.inc16(INDEX,INDEX_H);
        s.jump("run.load");
        s.label("run.start");
        s.store(TPA,G);
        s.store(0,H);
        s.emit(CHANGE_PAGE_JUMP,G,H);
        // Guest COM ABI: D=character, A=return page, B=return address.
        s.label("com.print");
        s.put(D,CH);
        s.put(A,PRINT_RETURN);
        s.put(B,PRINT_RETURN+1);
        s.jump("print");

        s.label("parse.name");
        s.set(INDEX,0);
        s.label("parse.trim");
        s.get(A,ARG);
        s.get(B,COUNT);
        s.eq(A,B,"usage");
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.store(32,A);
        s.ne(D,A,"parse.loop");
        s.get(A,ARG);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,ARG);
        s.jump("parse.trim");
        s.label("parse.loop");
        s.get(A,ARG);
        s.get(B,COUNT);
        s.eq(A,B,"parse.end");
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.store(32,A);
        s.eq(D,A,"parse.space");
        for(int separator:new int[]{47,92}) { s.store(separator,A); s.eq(D,A,"usage"); }
        s.get(A,INDEX);
        s.store(12,B);
        s.eq(A,B,"usage");
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.store(NAME,E);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.get(A,INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,INDEX);
        s.get(A,ARG);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,ARG);
        s.jump("parse.loop");
        s.label("parse.space");
        s.get(A,ARG);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,ARG);
        s.label("parse.end");
        s.read(NAME,64,D);
        s.store(46,A);
        String ordinary=s.unique("name.ordinary");
        s.ne(D,A,ordinary);
        s.get(A,INDEX);
        s.store(1,B);
        s.eq(A,B,"usage");
        s.read(NAME,65,D);
        s.store(46,A);
        s.ne(D,A,ordinary);
        s.get(A,INDEX);
        s.store(2,B);
        s.eq(A,B,"usage");
        s.label(ordinary);
        s.get(A,INDEX);
        s.store(0,B);
        s.eq(A,B,"usage");
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.store(0,D);
        s.store(NAME,E);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.ret(SEARCH_RETURN);

        s.label("search");
        s.set(FREE,255);
        s.set(FOUND,255);
        s.set(SLOT,0);
        s.label("search.slot");
        s.diskRead(1,D);
        s.store(0,A);
        s.eq(D,A,"search.free");
        s.store(255,A);
        s.eq(D,A,"search.next");
        s.parentFilter(SLOT,"search.next");
        s.set(INDEX,0);
        s.label("search.char");
        s.get(A,INDEX);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(NAME,C,D);
        s.put(D,TEMP);
        s.get(A,INDEX);
        s.store(1,B);
        s.emit(ADD,A,B,C);
        s.diskReadAt(C,D);
        s.get(A,TEMP);
        s.ne(D,A,"search.next");
        s.store(0,B);
        s.eq(A,B,"search.found");
        s.get(A,INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,INDEX);
        s.store(12,B);
        s.eq(A,B,"search.found");
        s.jump("search.char");
        s.label("search.free");
        s.get(A,FREE);
        s.store(255,B);
        s.ne(A,B,"search.next");
        s.get(A,SLOT);
        s.put(A,FREE);
        s.label("search.next");
        s.get(A,SLOT);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,SLOT);
        s.store(255,B);
        s.ne(A,B,"search.slot");
        s.ret(SEARCH_RETURN);
        s.label("search.found");
        s.get(A,SLOT);
        s.put(A,FOUND);
        s.ret(SEARCH_RETURN);

        s.label("name.write");
        s.get(C,SLOT);
        s.emit(INCREMENT_REGISTER,C);
        s.get(A,DISK_BANK);
        s.store(255,B);
        s.get(D,CWD);
        s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);
        s.set(INDEX,0);
        s.label("name.loop");
        s.get(A,INDEX);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(NAME,C,D);
        s.put(D,CH);
        s.get(A,INDEX);
        s.store(1,B);
        s.emit(ADD,A,B,C);
        s.get(D,CH);
        s.diskWriteAt(C,D);
        s.get(A,INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,INDEX);
        s.store(12,B);
        s.ne(A,B,"name.loop");
        s.ret(SEARCH_RETURN);

        // Decimal output supports the full unsigned 16-bit file length.
        s.label("number");
        s.set(NUM_H,0);
        s.call("number16",NUMBER16_RETURN);
        s.ret(NUMBER_RETURN);
        s.label("number16");
        s.copy16(TEMP,NUM_H,DECIMAL,DECIMAL_H);
        s.set(LEADING,0);
        for(int divisor:new int[]{10000,1000,100,10,1}) {
            String loop=s.unique("decimal.subtract"), digit=s.unique("decimal.digit"), skip=s.unique("decimal.skip"), output=s.unique("decimal.output");
            s.set(DIGIT,0);
            s.label(loop);
            s.less16Value(DECIMAL,DECIMAL_H,divisor,digit);
            s.subtract16(DECIMAL,DECIMAL_H,divisor);
            s.get(A,DIGIT);
            s.emit(INCREMENT_REGISTER,A);
            s.put(A,DIGIT);
            s.jump(loop);
            s.label(digit);
            if(divisor!=1) { s.get(A,DIGIT);
                s.store(0,B);
                s.ne(A,B,output);
                s.get(A,LEADING);
                s.eq(A,B,skip); }
            s.label(output);
            s.get(A,DIGIT);
            s.store(48,B);
            s.emit(ADD,A,B,D);
            s.put(D,CH);
            s.call("print",PRINT_RETURN);
            s.set(LEADING,1);
            s.label(skip);
        }
        s.ret(NUMBER16_RETURN);

        s.label("message");
        s.get(A,MESSAGE_PAGE);
        s.get(C,MESSAGE_ADDRESS);
        s.readDynamic(A,C,D);
        s.store(0,A); String messageEnd="message.end";
        s.eq(D,A,messageEnd);
        s.put(D,CH);
        s.call("print",PRINT_RETURN);
        s.get(A,MESSAGE_ADDRESS);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,MESSAGE_ADDRESS);
        s.jump("message");
        s.label(messageEnd);
        s.ret(MESSAGE_RETURN);

        s.label("print");
        s.get(D,CH);
        s.store(10,A);
        s.eq(D,A,"newline");
        s.get(A,X);
        s.get(B,Y);
        s.store(0,C);
        s.emit(ADD,A,C,E);
        s.emit(ADD,B,C,F);
        s.get(D,CH); // The reader clobbers E/F, so restore coordinates after reading the character.
        s.get(A,X);
        s.get(B,Y);
        s.store(0,C);
        s.emit(ADD,A,C,E);
        s.emit(ADD,B,C,F);
        s.call("glyph",GLYPH_RETURN);
        s.get(A,X);
        s.store(4,B);
        s.emit(ADD,A,B,A);
        s.put(A,X);
        s.emit(LOAD_MEM_ABSOLUTE,0,252,24,B);
        s.eq(A,B,"newline");
        s.ret(PRINT_RETURN);
        s.label("newline");
        s.set(X,4);
        s.get(A,Y);
        s.store(8,B);
        s.emit(ADD,A,B,A);
        s.put(A,Y);
        s.emit(LOAD_MEM_ABSOLUTE,0,252,25,B); String noScroll=s.unique("noscroll");
        s.ne(A,B,noScroll);
        // Move the framebuffer up eight rows entirely through READ_GRA_MEM/PUSH_GRA_MEM.
        s.store(0,A);
        s.store(8,B);
        s.store(0,C);
        s.emit(LOAD_MEM_ABSOLUTE,0,252,25,F);s.store(4,D);s.emit(ADD,F,D,F);
        s.label("scroll.pixel");
        s.emit(READ_GRA_MEM,A,B,D);
        s.emit(PUSH_GRA_MEM,A,C,D);
        s.emit(INCREMENT_REGISTER,A);
        s.emit(LOAD_MEM_ABSOLUTE,0,252,24,E);s.store(4,D);s.emit(ADD,E,D,E);
        s.ne(A,E,"scroll.pixel");s.store(0,A);
        s.emit(INCREMENT_REGISTER,B);
        s.emit(INCREMENT_REGISTER,C);
        s.ne(C,F,"scroll.pixel");
        s.store(0,D);
        s.label("scroll.clear");
        s.emit(PUSH_GRA_MEM,A,C,D);
        s.emit(INCREMENT_REGISTER,A);
        s.ne(A,E,"scroll.clear");s.store(0,A);
        s.emit(INCREMENT_REGISTER,C);
        s.emit(LOAD_MEM_ABSOLUTE,0,252,25,F);s.store(12,B);s.emit(ADD,F,B,F);
        s.ne(C,F,"scroll.clear");
        s.emit(LOAD_MEM_ABSOLUTE,0,252,26,A);s.put(A,Y);
        s.get(A,MODE);
        s.store(0,B); String editorScroll=s.unique("editor.scroll");
        s.ne(A,B,editorScroll);
        s.get(A,BASE_Y);
        s.store(8,B);
        s.emit(SUB,A,B,A);
        s.put(A,BASE_Y);
        s.label(editorScroll);
        s.get(A,RENDERING);
        s.store(1,B);
        s.ne(A,B,noScroll);
        s.get(A,TARGET);
        s.ne(A,B,noScroll);
        s.get(A,CURSOR_Y);
        s.store(8,B);
        s.emit(SUB,A,B,A);
        s.put(A,CURSOR_Y);
        s.label(noScroll);
        s.ret(PRINT_RETURN);

        emitBrowser(s); emitEditor(s); emitSourceLanguage(s); emitMedia(s); emitKeyboard(s); emitFontRenderer(s);
        emitFileSystem(s); emitShellNavigation(s);emitMove(s);
        return s.finish();
    }

    private static void emitKeyboard(Asm s) {
        s.label("keyboard.translate");
        for(int c='A';c<='Z';c++) { s.store(c,A);
            s.eq(D,A,"keyboard.letter"); }
        int[][] mapping={{32,32,32},{44,44,60},{45,45,95},{46,46,62},{47,47,63},
            {48,48,41},{49,49,33},{50,50,64},{51,51,35},{52,52,36},{53,53,37},
            {54,54,94},{55,55,38},{56,56,42},{57,57,40},{59,59,58},{61,61,43},
            {91,91,123},{92,92,124},{93,93,125},{192,96,126},{222,39,34},
            {96,48,48},{97,49,49},{98,50,50},{99,51,51},{100,52,52},{101,53,53},
            {102,54,54},{103,55,55},{104,56,56},{105,57,57},{106,42,42},
            {107,43,43},{108,44,44},{109,45,45},{110,46,46},{111,47,47}};
        for(int[] key:mapping) { s.store(key[0],A);
            s.eq(D,A,"keyboard."+key[0]); }
        s.jump("poll");
        s.label("keyboard.letter");
        // Keep DOS commands uppercase, but let browser URLs use normal letter case.
        s.put(D,KEY_RAW); s.get(A,MODE); s.store(0,B); s.ne(A,B,"keyboard.letter.original");
        s.get(A,CURSOR); s.store(7,B); s.emit(COMPARE_UNSIGNED,A,B,C); s.store(1,B); s.eq(C,B,"keyboard.letter.original");
        for(int i=0;i<7;i++) { s.read(LINE,64+i,A); s.store("BROWSE ".charAt(i),B); s.ne(A,B,"keyboard.letter.original"); }
        s.get(A,SHIFT); s.store(0,B); s.ne(A,B,"keyboard.letter.original");
        s.get(D,KEY_RAW); s.store(32,A); s.emit(ADD,D,A,D); s.jump("key");
        s.label("keyboard.letter.original"); s.get(D,KEY_RAW); s.jump("key");
        for(int[] key:mapping) {
            s.label("keyboard."+key[0]);
            s.get(A,SHIFT);
            s.store(1,B); String shifted=s.unique("shifted");
            s.eq(A,B,shifted);
            s.store(key[1],D);
            s.jump("key");
            s.label(shifted);
            s.store(key[2],D);
            s.jump("key");
        }
    }

    /** HTTP/1.0 text browser, assembled entirely into guest instructions. */
    private static void emitBrowser(Asm s) {
        final int HOST_START=264, HOST_END=265, PATH=266, PORT=267, PORT_H=268,
                HEADERS=269, TAG=270, IDLE=271, IDLE_H=272;
        s.label("web.sendtext");
        s.get(B,MESSAGE_PAGE); s.get(C,MESSAGE_ADDRESS); s.readDynamic(B,C,D);
        s.store(0,A); s.eq(D,A,"web.sendtext.done"); s.store(5,A); s.emit(WRITE_NETWORK,A,D);
        s.get(A,MESSAGE_ADDRESS); s.emit(INCREMENT_REGISTER,A); s.put(A,MESSAGE_ADDRESS);
        s.jump("web.sendtext");
        s.label("web.sendtext.done"); s.ret(MESSAGE_RETURN);
        s.label("cmd.BROWSE");
        s.netWrite(0,4);
        s.get(A,ARG); s.put(A,HOST_START); s.put(A,INDEX);
        // Accept either a bare authority or an explicit http:// prefix.
        s.get(A,INDEX); s.store(64,B); s.emit(ADD,A,B,C); s.readAt(LINE,C,D);
        s.store('h',A); String prefix=s.unique("web.prefix"); s.eq(D,A,prefix);
        s.store('H',A); s.ne(D,A,"web.host.start"); s.label(prefix);
        String scheme="ttp://";
        for(int i=0;i<scheme.length();i++) {
            s.get(A,INDEX); s.store(65+i,B); s.emit(ADD,A,B,C); s.readAt(LINE,C,D);
            s.store(scheme.charAt(i),A); s.ne(D,A,"web.host.start");
        }
        s.get(A,INDEX); s.store(7,B); s.emit(ADD,A,B,A); s.put(A,INDEX); s.put(A,HOST_START);
        s.label("web.host.start");
        s.set16(PORT,PORT_H,80);
        s.label("web.host");
        s.get(A,INDEX); s.get(B,COUNT); s.eq(A,B,"web.host.end");
        s.store(64,B); s.emit(ADD,A,B,C); s.readAt(LINE,C,D);
        s.store('/',A); s.eq(D,A,"web.host.end");
        s.store(':',A); s.eq(D,A,"web.port.start");
        s.store(2,A); s.emit(WRITE_NETWORK,A,D);
        s.get(A,INDEX); s.emit(INCREMENT_REGISTER,A); s.put(A,INDEX); s.jump("web.host");
        s.label("web.port.start");
        s.get(A,INDEX); s.put(A,HOST_END);
        s.emit(INCREMENT_REGISTER,A); s.put(A,INDEX); s.set16(PORT,PORT_H,0);
        s.label("web.port");
        s.get(A,INDEX); s.get(B,COUNT); s.eq(A,B,"web.path.port");
        s.store(64,B); s.emit(ADD,A,B,C); s.readAt(LINE,C,D);
        s.store('/',A); s.eq(D,A,"web.path.port");
        s.store('0',A); s.emit(COMPARE_UNSIGNED,D,A,C); s.store(1,B); s.eq(C,B,"web.usage");
        s.store('9',A); s.emit(COMPARE_UNSIGNED,D,A,C); s.store(2,B); s.eq(C,B,"web.usage");
        s.store('0',A); s.emit(SUB,D,A,D); s.put(D,DIGIT);
        s.copy16(PORT,PORT_H,WORK,WORK_H); s.set16(PORT,PORT_H,0); s.set(TEMP,10);
        s.label("web.port.multiply");
        s.get(A,PORT); s.get(B,WORK); s.emit(ADD,A,B,C); s.put(C,PORT);
        s.emit(COMPARE_UNSIGNED,C,A,D); s.put(D,CMP_TEMP);
        s.get(A,PORT_H); s.get(B,WORK_H); s.emit(ADD,A,B,C);
        s.emit(COMPARE_UNSIGNED,C,A,D); s.store(1,B); s.eq(D,B,"web.usage"); s.put(C,PORT_H);
        s.get(A,CMP_TEMP); s.eq(A,B,"web.port.carry"); s.jump("web.port.next");
        s.label("web.port.carry"); s.get(A,PORT_H); s.emit(INCREMENT_REGISTER,A); s.put(A,PORT_H);
        s.store(0,B); s.eq(A,B,"web.usage");
        s.label("web.port.next"); s.get(A,TEMP); s.store(1,B); s.emit(SUB,A,B,A); s.put(A,TEMP);
        s.store(0,B); s.ne(A,B,"web.port.multiply");
        s.label("web.port.digit"); s.get(A,DIGIT); s.store(0,B); s.eq(A,B,"web.port.advance");
        s.inc16(PORT,PORT_H); s.eq16Value(PORT,PORT_H,0,"web.usage");
        s.get(A,DIGIT); s.store(1,B); s.emit(SUB,A,B,A); s.put(A,DIGIT); s.jump("web.port.digit");
        s.label("web.port.advance"); s.get(A,INDEX); s.emit(INCREMENT_REGISTER,A); s.put(A,INDEX); s.jump("web.port");
        s.label("web.host.end"); s.get(A,INDEX); s.put(A,HOST_END);
        s.label("web.path.port");
        s.get(A,HOST_START); s.get(B,HOST_END); s.eq(A,B,"web.usage");
        s.eq16Value(PORT,PORT_H,0,"web.usage");
        s.get(A,INDEX); s.put(A,PATH);
        s.store(3,A); s.get(D,PORT_H); s.emit(WRITE_NETWORK,A,D);
        s.store(4,A); s.get(D,PORT); s.emit(WRITE_NETWORK,A,D);
        s.text("CONNECTING - ESC CANCELS\n"); s.netWrite(0,1); s.set16(IDLE,IDLE_H,0);
        s.label("web.connect");
        s.call("web.poll",READ_RETURN);
        s.store(0,A); s.emit(READ_NETWORK,A,D); s.store(1,B); s.eq(D,B,"web.connect");
        s.netText("GET ");
        s.get(A,PATH); s.get(B,COUNT); String path=s.unique("web.path"); s.ne(A,B,path);
        s.netText("/"); s.label(path); s.get(A,PATH); s.put(A,INDEX);
        s.label("web.request.path"); s.get(A,INDEX); s.get(B,COUNT); s.eq(A,B,"web.request.headers");
        s.store(64,B); s.emit(ADD,A,B,C); s.readAt(LINE,C,D); s.store(5,A); s.emit(WRITE_NETWORK,A,D);
        s.get(A,INDEX); s.emit(INCREMENT_REGISTER,A); s.put(A,INDEX); s.jump("web.request.path");
        s.label("web.request.headers"); s.netText(" HTTP/1.0\r\nHost: ");
        // Host includes a supplied port, as required for virtual hosts on non-default ports.
        s.get(A,HOST_START); s.put(A,INDEX);
        s.label("web.request.host"); s.get(A,INDEX); s.get(B,PATH); s.eq(A,B,"web.request.send");
        s.store(64,B); s.emit(ADD,A,B,C); s.readAt(LINE,C,D); s.store(5,A); s.emit(WRITE_NETWORK,A,D);
        s.get(A,INDEX); s.emit(INCREMENT_REGISTER,A); s.put(A,INDEX); s.jump("web.request.host");
        s.label("web.request.send"); s.netText("\r\nConnection: close\r\nAccept: text/html, text/plain\r\n\r\n"); s.netWrite(0,2);
        s.set16(LENGTH,LENGTH_H,0); s.set(HEADERS,0); s.set(TAG,0); s.set16(IDLE,IDLE_H,0);
        s.label("web.receive"); s.call("web.poll",READ_RETURN);
        s.store(7,A); s.emit(READ_NETWORK,A,D); s.store(0,B); s.ne(D,B,"web.byte");
        s.store(8,A); s.emit(READ_NETWORK,A,D); s.ne(D,B,"web.byte");
        s.store(0,A); s.emit(READ_NETWORK,A,D); s.store(16,B); s.emit(COMPARE_UNSIGNED,D,B,C);
        s.store(1,B); s.ne(C,B,"web.done"); s.jump("web.receive");
        s.label("web.byte"); s.set16(IDLE,IDLE_H,0); s.store(6,A); s.emit(READ_NETWORK,A,D); s.put(D,CH);
        s.get(A,HEADERS); s.store(2,B); s.eq(A,B,"web.body");
        s.store(13,A); s.eq(D,A,"web.receive"); s.store(10,A); s.eq(D,A,"web.header.lf");
        s.set(HEADERS,0); s.jump("web.receive");
        s.label("web.header.lf"); s.get(A,HEADERS); s.emit(INCREMENT_REGISTER,A); s.put(A,HEADERS); s.jump("web.receive");
        s.label("web.body"); s.get(D,CH); s.store('<',A); s.eq(D,A,"web.tag");
        s.store('>',A); s.eq(D,A,"web.tag.end"); s.get(A,TAG); s.store(0,B); s.ne(A,B,"web.receive");
        s.get(D,CH); s.store(13,A); s.eq(D,A,"web.receive");
        s.store(10,A); s.eq(D,A,"web.append"); s.store(9,A); s.eq(D,A,"web.space");
        s.store(32,A); s.emit(COMPARE_UNSIGNED,D,A,C); s.store(1,B); s.eq(C,B,"web.receive");
        s.store(126,A); s.emit(COMPARE_UNSIGNED,D,A,C); s.store(2,B); s.eq(C,B,"web.space"); s.jump("web.append");
        s.label("web.tag"); s.set(TAG,1); s.jump("web.receive");
        s.label("web.tag.end"); s.set(TAG,0); s.store(10,D); s.jump("web.append");
        s.label("web.space"); s.store(32,D);
        s.label("web.append"); s.bufferWrite(LENGTH,LENGTH_H,D); s.inc16(LENGTH,LENGTH_H);
        s.eq16Value(LENGTH,LENGTH_H,60000,"web.done"); s.jump("web.receive");
        s.label("web.done"); s.netWrite(0,3); s.jump("do.VIEW");
        s.label("web.poll"); s.emit(DISPLAY); s.emit(READ_INPUT,0,D); s.store(27,A); s.eq(D,A,"web.cancel");
        s.store(1,A); s.emit(READ_NETWORK,A,D); s.store(0,B); s.ne(D,B,"web.error");
        s.inc16(IDLE,IDLE_H); s.eq16Value(IDLE,IDLE_H,65535,"web.error"); s.ret(READ_RETURN);
        s.label("web.error"); s.netWrite(0,3); s.text("HTTP NETWORK ERROR OR TIMEOUT\n"); s.jump("shell");
        s.label("web.cancel"); s.netWrite(0,3); s.jump("shell");
        s.label("web.usage"); s.netWrite(0,3); s.text("BROWSE HOST[:PORT]/PATH\nPLAIN HTTP ONLY - NO HTTPS\n"); s.jump("shell");
    }

    /** Palette images, eight-voice MIDI-note streams, and a read-only document pager. */
    private static void emitMedia(Asm s) {
        s.label("do.SHOW");
        s.label("do.IMAGE");
        s.diskRead(16,D);
        s.store('J',A);
        s.ne(D,A,"media.error");
        s.diskRead(17,D);
        s.store('I',A);
        s.ne(D,A,"media.error");
        // Dimensions fit the 240 by 240 image area below the title.
        for(int dimension:new int[]{IMAGE_WIDTH,IMAGE_HEIGHT}) {
            s.diskRead(dimension==IMAGE_WIDTH?18:19,D);
            s.put(D,dimension);
            s.store(0,A);
            s.eq(D,A,"media.error");
            s.store(240,A);
            s.emit(COMPARE_UNSIGNED,D,A,C);
            s.store(2,B);
            s.eq(C,B,"media.error");
        }
        // Compute the full 16-bit payload length; the CPU multiply is only eight bits.
        s.set16(WORK,WORK_H,4);
        s.set(IMAGE_ROW,0);
        s.label("image.length.row");
        s.set(IMAGE_COL,0);
        s.label("image.length.pixel");
        s.inc16(WORK,WORK_H);
        s.get(A,IMAGE_COL);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,IMAGE_COL);
        s.get(B,IMAGE_WIDTH);
        s.ne(A,B,"image.length.pixel");
        s.get(A,IMAGE_ROW);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,IMAGE_ROW);
        s.get(B,IMAGE_HEIGHT);
        s.ne(A,B,"image.length.row");
        String imageLength=s.unique("image.length.valid");
        s.eq16(WORK,WORK_H,LENGTH,LENGTH_H,imageLength);
        s.jump("media.error");
        s.label(imageLength);
        s.set(IMAGE_SCALE,1);
        s.set(IMAGE_LEFT,8);
        s.set(IMAGE_TOP,16);
        for(int dimension:new int[]{IMAGE_WIDTH,IMAGE_HEIGHT}) {
            s.get(A,dimension);
            s.store(15,B);
            s.emit(COMPARE_UNSIGNED,A,B,C);
            s.store(2,D);
            s.eq(C,D,"image.draw");
        }
        // Retain the enlarged presentation for legacy icons.
        s.set(IMAGE_SCALE,8);
        s.set(IMAGE_LEFT,68);
        s.set(IMAGE_TOP,32);
        s.label("image.draw");
        s.emit(CLEAR_GRA);
        s.set(X,4);
        s.set(Y,4);
        s.set(INK,28);
        s.text("IMAGE - ESC TO CLOSE\n");
        s.set16(INDEX,INDEX_H,4);
        s.set(IMAGE_ROW,0);
        s.set(IMAGE_COL,0);
        s.label("image.pixel");
        s.bufferRead(INDEX,INDEX_H,D);
        s.put(D,IMAGE_COLOR);
        s.store(127,A);
        s.emit(COMPARE_UNSIGNED,D,A,C);
        s.store(2,B);
        s.eq(C,B,"media.error");
        s.get(A,IMAGE_COL);
        s.get(B,IMAGE_SCALE);
        s.emit(MULTIPLY,A,B,A);
        s.get(B,IMAGE_LEFT);
        s.emit(ADD,A,B,A);
        s.put(A,IMAGE_X);
        s.get(A,IMAGE_ROW);
        s.get(B,IMAGE_SCALE);
        s.emit(MULTIPLY,A,B,A);
        s.get(B,IMAGE_TOP);
        s.emit(ADD,A,B,A);
        s.put(A,IMAGE_Y);
        s.set(TILE_X,0);
        s.set(TILE_Y,0);
        s.label("image.tile");
        s.get(A,IMAGE_X);
        s.get(B,TILE_X);
        s.emit(ADD,A,B,A);
        s.put(A,PIXEL_X);
        s.get(A,IMAGE_Y);
        s.get(B,TILE_Y);
        s.emit(ADD,A,B,B);
        s.get(A,PIXEL_X);
        s.get(D,IMAGE_COLOR);
        s.emit(PUSH_GRA_MEM,A,B,D);
        s.get(A,TILE_X);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,TILE_X);
        s.get(B,IMAGE_SCALE);
        s.ne(A,B,"image.tile");
        s.set(TILE_X,0);
        s.get(A,TILE_Y);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,TILE_Y);
        s.get(B,IMAGE_SCALE);
        s.ne(A,B,"image.tile");
        s.inc16(INDEX,INDEX_H);
        s.get(A,IMAGE_COL);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,IMAGE_COL);
        s.get(B,IMAGE_WIDTH);
        s.ne(A,B,"image.pixel");
        s.set(IMAGE_COL,0);
        s.get(A,IMAGE_ROW);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,IMAGE_ROW);
        s.get(B,IMAGE_HEIGHT);
        s.ne(A,B,"image.pixel");
        s.jump("media.wait");

        s.label("do.PLAY");
        s.diskRead(16,D);
        s.store('J',A);
        s.ne(D,A,"media.error");
        s.diskRead(17,D);
        s.store('M',A);
        s.ne(D,A,"media.error");
        s.diskRead(18,D);
        s.store(2,A);
        s.eq(D,A,"music.extended");
        s.store(1,A);
        s.ne(D,A,"media.error");
        s.diskRead(19,D);
        s.put(D,SOURCE);
        s.store(0,A);
        s.eq(D,A,"media.error");
        s.set16(WORK,WORK_H,4);
        s.label("music.length");
        s.get(A,SOURCE);
        s.store(0,B);
        s.eq(A,B,"music.length.done");
        for(int i=0;i<4;i++)s.inc16(WORK,WORK_H);
        s.get(A,SOURCE);
        s.store(1,B);
        s.emit(SUB,A,B,A);
        s.put(A,SOURCE);
        s.jump("music.length");
        s.label("music.length.done"); String lengthValid=s.unique("music.length.valid");
        s.eq16(WORK,WORK_H,LENGTH,LENGTH_H,lengthValid);
        s.jump("media.error");
        s.label(lengthValid);
        s.jump("music.start");
        // Version 2 derives its event count from the linked file length.
        s.label("music.extended");
        s.diskRead(19,D);
        s.store(0,A);
        s.ne(D,A,"media.error");
        s.set16(WORK,WORK_H,4);
        s.eq16(WORK,WORK_H,LENGTH,LENGTH_H,"media.error");
        s.label("music.extended.length");
        for(int i=0;i<4;i++) {
            s.inc16(WORK,WORK_H);
            s.eq16(WORK,WORK_H,LENGTH,LENGTH_H,i==3?"music.start":"media.error");
        }
        s.jump("music.extended.length");
        s.label("music.start");
        s.text("PLAYING - ESC TO STOP\n");
        s.set16(INDEX,INDEX_H,4);
        s.silence();
        s.label("music.event");
        s.eq16(INDEX,INDEX_H,LENGTH,LENGTH_H,"music.done");
        for(int variable:new int[]{MUSIC_FRAMES,MUSIC_VOICE,MUSIC_NOTE,MUSIC_CONTROL}) {
            s.bufferRead(INDEX,INDEX_H,D);
            s.put(D,variable);
            s.inc16(INDEX,INDEX_H);
        }
        s.get(A,MUSIC_VOICE); String voice=s.unique("music.voice"); for(int n=0;n<8;n++) { s.store(n,B);
            s.eq(A,B,voice); } s.jump("media.error");
        s.label(voice);
        s.get(A,MUSIC_CONTROL); String control=s.unique("music.control"); for(int n:new int[]{0,128,129,130,131}) { s.store(n,B);
            s.eq(A,B,control); } s.jump("media.error");
        s.label(control);
        s.get(A,MUSIC_VOICE);
        s.get(B,MUSIC_NOTE);
        s.get(C,MUSIC_CONTROL);
        s.emit(OUTPUT_AUDIO,A,B,C);
        s.label("music.wait");
        s.get(A,MUSIC_FRAMES);
        s.store(0,B);
        s.eq(A,B,"music.event");
        s.emit(DISPLAY);
        s.emit(READ_INPUT,0,D);
        s.store(27,A);
        s.eq(D,A,"music.done");
        s.get(A,MUSIC_FRAMES);
        s.store(1,B);
        s.emit(SUB,A,B,A);
        s.put(A,MUSIC_FRAMES);
        s.jump("music.wait");
        s.label("music.done");
        s.silence();
        s.text("PLAYBACK FINISHED\n");
        s.jump("shell");
        s.label("media.error");
        s.silence();
        s.text("INVALID MEDIA FILE\nUSE THE HTML DISK TOOL\n");
        s.jump("shell");
        s.label("media.wait");
        s.emit(DISPLAY);
        s.emit(READ_INPUT,0,D); for(int key:new int[]{27,10,81}) { s.store(key,A);
            s.eq(D,A,"media.close"); } s.jump("media.wait");
        s.label("media.close");
        s.emit(CLEAR_GRA);
        s.set(X,4);
        s.set(Y,4);
        s.jump("shell");

        s.label("do.MORE");
        s.label("do.VIEW");
        s.set16(MEDIA_POS,MEDIA_POS_H,0);
        s.label("document.page");
        s.emit(CLEAR_GRA);
        s.set(X,4);
        s.set(Y,4);
        s.set(INK,28);
        s.text("DOCUMENT - UP DOWN ESC\n");
        s.set(MEDIA_LINES,0);
        s.copy16(MEDIA_POS,MEDIA_POS_H,INDEX,INDEX_H);
        s.label("document.char");
        s.eq16(INDEX,INDEX_H,LENGTH,LENGTH_H,"document.end");
        s.bufferRead(INDEX,INDEX_H,D);
        s.put(D,CH);
        s.call("print",PRINT_RETURN);
        s.inc16(INDEX,INDEX_H);
        s.get(A,X);
        s.store(4,B);
        s.ne(A,B,"document.char");
        s.get(A,MEDIA_LINES);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,MEDIA_LINES);
        s.store(24,B);
        s.ne(A,B,"document.char");
        s.label("document.end");
        s.copy16(INDEX,INDEX_H,MEDIA_NEXT,MEDIA_NEXT_H);
        s.label("document.wait");
        s.emit(DISPLAY);
        s.emit(READ_INPUT,0,D);
        s.store(27,A);
        s.eq(D,A,"media.close");
        for(int key:new int[]{40,34,32,10}) { s.store(key,A);
            s.eq(D,A,"document.down"); }
        for(int key:new int[]{38,33}) { s.store(key,A);
            s.eq(D,A,"document.up"); } s.store(36,A);
        s.eq(D,A,"do.VIEW");
        s.jump("document.wait");
        s.label("document.down");
        s.eq16(MEDIA_NEXT,MEDIA_NEXT_H,LENGTH,LENGTH_H,"document.wait");
        s.copy16(MEDIA_NEXT,MEDIA_NEXT_H,MEDIA_POS,MEDIA_POS_H);
        s.jump("document.page");
        s.label("document.up");
        s.eq16Value(MEDIA_POS,MEDIA_POS_H,0,"document.wait");
        s.set16(INDEX,INDEX_H,0);
        s.set16(TARGET,TARGET_H,0);
        s.set(MEDIA_LINES,0);
        s.set(COLUMN,0);
        s.label("document.previous.scan");
        s.bufferRead(INDEX,INDEX_H,D);
        s.inc16(INDEX,INDEX_H);
        s.store(10,A);
        s.eq(D,A,"document.previous.line");
        s.get(A,COLUMN);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,COLUMN);
        s.store(62,B);
        s.ne(A,B,"document.previous.scan");
        s.label("document.previous.line");
        s.set(COLUMN,0);
        s.get(A,MEDIA_LINES);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,MEDIA_LINES);
        s.store(24,B);
        s.ne(A,B,"document.previous.scan");
        s.eq16(INDEX,INDEX_H,MEDIA_POS,MEDIA_POS_H,"document.previous.found");
        s.copy16(INDEX,INDEX_H,TARGET,TARGET_H);
        s.set(MEDIA_LINES,0);
        s.jump("document.previous.scan");
        s.label("document.previous.found");
        s.copy16(TARGET,TARGET_H,MEDIA_POS,MEDIA_POS_H);
        s.jump("document.page");
    }

    private static void emitFontRenderer(Asm s) {
        s.label("highlight");
        s.get(A,MODE);
        s.store(1,B);
        s.ne(A,B,"highlight.normal");
        s.get(D,CH);
        s.store(10,A);
        s.eq(D,A,"highlight.newline");
        s.store(59,A);
        s.eq(D,A,"highlight.comment");
        s.get(A,HILITE);
        s.store(3,B);
        s.eq(A,B,"highlight.comment");
        s.store(32,A);
        s.eq(D,A,"highlight.space");
        s.get(A,HILITE);
        s.store(0,B); String phase=s.unique("highlight.phase");
        s.ne(A,B,phase);
        s.set(HILITE,1);
        s.label(phase);
        s.get(A,HILITE);
        s.store(1,B);
        s.eq(A,B,"highlight.keyword");
        s.get(D,CH); for(int c='0';c<='9';c++) { s.store(c,A);
            s.eq(D,A,"highlight.number"); }
        s.jump("highlight.normal");
        s.label("highlight.space");
        s.get(A,HILITE);
        s.store(1,B);
        s.ne(A,B,"highlight.normal");
        s.set(HILITE,2);
        s.jump("highlight.normal");
        s.label("highlight.newline");
        s.set(HILITE,0);
        s.jump("highlight.normal");
        s.label("highlight.comment");
        s.set(HILITE,3);
        s.set(INK,82);
        s.ret(HILITE_RETURN);
        s.label("highlight.keyword");
        s.set(INK,31);
        s.ret(HILITE_RETURN);
        s.label("highlight.number");
        s.set(INK,124);
        s.ret(HILITE_RETURN);
        s.label("highlight.normal");
        s.set(INK,28);
        s.ret(HILITE_RETURN);
        s.label("glyph");
        // DOS and external clients share the same register-only rasterizer.
        s.get(K,X);s.get(L,Y);s.get(Registers.M.address(),INK);s.store(1,E);
        s.store(0,Registers.N.address());s.ref("glyph.return",true,Registers.O.address());s.ref("glyph.return",false,Registers.P.address());
        s.jump("graphics.glyph");s.label("glyph.return");s.ret(GLYPH_RETURN);
        emitSharedGlyph(s);

    }

    /** Graphics ABI v1 at bank zero 249:18. K/L=x/y, M=ink, D=character,
     * E=0 for transparent 3x5, nonzero for opaque 4x6. N/O/P=return bank/page/offset.
     * Clobbers A-M; preserves N-P and all RAM, storage and display pacing state.
     * Register-only scratch permits use while the interpreted DOS is mid-glyph.
     */
    private static void emitSharedGlyph(Asm s) {
        final int M=16,N=17,O=18,P=19;
        s.label("graphics.glyph");
        s.store(32,A);s.emit(COMPARE_UNSIGNED,D,A,C);s.store(1,B);s.eq(C,B,"graphics.fallback");
        s.store(126,A);s.emit(COMPARE_UNSIGNED,D,A,C);s.store(2,B);s.ne(C,B,"graphics.lookup");
        s.label("graphics.fallback");s.store('?',D);
        s.label("graphics.lookup");s.store(32,A);s.emit(SUB,D,A,C);s.store(2,B);s.emit(MULTIPLY,C,B,C);s.store(64,A);s.emit(ADD,C,A,C);
        s.store(0,A);s.store(246,B);s.emit(LOAD_MEM_ON_BANK,A,B,C,I);s.emit(INCREMENT_REGISTER,C);s.emit(LOAD_MEM_ON_BANK,A,B,C,J);s.store(0,F);
        s.label("graphics.row");s.store(0,A);s.emit(LOAD_MEM_ON_BANK,A,I,J,D);
        for(int col=0;col<4;col++) {
            String blank=s.unique("graphics.blank"),draw=s.unique("graphics.draw"),next=s.unique("graphics.next");
            s.store(128>>col,B);s.emit(COMPARE_UNSIGNED,D,B,C);s.store(1,A);s.eq(C,A,blank);
            s.emit(SUB,D,B,D);s.store(0,A);s.emit(ADD,M,A,C);s.jump(draw);
            s.label(blank);s.store(0,A);s.eq(E,A,next);s.store(0,C);
            s.label(draw);s.store(col,A);s.emit(ADD,K,A,A);s.store(0,B);s.emit(ADD,L,B,B);s.emit(PUSH_GRA_MEM,A,B,C);s.label(next);
        }
        s.emit(INCREMENT_REGISTER,J);s.emit(INCREMENT_REGISTER,L);s.emit(INCREMENT_REGISTER,F);
        s.store(6,A);s.eq(F,A,"graphics.done");s.store(5,A);s.ne(F,A,"graphics.row");s.store(0,A);s.ne(E,A,"graphics.row");
        s.label("graphics.done");s.emit(CHANGE_BANK_PAGE_JUMP,N,O,P);
    }

    /** The text language uses the existing shell dispatcher and bytecode arithmetic instructions. */
    private static void emitSourceLanguage(Asm s) {
        s.label("cmd.CODEHELP");
        s.text("EDIT DEMO.JVS THEN RUN DEMO.JVS\nPRINT TEXT  BEEP  CLEAR\nSTORE 42 A  OUTPUT A\nADD A B C  SUB A B C\nMULTIPLY A B C  INC A\nEND OR STOP RETURNS TO DOS\nREM TEXT OR ; COMMENT\n");
        s.text("ONE STATEMENT PER LINE\nREGISTERS A TO P WRAP AT 256\nGOTO LINE  IF A VALUE LINE\nLINE NUMBERS START AT 1\nESC ABORTS A RUNNING PROGRAM\nARROWS EDIT  ESC SAVES\nSOUND ON OR SOUND OFF\n");
        s.jump("shell");
        s.label("cmd.BEEP");
        s.tone();
        s.jump("shell");
        s.label("cmd.SOUND");
        s.get(A,ARG);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.store('O',A);
        s.ne(D,A,"script.error");
        s.emit(INCREMENT_REGISTER,C);
        s.readAt(LINE,C,D);
        s.store('N',A);
        s.eq(D,A,"sound.on");
        s.store('F',A);
        s.ne(D,A,"script.error");
        s.set(KEY_SOUND,0);
        s.text("KEY SOUND OFF\n");
        s.jump("shell");
        s.label("sound.on");
        s.set(KEY_SOUND,1);
        s.text("KEY SOUND ON\n");
        s.jump("shell");
        s.label("cmd.REM");
        s.jump("shell");
        s.label("cmd.END");
        s.label("cmd.STOP");
        s.set(SCRIPT_ACTIVE,0);
        s.jump("shell");
        s.label("script.start");
        s.get(A,SLOT);
        s.put(A,SCRIPT_SLOT);
        s.copy16(LENGTH,LENGTH_H,SCRIPT_SIZE,SCRIPT_SIZE_H);
        s.set16(SCRIPT_INDEX,SCRIPT_INDEX_H,0);
        s.set(SCRIPT_ACTIVE,1);
        s.set16(RUN_LINE,RUN_LINE_H,0);
        s.set(MODE,0);
        s.set(INK,28);
        for(int register=0;register<SOURCE_REGISTER_COUNT;register++) s.set(SOURCE_REGISTER_BASE-64+register,0);
        s.jump("script.next");
        s.label("script.next");
        s.emit(READ_INPUT,0,D);
        s.store(27,A);
        s.eq(D,A,"script.finish");
        s.inc16(RUN_LINE,RUN_LINE_H);
        s.set(COUNT,0);
        s.get(A,SCRIPT_SLOT);
        s.put(A,SLOT);
        s.label("script.read");
        s.eq16(SCRIPT_INDEX,SCRIPT_INDEX_H,SCRIPT_SIZE,SCRIPT_SIZE_H,"script.last");
        s.bufferRead(SCRIPT_INDEX,SCRIPT_INDEX_H,D);
        s.put(D,CH);
        s.inc16(SCRIPT_INDEX,SCRIPT_INDEX_H);
        s.get(D,CH);
        s.store(10,A);
        s.eq(D,A,"script.line");
        s.store(13,A);
        s.eq(D,A,"script.read");
        s.get(A,COUNT);
        s.store(100,B);
        s.eq(A,B,"script.error");
        String keep=s.unique("script.keep");
        s.store(0,B);
        s.ne(A,B,keep);
        s.store(32,B);
        s.eq(D,B,"script.read");
        s.store(59,B);
        s.eq(D,B,"script.comment");
        s.label(keep);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.store(LINE,E);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,COUNT);
        s.jump("script.read");
        s.label("script.comment");
        s.eq16(SCRIPT_INDEX,SCRIPT_INDEX_H,SCRIPT_SIZE,SCRIPT_SIZE_H,"script.finish");
        s.bufferRead(SCRIPT_INDEX,SCRIPT_INDEX_H,D);
        s.inc16(SCRIPT_INDEX,SCRIPT_INDEX_H);
        s.store(10,A);
        s.ne(D,A,"script.comment");
        s.jump("script.next");
        s.label("script.last");
        s.get(A,COUNT);
        s.store(0,B);
        s.eq(A,B,"script.finish");
        s.jump("enter.command");
        s.label("script.line");
        s.get(A,COUNT);
        s.store(0,B);
        s.eq(A,B,"script.next");
        s.jump("enter.command");
        s.label("script.finish");
        s.set(SCRIPT_ACTIVE,0);
        s.jump("shell");
        s.label("script.error");
        s.get(A,SCRIPT_ACTIVE);
        s.store(1,B); String cliError=s.unique("source.cli.error");
        s.ne(A,B,cliError);
        s.set(SCRIPT_ACTIVE,0);
        s.text("SOURCE ERROR AT LINE ");
        s.get(D,RUN_LINE);
        s.put(D,TEMP);
        s.get(D,RUN_LINE_H);
        s.put(D,NUM_H);
        s.call("number16",NUMBER16_RETURN);
        s.text("\n");
        s.label(cliError);
        s.text("CHECK SYNTAX WITH CODEHELP\n");
        s.jump("shell");

        s.label("cmd.STORE");
        s.call("source.number",PARSE_RETURN);
        s.get(A,NUMBER_VALUE);
        s.put(A,USER_LEFT);
        s.call("source.register",PARSE_RETURN);
        s.call("source.end",PARSE_RETURN);
        s.get(D,USER_LEFT);
        s.call("source.write",PARSE_RETURN);
        s.jump("shell");
        for(String command:new String[]{"ADD","SUB","MULTIPLY"}) {
            s.label("cmd."+command);
            s.call("source.register",PARSE_RETURN);
            s.call("source.read",PARSE_RETURN);
            s.put(D,USER_LEFT);
            s.call("source.register",PARSE_RETURN);
            s.call("source.read",PARSE_RETURN);
            s.put(D,USER_RIGHT);
            s.call("source.register",PARSE_RETURN);
            s.call("source.end",PARSE_RETURN);
            s.get(A,USER_LEFT);
            s.get(B,USER_RIGHT);
            s.emit(command.equals("ADD") ? ADD : command.equals("SUB") ? SUB : MULTIPLY,A,B,D);
            s.call("source.write",PARSE_RETURN);
            s.jump("shell");
        }
        s.label("cmd.INC");
        s.call("source.register",PARSE_RETURN);
        s.call("source.end",PARSE_RETURN);
        s.call("source.read",PARSE_RETURN);
        s.emit(INCREMENT_REGISTER,D);
        s.call("source.write",PARSE_RETURN);
        s.jump("shell");
        s.label("cmd.OUTPUT");
        s.call("source.register",PARSE_RETURN);
        s.call("source.end",PARSE_RETURN);
        s.call("source.read",PARSE_RETURN);
        s.put(D,TEMP);
        s.call("number",NUMBER_RETURN);
        s.text("\n");
        s.jump("shell");
        s.label("cmd.GOTO");
        s.get(A,SCRIPT_ACTIVE);
        s.store(1,B);
        s.ne(A,B,"script.error");
        s.call("source.number",PARSE_RETURN);
        s.call("source.end",PARSE_RETURN);
        s.jump("source.goto");
        s.label("cmd.IF");
        s.get(A,SCRIPT_ACTIVE);
        s.store(1,B);
        s.ne(A,B,"script.error");
        s.call("source.register",PARSE_RETURN);
        s.call("source.read",PARSE_RETURN);
        s.put(D,USER_LEFT);
        s.call("source.number",PARSE_RETURN);
        s.get(A,NUMBER_VALUE);
        s.put(A,USER_RIGHT);
        s.call("source.number",PARSE_RETURN);
        s.call("source.end",PARSE_RETURN);
        s.get(A,USER_LEFT);
        s.get(B,USER_RIGHT);
        s.ne(A,B,"shell");
        s.label("source.goto");
        s.get(A,NUMBER_VALUE);
        s.store(0,B);
        s.eq(A,B,"script.error");
        s.store(1,B);
        s.emit(SUB,A,B,A);
        s.put(A,RUN_LINE);
        s.put(A,NUMBER_VALUE);
        s.set16(SCRIPT_INDEX,SCRIPT_INDEX_H,0);
        s.set(RUN_LINE_H,0);
        s.get(A,SCRIPT_SLOT);
        s.put(A,SLOT);
        s.label("source.goto.seek");
        s.get(A,NUMBER_VALUE);
        s.store(0,B);
        s.eq(A,B,"script.next");
        s.eq16(SCRIPT_INDEX,SCRIPT_INDEX_H,SCRIPT_SIZE,SCRIPT_SIZE_H,"script.error");
        s.bufferRead(SCRIPT_INDEX,SCRIPT_INDEX_H,D);
        s.inc16(SCRIPT_INDEX,SCRIPT_INDEX_H);
        s.store(10,A);
        s.ne(D,A,"source.goto.seek");
        s.get(A,NUMBER_VALUE);
        s.store(1,B);
        s.emit(SUB,A,B,A);
        s.put(A,NUMBER_VALUE);
        s.jump("source.goto.seek");
        s.label("source.register");
        s.get(A,ARG);
        s.get(B,COUNT);
        s.eq(A,B,"script.error");
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.store(32,A);
        s.eq(D,A,"source.register.space");
        // Validate the letter range once, then convert A-P to a guest-variable index.
        s.store('A',A);
        s.emit(COMPARE_UNSIGNED,D,A,B);
        s.store(1,C);
        s.eq(B,C,"script.error");
        s.store('P',A);
        s.emit(COMPARE_UNSIGNED,D,A,B);
        s.store(2,C);
        s.eq(B,C,"script.error");
        s.store('A',A);
        s.emit(SUB,D,A,D);
        s.put(D,USER_REG);
        s.get(A,ARG);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,ARG);
        s.label("source.register.done");
        s.get(A,ARG);
        s.get(B,COUNT); String registerDone=s.unique("register.done");
        s.eq(A,B,registerDone);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.store(32,A);
        s.eq(D,A,registerDone);
        s.store(59,A);
        s.ne(D,A,"script.error");
        s.label(registerDone);
        s.ret(PARSE_RETURN);
        s.label("source.register.space");
        s.get(A,ARG);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,ARG);
        s.jump("source.register");
        s.label("source.read");
        s.get(A,USER_REG);
        s.store(SOURCE_REGISTER_BASE,B);
        s.emit(ADD,A,B,C);
        s.readAt(STATE,C,D);
        s.ret(PARSE_RETURN);
        s.label("source.write");
        s.get(A,USER_REG);
        s.store(SOURCE_REGISTER_BASE,B);
        s.emit(ADD,A,B,C);
        s.store(STATE,E);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.ret(PARSE_RETURN);
        s.label("source.end");
        s.get(A,ARG);
        s.get(B,COUNT); String end=s.unique("source.end");
        s.eq(A,B,end);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.store(59,A);
        s.eq(D,A,end);
        s.store(32,A);
        s.ne(D,A,"script.error");
        s.get(A,ARG);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,ARG);
        s.jump("source.end");
        s.label(end);
        s.ret(PARSE_RETURN);
        s.label("source.number");
        s.set(NUMBER_VALUE,0);
        s.set(NUMBER_DIGIT,0);
        s.label("source.number.loop");
        s.get(A,ARG);
        s.get(B,COUNT);
        s.eq(A,B,"source.number.end");
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.store(32,A);
        s.eq(D,A,"source.number.space");
        for(int digit=0;digit<10;digit++) { s.store('0'+digit,A);
            s.eq(D,A,"source.digit."+digit); }
        s.jump("script.error");
        for(int digit=0;digit<10;digit++) { s.label("source.digit."+digit);
            s.set(REMAINING,digit);
            s.jump("source.number.add"); }
        s.label("source.number.add");
        s.get(A,NUMBER_VALUE);
        s.store(10,B);
        s.emit(MULTIPLY,A,B,A);
        s.get(B,REMAINING);
        s.emit(ADD,A,B,A);
        s.put(A,NUMBER_VALUE);
        s.set(NUMBER_DIGIT,1);
        s.get(A,ARG);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,ARG);
        s.jump("source.number.loop");
        s.label("source.number.space");
        s.get(A,ARG);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,ARG);
        s.get(A,NUMBER_DIGIT);
        s.store(0,B);
        s.eq(A,B,"source.number.loop");
        s.label("source.number.end");
        s.get(A,NUMBER_DIGIT);
        s.store(0,B);
        s.eq(A,B,"script.error");
        s.ret(PARSE_RETURN);
    }

    /** The line editor and file editor share 16-bit cursor navigation. */
    private static void emitEditor(Asm s) {
        s.label("line.insert");
        s.label("edit.insert");
        s.editSize();
        s.get(D,MODE);
        s.store(1,B); String editor=s.unique("insert.editor"), room=s.unique("insert.room");
        s.eq(D,B,editor);
        s.eq16Value(EDIT_SIZE,EDIT_SIZE_H,100,"poll");
        s.jump(room);
        s.label(editor);
        s.eq16Value(EDIT_SIZE,EDIT_SIZE_H,61200,"poll");
        s.label(room);
        s.copy16(EDIT_SIZE,EDIT_SIZE_H,INDEX,INDEX_H);
        s.eq16(INDEX,INDEX_H,CURSOR,CURSOR_H,"insert.append");
        s.label("insert.shift");
        s.eq16(INDEX,INDEX_H,CURSOR,CURSOR_H,"insert.character");
        s.dec16(INDEX,INDEX_H);
        s.call("read.character",READ_RETURN);
        s.inc16(INDEX,INDEX_H);
        s.call("write.character",READ_RETURN);
        s.dec16(INDEX,INDEX_H);
        s.jump("insert.shift");
        s.label("insert.character");
        s.get(D,CH);
        s.call("write.character",READ_RETURN);
        s.editIncrement(true);
        s.inc16(CURSOR,CURSOR_H);
        s.jump("redraw");
        s.label("insert.append");
        s.get(D,CH);
        s.call("write.character",READ_RETURN);
        s.editIncrement(true);
        s.inc16(CURSOR,CURSOR_H);
        s.call("highlight",HILITE_RETURN);
        s.call("print",PRINT_RETURN);
        s.set(INK,28);
        s.jump("poll");
        s.label("line.backspace");
        s.eq16Value(CURSOR,CURSOR_H,0,"poll");
        s.dec16(CURSOR,CURSOR_H);
        s.jump("delete.character");
        s.label("move.127");
        s.label("delete.character");
        s.editSize();
        s.eq16(CURSOR,CURSOR_H,EDIT_SIZE,EDIT_SIZE_H,"poll");
        s.copy16(CURSOR,CURSOR_H,INDEX,INDEX_H);
        s.label("delete.shift");
        s.inc16(INDEX,INDEX_H);
        s.eq16(INDEX,INDEX_H,EDIT_SIZE,EDIT_SIZE_H,"delete.end");
        s.call("read.character",READ_RETURN);
        s.dec16(INDEX,INDEX_H);
        s.call("write.character",READ_RETURN);
        s.inc16(INDEX,INDEX_H);
        s.jump("delete.shift");
        s.label("delete.end");
        s.editIncrement(false);
        s.jump("redraw");
        s.label("move.37");
        s.eq16Value(CURSOR,CURSOR_H,0,"poll");
        s.dec16(CURSOR,CURSOR_H);
        s.jump("redraw");
        s.label("move.39");
        s.editSize();
        s.eq16(CURSOR,CURSOR_H,EDIT_SIZE,EDIT_SIZE_H,"poll");
        s.inc16(CURSOR,CURSOR_H);
        s.jump("redraw");
        s.label("move.36");
        s.set16(CURSOR,CURSOR_H,0);
        s.jump("redraw");
        s.label("move.35");
        s.editSize();
        s.copy16(EDIT_SIZE,EDIT_SIZE_H,CURSOR,CURSOR_H);
        s.jump("redraw");
        s.label("move.38");
        s.get(A,MODE);
        s.store(0,B);
        s.eq(A,B,"history.up");
        s.set(TEMP,0);
        s.jump("move.scan");
        s.label("move.40");
        s.get(A,MODE);
        s.store(0,B);
        s.eq(A,B,"history.down");
        s.set(TEMP,1);
        s.label("move.scan");
        s.editSize();
        s.set16(INDEX,INDEX_H,0);
        s.set(COLUMN,0);
        s.set16(LINE_START,LINE_START_H,0);
        s.label("move.prefix");
        s.eq16(INDEX,INDEX_H,CURSOR,CURSOR_H,"move.direction");
        s.call("read.character",READ_RETURN);
        s.store(10,A);
        s.eq(D,A,"move.prefix.break");
        s.get(A,COLUMN);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,COLUMN);
        s.store(62,B);
        s.ne(A,B,"move.prefix.next");
        s.label("move.prefix.break");
        s.set(COLUMN,0);
        s.copy16(INDEX,INDEX_H,LINE_START,LINE_START_H);
        s.inc16(LINE_START,LINE_START_H);
        s.label("move.prefix.next");
        s.inc16(INDEX,INDEX_H);
        s.jump("move.prefix");
        s.label("move.direction");
        s.get(A,TEMP);
        s.store(1,B);
        s.eq(A,B,"move.down");
        s.eq16Value(LINE_START,LINE_START_H,0,"poll");
        s.set16(INDEX,INDEX_H,0);
        s.set16(TARGET,TARGET_H,0);
        s.set(EDIT_INDEX,0);
        s.label("move.previous");
        s.call("read.character",READ_RETURN);
        s.store(10,A);
        s.eq(D,A,"move.previous.break");
        s.get(A,EDIT_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,EDIT_INDEX);
        s.store(62,B);
        s.ne(A,B,"move.previous.next");
        s.label("move.previous.break");
        s.inc16(INDEX,INDEX_H);
        s.eq16(INDEX,INDEX_H,LINE_START,LINE_START_H,"move.target");
        s.copy16(INDEX,INDEX_H,TARGET,TARGET_H);
        s.set(EDIT_INDEX,0);
        s.jump("move.previous");
        s.label("move.previous.next");
        s.inc16(INDEX,INDEX_H);
        s.jump("move.previous");
        s.label("move.down");
        s.copy16(CURSOR,CURSOR_H,INDEX,INDEX_H);
        s.get(A,COLUMN);
        s.put(A,EDIT_INDEX);
        s.label("move.next");
        s.eq16(INDEX,INDEX_H,EDIT_SIZE,EDIT_SIZE_H,"poll");
        s.call("read.character",READ_RETURN);
        s.store(10,A);
        s.eq(D,A,"move.next.break");
        s.get(A,EDIT_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,EDIT_INDEX);
        s.store(62,B);
        s.ne(A,B,"move.next.advance");
        s.label("move.next.break");
        s.copy16(INDEX,INDEX_H,TARGET,TARGET_H);
        s.inc16(TARGET,TARGET_H);
        s.jump("move.target");
        s.label("move.next.advance");
        s.inc16(INDEX,INDEX_H);
        s.jump("move.next");
        s.label("move.target");
        s.copy16(TARGET,TARGET_H,INDEX,INDEX_H);
        s.set(EDIT_INDEX,0);
        s.label("move.column");
        s.get(A,EDIT_INDEX);
        s.get(B,COLUMN);
        s.eq(A,B,"move.finish");
        s.eq16(INDEX,INDEX_H,EDIT_SIZE,EDIT_SIZE_H,"move.finish");
        s.call("read.character",READ_RETURN);
        s.store(10,A);
        s.eq(D,A,"move.finish");
        s.inc16(INDEX,INDEX_H);
        s.get(A,EDIT_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,EDIT_INDEX);
        s.jump("move.column");
        s.label("move.finish");
        s.copy16(INDEX,INDEX_H,CURSOR,CURSOR_H);
        s.jump("redraw");

        s.label("read.character");
        s.get(A,MODE);
        s.store(1,B);
        s.eq(A,B,"read.editor");
        s.get(A,INDEX);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.ret(READ_RETURN);
        s.label("read.editor");
        s.bufferRead(INDEX,INDEX_H,D);
        s.ret(READ_RETURN);
        s.label("write.character");
        s.get(A,MODE);
        s.store(1,B);
        s.eq(A,B,"write.editor");
        s.get(A,INDEX);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.store(LINE,E);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.ret(READ_RETURN);
        s.label("write.editor");
        s.bufferWrite(INDEX,INDEX_H,D);
        s.ret(READ_RETURN);

        s.label("redraw");
        s.get(A,MODE);
        s.store(1,B);
        s.eq(A,B,"edit.redraw");
        s.get(A,BASE_X);
        s.put(A,X);
        s.get(A,BASE_Y);
        s.put(A,Y);
        s.jump("redraw.begin");
        s.label("edit.redraw");
        s.emit(CLEAR_GRA);
        s.set(INK,28);
        s.set(X,4);
        s.set(Y,4);
        s.text("EDIT ");
        s.set(INDEX,0);
        s.label("editor.title");
        s.get(A,INDEX);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(NAME,C,D);
        s.store(0,A);
        s.eq(D,A,"editor.title.end");
        s.put(D,CH);
        s.call("print",PRINT_RETURN);
        s.get(A,INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,INDEX);
        s.store(12,B);
        s.ne(A,B,"editor.title");
        s.label("editor.title.end");
        s.text("\nARROWS MOVE  TAB INDENT\nESC SAVE  CODEHELP IN DOS\n");
        s.label("redraw.begin");
        s.editSize();
        s.set(RENDERING,1);
        s.set(HILITE,0);
        s.set16(INDEX,INDEX_H,0);
        s.set(TARGET,0);
        s.label("redraw.loop"); String afterCursor=s.unique("after.cursor");
        s.eq16(INDEX,INDEX_H,CURSOR,CURSOR_H,"redraw.cursor");
        s.jump(afterCursor);
        s.label("redraw.cursor");
        s.get(A,X);
        s.put(A,CURSOR_X);
        s.get(A,Y);
        s.put(A,CURSOR_Y);
        s.set(TARGET,1);
        s.label(afterCursor);
        s.eq16(INDEX,INDEX_H,EDIT_SIZE,EDIT_SIZE_H,"redraw.end");
        s.get(A,TARGET);
        s.store(1,B); String render=s.unique("render");
        s.ne(A,B,render);
        s.get(A,Y);
        s.store(236,B);
        s.eq(A,B,"redraw.restore");
        s.label(render);
        s.call("read.character",READ_RETURN);
        s.put(D,CH);
        s.call("highlight",HILITE_RETURN);
        s.call("print",PRINT_RETURN);
        s.inc16(INDEX,INDEX_H);
        s.jump("redraw.loop");
        s.label("redraw.end");
        s.set(CH,32);
        s.call("print",PRINT_RETURN);
        s.label("redraw.restore");
        s.set(RENDERING,0);
        s.set(INK,28);
        s.get(A,CURSOR_X);
        s.put(A,X);
        s.get(A,CURSOR_Y);
        s.put(A,Y);
        s.get(A,ENTER_PENDING);
        s.store(1,B);
        s.eq(A,B,"enter.command");
        s.jump("poll");
    }

    /** Interactive shell input, executed entirely by guest instructions. */
    private static void emitShellNavigation(Asm s) {
        s.label("history.address");
        s.get(A,H_SLOT);
        s.store(128,B);
        s.emit(MULTIPLY,A,B,A);
        s.put(A,H_OFFSET);
        s.get(A,H_SLOT);
        s.put(A,H_INDEX);
        s.set(H_PAGE,240);
        s.label("history.address.divide");
        s.get(A,H_INDEX);
        s.store(2,B);
        s.emit(COMPARE_UNSIGNED,A,B,C);
        s.store(1,D);
        s.eq(C,D,"history.address.done");
        s.emit(SUB,A,B,A);
        s.put(A,H_INDEX);
        s.get(A,H_PAGE);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,H_PAGE);
        s.jump("history.address.divide");
        s.label("history.address.done");
        s.ret(C_RETURN);
        s.label("history.save");
        s.get(A,SCRIPT_ACTIVE);
        s.store(1,B);
        s.eq(A,B,"history.save.done");
        s.get(A,COUNT);
        s.store(0,B);
        s.eq(A,B,"history.save.done");
        // Map the ring slot inline: H_RETURN belongs to this outer call.
        s.get(A,H_NEXT);
        s.put(A,H_SLOT);
        s.call("history.address",C_RETURN);
        s.get(D,COUNT);
        s.get(B,H_PAGE);
        s.get(C,H_OFFSET);
        s.store(1,A);
        s.emit(STORE_MEM_ON_BANK,D,A,B,C);
        s.set(H_INDEX,0);
        s.label("history.save.char");
        s.get(A,H_INDEX);
        s.get(B,COUNT);
        s.eq(A,B,"history.save.finish");
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.get(A,H_INDEX);
        s.get(B,H_OFFSET);
        s.emit(ADD,A,B,C);
        s.store(4,B);
        s.emit(ADD,C,B,C);
        s.get(B,H_PAGE);
        s.store(1,A);
        s.emit(STORE_MEM_ON_BANK,D,A,B,C);
        s.get(A,H_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,H_INDEX);
        s.jump("history.save.char");
        s.label("history.save.finish");
        s.get(A,H_NEXT);
        s.emit(INCREMENT_REGISTER,A);
        s.store(16,B); String ringNext=s.unique("history.next");
        s.ne(A,B,ringNext);
        s.store(0,A);
        s.label(ringNext);
        s.put(A,H_NEXT);
        s.get(A,H_COUNT);
        s.store(16,B);
        s.eq(A,B,"history.save.done");
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,H_COUNT);
        s.label("history.save.done");
        s.ret(H_RETURN);

        s.label("history.up");
        s.get(A,H_COUNT);
        s.store(0,B);
        s.eq(A,B,"poll");
        s.get(A,H_VIEW);
        s.get(B,H_COUNT);
        s.eq(A,B,"poll");
        s.store(0,B); String captured=s.unique("history.draft.captured");
        s.ne(A,B,captured);
        s.get(D,COUNT);
        s.emit(STORE_MEM_ABSOLUTE,D,1,248,0);
        s.get(D,CURSOR);
        s.emit(STORE_MEM_ABSOLUTE,D,1,248,1);
        s.set(H_INDEX,0);
        s.label("history.draft.save");
        s.get(A,H_INDEX);
        s.get(B,COUNT);
        s.eq(A,B,captured);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.get(A,H_INDEX);
        s.store(16,B);
        s.emit(ADD,A,B,C);
        s.store(1,A);
        s.store(248,B);
        s.emit(STORE_MEM_ON_BANK,D,A,B,C);
        s.get(A,H_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,H_INDEX);
        s.jump("history.draft.save");
        s.label(captured);
        s.get(A,H_VIEW);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,H_VIEW);
        s.jump("history.recall");
        s.label("history.down");
        s.get(A,H_VIEW);
        s.store(0,B);
        s.eq(A,B,"poll");
        s.store(1,B);
        s.emit(SUB,A,B,A);
        s.put(A,H_VIEW);
        s.store(0,B);
        s.eq(A,B,"history.draft.restore");
        s.label("history.recall");
        s.get(A,COUNT);
        s.put(A,H_OLD_COUNT);
        s.get(A,H_NEXT);
        s.get(B,H_VIEW);
        s.emit(SUB,A,B,A);
        s.store(16,B);
        s.emit(COMPARE_UNSIGNED,A,B,C);
        s.store(1,D); String slot=s.unique("history.slot");
        s.eq(C,D,slot);
        s.emit(ADD,A,B,A);
        s.label(slot);
        s.put(A,H_SLOT);
        s.call("history.address",C_RETURN);
        s.get(B,H_PAGE);
        s.get(C,H_OFFSET);
        s.store(1,A);
        s.emit(LOAD_MEM_ON_BANK,A,B,C,D);
        s.store(100,B);
        s.emit(COMPARE_UNSIGNED,D,B,C);
        s.store(2,A);
        s.eq(C,A,"history.invalid");
        s.put(D,COUNT);
        s.put(D,CURSOR);
        s.set(CURSOR_H,0);
        s.set(H_INDEX,0);
        s.label("history.recall.char");
        s.get(A,H_INDEX);
        s.get(B,COUNT);
        s.eq(A,B,"shell.replace");
        s.get(B,H_OFFSET);
        s.emit(ADD,A,B,C);
        s.store(4,B);
        s.emit(ADD,C,B,C);
        s.get(B,H_PAGE);
        s.store(1,A);
        s.emit(LOAD_MEM_ON_BANK,A,B,C,D);
        s.get(A,H_INDEX);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.store(LINE,E);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.get(A,H_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,H_INDEX);
        s.jump("history.recall.char");
        s.label("history.draft.restore");
        s.get(A,COUNT);
        s.put(A,H_OLD_COUNT);
        s.emit(LOAD_MEM_ABSOLUTE,1,248,0,D);
        s.store(100,B);
        s.emit(COMPARE_UNSIGNED,D,B,C);
        s.store(2,A);
        s.eq(C,A,"history.invalid");
        s.put(D,COUNT);
        s.emit(LOAD_MEM_ABSOLUTE,1,248,1,A);
        s.get(B,COUNT);
        s.emit(COMPARE_UNSIGNED,A,B,C);
        s.store(2,D);
        s.eq(C,D,"history.invalid");
        s.put(A,CURSOR);
        s.set(CURSOR_H,0);
        s.set(H_INDEX,0);
        s.label("history.draft.load");
        s.get(A,H_INDEX);
        s.get(B,COUNT);
        s.eq(A,B,"shell.replace");
        s.store(16,B);
        s.emit(ADD,A,B,C);
        s.store(1,A);
        s.store(248,B);
        s.emit(LOAD_MEM_ON_BANK,A,B,C,D);
        s.get(A,H_INDEX);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.store(LINE,E);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.get(A,H_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,H_INDEX);
        s.jump("history.draft.load");
        s.label("history.invalid");
        s.set(H_COUNT,0);
        s.set(H_NEXT,0);
        s.set(H_VIEW,0);
        s.jump("poll");

        s.label("complete.start");
        s.set(C_WRAP,0);
        s.get(A,C_ACTIVE);
        s.store(1,B);
        s.eq(A,B,"complete.scan");
        s.get(A,COUNT);
        s.put(A,C_COUNT);
        s.get(A,CURSOR);
        s.put(A,C_CURSOR);
        s.set(C_INDEX,0);
        s.set(C_NEXT,0);
        s.set(C_START,0);
        s.label("complete.snapshot");
        s.get(A,C_INDEX);
        s.get(B,C_COUNT);
        s.eq(A,B,"complete.token.start");
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.readAt(LINE,C,D);
        s.get(A,C_INDEX);
        s.store(16,B);
        s.emit(ADD,A,B,C);
        s.store(1,A);
        s.store(249,B);
        s.emit(STORE_MEM_ON_BANK,D,A,B,C);
        s.get(A,C_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,C_INDEX);
        s.jump("complete.snapshot");
        s.label("complete.token.start");
        s.set(C_INDEX,0);
        s.label("complete.token.prefix");
        s.get(A,C_INDEX);
        s.get(B,C_CURSOR);
        s.eq(A,B,"complete.token.end");
        s.store(16,B);
        s.emit(ADD,A,B,C);
        s.store(1,A);
        s.store(249,B);
        s.emit(LOAD_MEM_ON_BANK,A,B,C,D);
        s.get(A,C_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,C_INDEX);
        s.store(32,B); String notSpace=s.unique("complete.prefix.character");
        s.ne(D,B,notSpace);
        s.put(A,C_START);
        s.label(notSpace);
        s.jump("complete.token.prefix");
        s.label("complete.token.end");
        s.get(A,C_CURSOR);
        s.put(A,C_END);
        s.get(B,C_START);
        s.emit(SUB,A,B,A);
        s.put(A,C_PREFIX);
        s.label("complete.token.suffix");
        s.get(A,C_END);
        s.get(B,C_COUNT);
        s.eq(A,B,"complete.context");
        s.store(16,B);
        s.emit(ADD,A,B,C);
        s.store(1,A);
        s.store(249,B);
        s.emit(LOAD_MEM_ON_BANK,A,B,C,D);
        s.store(32,A);
        s.eq(D,A,"complete.context");
        s.get(A,C_END);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,C_END);
        s.jump("complete.token.suffix");
        s.label("complete.context");
        s.set(C_ACTIVE,1);
        s.set(C_TYPE,0);
        s.get(A,C_START);
        s.store(0,B);
        s.eq(A,B,"complete.scan");
        for(String command:new String[]{"CD","CHDIR","RMDIR","RD","TYPE","CAT","VIEW","MORE","IMAGE","SHOW","PLAY","DEL","DELETE","ERASE","WRITE","EDIT","APPEND","COPY","REN","RENAME","MOVE","RUN","SAVE"}) {
            String next=s.unique("complete.context");
            for(int i=0;i<command.length();i++){s.emit(LOAD_MEM_ABSOLUTE,1,249,16+i,D);
                s.store(command.charAt(i),A);
                s.ne(D,A,next);}
            s.emit(LOAD_MEM_ABSOLUTE,1,249,16+command.length(),D);
            s.store(32,A);
            s.ne(D,A,next);
            s.set(C_TYPE,1);
            s.jump("complete.scan");
            s.label(next);
        }
        s.set(C_ACTIVE,0);
        s.jump("poll");
        s.label("complete.scan");
        s.get(A,C_TYPE);
        s.store(1,B);
        s.eq(A,B,"complete.files");
        s.get(A,C_NEXT);
        s.store(COMMANDS.length,B);
        s.emit(COMPARE_UNSIGNED,A,B,C);
        s.store(1,D);
        s.ne(C,D,"complete.wrap");
        for(int i=0;i<COMMANDS.length;i++){s.store(i,B);
            s.eq(A,B,"complete.command."+i);}
        s.jump("complete.wrap");
        for(int i=0;i<COMMANDS.length;i++) {
            s.label("complete.command."+i);
            s.set(C_MATCH,COMMANDS[i].length());
            s.set(C_BANK,0);
            s.stringPointer(COMMANDS[i],C_PAGE,C_OFFSET);
            s.set(C_NEXT,i+1);
            s.jump("complete.test");
        }
        s.label("complete.files");
        s.get(A,C_NEXT);
        s.store(255,B);
        s.eq(A,B,"complete.wrap");
        s.get(B,C_NEXT);
        s.get(A,DISK_BANK);
        s.store(1,C);
        s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);
        s.store(0,A);
        s.eq(D,A,"complete.file.next");
        s.store(255,A);
        s.eq(D,A,"complete.file.next");
        s.parentFilter(C_NEXT,"complete.file.next");
        s.set(C_INDEX,0);
        s.label("complete.file.name");
        s.get(A,C_INDEX);
        s.store(12,B);
        s.eq(A,B,"complete.file.ready");
        s.store(1,B);
        s.emit(ADD,A,B,C);
        s.get(B,C_NEXT);
        s.get(A,DISK_BANK);
        s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);
        s.store(0,A);
        s.eq(D,A,"complete.file.ready");
        s.get(C,C_INDEX);
        s.store(1,A);
        s.store(250,B);
        s.emit(STORE_MEM_ON_BANK,D,A,B,C);
        s.get(A,C_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,C_INDEX);
        s.jump("complete.file.name");
        s.label("complete.file.ready");
        s.get(A,C_INDEX);
        s.put(A,C_MATCH);
        s.set(C_BANK,1);
        s.set(C_PAGE,250);
        s.set(C_OFFSET,0);
        s.get(A,C_NEXT);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,C_NEXT);
        s.jump("complete.test");
        s.label("complete.file.next");
        s.get(A,C_NEXT);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,C_NEXT);
        s.jump("complete.files");
        s.label("complete.wrap");
        s.get(A,C_WRAP);
        s.store(1,B);
        s.eq(A,B,"complete.none");
        s.set(C_WRAP,1);
        s.set(C_NEXT,0);
        s.jump("complete.scan");
        s.label("complete.none");
        s.set(C_ACTIVE,0);
        s.jump("poll");
        s.label("complete.test");
        s.get(A,C_PREFIX);
        s.get(B,C_MATCH);
        s.emit(COMPARE_UNSIGNED,A,B,C);
        s.store(2,D);
        s.eq(C,D,"complete.scan");
        s.set(C_INDEX,0);
        s.label("complete.prefix.match");
        s.get(A,C_INDEX);
        s.get(B,C_PREFIX);
        s.eq(A,B,"complete.accept");
        s.get(B,C_START);
        s.emit(ADD,A,B,C);
        s.store(16,B);
        s.emit(ADD,C,B,C);
        s.store(1,A);
        s.store(249,B);
        s.emit(LOAD_MEM_ON_BANK,A,B,C,D);
        s.put(D,C_CHAR);
        s.get(C,C_OFFSET);
        s.get(D,C_INDEX);
        s.emit(ADD,C,D,C);
        s.get(A,C_BANK);
        s.get(B,C_PAGE);
        s.emit(LOAD_MEM_ON_BANK,A,B,C,D);
        s.get(A,C_CHAR);
        s.ne(D,A,"complete.scan");
        s.get(A,C_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,C_INDEX);
        s.jump("complete.prefix.match");
        s.label("complete.accept");
        s.set(C_SPACE,0);
        s.get(A,C_END);
        s.get(B,C_COUNT);String suffix=s.unique("complete.has.suffix");
        s.ne(A,B,suffix);
        s.set(C_SPACE,1);
        s.label(suffix);
        s.get(A,C_END);
        s.get(B,C_START);
        s.emit(SUB,A,B,A);
        s.put(A,C_CHAR);
        s.get(A,C_COUNT);
        s.get(B,C_CHAR);
        s.emit(SUB,A,B,A);
        s.get(B,C_MATCH);
        s.emit(ADD,A,B,A);
        s.get(B,C_SPACE);
        s.emit(ADD,A,B,A);
        s.put(A,C_TOTAL);
        s.store(100,B);
        s.emit(COMPARE_UNSIGNED,A,B,C);
        s.store(2,D);
        s.eq(C,D,"complete.scan");
        s.get(A,COUNT);
        s.put(A,H_OLD_COUNT);
        s.set(C_OUT,0);
        s.set(C_INDEX,0);
        s.label("complete.copy.prefix");
        s.get(A,C_INDEX);
        s.get(B,C_START);
        s.eq(A,B,"complete.copy.candidate.start");
        s.store(16,B);
        s.emit(ADD,A,B,C);
        s.store(1,A);
        s.store(249,B);
        s.emit(LOAD_MEM_ON_BANK,A,B,C,D);
        s.call("complete.write",C_RETURN);
        s.get(A,C_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,C_INDEX);
        s.jump("complete.copy.prefix");
        s.label("complete.copy.candidate.start");
        s.set(C_INDEX,0);
        s.label("complete.copy.candidate");
        s.get(A,C_INDEX);
        s.get(B,C_MATCH);
        s.eq(A,B,"complete.copy.space");
        s.get(C,C_OFFSET);
        s.get(D,C_INDEX);
        s.emit(ADD,C,D,C);
        s.get(A,C_BANK);
        s.get(B,C_PAGE);
        s.emit(LOAD_MEM_ON_BANK,A,B,C,D);
        s.call("complete.write",C_RETURN);
        s.get(A,C_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,C_INDEX);
        s.jump("complete.copy.candidate");
        s.label("complete.copy.space");
        s.get(A,C_SPACE);
        s.store(0,B);String noSpace=s.unique("complete.no.space");
        s.eq(A,B,noSpace);
        s.store(32,D);
        s.call("complete.write",C_RETURN);
        s.label(noSpace);
        s.get(A,C_OUT);
        s.put(A,C_NEW_CURSOR);
        s.get(A,C_END);
        s.put(A,C_INDEX);
        s.label("complete.copy.suffix");
        s.get(A,C_INDEX);
        s.get(B,C_COUNT);
        s.eq(A,B,"complete.done");
        s.store(16,B);
        s.emit(ADD,A,B,C);
        s.store(1,A);
        s.store(249,B);
        s.emit(LOAD_MEM_ON_BANK,A,B,C,D);
        s.call("complete.write",C_RETURN);
        s.get(A,C_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,C_INDEX);
        s.jump("complete.copy.suffix");
        s.label("complete.done");
        s.get(A,C_TOTAL);
        s.put(A,COUNT);
        s.get(A,C_NEW_CURSOR);
        s.put(A,CURSOR);
        s.set(CURSOR_H,0);
        s.jump("shell.replace");
        s.label("complete.write");
        s.get(A,C_OUT);
        s.store(64,B);
        s.emit(ADD,A,B,C);
        s.store(LINE,E);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.get(A,C_OUT);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,C_OUT);
        s.ret(C_RETURN);

        // Erase the old line before redrawing a shorter replacement, including wrapped rows.
        s.label("shell.replace");
        s.get(A,BASE_X);
        s.put(A,X);
        s.get(A,BASE_Y);
        s.put(A,Y);
        s.set(H_INDEX,0);
        s.label("shell.replace.erase");
        s.set(CH,32);
        s.call("print",PRINT_RETURN);
        s.get(A,H_INDEX);
        s.get(B,H_OLD_COUNT);
        s.eq(A,B,"redraw");
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,H_INDEX);
        s.jump("shell.replace.erase");
    }

    private static boolean printable(int c) { return c>=32&&c<=126; }
    private static byte[] helloProgram() {
        byte[] result=new byte[117]; String text="HELLO\n";
        for(int i=0;i<6;i++) {
            int n=i*18; int[] code={2,text.charAt(i),D,2,TPA,A,2,n+18,B,2,249,G,2,0,H,10,G,H};
            for(int j=0;j<18;j++) result[n+j]=(byte)code[j];
        }
        int[] tail={2,249,G,2,9,H,10,G,H}; for(int j=0;j<9;j++) result[108+j]=(byte)tail[j]; return result;
    }
    private static String font(char c) {
        return switch(c) {
            case 'A'->"010101111101101"; case 'B'->"110101110101110"; case 'C'->"111100100100111";
            case 'D'->"110101101101110"; case 'E'->"111100110100111"; case 'F'->"111100110100100";
            case 'G'->"111100101101111"; case 'H'->"101101111101101"; case 'I'->"111010010010111";
            case 'J'->"001001001101010"; case 'K'->"101101110101101"; case 'L'->"100100100100111";
            case 'M'->"101111111101101"; case 'N'->"101111111111101"; case 'O'->"111101101101111";
            case 'P'->"110101110100100"; case 'Q'->"111101101111001"; case 'R'->"110101110101101";
            case 'S'->"111100111001111"; case 'T'->"111010010010010"; case 'U'->"101101101101111";
            case 'V'->"101101101101010"; case 'W'->"101101111111101"; case 'X'->"101101010101101";
            case 'Y'->"101101010010010"; case 'Z'->"111001010100111";
            case '0'->"111101101101111"; case '1'->"010110010010111"; case '2'->"110001010100111";
            case '3'->"110001010001110"; case '4'->"101101111001001"; case '5'->"111100110001110";
            case '6'->"011100111101111"; case '7'->"111001010010010"; case '8'->"111101111101111";
            case '9'->"111101111001110"; case '.'->"000000000000010"; case ','->"000000000010100";
            case '-'->"000000111000000"; case '/'->"001001010100100"; case '>'->"100010001010100";
            case ':'->"000010000010000"; case ';'->"000010000010100"; case '='->"000111000111000";
            case '?'->"110001010000010"; case '!'->"010010010000010"; case '"'->"101101000000000";
            case '\''->"010010000000000"; case '#'->"101111101111101"; case '$'->"011110111011110";
            case '%'->"101001010100101"; case '&'->"010101010101011"; case '('->"001010010010001";
            case ')'->"100010010010100"; case '*'->"101010111010101"; case '+'->"000010111010000";
            case '<'->"001010100010001"; case '['->"011010010010011"; case ']'->"110010010010110";
            case '\\'->"100100010001001"; case '^'->"010101000000000"; case '_'->"000000000000111";
            case '`'->"100010000000000"; case '{'->"011010100010011"; case '}'->"110010001010110";
            case '|'->"010010010010010"; case '~'->"000011110000000"; case '@'->"111101111100111";
            case 'a'->"000011101101011"; case 'b'->"100100110101110"; case 'c'->"000011100100011";
            case 'd'->"001001011101011"; case 'e'->"000010101110011"; case 'f'->"011010111010010";
            case 'g'->"000011101011110"; case 'h'->"100100110101101"; case 'i'->"010000010010010";
            case 'j'->"001000001101010"; case 'k'->"100101110101101"; case 'l'->"110010010010011";
            case 'm'->"000111111101101"; case 'n'->"000110101101101"; case 'o'->"000010101101010";
            case 'p'->"000110101110100"; case 'q'->"000011101011001"; case 'r'->"000011100100100";
            case 's'->"000011110011110"; case 't'->"010111010010011"; case 'u'->"000101101101011";
            case 'v'->"000101101101010"; case 'w'->"000101101111111"; case 'x'->"000101010010101";
            case 'y'->"000101101011110"; case 'z'->"000111001100111"; default->"000000000000000";
        };
    }

    private static void emitDrive(Asm s) {
        s.label("cmd.DRIVE");
        s.get(A,ARG); s.get(B,COUNT); s.eq(A,B,"drive.show");
        s.set(WORK,0);
        s.label("drive.parse");
        s.get(A,ARG); s.get(B,COUNT); s.eq(A,B,"drive.select");
        s.store(64,B); s.emit(ADD,A,B,C); s.readAt(LINE,C,D);
        s.store('0',A); s.emit(COMPARE_UNSIGNED,D,A,C); s.store(1,B); s.eq(C,B,"drive.bad");
        s.store('9',A); s.emit(COMPARE_UNSIGNED,D,A,C); s.store(2,B); s.eq(C,B,"drive.bad");
        s.store('0',A); s.emit(SUB,D,A,D); s.put(D,DIGIT);
        s.get(A,WORK); s.store(25,B); s.emit(COMPARE_UNSIGNED,A,B,C);
        s.store(2,D); s.eq(C,D,"drive.bad");
        String safe=s.unique("drive.safe"); s.ne(A,B,safe);
        s.get(A,DIGIT); s.store(5,B); s.emit(COMPARE_UNSIGNED,A,B,C); s.store(2,D); s.eq(C,D,"drive.bad");
        s.label(safe); s.get(A,WORK); s.store(10,B); s.emit(MULTIPLY,A,B,A);
        s.get(B,DIGIT); s.emit(ADD,A,B,A); s.put(A,WORK);
        s.get(A,ARG); s.emit(INCREMENT_REGISTER,A); s.put(A,ARG); s.jump("drive.parse");
        s.label("drive.select");
        s.store(0,A); s.emit(ADD,Registers.STORAGE_PAGE.address(),A,A); s.put(A,SECOND);
        s.get(A,WORK); s.store(0,B); s.emit(ADD,A,B,Registers.STORAGE_PAGE.address());
        s.emit(READ_INPUT,0x40,A); s.store(1,B); String mounted=s.unique("drive.mounted"); s.eq(A,B,mounted);
        s.get(A,SECOND); s.store(0,B); s.emit(ADD,A,B,Registers.STORAGE_PAGE.address());
        s.text("DRIVE NOT MOUNTED\n"); s.jump("shell");
        s.label(mounted); s.set(CWD,0); s.set(DISK_BANK,2); s.jump("drive.show");
        s.label("drive.bad"); s.text("USE DRIVE 0-255\n"); s.jump("shell");
        s.label("drive.show"); s.text("DRIVE ");
        s.store(0,A); s.emit(ADD,Registers.STORAGE_PAGE.address(),A,D); s.put(D,TEMP);
        s.call("number",NUMBER_RETURN); s.text("\n"); s.jump("shell");
    }

    /** Parent IDs live in reserved disk page 255, offsets slot+1; zero means root.
     * A directory has type 4 and uses one ordinary head block with no data chain. */
    private static void emitDirectories(Asm s) {
        s.label("directory.required.file");
        s.text("IS A DIRECTORY\n"); s.jump("shell");
        s.label("directory.not.directory");
        s.text("NOT A DIRECTORY\n"); s.jump("shell");
        s.label("directory.not.empty");
        s.text("DIRECTORY NOT EMPTY\n"); s.jump("shell");
        for(String command:new String[]{"MKDIR","MD","CD","CHDIR","RMDIR","RD"}) {
            s.label("cmd."+command);
            boolean cd=command.equals("CD")||command.equals("CHDIR");
            boolean make=command.equals("MKDIR")||command.equals("MD");
            if(cd) {
                s.get(A,ARG); s.get(B,COUNT); s.eq(A,B,"shell");
                s.store(64,B); s.emit(ADD,A,B,C); s.readAt(LINE,C,D);
                for(int slash:new int[]{47,92}) {
                    String next=s.unique("cd.slash");
                    s.store(slash,A); s.ne(D,A,next);
                    s.get(A,ARG); s.emit(INCREMENT_REGISTER,A); s.get(B,COUNT); s.ne(A,B,"usage");
                    s.set(CWD,0); s.jump("shell"); s.label(next);
                }
                s.store(46,A); String named=s.unique("cd.named"); s.ne(D,A,named);
                s.emit(INCREMENT_REGISTER,C); s.readAt(LINE,C,D);
                s.store(0,A); s.eq(D,A,"shell");
                s.store(46,A); s.ne(D,A,named);
                s.get(A,ARG); s.store(2,B); s.emit(ADD,A,B,A); s.get(B,COUNT); s.ne(A,B,named);
                s.get(C,CWD); s.store(0,A); s.eq(C,A,"shell");
                s.get(A,DISK_BANK); s.store(255,B); s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);
                s.put(D,CWD); s.jump("shell"); s.label(named);
            }
            s.call("parse.name",SEARCH_RETURN); s.call("search",SEARCH_RETURN);
            s.get(A,FOUND); s.store(255,B);
            if(make) {
                s.ne(A,B,"exists"); s.get(A,FREE); s.eq(A,B,"full"); s.put(A,SLOT);
                s.call("name.write",SEARCH_RETURN);
                s.store(0,D); for(int offset:new int[]{0,14,15}) s.diskWrite(offset,D);
                s.store(4,D); s.diskWrite(13,D); s.jump("shell");
            } else {
                s.eq(A,B,"missing"); s.put(A,SLOT);
                s.diskRead(13,D); s.store(4,A); s.ne(D,A,"directory.not.directory");
                s.get(A,SLOT); s.emit(INCREMENT_REGISTER,A); s.put(A,DIR_TARGET);
                if(cd) { s.put(A,CWD); s.jump("shell"); }
                else {
                    s.set(SLOT,0); String loop=s.unique("rd.scan"),next=s.unique("rd.next"); s.label(loop);
                    s.diskRead(1,D); s.store(0,A); s.eq(D,A,next); s.store(255,A); s.eq(D,A,next);
                    s.get(C,SLOT); s.emit(INCREMENT_REGISTER,C); s.get(A,DISK_BANK); s.store(255,B);
                    s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D); s.get(A,DIR_TARGET); s.eq(D,A,"directory.not.empty");
                    s.label(next); s.get(A,SLOT); s.emit(INCREMENT_REGISTER,A); s.put(A,SLOT);
                    s.store(255,B); s.ne(A,B,loop);
                    s.get(A,DIR_TARGET); s.store(1,B); s.emit(SUB,A,B,A); s.put(A,SLOT);
                    s.store(0,D); for(int offset:new int[]{0,1,13,14,15}) s.diskWrite(offset,D);
                    s.jump("shell");
                }
            }
        }
    }

    /** Same-bank metadata moves; never copy or buffer file contents. */
    private static void emitMove(Asm s) {
        s.label("cmd.DELETE");s.jump("cmd.DEL");s.label("cmd.RENAME");s.jump("cmd.REN");
        s.label("cmd.MOVE");s.call("parse.name",SEARCH_RETURN);s.call("search",SEARCH_RETURN);
        s.get(A,FOUND);s.store(255,B);s.eq(A,B,"missing");s.put(A,SOURCE);
        s.get(A,ARG);s.get(B,COUNT);s.eq(A,B,"usage");s.emit(SUB,B,A,B);s.put(B,SECOND);s.store(64,C);s.emit(ADD,A,C,C);s.readAt(LINE,C,D);
        s.store(1,A);s.get(B,SECOND);String named=s.unique("move.named");s.ne(A,B,"move.dotdot");
        s.store('/',A);s.eq(D,A,"move.root");s.store(92,A);s.eq(D,A,"move.root");s.store('.',A);s.ne(D,A,named);s.get(A,CWD);s.put(A,DIR_TARGET);s.jump("move.check");
        s.label("move.dotdot");s.store(2,A);s.ne(A,B,named);s.store('.',A);s.ne(D,A,named);s.emit(INCREMENT_REGISTER,C);s.readAt(LINE,C,D);s.ne(D,A,named);
        s.get(C,CWD);s.store(0,A);s.eq(C,A,"move.root");s.get(A,DISK_BANK);s.store(255,B);s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);s.put(D,DIR_TARGET);s.jump("move.check");
        s.label("move.root");s.set(DIR_TARGET,0);s.jump("move.check");
        s.label(named);s.call("parse.name",SEARCH_RETURN);s.get(A,ARG);s.get(B,COUNT);s.ne(A,B,"usage");s.call("search",SEARCH_RETURN);
        s.get(A,FOUND);s.store(255,B);s.eq(A,B,"move.rename");s.put(A,SLOT);s.diskRead(13,D);s.store(4,A);s.ne(D,A,"exists");s.get(A,SLOT);s.emit(INCREMENT_REGISTER,A);s.put(A,DIR_TARGET);
        s.label("move.check");s.get(A,DIR_TARGET);s.put(A,TARGET);s.set(TEMP,0);
        s.label("move.ancestor");s.get(C,TARGET);s.store(0,A);s.eq(C,A,"move.name");s.get(A,SOURCE);s.emit(INCREMENT_REGISTER,A);s.eq(A,C,"move.invalid");
        s.store(1,B);s.emit(SUB,C,B,B);s.get(A,DISK_BANK);s.store(13,C);s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);s.store(4,A);s.ne(D,A,"move.invalid");
        s.get(C,TARGET);s.get(A,DISK_BANK);s.store(255,B);s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);s.put(D,TARGET);s.get(A,TEMP);s.emit(INCREMENT_REGISTER,A);s.put(A,TEMP);s.store(255,B);s.eq(A,B,"move.invalid");s.jump("move.ancestor");
        s.label("move.name");s.set(INDEX,0);
        s.label("move.name.copy");s.get(A,INDEX);s.store(1,B);s.emit(ADD,A,B,C);s.get(A,DISK_BANK);s.get(B,SOURCE);s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);s.put(D,CH);s.get(A,INDEX);s.store(64,B);s.emit(ADD,A,B,C);s.get(D,CH);s.store(NAME,E);s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.get(A,INDEX);s.emit(INCREMENT_REGISTER,A);s.put(A,INDEX);s.store(12,B);s.ne(A,B,"move.name.copy");s.store(0,D);s.store(NAME,E);s.store(76,C);s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.get(A,CWD);s.put(A,SECOND);s.get(A,DIR_TARGET);s.put(A,CWD);s.call("search",SEARCH_RETURN);s.get(A,SECOND);s.put(A,CWD);s.get(A,FOUND);s.store(255,B);s.eq(A,B,"move.commit");s.get(B,SOURCE);s.eq(A,B,"move.done");s.jump("exists");
        s.label("move.commit");s.get(C,SOURCE);s.emit(INCREMENT_REGISTER,C);s.get(A,DISK_BANK);s.store(255,B);s.get(D,DIR_TARGET);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);s.jump("move.done");
        s.label("move.rename");s.get(A,SOURCE);s.put(A,SLOT);s.call("name.write",SEARCH_RETURN);
        s.label("move.done");s.text("MOVED\n");s.jump("shell");
        s.label("move.invalid");s.text("INVALID MOVE DESTINATION\n");s.jump("shell");
    }

    /** Linked 240-byte disk blocks, buffered in RAM bank one. No host filesystem hooks. */
    private static void emitFileSystem(Asm s) {
        s.label("file.empty");
        s.set16(LENGTH,LENGTH_H,0);
        s.set(FILE_TYPE,1);
        s.ret(FILE_RETURN);
        s.label("file.load");
        s.diskRead(13,D);
        s.store(4,A);
        s.eq(D,A,"directory.required.file");
        s.get(A,SLOT);
        s.put(A,ROOT_SLOT);
        s.put(A,BLOCK);
        s.diskRead(0,D);
        s.put(D,LENGTH);
        s.diskRead(14,D);
        s.put(D,LENGTH_H);
        s.diskRead(13,D);
        s.put(D,FILE_TYPE);
        String valid=s.unique("file.length.valid");
        s.less16Value(LENGTH,LENGTH_H,61201,valid);
        s.jump("file.corrupt");
        s.label(valid);
        s.emit(LOAD_MEM_ABSOLUTE,0,252,24,A);s.store(124,B);String fullBuffer=s.unique("file.full.buffer");s.ne(A,B,fullBuffer);
        s.less16Value(LENGTH,LENGTH_H,8193,fullBuffer);s.jump("file.corrupt");s.label(fullBuffer);
        // Track visited slots in reserved RAM page 254 before following untrusted links.
        s.store(0,A);
        s.store(0,D);
        s.store(254,E);
        s.label("file.visited.clear");
        s.emit(STORE_MEM_ON_PAGE,D,E,A);
        s.emit(INCREMENT_REGISTER,A);
        s.store(0,B);
        s.ne(A,B,"file.visited.clear");
        s.set16(BUF,BUF_H,0);
        s.set(BLOCK_COUNT,0);
        s.label("file.load.block");
        s.get(C,BLOCK);
        s.readAt(254,C,D);
        s.store(0,A);
        s.ne(D,A,"file.corrupt");
        s.store(1,D);
        s.store(254,E);
        s.get(C,BLOCK);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.get(A,BLOCK_COUNT);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,BLOCK_COUNT);
        s.store(0,B);
        s.eq(A,B,"file.corrupt");
        s.set(SEGMENT,0);
        s.label("file.load.byte");
        s.eq16(BUF,BUF_H,LENGTH,LENGTH_H,"file.load.end");
        s.get(A,SEGMENT);
        s.store(240,B);
        s.eq(A,B,"file.load.next");
        s.store(16,B);
        s.emit(ADD,A,B,C);
        s.get(A,DISK_BANK);
        s.get(B,BLOCK);
        s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);
        s.bufferWrite(BUF,BUF_H,D);
        s.inc16(BUF,BUF_H);
        s.get(A,SEGMENT);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,SEGMENT);
        s.jump("file.load.byte");
        s.label("file.load.next");
        s.blockRead(15,D);
        s.store(0,A);
        s.eq(D,A,"file.corrupt");
        s.store(255,A);
        s.eq(D,A,"file.corrupt");
        s.put(D,BLOCK);
        s.blockRead(1,D);
        s.store(255,A);
        s.ne(D,A,"file.corrupt");
        s.jump("file.load.block");
        s.label("file.load.end");
        s.blockRead(15,D);
        s.store(0,A);
        s.ne(D,A,"file.corrupt");
        // A continuation cannot be owned by another head in the same bank.
        s.set(SCAN,0);
        s.label("file.owner.scan");
        s.get(A,SCAN);
        s.store(255,B);
        s.eq(A,B,"file.owner.done");
        s.get(B,ROOT_SLOT); String ownerNext=s.unique("file.owner.next");
        s.eq(A,B,ownerNext);
        s.put(A,BLOCK);
        s.blockRead(1,D);
        s.store(0,A);
        s.eq(D,A,ownerNext);
        s.store(255,A);
        s.eq(D,A,ownerNext);
        s.set(BLOCK_COUNT,0);
        s.label("file.owner.chain");
        s.blockRead(15,D);
        s.store(0,A);
        s.eq(D,A,ownerNext);
        s.store(255,A);
        s.eq(D,A,"file.corrupt");
        s.put(D,BLOCK);
        s.get(C,BLOCK);
        s.readAt(254,C,D);
        s.store(0,A);
        s.ne(D,A,"file.corrupt");
        s.blockRead(1,D);
        s.store(255,A);
        s.ne(D,A,"file.corrupt");
        s.get(A,BLOCK_COUNT);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,BLOCK_COUNT);
        s.store(0,B);
        s.eq(A,B,"file.corrupt");
        s.jump("file.owner.chain");
        s.label(ownerNext);
        s.get(A,SCAN);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,SCAN);
        s.jump("file.owner.scan");
        s.label("file.owner.done");
        s.get(A,ROOT_SLOT);
        s.put(A,SLOT);
        s.ret(FILE_RETURN);

        s.label("file.save");
        s.get(A,SLOT);
        s.put(A,ROOT_SLOT);
        s.put(A,BLOCK);
        s.copy16(LENGTH,LENGTH_H,WORK,WORK_H);
        s.set(NEEDED,1);
        s.label("file.needed"); String last=s.unique("file.needed.last");
        s.less16Value(WORK,WORK_H,241,last);
        s.subtract16(WORK,WORK_H,240);
        s.get(A,NEEDED);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,NEEDED);
        s.jump("file.needed");
        s.label(last);
        // Build an allocation plan before touching names, lengths, links or content.
        s.set(PLAN_COUNT,0);
        s.set(OLD_TAIL,0);
        s.label("file.plan.old");
        s.get(C,PLAN_COUNT);
        s.get(D,BLOCK);
        s.store(254,E);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.get(A,PLAN_COUNT);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,PLAN_COUNT);
        s.get(B,NEEDED);
        s.eq(A,B,"file.plan.enough");
        s.blockRead(1,D);
        s.store(0,A);
        s.eq(D,A,"file.plan.free.start");
        s.blockRead(15,D);
        s.store(0,A);
        s.eq(D,A,"file.plan.free.start");
        s.put(D,BLOCK);
        s.jump("file.plan.old");
        s.label("file.plan.enough");
        s.blockRead(1,D);
        s.store(0,A);
        s.eq(D,A,"file.plan.ready");
        s.blockRead(15,D);
        s.put(D,OLD_TAIL);
        s.jump("file.plan.ready");
        s.label("file.plan.free.start");
        s.set(SCAN,1);
        s.label("file.plan.free");
        s.get(A,SCAN);
        s.store(255,B);
        s.eq(A,B,"file.full");
        s.get(B,ROOT_SLOT); String next=s.unique("file.plan.scan.next");
        s.eq(A,B,next);
        s.put(A,BLOCK);
        s.blockRead(1,D);
        s.store(0,A);
        s.ne(D,A,next);
        s.get(C,PLAN_COUNT);
        s.get(D,BLOCK);
        s.store(254,E);
        s.emit(STORE_MEM_ON_PAGE,D,E,C);
        s.get(A,PLAN_COUNT);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,PLAN_COUNT);
        s.get(B,NEEDED);
        s.eq(A,B,"file.plan.ready");
        s.label(next);
        s.get(A,SCAN);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,SCAN);
        s.jump("file.plan.free");
        s.label("file.plan.ready");
        s.set(PLAN_INDEX,0);
        s.set16(BUF,BUF_H,0);
        s.label("file.save.block");
        s.get(C,PLAN_INDEX);
        s.readAt(254,C,D);
        s.put(D,BLOCK);
        s.set(SEGMENT,0);
        s.label("file.save.byte");
        s.eq16(BUF,BUF_H,LENGTH,LENGTH_H,"file.save.link");
        s.get(A,SEGMENT);
        s.store(240,B);
        s.eq(A,B,"file.save.link");
        s.bufferRead(BUF,BUF_H,D);
        s.put(D,CH);
        s.get(A,SEGMENT);
        s.store(16,B);
        s.emit(ADD,A,B,C);
        s.get(A,DISK_BANK);
        s.get(B,BLOCK);
        s.get(D,CH);
        s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);
        s.inc16(BUF,BUF_H);
        s.get(A,SEGMENT);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,SEGMENT);
        s.jump("file.save.byte");
        s.label("file.save.link");
        s.get(A,PLAN_INDEX);
        s.emit(INCREMENT_REGISTER,A);
        s.put(A,PLAN_INDEX);
        s.get(B,NEEDED);
        String finalBlock=s.unique("file.save.final"), link=s.unique("file.save.link.value");
        s.eq(A,B,finalBlock);
        s.get(C,PLAN_INDEX);
        s.readAt(254,C,D);
        s.jump(link);
        s.label(finalBlock);
        s.store(0,D);
        s.label(link);
        s.blockWrite(15,D);
        s.get(A,BLOCK);
        s.get(B,ROOT_SLOT); String head=s.unique("file.save.head");
        s.eq(A,B,head);
        s.store(255,D);
        s.blockWrite(1,D);
        s.label(head);
        s.get(A,PLAN_INDEX);
        s.get(B,NEEDED);
        s.ne(A,B,"file.save.block");
        // Release old tail blocks only after all replacement blocks have been written.
        s.get(A,OLD_TAIL);
        s.put(A,BLOCK);
        s.label("file.save.free");
        s.get(A,BLOCK);
        s.store(0,B);
        s.eq(A,B,"file.save.header");
        s.blockRead(15,D);
        s.put(D,OLD_TAIL);
        s.store(0,D);
        s.blockWrite(1,D);
        s.blockWrite(0,D);
        s.blockWrite(14,D);
        s.blockWrite(15,D);
        s.get(A,OLD_TAIL);
        s.put(A,BLOCK);
        s.jump("file.save.free");
        s.label("file.save.header");
        s.get(A,ROOT_SLOT);
        s.put(A,SLOT);
        s.call("name.write",SEARCH_RETURN);
        s.get(D,FILE_TYPE);
        s.diskWrite(13,D);
        s.get(D,LENGTH_H);
        s.diskWrite(14,D);
        s.get(D,LENGTH);
        s.diskWrite(0,D);
        s.ret(FILE_RETURN);

        s.label("file.delete");
        s.get(A,SLOT);
        s.put(A,BLOCK);
        s.label("file.delete.block");
        s.blockRead(15,D);
        s.put(D,OLD_TAIL);
        s.store(0,D);
        s.blockWrite(1,D);
        s.blockWrite(0,D);
        s.blockWrite(14,D);
        s.blockWrite(15,D);
        s.get(A,OLD_TAIL);
        s.store(0,B); String deleted=s.unique("file.deleted");
        s.eq(A,B,deleted);
        s.put(A,BLOCK);
        s.jump("file.delete.block");
        s.label(deleted);
        s.ret(FILE_RETURN);
        s.label("file.full");
        s.text("DISK BANK FULL - FILE UNCHANGED\n");
        s.get(A,MODE);
        s.store(1,B);
        s.eq(A,B,"poll");
        s.jump("shell");
        s.label("file.corrupt");
        s.set(SCRIPT_ACTIVE,0);
        s.text("INVALID FILE CHAIN\n");
        s.jump("shell");
    }

    /** Labels, far branches and calls compile exclusively to the existing CPU opcodes. */
    private static final class Asm {
            final int first; final List<byte[]> pages=new ArrayList<>(); final Map<String,Integer> labels=new HashMap<>();
        final List<Fix> fixes=new ArrayList<>(); final Map<String,String> messages=new java.util.LinkedHashMap<>();
        int page,pos,serial,branchSerial,codeEndPage;
        record Fix(int page,int offset,String label,boolean high) {}
        record Branch(int id,int page,String target,boolean local) { }
        final java.util.Set<Integer> localBranches;
        final List<Branch> branches=new ArrayList<>();
        Asm(int first,java.util.Set<Integer> local) { this.first=first;localBranches=local; page=first; pages.add(new byte[256]); }
        byte[] current() { return pages.get(page-first); }
        void reserve(int n) {
            if(pos+n<=247) return;
            raw(STORE.code(),page+1,G,STORE.code(),0,H,CHANGE_PAGE_JUMP.code(),G,H); page++; pos=0; pages.add(new byte[256]);
        }
        void raw(int...bytes) { for(int b:bytes) current()[pos++]=(byte)b; }
        void emit(OPCode opcode, int... operands) {
            if (operands.length != opcode.inputLength()) {
                throw new IllegalArgumentException("Wrong operand count for " + opcode);
            }
            reserve(opcode.length());
            raw(opcode.code());
            raw(operands);
        }
        void label(String name) { if(labels.put(name,page*256+pos)!=null) throw new IllegalStateException("Duplicate "+name); }
        String unique(String prefix) { return prefix+"."+serial++; }
        void store(int value,int register) { emit(STORE,value,register); }
        void ref(String target,boolean high,int register) { reserve(3); raw(STORE.code(),0,register); fixes.add(new Fix(page,pos-2,target,high)); }
        void jump(String target) { int id=branchSerial++;boolean local=localBranches.contains(id);reserve(local?2:9);branches.add(new Branch(id,page,target,local));if(local){raw(JMP.code(),0);fixes.add(new Fix(page,pos-1,target,false));}else{ref(target,true,G);ref(target,false,H);emit(CHANGE_PAGE_JUMP,G,H);} }
        void eq(int a,int b,String target) { int id=branchSerial++;boolean local=localBranches.contains(id);reserve(local?4:8);branches.add(new Branch(id,page,target,local));if(local)raw(COND_JUMP.code(),a,b,0);else{ref(target,true,G);raw(CHANGE_PAGE_COND_JUMP.code(),G,a,b,0);}fixes.add(new Fix(page,pos-1,target,false)); }
        void ne(int a,int b,String target) { String same=unique("same"); eq(a,b,same); jump(target); label(same); }
        void put(int register,int slot) { emit(STORE_MEM_ABSOLUTE,register,0,slot>=192?253:STATE,slot>=192?slot-192:slot+64); }
        void set(int slot,int value) { store(value,D); put(D,slot); }
        void netWrite(int address,int value) { store(address,A); store(value,D); emit(WRITE_NETWORK,A,D); }
        void netText(String text) { stringPointer(text,MESSAGE_PAGE,MESSAGE_ADDRESS); call("web.sendtext",MESSAGE_RETURN); }
        void click() {
            get(A,KEY_SOUND); store(0,B); String done=unique("click.off"); eq(A,B,done);
            tone(); label(done);
        }
        void tone() {
            store(0,A); store(84,B); store(131,C); emit(OUTPUT_AUDIO,A,B,C);
            emit(DISPLAY); store(0,C); emit(OUTPUT_AUDIO,A,B,C);
        }
        void silence() { store(0,B); store(0,C); for(int voice=0;voice<8;voice++) { store(voice,A); emit(OUTPUT_AUDIO,A,B,C); } }
        void cursor(int color) {
            get(A,X); get(B,Y); store(5,C); emit(ADD,B,C,B); store(color,C);
            for(int i=0;i<3;i++) { emit(PUSH_GRA_MEM,A,B,C); emit(INCREMENT_REGISTER,A); }
        }
        void get(int register,int slot) {
            read(slot>=192?253:STATE,slot>=192?slot-192:slot+64,register);
        }
        void editSize() {
            get(A,MODE); store(1,B); String editor=unique("edit.size"),done=unique("edit.size.done"); eq(A,B,editor);
            get(A,COUNT); put(A,EDIT_SIZE); set(EDIT_SIZE_H,0); jump(done); label(editor); copy16(LENGTH,LENGTH_H,EDIT_SIZE,EDIT_SIZE_H); label(done);
        }
        void editIncrement(boolean up) {
            get(A,MODE); store(1,B); String editor=unique("edit.length"),done=unique("edit.length.done"); eq(A,B,editor);
            get(A,COUNT); if(up) emit(INCREMENT_REGISTER,A); else {store(1,B);emit(SUB,A,B,A);} put(A,COUNT); jump(done);
            label(editor); if(up) inc16(LENGTH,LENGTH_H); else dec16(LENGTH,LENGTH_H); label(done);
        }
        void read(int bank,int slot,int register) { emit(LOAD_MEM_ABSOLUTE,0,bank,slot,register); }
        // Indirect reads preserve A?H unless selected as the destination. I/J are scratch.
        void readAt(int bank,int addressRegister,int register) {
            reserve(11); store(0,I); store(bank,J); emit(LOAD_MEM_ON_BANK,I,J,addressRegister,register);
        }
        void call(String target,int slot) {
            String back=unique("call.return"); ref(back,true,C); put(C,slot); ref(back,false,C); put(C,slot+1); jump(target); label(back);
        }
        void ret(int slot) { get(A,slot); get(B,slot+1); emit(CHANGE_PAGE_JUMP,A,B); }
        void stringPointer(String text,int pageSlot,int offsetSlot) {
            String name=messages.computeIfAbsent(text,t->unique("message.data"));
            ref(name,true,C); put(C,pageSlot); ref(name,false,C); put(C,offsetSlot);
        }
        void text(String text) {
            String name=messages.computeIfAbsent(text,t->unique("message.data"));
            ref(name,true,C); put(C,MESSAGE_PAGE); ref(name,false,C); put(C,MESSAGE_ADDRESS); call("message",MESSAGE_RETURN);
        }
        void readDynamic(int bankRegister,int addressRegister,int dest) {
            // Copy the page before setting the bank; the page may be in a working register.
            store(0,I); emit(ADD,bankRegister,I,J); emit(LOAD_MEM_ON_BANK,I,J,addressRegister,dest);
        }
        void copy16(int low,int high,int destLow,int destHigh) { get(A,low); put(A,destLow); get(A,high); put(A,destHigh); }
        void set16(int low,int high,int value) { set(low,value&255); set(high,value>>>8); }
        void inc16(int low,int high) {
            get(A,low); emit(INCREMENT_REGISTER,A); put(A,low); store(0,B); String done=unique("inc16"); ne(A,B,done);
            get(A,high); emit(INCREMENT_REGISTER,A); put(A,high); label(done);
        }
        void dec16(int low,int high) {
            get(A,low); store(0,B); String done=unique("dec16"); ne(A,B,done);
            get(A,high); store(1,B); emit(SUB,A,B,A); put(A,high); label(done);
            get(A,low); store(1,B); emit(SUB,A,B,A); put(A,low);
        }
        void eq16(int low,int high,int otherLow,int otherHigh,String target) {
            String done=unique("eq16"); get(A,high); get(B,otherHigh); ne(A,B,done);
            get(A,low); get(B,otherLow); eq(A,B,target); label(done);
        }
        void eq16Value(int low,int high,int value,String target) {
            String done=unique("eq16.literal"); get(A,high); store(value>>>8,B); ne(A,B,done);
            get(A,low); store(value&255,B); eq(A,B,target); label(done);
        }
        void less16Value(int low,int high,int value,String target) {
            String done=unique("less16"); get(A,high); store(value>>>8,B); emit(COMPARE_UNSIGNED,A,B,C); store(1,D); eq(C,D,target);
            store(2,D); eq(C,D,done); get(A,low); store(value&255,B); emit(COMPARE_UNSIGNED,A,B,C); store(1,D); eq(C,D,target); label(done);
        }
        void subtract16(int low,int high,int value) {
            get(A,low); store(value&255,B); emit(COMPARE_UNSIGNED,A,B,C); put(C,CMP_TEMP); emit(SUB,A,B,A); put(A,low);
            get(A,high); store(value>>>8,B); emit(SUB,A,B,A); put(A,high);
            get(A,CMP_TEMP); store(1,B); String done=unique("subtract16"); ne(A,B,done);
            get(A,high); emit(SUB,A,B,A); put(A,high); label(done);
        }
        void bufferRead(int low,int high,int dest) { get(B,high); get(C,low); store(1,A); emit(LOAD_MEM_ON_BANK,A,B,C,dest); }
        void bufferWrite(int low,int high,int value) { get(B,high); get(C,low); store(1,A); emit(STORE_MEM_ON_BANK,value,A,B,C); }
        void blockRead(int offset,int dest) { get(A,DISK_BANK); get(B,BLOCK); store(offset,C); emit(READ_BYTE_FROM_STORAGE,A,B,C,dest); }
        void blockWrite(int offset,int value) { get(A,DISK_BANK); get(B,BLOCK); store(offset,C); emit(WRITE_BYTE_TO_STORAGE,A,B,C,value); }
        void parentFilter(int slot,String skip) {
            get(C,slot); emit(INCREMENT_REGISTER,C); get(A,DISK_BANK); store(255,B);
            emit(READ_BYTE_FROM_STORAGE,A,B,C,D); get(A,CWD); ne(D,A,skip);
        }
        void diskRead(int offset,int dest) { store(offset,C); diskReadAt(C,dest); }
        void diskReadAt(int offset,int dest) { get(B,SLOT); get(A,DISK_BANK); emit(READ_BYTE_FROM_STORAGE,A,B,offset,dest); }
        void diskWrite(int offset,int value) { store(offset,C); diskWriteAt(C,value); }
        void diskWriteAt(int offset,int value) { get(B,SLOT); get(A,DISK_BANK); emit(WRITE_BYTE_TO_STORAGE,A,B,offset,value); }
        void diskLiteral(int slot,int offset,int value) { store(2,A); store(slot,B); store(offset,C); store(value,D); emit(WRITE_BYTE_TO_STORAGE,A,B,C,D); }
        byte[][] finish() {
            boolean firstMessage=true;
            for(Map.Entry<String,String> message:messages.entrySet()) {
                if(message.getKey().length()>222) throw new IllegalStateException("Message too long");
                if(firstMessage || pos+message.getKey().length()+1>256) {
                    page++; pos=24; pages.add(new byte[256]); installReaders(current()); firstMessage=false;
                }
                label(message.getValue());
                for(char c:message.getKey().toCharArray()) raw(c); raw(0);
            }
            for(int c=32;c<=126;c++) {
                if(pos+6>256) { page++; pos=24; pages.add(new byte[256]); installReaders(current()); }
                label("font.data."+c); String bits=font((char)c);
                for(int row=0;row<6;row++) {
                    int packed=0;
                    if(row<5) for(int col=0;col<3;col++) if(bits.charAt(row*3+col)=='1') packed|=128>>>col;
                    raw(packed);
                }
            }
            codeEndPage=page;
            for(Fix fix:fixes) {
                Integer address=labels.get(fix.label); if(address==null) throw new IllegalStateException("Undefined "+fix.label);
                pages.get(fix.page-first)[fix.offset]=(byte)(fix.high?address/256:address%256);
            }
            while(pages.size()+first<=STATE) pages.add(new byte[256]);
            byte[] services=pages.get(249-first);
            for(int i=0;i<3;i++) {
                int address=labels.get(new String[]{"com.print","shell","graphics.glyph"}[i]); int offset=i*9;
                int[] code={STORE.code(),address/256,G,STORE.code(),address%256,H,CHANGE_PAGE_JUMP.code(),G,H};
                for(int j=0;j<code.length;j++) services[offset+j]=(byte)code[j];
            }
            services[JvDosServices.GRAPHICS_SIGNATURE]='J';services[JvDosServices.GRAPHICS_SIGNATURE+1]='G';services[JvDosServices.GRAPHICS_SIGNATURE+2]=JvDosServices.GRAPHICS_VERSION;
            for(int bank:new int[]{246,247,NAME,LINE,STATE}) installReaders(pages.get(bank-first));
            byte[] state=pages.get(STATE-first);
            byte[] pointers=pages.get(246-first);
            for(int c=32;c<=126;c++) {
                int address=labels.get("font.data."+c), offset=64+(c-32)*2;
                pointers[offset]=(byte)(address/256); pointers[offset+1]=(byte)address;
            }
            String[] operands={"rrr","rrr","lr","l","","r","rrl","rl","rl","r","rr","rrrl","rrr","","","rrr","lr","rrr","rrrr","rrrr","rrr","r","rrr","rrr","rrrr","rrrr","rrr","lllr","rlll","rr","rr"};
            byte[] descriptors=pages.get(247-first);
            for(OPCode opcode:OPCode.values()) {
                int code=opcode.code()&255, offset=64+code*5;
                descriptors[offset]=(byte)opcode.length();
                for(int i=0;i<operands[code].length();i++) descriptors[offset+1+i]=(byte)(operands[code].charAt(i)=='r'?1:0);
            }
            return pages.toArray(byte[][]::new);
        }
        void installReaders(byte[] memory) {
            for(int r=0;r<4;r++) { int i=r*6; memory[i]=7; memory[i+1]=(byte)r; memory[i+2]=0; memory[i+3]=10; memory[i+4]=G; memory[i+5]=H; }
        }
    }
}
