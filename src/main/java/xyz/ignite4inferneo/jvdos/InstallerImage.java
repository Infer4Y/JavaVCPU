package xyz.ignite4inferneo.jvdos;

import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import xyz.ignite4inferneo.OPCode;
import static xyz.ignite4inferneo.OPCode.*;

/** Reusable self-extracting native DOS COM installer; payload programs are never executed. */
public final class InstallerImage {
    private static final int A=0,B=1,C=2,D=3,E=4,F=5,I=12,J=13,K=14;
    private static final int TPA=248,MAIL=239;
    public static final int SOURCE_DRIVE=64,REPLACE=65,PHASE=66;
    private static final int PAGE=67,END=68,TAIL=69,STR_PAGE=70,STR_OFFSET=71,PART=72,
            TEXT_RETURN=74,FILE_RETURN=76;
    private static DesktopImage.Asm s;
    private InstallerImage() { }
    private static void get(int slot,int reg){s.emit(LOAD_MEM_ABSOLUTE,0,TPA,slot,reg);}
    private static void put(int slot,int reg){s.emit(STORE_MEM_ABSOLUTE,reg,0,TPA,slot);}
    private static void set(int slot,int value){s.store(value,D);put(slot,D);}
    private static void fget(int slot,int reg){s.emit(LOAD_MEM_ABSOLUTE,1,MAIL,slot,reg);}
    private static void fset(int slot,int value){s.store(value,D);s.emit(STORE_MEM_ABSOLUTE,D,1,MAIL,slot);}
    private static void fs(int op){
        fset(FileSystemAbi.OP,op);s.store(1,A);s.store(MAIL,B);s.store(1,17);String back=s.unique("setup.fs");s.ref(back,true,18);s.ref(back,false,19);
        s.reserve(13);s.store(0,I);s.store(249,J);s.store(FileSystemAbi.ENTRY,K);s.emit(CHANGE_BANK_PAGE_JUMP,I,J,K);s.label(back);
    }
    private static void call(String label,int slot){String back=s.unique("setup.return");s.ref(back,true,E);put(slot,E);s.ref(back,false,E);put(slot+1,E);s.jump(label);s.label(back);}
    private static void ret(int slot){get(slot,I);get(slot+1,J);s.emit(CHANGE_PAGE_JUMP,I,J);}
    private static void text(String text){String label=s.strings.computeIfAbsent(text,t->s.unique("setup.literal"));s.ref(label,true,D);put(STR_PAGE,D);s.ref(label,false,D);put(STR_OFFSET,D);call("text",TEXT_RETURN);}

