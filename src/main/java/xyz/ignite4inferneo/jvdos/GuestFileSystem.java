package xyz.ignite4inferneo.jvdos;

import static xyz.ignite4inferneo.OPCode.*;
import static xyz.ignite4inferneo.jvdos.FileSystemAbi.*;

/** The JVFS driver, emitted exclusively as ordinary VCPU instructions. */
final class GuestFileSystem {
    private GuestFileSystem() { }
    private static final int A=0,B=1,C=2,D=3,E=4,F=5,I=12,J=13,K=14,L=15,N=17,O=18,P=19;
    private static final int MB=64,MP=65,RB=66,RP=67,RO=68,DEVICE=69,CL=70,FL=72,
            X=74,Y=75,Z=76,LO=77,HI=78,HB=79,DC=80,DR=82,HOPS=83,INDEX=85,TEMP=86,TEMP2=87,
            EX=88,EY=89,EZ=90,FREE_X=91,FREE_Y=92,FREE_Z=93,FLAG=94,
            RFAT=95,RADDR=97,RDIR=99,RFIND=101,RALLOC=103,RZERO=105,RRW=107,RHANDLE=109,RCHAIN=111,RSTREAM=113,
            APAGE=116,AOFF=117,HINT=119,ORIGINAL=121;
    static void emit(JvDosImage.Asm source){new Driver(source).emit();}
    private static final class Driver {
        final JvDosImage.Asm s;
        Driver(JvDosImage.Asm s){this.s=s;}
        void get(int r,int n){s.emit(LOAD_MEM_ABSOLUTE,0,PRIVATE_PAGE,n,r);}
        void put(int r,int n){s.emit(STORE_MEM_ABSOLUTE,r,0,PRIVATE_PAGE,n);}
        void set(int n,int v){s.store(v,D);put(D,n);}
        void copy(int from,int to,int count){for(int i=0;i<count;i++){get(D,from+i);put(D,to+i);}}
        void value(int r,int v,String target){s.store(v,F);s.eq(r,F,target);}
        void neValue(int r,int v,String target){s.store(v,F);s.ne(r,F,target);}
        void less(int r,int v,String target){s.store(v,F);s.emit(COMPARE_UNSIGNED,r,F,E);value(E,1,target);}
        void call(String label,int ret){String back=s.unique("fs.return");s.ref(back,true,C);put(C,ret);s.ref(back,false,C);put(C,ret+1);s.jump(label);s.label(back);}
        void ret(int slot){get(A,slot);get(B,slot+1);s.emit(CHANGE_PAGE_JUMP,A,B);}
        void inc(int n){get(A,n);s.emit(INCREMENT_REGISTER,A);put(A,n);}
        void inc16(int n){inc(n);String done=s.unique("fs.inc");neValue(A,0,done);inc(n+1);s.label(done);}
        void hread(int offset,int dest){get(C,HB);s.store(offset,B);s.emit(ADD,C,B,C);s.store(0,A);s.store(PRIVATE_PAGE,B);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);put(D,dest);}
        void hwrite(int source,int offset){get(D,source);get(K,HB);s.store(offset,J);s.emit(ADD,K,J,K);s.store(0,I);s.store(PRIVATE_PAGE,J);s.emit(STORE_MEM_ON_BANK,D,I,J,K);}
        void cache(int r,int offset){s.emit(LOAD_MEM_ABSOLUTE,0,CACHE_PAGE,offset,r);}
        void addressEntry(){copy(EX,ADDRESS,3);}
        void emit() {
            s.label("fs.entry");put(A,MB);put(B,MP);put(N,RB);put(O,RP);put(P,RO);s.store(0,A);s.emit(ADD,10,A,D);put(D,DEVICE);
            s.store(0,C);s.label("fs.request");get(A,MB);get(B,MP);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.store(0,I);s.store(PRIVATE_PAGE,J);s.emit(STORE_MEM_ON_BANK,D,I,J,C);s.emit(INCREMENT_REGISTER,C);value(C,64,"fs.request.done");s.jump("fs.request");
            s.label("fs.request.done");set(STATUS,0);set(HOPS,0);set(HOPS+1,0);copy(TYPE,124,1);
            s.emit(READ_INPUT,0x40,D);value(D,0,"fs.unformatted");
            for(int i=0;i<JvFileSystem.SIGNATURE.length;i++){s.store(2,A);s.store(0,B);s.store(i,C);s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);neValue(D,JvFileSystem.SIGNATURE[i]&255,"fs.unformatted");}
            get(A,OP);for(int op=RESET;op<=STAT;op++)value(A,op,"fs.op."+op);s.jump("fs.argument");
            for(int[] failure:new int[][]{{NOT_FOUND,1},{EXISTS,2},{FULL,3},{CORRUPT,4},{BAD_ARGUMENT,5},{IS_DIRECTORY,6},{NOT_EMPTY,7},{EOF,8},{BAD_HANDLE,9},{UNFORMATTED,10}}){
                s.label("fs.error."+failure[0]);set(STATUS,failure[0]);s.jump("fs.reply");
            }
            s.label("fs.argument");s.jump("fs.error."+BAD_ARGUMENT);s.label("fs.corrupt");s.jump("fs.error."+CORRUPT);s.label("fs.unformatted");s.jump("fs.error."+UNFORMATTED);
            s.label("fs.reply");get(A,OP);value(A,READ_BLOCK,"fs.reply.count");value(A,WRITE_BLOCK,"fs.reply.count");s.jump("fs.reply.device");s.label("fs.reply.count");copy(125,COUNT,1);s.label("fs.reply.device");get(D,DEVICE);s.store(0,A);s.emit(ADD,D,A,10);s.store(0,C);
            s.label("fs.reply.byte");s.store(0,A);s.store(PRIVATE_PAGE,B);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);get(I,MB);get(J,MP);s.emit(STORE_MEM_ON_BANK,D,I,J,C);s.emit(INCREMENT_REGISTER,C);neValue(C,64,"fs.reply.byte");get(N,RB);get(O,RP);get(P,RO);s.emit(CHANGE_BANK_PAGE_JUMP,N,O,P);
            s.label("fs.op."+RESET);s.store(128,C);s.store(0,A);s.store(PRIVATE_PAGE,B);s.store(0,D);
            s.label("fs.reset");s.emit(STORE_MEM_ON_BANK,D,A,B,C);s.emit(INCREMENT_REGISTER,C);s.store(224,F);s.eq(C,F,"fs.reset.done");s.jump("fs.reset");s.label("fs.reset.done");set(HINT,162);set(HINT+1,0);s.jump("fs.reply");
            emitAddressing();emitDirectories();emitAllocation();emitFiles();emitChanges();
        }
        void emitAddressing() {
            s.label("fs.name.valid");get(A,NAME);value(A,0,"fs.argument");value(A,'.',"fs.argument");set(INDEX,0);set(FLAG,0);
            s.label("fs.name.byte");get(C,INDEX);s.store(NAME,B);s.emit(ADD,C,B,C);s.store(0,A);s.store(PRIVATE_PAGE,B);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);get(A,FLAG);value(A,1,"fs.name.pad");value(D,0,"fs.name.end");
            for(int ch:new int[]{'-','.','_'})value(D,ch,"fs.name.next");less(D,'0',"fs.argument");less(D,':',"fs.name.next");less(D,'A',"fs.argument");less(D,'Z'+1,"fs.name.next");s.jump("fs.argument");
            s.label("fs.name.end");set(FLAG,1);s.label("fs.name.pad");s.store(0,D);s.store(0,A);s.store(PRIVATE_PAGE,B);s.emit(STORE_MEM_ON_BANK,D,A,B,C);
            s.label("fs.name.next");inc(INDEX);neValue(A,12,"fs.name.byte");ret(RHANDLE);
            s.label("fs.entry.input");get(A,ADDRESS);less(A,2,"fs.argument");neValue(A,2,"fs.entry.input.low");get(A,ADDRESS+1);less(A,132,"fs.argument");s.label("fs.entry.input.low");get(A,ADDRESS+2);
            s.label("fs.entry.aligned");value(A,0,"fs.entry.input.ok");less(A,32,"fs.argument");s.store(32,B);s.emit(SUB,A,B,A);s.jump("fs.entry.aligned");s.label("fs.entry.input.ok");copy(ADDRESS,EX,3);call("fs.entry.read",RRW);cache(A,0);value(A,0,"fs.error."+NOT_FOUND);value(A,'.',"fs.argument");ret(RHANDLE);
            s.label("fs.cluster.valid");get(A,CL+1);less(A,64,"fs.cluster.low");s.jump("fs.corrupt");s.label("fs.cluster.low");neValue(A,0,"fs.cluster.ok");get(A,CL);less(A,JvFileSystem.ROOT,"fs.corrupt");s.label("fs.cluster.ok");ret(RHANDLE);
            s.label("fs.address");call("fs.cluster.valid",RHANDLE);get(A,CL);s.store(0,B);
            s.label("fs.address.divide");less(A,64,"fs.address.ready");s.store(64,C);s.emit(SUB,A,C,A);s.emit(INCREMENT_REGISTER,B);s.jump("fs.address.divide");
            s.label("fs.address.ready");s.store(4,C);s.emit(MULTIPLY,A,C,A);get(C,APAGE);s.emit(ADD,A,C,A);put(A,Y);get(A,CL+1);s.store(4,C);s.emit(MULTIPLY,A,C,A);s.emit(ADD,A,B,A);put(A,X);copy(AOFF,Z,1);ret(RADDR);
            for(boolean write:new boolean[]{false,true}) {
                s.label(write?"fs.fat.write":"fs.fat.read");get(A,CL+1);less(A,64,"fs.fat.valid."+write);s.jump("fs.corrupt");s.label("fs.fat.valid."+write);
                get(A,CL);s.emit(ADD,A,A,C);get(B,CL+1);s.store(2,D);s.emit(MULTIPLY,B,D,B);s.store(4,D);s.emit(ADD,B,D,B);s.emit(COMPARE_UNSIGNED,C,A,D);value(D,1,"fs.fat.carry."+write);s.jump("fs.fat.ready."+write);s.label("fs.fat.carry."+write);s.emit(INCREMENT_REGISTER,B);s.label("fs.fat.ready."+write);s.store(2,A);
                if(write){get(D,LO);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);s.emit(INCREMENT_REGISTER,C);get(D,HI);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);}
                else{s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);put(D,LO);s.emit(INCREMENT_REGISTER,C);s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);put(D,HI);}
                ret(RFAT);
            }
            s.label("fs.entry.read");copy(EX,X,3);s.store(0,I);s.store(CACHE_PAGE,J);s.store(0,K);get(A,X);get(B,Y);get(C,Z);
            s.label("fs.entry.byte");s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);s.emit(STORE_MEM_ON_BANK,D,I,J,K);s.emit(INCREMENT_REGISTER,C);s.emit(INCREMENT_REGISTER,K);s.store(32,F);s.eq(K,F,"fs.entry.read.done");s.jump("fs.entry.byte");s.label("fs.entry.read.done");ret(RRW);
            s.label("fs.entry.decode");for(int i=0;i<12;i++){cache(D,i);put(D,NAME+i);}cache(D,12);put(D,TYPE);for(int i=0;i<2;i++){cache(D,14+i);put(D,FIRST+i);}for(int i=0;i<3;i++){cache(D,16+i);put(D,SIZE+i);}addressEntry();ret(RRW);
            s.label("fs.entry.write");get(A,EX);get(B,EY);get(C,EZ);s.store(0,I);s.store(PRIVATE_PAGE,J);s.store(NAME,K);
            s.label("fs.entry.name");s.emit(LOAD_MEM_ON_BANK,I,J,K,D);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);s.emit(INCREMENT_REGISTER,C);s.emit(INCREMENT_REGISTER,K);s.store(NAME+12,F);s.eq(K,F,"fs.entry.fields");s.jump("fs.entry.name");
            s.label("fs.entry.fields");get(D,TYPE);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);s.emit(INCREMENT_REGISTER,C);s.store(0,D);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);s.emit(INCREMENT_REGISTER,C);
            for(int i=0;i<2;i++){get(D,FIRST+i);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);s.emit(INCREMENT_REGISTER,C);}for(int i=0;i<3;i++){get(D,SIZE+i);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);s.emit(INCREMENT_REGISTER,C);}ret(RRW);
        }
        void emitDirectories() {
            s.label("fs.dir.start");copy(DIR,DC,2);get(A,DC);get(B,DC+1);s.store(0,C);s.ne(A,C,"fs.dir.start.ready");s.ne(B,C,"fs.dir.start.ready");set(DC,JvFileSystem.ROOT);s.label("fs.dir.start.ready");set(DR,0);set(FREE_X,0);ret(RDIR);
            s.label("fs.dir.address");copy(DC,CL,2);get(A,DR);s.store(0,B);
            s.label("fs.dir.divide");less(A,8,"fs.dir.offset");s.store(8,C);s.emit(SUB,A,C,A);s.emit(INCREMENT_REGISTER,B);s.jump("fs.dir.divide");
            s.label("fs.dir.offset");put(B,APAGE);s.store(32,B);s.emit(MULTIPLY,A,B,A);put(A,AOFF);call("fs.address",RADDR);copy(X,EX,3);call("fs.entry.read",RRW);ret(RDIR);
            s.label("fs.dir.advance");inc(DR);neValue(A,32,"fs.dir.advance.ok");set(DR,0);copy(DC,CL,2);call("fs.fat.read",RFAT);get(A,HI);neValue(A,255,"fs.dir.advance.cluster");get(A,LO);value(A,255,"fs.dir.end");s.jump("fs.corrupt");
            s.label("fs.dir.advance.cluster");copy(LO,DC,2);copy(DC,CL,2);call("fs.cluster.valid",RHANDLE);inc16(HOPS);get(A,HOPS+1);value(A,64,"fs.corrupt");
            s.label("fs.dir.advance.ok");set(FLAG,0);ret(RDIR);s.label("fs.dir.end");set(FLAG,1);ret(RDIR);
            s.label("fs.find");call("fs.dir.start",RDIR);
            s.label("fs.find.loop");call("fs.dir.address",RDIR);cache(A,0);value(A,0,"fs.find.free");
            for(int i=0;i<12;i++){cache(A,i);get(B,NAME+i);s.ne(A,B,"fs.find.next");}
            set(STATUS,OK);call("fs.entry.decode",RRW);ret(RFIND);
            s.label("fs.find.free");get(A,FREE_X);neValue(A,0,"fs.find.next");copy(EX,FREE_X,3);
            s.label("fs.find.next");call("fs.dir.advance",RDIR);get(A,FLAG);value(A,0,"fs.find.loop");set(STATUS,NOT_FOUND);ret(RFIND);
            s.label("fs.op."+LOOKUP);call("fs.find",RFIND);s.jump("fs.reply");
            s.label("fs.op."+NEXT);get(A,COOKIE);get(B,COOKIE+1);s.store(0,C);s.ne(A,C,"fs.next.resume");s.ne(B,C,"fs.next.resume");call("fs.dir.start",RDIR);s.jump("fs.next.loop");s.label("fs.next.resume");copy(COOKIE,DC,2);copy(ROW,DR,1);get(A,DR);value(A,255,"fs.error."+EOF);
            s.label("fs.next.loop");call("fs.dir.address",RDIR);cache(A,0);value(A,0,"fs.next.skip");value(A,'.',"fs.next.skip");call("fs.entry.decode",RRW);call("fs.dir.advance",RDIR);copy(DC,COOKIE,2);copy(DR,ROW,1);get(A,FLAG);value(A,0,"fs.reply");set(ROW,255);s.jump("fs.reply");
            s.label("fs.next.skip");call("fs.dir.advance",RDIR);get(A,FLAG);value(A,0,"fs.next.loop");set(ROW,255);s.jump("fs.error."+EOF);
            s.label("fs.dir.slot");get(A,FREE_X);value(A,0,"fs.dir.grow");copy(FREE_X,EX,3);ret(RFIND);
            s.label("fs.dir.grow");copy(DC,FL,2);call("fs.allocate",RALLOC);call("fs.zero",RZERO);copy(CL,ORIGINAL,2);copy(CL,LO,2);copy(FL,CL,2);call("fs.fat.write",RFAT);copy(ORIGINAL,CL,2);set(APAGE,0);set(AOFF,0);call("fs.address",RADDR);copy(X,EX,3);ret(RFIND);
            s.label("fs.op."+PARENT);copy(DIR,DC,2);set(DR,1);call("fs.dir.address",RDIR);call("fs.entry.decode",RRW);s.jump("fs.reply");
        }
        void emitAllocation() {
            s.label("fs.allocate");copy(HINT,CL,2);get(A,CL);get(B,CL+1);s.store(0,C);s.ne(A,C,"fs.allocate.start");s.ne(B,C,"fs.allocate.start");set(CL,162);set(CL+1,0);s.label("fs.allocate.start");set(INDEX,0);set(TEMP2,0);
            s.label("fs.allocate.scan");call("fs.fat.read",RFAT);get(A,LO);get(B,HI);s.store(0,C);s.ne(A,C,"fs.allocate.next");s.ne(B,C,"fs.allocate.next");set(LO,255);set(HI,255);call("fs.fat.write",RFAT);copy(CL,HINT,2);ret(RALLOC);
            s.label("fs.allocate.next");inc16(CL);get(A,CL+1);neValue(A,64,"fs.allocate.count");set(CL,162);set(CL+1,0);s.label("fs.allocate.count");inc(INDEX);neValue(A,0,"fs.allocate.scan");inc(TEMP2);less(A,64,"fs.allocate.scan");s.jump("fs.error."+FULL);
            s.label("fs.zero");set(APAGE,0);set(AOFF,0);
            s.label("fs.zero.page");call("fs.address",RADDR);get(A,X);get(B,Y);s.store(0,C);s.store(0,D);
            s.label("fs.zero.byte");s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);s.emit(INCREMENT_REGISTER,C);s.store(0,F);s.ne(C,F,"fs.zero.byte");inc(APAGE);neValue(A,4,"fs.zero.page");ret(RZERO);
            // Validate the exact chain length before destructive operations. The
            // bounded remaining byte count also rejects cycles without a RAM bitmap.
            s.label("fs.validate");copy(FIRST,CL,2);copy(SIZE,INDEX,3);get(A,INDEX);get(B,INDEX+1);get(C,INDEX+2);s.store(0,D);s.ne(A,D,"fs.validate.loop");s.ne(B,D,"fs.validate.loop");s.ne(C,D,"fs.validate.loop");get(A,CL);get(B,CL+1);s.ne(A,D,"fs.corrupt");s.ne(B,D,"fs.corrupt");ret(RCHAIN);
            s.label("fs.validate.loop");call("fs.cluster.valid",RHANDLE);call("fs.fat.read",RFAT);get(A,INDEX+2);neValue(A,0,"fs.validate.more");get(A,INDEX+1);less(A,4,"fs.validate.last");value(A,4,"fs.validate.exact");s.jump("fs.validate.more");s.label("fs.validate.exact");get(A,INDEX);value(A,0,"fs.validate.last");
            s.label("fs.validate.more");get(A,INDEX+1);s.store(4,B);s.emit(COMPARE_UNSIGNED,A,B,C);put(C,FLAG);s.emit(SUB,A,B,A);put(A,INDEX+1);get(A,FLAG);neValue(A,1,"fs.validate.next");get(A,INDEX+2);s.store(1,B);s.emit(SUB,A,B,A);put(A,INDEX+2);
            s.label("fs.validate.next");copy(LO,CL,2);s.jump("fs.validate.loop");
            s.label("fs.validate.last");get(A,LO);neValue(A,255,"fs.corrupt");get(A,HI);neValue(A,255,"fs.corrupt");ret(RCHAIN);
            s.label("fs.free");get(A,CL);get(B,CL+1);s.store(0,C);s.ne(A,C,"fs.free.loop");s.eq(B,C,"fs.free.done");
            s.label("fs.free.loop");call("fs.cluster.valid",RHANDLE);call("fs.fat.read",RFAT);copy(LO,FL,2);set(LO,0);set(HI,0);call("fs.fat.write",RFAT);get(A,FL);get(B,FL+1);s.store(255,C);s.ne(A,C,"fs.free.next");s.eq(B,C,"fs.free.done");s.label("fs.free.next");copy(FL,CL,2);s.jump("fs.free.loop");s.label("fs.free.done");ret(RCHAIN);
        }
        void emitFiles() {
            s.label("fs.handle");get(A,HANDLE);less(A,4,"fs.handle.valid");s.jump("fs.error."+BAD_HANDLE);s.label("fs.handle.valid");s.store(24,B);s.emit(MULTIPLY,A,B,C);s.store(128,B);s.emit(ADD,C,B,C);put(C,HB);ret(RHANDLE);
            s.label("fs.op."+STAT);call("fs.entry.input",RHANDLE);call("fs.entry.decode",RRW);s.jump("fs.reply");
            s.label("fs.op."+OPEN);call("fs.handle.available",RSTREAM);call("fs.entry.input",RHANDLE);call("fs.entry.decode",RRW);get(A,TYPE);value(A,4,"fs.error."+IS_DIRECTORY);call("fs.validate",RCHAIN);set(MODE,1);set(28,0);s.jump("fs.handle.initialize");
            s.label("fs.op."+CREATE);call("fs.name.valid",RHANDLE);call("fs.handle.available",RSTREAM);call("fs.find",RFIND);set(28,0);get(A,STATUS);value(A,NOT_FOUND,"fs.create.new");get(A,TYPE);value(A,4,"fs.error."+IS_DIRECTORY);call("fs.validate",RCHAIN);s.jump("fs.create.ready");s.label("fs.create.new");call("fs.dir.slot",RFIND);addressEntry();set(FIRST,0);set(FIRST+1,0);for(int i=0;i<3;i++)set(SIZE+i,0);copy(124,TYPE,1);call("fs.entry.write",RRW);set(28,1);s.label("fs.create.ready");copy(124,TYPE,1);set(STATUS,0);set(MODE,2);
            // Keep the requested type; find/decode returns an existing entry's type.
            s.label("fs.handle.initialize");hwrite(MODE,0);hwrite(DEVICE,1);for(int i=0;i<2;i++){hwrite(FIRST+i,2+i);hwrite(FIRST+i,4+i);hwrite(FIRST+i,21+i);}for(int i=0;i<3;i++){hwrite(SIZE+i,8+i);hwrite(ADDRESS+i,14+i);}hwrite(TYPE,20);
            set(INDEX,0);for(int i:new int[]{6,7,11,12,13})hwrite(INDEX,i);hwrite(28,19);get(A,MODE);value(A,1,"fs.reply");for(int i:new int[]{2,3,4,5,8,9,10})hwrite(INDEX,i);s.jump("fs.reply");
            s.label("fs.handle.available");call("fs.handle",RHANDLE);hread(0,MODE);get(A,MODE);neValue(A,0,"fs.error."+BAD_HANDLE);ret(RSTREAM);
            s.label("fs.stream.handle");call("fs.handle",RHANDLE);hread(0,MODE);get(A,MODE);value(A,0,"fs.error."+BAD_HANDLE);hread(1,TEMP);get(D,TEMP);s.store(0,A);s.emit(ADD,D,A,10);ret(RSTREAM);
            s.label("fs.op."+READ);call("fs.stream.handle",RSTREAM);get(A,MODE);neValue(A,1,"fs.argument");call("fs.read",RSTREAM);s.jump("fs.reply");
            s.label("fs.read");for(int i=2;i>=0;i--){hread(11+i,POSITION+i);hread(8+i,SIZE+i);get(A,POSITION+i);get(B,SIZE+i);s.ne(A,B,"fs.read.byte");}s.jump("fs.error."+EOF);
            s.label("fs.read.byte");hread(4,CL);hread(5,CL+1);hread(6,AOFF);hread(7,APAGE);get(A,APAGE);value(A,4,"fs.read.next");s.jump("fs.read.address");
            s.label("fs.read.next");call("fs.fat.read",RFAT);copy(LO,CL,2);hwrite(CL,4);hwrite(CL+1,5);set(APAGE,0);hwrite(APAGE,7);
            s.label("fs.read.address");call("fs.address",RADDR);get(A,X);get(B,Y);get(C,Z);s.emit(READ_BYTE_FROM_STORAGE,A,B,C,D);put(D,DATA);call("fs.advance",RHANDLE);ret(RSTREAM);
            s.label("fs.advance");hread(6,AOFF);inc(AOFF);hwrite(AOFF,6);neValue(A,0,"fs.advance.pos");hread(7,APAGE);inc(APAGE);hwrite(APAGE,7);
            s.label("fs.advance.pos");for(int i=0;i<3;i++)hread(11+i,POSITION+i);for(int i=0;i<3;i++){hread(11+i,POSITION+i);inc(POSITION+i);hwrite(POSITION+i,11+i);String done=s.unique("fs.advance.done");neValue(A,0,done);if(i==2){s.label(done);ret(RHANDLE);}else{s.jump("fs.advance.carry."+i);s.label(done);ret(RHANDLE);s.label("fs.advance.carry."+i);}}
            s.label("fs.op."+WRITE);call("fs.stream.handle",RSTREAM);get(A,MODE);neValue(A,2,"fs.argument");call("fs.write",RSTREAM);s.jump("fs.reply");
            s.label("fs.write");hread(4,CL);hread(5,CL+1);hread(6,AOFF);hread(7,APAGE);get(A,CL);get(B,CL+1);s.store(0,C);s.ne(A,C,"fs.write.boundary");s.ne(B,C,"fs.write.boundary");call("fs.allocate",RALLOC);hwrite(CL,2);hwrite(CL+1,3);hwrite(CL,4);hwrite(CL+1,5);s.jump("fs.write.address");
            s.label("fs.write.boundary");get(A,APAGE);neValue(A,4,"fs.write.address");copy(CL,ORIGINAL,2);call("fs.allocate",RALLOC);copy(CL,FL,2);copy(CL,LO,2);copy(ORIGINAL,CL,2);call("fs.fat.write",RFAT);copy(FL,CL,2);hwrite(CL,4);hwrite(CL+1,5);set(APAGE,0);hwrite(APAGE,7);
            s.label("fs.write.address");call("fs.address",RADDR);get(A,X);get(B,Y);get(C,Z);get(D,DATA);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);call("fs.advance",RHANDLE);for(int i=0;i<3;i++)hwrite(POSITION+i,8+i);ret(RSTREAM);
            s.label("fs.op."+SEEK);call("fs.stream.handle",RSTREAM);get(A,MODE);neValue(A,1,"fs.argument");copy(POSITION,ORIGINAL,3);for(int i=2;i>=0;i--){hread(8+i,SIZE+i);get(A,POSITION+i);get(B,SIZE+i);s.emit(COMPARE_UNSIGNED,A,B,C);value(C,2,"fs.argument");value(C,1,"fs.seek.valid");}s.label("fs.seek.valid");hread(2,CL);hread(3,CL+1);copy(POSITION,INDEX,3);
            s.label("fs.seek.cluster");get(A,INDEX+2);neValue(A,0,"fs.seek.more");get(A,INDEX+1);less(A,4,"fs.seek.done");
            s.label("fs.seek.more");call("fs.fat.read",RFAT);copy(LO,CL,2);get(A,INDEX+1);s.store(4,B);s.emit(COMPARE_UNSIGNED,A,B,C);put(C,FLAG);s.emit(SUB,A,B,A);put(A,INDEX+1);get(A,FLAG);neValue(A,1,"fs.seek.cluster");get(A,INDEX+2);s.store(1,B);s.emit(SUB,A,B,A);put(A,INDEX+2);s.jump("fs.seek.cluster");
            s.label("fs.seek.done");hwrite(CL,4);hwrite(CL+1,5);hwrite(INDEX,6);hwrite(INDEX+1,7);for(int i=0;i<3;i++)hwrite(ORIGINAL+i,11+i);s.jump("fs.reply");
            for(int op:new int[]{CLOSE,ABORT}) {
                s.label("fs.op."+op);call("fs.handle",RHANDLE);hread(0,MODE);get(A,MODE);neValue(A,2,"fs.close.clear."+op);
                if(op==CLOSE){for(int i=0;i<3;i++){hread(14+i,EX+i);hread(8+i,SIZE+i);}for(int i=0;i<2;i++)hread(2+i,FIRST+i);hread(20,TYPE);call("fs.entry.read",RRW);for(int i=0;i<12;i++){cache(D,i);put(D,NAME+i);}call("fs.entry.write",RRW);hread(21,CL);hread(22,CL+1);}
                else{hread(2,CL);hread(3,CL+1);}call("fs.free",RCHAIN);
                if(op==ABORT){hread(19,INDEX);get(A,INDEX);value(A,0,"fs.close.clear."+op);for(int i=0;i<3;i++)hread(14+i,EX+i);get(A,EX);get(B,EY);get(C,EZ);s.store(0,D);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);}
                s.label("fs.close.clear."+op);set(INDEX,0);hwrite(INDEX,0);s.jump("fs.reply");
            }
            for(int op:new int[]{READ_BLOCK,WRITE_BLOCK}) {
                s.label("fs.op."+op);call("fs.stream.handle",RSTREAM);get(A,MODE);neValue(A,op==WRITE_BLOCK?2:1,"fs.argument");set(125,0);
                s.label("fs.block.loop."+op);
                if(op==WRITE_BLOCK){get(A,BUFFER_BANK);get(B,BUFFER_PAGE);get(C,BUFFER_OFFSET);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);put(D,DATA);call("fs.write",RSTREAM);}else{call("fs.read",RSTREAM);get(A,BUFFER_BANK);get(B,BUFFER_PAGE);get(C,BUFFER_OFFSET);get(D,DATA);s.emit(STORE_MEM_ON_BANK,D,A,B,C);}
                inc(BUFFER_OFFSET);neValue(A,0,"fs.block.next."+op);inc(BUFFER_PAGE);s.label("fs.block.next."+op);inc(125);get(B,COUNT);s.ne(A,B,"fs.block.loop."+op);s.jump("fs.reply");
            }
        }
        void emitChanges() {
            s.label("fs.op."+MKDIR);call("fs.name.valid",RHANDLE);call("fs.find",RFIND);get(A,STATUS);value(A,OK,"fs.error."+EXISTS);call("fs.dir.slot",RFIND);addressEntry();call("fs.allocate",RALLOC);copy(CL,FIRST,2);call("fs.zero",RZERO);
            // Create . and .. before publishing the directory entry.
            copy(ADDRESS,FREE_X,3);set(APAGE,0);set(AOFF,0);call("fs.address",RADDR);copy(X,EX,3);for(int i=0;i<12;i++){get(D,NAME+i);s.emit(STORE_MEM_ABSOLUTE,D,0,CACHE_PAGE,32+i);set(NAME+i,0);}set(NAME,'.');set(TYPE,4);for(int i=0;i<3;i++)set(SIZE+i,0);call("fs.entry.write",RRW);copy(DIR,FIRST,2);get(A,FIRST);get(B,FIRST+1);s.store(0,C);s.ne(A,C,"fs.mkdir.parent");s.ne(B,C,"fs.mkdir.parent");set(FIRST,JvFileSystem.ROOT);s.label("fs.mkdir.parent");inc(EX+2);get(A,EX+2);s.store(31,B);s.emit(ADD,A,B,A);put(A,EX+2);set(NAME+1,'.');call("fs.entry.write",RRW);
            copy(CL,FIRST,2);copy(FREE_X,EX,3);for(int i=0;i<12;i++){cache(D,32+i);put(D,NAME+i);}call("fs.entry.write",RRW);set(STATUS,OK);s.jump("fs.reply");
            s.label("fs.op."+REMOVE);call("fs.entry.input",RHANDLE);call("fs.entry.decode",RRW);get(A,TYPE);value(A,4,"fs.remove.directory");call("fs.validate",RCHAIN);s.jump("fs.remove.commit");
            s.label("fs.remove.directory");copy(FIRST,DIR,2);copy(ADDRESS,224,3);call("fs.dir.start",RDIR);s.label("fs.remove.scan");call("fs.dir.address",RDIR);cache(A,0);value(A,0,"fs.remove.next");value(A,'.',"fs.remove.next");s.jump("fs.error."+NOT_EMPTY);s.label("fs.remove.next");call("fs.dir.advance",RDIR);get(A,FLAG);value(A,0,"fs.remove.scan");copy(224,ADDRESS,3);copy(ADDRESS,EX,3);call("fs.entry.read",RRW);call("fs.entry.decode",RRW);
            s.label("fs.remove.commit");copy(FIRST,CL,2);copy(ADDRESS,EX,3);get(A,EX);get(B,EY);get(C,EZ);s.store(0,D);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);call("fs.free",RCHAIN);s.jump("fs.reply");
            s.label("fs.op."+RENAME);s.jump("fs.move.start");s.label("fs.op."+MOVE);
            s.label("fs.move.start");call("fs.entry.input",RHANDLE);call("fs.entry.decode",RRW);copy(ADDRESS,224,3);copy(FIRST,227,2);copy(TYPE,229,1);copy(SIZE,230,3);copy(DIR,233,2);
            for(int i=0;i<12;i++){get(D,NAME+i);s.emit(STORE_MEM_ABSOLUTE,D,0,CACHE_PAGE,64+i);get(D,NEW_NAME+i);put(D,NAME+i);}
            get(A,OP);value(A,RENAME,"fs.move.rename");get(A,NAME);value(A,'/',"fs.move.root");value(A,'\\',"fs.move.root");value(A,'.',"fs.move.parent");
            s.label("fs.move.rename");call("fs.name.valid",RHANDLE);call("fs.find",RFIND);get(A,STATUS);value(A,NOT_FOUND,"fs.move.same.parent");get(A,OP);value(A,RENAME,"fs.error."+EXISTS);get(A,TYPE);neValue(A,4,"fs.error."+EXISTS);copy(FIRST,235,2);s.jump("fs.move.original.name");
            s.label("fs.move.same.parent");copy(233,235,2);s.jump("fs.move.check");
            s.label("fs.move.root");set(235,JvFileSystem.ROOT);set(236,0);s.jump("fs.move.original.name");
            s.label("fs.move.parent");get(A,NAME+1);neValue(A,'.',"fs.argument");get(A,NAME+2);neValue(A,0,"fs.argument");copy(233,DC,2);set(DR,1);call("fs.dir.address",RDIR);cache(D,14);put(D,235);cache(D,15);put(D,236);
            s.label("fs.move.original.name");for(int i=0;i<12;i++){cache(D,64+i);put(D,NAME+i);}
            s.label("fs.move.check");for(int i=0;i<12;i++){get(D,NAME+i);s.emit(STORE_MEM_ABSOLUTE,D,0,CACHE_PAGE,96+i);}get(A,229);neValue(A,4,"fs.move.destination");copy(235,DC,2);set(HOPS,0);set(HOPS+1,0);
            s.label("fs.move.ancestor");get(A,DC);get(B,227);String different=s.unique("fs.move.different");s.ne(A,B,different);get(A,DC+1);get(B,228);s.eq(A,B,"fs.argument");s.label(different);get(A,DC);neValue(A,JvFileSystem.ROOT,"fs.move.ancestor.next");get(A,DC+1);value(A,0,"fs.move.destination");
            s.label("fs.move.ancestor.next");set(DR,1);call("fs.dir.address",RDIR);cache(D,14);put(D,DC);cache(D,15);put(D,DC+1);inc16(HOPS);get(A,HOPS+1);value(A,64,"fs.corrupt");s.jump("fs.move.ancestor");
            s.label("fs.move.destination");for(int i=0;i<12;i++){cache(D,96+i);put(D,NAME+i);}copy(235,DIR,2);call("fs.find",RFIND);get(A,STATUS);value(A,NOT_FOUND,"fs.move.slot");for(int i=0;i<3;i++){get(A,ADDRESS+i);get(B,224+i);s.ne(A,B,"fs.error."+EXISTS);}s.jump("fs.reply");
            s.label("fs.move.slot");get(A,233);get(B,235);s.ne(A,B,"fs.move.other.parent");get(A,234);get(B,236);s.ne(A,B,"fs.move.other.parent");copy(224,EX,3);s.jump("fs.move.commit");s.label("fs.move.other.parent");call("fs.dir.slot",RFIND);
            s.label("fs.move.commit");copy(EX,237,3);copy(227,FIRST,2);copy(229,TYPE,1);copy(230,SIZE,3);call("fs.entry.write",RRW);
            for(int i=0;i<3;i++){get(A,224+i);get(B,237+i);String same=s.unique("fs.move.same.slot");s.eq(A,B,same);get(A,224);get(B,225);get(C,226);s.store(0,D);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);s.jump("fs.move.parent.update");s.label(same);}
            s.label("fs.move.parent.update");get(A,229);neValue(A,4,"fs.move.done");copy(227,CL,2);set(APAGE,0);set(AOFF,46);call("fs.address",RADDR);get(A,X);get(B,Y);get(C,Z);get(D,235);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);s.emit(INCREMENT_REGISTER,C);get(D,236);s.emit(WRITE_BYTE_TO_STORAGE,A,B,C,D);s.label("fs.move.done");set(STATUS,OK);s.jump("fs.reply");
        }
    }
}
