package xyz.ignite4inferneo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Assembly source for the resident DOS image. Java only emits bytes and resolves addresses.
 * Every keyboard, filesystem, display and executable-loader operation runs on the guest CPU.
 * Disk bank 2 contains sixteen 256-byte files: length at 0, name at 1..12, data at 16..255.
 * RAM 246 holds font pointers and 247 instruction descriptors. RAM 248 is the transient
 * program area; 249 is the COM service page; 250/251/252 hold
 * names, input and kernel variables. COM services: 249:0 prints D and returns to A:B;
 * 249:9 returns to the command prompt. These entry addresses survive kernel reassembly.
 * The six-byte RAM readers implement indirect loads using existing self-modifying bytecode.
 */
final class JvDosImage {
    private static final int A=0,B=1,C=2,D=3,E=4,F=5,G=6,H=7;
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
            KEY_RAW=150,SHIFT=151,MUSIC_FRAMES=152,MUSIC_VOICE=153,MUSIC_NOTE=154,MUSIC_CONTROL=155;

    /** Exports the assembled boot sector and byte arrays for the unchanged disk installer. */
    public static void main(String[] args) throws java.io.IOException {
        if(args.length!=1) throw new IllegalArgumentException("Supply an output image path");
        byte[][] pages=Programs.bootImage(); byte[] image=new byte[65536];
        byte[] header={'J','V','C','P','M',4,(byte)pages.length,1};
        System.arraycopy(header,0,image,0,header.length);
        for(int page=0;page<pages.length;page++) System.arraycopy(pages[page],0,image,512+page*256,pages[page].length);
        java.nio.file.Files.write(java.nio.file.Path.of(args[0]),image);
    }

    static byte[][] assemble(int firstPage) {
        Asm s=new Asm(firstPage);
        s.label("boot"); s.emit(13); s.set(X,4); s.set(Y,4); s.set(MODE,0); s.set(RENDERING,0); s.set(ENTER_PENDING,0);
        s.set(SCRIPT_ACTIVE,0); s.set(KEY_SOUND,1); s.set(INK,28);
        s.set(TAB_PENDING,0); s.set(KEY_SYNTH,0);
        s.set(SHIFT,0); s.text("JV-DOS 1.1\n64K RAM  16M DISK\nHELP FOR COMMANDS\n");
        // Seed a real executable once; later boots preserve the user's directory and data.
        s.store(2,A); s.store(255,B); s.store(0,C); s.emit(18,A,B,C,D);
        s.store(0x4a,A); s.eq(D,A,"shell");
        byte[] hello=helloProgram();
        s.diskLiteral(0,0,hello.length);
        String filename="HELLO.COM";
        for(int i=0;i<12;i++) s.diskLiteral(0,i+1,i<filename.length()?filename.charAt(i):0);
        for(int i=0;i<hello.length;i++) s.diskLiteral(0,16+i,hello[i]&255);
        s.diskLiteral(255,0,0x4a);
        s.jump("shell");

        s.label("shell"); s.get(A,SCRIPT_ACTIVE); s.store(1,B); s.eq(A,B,"script.next");
        s.set(INK,28); s.set(COUNT,0); s.set(CURSOR,0); s.set(MODE,0); s.text("A> ");
        s.get(A,X); s.put(A,BASE_X); s.get(A,Y); s.put(A,BASE_Y);
        s.label("poll"); s.cursor(28); s.emit(14);
        s.get(A,TAB_PENDING); s.store(0,B); s.eq(A,B,"poll.hardware");
        s.store(1,B); s.emit(1,A,B,A); s.put(A,TAB_PENDING); s.set(KEY_SYNTH,1); s.store(32,D); s.jump("poll.input");
        s.label("poll.hardware"); s.set(KEY_SYNTH,0); s.emit(16,0,D); s.label("poll.input");
        s.store(0,A); s.eq(D,A,"poll");
        s.cursor(0);
        s.store(16,A); s.eq(D,A,"shift.on"); s.store(144,A); s.eq(D,A,"shift.off");
        for(int scan:new int[]{192,222}) { s.store(scan,A); s.eq(D,A,"extended."+scan); }
        String dispatch="key.dispatch";
        for(int key:new int[]{9,27,8,10,37,38,39,40,36,35,127}) { s.store(key,A); s.eq(D,A,"key.click"); }
        for(int key=32;key<=111;key++) if(printable(key)) { s.store(key,A); s.eq(D,A,"key.click"); }
        s.jump("poll");
        s.label("key.click"); s.get(A,KEY_SYNTH); s.store(1,B); s.eq(A,B,dispatch); s.click(); s.label(dispatch);
        s.store(9,A); s.eq(D,A,"key.tab");
        s.store(27,A); s.eq(D,A,"cancel");
        s.store(8,A); s.eq(D,A,"backspace");
        s.store(10,A); s.eq(D,A,"enter");
        for(int key:new int[]{37,38,39,40,36,35,127}) { s.store(key,A); s.eq(D,A,"move."+key); }
        // Printable make events only; navigation has separate handlers and releases are ignored.
        s.jump("keyboard.translate");
        s.label("shift.on"); s.set(SHIFT,1); s.jump("poll");
        s.label("shift.off"); s.set(SHIFT,0); s.jump("poll");
        for(int scan:new int[]{192,222}) {
            s.label("extended."+scan); s.put(D,KEY_RAW); s.emit(16,1+scan/8,A);
            s.store(1<<(7-scan%8),B); s.emit(20,A,B,A); s.store(128,B); s.store(0,C);
            String bit=s.unique("keyboard.bit"), down=s.unique("keyboard.down"); s.label(bit);
            s.eq(B,C,down); s.eq(A,C,"poll"); s.store(1,C); s.emit(1,A,C,A); s.emit(1,B,C,B); s.store(0,C); s.jump(bit);
            s.label(down); s.get(D,KEY_RAW); s.jump("key.click");
        }
        s.label("key.tab"); s.set(TAB_PENDING,4); s.jump("poll");
        s.label("key"); s.put(D,CH); s.get(A,MODE); s.store(1,B); s.eq(A,B,"edit.key");
        s.jump("line.insert");

        s.label("backspace"); s.get(A,MODE); s.store(1,B); s.eq(A,B,"edit.backspace");
        s.jump("line.backspace");
        s.label("erase"); s.get(A,X); s.store(4,B); s.eq(A,B,"erase.wrap");
        s.emit(1,A,B,A); s.put(A,X); s.jump("erase.cell");
        s.label("erase.wrap"); s.set(X,248); s.get(A,Y); s.store(8,B); s.emit(1,A,B,A); s.put(A,Y);
        s.label("erase.cell"); s.store(32,D); s.call("glyph",GLYPH_RETURN); s.jump("poll");
        s.label("cancel"); s.get(A,MODE); s.store(1,B); s.eq(A,B,"edit.save");
        s.text("\n"); s.jump("shell");
        s.label("enter"); s.get(A,MODE); s.store(1,B); s.eq(A,B,"edit.newline");
        s.get(A,COUNT); s.put(A,CURSOR); s.set(ENTER_PENDING,1); s.jump("redraw");
        s.label("enter.command"); s.set(ENTER_PENDING,0);
        s.get(A,COUNT); s.store(64,B); s.emit(0,A,B,C); s.store(0,D); s.store(LINE,E); s.emit(22,D,E,C);
        s.text("\n"); s.get(A,COUNT); s.store(0,B); s.eq(A,B,"shell");
        for(String command:new String[]{"HELP","CODEHELP","DIR","TYPE","CAT","VIEW","MORE","IMAGE","SHOW","PLAY","DEL","ERASE","WRITE","EDIT","APPEND","COPY","REN","ECHO","PRINT","CLS","CLEAR","VER","MEM","RUN","SAVE","STORE","ADD","SUB","MULTIPLY","INC","OUTPUT","GOTO","IF","BEEP","SOUND","REM","END","STOP","REBOOT","EXIT"}) {
            String next=s.unique("command");
            if(!java.util.Set.of("PRINT","ECHO","CLS","CLEAR","STORE","ADD","SUB","MULTIPLY","INC","OUTPUT","GOTO","IF","BEEP","REM","END","STOP").contains(command)) {
                s.get(A,SCRIPT_ACTIVE); s.store(1,B); s.eq(A,B,next);
            }
            for(int i=0;i<command.length();i++) {
                s.read(LINE,64+i,D); s.store(command.charAt(i),A); s.ne(D,A,next);
            }
            s.read(LINE,64+command.length(),D); s.store(0,A); String empty=s.unique("empty.argument");
            s.eq(D,A,empty); s.store(32,A); s.ne(D,A,next);
            s.set(ARG,command.length()+1); s.jump("cmd."+command);
            s.label(empty); s.set(ARG,command.length()); s.jump("cmd."+command);
            s.label(next);
        }
        s.get(A,SCRIPT_ACTIVE); s.store(1,B); s.eq(A,B,"script.error");
        s.text("BAD COMMAND - CODEHELP\n"); s.jump("shell");

        s.label("cmd.HELP"); s.text("DIR  TYPE FILE  DEL FILE\nWRITE FILE TEXT\nAPPEND FILE TEXT\nEDIT FILE - ESC SAVES\nCOPY FILE NEW  REN FILE NEW\nRUN FILE.COM  ECHO TEXT\nSAVE FILE HEX BYTE PAIRS\nCLS VER MEM REBOOT EXIT\nBACKSPACE EDITS INPUT\n");
        s.text("CODEHELP FOR SOURCE PROGRAMS\nSOUND ON OR SOUND OFF\nVIEW FILE  IMAGE FILE  PLAY FILE\n"); s.jump("shell");
        s.label("cmd.CLEAR"); s.label("cmd.CLS"); s.emit(13); s.set(X,4); s.set(Y,4); s.jump("shell");
        s.label("cmd.VER"); s.text("JV-DOS 1.1\nGUEST BYTECODE KERNEL\n"); s.jump("shell");
        s.label("cmd.MEM"); s.text("65536 BYTES RAM\n256 BYTE TRANSIENT AREA\n16 FILES X 240 BYTES\n"); s.jump("shell");
        s.label("cmd.REBOOT"); s.jump("boot");
        s.label("cmd.EXIT"); s.text("SYSTEM HALTED\n"); s.emit(14); s.emit(4);
        s.label("cmd.PRINT"); s.label("cmd.ECHO"); s.label("echo.loop"); s.get(A,ARG); s.get(B,COUNT); s.eq(A,B,"echo.end");
        s.store(64,B); s.emit(0,A,B,C); s.readAt(LINE,C,D); s.put(D,CH); s.call("print",PRINT_RETURN);
        s.get(A,ARG); s.emit(21,A); s.put(A,ARG); s.jump("echo.loop");
        s.label("echo.end"); s.text("\n"); s.jump("shell");

        s.label("cmd.DIR"); s.set(SLOT,0);
        s.label("dir.loop"); s.diskRead(1,D); s.store(0,A); s.eq(D,A,"dir.next");
        s.set(INDEX,1);
        s.label("dir.name"); s.get(C,INDEX); s.diskReadAt(C,D); s.store(0,A); s.eq(D,A,"dir.size");
        s.put(D,CH); s.call("print",PRINT_RETURN); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX);
        s.store(13,B); s.eq(A,B,"dir.size"); s.jump("dir.name");
        s.label("dir.size"); s.text(" "); s.diskRead(0,D); s.put(D,TEMP); s.call("number",NUMBER_RETURN); s.text(" BYTES\n");
        s.label("dir.next"); s.get(A,SLOT); s.emit(21,A); s.put(A,SLOT); s.store(16,B); s.ne(A,B,"dir.loop"); s.jump("shell");