    public static byte[] program(List<DesktopBundle.Component> components) {
        if(components.isEmpty())throw new IllegalArgumentException("An installer needs programs");
        var seen=new HashSet<String>();for(var component:components){
            if(!component.name().matches("[A-Z0-9_-][A-Z0-9_.-]{0,11}")||!seen.add(component.name()))throw new IllegalArgumentException("Invalid or duplicate program name");
            validateProgram(component.bytes());
        }
        int prefix=1;byte[] code=null;
        for(int n=0;n<4;n++) {int start=prefix;code=DesktopImage.assembleOptimized(asm->emit(asm,components,start)).bytes();if(code.length/256==prefix)break;prefix=code.length/256;}
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.writeBytes(code);
        for(var component:components){out.writeBytes(component.bytes());while(out.size()%256!=0)out.write(STOP.code());}
        if(out.size()>239*256)throw new IllegalArgumentException("Installer exceeds the native COM limit (including setup code)");
        return out.toByteArray();
    }
    private static void validateProgram(byte[] bytes) {
        if(bytes.length==0)throw new IllegalArgumentException("Empty program");
        String[] operands={"rrr","rrr","lr","l","","r","rrl","rl","rl","r","rr","rrrl","rrr","","","rrr","lr","rrr","rrrr","rrrr","rrr","r","rrr","rrr","rrrr","rrrr","rrr","lllr","rlll","rr","rr"};
        for(int p=0;p<bytes.length;){int op=bytes[p]&255;if(op>=OPCode.values().length)throw new IllegalArgumentException("Invalid COM opcode");int length=OPCode.values()[op].length();if(p+length>bytes.length||(p&255)+length>256)throw new IllegalArgumentException("COM instruction crosses a boundary");for(int i=0;i<operands[op].length();i++)if(operands[op].charAt(i)=='r'&&(bytes[p+i+1]&255)>=20)throw new IllegalArgumentException("Invalid COM register");p+=length;}
    }
    private static void emit(DesktopImage.Asm asm,List<DesktopBundle.Component> components,int firstPage) {
        s=asm;s.packStrings=true;
        s.emit(CLEAR_GRA);s.store(4,D);s.emit(STORE_MEM_ABSOLUTE,D,0,252,129);s.emit(STORE_MEM_ABSOLUTE,D,0,252,130);
        // The legacy character service returns into bank zero. This tiny trampoline
        // returns to the bank-one setup loop; all other TPA state is above byte 64.
        int[] trampoline={STORE.code(),1,A,STORE.code(),0,B,STORE.code(),0,C,CHANGE_BANK_PAGE_JUMP.code(),A,B,C};
        for(int n=0;n<trampoline.length;n++){s.store(trampoline[n],D);put(n,D);}s.ref("text.printed",true,D);put(4,D);s.ref("text.printed",false,D);put(7,D);
        s.store(0,A);s.emit(ADD,10,A,D);put(SOURCE_DRIVE,D);set(REPLACE,0);set(PHASE,1);
        // Only initialize mailbox fields this client uses, preserving loaded payload RAM.
        fset(FileSystemAbi.HANDLE,3);fset(FileSystemAbi.DIR,JvFileSystem.ROOT);fset(FileSystemAbi.DIR+1,0);fset(FileSystemAbi.TYPE,0);fset(FileSystemAbi.BUFFER_BANK,1);fset(FileSystemAbi.BUFFER_OFFSET,0);
        text("SETUP -> DRIVE 0 ROOT\n");
        for(var component:components)text(component.name()+"\n");
        text("Y INSTALL MISSING / R REPLACE / ESC CANCEL\n");
        s.label("confirm");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.equal(D,27,"quit");s.equal(D,'R',"replace");s.notEqual(D,'Y',"confirm");s.jump("install");s.label("replace");set(REPLACE,1);
        s.label("install");set(PHASE,0);s.store(0,10);
        int page=firstPage;
        for(var component:components){
            for(int n=0;n<12;n++)fset(FileSystemAbi.NAME+n,n<component.name().length()?component.name().charAt(n):0);
            set(PAGE,page);set(END,page+component.bytes().length/256);set(TAIL,component.bytes().length&255);call("file",FILE_RETURN);
            page+=(component.bytes().length+255)/256;
        }
        set(PHASE,2);text("SETUP COMPLETE\n");s.jump("quit");
        s.label("error");fs(FileSystemAbi.ABORT);set(PHASE,3);text("SETUP STOPPED - CHECK DISK / FREE SPACE\n");s.jump("quit");
        s.label("quit");get(SOURCE_DRIVE,10);s.store(0,A);s.store(249,B);s.store(9,C);s.emit(CHANGE_BANK_PAGE_JUMP,A,B,C);
        s.label("file");fs(FileSystemAbi.LOOKUP);fget(FileSystemAbi.STATUS,A);s.equal(A,FileSystemAbi.NOT_FOUND,"file.create");s.notEqual(A,0,"error");fget(FileSystemAbi.TYPE,A);s.equal(A,4,"error");get(REPLACE,A);s.equal(A,0,"file.done");
        s.label("file.create");fset(FileSystemAbi.TYPE,0);fs(FileSystemAbi.CREATE);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"error");
        s.label("file.block");get(PAGE,A);get(END,B);s.eq(A,B,"file.tail");fset(FileSystemAbi.COUNT,0);s.jump("file.write");
        s.label("file.tail");get(TAIL,A);s.equal(A,0,"file.close");s.emit(STORE_MEM_ABSOLUTE,A,1,MAIL,FileSystemAbi.COUNT);
        s.label("file.write");get(PAGE,A);s.emit(STORE_MEM_ABSOLUTE,A,1,MAIL,FileSystemAbi.BUFFER_PAGE);fs(FileSystemAbi.WRITE_BLOCK);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"error");get(PAGE,A);get(END,B);s.eq(A,B,"file.close");s.emit(INCREMENT_REGISTER,A);put(PAGE,A);s.jump("file.block");
        s.label("file.close");fs(FileSystemAbi.CLOSE);fget(FileSystemAbi.STATUS,A);s.notEqual(A,0,"error");s.label("file.done");ret(FILE_RETURN);
        s.label("text");set(PART,0);s.label("text.byte");get(STR_PAGE,J);get(STR_OFFSET,K);s.store(1,I);s.emit(LOAD_MEM_ON_BANK,I,J,K,D);s.equal(D,0,"text.done");
        s.store(TPA,A);s.store(0,B);s.reserve(13);s.store(0,I);s.store(249,J);s.store(0,K);s.emit(CHANGE_BANK_PAGE_JUMP,I,J,K);
        s.label("text.printed");get(STR_OFFSET,A);s.emit(INCREMENT_REGISTER,A);put(STR_OFFSET,A);get(PART,A);s.emit(INCREMENT_REGISTER,A);put(PART,A);s.notEqual(A,3,"text.byte");set(PART,0);get(STR_OFFSET,A);s.store(2,B);s.emit(ADD,A,B,A);put(STR_OFFSET,A);s.jump("text.byte");s.label("text.done");ret(TEXT_RETURN);
    }
    public static void main(String[] args)throws Exception {
        if(args.length<1)throw new IllegalArgumentException("Output COM and optional NAME.COM=path components");
        List<DesktopBundle.Component> components=new ArrayList<>();
        for(int n=1;n<args.length;n++){int split=args[n].indexOf('=');if(split<=0)throw new IllegalArgumentException("Use NAME.COM=path");components.add(new DesktopBundle.Component(args[n].substring(0,split).toUpperCase(Locale.ROOT),Files.readAllBytes(Path.of(args[n].substring(split+1)))));}
        if(components.isEmpty())components=DesktopBundle.components();byte[] bytes=program(components);Files.write(Path.of(args[0]),bytes);System.out.println("SETUP COM: "+bytes.length+" bytes / "+components.size()+" programs");
    }
}
