package xyz.ignite4inferneo.jvdos;

import java.nio.file.Files;
import java.nio.file.Path;
import static xyz.ignite4inferneo.OPCode.*;

/** An HTTP browser made exclusively of guest instructions; no host rendering or VM hooks. */
public final class BrowserImage {
    private static final int A=0,B=1,C=2,D=3,E=4,F=5;
    static final int BUFFER=27, BUFFER_END=30, URL=30, STATE=31;
    private static final int LEN=0, INDEX=1, HOST=2, PATH=3, PORT=4, PORT_H=5,
        SIZE=8, SIZE_H=9, POS=10, POS_H=11, SPACE=12, CHAR=18,
        SHIFT=22, MODE=23, MORE=25, READY=26, START=27, START_H=28,
        MESSAGE_PAGE=32, MESSAGE_OFFSET=33, MESSAGE_PART=34, MESSAGE_MODE=35,
        MESSAGE_RETURN=40, PRINT_RETURN=42, POLL_RETURN=44,
        STATUS=52, STATUS2=53, STATUS3=54, HEADER_LEN=55, HEADER_FIRST=56,
        TAG=57, HASH=58, NAME_LEN=59, NAME_END=60, CLOSING=61, QUOTE=62, HIDDEN=63,
        ENTITY=66, ENTITY_LEN=67, ENTITY_HASH=68, NUMBER=69,
        BATCH=71, IDLE=72, IDLE_H=73, ROWS=74, SCREEN_COL=75,
        DIGITS=77, WORK=78, WORK_H=79, PORT_DIGIT=80, MULT=81, CARRY=82, KEY_OFFSET=84, LOOK_LEN=85, LOOK_HASH=86, LOOK_PAGE=87, LOOK_OFFSET=88, PLAIN=89,
        LINE=128, LINE_CAP=96;
    private static DesktopImage.Asm s;
    private BrowserImage() { }
    private static void get(int slot,int reg){s.emit(LOAD_MEM_ABSOLUTE,1,STATE,slot,reg);}
    private static void put(int slot,int reg){s.emit(STORE_MEM_ABSOLUTE,reg,1,STATE,slot);}
    private static void set(int slot,int value){s.store(value,D);put(slot,D);}
    private static void inc(int slot){get(slot,A);s.emit(INCREMENT_REGISTER,A);put(slot,A);}
    private static void inc16(int low,int high){inc(low);get(low,A);String done=s.unique("inc16");s.notEqual(A,0,done);inc(high);s.label(done);}
    private static void copy(int from,int to){get(from,A);put(to,A);}
    private static void read(int page,int index,int reg){s.store(page,F);get(index,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,reg);}
    private static void write(int page,int index,int reg){s.store(page,F);get(index,C);s.emit(STORE_MEM_ON_BANK,reg,E,F,C);}
    private static void net(int address,int value){s.store(address,A);s.store(value,D);s.emit(WRITE_NETWORK,A,D);}
    private static void call(String target,int slot){
        String back=s.unique("return");
        if(slot==PRINT_RETURN){s.ref(back,true,12);s.ref(back,false,13);}
        else {s.ref(back,true,14);s.ref(back,false,15);}
        s.jump(target);s.label(back);
    }
    private static void ret(int slot){
        if(slot==PRINT_RETURN)s.emit(CHANGE_PAGE_JUMP,12,13);
        else s.emit(CHANGE_PAGE_JUMP,14,15);
    }
    private static void print(){call("print",PRINT_RETURN);}
    private static void message(String text,boolean network){
        String label=s.strings.computeIfAbsent(text,t->s.unique("literal"));
        s.ref(label,true,A);put(MESSAGE_PAGE,A);s.ref(label,false,A);put(MESSAGE_OFFSET,A);
        set(MESSAGE_PART,0);set(MESSAGE_MODE,network?1:0);call("message",MESSAGE_RETURN);
    }
    private static void text(String text){message(text,false);}
    private static void send(String text){message(text,true);}
    // Keep N/O available as the receive cursor. Asm.less uses both as scratch;
    // this browser uses C/P instead (call sites do not keep C live across tests).
    private static void less(int register,int literal,String target){s.store(literal,19);s.emit(COMPARE_UNSIGNED,register,19,C);s.equal(C,1,target);}
    private static void lowercase(){String done=s.unique("lower");less(D,'A',done);less(D,'Z'+1,done+".letter");s.jump(done);s.label(done+".letter");s.store(32,A);s.emit(ADD,D,A,D);s.label(done);}
    private static int hash(String text){int n=0;for(char c:text.toCharArray())n=(n*31+c)&255;return n;}
    public static byte[] program(){byte[] bytes=DesktopImage.assembleOptimized(BrowserImage::emit).bytes();if(bytes.length>BUFFER*256)throw new IllegalStateException("Browser overlaps text workspace: "+bytes.length);return bytes;}
    private static void emit(DesktopImage.Asm assembler){
        s=assembler;s.jump("start");
        // The print ABI returns with virtual bank zero selected. Its alias at 248
        // lands on this page-zero trampoline, which restores bank one explicitly.
        s.label("print.return");s.store(1,A);s.emit(CHANGE_BANK_PAGE_JUMP,A,12,13);
        s.label("start");
        // Like Pong, reject fullscreen execution before touching private workspace.
        s.store(255,A);s.store(255,B);s.store(0,C);s.emit(PUSH_GRA_MEM,A,B,C);
        s.store(254,A);s.store(254,B);s.store(127,C);s.emit(PUSH_GRA_MEM,A,B,C);
        s.store(255,A);s.store(255,B);s.emit(READ_GRA_MEM,A,B,D);s.notEqual(D,127,"quit");
        s.store(1,E);set(LEN,0);set(SHIFT,0);set(READY,0);s.jump("address");
        emitAddress();emitRequest();emitReceive();emitHtml();emitPager();emitRoutines();
        s.label("error");net(0,3);set(READY,0);set(MODE,3);s.emit(CLEAR_GRA);text("WEB error U:url R:retry\n");s.jump("wait");
        s.label("https");net(0,3);set(MODE,3);s.emit(CLEAR_GRA);text("HTTP only. U:url\n");s.jump("wait");
        s.label("quit");net(0,3);s.store(0,A);s.store(249,B);s.store(9,C);s.emit(CHANGE_BANK_PAGE_JUMP,A,B,C);emitKeys();emitTables();
    }
    private static void emitAddress(){
        s.label("address");net(0,3);set(MODE,1);s.emit(CLEAR_GRA);text("WEB Enter:go Home:clear\nHTTP host[:port]/path\n");set(INDEX,0);
        s.label("address.show");get(INDEX,A);get(LEN,B);s.eq(A,B,"edit");read(URL,INDEX,D);print();inc(INDEX);s.jump("address.show");
        s.label("edit");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.equal(D,10,"load");s.equal(D,8,"erase");s.equal(D,16,"shift.on");s.equal(D,144,"shift.off");s.equal(D,36,"address.clear");less(D,32,"edit");
        // Keyboard ports supply VK codes, not ASCII punctuation.
        less(D,65,"key.punctuation");less(D,91,"key.letter");
        s.label("key.punctuation");
        put(CHAR,D);s.ref("keys",false,A);put(KEY_OFFSET,A);
        s.label("key.lookup");s.ref("keys",true,F);get(KEY_OFFSET,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,A);s.equal(A,0,"edit");get(CHAR,D);s.eq(A,D,"key.found");s.store(5,A);s.emit(ADD,C,A,A);put(KEY_OFFSET,A);s.jump("key.lookup");
        s.label("key.found");get(SHIFT,A);s.emit(ADD,C,A,C);s.emit(INCREMENT_REGISTER,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);s.jump("insert");
        s.label("key.letter");get(SHIFT,A);s.notEqual(A,0,"insert");s.store(32,A);s.emit(ADD,D,A,D);s.jump("insert");
        s.label("shift.on");set(SHIFT,1);s.jump("edit");s.label("shift.off");set(SHIFT,0);s.jump("edit");
        s.label("insert");put(CHAR,D);get(LEN,A);s.equal(A,120,"edit");get(CHAR,D);write(URL,LEN,D);inc(LEN);get(CHAR,D);print();s.jump("edit");
        s.label("erase");get(LEN,A);s.equal(A,0,"edit");s.store(1,B);s.emit(SUB,A,B,A);put(LEN,A);s.jump("address");
        s.label("address.clear");set(LEN,0);s.jump("address");
    }
    private static void emitKeys(){
        int[][] keys={{32,32,32},{44,44,60},{45,45,95},{46,46,62},{47,47,63},
            {48,48,41},{49,49,33},{50,50,64},{51,51,35},{52,52,36},{53,53,37},
            {54,54,94},{55,55,38},{56,56,42},{57,57,40},{59,59,58},{61,61,43},
            {91,91,123},{92,92,124},{93,93,125},{192,96,126},{222,39,34}};
        s.reserve(246);s.labels.put("keys",s.page*256+s.pos+2);
        for(int[] key:keys)s.emit(STORE_MEM_ABSOLUTE,D,key[0],key[1],key[2]);
        s.emit(STORE_MEM_ABSOLUTE,D,0,0,0);
    }
    private static void emitTables(){
        String[] tags={"head","script","style","p","div","br","li","ul","ol","tr","table","h1","h2","h3","h4","h5","h6","pre"};
        s.labels.put("tags",s.page*256+s.pos+2);
        for(int i=0;i<tags.length;i++)s.emit(STORE_MEM_ABSOLUTE,D,hash(tags[i]),tags[i].length(),i<3?i+1:4);
        s.emit(STORE_MEM_ABSOLUTE,D,0,0,0);
        s.labels.put("entities",s.page*256+s.pos+2);
        String[] entities={"amp","lt","gt","quot","apos","nbsp"};int[] values={'&','<','>','"','\'',32};
        for(int i=0;i<entities.length;i++)s.emit(STORE_MEM_ABSOLUTE,D,hash(entities[i]),entities[i].length(),values[i]);
        s.emit(STORE_MEM_ABSOLUTE,D,0,0,0);
    }
    private static void emitRequest(){
        s.label("load");get(LEN,A);s.equal(A,0,"address");net(0,4);set(READY,0);set(INDEX,0);set(PORT,80);set(PORT_H,0);
        // Prefix reads are length checked: edits may leave old bytes in URL RAM.
        get(LEN,A);less(A,7,"host.start");
        for(int i=0;i<4;i++){String ok=s.unique("scheme.letter");s.emit(LOAD_MEM_ABSOLUTE,1,URL,i,D);s.equal(D,"http".charAt(i),ok);s.notEqual(D,"HTTP".charAt(i),"host.start");s.label(ok);}
        s.emit(LOAD_MEM_ABSOLUTE,1,URL,4,D);s.equal(D,':',"scheme.http");s.equal(D,'s',"https");s.equal(D,'S',"https");s.jump("host.start");
        s.label("scheme.http");for(int i=5;i<7;i++){s.emit(LOAD_MEM_ABSOLUTE,1,URL,i,D);s.notEqual(D,'/',"error");}set(INDEX,7);
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
        get(PORT_H,D);s.store(3,A);s.emit(WRITE_NETWORK,A,D);get(PORT,D);s.store(4,A);s.emit(WRITE_NETWORK,A,D);net(0,1);
        set(MODE,2);s.emit(CLEAR_GRA);text("WEB: loading...\n");set(IDLE,0);set(IDLE_H,0);
        s.label("connect");call("poll",POLL_RETURN);s.store(0,A);s.emit(READ_NETWORK,A,D);s.equal(D,1,"connect");
        send("GET ");copy(PATH,INDEX);get(INDEX,A);get(LEN,B);s.eq(A,B,"request.slash");read(URL,INDEX,D);s.equal(D,'/',"request.path");
        s.label("request.slash");send("/");
        s.label("request.path");get(INDEX,A);get(LEN,B);s.eq(A,B,"request.headers");read(URL,INDEX,D);s.equal(D,'#',"request.headers");less(D,33,"error");s.store(5,A);s.emit(WRITE_NETWORK,A,D);inc(INDEX);s.jump("request.path");
        s.label("request.headers");send(" HTTP/1.0\r\nHost: ");copy(HOST,INDEX);
        s.label("request.host");get(INDEX,A);get(PATH,B);s.eq(A,B,"request.end");read(URL,INDEX,D);s.store(5,A);s.emit(WRITE_NETWORK,A,D);inc(INDEX);s.jump("request.host");
        s.label("request.end");send("\r\nConnection: close\r\nAccept-Encoding: identity\r\n\r\n");net(0,2);
        for(int slot:new int[]{HEADER_LEN,TAG,HIDDEN,ENTITY,MORE,SIZE,PLAIN})set(slot,0);set(HEADER_FIRST,1);set(SIZE_H,BUFFER);s.store(0,16);s.store(0,17);s.store(BUFFER,18);set(SPACE,1);set(STATUS,'?');set(STATUS2,'?');set(STATUS3,'?');set(BATCH,0);s.jump("receive");
    }
    private static void emitReceive(){
        // DISPLAY is deliberately absent on the byte path. The desktop already
        // preempts the interpreter every 64 instructions; drain 64 bytes per poll.
        s.label("receive");s.store(7,A);s.emit(READ_NETWORK,A,D);s.notEqual(D,0,"receive.byte");s.store(8,A);s.emit(READ_NETWORK,A,D);s.notEqual(D,0,"receive.byte");
        s.store(0,A);s.emit(READ_NETWORK,A,D);less(D,16,"receive.wait");s.jump("done");
        s.label("receive.wait");call("poll",POLL_RETURN);s.jump("receive");
        s.label("receive.byte");s.store(6,A);s.emit(READ_NETWORK,A,D);put(CHAR,D);
        get(HEADER_FIRST,A);s.equal(A,2,"receive.body");get(CHAR,D);s.equal(D,13,"receive.next");s.equal(D,10,"header.end");get(HEADER_LEN,A);s.equal(A,LINE_CAP,"receive.next");s.store(LINE,B);s.emit(ADD,A,B,C);s.store(STATE,F);s.emit(STORE_MEM_ON_BANK,D,E,F,C);inc(HEADER_LEN);s.jump("receive.next");
        s.label("header.end");get(HEADER_FIRST,A);s.equal(A,1,"header.status");get(HEADER_LEN,A);s.equal(A,0,"header.done");s.jump("header.classify");
        s.label("header.status");get(HEADER_LEN,A);less(A,12,"error");for(int i=0;i<3;i++){s.emit(LOAD_MEM_ABSOLUTE,1,STATE,LINE+9+i,D);put(STATUS+i,D);}set(HEADER_FIRST,0);s.jump("header.reset");
        // Recognize text/plain case-insensitively, allowing optional whitespace
        // and a charset suffix. Other content types use the bounded HTML reader.
        s.label("header.classify");set(INDEX,0);set(HASH,0);set(NAME_LEN,0);
        s.label("header.hash");get(INDEX,A);get(HEADER_LEN,B);s.eq(A,B,"header.check");s.store(LINE,B);s.emit(ADD,A,B,C);s.store(STATE,F);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);s.equal(D,';',"header.check");less(D,33,"header.advance");lowercase();put(CHAR,D);get(HASH,A);s.store(31,B);s.emit(MULTIPLY,A,B,A);get(CHAR,D);s.emit(ADD,A,D,A);put(HASH,A);inc(NAME_LEN);
        s.label("header.advance");inc(INDEX);s.jump("header.hash");s.label("header.check");get(NAME_LEN,A);s.notEqual(A,"content-type:text/plain".length(),"header.reset");get(HASH,A);s.notEqual(A,hash("content-type:text/plain"),"header.reset");set(PLAIN,1);s.jump("header.reset");
        s.label("header.done");set(HEADER_FIRST,2);s.jump("receive.next");s.label("header.reset");set(HEADER_LEN,0);s.jump("receive.next");
        s.label("receive.body");get(CHAR,D);s.jump("html");
        s.label("receive.next");inc(BATCH);get(BATCH,A);s.notEqual(A,64,"receive");set(BATCH,0);set(IDLE,0);set(IDLE_H,0);call("poll",POLL_RETURN);s.jump("receive");
        s.label("done");put(SIZE,17);put(SIZE_H,18);get(HEADER_FIRST,A);s.notEqual(A,2,"error");s.store(1,A);s.emit(READ_NETWORK,A,D);s.notEqual(D,0,"error");net(0,3);set(MORE,0);s.label("cache.ready");set(READY,1);set(POS,0);set(POS_H,BUFFER);s.jump("page");
    }
    private static void emitHtml(){
        s.label("html");get(PLAIN,A);s.equal(A,1,"plain");get(TAG,A);s.notEqual(A,0,"html.tag");s.equal(D,'<',"html.open");get(HIDDEN,A);s.notEqual(A,0,"html.return");get(ENTITY,A);s.notEqual(A,0,"entity.char");get(CHAR,D);s.equal(D,'&',"entity.open");less(D,33,"html.space");less(D,127,"html.append");s.jump("html.space");
        s.label("plain");get(CHAR,D);s.equal(D,13,"html.return");s.equal(D,10,"html.append");less(D,33,"html.space");less(D,127,"html.append");
        s.label("html.space");s.store(32,D);s.label("html.append");s.jump("append");s.label("html.return");s.jump("receive.next");
        s.label("html.open");set(TAG,1);for(int slot:new int[]{HASH,NAME_LEN,NAME_END,CLOSING,QUOTE,ENTITY})set(slot,0);s.jump("html.return");
        s.label("html.tag");get(QUOTE,A);s.notEqual(A,0,"tag.quoted");get(CHAR,D);s.equal(D,'<',"html.open");s.equal(D,'>',"tag.end");s.equal(D,'\'',"tag.quote");s.equal(D,'\"',"tag.quote");get(NAME_END,A);s.notEqual(A,0,"html.return");get(CHAR,D);less(D,33,"tag.name.end");get(NAME_LEN,A);s.notEqual(A,0,"tag.name");s.equal(D,'/',"tag.closing");
        s.label("tag.name");lowercase();put(CHAR,D);get(NAME_LEN,A);s.equal(A,15,"tag.name.end");inc(NAME_LEN);get(HASH,A);s.store(31,B);s.emit(MULTIPLY,A,B,A);get(CHAR,D);s.emit(ADD,A,D,A);put(HASH,A);
        s.jump("html.return");
        s.label("tag.name.end");set(NAME_END,1);s.jump("html.return");s.label("tag.closing");set(CLOSING,1);s.jump("html.return");
        s.label("tag.quote");put(QUOTE,D);s.jump("html.return");s.label("tag.quoted");get(CHAR,D);s.ne(A,D,"html.return");set(QUOTE,0);s.jump("html.return");
        s.label("tag.end");set(TAG,0);copy(NAME_LEN,LOOK_LEN);copy(HASH,LOOK_HASH);s.ref("tags",true,A);put(LOOK_PAGE,A);s.ref("tags",false,A);put(LOOK_OFFSET,A);call("lookup",MESSAGE_RETURN);
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
        s.label("home");get(READY,A);s.equal(A,0,"wait");set(POS,0);set(POS_H,BUFFER);s.jump("page");
        s.label("previous");get(READY,A);s.equal(A,0,"wait");get(START_H,A);less(A,BUFFER+2,"home");s.store(2,B);s.emit(SUB,A,B,A);put(POS_H,A);set(POS,0);s.jump("page");
        s.label("next");get(READY,A);s.equal(A,0,"wait");get(POS_H,A);get(SIZE_H,B);s.ne(A,B,"page");get(POS,A);get(SIZE,B);s.ne(A,B,"page");get(MORE,A);s.equal(A,0,"wait");set(READY,0);set(MODE,2);set(SIZE,0);set(SIZE_H,BUFFER);s.store(0,17);s.store(BUFFER,18);s.jump("receive");
        s.label("page");set(MODE,0);copy(POS,START);copy(POS_H,START_H);s.emit(CLEAR_GRA);text("WEB HTTP ");for(int slot:new int[]{STATUS,STATUS2,STATUS3}){get(slot,D);print();}text("\nN:next P:prev H:home\nR:reload U:url\n");set(ROWS,0);set(SCREEN_COL,0);
        s.label("page.char");get(POS_H,A);get(SIZE_H,B);s.ne(A,B,"page.read");get(POS,A);get(SIZE,B);s.eq(A,B,"wait");
        s.label("page.read");get(POS_H,F);get(POS,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);print();inc16(POS,POS_H);inc(SCREEN_COL);get(SCREEN_COL,A);s.notEqual(A,32,"page.char");set(SCREEN_COL,0);inc(ROWS);get(ROWS,A);s.notEqual(A,16,"page.char");s.jump("wait");
        s.label("wait");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.equal(D,'U',"address");s.equal(D,'R',"load");get(READY,A);s.equal(A,0,"wait");for(int key:new int[]{'N',32,34,40})s.equal(D,key,"next");for(int key:new int[]{'P',33,38,8})s.equal(D,key,"previous");for(int key:new int[]{'H',36})s.equal(D,key,"home");s.jump("wait");
    }
    private static void emitRoutines(){
        s.label("message");get(MESSAGE_PAGE,F);get(MESSAGE_OFFSET,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);s.equal(D,0,"message.done");get(MESSAGE_MODE,A);s.equal(A,1,"message.send");print();s.jump("message.advance");s.label("message.send");s.store(5,A);s.emit(WRITE_NETWORK,A,D);
        s.label("message.advance");inc(MESSAGE_OFFSET);inc(MESSAGE_PART);get(MESSAGE_PART,A);s.notEqual(A,3,"message");set(MESSAGE_PART,0);get(MESSAGE_OFFSET,A);s.store(2,B);s.emit(ADD,A,B,A);put(MESSAGE_OFFSET,A);s.jump("message");s.label("message.done");ret(MESSAGE_RETURN);
        s.label("print");s.store(248,A);s.ref("print.return",false,B);s.store(249,F);s.store(0,C);s.emit(CHANGE_BANK_PAGE_JUMP,C,F,C);
        // Receive cursor and column live in M/N/O rather than guest RAM.
        // The normal character path writes once and never calls DISPLAY/print.
        s.label("append");s.equal(D,10,"append.break");s.equal(D,32,"append.space");less(D,33,"append.return");s.store(0,A);put(SPACE,A);s.jump("append.write");
        s.label("append.space");get(SPACE,A);s.equal(A,1,"append.return");s.store(1,A);put(SPACE,A);s.jump("append.write");
        s.label("append.break");s.equal(16,0,"append.return");s.store(32,D);s.emit(STORE_MEM_ON_BANK,D,E,18,17);s.emit(INCREMENT_REGISTER,17);s.emit(INCREMENT_REGISTER,16);s.notEqual(16,32,"append.break");s.store(0,16);s.store(1,A);put(SPACE,A);s.equal(17,0,"append.carry");s.jump("append.return");
        s.label("append.write");s.emit(STORE_MEM_ON_BANK,D,E,18,17);s.emit(INCREMENT_REGISTER,17);s.equal(17,0,"append.write.carry");s.jump("append.column");s.label("append.write.carry");s.emit(INCREMENT_REGISTER,18);
        s.label("append.column");s.emit(INCREMENT_REGISTER,16);s.notEqual(16,32,"append.return");s.store(0,16);s.store(1,A);put(SPACE,A);s.jump("append.return");
        s.label("append.carry");s.emit(INCREMENT_REGISTER,18);
        s.label("append.return");s.equal(18,BUFFER_END,"append.full");s.jump("receive.next");
        s.label("append.full");put(SIZE,17);put(SIZE_H,18);set(MORE,1);s.jump("cache.ready");
        s.label("lookup");get(LOOK_PAGE,F);get(LOOK_OFFSET,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);s.equal(D,0,"lookup.done");get(LOOK_HASH,A);s.ne(A,D,"lookup.next");s.emit(INCREMENT_REGISTER,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);get(LOOK_LEN,A);s.ne(A,D,"lookup.next");s.emit(INCREMENT_REGISTER,C);s.emit(LOAD_MEM_ON_BANK,E,F,C,D);s.jump("lookup.done");
        s.label("lookup.next");get(LOOK_OFFSET,A);s.store(5,B);s.emit(ADD,A,B,A);put(LOOK_OFFSET,A);s.jump("lookup");s.label("lookup.done");ret(MESSAGE_RETURN);
        s.label("poll");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.equal(D,16,"poll.shift.on");s.equal(D,144,"poll.shift.off");s.jump("poll.network");s.label("poll.shift.on");set(SHIFT,1);s.jump("poll.network");s.label("poll.shift.off");set(SHIFT,0);
        s.label("poll.network");s.store(1,A);s.emit(READ_NETWORK,A,D);s.notEqual(D,0,"error");inc16(IDLE,IDLE_H);get(IDLE_H,A);s.equal(A,255,"error");ret(POLL_RETURN);
    }
    public static void main(String[] args)throws Exception{byte[] bytes=program();Files.write(Path.of(args[0]),bytes);System.out.println("WEB.COM: "+bytes.length+" bytes; "+((BUFFER_END-BUFFER)*256)+" bytes text RAM");}
}