        // All commands below share guest-side argument scanning and directory lookup.
        for(String command:new String[]{"TYPE","CAT","VIEW","MORE","IMAGE","SHOW","PLAY","DEL","ERASE","WRITE","EDIT","APPEND","COPY","REN","RUN","SAVE"}) {
            s.label("cmd."+command); s.call("parse.name",SEARCH_RETURN); s.call("search",SEARCH_RETURN);
            if(command.equals("WRITE")||command.equals("EDIT")||command.equals("SAVE")) s.jump("prepare."+command);
            else {
                s.get(A,FOUND); s.store(255,B); s.eq(A,B,"missing"); s.put(A,SLOT);
                s.jump("do."+command);
            }
        }
        s.label("missing"); s.text("FILE NOT FOUND\n"); s.jump("shell");
        s.label("full"); s.text("DIRECTORY FULL\n"); s.jump("shell");
        s.label("usage"); s.text("BAD OR MISSING FILE NAME\n"); s.jump("shell");
        s.label("exists"); s.text("DESTINATION EXISTS\n"); s.jump("shell");
        for(String command:new String[]{"WRITE","EDIT","SAVE"}) {
            s.label("prepare."+command); s.get(A,FOUND); s.store(255,B); String found=s.unique("found"); s.ne(A,B,found);
            s.get(A,FREE); s.eq(A,B,"full"); s.label(found); s.put(A,SLOT);
            s.call("name.write",SEARCH_RETURN);
            if(command.equals("EDIT")) { s.diskRead(0,D); s.put(D,LENGTH); }
            else s.set(LENGTH,0);
            s.jump("do."+command);
        }
        s.label("do.APPEND"); s.diskRead(0,D); s.put(D,LENGTH); s.store(1,D); s.diskWrite(13,D); s.jump("write.text");
        s.label("do.WRITE"); s.store(1,D); s.diskWrite(13,D); s.label("write.text"); s.get(A,ARG); s.get(B,COUNT); s.eq(A,B,"write.done");
        s.get(A,LENGTH); s.store(240,B); s.eq(A,B,"write.done");
        s.get(A,ARG); s.store(64,B); s.emit(0,A,B,C); s.readAt(LINE,C,D); s.put(D,CH);
        s.get(A,LENGTH); s.store(16,B); s.emit(0,A,B,C); s.get(D,CH); s.diskWriteAt(C,D);
        s.get(A,LENGTH); s.emit(21,A); s.put(A,LENGTH); s.get(A,ARG); s.emit(21,A); s.put(A,ARG); s.jump("write.text");
        s.label("write.done"); s.get(D,LENGTH); s.diskWrite(0,D); s.text("SAVED\n"); s.jump("shell");
        s.label("do.SAVE"); s.store(0,D); s.diskWrite(13,D); s.set(SECOND,0);
        s.label("hex.loop"); s.get(A,ARG); s.get(B,COUNT); s.eq(A,B,"hex.end");
        s.store(64,B); s.emit(0,A,B,C); s.readAt(LINE,C,D); s.store(32,A); s.eq(D,A,"hex.next");
        for(int digit=0;digit<16;digit++) { s.store(digit<10?'0'+digit:'A'+digit-10,A); s.eq(D,A,"hex."+digit); }
        s.jump("hex.bad");
        for(int digit=0;digit<16;digit++) { s.label("hex."+digit); s.set(TEMP,digit); s.jump("hex.value"); }
        s.label("hex.value"); s.get(A,SECOND); s.store(0,B); s.ne(A,B,"hex.low");
        s.get(A,TEMP); s.store(16,B); s.emit(20,A,B,A); s.put(A,SOURCE); s.set(SECOND,1); s.jump("hex.next");
        s.label("hex.low"); s.get(A,SOURCE); s.get(B,TEMP); s.emit(0,A,B,D); s.put(D,CH);
        s.get(A,LENGTH); s.store(240,B); s.eq(A,B,"hex.bad"); s.store(16,B); s.emit(0,A,B,C); s.get(D,CH); s.diskWriteAt(C,D);
        s.get(A,LENGTH); s.emit(21,A); s.put(A,LENGTH); s.set(SECOND,0);
        s.label("hex.next"); s.get(A,ARG); s.emit(21,A); s.put(A,ARG); s.jump("hex.loop");
        s.label("hex.end"); s.get(A,SECOND); s.store(0,B); s.eq(A,B,"write.done");
        s.label("hex.bad"); s.text("INVALID HEX BYTE PAIRS\n"); s.jump("shell");
        s.label("do.EDIT"); s.get(A,LENGTH); s.store(0,B); String existingEdit=s.unique("existing.edit"); s.ne(A,B,existingEdit);
        s.store(1,D); s.diskWrite(13,D); s.label(existingEdit); s.set(MODE,1); s.set(CURSOR,0); s.jump("edit.redraw");
        s.label("edit.newline"); s.set(CH,10); s.jump("edit.store");
        s.label("edit.key"); s.label("edit.store"); s.jump("edit.insert");
        s.label("edit.backspace"); s.jump("line.backspace");
        s.label("edit.save"); s.get(D,LENGTH); s.diskWrite(0,D); s.set(INK,28); s.text("\nSAVED\n"); s.jump("shell");
        s.label("do.DEL"); s.label("do.ERASE"); s.store(0,D); s.diskWrite(1,D); s.diskWrite(0,D); s.text("DELETED\n"); s.jump("shell");
        s.label("do.TYPE"); s.label("do.CAT"); s.diskRead(0,D); s.put(D,LENGTH); s.set(INDEX,0);
        s.label("type.loop"); s.get(A,INDEX); s.get(B,LENGTH); s.eq(A,B,"type.end");
        s.store(16,B); s.emit(0,A,B,C); s.diskReadAt(C,D); s.put(D,CH); s.call("print",PRINT_RETURN);
        s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.jump("type.loop");
        s.label("type.end"); s.text("\n"); s.jump("shell");

