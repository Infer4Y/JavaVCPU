package xyz.ignite4inferneo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;


/** Headless regression checks; runs the real BIOS/DOS bytecode with stubbed peripherals. */
public final class DosStorageRegression {
    private final byte[][] ram = new byte[512][256];
    private final int[] r = new int[12];
    private int note60Updates;
    private final int[][] pixels = new int[256][256];
    private final ArrayDeque<Integer> keys = new ArrayDeque<>();

    public static void main(String[] args) throws Exception {
        MemoryRegression.run();
        Path image = Path.of(args.length == 0 ? "build/check/regression.img" : args[0]);
        Files.deleteIfExists(image);
        Storage.load(image);
        check(Files.size(image) == Storage.STORAGE_SIZE, "new disk size");
        Storage.writeData((byte)255, (byte)255, (byte)255, (byte)123);
        check(Storage.readData((byte)255,(byte)255,(byte)255) == 123, "unsigned addressing");
        Storage.writeRange(Storage.STORAGE_SIZE, new byte[0]);
        reject(() -> Storage.readRange(Integer.MAX_VALUE, 2));
        reject(() -> Storage.readRange(0, -1));
        reject(() -> Storage.writeRange(Storage.STORAGE_SIZE - 1, new byte[2]));
        Storage.save();
        Storage.load(image);
        check(Storage.readRange(Storage.STORAGE_SIZE - 1, 1)[0] == 123, "disk persistence");

        for (int slot=0;slot<16;slot++) file(2,slot,"F"+slot+".TXT","OLD");
        file(2,254,"LAST.TXT","END");
        Storage.writeRange(0x2ff00, new byte[]{0x4a});
        DiskInstaller.installIfMissing();
        check(Storage.readRange(0,8)[5] == 10, "system revision");
        check(Storage.readRange(0x20010,3)[0] == 'O', "installation preserves files");
        DosStorageRegression vm = new DosStorageRegression();
        byte[][] pages = Programs.bootImage();
        check(pages.length <= 255, "boot image fits RAM");
        for(int p=0;p<pages.length;p++) System.arraycopy(pages[p],0,vm.ram[p],0,pages[p].length);
        vm.run();
        vm.command("WRITE NEW.TXT HELLO");
        check(name(2,16).equals("NEW.TXT"), "allocation beyond original 16 slots");
        check(text(2,16).equals("HELLO"), "new file data");
        vm.command("DEL LAST.TXT");
        check(name(2,254).isEmpty(), "lookup includes slot 254");
        vm.command("BANK +");
        vm.command("WRITE OTHER.TXT WORLD");
        check(name(3,0).equals("OTHER.TXT") && text(3,0).equals("WORLD"), "bank selection");
        check(name(2,0).equals("F0.TXT"), "bank isolation");
        file(3,1,"MATH.JVS","STORE 6 A\nSTORE 7 B\nMULTIPLY A B C\nEND\n");
        vm.command("RUN MATH.JVS");
        check((vm.ram[252][226]&255)==42,"JVS arithmetic in selected bank");
        vm.command("COPY OTHER.TXT COPY.TXT");
        check(text(3,2).equals("WORLD"),"copy within bank");
        byte[] bankProgram={2,1,0,2,(byte)255,1,2,(byte)255,2,2,42,3,25,3,0,1,2,24,0,1,2,4,2,(byte)249,6,2,9,7,10,6,7};
        byte[] com=new byte[256];com[0]=(byte)bankProgram.length;
        byte[] comName="BANK.COM".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(comName,0,com,1,comName.length);System.arraycopy(bankProgram,0,com,16,bankProgram.length);
        Storage.writeRange(0x30300,com);vm.command("RUN BANK.COM");
        check((vm.ram[511][255]&255)==42,"COM loader accepts bank-memory opcodes");
        vm.command("BANK -");
        vm.command("DEL NEW.TXT");
        check(name(2,16).isEmpty(), "return to original bank");
        vm.command("BANK -");
        check((vm.ram[252][220]&255)==2, "lower bank boundary");
        vm.ram[252][220]=(byte)255;
        vm.command("BANK +");
        check((vm.ram[252][220]&255)==255, "upper bank boundary");
        vm.command("BANK ++");
        check((vm.ram[252][220]&255)==255, "invalid bank argument");
        for(int slot=0;slot<255;slot++)file(4,slot,"F"+slot+".TXT","FULL");
        vm.ram[252][220]=4;
        vm.command("WRITE EXTRA.TXT DATA");
        check(name(4,254).equals("F254.TXT"),"full directory preserves final file");
        check(Storage.readRange(0x4ff01,1)[0]==0,"reserved slot remains unused");
        vm.command("DEL F254.TXT");
        vm.command("WRITE EXTRA.TXT DATA");
        check(name(4,254).equals("EXTRA.TXT"),"allocation reuses slot 254");
        // Multi-block files retain their head entries while consuming continuation slots.
        vm.ram[252][220]=5;
        String large="ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(24);
        chain(5,0,"LARGE.TXT",large.getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        vm.command("APPEND LARGE.TXT XYZ");
        check(chainText(5,0).equals(large+"XYZ"),"append across chained blocks");
        vm.command("COPY LARGE.TXT COPY.TXT");
        int copied=find(5,"COPY.TXT");check(copied>=0&&chainText(5,copied).equals(large+"XYZ"),"large copy");
        vm.command("REN COPY.TXT RENAMED.TXT");check(find(5,"RENAMED.TXT")==copied,"rename retains chain");
        vm.command("EDIT LARGE.TXT");vm.press(35,88,27);
        check(chainText(5,0).equals(large+"XYZX"),"editor End and save beyond byte 255");
        vm.command("EDIT LARGE.TXT");vm.press(36,39,90,127,27);
        check(chainText(5,0).equals("AZ"+large.substring(2)+"XYZX"),"editor insertion and deletion");
        vm.command("TYPE LARGE.TXT");
        vm.command("DEL RENAMED.TXT");check(name(5,copied).isEmpty(),"delete large file");
        String script="REM PADDING FOR A LONG SOURCE FILE\n".repeat(30)+"STORE 6 A\nSTORE 7 B\nMULTIPLY A B C\nEND\n";
        chain(6,0,"LONG.JVS",script.getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        vm.ram[252][220]=6;vm.command("RUN LONG.JVS");
        check((vm.ram[252][226]&255)==42,"large JVS execution");
        byte[] example=Files.readAllBytes(Path.of("examples/dos/LONG.JVS"));
        chain(15,0,"LONG.JVS",example,1);vm.ram[252][220]=15;vm.command("RUN LONG.JVS");
        check((vm.ram[252][225]&255)==55,"multi-block GOTO/IF example");
        // An extended COM uses bank-one code pages and explicitly returns to bank-zero DOS.
        byte[] program=new byte[300];java.util.Arrays.fill(program,(byte)4);
        byte[] entry={2,0,0,2,(byte)249,1,2,9,2,23,0,1,2};System.arraycopy(entry,0,program,0,entry.length);
        chain(7,0,"LARGE.COM",program,0);vm.ram[252][220]=7;vm.command("RUN LARGE.COM");
        check(vm.r[11]==0,"large COM returns across RAM banks");
        // Growth fails before writing either the existing file or its neighbors.
        chain(8,0,"TIGHT.TXT","A".repeat(240).getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        for(int slot=1;slot<255;slot++)file(8,slot,"F"+slot+".TXT","FULL");
        byte[] beforeFull=Storage.readRange(8<<16,65536);vm.ram[252][220]=8;vm.command("APPEND TIGHT.TXT X");
        check(java.util.Arrays.equals(beforeFull,Storage.readRange(8<<16,65536)),"disk-full append is unchanged");
        byte[] tune=new byte[1024];tune[0]=74;tune[1]=77;tune[2]=1;tune[3]=(byte)255;
        for(int event=0;event<255;event++)tune[4+event*4+2]=60;
        chain(10,0,"LONG.JMT",tune,3);vm.ram[252][220]=10;int beforeNotes=vm.note60Updates;vm.command("PLAY LONG.JMT");
        check(vm.note60Updates-beforeNotes==255,"music streams all 255 events");
        byte[] extendedTune=new byte[61200];extendedTune[0]=74;extendedTune[1]=77;extendedTune[2]=2;
        for(int event=0;event<15299;event++){extendedTune[4+event*4+1]=(byte)(event%8);extendedTune[4+event*4+2]=60;}
        chain(16,0,"MAX.JMT",extendedTune,3);vm.ram[252][220]=16;beforeNotes=vm.note60Updates;vm.command("PLAY MAX.JMT");
        check(vm.note60Updates-beforeNotes==15299,"music streams maximum-size version 2 tune");
        byte[] invalidTune=java.util.Arrays.copyOf(extendedTune,9);
        chain(17,0,"BAD.JMT",invalidTune,3);vm.ram[252][220]=17;beforeNotes=vm.note60Updates;vm.command("PLAY BAD.JMT");
        check(vm.note60Updates==beforeNotes,"partial music event is rejected");

        // A full-size file consumes all 255 blocks, and an empty overwrite releases its tail.
        chain(11,0,"MAX.TXT","M".repeat(61200).getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        vm.ram[252][220]=11;vm.command("WRITE MAX.TXT");
        check(chainText(11,0).isEmpty()&&name(11,254).isEmpty(),"maximum-size file loads and shrinks");
        // Text paging crosses multiple file blocks and supports going back.
        chain(12,0,"DOC.TXT","LINE\n".repeat(100).getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        vm.ram[252][220]=12;vm.command("VIEW DOC.TXT");vm.press(40,40,38,27);
        // Lengths spanning both the disk block boundary and the RAM page boundary.
        for(int length:new int[]{239,240,241,255,256,257,4096}){
            byte[] blank=new byte[65536];Storage.writeRange(13<<16,blank);
            String boundary="B".repeat(length);chain(13,0,"EDGE.TXT",boundary.getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
            vm.ram[252][220]=13;vm.command("APPEND EDGE.TXT X");
            check(chainText(13,0).equals(boundary+"X"),"guest length boundary "+length);
        }
        // Truncated and cyclic links cannot be used for writes or deletes.
        chain(9,0,"BAD.TXT","B".repeat(500).getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        Storage.writeRange((9<<16)+256+15,new byte[]{1});byte[] corrupt=Storage.readRange(9<<16,65536);
        vm.ram[252][220]=9;vm.command("DEL BAD.TXT");
        check(java.util.Arrays.equals(corrupt,Storage.readRange(9<<16,65536)),"corrupt chain is rejected safely");
        // Two heads cannot share a continuation: deleting one must preserve both.
        chain(14,0,"FIRST.TXT","S".repeat(500).getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        file(14,3,"SECOND.TXT","X");Storage.writeRange((14<<16)+(3<<8),new byte[]{(byte)244});
        Storage.writeRange((14<<16)+(3<<8)+14,new byte[]{1,1});
        byte[] shared=Storage.readRange(14<<16,65536);vm.ram[252][220]=14;vm.command("DEL FIRST.TXT");
        check(java.util.Arrays.equals(shared,Storage.readRange(14<<16,65536)),"shared continuation is rejected");
        shellNavigationChecks();
        // Image rendering crosses disk blocks and RAM pages using the entire display area.
        for(int[] size:new int[][]{{2,2},{15,15},{16,17},{240,240},{1,240},{240,1}}) {
            int width=size[0],height=size[1];byte[] picture=new byte[4+width*height];
            picture[0]=74;picture[1]=73;picture[2]=(byte)width;picture[3]=(byte)height;
            for(int i=4;i<picture.length;i++)picture[i]=(byte)((i-4)%127+1);
            chain(18,0,"IMAGE.IMG",picture,2);vm.ram[252][220]=18;vm.command("IMAGE IMAGE.IMG");
            int scale=width<=15&&height<=15?8:1,left=scale==8?68:8,top=scale==8?32:16;
            for(int y=0;y<height*scale;y++)for(int x=0;x<width*scale;x++)
                check(vm.pixels[top+y][left+x]==(picture[4+(y/scale)*width+x/scale]&255),"image pixel "+width+"x"+height+" at "+x+","+y);
            vm.press(27);
        }
        byte[] badImage={74,73,2,2,1,2,3};
        chain(19,0,"BAD.IMG",badImage,2);vm.ram[252][220]=19;vm.command("IMAGE BAD.IMG");
        vm.command("WRITE RESULT.TXT OK");check(find(19,"RESULT.TXT")>=0,"truncated image returns to shell");
        chain(19,0,"BAD.IMG",new byte[]{74,73,(byte)241,1,0},2);vm.command("IMAGE BAD.IMG");
        vm.command("WRITE WIDE.TXT OK");check(find(19,"WIDE.TXT")>=0,"oversized image returns to shell");
        chain(19,0,"BAD.IMG",new byte[]{74,73,1,1,(byte)128},2);vm.command("IMAGE BAD.IMG");
        vm.command("WRITE COLOR.TXT OK");check(find(19,"COLOR.TXT")>=0,"invalid image palette returns to shell");

        Storage.save();
        System.out.println("PASS: disk persistence, compatible files, linked-file limits, editing/copying/deletion, JVS/COM/music, paging and corrupt/full-bank preservation");
    }

    private static DosStorageRegression terminal() {
        DosStorageRegression terminal=new DosStorageRegression();byte[][] pages=Programs.bootImage();
        for(int page=0;page<pages.length;page++)System.arraycopy(pages[page],0,terminal.ram[page],0,pages[page].length);
        terminal.run();return terminal;
    }
    private String line(){return new String(ram[251],64,ram[252][131]&255,java.nio.charset.StandardCharsets.US_ASCII);}
    private int cursor(){return (ram[252][160]&255)|((ram[253][2]&255)<<8);}
    private void type(String text){for(char c:text.toCharArray())keys.add((int)c);run();}
    private static void shellNavigationChecks() throws Exception {
        DosStorageRegression shell=terminal();shell.press(38,40);check(shell.line().isEmpty(),"empty history");
        shell.command("ECHO FIRST");shell.command("ECHO SECOND");shell.type("ECHO DRAFT");shell.press(37,37);
        int draftCursor=shell.cursor();shell.press(38);check(shell.line().equals("ECHO SECOND"),"history newest");
        shell.press(38,38);check(shell.line().equals("ECHO FIRST"),"history oldest boundary");
        shell.press(40);check(shell.line().equals("ECHO SECOND"),"history forward");
        shell.press(40,40);check(shell.line().equals("ECHO DRAFT")&&shell.cursor()==draftCursor,"history restores draft and cursor");
        shell.press(27,38,35,88,10,38);check(shell.line().equals("ECHO SECONDX"),"edit and execute recalled command");
        shell.press(27);int count=shell.ram[253][36]&255;shell.press(10);check((shell.ram[253][36]&255)==count,"blank command excluded");
        DosStorageRegression ring=terminal();for(int i=0;i<20;i++)ring.command("ECHO H"+i);
        for(int i=0;i<20;i++)ring.press(38);check(ring.line().equals("ECHO H4"),"history ring keeps latest sixteen");
        for(int i=0;i<16;i++)ring.press(40);check(ring.line().isEmpty(),"history ring restores empty draft");
        String maximum="ECHO "+"A".repeat(95);ring.command(maximum);ring.press(38);check(ring.line().equals(maximum),"full 100-character history line");
        ring.press(27);ring.command("REBOOT");ring.press(38);check(ring.line().isEmpty(),"reboot clears history");
        DosStorageRegression completion=terminal();completion.type("HE");completion.press(9);check(completion.line().equals("HELP "),"command completion");
        completion.press(27);completion.type("V");completion.press(9);check(completion.line().equals("VIEW "),"first ambiguous command");
        completion.press(9);check(completion.line().equals("VER "),"command completion cycles");completion.press(9);check(completion.line().equals("VIEW "),"command completion wraps");
        completion.press(27);completion.type("ZZZZ");completion.press(9,9);check(completion.line().equals("ZZZZ"),"unmatched Tab leaves input unchanged");
        file(16,0,"ALPHA.TXT","");file(16,1,"ALPINE.TXT","");file(16,2,"ABCDEFGHIJKL","");
        chain(16,3,"LONG.TXT","A".repeat(500).getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);file(17,0,"ALOTHER.TXT","");
        completion.ram[252][220]=16;completion.press(27);completion.type("RUN AL");completion.press(9);check(completion.line().equals("RUN ALPHA.TXT "),"filename completion");
        completion.press(9);check(completion.line().equals("RUN ALPINE.TXT "),"filename completion cycles");completion.press(9);check(completion.line().equals("RUN ALPHA.TXT "),"completion stays in selected bank");
        completion.press(27);completion.type("COPY ALPHA.TXT AL");completion.press(9);check(completion.line().equals("COPY ALPHA.TXT ALPHA.TXT "),"second filename completion");
        completion.press(27);completion.type("RUN ALZZ EXTRA");completion.press(36,39,39,39,39,39,39,9);
        check(completion.line().equals("RUN ALPHA.TXT EXTRA")&&completion.cursor()==13,"completion at cursor preserves suffix");
        completion.press(27);completion.type("RUZ ALPHA.TXT");completion.press(36,39,39,9);check(completion.line().equals("RUN ALPHA.TXT"),"command completion preserves arguments");
        completion.press(27);completion.type("TYPE ABC");completion.press(9);check(completion.line().equals("TYPE ABCDEFGHIJKL "),"full twelve-character name completion");
        completion.press(27);completion.type("TYPE ");completion.press(9,9,9,9);check(completion.line().equals("TYPE LONG.TXT "),"continuations are hidden from completion");
        completion.press(9);check(completion.line().equals("TYPE ALPHA.TXT "),"empty filename prefix wraps");
        completion.press(27);completion.type("ECHO AL");completion.press(9);check(completion.line().equals("ECHO AL"),"text arguments are not filename-completed");
        completion.press(27);completion.type("V "+"A".repeat(98));completion.press(36,39,9);check(completion.line().equals("V "+"A".repeat(98)),"completion respects line limit");
        DosStorageRegression script=terminal();script.ram[252][220]=15;script.command("RUN LONG.JVS");
        check((script.ram[253][36]&255)==1,"script statements do not enter history");script.press(38);check(script.line().equals("RUN LONG.JVS"),"history survives file buffer use");
        DosStorageRegression editor=terminal();editor.ram[252][220]=16;editor.command("EDIT ALPHA.TXT");editor.press(65,9,66,38,40,27);
        check(chainText(16,0).equals("A    B"),"editor retains tab indentation and arrow behavior");
        check((editor.ram[253][36]&255)==1,"editor text does not enter history");
        System.out.println("PASS: shell history/draft/ring/reboot and command/filename Tab cycling, cursor suffixes, line limits and editor isolation");
    }

    private static void file(int bank,int slot,String name,String text) throws Exception {
        byte[] data=new byte[256]; data[0]=(byte)text.length(); data[13]=1;
        System.arraycopy(name.getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,data,1,name.length());
        System.arraycopy(text.getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,data,16,text.length());
        Storage.writeRange((bank<<16)+(slot<<8),data);
    }
    private static String name(int bank,int slot) {
        byte[] b=Storage.readRange((bank<<16)+(slot<<8)+1,12); int n=0;
        while(n<b.length&&b[n]!=0)n++;
        return new String(b,0,n,java.nio.charset.StandardCharsets.US_ASCII);
    }
    private static String text(int bank,int slot) {
        int p=(bank<<16)+(slot<<8);
        return new String(Storage.readRange(p+16,Storage.readRange(p,1)[0]&255),java.nio.charset.StandardCharsets.US_ASCII);
    }
    private void command(String text) {
        for(char c:text.toCharArray()) keys.add(c=='+'?107:c=='-'?109:(int)c);
        keys.add(10); run();
    }
    private void press(int... codes) { for(int code:codes)keys.add(code);run(); }
    private static void chain(int bank,int head,String name,byte[] content,int type) throws Exception {
        int blocks=Math.max(1,(content.length+239)/240);
        for(int block=0;block<blocks;block++){
            byte[] bytes=new byte[256];int slot=head+block;
            if(block==0){bytes[0]=(byte)content.length;bytes[14]=(byte)(content.length>>>8);bytes[13]=(byte)type;
                System.arraycopy(name.getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,bytes,1,name.length());}
            else bytes[1]=(byte)255;
            bytes[15]=(byte)(block+1<blocks?slot+1:0);
            int length=Math.min(240,content.length-block*240);System.arraycopy(content,block*240,bytes,16,length);
            Storage.writeRange((bank<<16)+(slot<<8),bytes);
        }
    }
    private static int find(int bank,String target){for(int slot=0;slot<255;slot++)if(name(bank,slot).equals(target))return slot;return -1;}
    private static String chainText(int bank,int head){
        int p=(bank<<16)+(head<<8);byte[] header=Storage.readRange(p,16);
        int length=(header[0]&255)+((header[14]&255)<<8),position=0,slot=head;byte[] bytes=new byte[length];
        do {int count=Math.min(240,length-position);p=(bank<<16)+(slot<<8);System.arraycopy(Storage.readRange(p+16,count),0,bytes,position,count);
            position+=count;slot=Storage.readRange(p+15,1)[0]&255;}while(position<length);
        return new String(bytes,java.nio.charset.StandardCharsets.US_ASCII);
    }
    private void run() {
        for(int steps=0;steps<200_000_000;steps++) {
            byte[] m=ram[(r[11]<<8)|r[9]]; int pc=r[8],op=m[pc]&255;
            int a=m[(pc+1)&255]&255,b=m[(pc+2)&255]&255,c=m[(pc+3)&255]&255,d=m[(pc+4)&255]&255;
            int length=OPCode.values()[op].length(); r[8]=(pc+length)&255;
            switch(op) {
                case 0 -> r[c]=(r[a]+r[b])&255;
                case 1 -> r[c]=(r[a]-r[b])&255;
                case 2 -> r[b]=a;
                case 3 -> r[8]=a;
                case 4 -> throw new AssertionError("Unexpected HALT");
                case 5,14 -> { }
                case 12 -> pixels[r[b]][r[a]]=r[c];
                case 13 -> {for(int[] row:pixels)java.util.Arrays.fill(row,0);}
                case 17 -> { if(r[b]==60)note60Updates++; }
                case 6 -> { if(r[a]==r[b])r[8]=c; }
                case 7 -> r[a]=m[b]&255;
                case 8 -> m[b]=(byte)r[a];
                case 9 -> r[9]=r[a];
                case 10 -> { r[9]=r[a]; r[8]=r[b]; }
                case 11 -> { if(r[b]==r[c]) {r[9]=r[a];r[8]=d;} }
                case 15 -> r[c]=0;
                case 16 -> {
                    if(a==0&&keys.isEmpty()&&ram[252][176]==0) {r[8]=pc;return;}
                    r[b]=a==0&&!keys.isEmpty()?keys.removeFirst():0;
                }
                case 18 -> r[d]=Storage.readData((byte)r[a],(byte)r[b],(byte)r[c])&255;
                case 19 -> Storage.writeData((byte)r[a],(byte)r[b],(byte)r[c],(byte)r[d]);
                case 20 -> r[c]=(r[a]*r[b])&255;
                case 21 -> r[a]=(r[a]+1)&255;
                case 22 -> ram[(r[11]<<8)|r[b]][r[c]]=(byte)r[a];
                case 23 -> { int bank=r[a],page=r[b],offset=r[c]; if(bank>1)throw new AssertionError("Invalid RAM bank");r[11]=bank;r[9]=page;r[8]=offset; }
                case 24 -> r[d]=ram[(r[a]<<8)|r[b]][r[c]]&255;
                case 25 -> ram[(r[b]<<8)|r[c]][r[d]]=(byte)r[a];
                case 26 -> r[c]=r[a]==r[b]?0:r[a]<r[b]?1:2;
                case 27 -> r[d]=ram[(a<<8)|b][c]&255;
                case 28 -> ram[(b<<8)|c][d]=(byte)r[a];
                default -> throw new AssertionError("Opcode "+op);
            }
        }
        throw new AssertionError("Guest failed to return to keyboard poll");
    }
    private static void check(boolean value,String label) {if(!value)throw new AssertionError(label);}
    private static void reject(Checked action) throws Exception {
        try {action.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError("Invalid range accepted");
    }
    private interface Checked {void run() throws Exception;}
}
