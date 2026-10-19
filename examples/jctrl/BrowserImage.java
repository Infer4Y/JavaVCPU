package xyz.ignite4inferneo.jvdos;

import java.nio.file.Files;
import java.nio.file.Path;
import static xyz.ignite4inferneo.OPCode.*;
import static xyz.ignite4inferneo.jvdos.DesktopServices.*;

/** An HTTP browser made exclusively of guest instructions; no host rendering or VM hooks. */
public final class BrowserImage {
    private static final int A=0,B=1,C=2,D=3,E=4,F=5;
    // Page 31 remains the shared spool mailbox; the assembler branches around it.
    // The larger document layout keeps code, text, URLs, links and history separate.
    static final int BUFFER=78, BUFFER_END=81, URL=81, STATE=81;
    private static final int[] UNUSED_STATE={6,7,13,14,15,16,17,19,20,21,24,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,64,65,70,76,83,84,106,107,119,120,121,123,126,127,136,138,139,155,156,157,158,159,160,161,162,163,178,179};
    /** Physical offset of an assembler state slot, also used by the bytecode regression driver. */
    public static int stateOffset(int logical){int offset=128+logical;for(int unused:UNUSED_STATE){if(unused==logical)throw new IllegalArgumentException("Unused state slot "+logical);if(unused<logical)offset--;}if(offset>255)throw new IllegalArgumentException("State overflow");return offset;}
    private static final int LEN=0, INDEX=1, HOST=2, PATH=3, PORT=4, PORT_H=5,
        SIZE=8, SIZE_H=9, POS=10, POS_H=11, SPACE=12, CHAR=18,
        SHIFT=22, MODE=23, MORE=25, READY=26, START=27, START_H=28,
        MESSAGE_PAGE=32, MESSAGE_OFFSET=33, MESSAGE_PART=34,
        MESSAGE_RETURN=40, PRINT_RETURN=42, POLL_RETURN=44,
        STATUS=52, STATUS2=53, STATUS3=54, HEADER_LEN=55, HEADER_FIRST=56,
        TAG=57, HASH=58, NAME_LEN=59, NAME_END=60, CLOSING=61, QUOTE=62, HIDDEN=63,
        ENTITY=66, ENTITY_LEN=67, ENTITY_HASH=68, NUMBER=69,
        BATCH=71, IDLE=72, IDLE_H=73, ROWS=74, SCREEN_COL=75,
        DIGITS=77, WORK=78, WORK_H=79, PORT_DIGIT=80, MULT=81, CARRY=82, LOOK_LEN=85, LOOK_HASH=86, LOOK_PAGE=87, LOOK_OFFSET=88, PLAIN=89, SAVE_N=90, SAVE_O=91, DISK=92, LOAD_SERIAL=93,
        GATEWAY=94, TLS=95, TLEN=96, REDIRS=97, TINDEX=98, OUT=99,
        SRC_PAGE=100, SRC_END=101, SRC_INDEX=102, RESOLVE_RETURN=104, SPAN_RETURN=106,
        LINK_COUNT=108, LINK_PAGE=109, ATTR_STATE=110, ATTR_HASH=111, ATTR_LEN=112, ATTR_QUOTE=113,
        ATTR_VALUE=114, ATTR_OVERFLOW=115, LINK_ACTIVE=116, LINK_CHOICE=117, TAG_LEN=118,
        APPEND_RETURN=120, APPEND_MODE=122, LINK_RETURN=124, NAV_RETURN=126,
        HISTORY_DEPTH=128, HISTORY_NAV=129, BASE_LEN=130, NORM_START=131, NORM_READ=132,
        NORM_END=133, NORM_CHAR=134, SAVED_CHAR=135, LABEL_LEN=136, ORIGIN_SCAN=137, CHUNK=140, CHUNK_LO=141, CHUNK_HI=142,
        CHUNK_DIGITS=143, CHUNK_EXT=144, CHUNK_CR=145, CHUNK_LINE=146, ENCODED=147, GATEWAY_PORT=148, GATEWAY_PORT_H=149, LAST_ERROR=150,
        LINK_OFFSET=151, ACTIVE_PAGE=152, ACTIVE_OFFSET=153, ACTIVE_LIMIT=154, LOCATE_RETURN=156,
        LOWER_RETURN=158, OUT_RETURN=160, ATTR_RETURN=162, ATTR_WANT=164, ATTR_WANT_LEN=165, ATTR_PRESENT=166,
        RECORD_KIND=167, FORM_PAGE=168, FORM_OFFSET=169, FORM_LEN=170, FORM_FIELDS=171, FORM_RETURN=172,
        SELECT_OPEN=174, SELECT_VALUE=175, OPTION_SEEN=176, OPTION_CAPTURE=177, QUERY_RETURN=178,
        CURSOR=180, EDIT_CHAR=181, FORM_EDIT=182, QUERY_ATTR_RETURN=183,
        GROUP_SKIP=185, GROUP_SEEN=186, GROUP_MORE=187, GROUP_NAV=188, OPTION_SPACE=189,
        LINE=0, LINE_CAP=240, HEADER=82, TARGET_URL=83, LINKS=84, LINK_MAX=9, RESOLVED=83+256, BASE_URL=88+256, HISTORY=89;
    private static DesktopImage.Asm s;
    private BrowserImage() { }
    private static void get(int slot,int reg){s.emit(LOAD_MEM_ABSOLUTE,1,STATE,stateOffset(slot),reg);}
    private static void put(int slot,int reg){s.emit(STORE_MEM_ABSOLUTE,reg,1,STATE,stateOffset(slot));}
    private static void set(int slot,int value){s.store(value,D);put(slot,D);}
    private static void clear(int... slots){s.store(0,D);for(int slot:slots)put(slot,D);}
    private static void inc(int slot){get(slot,A);s.emit(INCREMENT_REGISTER,A);put(slot,A);}
    private static void inc16(int low,int high){inc(low);get(low,A);String done=s.unique("inc16");s.notEqual(A,0,done);inc(high);s.label(done);}
    private static void copy(int from,int to){get(from,A);put(to,A);}
    private static void address(int page,int index){s.store(page&255,F);get(index,C);if(page>=256){s.store(128,19);s.emit(ADD,C,19,C);}}
    private static void read(int page,int index,int reg){address(page,index);s.emit(LOAD_MEM_ON_BANK,E,F,C,reg);}
    private static void write(int page,int index,int reg){address(page,index);s.emit(STORE_MEM_ON_BANK,reg,E,F,C);}
    private static void net(int address,int value){s.store(address,A);s.store(value,D);s.emit(WRITE_NETWORK,A,D);}
    private static void call(String target,int slot){
        String back=s.unique("return");
        if(registerReturn(slot)){s.ref(back,true,12);s.ref(back,false,13);}
        else if(pairReturn(slot)){s.ref(back,true,14);s.ref(back,false,15);}
        else {s.ref(back,true,A);put(slot,A);s.ref(back,false,A);put(slot+1,A);}
        s.jump(target);s.label(back);
    }
    private static void ret(int slot){
        if(registerReturn(slot))s.emit(CHANGE_PAGE_JUMP,12,13);
        else if(pairReturn(slot))s.emit(CHANGE_PAGE_JUMP,14,15);
        else {get(slot,A);get(slot+1,B);s.emit(CHANGE_PAGE_JUMP,A,B);}
    }
    private static boolean registerReturn(int slot){return slot==PRINT_RETURN||slot==SPAN_RETURN||slot==APPEND_RETURN||slot==NAV_RETURN||slot==LOWER_RETURN;}
    private static boolean pairReturn(int slot){return slot==MESSAGE_RETURN||slot==POLL_RETURN||slot==LOCATE_RETURN||slot==ATTR_RETURN||slot==QUERY_RETURN||slot==OUT_RETURN;}
    private static void print(){call("print",PRINT_RETURN);}
    private static void syscall(int entry){if(entry!=ENTRY)throw new IllegalArgumentException("Unsupported service entry");call("system",PRINT_RETURN);}
    private static void message(String text,boolean network){
        String label=s.strings.computeIfAbsent(text,t->s.unique("literal"));
        s.ref(label,true,B);s.ref(label,false,C);s.store(network?NETWORK_PACKED:PACKED_TEXT,A);syscall(ENTRY);
    }
    private static void text(String text){message(text,false);}
    private static void send(String text){message(text,true);}
    // Keep N/O available as the receive cursor. Asm.less uses both as scratch;
    // this browser uses C/P instead (call sites do not keep C live across tests).
    private static void less(int register,int literal,String target){s.store(literal,19);s.emit(COMPARE_UNSIGNED,register,19,C);s.equal(C,1,target);}
    private static void lowercase(){call("lowercase",LOWER_RETURN);}
    private static int hash(String text){int n=0;for(char c:text.toCharArray())n=(n*31+c)&255;return n;}
    public static byte[] program(){byte[] bytes=DesktopImage.assembleOptimized(BrowserImage::emit).bytes();if(bytes.length>BUFFER*256)throw new IllegalStateException("Browser overlaps text workspace: "+bytes.length+"; table page="+s.labels.get("tags")/256+"; literal bytes="+s.strings.keySet().stream().mapToInt(t->5*((t.length()+3)/3)).sum());byte[] image=new byte[Math.max(bytes.length,33*256)];java.util.Arrays.fill(image,STOP.code());System.arraycopy(bytes,0,image,0,bytes.length);return image;}
    private static void emit(DesktopImage.Asm assembler){
        s=assembler;s.mailboxHole=true;s.packStrings=true;s.jump("start");
        // The print ABI returns with virtual bank zero selected. Its alias at 248
        // lands on this page-zero trampoline, which restores bank one explicitly.
        s.label("print.return");s.store(1,A);s.emit(CHANGE_BANK_PAGE_JUMP,A,12,13);
        s.label("start");
        // Like Pong, reject fullscreen execution before touching private workspace.
        s.store(255,A);s.store(255,B);s.store(0,C);s.emit(PUSH_GRA_MEM,A,B,C);
        s.store(254,A);s.store(254,B);s.store(127,C);s.emit(PUSH_GRA_MEM,A,B,C);
        s.store(255,A);s.store(255,B);s.emit(READ_GRA_MEM,A,B,D);s.notEqual(D,127,"quit.native");
        s.store(1,E);s.store(DOCUMENT,A);syscall(ENTRY);s.notEqual(A,0,"quit.native");set(LEN,0);set(SHIFT,0);set(READY,0);set(DISK,0);set(LOAD_SERIAL,0);set(GATEWAY,0);set(GATEWAY_PORT,144);set(GATEWAY_PORT_H,31);set(HISTORY_DEPTH,0);set(HISTORY_NAV,0);set(BASE_LEN,0);s.jump("address");
        emitAddress();emitRequest();emitReceive();emitHtml();emitPager();emitRoutines();emitCache();emitNavigation();emitLinks();emitTransfer();emitForms();
        s.label("error");s.store(1,A);s.emit(READ_NETWORK,A,D);put(LAST_ERROR,D);call("cache.abort",MESSAGE_RETURN);net(0,3);set(READY,0);set(MODE,3);s.emit(CLEAR_GRA);text("WEB error U:url R:retry\n");s.jump("wait");
        s.label("https");net(0,3);set(MODE,3);s.emit(CLEAR_GRA);text("HTTPS: G enables TLS gateway; U:URL\n");s.jump("wait");
        s.label("redirect.limit");call("cache.abort",MESSAGE_RETURN);net(0,3);set(READY,0);set(MODE,3);s.emit(CLEAR_GRA);text("Redirect loop / limit (5 hops). U:URL\n");s.jump("wait");
        s.label("url.invalid");call("cache.abort",MESSAGE_RETURN);net(0,3);set(READY,0);set(MODE,3);s.emit(CLEAR_GRA);text("Invalid/long link or form. U:URL\n");s.jump("wait");
        s.label("quit");call("cache.abort",MESSAGE_RETURN);s.label("quit.native");net(0,3);s.store(0,A);s.store(249,B);s.store(9,C);s.emit(CHANGE_BANK_PAGE_JUMP,A,B,C);emitTables();
    }
    private static void emitAddress(){
        s.label("address");set(FORM_EDIT,0);s.label("address.form");call("cache.abort",MESSAGE_RETURN);net(0,3);set(MODE,1);set(HISTORY_NAV,0);copy(LEN,CURSOR);
        s.label("address.draw");s.emit(CLEAR_GRA);get(FORM_EDIT,A);s.equal(A,0,"address.prompt");text("GET form: edit query, Enter submit\n");s.jump("address.controls");s.label("address.prompt");text("Address: Enter go / Home clear\n");s.label("address.controls");text("Left/Right: cursor  End: end\n");get(CURSOR,D);s.equal(D,0,"address.caret");s.store(URL,B);s.store(0,C);s.store(TEXT,A);syscall(ENTRY);
        s.label("address.caret");s.store('|',D);print();get(LEN,A);get(CURSOR,C);s.eq(A,C,"edit");s.emit(SUB,A,C,D);s.store(URL,B);s.store(TEXT,A);syscall(ENTRY);
        s.label("edit");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.equal(D,10,"load");s.equal(D,8,"erase");s.equal(D,36,"address.clear");s.equal(D,37,"address.left");s.equal(D,39,"address.right");s.equal(D,35,"address.end");
        s.store(0,A);s.emit(ADD,D,A,B);get(SHIFT,C);s.store(KEYBOARD,A);syscall(ENTRY);put(SHIFT,C);s.equal(D,0,"edit");
        s.label("insert");put(EDIT_CHAR,D);get(LEN,A);s.equal(A,120,"edit");copy(LEN,INDEX);
        s.label("insert.shift");get(INDEX,A);get(CURSOR,B);s.eq(A,B,"insert.char");s.store(1,B);s.emit(SUB,A,B,A);put(TINDEX,A);read(URL,TINDEX,D);write(URL,INDEX,D);copy(TINDEX,INDEX);s.jump("insert.shift");
        s.label("insert.char");get(EDIT_CHAR,D);write(URL,CURSOR,D);inc(LEN);inc(CURSOR);s.jump("address.draw");
        s.label("erase");get(CURSOR,A);s.equal(A,0,"edit");s.store(1,B);s.emit(SUB,A,B,A);put(CURSOR,A);put(INDEX,A);
        s.label("erase.shift");inc(INDEX);get(INDEX,A);get(LEN,B);s.eq(A,B,"erase.end");read(URL,INDEX,D);get(INDEX,A);s.store(1,B);s.emit(SUB,A,B,A);put(TINDEX,A);write(URL,TINDEX,D);s.jump("erase.shift");
        s.label("erase.end");get(LEN,A);s.store(1,B);s.emit(SUB,A,B,A);put(LEN,A);s.jump("address.draw");
        s.label("address.left");get(CURSOR,A);s.equal(A,0,"edit");s.store(1,B);s.emit(SUB,A,B,A);put(CURSOR,A);s.jump("address.draw");
        s.label("address.right");get(CURSOR,A);get(LEN,B);s.eq(A,B,"edit");inc(CURSOR);s.jump("address.draw");
        s.label("address.end");copy(LEN,CURSOR);s.jump("address.draw");s.label("address.clear");set(LEN,0);set(CURSOR,0);s.jump("address.draw");
    }
    private static void emitTables(){
        String[] tags={"head","script","style","p","div","br","li","ul","ol","tr","table","h1","h2","h3","h4","h5","h6","pre"};
        s.reserve(130);s.labels.put("tags",s.page*256+s.pos+2);
        for(int i=0;i<tags.length;i++)s.emit(STORE_MEM_ABSOLUTE,D,hash(tags[i]),tags[i].length(),i<3?i+1:4);
        s.emit(STORE_MEM_ABSOLUTE,D,0,0,0);
        s.labels.put("entities",s.page*256+s.pos+2);
        String[] entities={"amp","lt","gt","quot","apos","nbsp"};int[] values={'&','<','>','"','\'',32};
        for(int i=0;i<entities.length;i++)s.emit(STORE_MEM_ABSOLUTE,D,hash(entities[i]),entities[i].length(),values[i]);
        s.emit(STORE_MEM_ABSOLUTE,D,0,0,0);
    }
    private static void emitRequest(){
        s.label("load");set(GROUP_SKIP,0);set(GROUP_NAV,0);s.label("load.links");set(REDIRS,0);s.label("request.start");get(LEN,A);s.equal(A,0,"address");inc(LOAD_SERIAL);net(0,4);set(READY,0);set(INDEX,0);set(PORT,80);set(PORT_H,0);set(TLS,0);set(TLEN,0);
        // Prefix reads are length checked: edits may leave old bytes in URL RAM.
        get(LEN,A);less(A,7,"host.start");
        for(int i=0;i<4;i++){String ok=s.unique("scheme.letter");s.emit(LOAD_MEM_ABSOLUTE,1,URL,i,D);s.equal(D,"http".charAt(i),ok);s.notEqual(D,"HTTP".charAt(i),"host.start");s.label(ok);}
        s.emit(LOAD_MEM_ABSOLUTE,1,URL,4,D);s.equal(D,':',"scheme.http");s.equal(D,'s',"scheme.https");s.equal(D,'S',"scheme.https");s.jump("host.start");
        s.label("scheme.http");for(int i=5;i<7;i++){s.emit(LOAD_MEM_ABSOLUTE,1,URL,i,D);s.notEqual(D,'/',"error");}set(INDEX,7);
        s.jump("host.start");
        s.label("scheme.https");get(LEN,A);less(A,8,"error");for(int i=5;i<8;i++){s.emit(LOAD_MEM_ABSOLUTE,1,URL,i,D);s.notEqual(D,i==5?':':'/',"error");}get(GATEWAY,A);s.equal(A,0,"https");set(TLS,1);set(PORT,187);set(PORT_H,1);set(INDEX,8);
        s.label("host.start");copy(INDEX,HOST);
        s.label("host");get(INDEX,A);get(LEN,B);s.eq(A,B,"host.end");read(URL,INDEX,D);s.equal(D,'/',"host.end");s.equal(D,'?',"host.end");s.equal(D,'#',"host.end");s.equal(D,':',"port.start");less(D,33,"error");s.store(2,A);s.emit(WRITE_NETWORK,A,D);inc(INDEX);s.jump("host");
        s.label("port.start");get(INDEX,A);get(HOST,B);s.eq(A,B,"error");inc(INDEX);set(PORT,0);set(PORT_H,0);set(DIGITS,0);
        s.label("port");get(INDEX,A);get(LEN,B);s.eq(A,B,"port.end");read(URL,INDEX,D);for(int ch:new int[]{'/','?','#'})s.equal(D,ch,"port.end");less(D,'0',"error");less(D,':',"port.digit");s.jump("error");
        s.label("port.digit");s.store('0',A);s.emit(SUB,D,A,D);put(PORT_DIGIT,D);inc(DIGITS);get(DIGITS,A);s.equal(A,6,"error");
        // Checked 16-bit x10: both high-byte overflow and low-byte carry matter.
        copy(PORT,WORK);copy(PORT_H,WORK_H);set(PORT,0);set(PORT_H,0);set(MULT,10);
        s.label("port.multiply");get(PORT,A);get(WORK,B);s.emit(ADD,A,B,C);put(PORT,C);s.emit(COMPARE_UNSIGNED,C,A,D);put(CARRY,D);
        get(PORT_H,A);get(WORK_H,B);s.emit(ADD,A,B,C);s.emit(COMPARE_UNSIGNED,C,A,D);s.equal(D,1,"error");get(CARRY,A);s.notEqual(A,1,"port.sum");s.emit(INCREMENT_REGISTER,C);s.equal(C,0,"error");s.label("port.sum");put(PORT_H,C);
        get(MULT,A);s.store(1,B);s.emit(SUB,A,B,A);put(MULT,A);s.notEqual(A,0,"port.multiply");
        get(PORT,A);get(PORT_DIGIT,B);s.emit(ADD,A,B,C);put(PORT,C);s.emit(COMPARE_UNSIGNED,C,A,D);s.notEqual(D,1,"port.next");inc(PORT_H);get(PORT_H,A);s.equal(A,0,"error");s.label("port.next");inc(INDEX);s.jump("port");
        s.label("port.end");get(DIGITS,A);s.equal(A,0,"error");
        s.label("host.end");get(INDEX,A);get(HOST,B);s.eq(A,B,"error");put(PATH,A);get(PORT,A);get(PORT_H,B);s.notEqual(A,0,"port.valid");s.equal(B,0,"error");s.label("port.valid");
        get(GATEWAY,A);s.equal(A,0,"request.direct");net(0,4);for(char ch:"127.0.0.1".toCharArray())net(2,ch);copy(GATEWAY_PORT,PORT);copy(GATEWAY_PORT_H,PORT_H);
        s.label("request.direct");get(PORT_H,D);s.store(3,A);s.emit(WRITE_NETWORK,A,D);get(PORT,D);s.store(4,A);s.emit(WRITE_NETWORK,A,D);net(0,1);
        set(MODE,2);s.emit(CLEAR_GRA);text("WEB: loading...\n");set(IDLE,0);set(IDLE_H,0);
        s.label("connect");call("poll",POLL_RETURN);s.store(0,A);s.emit(READ_NETWORK,A,D);s.equal(D,2,"connected");s.equal(D,6,"connected");less(D,16,"connect");s.jump("error");s.label("connected");
        send("GET ");get(GATEWAY,A);s.equal(A,0,"request.origin.form");get(HOST,A);s.notEqual(A,0,"request.absolute.form");send("http://");s.label("request.absolute.form");set(INDEX,0);s.jump("request.path");
        s.label("request.origin.form");copy(PATH,INDEX);get(INDEX,A);get(LEN,B);s.eq(A,B,"request.slash");read(URL,INDEX,D);s.equal(D,'/',"request.path");
        s.label("request.slash");send("/");
        s.label("request.path");get(INDEX,A);get(LEN,B);s.eq(A,B,"request.headers");read(URL,INDEX,D);s.equal(D,'#',"request.headers");less(D,33,"error");s.store(5,A);s.emit(WRITE_NETWORK,A,D);inc(INDEX);s.jump("request.path");
        s.label("request.headers");send(" HTTP/1.0\r\nHost: ");copy(HOST,INDEX);
        s.label("request.host");get(INDEX,A);get(PATH,B);s.eq(A,B,"request.end");read(URL,INDEX,D);s.store(5,A);s.emit(WRITE_NETWORK,A,D);inc(INDEX);s.jump("request.host");
        s.label("request.end");send("\r\nConnection: close\r\nAccept-Encoding: identity\r\n");send("User-Agent: JCTRL-WEB/0.3\r\n\r\n");net(0,2);
        clear(HEADER_LEN,TAG,HIDDEN,ENTITY,MORE,SIZE,PLAIN,LINK_COUNT,LINK_ACTIVE,APPEND_MODE,TAG_LEN,CHUNK,CHUNK_LO,CHUNK_HI,CHUNK_DIGITS,CHUNK_EXT,CHUNK_CR,CHUNK_LINE,ENCODED,FORM_PAGE,SELECT_OPEN,OPTION_CAPTURE,GROUP_SEEN,GROUP_MORE);set(HEADER_FIRST,1);set(SIZE_H,BUFFER);s.store(0,16);s.store(0,17);s.store(BUFFER,18);set(SPACE,1);set(STATUS,'?');set(STATUS2,'?');set(STATUS3,'?');set(BATCH,0);call("cache.begin",MESSAGE_RETURN);s.jump("receive");
    }
    private static void emitReceive(){
        // DISPLAY is deliberately absent on the byte path. The desktop already
        // preempts the interpreter every 64 instructions; drain 64 bytes per poll.
        s.label("receive");s.store(7,A);s.emit(READ_NETWORK,A,D);s.notEqual(D,0,"receive.byte");s.store(8,A);s.emit(READ_NETWORK,A,D);s.notEqual(D,0,"receive.byte");
        // The peer may close between the empty-count reads and this status read.
        // EOF+RX_READY still has data to drain (TX_BUSY may also remain set).
        s.store(0,A);s.emit(READ_NETWORK,A,D);s.equal(D,20,"receive.byte");s.equal(D,28,"receive.byte");less(D,16,"receive.wait");s.jump("done");
        s.label("receive.wait");call("poll",POLL_RETURN);s.jump("receive");
        s.label("receive.byte");s.store(6,A);s.emit(READ_NETWORK,A,D);put(CHAR,D);
        get(HEADER_FIRST,A);s.equal(A,2,"receive.body");get(CHAR,D);s.equal(D,13,"receive.next");s.equal(D,10,"header.end");get(HEADER_LEN,A);s.equal(A,LINE_CAP,"receive.next");s.store(LINE,B);s.emit(ADD,A,B,C);s.store(HEADER,F);s.emit(STORE_MEM_ON_BANK,D,E,F,C);inc(HEADER_LEN);s.jump("receive.next");
        s.label("header.end");get(HEADER_FIRST,A);s.equal(A,1,"header.status");get(HEADER_LEN,A);s.equal(A,0,"header.done");s.jump("header.classify");
        s.label("header.status");get(HEADER_LEN,A);less(A,12,"error");for(int i=0;i<3;i++){s.emit(LOAD_MEM_ABSOLUTE,1,HEADER,LINE+9+i,D);put(STATUS+i,D);}set(HEADER_FIRST,0);s.jump("header.reset");
        // Recognize text/plain case-insensitively, allowing optional whitespace
        // and a charset suffix. Other content types use the bounded HTML reader.
        s.label("header.classify");get(HEADER_LEN,A);less(A,9,"header.type");s.emit(LOAD_MEM_ABSOLUTE,1,HEADER,8,D);s.notEqual(D,':',"header.type");
        set(INDEX,0);set(HASH,0);s.label("header.location.name");read(HEADER,INDEX,D);lowercase();put(SAVED_CHAR,D);get(HASH,A);s.store(31,B);s.emit(MULTIPLY,A,B,A);get(SAVED_CHAR,D);s.emit(ADD,A,D,A);put(HASH,A);inc(INDEX);get(INDEX,A);s.notEqual(A,9,"header.location.name");get(HASH,A);s.notEqual(A,hash("location:"),"header.type");
        set(INDEX,9);set(TLEN,0);
        s.label("location.space");get(INDEX,A);get(HEADER_LEN,B);s.eq(A,B,"header.reset");read(HEADER,INDEX,D);s.notEqual(D,32,"location.copy");inc(INDEX);s.jump("location.space");
        s.label("location.copy");get(INDEX,A);get(HEADER_LEN,B);s.eq(A,B,"header.reset");read(HEADER,INDEX,D);less(D,33,"header.reset");get(TLEN,A);s.equal(A,120,"location.long");read(HEADER,INDEX,D);write(TARGET_URL,TLEN,D);inc(TLEN);inc(INDEX);s.jump("location.copy");
        s.label("location.long");set(TLEN,255);s.jump("header.reset");
        s.label("header.type");set(INDEX,0);set(HASH,0);set(NAME_LEN,0);
        s.label("header.hash");get(INDEX,A);get(HEADER_LEN,B);s.eq(A,B,"header.check");s.store(LINE,B);s.emit(ADD,A,B,C);s.store(HEADER,F);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);s.equal(D,';',"header.check");less(D,33,"header.advance");lowercase();put(CHAR,D);get(HASH,A);s.store(31,B);s.emit(MULTIPLY,A,B,A);get(CHAR,D);s.emit(ADD,A,D,A);put(HASH,A);inc(NAME_LEN);
        s.label("header.advance");inc(INDEX);s.jump("header.hash");s.label("header.check");get(NAME_LEN,A);s.notEqual(A,"transfer-encoding:chunked".length(),"header.encoding");get(HASH,A);s.notEqual(A,hash("transfer-encoding:chunked"),"header.encoding");set(CHUNK,1);s.jump("header.reset");
        s.label("header.encoding");get(NAME_LEN,A);s.equal(A,"content-encoding:gzip".length(),"header.encoded.check");s.equal(A,"content-encoding:deflate".length(),"header.encoded.check");s.equal(A,"content-encoding:br".length(),"header.encoded.check");s.jump("header.plain");s.label("header.encoded.check");get(HASH,A);for(String value:new String[]{"content-encoding:gzip","content-encoding:deflate","content-encoding:br"})s.equal(A,hash(value),"header.encoded");s.jump("header.plain");s.label("header.encoded");set(ENCODED,1);s.jump("header.reset");
        s.label("header.plain");get(NAME_LEN,A);s.notEqual(A,"content-type:text/plain".length(),"header.reset");get(HASH,A);s.notEqual(A,hash("content-type:text/plain"),"header.reset");set(PLAIN,1);s.jump("header.reset");
        s.label("header.done");get(STATUS,A);s.notEqual(A,'3',"header.accept");get(STATUS2,A);s.notEqual(A,'0',"header.accept");get(STATUS3,A);for(int code:new int[]{'1','2','3','7','8'})s.equal(A,code,"header.redirect");s.jump("header.accept");
        s.label("header.redirect");get(TLEN,A);s.equal(A,0,"header.accept");get(REDIRS,A);s.equal(A,5,"redirect.limit");inc(REDIRS);call("cache.abort",MESSAGE_RETURN);net(0,3);call("url.resolve",RESOLVE_RETURN);s.jump("request.start");
        s.label("header.accept");get(ENCODED,A);s.notEqual(A,0,"encoding.unsupported");call("history.commit",NAV_RETURN);set(HEADER_FIRST,2);s.jump("receive.next");s.label("header.reset");set(HEADER_LEN,0);s.jump("receive.next");
        s.label("receive.body");get(CHUNK,A);s.notEqual(A,0,"chunk.byte");get(CHAR,D);s.jump("html");
        s.label("receive.next");inc(BATCH);get(BATCH,A);s.notEqual(A,64,"receive");set(BATCH,0);set(IDLE,0);set(IDLE_H,0);call("poll",POLL_RETURN);s.jump("receive");
        s.label("done");get(CHUNK,A);s.notEqual(A,0,"error");put(SIZE,17);put(SIZE_H,18);get(HEADER_FIRST,A);s.notEqual(A,2,"error");s.store(1,A);s.emit(READ_NETWORK,A,D);s.notEqual(D,0,"error");net(0,3);call("cache.flush",MESSAGE_RETURN);call("cache.finish",MESSAGE_RETURN);set(MORE,0);get(GROUP_NAV,A);s.equal(A,0,"cache.ready");set(GROUP_NAV,0);set(READY,1);set(START,0);set(START_H,BUFFER);set(LINK_CHOICE,0);s.jump("links.draw");s.label("cache.ready");set(READY,1);set(POS,0);set(POS_H,BUFFER);s.jump("page");
    }
    private static void emitHtml(){
        s.label("html");get(PLAIN,A);s.equal(A,1,"plain");get(TAG,A);s.notEqual(A,0,"html.tag");s.equal(D,'<',"html.open");get(HIDDEN,A);s.notEqual(A,0,"html.return");get(SELECT_OPEN,A);s.notEqual(A,0,"form.option.text");get(ENTITY,A);s.notEqual(A,0,"entity.char");get(CHAR,D);s.equal(D,'&',"entity.open");less(D,33,"html.space");less(D,127,"html.append");s.jump("html.space");
        s.label("plain");get(CHAR,D);s.equal(D,13,"html.return");s.equal(D,10,"html.append");less(D,33,"html.space");less(D,127,"html.append");
        s.label("html.space");s.store(32,D);s.label("html.append");s.jump("append");s.label("html.return");s.jump("receive.next");
        s.label("html.open");set(TAG,1);set(TAG_LEN,0);set(ATTR_OVERFLOW,0);clear(HASH,NAME_LEN,NAME_END,CLOSING,QUOTE,ENTITY);s.jump("html.return");
        s.label("html.tag");get(TAG_LEN,A);s.equal(A,LINE_CAP,"tag.capture.full");get(CHAR,D);write(HEADER,TAG_LEN,D);inc(TAG_LEN);s.jump("tag.capture.done");s.label("tag.capture.full");set(ATTR_OVERFLOW,1);s.label("tag.capture.done");get(QUOTE,A);s.notEqual(A,0,"tag.quoted");get(CHAR,D);s.equal(D,'<',"html.open");s.equal(D,'>',"tag.end");s.equal(D,'\'',"tag.quote");s.equal(D,'\"',"tag.quote");get(NAME_END,A);s.notEqual(A,0,"html.return");get(CHAR,D);less(D,33,"tag.name.end");get(NAME_LEN,A);s.notEqual(A,0,"tag.name");s.equal(D,'/',"tag.closing");
        s.label("tag.name");lowercase();put(CHAR,D);get(NAME_LEN,A);s.equal(A,15,"tag.name.end");inc(NAME_LEN);get(HASH,A);s.store(31,B);s.emit(MULTIPLY,A,B,A);get(CHAR,D);s.emit(ADD,A,D,A);put(HASH,A);
        s.jump("html.return");
        s.label("tag.name.end");set(NAME_END,1);s.jump("html.return");s.label("tag.closing");set(CLOSING,1);s.jump("html.return");
        s.label("tag.quote");put(QUOTE,D);s.jump("html.return");s.label("tag.quoted");get(CHAR,D);s.ne(A,D,"html.return");set(QUOTE,0);s.jump("html.return");
        s.label("tag.end");set(TAG,0);get(NAME_LEN,A);s.notEqual(A,1,"tag.classify");get(HASH,A);s.notEqual(A,'a',"tag.classify");get(CLOSING,A);s.equal(A,0,"tag.anchor.open");set(LINK_ACTIVE,0);s.jump("tag.classify");
        s.label("tag.anchor.open");get(HIDDEN,A);s.notEqual(A,0,"tag.classify");call("link.capture",LINK_RETURN);
        s.label("tag.classify");get(HIDDEN,A);s.notEqual(A,0,"tag.lookup");call("form.tag",FORM_RETURN);s.label("tag.lookup");copy(NAME_LEN,LOOK_LEN);copy(HASH,LOOK_HASH);s.ref("tags",true,A);put(LOOK_PAGE,A);s.ref("tags",false,A);put(LOOK_OFFSET,A);call("lookup",MESSAGE_RETURN);
        get(HIDDEN,A);s.notEqual(A,0,"tag.hidden");s.equal(D,0,"html.return");s.equal(D,4,"html.break");get(CLOSING,A);s.notEqual(A,0,"html.return");put(HIDDEN,D);s.jump("html.return");
        s.label("tag.hidden");s.ne(A,D,"html.return");get(CLOSING,A);s.equal(A,0,"html.return");set(HIDDEN,0);s.jump("html.return");
        s.label("html.break");s.equal(16,0,"html.return");s.store(10,D);s.jump("html.append");
        s.label("entity.open");set(ENTITY,1);set(ENTITY_LEN,0);set(ENTITY_HASH,0);set(NUMBER,0);s.jump("html.return");
        s.label("entity.char");get(CHAR,D);s.equal(D,';',"entity.end");get(ENTITY_LEN,A);s.equal(A,10,"entity.invalid");inc(ENTITY_LEN);get(ENTITY,A);s.equal(A,2,"entity.number");get(CHAR,D);s.equal(D,'#',"entity.numeric");get(ENTITY_HASH,A);s.store(31,B);s.emit(MULTIPLY,A,B,A);s.emit(ADD,A,D,A);put(ENTITY_HASH,A);s.jump("html.return");
        s.label("entity.numeric");get(ENTITY_LEN,A);s.notEqual(A,1,"entity.invalid");set(ENTITY,2);s.jump("html.return");
        s.label("entity.number");get(CHAR,D);less(D,'0',"entity.bad.number");less(D,':',"entity.digit");s.jump("entity.bad.number");
        s.label("entity.digit");s.store('0',A);s.emit(SUB,D,A,D);get(NUMBER,A);less(A,13,"entity.small");s.jump("entity.bad.number");s.label("entity.small");s.store(10,B);s.emit(MULTIPLY,A,B,A);s.emit(ADD,A,D,D);less(D,127,"entity.store");s.jump("entity.bad.number");s.label("entity.store");put(NUMBER,D);s.jump("html.return");s.label("entity.bad.number");set(NUMBER,255);s.jump("html.return");
        s.label("entity.end");get(ENTITY,A);set(ENTITY,0);s.equal(A,2,"entity.output.number");
        copy(ENTITY_LEN,LOOK_LEN);copy(ENTITY_HASH,LOOK_HASH);s.ref("entities",true,A);put(LOOK_PAGE,A);s.ref("entities",false,A);put(LOOK_OFFSET,A);call("lookup",MESSAGE_RETURN);s.equal(D,0,"entity.unknown");s.jump("html.append");
        s.label("entity.output.number");get(NUMBER,D);less(D,32,"entity.unknown");less(D,127,"html.append");s.jump("entity.unknown");
        s.label("entity.unknown");s.store('?',D);s.jump("html.append");s.label("entity.invalid");set(ENTITY,0);s.jump("entity.unknown");
    }
    private static void emitPager(){
        s.label("home");get(DISK,A);s.equal(A,2,"cache.home");get(READY,A);s.equal(A,0,"wait");set(POS,0);set(POS_H,BUFFER);s.jump("page");
        s.label("previous");get(DISK,A);s.equal(A,2,"cache.previous");get(READY,A);s.equal(A,0,"wait");get(START_H,A);less(A,BUFFER+3,"home");s.store(3,B);s.emit(SUB,A,B,A);put(POS_H,A);set(POS,0);s.jump("page");
        s.label("next");get(DISK,A);s.equal(A,2,"cache.next");get(READY,A);s.equal(A,0,"wait");get(POS_H,A);get(SIZE_H,B);s.ne(A,B,"page");get(POS,A);get(SIZE,B);s.ne(A,B,"page");get(MORE,A);s.equal(A,0,"wait");set(READY,0);set(MODE,2);set(SIZE,0);set(SIZE_H,BUFFER);s.store(0,17);s.store(BUFFER,18);s.jump("receive");
        s.label("page");set(MODE,0);copy(POS,START);copy(POS_H,START_H);s.emit(CLEAR_GRA);text("WEB HTTP ");for(int slot:new int[]{STATUS,STATUS2,STATUS3}){get(slot,D);print();}get(GATEWAY,A);s.equal(A,0,"page.transport");text(" [gateway]");s.label("page.transport");text("\n");get(LEN,D);s.store(URL,B);s.store(0,C);s.store(TEXT,A);syscall(ENTRY);text("\n------------------------------------------------\n");set(ROWS,0);set(SCREEN_COL,0);
        // Print at most three 256-byte blocks in native desktop guest code. The
        // shared service validates the span and yields once per block.
        s.label("page.char");get(POS_H,A);get(SIZE_H,B);s.ne(A,B,"page.full.block");get(POS,A);get(SIZE,B);s.eq(A,B,"wait");s.emit(SUB,B,A,D);s.jump("page.block");
        s.label("page.full.block");s.store(0,D);
        s.label("page.block");get(POS_H,B);get(POS,C);s.store(TEXT,A);syscall(ENTRY);s.notEqual(A,0,"error");put(POS,D);s.notEqual(D,0,"wait");inc(POS_H);inc(ROWS);get(ROWS,A);s.notEqual(A,3,"page.char");s.jump("wait");
        s.label("wait");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.equal(D,'U',"address");s.equal(D,'R',"load");s.equal(D,'G',"gateway.toggle");s.equal(D,'B',"history.back");s.equal(D,'L',"links.show");s.equal(D,9,"links.show");get(READY,A);s.equal(A,0,"wait");for(int key:new int[]{'N',32,34,40})s.equal(D,key,"next");for(int key:new int[]{'P',33,38,8})s.equal(D,key,"previous");for(int key:new int[]{'H',36})s.equal(D,key,"home");s.jump("wait");
    }
    private static void emitRoutines(){
        s.label("lowercase");less(D,'A',"lowercase.done");less(D,'Z'+1,"lowercase.letter");s.jump("lowercase.done");s.label("lowercase.letter");s.store(32,A);s.emit(ADD,D,A,D);s.label("lowercase.done");ret(LOWER_RETURN);
        // Two variable-label records per page: URL, label, flag, lengths.
        // Labels use the space after the URL, up to forty characters.
        s.label("history.locate");s.store(HISTORY,F);s.jump("record.locate");s.label("link.locate");s.store(LINKS,F);s.label("record.locate");s.store(0,C);
        s.label("link.locate.loop");less(A,2,"link.locate.end");s.store(2,B);s.emit(SUB,A,B,A);s.emit(INCREMENT_REGISTER,F);s.jump("link.locate.loop");
        s.label("link.locate.end");s.store(128,B);s.emit(MULTIPLY,A,B,C);put(LINK_PAGE,F);put(LINK_OFFSET,C);ret(LOCATE_RETURN);
        s.label("system");s.store(ENTRY,F);put(SAVE_N,17);put(SAVE_O,18);s.store(1,17);s.ref("system.return",true,18);s.ref("system.return",false,19);
        s.reserve(10);s.store(0,6);s.store(249,7);s.emit(CHANGE_BANK_PAGE_JUMP,6,7,F);
        s.label("system.return");get(SAVE_N,17);get(SAVE_O,18);ret(PRINT_RETURN);
        s.label("print");s.store(248,A);s.ref("print.return",false,B);s.store(249,F);s.store(0,C);s.emit(CHANGE_BANK_PAGE_JUMP,C,F,C);
        // Receive cursor and column live in M/N/O rather than guest RAM.
        // The normal character path writes once and never calls DISPLAY/print.
        s.label("append");get(LINK_ACTIVE,A);s.equal(A,0,"append.raw");put(SAVED_CHAR,D);get(APPEND_MODE,A);s.notEqual(A,0,"append.label.done");get(SAVED_CHAR,D);less(D,32,"append.label.done");s.notEqual(D,32,"append.label.visible");get(SPACE,A);s.equal(A,1,"append.label.done");s.label("append.label.visible");get(ACTIVE_PAGE,F);get(ACTIVE_OFFSET,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,A);get(ACTIVE_LIMIT,B);s.eq(A,B,"append.label.done");s.emit(INCREMENT_REGISTER,A);s.emit(STORE_MEM_ON_BANK,A,E,F,C);get(ACTIVE_OFFSET,C);s.store(1,B);s.emit(SUB,C,B,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,B);s.emit(ADD,A,B,A);get(ACTIVE_OFFSET,C);s.store(127,B);s.emit(SUB,C,B,C);s.emit(ADD,C,A,C);s.store(1,B);s.emit(SUB,C,B,C);get(SAVED_CHAR,D);s.emit(STORE_MEM_ON_BANK,D,E,F,C);
        s.label("append.label.done");get(SAVED_CHAR,D);s.label("append.raw");s.equal(D,10,"append.break");s.equal(D,32,"append.space");less(D,33,"append.return");s.store(0,A);put(SPACE,A);s.jump("append.write");
        s.label("append.space");get(SPACE,A);s.equal(A,1,"append.return");s.store(1,A);put(SPACE,A);s.jump("append.write");
        s.label("append.break");s.equal(16,0,"append.return");s.store(32,D);s.emit(STORE_MEM_ON_BANK,D,E,18,17);s.emit(INCREMENT_REGISTER,17);s.notEqual(17,0,"append.break.column");s.emit(INCREMENT_REGISTER,18);s.label("append.break.column");s.emit(INCREMENT_REGISTER,16);s.notEqual(16,48,"append.break");s.store(0,16);s.store(1,A);put(SPACE,A);s.jump("append.return");
        s.label("append.write");s.emit(STORE_MEM_ON_BANK,D,E,18,17);s.emit(INCREMENT_REGISTER,17);s.equal(17,0,"append.write.carry");s.jump("append.column");s.label("append.write.carry");s.emit(INCREMENT_REGISTER,18);
        s.label("append.column");s.emit(INCREMENT_REGISTER,16);s.notEqual(16,48,"append.return");s.store(0,16);s.jump("append.return");

        s.label("append.return");s.equal(18,BUFFER_END,"append.full");get(APPEND_MODE,A);s.equal(A,0,"receive.next");ret(APPEND_RETURN);
        s.label("append.full");put(SIZE,17);put(SIZE_H,18);set(MORE,1);call("cache.flush",MESSAGE_RETURN);get(GROUP_NAV,A);s.equal(A,0,"cache.ready");set(SIZE,0);set(SIZE_H,BUFFER);s.store(0,17);s.store(BUFFER,18);s.jump("receive");
        s.label("lookup");get(LOOK_PAGE,F);get(LOOK_OFFSET,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);s.equal(D,0,"lookup.done");get(LOOK_HASH,A);s.ne(A,D,"lookup.next");s.emit(INCREMENT_REGISTER,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);get(LOOK_LEN,A);s.ne(A,D,"lookup.next");s.emit(INCREMENT_REGISTER,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);s.jump("lookup.done");
        s.label("lookup.next");get(LOOK_OFFSET,A);s.store(5,B);s.emit(ADD,A,B,A);put(LOOK_OFFSET,A);s.jump("lookup");s.label("lookup.done");ret(MESSAGE_RETURN);
        s.label("poll");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.equal(D,'U',"address");s.equal(D,16,"poll.shift.on");s.equal(D,144,"poll.shift.off");s.jump("poll.network");s.label("poll.shift.on");set(SHIFT,1);s.jump("poll.network");s.label("poll.shift.off");set(SHIFT,0);
        s.label("poll.network");s.store(1,A);s.emit(READ_NETWORK,A,D);s.notEqual(D,0,"error");inc16(IDLE,IDLE_H);get(IDLE_H,A);s.equal(A,255,"error");ret(POLL_RETURN);
    }
    /** Thin clients of the shared desktop spool; no private filesystem driver. */
    private static void emitCache(){
        s.label("cache.abort");get(DISK,A);s.equal(A,0,"cache.return");s.store(CACHE_ABORT,A);syscall(ENTRY);put(DISK,D);s.jump("cache.return");
        s.label("cache.begin");s.store(BUFFER,B);s.store(BUFFER_END,C);s.store(CACHE_BEGIN,A);syscall(ENTRY);put(DISK,D);s.jump("cache.return");
        s.label("cache.flush");get(SIZE_H,B);get(SIZE,C);s.store(CACHE_FLUSH,A);syscall(ENTRY);put(DISK,D);s.jump("cache.return");
        s.label("cache.finish");s.store(CACHE_FINISH,A);syscall(ENTRY);put(DISK,D);
        s.label("cache.return");ret(MESSAGE_RETURN);
        for(int action=0;action<3;action++){
            s.label(new String[]{"cache.home","cache.previous","cache.next"}[action]);s.store(action,B);s.jump("cache.navigate");
        }
        s.label("cache.navigate");get(START_H,C);s.store(CACHE_NAVIGATE,A);syscall(ENTRY);s.equal(A,DesktopServices.END,"wait");s.notEqual(A,0,"error");put(DISK,D);s.notEqual(D,2,"error");put(SIZE_H,B);put(SIZE,C);set(MORE,0);s.jump("cache.ready");
    }

    private static void span(int page,int beginSlot,int endSlot){
        set(SRC_PAGE,page);copy(beginSlot,SRC_INDEX);copy(endSlot,SRC_END);call("url.span",SPAN_RETURN);
    }
    private static void spanZero(int page,int endSlot){set(SRC_INDEX,0);set(SRC_PAGE,page);copy(endSlot,SRC_END);call("url.span",SPAN_RETURN);}
    private static void outChar(int ch){s.store(ch,D);call("url.out.char",OUT_RETURN);}
    /** Bounded URI-reference resolution shared by redirects and selected links. */
    private static void emitNavigation(){
        s.label("gateway.toggle");get(GATEWAY,A);s.equal(A,0,"gateway.on");set(GATEWAY,0);s.jump("load");s.label("gateway.on");set(GATEWAY,1);s.jump("load");
        s.label("url.out.char");get(OUT,A);s.equal(A,120,"url.invalid");write(RESOLVED,OUT,D);inc(OUT);ret(OUT_RETURN);
        s.label("url.span");get(SRC_INDEX,A);get(SRC_END,B);s.eq(A,B,"url.span.done");get(OUT,A);s.equal(A,120,"url.invalid");get(SRC_PAGE,F);get(SRC_INDEX,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);less(D,33,"url.invalid");less(D,127,"url.span.write");s.jump("url.invalid");
        s.label("url.span.write");write(RESOLVED,OUT,D);inc(OUT);inc(SRC_INDEX);s.jump("url.span");s.label("url.span.done");ret(SPAN_RETURN);

        s.label("url.resolve");get(TLEN,A);s.equal(A,0,"url.invalid");less(A,121,"url.length.ok");s.jump("url.invalid");s.label("url.length.ok");set(OUT,0);
        less(A,7,"url.reference");
        for(int i=0;i<4;i++){s.emit(LOAD_MEM_ABSOLUTE,1,TARGET_URL,i,D);lowercase();s.notEqual(D,"http".charAt(i),"url.reference");}
        s.emit(LOAD_MEM_ABSOLUTE,1,TARGET_URL,4,D);s.equal(D,':',"url.absolute.http");lowercase();s.notEqual(D,'s',"url.reference");get(TLEN,A);less(A,8,"url.invalid");
        s.emit(LOAD_MEM_ABSOLUTE,1,TARGET_URL,5,D);s.notEqual(D,':',"url.invalid");for(int i=6;i<8;i++){s.emit(LOAD_MEM_ABSOLUTE,1,TARGET_URL,i,D);s.notEqual(D,'/',"url.invalid");}set(ORIGIN_SCAN,8);s.jump("url.absolute");
        s.label("url.absolute.http");for(int i=5;i<7;i++){s.emit(LOAD_MEM_ABSOLUTE,1,TARGET_URL,i,D);s.notEqual(D,'/',"url.invalid");}set(ORIGIN_SCAN,7);
        s.label("url.absolute");spanZero(TARGET_URL,TLEN);s.jump("url.normalize");

        s.label("url.reference");set(TINDEX,0);
        s.label("url.scheme.check");get(TINDEX,A);get(TLEN,B);s.eq(A,B,"url.relative");read(TARGET_URL,TINDEX,D);for(int ch:new int[]{'/','?','#'})s.equal(D,ch,"url.relative");s.equal(D,':',"url.invalid");inc(TINDEX);s.jump("url.scheme.check");
        s.label("url.relative");copy(HOST,ORIGIN_SCAN);s.emit(LOAD_MEM_ABSOLUTE,1,TARGET_URL,0,D);s.notEqual(D,'/',"url.relative.path");get(TLEN,A);s.equal(A,1,"url.root");s.emit(LOAD_MEM_ABSOLUTE,1,TARGET_URL,1,D);s.notEqual(D,'/',"url.root");
        get(HOST,A);s.equal(A,0,"url.default.scheme");s.store(2,B);s.emit(SUB,A,B,A);put(SRC_END,A);spanZero(URL,SRC_END);s.jump("url.reference.copy");
        s.label("url.default.scheme");for(char ch:"http:".toCharArray())outChar(ch);set(ORIGIN_SCAN,7);s.jump("url.reference.copy");
        s.label("url.root");spanZero(URL,PATH);s.jump("url.reference.copy");
        s.label("url.relative.path");copy(PATH,TINDEX);copy(PATH,SRC_END);s.emit(LOAD_MEM_ABSOLUTE,1,TARGET_URL,0,D);s.equal(D,'?',"url.query");s.equal(D,'#',"url.fragment");
        s.label("url.directory");get(TINDEX,A);get(LEN,B);s.eq(A,B,"url.directory.done");read(URL,TINDEX,D);s.equal(D,'?',"url.directory.done");s.equal(D,'#',"url.directory.done");inc(TINDEX);s.notEqual(D,'/',"url.directory");copy(TINDEX,SRC_END);s.jump("url.directory");
        s.label("url.directory.done");spanZero(URL,SRC_END);get(SRC_END,A);get(PATH,B);s.ne(A,B,"url.reference.copy");outChar('/');s.jump("url.reference.copy");
        s.label("url.query");set(ATTR_STATE,0);s.jump("url.base.scan");s.label("url.fragment");set(ATTR_STATE,1);
        s.label("url.base.scan");get(TINDEX,A);get(LEN,B);s.eq(A,B,"url.base.done");read(URL,TINDEX,D);s.equal(D,'#',"url.base.done");get(ATTR_STATE,A);s.notEqual(A,0,"url.base.advance");s.equal(D,'?',"url.base.done");
        s.label("url.base.advance");inc(TINDEX);s.jump("url.base.scan");
        s.label("url.base.done");spanZero(URL,TINDEX);get(TINDEX,A);get(PATH,B);s.ne(A,B,"url.reference.copy");outChar('/');
        s.label("url.reference.copy");spanZero(TARGET_URL,TLEN);

        // Remove dot segments only in the path. Query/fragment bytes stay opaque.
        s.label("url.normalize");copy(ORIGIN_SCAN,TINDEX);copy(OUT,NORM_END);
        s.label("url.origin.scan");get(TINDEX,A);get(NORM_END,B);s.eq(A,B,"url.origin.done");read(RESOLVED,TINDEX,D);for(int ch:new int[]{'/','?','#'})s.equal(D,ch,"url.origin.done");inc(TINDEX);s.jump("url.origin.scan");
        s.label("url.origin.done");copy(TINDEX,NORM_START);set(NORM_READ,0);set(TLEN,0);
        s.label("url.origin.copy");get(NORM_READ,A);get(NORM_START,B);s.eq(A,B,"url.norm.path");read(RESOLVED,NORM_READ,D);write(TARGET_URL,TLEN,D);inc(TLEN);inc(NORM_READ);s.jump("url.origin.copy");
        s.label("url.norm.path");get(NORM_READ,A);get(NORM_END,B);s.eq(A,B,"url.norm.done");read(RESOLVED,NORM_READ,D);put(NORM_CHAR,D);s.equal(D,'?',"url.norm.tail");s.equal(D,'#',"url.norm.tail");s.notEqual(D,'/',"url.norm.emit");copy(NORM_READ,TINDEX);inc(TINDEX);get(TINDEX,A);get(NORM_END,B);s.eq(A,B,"url.norm.emit");read(RESOLVED,TINDEX,D);s.notEqual(D,'.',"url.norm.emit");inc(TINDEX);get(TINDEX,A);get(NORM_END,B);s.eq(A,B,"url.dot");read(RESOLVED,TINDEX,D);for(int ch:new int[]{'/','?','#'})s.equal(D,ch,"url.dot");s.notEqual(D,'.',"url.norm.emit");inc(TINDEX);get(TINDEX,A);get(NORM_END,B);s.eq(A,B,"url.dotdot");read(RESOLVED,TINDEX,D);for(int ch:new int[]{'/','?','#'})s.equal(D,ch,"url.dotdot");s.jump("url.norm.emit");
        s.label("url.dotdot");get(TLEN,A);get(NORM_START,B);s.eq(A,B,"url.dot");s.store(1,B);s.emit(SUB,A,B,A);put(TLEN,A);read(TARGET_URL,TLEN,D);s.notEqual(D,'/',"url.dotdot");
        s.label("url.dot");copy(TINDEX,NORM_READ);get(NORM_READ,A);get(NORM_END,B);s.eq(A,B,"url.dot.slash");read(RESOLVED,NORM_READ,D);s.equal(D,'/',"url.norm.path");
        s.label("url.dot.slash");s.store('/',D);write(TARGET_URL,TLEN,D);inc(TLEN);s.jump("url.norm.path");
        s.label("url.norm.emit");get(NORM_CHAR,D);write(TARGET_URL,TLEN,D);inc(TLEN);inc(NORM_READ);s.jump("url.norm.path");
        s.label("url.norm.tail");read(RESOLVED,NORM_READ,D);write(TARGET_URL,TLEN,D);inc(TLEN);inc(NORM_READ);get(NORM_READ,A);get(NORM_END,B);s.ne(A,B,"url.norm.tail");
        s.label("url.norm.done");copy(TLEN,LEN);set(TINDEX,0);
        s.label("url.apply");get(TINDEX,A);get(LEN,B);s.eq(A,B,"url.applied");read(TARGET_URL,TINDEX,D);write(URL,TINDEX,D);inc(TINDEX);s.jump("url.apply");s.label("url.applied");ret(RESOLVE_RETURN);

        // A bounded three-document back stack, committed after final headers.
        s.label("history.commit");get(HISTORY_NAV,A);s.equal(A,2,"history.save.current");get(BASE_LEN,A);s.equal(A,0,"history.save.current");get(LEN,B);s.ne(A,B,"history.push");set(TINDEX,0);
        s.label("history.compare");get(TINDEX,A);get(LEN,B);s.eq(A,B,"history.save.current");read(URL,TINDEX,D);put(SAVED_CHAR,D);read(BASE_URL,TINDEX,D);get(SAVED_CHAR,A);s.ne(A,D,"history.push");inc(TINDEX);s.jump("history.compare");
        s.label("history.push");get(HISTORY_DEPTH,A);s.notEqual(A,3,"history.push.room");
        for(int index=0;index<2;index++){set(TINDEX,0);s.label("history.shift."+index);read(HISTORY+(index+1)/2+(((index+1)%2)*256),TINDEX,D);write(HISTORY+index/2+((index%2)*256),TINDEX,D);inc(TINDEX);get(TINDEX,A);s.notEqual(A,121,"history.shift."+index);}
        set(HISTORY_DEPTH,2);
        s.label("history.push.room");get(HISTORY_DEPTH,A);call("history.locate",LOCATE_RETURN);set(TINDEX,0);
        s.label("history.push.copy");get(TINDEX,A);get(BASE_LEN,B);s.eq(A,B,"history.push.length");read(BASE_URL,TINDEX,D);get(LINK_PAGE,F);get(TINDEX,A);get(LINK_OFFSET,C);s.emit(ADD,C,A,C);s.emit(STORE_MEM_ON_BANK,D,E,F,C);inc(TINDEX);s.jump("history.push.copy");
        s.label("history.push.length");get(BASE_LEN,D);get(LINK_PAGE,F);get(LINK_OFFSET,C);s.store(120,A);s.emit(ADD,C,A,C);s.emit(STORE_MEM_ON_BANK,D,E,F,C);inc(HISTORY_DEPTH);
        s.label("history.save.current");copy(LEN,BASE_LEN);set(TINDEX,0);
        s.label("history.save.loop");get(TINDEX,A);get(LEN,B);s.eq(A,B,"history.saved");read(URL,TINDEX,D);write(BASE_URL,TINDEX,D);inc(TINDEX);s.jump("history.save.loop");s.label("history.saved");set(HISTORY_NAV,0);ret(NAV_RETURN);
        s.label("history.back");get(HISTORY_DEPTH,A);s.equal(A,0,"wait");s.store(1,B);s.emit(SUB,A,B,A);put(HISTORY_DEPTH,A);call("history.locate",LOCATE_RETURN);get(LINK_OFFSET,C);s.store(120,A);s.emit(ADD,C,A,C);get(LINK_PAGE,F);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);put(LEN,D);set(TINDEX,0);
        s.label("history.restore");get(TINDEX,A);get(LEN,B);s.eq(A,B,"history.restored");get(LINK_PAGE,F);get(TINDEX,A);get(LINK_OFFSET,C);s.emit(ADD,C,A,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);write(URL,TINDEX,D);inc(TINDEX);s.jump("history.restore");s.label("history.restored");set(HISTORY_NAV,2);s.jump("load");
    }

    private static void attribute(String name){set(ATTR_WANT,hash(name));set(ATTR_WANT_LEN,name.length());call("attribute.read",ATTR_RETURN);}
    private static void emitLinks(){
        s.label("link.candidate");get(GROUP_SEEN,A);get(GROUP_SKIP,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(A,255,"link.candidate.skip");inc(GROUP_SEEN);s.equal(C,1,"link.candidate.skip");get(LINK_COUNT,A);s.notEqual(A,LINK_MAX,"link.candidate.yes");set(GROUP_MORE,1);s.label("link.candidate.skip");s.store(0,A);ret(LOCATE_RETURN);s.label("link.candidate.yes");s.store(1,A);ret(LOCATE_RETURN);
        s.label("link.capture");set(LINK_ACTIVE,0);set(RECORD_KIND,0);attribute("href");get(TLEN,A);s.equal(A,0,"link.return");get(ATTR_OVERFLOW,A);s.notEqual(A,0,"link.return");call("link.candidate",LOCATE_RETURN);s.equal(A,0,"link.return");s.jump("link.store");
        s.label("attribute.read");put(SCREEN_COL,16);s.store(1,16);set(ATTR_STATE,0);set(ATTR_VALUE,0);set(TLEN,0);set(ATTR_PRESENT,0);get(ATTR_OVERFLOW,A);s.notEqual(A,0,"attribute.return");
        s.label("link.attr");get(TAG_LEN,B);s.eq(16,B,"attribute.return");s.store(HEADER,F);s.emit(LOAD_MEM_ON_BANK,E,F,16,D);get(ATTR_STATE,A);s.equal(A,1,"link.name");s.equal(A,2,"link.equals");s.equal(A,3,"link.value.start");s.equal(A,4,"link.value");
        less(D,33,"link.advance");s.equal(D,'>',"attribute.return");s.equal(D,'/',"link.advance");set(ATTR_STATE,1);set(ATTR_HASH,0);set(ATTR_LEN,0);s.store(HEADER,F);s.emit(LOAD_MEM_ON_BANK,E,F,16,D);
        s.label("link.name");s.equal(D,'=',"link.name.end");less(D,33,"link.name.end");s.equal(D,'>',"link.name.end");lowercase();put(SAVED_CHAR,D);get(ATTR_HASH,A);s.store(31,B);s.emit(MULTIPLY,A,B,A);get(SAVED_CHAR,D);s.emit(ADD,A,D,A);put(ATTR_HASH,A);inc(ATTR_LEN);s.jump("link.advance");
        s.label("link.name.end");set(ATTR_VALUE,0);get(ATTR_LEN,A);get(ATTR_WANT_LEN,B);s.ne(A,B,"link.name.classified");get(ATTR_HASH,A);get(ATTR_WANT,B);s.ne(A,B,"link.name.classified");set(ATTR_VALUE,1);set(ATTR_PRESENT,1);
        s.label("link.name.classified");set(ATTR_STATE,2);s.store(HEADER,F);s.emit(LOAD_MEM_ON_BANK,E,F,16,D);s.notEqual(D,'=',"link.advance");set(ATTR_STATE,3);s.jump("link.advance");
        s.label("link.equals");less(D,33,"link.advance");s.equal(D,'=',"link.equals.found");get(ATTR_VALUE,A);s.notEqual(A,0,"attribute.return");set(ATTR_STATE,0);s.jump("link.attr");s.label("link.equals.found");set(ATTR_STATE,3);s.jump("link.advance");
        s.label("link.value.start");less(D,33,"link.advance");s.equal(D,'>',"attribute.return");set(ATTR_STATE,4);set(ATTR_QUOTE,0);s.store(HEADER,F);s.emit(LOAD_MEM_ON_BANK,E,F,16,D);s.equal(D,'\'',"link.value.quote");s.equal(D,'"',"link.value.quote");s.jump("link.value");s.label("link.value.quote");put(ATTR_QUOTE,D);s.jump("link.advance");
        s.label("link.value");get(ATTR_QUOTE,A);s.equal(A,0,"link.value.unquoted");s.eq(A,D,"link.value.end");s.jump("link.value.char");
        s.label("link.value.unquoted");s.equal(D,'>',"link.value.end");less(D,33,"link.value.end");
        s.label("link.value.char");get(ATTR_VALUE,A);s.equal(A,0,"link.advance");get(TLEN,A);s.equal(A,120,"link.value.long");s.store(HEADER,F);s.emit(LOAD_MEM_ON_BANK,E,F,16,D);s.notEqual(D,'&',"link.value.write");put(TINDEX,16);
        for(char ch:"amp;".toCharArray()){inc(TINDEX);get(TINDEX,A);get(TAG_LEN,B);s.eq(A,B,"link.value.raw");read(HEADER,TINDEX,D);s.notEqual(D,ch,"link.value.raw");}get(TINDEX,16);s.store('&',D);s.jump("link.value.write");s.label("link.value.raw");s.store(HEADER,F);s.emit(LOAD_MEM_ON_BANK,E,F,16,D);s.label("link.value.write");write(TARGET_URL,TLEN,D);inc(TLEN);s.jump("link.advance");s.label("link.value.long");set(ATTR_OVERFLOW,1);s.jump("link.advance");
        s.label("link.value.end");get(ATTR_VALUE,A);s.notEqual(A,0,"attribute.return");
        s.label("link.value.skip");set(ATTR_STATE,0);s.store(HEADER,F);s.emit(LOAD_MEM_ON_BANK,E,F,16,D);s.equal(D,'>',"attribute.return");
        s.label("link.advance");s.emit(INCREMENT_REGISTER,16);s.jump("link.attr");
        s.label("attribute.return");get(SCREEN_COL,16);ret(ATTR_RETURN);
        s.label("link.store");get(LINK_COUNT,A);call("link.locate",LOCATE_RETURN);set(TINDEX,0);
        s.label("link.store.loop");get(TINDEX,A);get(TLEN,B);s.eq(A,B,"link.stored");read(TARGET_URL,TINDEX,D);get(LINK_PAGE,F);get(TINDEX,A);get(LINK_OFFSET,C);s.emit(ADD,C,A,C);s.emit(STORE_MEM_ON_BANK,D,E,F,C);inc(TINDEX);s.jump("link.store.loop");
        s.label("link.stored");get(LINK_PAGE,F);put(ACTIVE_PAGE,F);get(LINK_OFFSET,C);s.store(125,A);s.emit(ADD,C,A,C);get(RECORD_KIND,D);s.emit(STORE_MEM_ON_BANK,D,E,F,C);s.emit(INCREMENT_REGISTER,C);get(TLEN,D);s.emit(STORE_MEM_ON_BANK,D,E,F,C);s.store(0,D);s.emit(INCREMENT_REGISTER,C);s.emit(STORE_MEM_ON_BANK,D,E,F,C);put(ACTIVE_OFFSET,C);get(TLEN,A);s.store(125,B);s.emit(SUB,B,A,A);less(A,41,"link.label.limit");s.store(40,A);s.label("link.label.limit");put(ACTIVE_LIMIT,A);inc(LINK_COUNT);copy(LINK_COUNT,LINK_ACTIVE);
        // Leave a marker only when its complete output fits in this RAM screen.
        s.notEqual(18,BUFFER_END-1,"link.marker");less(17,253,"link.marker");s.jump("link.return");
        s.label("link.marker");set(APPEND_MODE,1);s.store('[',D);call("append",APPEND_RETURN);get(LINK_COUNT,D);s.store('0',A);s.emit(ADD,D,A,D);call("append",APPEND_RETURN);s.store(']',D);call("append",APPEND_RETURN);set(APPEND_MODE,0);
        s.label("link.return");ret(LINK_RETURN);

        s.label("links.show");get(READY,A);s.equal(A,0,"wait");get(LINK_COUNT,A);s.equal(A,0,"wait");set(LINK_CHOICE,0);
        s.label("links.draw");set(MODE,4);s.emit(CLEAR_GRA);text("Links: Tab/Up/Down choose; Enter open\nLeft/Right: groups (reload) L:page B:back\n");set(INDEX,0);
        s.label("links.row");get(INDEX,A);get(LINK_COUNT,B);s.eq(A,B,"links.target");get(LINK_CHOICE,B);s.ne(A,B,"links.row.other");s.store('>',D);s.jump("links.row.marker");s.label("links.row.other");s.store(' ',D);s.label("links.row.marker");print();get(INDEX,A);s.store('1',B);s.emit(ADD,A,B,D);print();text(" ");
        get(INDEX,A);call("link.locate",LOCATE_RETURN);get(LINK_PAGE,B);get(LINK_OFFSET,C);s.store(127,A);s.emit(ADD,C,A,C);s.emit(LOAD_MEM_ON_BANK,E,B,C,D);s.equal(D,0,"links.row.url");s.emit(INCREMENT_REGISTER,C);s.store(2,A);s.emit(SUB,C,A,C);s.emit(LOAD_MEM_ON_BANK,E,B,C,A);get(LINK_OFFSET,C);s.emit(ADD,C,A,C);s.jump("links.row.text");
        s.label("links.row.url");get(LINK_OFFSET,C);s.store(126,A);s.emit(ADD,C,A,C);s.emit(LOAD_MEM_ON_BANK,E,B,C,D);less(D,41,"links.row.short");s.store(40,D);s.label("links.row.short");get(LINK_OFFSET,C);
        s.label("links.row.text");s.store(TEXT,A);syscall(ENTRY);text("\n");inc(INDEX);s.jump("links.row");
        s.label("links.target");get(LINK_COUNT,A);s.equal(A,0,"links.keys");text("\nTarget: ");get(LINK_CHOICE,A);call("link.locate",LOCATE_RETURN);get(LINK_PAGE,B);get(LINK_OFFSET,C);s.store(126,A);s.emit(ADD,C,A,C);s.emit(LOAD_MEM_ON_BANK,E,B,C,D);get(LINK_OFFSET,C);s.store(TEXT,A);syscall(ENTRY);
        s.label("links.keys");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.equal(D,'L',"links.page");s.equal(D,'B',"history.back");s.equal(D,'G',"gateway.toggle");s.equal(D,'U',"address");s.equal(D,39,"links.group.next");s.equal(D,37,"links.group.previous");s.equal(D,10,"links.open");for(int key:new int[]{9,40,'N'})s.equal(D,key,"links.select.next");for(int key:new int[]{38,'P'})s.equal(D,key,"links.select.previous");less(D,'1',"links.keys");less(D,'1'+LINK_MAX,"links.digit");s.jump("links.keys");
        s.label("links.group.next");get(GROUP_MORE,A);s.notEqual(A,0,"links.group.advance");get(MORE,A);s.equal(A,0,"links.keys");s.label("links.group.advance");get(GROUP_SKIP,A);less(A,247,"links.group.room");s.jump("links.keys");s.label("links.group.room");s.store(9,B);s.emit(ADD,A,B,A);put(GROUP_SKIP,A);s.jump("links.group.load");
        s.label("links.group.previous");get(GROUP_SKIP,A);s.equal(A,0,"links.keys");s.store(9,B);s.emit(SUB,A,B,A);put(GROUP_SKIP,A);s.label("links.group.load");set(GROUP_NAV,1);s.jump("load.links");
        s.label("links.digit");s.store('1',A);s.emit(SUB,D,A,A);get(LINK_COUNT,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.notEqual(C,1,"links.keys");put(LINK_CHOICE,A);s.jump("links.open");
        s.label("links.select.next");get(LINK_COUNT,A);s.equal(A,0,"links.keys");inc(LINK_CHOICE);get(LINK_CHOICE,A);get(LINK_COUNT,B);s.ne(A,B,"links.draw");set(LINK_CHOICE,0);s.jump("links.draw");
        s.label("links.select.previous");get(LINK_COUNT,A);s.equal(A,0,"links.keys");get(LINK_CHOICE,A);s.notEqual(A,0,"links.select.decrement");get(LINK_COUNT,A);s.label("links.select.decrement");s.store(1,B);s.emit(SUB,A,B,A);put(LINK_CHOICE,A);s.jump("links.draw");
        s.label("links.page");copy(START,POS);copy(START_H,POS_H);s.jump("page");
        s.label("links.open");get(LINK_CHOICE,A);get(LINK_COUNT,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.notEqual(C,1,"links.keys");get(LINK_CHOICE,A);call("link.locate",LOCATE_RETURN);get(LINK_OFFSET,C);s.store(126,A);s.emit(ADD,C,A,C);get(LINK_PAGE,F);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);put(TLEN,D);set(TINDEX,0);
        s.label("links.copy");get(TINDEX,A);get(TLEN,B);s.eq(A,B,"links.follow");get(LINK_PAGE,F);get(TINDEX,A);get(LINK_OFFSET,C);s.emit(ADD,C,A,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);write(TARGET_URL,TINDEX,D);inc(TINDEX);s.jump("links.copy");
        s.label("links.follow");get(LINK_PAGE,F);get(LINK_OFFSET,C);s.store(125,A);s.emit(ADD,C,A,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);less(D,2,"links.valid");s.jump("url.invalid");s.label("links.valid");put(FORM_EDIT,D);call("cache.abort",MESSAGE_RETURN);net(0,3);set(HISTORY_NAV,0);call("url.resolve",RESOLVE_RETURN);get(FORM_EDIT,A);s.notEqual(A,0,"address.form");s.jump("load");
    }

    /** Basic GET forms share the link records and the address/query editor. */
    private static void emitForms(){
        s.label("form.tag");get(NAME_LEN,A);s.notEqual(A,4,"form.tag.input");get(HASH,A);s.equal(A,hash("form"),"form.begin");
        s.label("form.tag.input");get(FORM_PAGE,A);s.equal(A,0,"form.return");get(NAME_LEN,A);s.notEqual(A,5,"form.tag.select");get(HASH,A);s.equal(A,hash("input"),"form.input");
        s.label("form.tag.select");get(NAME_LEN,A);s.notEqual(A,6,"form.return");get(HASH,A);s.equal(A,hash("select"),"form.select");s.equal(A,hash("option"),"form.option");s.jump("form.return");
        s.label("form.begin");get(CLOSING,A);s.notEqual(A,0,"form.close");set(FORM_PAGE,0);set(SELECT_OPEN,0);set(OPTION_CAPTURE,0);get(ATTR_OVERFLOW,A);s.notEqual(A,0,"form.return");attribute("method");get(TLEN,A);s.equal(A,0,"form.get");s.notEqual(A,3,"form.return");for(int i=0;i<3;i++){s.emit(LOAD_MEM_ABSOLUTE,1,TARGET_URL,i,D);lowercase();s.notEqual(D,"get".charAt(i),"form.return");}
        s.label("form.get");attribute("action");get(TLEN,A);s.notEqual(A,0,"form.action");copy(LEN,TLEN);set(TINDEX,0);
        s.label("form.default.action");get(TINDEX,A);get(LEN,B);s.eq(A,B,"form.action");read(URL,TINDEX,D);write(TARGET_URL,TINDEX,D);inc(TINDEX);s.jump("form.default.action");
        s.label("form.action");set(TINDEX,0);
        s.label("form.action.scan");get(TINDEX,A);get(TLEN,B);s.eq(A,B,"form.action.ready");read(TARGET_URL,TINDEX,D);s.equal(D,'?',"form.action.trim");s.equal(D,'#',"form.action.trim");inc(TINDEX);s.jump("form.action.scan");s.label("form.action.trim");copy(TINDEX,TLEN);
        s.label("form.action.ready");call("link.candidate",LOCATE_RETURN);s.equal(A,0,"form.return");set(RECORD_KIND,3);call("link.store",LINK_RETURN);get(LINK_PAGE,A);put(FORM_PAGE,A);copy(LINK_OFFSET,FORM_OFFSET);copy(TLEN,FORM_LEN);set(FORM_FIELDS,0);set(LINK_ACTIVE,0);s.jump("form.return");
        s.label("form.close");get(FORM_PAGE,F);s.equal(F,0,"form.return");get(FORM_OFFSET,C);s.store(125,A);s.emit(ADD,C,A,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);s.notEqual(D,3,"form.close.done");s.store(1,D);s.emit(STORE_MEM_ON_BANK,D,E,F,C);s.label("form.close.done");set(FORM_PAGE,0);set(SELECT_OPEN,0);set(OPTION_CAPTURE,0);s.jump("form.return");
        s.label("form.input");attribute("disabled");get(ATTR_PRESENT,A);s.notEqual(A,0,"form.return");attribute("type");get(TLEN,A);s.equal(A,0,"form.field");s.emit(LOAD_MEM_ABSOLUTE,1,TARGET_URL,0,D);lowercase();for(int ch:new int[]{'t','s','h'})s.equal(D,ch,"form.field");s.jump("form.return");
        s.label("form.select");set(OPTION_CAPTURE,0);get(CLOSING,A);s.equal(A,0,"form.select.open");set(SELECT_OPEN,0);s.jump("form.return");s.label("form.select.open");attribute("disabled");get(ATTR_PRESENT,A);s.notEqual(A,0,"form.return");set(SELECT_OPEN,1);set(OPTION_SEEN,0);
        s.label("form.field");attribute("name");get(TLEN,A);s.equal(A,0,"form.field.missing");get(FORM_FIELDS,A);s.equal(A,0,"form.field.first");s.store('&',D);s.jump("form.field.separator");s.label("form.field.first");s.store('?',D);
        s.label("form.field.separator");call("query.raw",QUERY_RETURN);inc(FORM_FIELDS);call("query.attribute",QUERY_ATTR_RETURN);s.store('=',D);call("query.raw",QUERY_RETURN);get(SELECT_OPEN,A);s.equal(A,0,"form.field.value");copy(FORM_LEN,SELECT_VALUE);s.jump("form.return");
        s.label("form.field.value");attribute("value");call("query.attribute",QUERY_ATTR_RETURN);s.jump("form.return");
        s.label("form.field.missing");clear(SELECT_OPEN,OPTION_CAPTURE);s.jump("form.return");
        s.label("form.option");set(OPTION_CAPTURE,0);get(CLOSING,A);s.notEqual(A,0,"form.return");get(SELECT_OPEN,A);s.equal(A,0,"form.return");attribute("selected");get(ATTR_PRESENT,A);s.notEqual(A,0,"form.option.use");get(OPTION_SEEN,A);s.notEqual(A,0,"form.return");
        s.label("form.option.use");set(OPTION_SPACE,0);set(OPTION_SEEN,1);copy(SELECT_VALUE,FORM_LEN);call("query.length",LOWER_RETURN);attribute("value");get(ATTR_PRESENT,A);s.equal(A,0,"form.option.capture");call("query.attribute",QUERY_ATTR_RETURN);s.jump("form.return");s.label("form.option.capture");set(OPTION_CAPTURE,1);s.jump("form.return");
        s.label("form.option.text");get(OPTION_CAPTURE,A);s.equal(A,0,"html.return");get(CHAR,D);less(D,33,"form.option.space");less(D,127,"form.option.char");s.jump("html.return");s.label("form.option.space");get(FORM_LEN,A);get(SELECT_VALUE,B);s.eq(A,B,"html.return");set(OPTION_SPACE,1);s.jump("html.return");s.label("form.option.char");get(OPTION_SPACE,A);s.equal(A,0,"form.option.emit");set(OPTION_SPACE,0);s.store(32,D);call("query.char",QUERY_RETURN);s.label("form.option.emit");get(CHAR,D);call("query.char",QUERY_RETURN);s.jump("html.return");
        s.label("form.return");ret(FORM_RETURN);
        // The outer return stays in RAM; nested encoder calls use register pairs.
        s.label("query.attribute");set(TINDEX,0);
        s.label("query.attribute.loop");get(TINDEX,A);get(TLEN,B);s.eq(A,B,"query.attribute.end");read(TARGET_URL,TINDEX,D);call("query.char",QUERY_RETURN);inc(TINDEX);s.jump("query.attribute.loop");s.label("query.attribute.end");ret(QUERY_ATTR_RETURN);
        s.label("query.char");s.equal(D,32,"query.space");for(int ch:new int[]{'-','_','.','~'})s.equal(D,ch,"query.raw");less(D,'0',"query.escape");less(D,':',"query.raw");less(D,'A',"query.escape");less(D,'Z'+1,"query.raw");less(D,'a',"query.escape");less(D,'z'+1,"query.raw");
        s.label("query.escape");put(EDIT_CHAR,D);s.store('%',D);call("query.byte",LOWER_RETURN);get(EDIT_CHAR,A);s.store(0,B);
        s.label("query.hex.divide");less(A,16,"query.hex.first");s.store(16,C);s.emit(SUB,A,C,A);s.emit(INCREMENT_REGISTER,B);s.jump("query.hex.divide");s.label("query.hex.first");put(WORK,A);s.store(0,A);s.emit(ADD,B,A,D);call("query.hex",LOWER_RETURN);get(WORK,D);call("query.hex",LOWER_RETURN);ret(QUERY_RETURN);
        s.label("query.hex");less(D,10,"query.hex.number");s.store('A'-10,A);s.jump("query.hex.emit");s.label("query.hex.number");s.store('0',A);s.label("query.hex.emit");s.emit(ADD,D,A,D);s.jump("query.byte");
        s.label("query.space");s.store('+',D);s.label("query.raw");call("query.byte",LOWER_RETURN);ret(QUERY_RETURN);
        s.label("query.byte");get(FORM_LEN,A);s.equal(A,120,"query.overflow");get(FORM_OFFSET,C);s.emit(ADD,C,A,C);get(FORM_PAGE,F);s.emit(STORE_MEM_ON_BANK,D,E,F,C);inc(FORM_LEN);s.label("query.length");get(FORM_PAGE,F);get(FORM_OFFSET,C);s.store(126,A);s.emit(ADD,C,A,C);get(FORM_LEN,D);s.emit(STORE_MEM_ON_BANK,D,E,F,C);ret(LOWER_RETURN);
        s.label("query.overflow");get(FORM_PAGE,F);get(FORM_OFFSET,C);s.store(125,A);s.emit(ADD,C,A,C);s.store(2,D);s.emit(STORE_MEM_ON_BANK,D,E,F,C);ret(LOWER_RETURN);
    }

    private static void emitTransfer(){
        s.label("encoding.unsupported");call("cache.abort",MESSAGE_RETURN);net(0,3);set(READY,0);set(MODE,3);s.emit(CLEAR_GRA);text("Compressed: G enables gateway; U:URL\n");s.jump("wait");
        s.label("chunk.byte");get(CHUNK,A);s.equal(A,1,"chunk.size");s.equal(A,2,"chunk.data");s.equal(A,3,"chunk.delimiter");
        // Consume trailers until the final empty line, without rendering them.
        get(CHAR,D);s.equal(D,13,"receive.next");s.equal(D,10,"chunk.trailer.end");set(CHUNK_LINE,1);s.jump("receive.next");
        s.label("chunk.trailer.end");get(CHUNK_LINE,A);s.equal(A,0,"chunk.finished");set(CHUNK_LINE,0);s.jump("receive.next");
        s.label("chunk.finished");set(CHUNK,0);s.jump("done");
        s.label("chunk.size");get(CHAR,D);s.equal(D,13,"receive.next");s.equal(D,10,"chunk.size.end");get(CHUNK_EXT,A);s.notEqual(A,0,"receive.next");get(CHAR,D);s.equal(D,';',"chunk.extension");less(D,'0',"error");less(D,':',"chunk.decimal");lowercase();less(D,'a',"error");less(D,'g',"chunk.hex");s.jump("error");
        s.label("chunk.decimal");s.store('0',A);s.emit(SUB,D,A,D);s.jump("chunk.digit");s.label("chunk.hex");s.store('a'-10,A);s.emit(SUB,D,A,D);
        s.label("chunk.digit");put(PORT_DIGIT,D);inc(CHUNK_DIGITS);get(CHUNK_DIGITS,A);s.equal(A,5,"error");get(CHUNK_HI,A);less(A,16,"chunk.multiply");s.jump("error");s.label("chunk.multiply");s.store(16,B);s.emit(MULTIPLY,A,B,A);put(CHUNK_HI,A);get(CHUNK_LO,A);s.store(0,B);
        s.label("chunk.carry");less(A,16,"chunk.carry.done");s.store(16,C);s.emit(SUB,A,C,A);s.emit(INCREMENT_REGISTER,B);s.jump("chunk.carry");
        s.label("chunk.carry.done");put(WORK,A);get(CHUNK_HI,C);s.emit(ADD,C,B,C);put(CHUNK_HI,C);get(WORK,A);s.store(16,B);s.emit(MULTIPLY,A,B,A);get(PORT_DIGIT,B);s.emit(ADD,A,B,A);put(CHUNK_LO,A);s.jump("receive.next");
        s.label("chunk.extension");get(CHUNK_DIGITS,A);s.equal(A,0,"error");set(CHUNK_EXT,1);s.jump("receive.next");
        s.label("chunk.size.end");get(CHUNK_DIGITS,A);s.equal(A,0,"error");get(CHUNK_LO,A);s.notEqual(A,0,"chunk.begin");get(CHUNK_HI,A);s.notEqual(A,0,"chunk.begin");set(CHUNK,4);set(CHUNK_LINE,0);s.jump("receive.next");
        s.label("chunk.begin");set(CHUNK,2);s.jump("receive.next");
        s.label("chunk.data");get(CHUNK_LO,A);s.notEqual(A,0,"chunk.decrement");get(CHUNK_HI,A);s.store(1,B);s.emit(SUB,A,B,A);put(CHUNK_HI,A);s.label("chunk.decrement");get(CHUNK_LO,A);s.store(1,B);s.emit(SUB,A,B,A);put(CHUNK_LO,A);s.notEqual(A,0,"chunk.body");get(CHUNK_HI,A);s.notEqual(A,0,"chunk.body");set(CHUNK,3);set(CHUNK_CR,0);
        s.label("chunk.body");get(CHAR,D);s.jump("html");
        s.label("chunk.delimiter");get(CHUNK_CR,A);s.notEqual(A,0,"chunk.lf");get(CHAR,D);s.notEqual(D,13,"error");set(CHUNK_CR,1);s.jump("receive.next");s.label("chunk.lf");get(CHAR,D);s.notEqual(D,10,"error");set(CHUNK,1);set(CHUNK_DIGITS,0);set(CHUNK_EXT,0);s.jump("receive.next");
    }
    public static void main(String[] args)throws Exception{byte[] bytes=program();Files.write(Path.of(args[0]),bytes);System.out.println("WEB.COM: "+bytes.length+" bytes; "+((BUFFER_END-BUFFER)*256)+" bytes text RAM");}
}