        for(String command:new String[]{"COPY","REN"}) {
            s.label("do."+command); s.get(A,SLOT); s.put(A,SOURCE); s.call("parse.name",SEARCH_RETURN); s.call("search",SEARCH_RETURN);
            s.get(A,FOUND); s.store(255,B); s.ne(A,B,"exists");
            if(command.equals("COPY")) { s.get(A,FREE); s.eq(A,B,"full"); s.put(A,SECOND); s.put(A,SLOT); }
            else { s.get(A,SOURCE); s.put(A,SLOT); }
            s.call("name.write",SEARCH_RETURN);
            if(command.equals("COPY")) {
                s.get(A,SOURCE); s.put(A,SLOT); s.diskRead(0,D); s.put(D,LENGTH); s.set(INDEX,0);
                String loop="copy.loop"; s.label(loop); s.get(A,INDEX); s.get(B,LENGTH); s.eq(A,B,"copy.end");
                s.store(16,B); s.emit(0,A,B,C); s.diskReadAt(C,D); s.put(D,CH);
                s.get(A,SECOND); s.put(A,SLOT); s.get(A,INDEX); s.store(16,B); s.emit(0,A,B,C); s.get(D,CH); s.diskWriteAt(C,D);
                s.get(A,SOURCE); s.put(A,SLOT); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.jump(loop);
                s.label("copy.end"); s.get(A,SOURCE); s.put(A,SLOT); s.diskRead(13,D); s.put(D,CH);
                s.get(A,SECOND); s.put(A,SLOT); s.get(D,CH); s.diskWrite(13,D); s.get(D,LENGTH); s.diskWrite(0,D);
            }
            s.text("OK\n"); s.jump("shell");
        }
        s.label("do.RUN"); s.get(A,SCRIPT_ACTIVE); s.store(1,B); s.eq(A,B,"script.error");
        s.diskRead(13,D); s.store(2,A); s.eq(D,A,"do.IMAGE"); s.store(3,A); s.eq(D,A,"do.PLAY");
        s.diskRead(13,D); s.store(1,A); s.eq(D,A,"script.start");
        s.diskRead(16,D); for(int c=32;c<=90;c++) if(printable(c)) { s.store(c,A); s.eq(D,A,"script.start"); }
        s.diskRead(0,D); s.put(D,LENGTH); s.store(0,A); s.eq(D,A,"invalid.program");
        for(int length=241;length<=255;length++) { s.store(length,A); s.eq(D,A,"invalid.program"); }
        s.set(INDEX,0);
        // Decode the entire flat COM instruction stream before transferring control. The guest
        // validates opcode values, operand counts and register indices using a byte-array table.
        s.label("run.verify"); s.get(A,INDEX); s.get(B,LENGTH); s.eq(A,B,"run.verified");
        s.store(16,B); s.emit(0,A,B,C); s.diskReadAt(C,D);
        for(OPCode opcode:OPCode.values()) { s.store(opcode.code()&255,A); s.eq(D,A,"run.descriptor"); }
        s.jump("invalid.program");
        s.label("run.descriptor"); s.store(5,A); s.emit(20,D,A,C); s.store(64,A); s.emit(0,C,A,C);
        s.put(C,DESCRIPTOR); s.readAt(247,C,D); s.put(D,REMAINING);
        s.label("run.consume"); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX);
        s.get(A,DESCRIPTOR); s.emit(21,A); s.put(A,DESCRIPTOR);
        s.get(A,REMAINING); s.store(1,B); s.emit(1,A,B,A); s.put(A,REMAINING);
        s.store(0,B); s.eq(A,B,"run.verify");
        s.get(A,INDEX); s.get(B,LENGTH); s.eq(A,B,"invalid.program");
        s.get(C,DESCRIPTOR); s.readAt(247,C,D); s.store(0,A); s.eq(D,A,"run.consume");
        s.get(A,INDEX); s.store(16,B); s.emit(0,A,B,C); s.diskReadAt(C,D);
        for(int register=0;register<11;register++) { s.store(register,A); s.eq(D,A,"run.consume"); }
        s.jump("invalid.program");
        s.label("invalid.program"); s.text("INVALID EXECUTABLE\nUSE SAVE FOR BYTECODE\nECHO TEXT RUNS AT THE PROMPT\n"); s.jump("shell");
        s.label("run.verified"); s.set(INDEX,0);
        s.label("run.load"); s.get(A,INDEX); s.get(B,LENGTH); s.eq(A,B,"run.start"); s.store(16,B); s.emit(0,A,B,C); s.diskReadAt(C,D);
        s.get(C,INDEX); s.store(TPA,E); s.emit(22,D,E,C); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.jump("run.load");
        s.label("run.start"); s.store(TPA,G); s.store(0,H); s.emit(10,G,H);
        // Guest COM ABI: D=character, A=return page, B=return address.
        s.label("com.print"); s.put(D,CH); s.put(A,PRINT_RETURN); s.put(B,PRINT_RETURN+1); s.jump("print");

        s.label("parse.name"); s.set(INDEX,0);
        s.label("parse.trim"); s.get(A,ARG); s.get(B,COUNT); s.eq(A,B,"usage");
        s.store(64,B); s.emit(0,A,B,C); s.readAt(LINE,C,D); s.store(32,A); s.ne(D,A,"parse.loop");
        s.get(A,ARG); s.emit(21,A); s.put(A,ARG); s.jump("parse.trim");
        s.label("parse.loop"); s.get(A,ARG); s.get(B,COUNT); s.eq(A,B,"parse.end"); s.store(64,B); s.emit(0,A,B,C); s.readAt(LINE,C,D);
        s.store(32,A); s.eq(D,A,"parse.space"); s.get(A,INDEX); s.store(12,B); s.eq(A,B,"usage");
        s.store(64,B); s.emit(0,A,B,C); s.store(NAME,E); s.emit(22,D,E,C);
        s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.get(A,ARG); s.emit(21,A); s.put(A,ARG); s.jump("parse.loop");
        s.label("parse.space"); s.get(A,ARG); s.emit(21,A); s.put(A,ARG);
        s.label("parse.end"); s.get(A,INDEX); s.store(0,B); s.eq(A,B,"usage");
        s.store(64,B); s.emit(0,A,B,C); s.store(0,D); s.store(NAME,E); s.emit(22,D,E,C); s.ret(SEARCH_RETURN);

        s.label("search"); s.set(FREE,255); s.set(FOUND,255); s.set(SLOT,0);
        s.label("search.slot"); s.diskRead(1,D); s.store(0,A); s.eq(D,A,"search.free"); s.set(INDEX,0);
        s.label("search.char"); s.get(A,INDEX); s.store(64,B); s.emit(0,A,B,C); s.readAt(NAME,C,D); s.put(D,TEMP);
        s.get(A,INDEX); s.store(1,B); s.emit(0,A,B,C); s.diskReadAt(C,D); s.get(A,TEMP); s.ne(D,A,"search.next");
        s.store(0,B); s.eq(A,B,"search.found"); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.store(12,B); s.eq(A,B,"search.found"); s.jump("search.char");
        s.label("search.free"); s.get(A,FREE); s.store(255,B); s.ne(A,B,"search.next"); s.get(A,SLOT); s.put(A,FREE);
        s.label("search.next"); s.get(A,SLOT); s.emit(21,A); s.put(A,SLOT); s.store(16,B); s.ne(A,B,"search.slot"); s.ret(SEARCH_RETURN);
        s.label("search.found"); s.get(A,SLOT); s.put(A,FOUND); s.ret(SEARCH_RETURN);

        s.label("name.write"); s.set(INDEX,0);
        s.label("name.loop"); s.get(A,INDEX); s.store(64,B); s.emit(0,A,B,C); s.readAt(NAME,C,D); s.put(D,CH);
        s.get(A,INDEX); s.store(1,B); s.emit(0,A,B,C); s.get(D,CH); s.diskWriteAt(C,D);
        s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.store(12,B); s.ne(A,B,"name.loop"); s.ret(SEARCH_RETURN);

        // Unsigned decimal display by repeated subtraction, using equality and wrapping bytes.
        s.label("number"); s.set(INDEX,0); s.set(SECOND,0);
        s.label("number.hundreds"); s.get(A,TEMP);
        for(int i=0;i<100;i++) { s.store(i,B); s.eq(A,B,"number.tens.init"); }
        s.store(100,B); s.emit(1,A,B,A); s.put(A,TEMP); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.jump("number.hundreds");
        s.label("number.tens.init"); s.get(A,INDEX); s.store(0,B); s.eq(A,B,"number.tens");
        s.store(48,B); s.emit(0,A,B,D); s.put(D,CH); s.call("print",PRINT_RETURN); s.set(SECOND,1);
        s.label("number.tens"); s.set(INDEX,0);
        s.label("number.ten.loop"); s.get(A,TEMP);
        for(int i=0;i<10;i++) { s.store(i,B); s.eq(A,B,"number.ten.print"); }
        s.store(10,B); s.emit(1,A,B,A); s.put(A,TEMP); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.jump("number.ten.loop");
        s.label("number.ten.print"); s.get(A,INDEX); s.store(0,B); String tens=s.unique("tens"); s.ne(A,B,tens);
        s.get(A,SECOND); s.eq(A,B,"number.ones"); s.label(tens); s.get(A,INDEX); s.store(48,B); s.emit(0,A,B,D); s.put(D,CH); s.call("print",PRINT_RETURN);
        s.label("number.ones"); s.get(A,TEMP); s.store(48,B); s.emit(0,A,B,D); s.put(D,CH); s.call("print",PRINT_RETURN); s.ret(NUMBER_RETURN);

        s.label("message"); s.get(A,MESSAGE_PAGE); s.get(C,MESSAGE_ADDRESS); s.readDynamic(A,C,D);
        s.store(0,A); String messageEnd="message.end"; s.eq(D,A,messageEnd);
        s.put(D,CH); s.call("print",PRINT_RETURN); s.get(A,MESSAGE_ADDRESS); s.emit(21,A); s.put(A,MESSAGE_ADDRESS); s.jump("message");
        s.label(messageEnd); s.ret(MESSAGE_RETURN);

        s.label("print"); s.get(D,CH); s.store(10,A); s.eq(D,A,"newline");
        s.get(A,X); s.get(B,Y); s.store(0,C); s.emit(0,A,C,E); s.emit(0,B,C,F);
        s.get(D,CH); // The reader clobbers E/F, so restore coordinates after reading the character.
        s.get(A,X); s.get(B,Y); s.store(0,C); s.emit(0,A,C,E); s.emit(0,B,C,F);
        s.call("glyph",GLYPH_RETURN); s.get(A,X); s.store(4,B); s.emit(0,A,B,A); s.put(A,X);
        s.store(252,B); s.eq(A,B,"newline"); s.ret(PRINT_RETURN);
        s.label("newline"); s.set(X,4); s.get(A,Y); s.store(8,B); s.emit(0,A,B,A); s.put(A,Y);
        s.store(244,B); String noScroll=s.unique("noscroll"); s.ne(A,B,noScroll);
        // Move the framebuffer up eight rows entirely through READ_GRA_MEM/PUSH_GRA_MEM.
        s.store(0,A); s.store(8,B); s.store(0,C); s.store(248,F);
        s.label("scroll.pixel"); s.emit(15,A,B,D); s.emit(12,A,C,D); s.emit(21,A); s.store(0,E); s.ne(A,E,"scroll.pixel");
        s.emit(21,B); s.emit(21,C); s.ne(C,F,"scroll.pixel"); s.store(0,D);
        s.label("scroll.clear"); s.emit(12,A,C,D); s.emit(21,A); s.ne(A,E,"scroll.clear"); s.emit(21,C); s.ne(C,E,"scroll.clear");
        s.set(Y,236); s.get(A,MODE); s.store(0,B); String editorScroll=s.unique("editor.scroll"); s.ne(A,B,editorScroll);
        s.get(A,BASE_Y); s.store(8,B); s.emit(1,A,B,A); s.put(A,BASE_Y); s.label(editorScroll);
        s.get(A,RENDERING); s.store(1,B); s.ne(A,B,noScroll); s.get(A,TARGET); s.ne(A,B,noScroll);
        s.get(A,CURSOR_Y); s.store(8,B); s.emit(1,A,B,A); s.put(A,CURSOR_Y);
        s.label(noScroll); s.ret(PRINT_RETURN);

        emitEditor(s); emitSourceLanguage(s); emitMedia(s); emitKeyboard(s); emitFontRenderer(s);
        return s.finish();
    }

    private static void emitKeyboard(Asm s) {
        s.label("keyboard.translate");
        for(int c='A';c<='Z';c++) { s.store(c,A); s.eq(D,A,"key"); }
        int[][] mapping={{32,32,32},{44,44,60},{45,45,95},{46,46,62},{47,47,63},
                {48,48,41},{49,49,33},{50,50,64},{51,51,35},{52,52,36},{53,53,37},
                {54,54,94},{55,55,38},{56,56,42},{57,57,40},{59,59,58},{61,61,43},
                {91,91,123},{92,92,124},{93,93,125},{192,96,126},{222,39,34},
                {96,48,48},{97,49,49},{98,50,50},{99,51,51},{100,52,52},{101,53,53},
                {102,54,54},{103,55,55},{104,56,56},{105,57,57},{106,42,42},
                {107,43,43},{108,44,44},{109,45,45},{110,46,46},{111,47,47}};
        for(int[] key:mapping) { s.store(key[0],A); s.eq(D,A,"keyboard."+key[0]); }
        s.jump("poll");
        for(int[] key:mapping) {
            s.label("keyboard."+key[0]); s.get(A,SHIFT); s.store(1,B); String shifted=s.unique("shifted"); s.eq(A,B,shifted);
            s.store(key[1],D); s.jump("key"); s.label(shifted); s.store(key[2],D); s.jump("key");
        }
    }

    /** Small palette images, four-voice MIDI-note streams, and a read-only document pager. */
    private static void emitMedia(Asm s) {
        s.label("do.SHOW"); s.label("do.IMAGE"); s.diskRead(16,D); s.store('J',A); s.ne(D,A,"media.error");
        s.diskRead(17,D); s.store('I',A); s.ne(D,A,"media.error");
        s.diskRead(18,D); s.put(D,IMAGE_WIDTH); String width=s.unique("image.width");
        for(int n=1;n<=15;n++) { s.store(n,A); s.eq(D,A,width); } s.jump("media.error"); s.label(width);
        s.diskRead(19,D); s.put(D,IMAGE_HEIGHT); String height=s.unique("image.height");
        for(int n=1;n<=15;n++) { s.store(n,A); s.eq(D,A,height); } s.jump("media.error"); s.label(height);
        s.get(A,IMAGE_WIDTH); s.get(B,IMAGE_HEIGHT); s.emit(20,A,B,A); s.store(4,B); s.emit(0,A,B,A); s.put(A,LENGTH);
        s.diskRead(0,D); s.get(A,LENGTH); s.ne(D,A,"media.error");
        s.emit(13); s.set(X,4); s.set(Y,4); s.set(INK,28); s.text("IMAGE - ESC TO CLOSE\n");
        s.set(INDEX,4); s.set(IMAGE_ROW,0); s.set(IMAGE_COL,0);
        s.label("image.pixel"); s.get(A,INDEX); s.store(16,B); s.emit(0,A,B,C); s.diskReadAt(C,D); s.put(D,IMAGE_COLOR);
        s.get(A,IMAGE_COL); s.store(8,B); s.emit(20,A,B,A); s.store(68,B); s.emit(0,A,B,A); s.put(A,IMAGE_X);
        s.get(A,IMAGE_ROW); s.store(8,B); s.emit(20,A,B,A); s.store(32,B); s.emit(0,A,B,A); s.put(A,IMAGE_Y);
        s.set(TILE_X,0); s.set(TILE_Y,0);
        s.label("image.tile"); s.get(A,IMAGE_X); s.get(B,TILE_X); s.emit(0,A,B,A); s.put(A,PIXEL_X);
        s.get(A,IMAGE_Y); s.get(B,TILE_Y); s.emit(0,A,B,B); s.get(A,PIXEL_X); s.get(D,IMAGE_COLOR); s.emit(12,A,B,D);
        s.get(A,TILE_X); s.emit(21,A); s.put(A,TILE_X); s.store(8,B); s.ne(A,B,"image.tile");
        s.set(TILE_X,0); s.get(A,TILE_Y); s.emit(21,A); s.put(A,TILE_Y); s.store(8,B); s.ne(A,B,"image.tile");
        s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.get(A,IMAGE_COL); s.emit(21,A); s.put(A,IMAGE_COL); s.get(B,IMAGE_WIDTH); s.ne(A,B,"image.pixel");
        s.set(IMAGE_COL,0); s.get(A,IMAGE_ROW); s.emit(21,A); s.put(A,IMAGE_ROW); s.get(B,IMAGE_HEIGHT); s.ne(A,B,"image.pixel"); s.jump("media.wait");

        s.label("do.PLAY"); s.diskRead(16,D); s.store('J',A); s.ne(D,A,"media.error"); s.diskRead(17,D); s.store('M',A); s.ne(D,A,"media.error");
        s.diskRead(18,D); s.store(1,A); s.ne(D,A,"media.error"); s.diskRead(19,D); s.put(D,LENGTH);
        String count=s.unique("music.count"); for(int n=1;n<=59;n++) { s.store(n,A); s.eq(D,A,count); } s.jump("media.error"); s.label(count);
        s.get(A,LENGTH); s.store(4,B); s.emit(20,A,B,A); s.emit(0,A,B,A); s.put(A,LENGTH); s.diskRead(0,D); s.get(A,LENGTH); s.ne(D,A,"media.error");
        s.text("PLAYING - ESC TO STOP\n"); s.set(INDEX,4); s.silence();
        s.label("music.event"); s.get(A,INDEX); s.get(B,LENGTH); s.eq(A,B,"music.done");
        for(int variable:new int[]{MUSIC_FRAMES,MUSIC_VOICE,MUSIC_NOTE,MUSIC_CONTROL}) {
            s.get(A,INDEX); s.store(16,B); s.emit(0,A,B,C); s.diskReadAt(C,D); s.put(D,variable); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX);
        }
        s.get(A,MUSIC_VOICE); String voice=s.unique("music.voice"); for(int n=0;n<4;n++) { s.store(n,B); s.eq(A,B,voice); } s.jump("media.error"); s.label(voice);
        s.get(A,MUSIC_CONTROL); String control=s.unique("music.control"); for(int n:new int[]{0,128,129,130,131}) { s.store(n,B); s.eq(A,B,control); } s.jump("media.error"); s.label(control);
        s.get(A,MUSIC_VOICE); s.get(B,MUSIC_NOTE); s.get(C,MUSIC_CONTROL); s.emit(17,A,B,C);
        s.label("music.wait"); s.get(A,MUSIC_FRAMES); s.store(0,B); s.eq(A,B,"music.event"); s.emit(14); s.emit(16,0,D); s.store(27,A); s.eq(D,A,"music.done");
        s.get(A,MUSIC_FRAMES); s.store(1,B); s.emit(1,A,B,A); s.put(A,MUSIC_FRAMES); s.jump("music.wait");
        s.label("music.done"); s.silence(); s.text("PLAYBACK FINISHED\n"); s.jump("shell");
        s.label("media.error"); s.silence(); s.text("INVALID MEDIA FILE\nUSE THE HTML DISK TOOL\n"); s.jump("shell");
        s.label("media.wait"); s.emit(14); s.emit(16,0,D); for(int key:new int[]{27,10,81}) { s.store(key,A); s.eq(D,A,"media.close"); } s.jump("media.wait");
        s.label("media.close"); s.emit(13); s.set(X,4); s.set(Y,4); s.jump("shell");

        s.label("do.MORE"); s.label("do.VIEW"); s.diskRead(0,D); s.put(D,LENGTH); s.set(MEDIA_POS,0); s.set(MEDIA_PAGE,0);
        s.store(0,D); s.store(STATE,E); s.store(244,F); s.emit(22,D,E,F);
        s.label("document.page"); s.emit(13); s.set(X,4); s.set(Y,4); s.set(INK,28); s.text("DOCUMENT - UP DOWN ESC\n"); s.set(MEDIA_LINES,0); s.get(A,MEDIA_POS); s.put(A,INDEX);
        s.label("document.char"); s.get(A,INDEX); s.get(B,LENGTH); s.eq(A,B,"document.end");
        s.store(16,B); s.emit(0,A,B,C); s.diskReadAt(C,D); s.put(D,CH); s.call("print",PRINT_RETURN); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX);
        s.get(A,X); s.store(4,B); s.ne(A,B,"document.char"); s.get(A,MEDIA_LINES); s.emit(21,A); s.put(A,MEDIA_LINES); s.store(24,B); s.ne(A,B,"document.char");
        s.label("document.end"); s.get(A,INDEX); s.put(A,MEDIA_NEXT);
        s.label("document.wait"); s.emit(14); s.emit(16,0,D); s.store(27,A); s.eq(D,A,"media.close");
        for(int key:new int[]{40,34,32,10}) { s.store(key,A); s.eq(D,A,"document.down"); }
        for(int key:new int[]{38,33}) { s.store(key,A); s.eq(D,A,"document.up"); } s.store(36,A); s.eq(D,A,"do.VIEW"); s.jump("document.wait");
        s.label("document.down"); s.get(A,MEDIA_NEXT); s.get(B,LENGTH); s.eq(A,B,"document.wait"); s.put(A,MEDIA_POS); s.put(A,TEMP);
        s.get(A,MEDIA_PAGE); s.emit(21,A); s.put(A,MEDIA_PAGE); s.store(244,B); s.emit(0,A,B,C); s.get(D,TEMP); s.store(STATE,E); s.emit(22,D,E,C); s.jump("document.page");
        s.label("document.up"); s.get(A,MEDIA_PAGE); s.store(0,B); s.eq(A,B,"document.wait"); s.store(1,B); s.emit(1,A,B,A); s.put(A,MEDIA_PAGE);
        s.store(244,B); s.emit(0,A,B,C); s.readAt(STATE,C,D); s.put(D,MEDIA_POS); s.jump("document.page");
    }

    private static void emitFontRenderer(Asm s) {
        s.label("highlight"); s.get(A,MODE); s.store(1,B); s.ne(A,B,"highlight.normal");
        s.get(D,CH); s.store(10,A); s.eq(D,A,"highlight.newline");
        s.store(59,A); s.eq(D,A,"highlight.comment"); s.get(A,HILITE); s.store(3,B); s.eq(A,B,"highlight.comment");
        s.store(32,A); s.eq(D,A,"highlight.space");
        s.get(A,HILITE); s.store(0,B); String phase=s.unique("highlight.phase"); s.ne(A,B,phase); s.set(HILITE,1); s.label(phase);
        s.get(A,HILITE); s.store(1,B); s.eq(A,B,"highlight.keyword");
        s.get(D,CH); for(int c='0';c<='9';c++) { s.store(c,A); s.eq(D,A,"highlight.number"); }
        s.jump("highlight.normal");
        s.label("highlight.space"); s.get(A,HILITE); s.store(1,B); s.ne(A,B,"highlight.normal"); s.set(HILITE,2); s.jump("highlight.normal");
        s.label("highlight.newline"); s.set(HILITE,0); s.jump("highlight.normal");
        s.label("highlight.comment"); s.set(HILITE,3); s.set(INK,82); s.ret(HILITE_RETURN);
        s.label("highlight.keyword"); s.set(INK,31); s.ret(HILITE_RETURN);
        s.label("highlight.number"); s.set(INK,124); s.ret(HILITE_RETURN);
        s.label("highlight.normal"); s.set(INK,28); s.ret(HILITE_RETURN);
        s.label("glyph");
        for(int c=32;c<=126;c++) if(printable(c)) { s.store(c,A); s.eq(D,A,"glyph.lookup"); }
        s.store(63,D);
        s.label("glyph.lookup"); s.store(32,A); s.emit(1,D,A,C); s.store(2,B); s.emit(20,C,B,C); s.store(64,A); s.emit(0,C,A,C);
        s.readAt(246,C,D); s.put(D,FONT_PAGE); s.emit(21,C); s.readAt(246,C,D); s.put(D,FONT_OFFSET);
        s.set(FONT_ROW,0); s.set(FONT_COL,0);
        s.label("glyph.pixel"); s.get(A,FONT_PAGE); s.get(C,FONT_OFFSET); s.readDynamic(A,C,D); s.put(D,FONT_PIXEL);
        s.get(A,X); s.get(B,FONT_COL); s.emit(0,A,B,A); s.put(A,PIXEL_X);
        s.get(A,Y); s.get(B,FONT_ROW); s.emit(0,A,B,A); s.put(A,PIXEL_Y);
        s.get(A,INK); s.get(D,FONT_PIXEL); s.emit(20,A,D,D); s.get(A,PIXEL_X); s.get(B,PIXEL_Y); s.emit(12,A,B,D);
        s.get(A,FONT_OFFSET); s.emit(21,A); s.put(A,FONT_OFFSET);
        s.get(A,FONT_COL); s.emit(21,A); s.put(A,FONT_COL); s.store(4,B); s.ne(A,B,"glyph.pixel");
        s.set(FONT_COL,0); s.get(A,FONT_ROW); s.emit(21,A); s.put(A,FONT_ROW); s.store(6,B); s.ne(A,B,"glyph.pixel"); s.ret(GLYPH_RETURN);
    }

    /** The text language uses the existing shell dispatcher and bytecode arithmetic instructions. */
    private static void emitSourceLanguage(Asm s) {
        s.label("cmd.CODEHELP");
        s.text("EDIT DEMO.JVS THEN RUN DEMO.JVS\nPRINT TEXT  BEEP  CLEAR\nSTORE 42 A  OUTPUT A\nADD A B C  SUB A B C\nMULTIPLY A B C  INC A\nEND OR STOP RETURNS TO DOS\nREM TEXT OR ; COMMENT\n");
        s.text("ONE STATEMENT PER LINE\nREGISTERS A TO H WRAP AT 256\nGOTO LINE  IF A VALUE LINE\nLINE NUMBERS START AT 1\nESC ABORTS A RUNNING PROGRAM\nARROWS EDIT  ESC SAVES\nSOUND ON OR SOUND OFF\n"); s.jump("shell");
        s.label("cmd.BEEP"); s.tone(); s.jump("shell");
        s.label("cmd.SOUND"); s.get(A,ARG); s.store(64,B); s.emit(0,A,B,C); s.readAt(LINE,C,D); s.store('O',A); s.ne(D,A,"script.error");
        s.emit(21,C); s.readAt(LINE,C,D); s.store('N',A); s.eq(D,A,"sound.on"); s.store('F',A); s.ne(D,A,"script.error");
        s.set(KEY_SOUND,0); s.text("KEY SOUND OFF\n"); s.jump("shell");
        s.label("sound.on"); s.set(KEY_SOUND,1); s.text("KEY SOUND ON\n"); s.jump("shell");
        s.label("cmd.REM"); s.jump("shell");
        s.label("cmd.END"); s.label("cmd.STOP"); s.set(SCRIPT_ACTIVE,0); s.jump("shell");
        s.label("script.start"); s.get(A,SLOT); s.put(A,SCRIPT_SLOT); s.diskRead(0,D); s.put(D,SCRIPT_SIZE);
        s.set(SCRIPT_INDEX,0); s.set(SCRIPT_ACTIVE,1); s.set(RUN_LINE,0); s.set(MODE,0); s.set(INK,28);
        for(int register=0;register<8;register++) s.set(160+register,0);
        s.jump("script.next");
        s.label("script.next"); s.emit(16,0,D); s.store(27,A); s.eq(D,A,"script.finish");
        s.get(A,RUN_LINE); s.emit(21,A); s.put(A,RUN_LINE);
        s.set(COUNT,0); s.get(A,SCRIPT_SLOT); s.put(A,SLOT);
        s.label("script.read"); s.get(A,SCRIPT_INDEX); s.get(B,SCRIPT_SIZE); s.eq(A,B,"script.last");
        s.store(16,B); s.emit(0,A,B,C); s.diskReadAt(C,D); s.put(D,CH);
        s.get(A,SCRIPT_INDEX); s.emit(21,A); s.put(A,SCRIPT_INDEX); s.get(D,CH);
        s.store(10,A); s.eq(D,A,"script.line"); s.store(13,A); s.eq(D,A,"script.read");
        s.get(A,COUNT); s.store(100,B); s.eq(A,B,"script.error");
        String keep=s.unique("script.keep"); s.store(0,B); s.ne(A,B,keep); s.store(32,B); s.eq(D,B,"script.read");
        s.store(59,B); s.eq(D,B,"script.comment"); s.label(keep);
        s.store(64,B); s.emit(0,A,B,C); s.store(LINE,E); s.emit(22,D,E,C); s.emit(21,A); s.put(A,COUNT); s.jump("script.read");
        s.label("script.comment"); s.get(A,SCRIPT_INDEX); s.get(B,SCRIPT_SIZE); s.eq(A,B,"script.finish");
        s.store(16,B); s.emit(0,A,B,C); s.diskReadAt(C,D); s.get(A,SCRIPT_INDEX); s.emit(21,A); s.put(A,SCRIPT_INDEX);
        s.store(10,A); s.ne(D,A,"script.comment"); s.jump("script.next");
        s.label("script.last"); s.get(A,COUNT); s.store(0,B); s.eq(A,B,"script.finish"); s.jump("enter.command");
        s.label("script.line"); s.get(A,COUNT); s.store(0,B); s.eq(A,B,"script.next"); s.jump("enter.command");
        s.label("script.finish"); s.set(SCRIPT_ACTIVE,0); s.jump("shell");
        s.label("script.error"); s.get(A,SCRIPT_ACTIVE); s.store(1,B); String cliError=s.unique("source.cli.error"); s.ne(A,B,cliError);
        s.set(SCRIPT_ACTIVE,0); s.text("SOURCE ERROR AT LINE "); s.get(D,RUN_LINE); s.put(D,TEMP); s.call("number",NUMBER_RETURN); s.text("\n");
        s.label(cliError); s.text("CHECK SYNTAX WITH CODEHELP\n"); s.jump("shell");

        s.label("cmd.STORE"); s.call("source.number",PARSE_RETURN); s.get(A,NUMBER_VALUE); s.put(A,USER_LEFT);
        s.call("source.register",PARSE_RETURN); s.call("source.end",PARSE_RETURN); s.get(D,USER_LEFT); s.call("source.write",PARSE_RETURN); s.jump("shell");
        for(String command:new String[]{"ADD","SUB","MULTIPLY"}) {
            s.label("cmd."+command); s.call("source.register",PARSE_RETURN); s.call("source.read",PARSE_RETURN); s.put(D,USER_LEFT);
            s.call("source.register",PARSE_RETURN); s.call("source.read",PARSE_RETURN); s.put(D,USER_RIGHT);
            s.call("source.register",PARSE_RETURN); s.call("source.end",PARSE_RETURN); s.get(A,USER_LEFT); s.get(B,USER_RIGHT);
            s.emit(command.equals("ADD")?0:command.equals("SUB")?1:20,A,B,D); s.call("source.write",PARSE_RETURN); s.jump("shell");
        }
        s.label("cmd.INC"); s.call("source.register",PARSE_RETURN); s.call("source.end",PARSE_RETURN); s.call("source.read",PARSE_RETURN); s.emit(21,D); s.call("source.write",PARSE_RETURN); s.jump("shell");
        s.label("cmd.OUTPUT"); s.call("source.register",PARSE_RETURN); s.call("source.end",PARSE_RETURN); s.call("source.read",PARSE_RETURN); s.put(D,TEMP);
        s.call("number",NUMBER_RETURN); s.text("\n"); s.jump("shell");
        s.label("cmd.GOTO"); s.get(A,SCRIPT_ACTIVE); s.store(1,B); s.ne(A,B,"script.error"); s.call("source.number",PARSE_RETURN); s.call("source.end",PARSE_RETURN); s.jump("source.goto");
        s.label("cmd.IF"); s.get(A,SCRIPT_ACTIVE); s.store(1,B); s.ne(A,B,"script.error");
        s.call("source.register",PARSE_RETURN); s.call("source.read",PARSE_RETURN); s.put(D,USER_LEFT);
        s.call("source.number",PARSE_RETURN); s.get(A,NUMBER_VALUE); s.put(A,USER_RIGHT); s.call("source.number",PARSE_RETURN); s.call("source.end",PARSE_RETURN);
        s.get(A,USER_LEFT); s.get(B,USER_RIGHT); s.ne(A,B,"shell");
        s.label("source.goto"); s.get(A,NUMBER_VALUE); s.store(0,B); s.eq(A,B,"script.error");
        s.store(1,B); s.emit(1,A,B,A); s.put(A,RUN_LINE); s.put(A,NUMBER_VALUE); s.set(SCRIPT_INDEX,0);
        s.get(A,SCRIPT_SLOT); s.put(A,SLOT);
        s.label("source.goto.seek"); s.get(A,NUMBER_VALUE); s.store(0,B); s.eq(A,B,"script.next");
        s.get(A,SCRIPT_INDEX); s.get(B,SCRIPT_SIZE); s.eq(A,B,"script.error"); s.store(16,B); s.emit(0,A,B,C); s.diskReadAt(C,D);
        s.get(A,SCRIPT_INDEX); s.emit(21,A); s.put(A,SCRIPT_INDEX); s.store(10,A); s.ne(D,A,"source.goto.seek");
        s.get(A,NUMBER_VALUE); s.store(1,B); s.emit(1,A,B,A); s.put(A,NUMBER_VALUE); s.jump("source.goto.seek");
        s.label("source.register"); s.get(A,ARG); s.get(B,COUNT); s.eq(A,B,"script.error");
        s.store(64,B); s.emit(0,A,B,C); s.readAt(LINE,C,D); s.store(32,A); s.eq(D,A,"source.register.space");
        for(int register=0;register<8;register++) { s.store('A'+register,A); s.eq(D,A,"source.register."+register); }
        s.jump("script.error");
        for(int register=0;register<8;register++) { s.label("source.register."+register); s.set(USER_REG,register); s.get(A,ARG); s.emit(21,A); s.put(A,ARG); s.jump("source.register.done"); }
        s.label("source.register.done"); s.get(A,ARG); s.get(B,COUNT); String registerDone=s.unique("register.done"); s.eq(A,B,registerDone);
        s.store(64,B); s.emit(0,A,B,C); s.readAt(LINE,C,D); s.store(32,A); s.eq(D,A,registerDone); s.store(59,A); s.ne(D,A,"script.error"); s.label(registerDone); s.ret(PARSE_RETURN);
        s.label("source.register.space"); s.get(A,ARG); s.emit(21,A); s.put(A,ARG); s.jump("source.register");
        s.label("source.read"); s.get(A,USER_REG); s.store(224,B); s.emit(0,A,B,C); s.readAt(STATE,C,D); s.ret(PARSE_RETURN);
        s.label("source.write"); s.get(A,USER_REG); s.store(224,B); s.emit(0,A,B,C); s.store(STATE,E); s.emit(22,D,E,C); s.ret(PARSE_RETURN);
        s.label("source.end"); s.get(A,ARG); s.get(B,COUNT); String end=s.unique("source.end"); s.eq(A,B,end);
        s.store(64,B); s.emit(0,A,B,C); s.readAt(LINE,C,D); s.store(59,A); s.eq(D,A,end); s.store(32,A); s.ne(D,A,"script.error");
        s.get(A,ARG); s.emit(21,A); s.put(A,ARG); s.jump("source.end"); s.label(end); s.ret(PARSE_RETURN);
        s.label("source.number"); s.set(NUMBER_VALUE,0); s.set(NUMBER_DIGIT,0);
        s.label("source.number.loop"); s.get(A,ARG); s.get(B,COUNT); s.eq(A,B,"source.number.end");
        s.store(64,B); s.emit(0,A,B,C); s.readAt(LINE,C,D); s.store(32,A); s.eq(D,A,"source.number.space");
        for(int digit=0;digit<10;digit++) { s.store('0'+digit,A); s.eq(D,A,"source.digit."+digit); }
        s.jump("script.error");
        for(int digit=0;digit<10;digit++) { s.label("source.digit."+digit); s.set(REMAINING,digit); s.jump("source.number.add"); }
        s.label("source.number.add"); s.get(A,NUMBER_VALUE); s.store(10,B); s.emit(20,A,B,A); s.get(B,REMAINING); s.emit(0,A,B,A); s.put(A,NUMBER_VALUE); s.set(NUMBER_DIGIT,1);
        s.get(A,ARG); s.emit(21,A); s.put(A,ARG); s.jump("source.number.loop");
        s.label("source.number.space"); s.get(A,ARG); s.emit(21,A); s.put(A,ARG); s.get(A,NUMBER_DIGIT); s.store(0,B); s.eq(A,B,"source.number.loop");
        s.label("source.number.end"); s.get(A,NUMBER_DIGIT); s.store(0,B); s.eq(A,B,"script.error"); s.ret(PARSE_RETURN);
    }

    /** In-place insertion/deletion and cursor navigation, executed entirely as guest bytecode. */
    private static void emitEditor(Asm s) {
        s.label("line.insert"); s.label("edit.insert"); s.editLength(A);
        s.get(D,MODE); s.store(1,B); String editorLimit=s.unique("editor.limit"), room=s.unique("editor.room"); s.eq(D,B,editorLimit);
        s.store(100,B); s.eq(A,B,"poll"); s.jump(room); s.label(editorLimit); s.store(240,B); s.eq(A,B,"poll"); s.label(room);
        s.put(A,INDEX); s.get(B,CURSOR); s.eq(A,B,"insert.append");
        s.label("insert.shift"); s.get(A,INDEX); s.get(B,CURSOR); s.eq(A,B,"insert.character");
        s.store(1,B); s.emit(1,A,B,A); s.put(A,INDEX); s.call("read.character",READ_RETURN);
        s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.call("write.character",READ_RETURN);
        s.get(A,INDEX); s.store(1,B); s.emit(1,A,B,A); s.put(A,INDEX); s.jump("insert.shift");
        s.label("insert.character"); s.get(D,CH); s.call("write.character",READ_RETURN);
        s.editLength(A); s.emit(21,A); s.editPutLength(A); s.get(A,CURSOR); s.emit(21,A); s.put(A,CURSOR); s.jump("redraw");
        s.label("insert.append"); s.get(D,CH); s.call("write.character",READ_RETURN);
        s.editLength(A); s.emit(21,A); s.editPutLength(A); s.get(A,CURSOR); s.emit(21,A); s.put(A,CURSOR);
        s.call("highlight",HILITE_RETURN); s.call("print",PRINT_RETURN); s.set(INK,28); s.jump("poll");
        s.label("line.backspace"); s.get(A,CURSOR); s.store(0,B); s.eq(A,B,"poll");
        s.store(1,B); s.emit(1,A,B,A); s.put(A,CURSOR); s.jump("delete.character");
        s.label("move.127"); s.label("delete.character"); s.get(A,CURSOR); s.editLength(B); s.eq(A,B,"poll"); s.put(A,INDEX);
        s.label("delete.shift"); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.call("read.character",READ_RETURN);
        s.get(A,INDEX); s.store(1,B); s.emit(1,A,B,A); s.put(A,INDEX); s.call("write.character",READ_RETURN);
        s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.editLength(B); s.ne(A,B,"delete.shift");
        s.editLength(A); s.store(1,B); s.emit(1,A,B,A); s.editPutLength(A); s.jump("redraw");
        s.label("move.37"); s.get(A,CURSOR); s.store(0,B); s.eq(A,B,"poll"); s.store(1,B); s.emit(1,A,B,A); s.put(A,CURSOR); s.jump("redraw");
        s.label("move.39"); s.get(A,CURSOR); s.editLength(B); s.eq(A,B,"poll"); s.emit(21,A); s.put(A,CURSOR); s.jump("redraw");
        s.label("move.36"); s.set(CURSOR,0); s.jump("redraw");
        s.label("move.35"); s.editLength(A); s.put(A,CURSOR); s.jump("redraw");
        // UP/DOWN preserve a column, crossing both newlines and 62-column wrapped lines.
        s.label("move.38"); s.set(TEMP,0); s.jump("move.scan");
        s.label("move.40"); s.set(TEMP,1);
        s.label("move.scan"); s.set(INDEX,0); s.set(COLUMN,0); s.set(LINE_START,0);
        s.label("move.prefix"); s.get(A,INDEX); s.get(B,CURSOR); s.eq(A,B,"move.direction");
        s.call("read.character",READ_RETURN); s.store(10,A); s.eq(D,A,"move.prefix.break");
        s.get(A,COLUMN); s.emit(21,A); s.put(A,COLUMN); s.store(62,B); s.ne(A,B,"move.prefix.next");
        s.label("move.prefix.break"); s.set(COLUMN,0); s.get(A,INDEX); s.emit(21,A); s.put(A,LINE_START);
        s.label("move.prefix.next"); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.jump("move.prefix");
        s.label("move.direction"); s.get(A,TEMP); s.store(1,B); s.eq(A,B,"move.down");
        s.get(A,LINE_START); s.store(0,B); s.eq(A,B,"poll"); s.set(INDEX,0); s.set(TARGET,0); s.set(EDIT_INDEX,0);
        s.label("move.previous"); s.call("read.character",READ_RETURN); s.store(10,A); s.eq(D,A,"move.previous.break");
        s.get(A,EDIT_INDEX); s.emit(21,A); s.put(A,EDIT_INDEX); s.store(62,B); s.ne(A,B,"move.previous.next");
        s.label("move.previous.break"); s.get(A,INDEX); s.emit(21,A); s.get(B,LINE_START); s.eq(A,B,"move.target");
        s.put(A,TARGET); s.set(EDIT_INDEX,0);
        s.label("move.previous.next"); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.jump("move.previous");
        s.label("move.down"); s.get(A,CURSOR); s.put(A,INDEX); s.get(A,COLUMN); s.put(A,EDIT_INDEX);
        s.label("move.next"); s.get(A,INDEX); s.editLength(B); s.eq(A,B,"poll"); s.call("read.character",READ_RETURN);
        s.store(10,A); s.eq(D,A,"move.next.break"); s.get(A,EDIT_INDEX); s.emit(21,A); s.put(A,EDIT_INDEX); s.store(62,B); s.ne(A,B,"move.next.advance");
        s.label("move.next.break"); s.get(A,INDEX); s.emit(21,A); s.put(A,TARGET); s.jump("move.target");
        s.label("move.next.advance"); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.jump("move.next");
        s.label("move.target"); s.get(A,TARGET); s.put(A,INDEX); s.set(EDIT_INDEX,0);
        s.label("move.column"); s.get(A,EDIT_INDEX); s.get(B,COLUMN); s.eq(A,B,"move.finish");
        s.get(A,INDEX); s.editLength(B); s.eq(A,B,"move.finish"); s.call("read.character",READ_RETURN);
        s.store(10,A); s.eq(D,A,"move.finish"); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.get(A,EDIT_INDEX); s.emit(21,A); s.put(A,EDIT_INDEX); s.jump("move.column");
        s.label("move.finish"); s.get(A,INDEX); s.put(A,CURSOR); s.jump("redraw");

        s.label("read.character"); s.get(A,MODE); s.store(1,B); s.eq(A,B,"read.editor");
        s.get(A,INDEX); s.store(64,B); s.emit(0,A,B,C); s.readAt(LINE,C,D); s.ret(READ_RETURN);
        s.label("read.editor"); s.get(A,INDEX); s.store(16,B); s.emit(0,A,B,C); s.diskReadAt(C,D); s.ret(READ_RETURN);
        s.label("write.character"); s.get(A,MODE); s.store(1,B); s.eq(A,B,"write.editor");
        s.get(A,INDEX); s.store(64,B); s.emit(0,A,B,C); s.store(LINE,E); s.emit(22,D,E,C); s.ret(READ_RETURN);
        s.label("write.editor"); s.get(A,INDEX); s.store(16,B); s.emit(0,A,B,C); s.diskWriteAt(C,D); s.ret(READ_RETURN);

        s.label("redraw"); s.get(A,MODE); s.store(1,B); s.eq(A,B,"edit.redraw");
        s.get(A,BASE_X); s.put(A,X); s.get(A,BASE_Y); s.put(A,Y); s.jump("redraw.begin");
        s.label("edit.redraw"); s.emit(13); s.set(INK,28); s.set(X,4); s.set(Y,4); s.text("EDIT "); s.set(INDEX,1);
        s.label("editor.title"); s.get(C,INDEX); s.diskReadAt(C,D); s.store(0,A); s.eq(D,A,"editor.title.end");
        s.put(D,CH); s.call("print",PRINT_RETURN); s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.store(13,B); s.ne(A,B,"editor.title");
        s.label("editor.title.end"); s.text("\nARROWS MOVE  TAB INDENT\nESC SAVE  CODEHELP IN DOS\n");
        s.label("redraw.begin"); s.set(RENDERING,1); s.set(HILITE,0); s.set(INDEX,0); s.set(TARGET,0);
        s.label("redraw.loop"); s.get(A,INDEX); s.get(B,CURSOR); String afterCursor=s.unique("after.cursor"); s.ne(A,B,afterCursor);
        s.get(A,X); s.put(A,CURSOR_X); s.get(A,Y); s.put(A,CURSOR_Y); s.set(TARGET,1); s.label(afterCursor);
        s.get(A,INDEX); s.editLength(B); s.eq(A,B,"redraw.end");
        s.get(A,TARGET); s.store(1,B); String render=s.unique("render"); s.ne(A,B,render);
        s.get(A,Y); s.store(236,B); s.eq(A,B,"redraw.restore"); s.label(render);
        s.call("read.character",READ_RETURN); s.put(D,CH); s.call("highlight",HILITE_RETURN); s.call("print",PRINT_RETURN);
        s.get(A,INDEX); s.emit(21,A); s.put(A,INDEX); s.jump("redraw.loop");
        s.label("redraw.end"); s.set(CH,32); s.call("print",PRINT_RETURN);
        s.label("redraw.restore"); s.set(RENDERING,0); s.set(INK,28); s.get(A,CURSOR_X); s.put(A,X); s.get(A,CURSOR_Y); s.put(A,Y);
        s.get(A,ENTER_PENDING); s.store(1,B); s.eq(A,B,"enter.command"); s.jump("poll");
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

    /** Labels, far branches and calls compile exclusively to the existing 0..22 opcodes. */
    private static final class Asm {
        final int first; final List<byte[]> pages=new ArrayList<>(); final Map<String,Integer> labels=new HashMap<>();
        final List<Fix> fixes=new ArrayList<>(); final Map<String,String> messages=new java.util.LinkedHashMap<>();
        final Map<Integer,Integer> fastReads=new java.util.LinkedHashMap<>(); int page,pos,serial;
        record Fix(int page,int offset,String label,boolean high) {}
        Asm(int first) { this.first=first; page=first; pages.add(new byte[256]); }
        byte[] current() { return pages.get(page-first); }
        void reserve(int n) {
            if(pos+n<=247) return;
            raw(2,page+1,G,2,0,H,10,G,H); page++; pos=0; pages.add(new byte[256]);
        }
        void raw(int...bytes) { for(int b:bytes) current()[pos++]=(byte)b; }
        void emit(int...bytes) { reserve(bytes.length); raw(bytes); }
        void label(String name) { if(labels.put(name,page*256+pos)!=null) throw new IllegalStateException("Duplicate "+name); }
        String unique(String prefix) { return prefix+"."+serial++; }
        void store(int value,int register) { emit(2,value,register); }
        void ref(String target,boolean high,int register) { reserve(3); raw(2,0,register); fixes.add(new Fix(page,pos-2,target,high)); }
        void jump(String target) { reserve(9); ref(target,true,G); ref(target,false,H); emit(10,G,H); }
        void eq(int a,int b,String target) { reserve(8); ref(target,true,G); raw(11,G,a,b,0); fixes.add(new Fix(page,pos-1,target,false)); }
        void ne(int a,int b,String target) { String same=unique("same"); eq(a,b,same); jump(target); label(same); }
        void put(int register,int slot) { reserve(10); store(STATE,E); store(slot+64,F); emit(22,register,E,F); }
        void set(int slot,int value) { store(value,D); put(D,slot); }
        void click() {
            get(A,KEY_SOUND); store(0,B); String done=unique("click.off"); eq(A,B,done);
            tone(); label(done);
        }
        void tone() {
            store(0,A); store(84,B); store(131,C); emit(17,A,B,C);
            emit(14); store(0,C); emit(17,A,B,C);
        }
        void silence() { store(0,B); store(0,C); for(int voice=0;voice<4;voice++) { store(voice,A); emit(17,A,B,C); } }
        void cursor(int color) {
            get(A,X); get(B,Y); store(5,C); emit(0,B,C,B); store(color,C);
            for(int i=0;i<3;i++) { emit(12,A,B,C); emit(21,A); }
        }
        void get(int register,int slot) {
            int key=slot*4+register;
            if(!fastReads.containsKey(key) && fastReads.size()<17) fastReads.put(key,24+fastReads.size()*6);
            Integer target=fastReads.get(key);
            if(target==null) { read(STATE,slot+64,register); return; }
            reserve(15); String back=unique("fast.read.return"); ref(back,true,G); ref(back,false,H);
            store(STATE,E); store(target,F); emit(10,E,F); label(back);
        }
        void editLength(int dest) {
            get(D,MODE); store(1,E); String editor=unique("length.editor"), done=unique("length.done"); eq(D,E,editor);
            get(dest,COUNT); jump(done); label(editor); get(dest,LENGTH); label(done);
        }
        void editPutLength(int source) {
            get(D,MODE); store(1,E); String editor=unique("length.editor"), done=unique("length.done"); eq(D,E,editor);
            put(source,COUNT); jump(done); label(editor); put(source,LENGTH); label(done);
        }
        void read(int bank,int slot,int register) { reserve(34); store(slot,H); readAt(bank,H,register); }
        // C is the indirect address; A/B/D survive unless chosen as the destination. E..H are scratch.
        void readAt(int bank,int addressRegister,int register) {
            reserve(31); store(bank,E); store(register*6+2,F); emit(22,addressRegister,E,F);
            String back=unique("read.return"); ref(back,true,G); ref(back,false,H);
            store(bank,E); store(register*6,F); emit(10,E,F); label(back);
        }
        void call(String target,int slot) {
            String back=unique("call.return"); ref(back,true,C); put(C,slot); ref(back,false,C); put(C,slot+1); jump(target); label(back);
        }
        void ret(int slot) { get(A,slot); get(B,slot+1); emit(10,A,B); }
        void text(String text) {
            String name=messages.computeIfAbsent(text,t->unique("message.data"));
            ref(name,true,C); put(C,MESSAGE_PAGE); ref(name,false,C); put(C,MESSAGE_ADDRESS); call("message",MESSAGE_RETURN);
        }
        void readDynamic(int bankRegister,int addressRegister,int dest) {
            reserve(34); store(0,E); emit(0,bankRegister,E,E); store(dest*6+2,F); emit(22,addressRegister,E,F);
            String back=unique("read.return"); ref(back,true,G); ref(back,false,H); store(dest*6,F); emit(10,E,F); label(back);
        }
        void diskRead(int offset,int dest) { store(offset,C); diskReadAt(C,dest); }
        void diskReadAt(int offset,int dest) { get(B,SLOT); store(2,A); emit(18,A,B,offset,dest); }
        void diskWrite(int offset,int value) { store(offset,C); diskWriteAt(C,value); }
        void diskWriteAt(int offset,int value) { get(B,SLOT); store(2,A); emit(19,A,B,offset,value); }
        void diskLiteral(int slot,int offset,int value) { store(2,A); store(slot,B); store(offset,C); store(value,D); emit(19,A,B,C,D); }
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
                if((c-32)%9==0) { page++; pos=24; pages.add(new byte[256]); installReaders(current()); }
                label("font.data."+c); String bits=font((char)c);
                for(int row=0;row<6;row++) for(int col=0;col<4;col++) raw(row<5&&col<3&&bits.charAt(row*3+col)=='1'?1:0);
            }
            if(page>=246) throw new IllegalStateException("Kernel overlaps font pointers: "+page);
            for(Fix fix:fixes) {
                Integer address=labels.get(fix.label); if(address==null) throw new IllegalStateException("Undefined "+fix.label);
                pages.get(fix.page-first)[fix.offset]=(byte)(fix.high?address/256:address%256);
            }
            while(pages.size()+first<=STATE) pages.add(new byte[256]);
            byte[] services=pages.get(249-first);
            for(int i=0;i<2;i++) {
                int address=labels.get(i==0?"com.print":"shell"); int offset=i*9;
                int[] code={2,address/256,G,2,address%256,H,10,G,H};
                for(int j=0;j<code.length;j++) services[offset+j]=(byte)code[j];
            }
            for(int bank:new int[]{246,247,NAME,LINE,STATE}) installReaders(pages.get(bank-first));
            byte[] state=pages.get(STATE-first);
            for(Map.Entry<Integer,Integer> entry:fastReads.entrySet()) {
                int key=entry.getKey(), address=entry.getValue();
                int[] code={7,key%4,key/4+64,10,G,H};
                for(int i=0;i<code.length;i++) state[address+i]=(byte)code[i];
            }
            byte[] pointers=pages.get(246-first);
            for(int c=32;c<=126;c++) {
                int address=labels.get("font.data."+c), offset=64+(c-32)*2;
                pointers[offset]=(byte)(address/256); pointers[offset+1]=(byte)address;
            }
            String[] operands={"rrr","rrr","lr","l","","r","rrl","rl","rl","r","rr","rrrl","rrr","","","rrr","lr","rrr","rrrr","rrrr","rrr","r","rrr"};
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
