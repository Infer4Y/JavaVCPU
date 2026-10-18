package xyz.ignite4inferneo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;


/** Headless regression checks; runs the real BIOS/DOS bytecode with stubbed peripherals. */
public final class DosStorageRegression {
    private final byte[][] ram = new byte[512][256];
    private final int[] r = new int[Registers.values().length];
    private int note60Updates;
    private long instructions, pixelWrites, displayFrames, noteOnFrame, noteOffFrame, zeroDurationFrame;
    private final int[] voiceControls=new int[8];
    private boolean browserNetwork;
    private int mouseX,mouseY,mouseButtons,mouseWheel;
    private final int[][] pixels = new int[256][256];
    private final ArrayDeque<Integer> keys = new ArrayDeque<>();

    private static void folderChecks(DosStorageRegression vm) throws Exception {
        vm.ram[252][220]=23;
        vm.command("MKDIR DOCS");
        check(name(23,0).equals("DOCS") && Storage.readRange((23<<16)+13,1)[0]==4,"directory type");
        vm.command("CD DOCS"); vm.command("WRITE SAME.TXT CHILD");
        check(Storage.readRange((23<<16)+0xff02,1)[0]==1,"persistent parent ID");
        vm.command("MD NESTED");
        vm.type("CD NE"); vm.press(9); check(vm.line().equals("CD NESTED "),"folder completion"); vm.press(27);
        vm.command("CHDIR NESTED"); vm.command("WRITE DEEP.TXT DATA");
        check(Storage.readRange((23<<16)+0xff04,1)[0]==3,"nested parent ID");
        vm.command("CD .."); vm.command("COPY SAME.TXT COPY.TXT");
        check(chainText(23,find(23,"COPY.TXT")).equals("CHILD"),"copy in folder");
        vm.command("CD /");
        vm.type("TYPE DE"); vm.press(9); check(vm.line().equals("TYPE DE"),"completion hides nested files"); vm.press(27);
        vm.command("WRITE SAME.TXT ROOT");
        check(chainText(23,1).equals("CHILD"),"same filename isolated from root");
        byte[] before=Storage.readRange(23<<16,65536);
        for(String command:new String[]{"RD DOCS","DEL DOCS","WRITE DOCS BAD","COPY DOCS BAD","TYPE DOCS","RUN DOCS","MKDIR DOCS","MKDIR ..","MKDIR BAD/NAME","CD SAME.TXT"}) vm.command(command);
        check(java.util.Arrays.equals(before,Storage.readRange(23<<16,65536)),"invalid folder operations preserve disk");
        vm.command("CD DOCS"); vm.command("CD ."); vm.command("REN COPY.TXT RENAMED.TXT");
        vm.command("DIR"); vm.command("CD NESTED"); vm.command("DEL DEEP.TXT"); vm.command("CD ..");
        vm.command("RMDIR NESTED"); check(name(23,2).isEmpty(),"remove empty nested folder");
        vm.command("WRITE REUSED.TXT NEW");
        check(name(23,2).equals("REUSED.TXT") && Storage.readRange((23<<16)+0xff03,1)[0]==1,"reuse deleted directory slot");
        vm.command("BANK +"); vm.command("WRITE BANK.TXT ROOT");
        check(Storage.readRange((24<<16)+0xff01,1)[0]==0,"bank switch resets folder");
        vm.command("BANK -"); vm.command("CD DOCS"); vm.command("REBOOT");
        vm.ram[252][220]=23; vm.command("CD DOCS"); vm.command("APPEND SAME.TXT AGAIN");
        check(chainText(23,1).equals("CHILDAGAIN"),"folders survive reboot");
        vm.command("CD /"); vm.command("CD ..");
        System.out.println("PASS: nested folders, parent persistence, name isolation, bank reset and safe removal");
    }

    private static void moveChecks() throws Exception {
        DosStorageRegression vm=terminal();vm.ram[252][220]=30;
        byte[] contents=new byte[12000];for(int i=0;i<contents.length;i++)contents[i]=(byte)(i*17);
        chain(30,0,"LARGE.BIN",contents,0);vm.command("MKDIR DEST");int folder=find(30,"DEST");
        byte[] before=Storage.readRange(30<<16,65536);vm.command("MOVE LARGE.BIN DEST");
        check((Storage.readRange((30<<16)+0xff01,1)[0]&255)==folder+1,"MOVE places large file in folder without buffering");
        before[0xff01]=(byte)(folder+1);check(java.util.Arrays.equals(before,Storage.readRange(30<<16,65536)),"MOVE preserves all file and chain bytes");
        vm.command("CD DEST");vm.command("MOVE LARGE.BIN ..");check(Storage.readRange((30<<16)+0xff01,1)[0]==0,"MOVE to parent");
        vm.command("CD /");vm.command("MOVE LARGE.BIN NEW.BIN");check(name(30,0).equals("NEW.BIN"),"MOVE renames files");
        vm.command("MKDIR OUTER");vm.command("CD OUTER");vm.command("MKDIR INNER");vm.command("CD INNER");
        vm.command("WRITE DUP.TXT CHILD");vm.command("MOVE DUP.TXT /");int duplicate=find(30,"DUP.TXT");check(Storage.readRange((30<<16)+0xff00+duplicate+1,1)[0]==0,"MOVE to root");
        vm.command("CD /");vm.command("WRITE DUP2.TXT ROOT");vm.command("CD DEST");vm.command("WRITE DUP2.TXT CHILD");vm.command("CD /");
        before=Storage.readRange(30<<16,65536);for(String command:new String[]{"MOVE DUP2.TXT DEST","MOVE OUTER OUTER","MOVE MISSING DEST","MOVE DUP.TXT DEST EXTRA"}){vm.command(command);check(java.util.Arrays.equals(before,Storage.readRange(30<<16,65536)),"invalid MOVE preserves disk: "+command);}
        vm.command("RENAME DUP.TXT RENAMED.TXT");check(find(30,"RENAMED.TXT")!=-1,"RENAME alias");vm.command("DELETE RENAMED.TXT");check(find(30,"RENAMED.TXT")==-1,"DELETE alias");
        System.out.println("PASS: DOS MOVE folder/parent/root/rename, large-file metadata preservation, collisions and file-operation aliases");
    }

    private int desktopState(int slot) {return ram[256+224][slot]&255;}
    private void mouse(int x,int y,int buttons) {mouseX=x;mouseY=y;mouseButtons=buttons;press(0);}
    private void click(int x,int y) {mouse(x,y,0);mouse(x,y,1);mouse(x,y,0);}
    private void snapshot(String name) throws Exception {
        var image=new java.awt.image.BufferedImage(768,768,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<256;y++)for(int x=0;x<256;x++) {
            int c=pixels[y][x],rgb=(((c>>5)&3)*85<<16)|(((c>>2)&7)*255/7<<8)|(c&3)*85;
            for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++)image.setRGB(x*3+dx,y*3+dy,rgb);
        }
        Path output=Path.of("build",name+".png");Files.createDirectories(output.getParent());
        javax.imageio.ImageIO.write(image,"png",output.toFile());
    }
    private static void desktopChecks() throws Exception {
        DosStorageRegression vm=terminal();
        byte[] program=xyz.ignite4inferneo.jvdos.DesktopImage.program();
        check(java.util.Arrays.equals(program,Files.readAllBytes(Path.of("examples/dos/JCTRL.COM"))),"desktop executable is up to date");
        check(Files.readString(Path.of("tools/disk-workshop/desktop.js")).contains(java.util.Base64.getEncoder().encodeToString(program)),"Workshop desktop asset is up to date");
        chain(25,0,"JCTRL.COM",program,0);
        // Use a linked file for viewer paging.
        chain(25,150,"HELLO.TXT",("HELLO FROM A WINDOW\n"+"SECOND LINE\n".repeat(40)).getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        file(25,160,"DOCS","");Storage.writeRange((25<<16)+(160<<8)+13,new byte[]{4});
        file(25,161,"CHILD.TXT","CHILD CONTENT");Storage.writeRange((25<<16)+0xff00+162,new byte[]{(byte)161});
        for(int slot=190;slot<215;slot++)file(25,slot,"ITEM"+slot+".TXT","ITEM");
        chain(25,164,"BAD.TXT",new byte[500],1);
        vm.ram[252][220]=25;
        byte[] before=Storage.readRange(25<<16,65536);
        vm.command("RUN JCTRL.COM");
        check(vm.r[11]==1,"desktop launches through normal COM loader");
        check(vm.desktopState(160)==8&&vm.desktopState(161)==24,"initial window position");
        check(vm.pixels[25][9]==22&&vm.pixels[100][220]==5,"desktop window chrome");
        check(vm.desktopState(9)==0,"file window focused");
        vm.mouse(20,28,0);vm.mouse(20,28,1);vm.mouse(40,48,1);vm.mouse(40,48,0);
        check(vm.desktopState(160)==28&&vm.desktopState(161)==44,"title drag preserves pointer offset");
        vm.mouse(40,48,0);vm.mouse(40,48,1);vm.mouse(255,255,1);vm.mouse(255,255,0);
        check(vm.desktopState(160)==40&&vm.desktopState(161)==72,"drag clamps to display edges");
        vm.mouse(52,76,0);vm.mouse(52,76,1);vm.mouse(40,48,1);vm.mouse(40,48,0);
        check(vm.desktopState(160)==28&&vm.desktopState(161)==44,"drag returns window");
        vm.press(114);check(vm.desktopState(178)==1&&vm.desktopState(9)==2,"windowed VER MEM");
        vm.snapshot("desktop-system");
        vm.press(112);check(vm.desktopState(17)==1,"windowed help");
        vm.snapshot("desktop-help");
        vm.click(32+216-7,48+5);check(vm.desktopState(178)==0&&vm.desktopState(9)==0,"close restores keyboard focus");
        vm.press(113);check(vm.desktopState(9)==0&&vm.desktopState(12)==0,"window focus and stacking");
        // Browser rows: JCTRL.COM, HELLO.TXT, DOCS. Open the second row.
        vm.press(40);check(vm.appState(100)==1,"explorer keyboard selects next row");vm.press(38);check(vm.appState(100)==0,"explorer keyboard selects previous row");vm.press(40,10);
        check(vm.desktopState(170)==1&&vm.desktopState(9)==1,"text viewer opens from file window");
        check(vm.desktopState(43)==150,"viewer selected file");
        vm.snapshot("desktop-text");
        vm.click(250,220);check(vm.desktopState(9)==1,"background click preserves focus");
        vm.mouse(250,220,4);vm.mouse(250,220,0);check(vm.desktopState(9)==1,"right click does not activate another window");
        vm.press(34);check(vm.desktopState(50)>0||vm.desktopState(51)>0,"linked text next page");
        vm.press(34);int end=vm.desktopState(50)+(vm.desktopState(51)<<8);vm.press(34);
        check(vm.desktopState(50)+(vm.desktopState(51)<<8)==end,"viewer last page stays visible");
        vm.press(36);check(vm.desktopState(50)==0&&vm.desktopState(51)==0,"viewer Home");
        vm.mouseWheel=1;vm.press(0);check(vm.desktopState(50)>0||vm.desktopState(51)>0,"mouse wheel paging");
        vm.press(113);vm.click(40,44+28+12+2);
        check(vm.desktopState(16)==161,"desktop enters folder");
        vm.click(40,44+18);check(vm.desktopState(16)==0,"desktop parent folder");
        vm.press(34);check(vm.desktopState(36)>0,"directory next page");int secondPage=vm.desktopState(36);vm.press(33);check(vm.desktopState(36)==0,"directory previous page restores filtered page start");vm.press(34);check(vm.desktopState(36)==secondPage,"directory next page can be revisited");vm.press(36);check(vm.desktopState(36)==0,"directory Home");
        vm.snapshot("desktop-files");
        check(java.util.Arrays.equals(before,Storage.readRange(25<<16,65536)),"desktop browsing preserves disk");
        Storage.writeRange((25<<16)+(165<<8)+15,new byte[]{(byte)165});
        // Model external corruption after loading the desktop; the DOS loader rejects it beforehand.
        before=Storage.readRange(25<<16,65536);
        vm.click(40,44+28+18+2);check(vm.desktopState(43)==164&&vm.desktopState(58)==2,"corrupt chain terminates safely");
        vm.press(27);check(vm.r[11]==0,"desktop exits to resident DOS");
        check(vm.ram[252][220]==25&&vm.ram[253][70]==0,"desktop preserves DOS bank and folder");
        check(java.util.Arrays.equals(before,Storage.readRange(25<<16,65536)),"window applications are read only");
        vm.command("ECHO BACK IN DOS");
        System.out.println("PASS: separate desktop COM, mouse dragging, stacking, windowed DIR/VIEW/VER/MEM/HELP, linked text, folders and return to DOS");
    }

    private int appState(int slot) {return ram[256+225][slot]&255;}
    private void applicationFrames() {for(int frame=0;frame<200&&appState(0)!=0;frame++)press(0);check(appState(0)==0,"application yields and finishes");}
    private static void desktopApplicationChecks() throws Exception {
        DosStorageRegression vm=terminal();byte[] desktop=xyz.ignite4inferneo.jvdos.DesktopImage.program();
        chain(26,0,"JCTRL.COM",desktop,0);
        chain(26,150,"HI.COM",Files.readAllBytes(Path.of("examples/dos/HI.COM")),0);
        chain(26,151,"PIC.IMG",new byte[]{74,73,2,2,1,28,96,127},2);
        chain(26,152,"TUNE.JMT",new byte[]{74,77,1,2,0,6,64,(byte)129,2,7,60,(byte)129},3);
        chain(26,153,"PIXEL.COM",new byte[]{2,(byte)128,0,2,(byte)128,1,2,96,2,12,0,1,2,16,64,19,4},0);
        chain(26,154,"BAD.COM",new byte[]{2,99,(byte)255},0);
        chain(26,155,"COUNT.JVS",Files.readAllBytes(Path.of("examples/dos/COUNT.JVS")),1);
        chain(26,156,"MATH.JVS",Files.readAllBytes(Path.of("examples/dos/MATH.JVS")),1);
        chain(26,157,"REGS.JVS",Files.readAllBytes(Path.of("examples/dos/REGISTERS.JVS")),1);
        chain(26,158,"LONG.JVS",Files.readAllBytes(Path.of("examples/dos/LONG.JVS")),1);
        byte[] large=new byte[512];java.util.Arrays.fill(large,(byte)4);
        byte[] jump={2,1,0,2,0,1,10,0,1};System.arraycopy(jump,0,large,0,jump.length);
        byte[] second={2,42,19,4};System.arraycopy(second,0,large,256,second.length);chain(26,163,"LARGE.COM",large,0);
        chain(26,166,"DEVICE.COM",new byte[]{2,(byte)255,10,2,0,0,18,0,0,0,1,4},0);
        chain(26,167,"ESCAPE.COM",new byte[]{2,99,3,28,3,1,(byte)224,(byte)160,4},0);
        chain(26,168,"LOOP.COM",new byte[]{3,0},0);
        chain(26,169,"EMPTY.JMT",new byte[]{74,77,2,0},3);
        chain(26,170,"LONG.JMT",new byte[]{74,77,2,0,100,7,60,(byte)129},3);
        byte[] picture=new byte[4+120*96];picture[0]=74;picture[1]=73;picture[2]=120;picture[3]=96;
        for(int y=0;y<96;y++)for(int x=0;x<120;x++)picture[4+y*120+x]=(byte)(1+(x/10+y/8*12)%126);
        chain(26,171,"GRID.IMG",picture,2);
        chain(26,220,"BIG.COM",new byte[8193],0);
        vm.ram[252][220]=26;vm.command("RUN JCTRL.COM");
        byte[] before=Storage.readRange(26<<16,65536);
        vm.click(20,24+28+6+1);vm.applicationFrames();
        check(vm.appState(1)==1,"COM interpreter stays inside guest JCTRL");
        check((vm.ram[416][0]&255)==127,"legacy DOS print service renders H in guest framebuffer");
        check(vm.desktopState(160)==8&&vm.r[11]==1,"desktop remains resident during COM execution");
        vm.mouse(30,40,0);vm.mouse(30,40,1);vm.mouse(40,60,1);vm.mouse(40,60,0);
        check(vm.desktopState(168)==30&&vm.desktopState(169)==56&&(vm.ram[416][0]&255)==127,"program pixels survive window dragging using guest RAM");
        vm.mouse(40,60,1);vm.mouse(30,40,1);vm.mouse(30,40,0);
        vm.snapshot("jctrl-program");
        vm.press(113);vm.click(20,24+28+12+1);
        check(vm.appState(1)==3,"palette image opens inside JCTRL");
        check((vm.ram[416][0]&255)==1&&(vm.ram[416][1]&255)==28&&(vm.ram[416][128]&255)==96,"palette pixels are backed by guest RAM");
        check(vm.pixels[64][65]==28 && vm.pixels[65][64]==96,"frame blit presents both columns and rows");
        vm.snapshot("jctrl-image");
        vm.press(113);vm.click(20,24+28+18+1);vm.applicationFrames();check(vm.appState(1)==4&&vm.note60Updates>0,"guest music uses existing audio opcodes");
        check(vm.zeroDurationFrame==vm.noteOnFrame,"zero-duration events share the next event display interval");
        check(vm.noteOffFrame-vm.noteOnFrame==2,"music duration retains exactly two display intervals");
        long idlePixels=vm.pixelWrites;vm.press(0);check(vm.pixelWrites==idlePixels,"finished app does not force repaint");
        vm.press(113);vm.click(20,24+28+24+1);vm.applicationFrames();
        check((vm.ram[416+32][64]&255)==96,"guest program graphics map into bounded framebuffer");
        check((vm.ram[484][19]&255)==1,"relocated self-modifying READ_INPUT operand uses the mounted-drive port");
        vm.press(113);vm.click(20,24+28+30+1);vm.applicationFrames();check(vm.appState(1)==6,"bad register operand is rejected without corrupting JCTRL");
        for(int row=6;row<=9;row++) {
            vm.press(113);vm.click(20,24+28+row*6+1);vm.press(116);
            vm.sourceFrames();
            check(vm.appState(1)==5&&vm.appState(103)==vm.appState(104)&&vm.appState(116)==1&&vm.ram[252][176]==0,"source completes through resident DOS: row "+row);
            if(row==6)check((vm.ram[252][224]&255)==5,"JVS IF/GOTO loop");
            if(row==7)check((vm.ram[252][226]&255)==42&&(vm.ram[252][224]&255)==0,"JVS arithmetic and wraparound");
            if(row==8)check((vm.ram[252][239]&255)==42,"JVS extended registers");
            if(row==9)check((vm.ram[252][225]&255)==55,"linked JVS source and physical line targets");
        }
        vm.snapshot("jctrl-source");
        vm.press(113);vm.click(20,24+28+10*6+1);vm.applicationFrames();
        check(vm.appState(1)==1&&(vm.ram[484][19]&255)==42,"bank-one COM page jumps use guest workspace");
        vm.press(113);vm.click(20,24+28+16*6+1);check(vm.appState(1)==3,"linked palette image streams into its window");vm.snapshot("jctrl-image");
        for(int row:new int[]{11,12,14,17}) {
            vm.press(113);vm.click(20,24+28+row*6+1);vm.applicationFrames();
            check(vm.appState(1)==6&&vm.desktopState(160)==8,"unsupported device/address/empty tune/oversize app is bounded: "+row);
            check(vm.r[10]==0,"interpreted storage selector is restored");
        }
        vm.press(113);vm.click(20,24+28+13*6+1);vm.press(0);
        check(vm.appState(0)==1,"looping app yields to the JCTRL event loop");vm.press(27);check(vm.appState(0)==0&&vm.r[11]==1,"Escape stops a guest program without exiting JCTRL");
        vm.press(113);vm.click(20,24+28+15*6+1);vm.press(0);check(vm.voiceControls[7]!=0,"long version-two tune starts");
        long musicInstructions=vm.instructions,musicPixels=vm.pixelWrites;
        for(int tick=0;tick<10;tick++)vm.press(0);
        check(vm.pixelWrites==musicPixels,"sustained music does not repaint unchanged windows");
        check(vm.instructions-musicInstructions<10_000,"sustained music scheduler stays below 1000 instructions per tick");
        System.out.println("JCTRL sustained music: "+((vm.instructions-musicInstructions)/10)+" instructions/tick, zero pixel writes");
        vm.click(230,40);check(vm.appState(0)==0&&java.util.Arrays.stream(vm.voiceControls).allMatch(v->v==0),"closing music window silences all voices");
        check(java.util.Arrays.equals(before,Storage.readRange(26<<16,65536)),"guest media and read-only programs preserve disk");
        byte[] maximum=new byte[4+240*240];maximum[0]=74;maximum[1]=73;maximum[2]=(byte)240;maximum[3]=(byte)240;java.util.Arrays.fill(maximum,4,maximum.length,(byte)127);
        chain(27,0,"MAX.IMG",maximum,2);
        vm.press(113);vm.click(178,42);check(vm.desktopState(15)==27,"guest file browser selects another storage bank");
        vm.click(20,24+28+1);check(vm.appState(1)==3,"maximum linked image streams without extra RAM");
        check((vm.ram[475][247]&255)==127&&(vm.ram[416][120]&255)==0,"240-square media scales to a bounded 120-square image");
        vm.press(113);vm.click(133,42);check(vm.desktopState(15)==26,"file bank can be restored");
        check(vm.ram[252][220]==26,"guest application execution preserves DOS bank");
        vm.press(27,27);check(vm.r[11]==0,"guest interpreter returns through normal DOS ABI");
        System.out.println("PASS: guest-only JCTRL COM execution, DOS print compatibility, RAM-backed pixels, image/music playback and invalid operand handling");
    }

    private static void desktopDragChecks() throws Exception {
        DosStorageRegression vm=terminal();chain(29,0,"JCTRL.COM",xyz.ignite4inferneo.jvdos.DesktopImage.program(),0);
        file(29,150,"A.TXT","A");file(29,151,"DOCS","");Storage.writeRange((29<<16)+(151<<8)+13,new byte[]{4});
        file(29,152,"A.TXT","EXISTING");Storage.writeRange((29<<16)+0xff00+153,new byte[]{(byte)152});
        file(29,153,"B.TXT","B");vm.ram[252][220]=29;vm.command("RUN JCTRL.COM");
        byte[] before=Storage.readRange(29<<16,65536);
        vm.mouse(30,59,0);vm.mouse(30,59,1);vm.mouse(30,65,1);vm.mouse(30,65,0);
        check(java.util.Arrays.equals(before,Storage.readRange(29<<16,65536)),"duplicate-name drop leaves disk unchanged");
        vm.mouse(30,71,0);vm.mouse(30,71,1);vm.mouse(30,65,1);vm.mouse(30,65,0);
        check((Storage.readRange((29<<16)+0xff00+154,1)[0]&255)==152,"drag moves file into destination folder");
        check(chainText(29,153).equals("B"),"drag preserves linked file contents");
        byte[] after=Storage.readRange(29<<16,65536);before[0xff00+154]=(byte)152;
        check(java.util.Arrays.equals(before,after),"move changes only parent metadata");
        vm.mouse(30,65,0);vm.mouse(30,65,1);vm.mouse(35,65,1);vm.mouse(35,65,0);
        check(java.util.Arrays.equals(after,Storage.readRange(29<<16,65536)),"folder cannot be dropped into itself");
        System.out.println("PASS: guest drag/drop folder move, duplicate rejection, self-drop rejection and byte preservation");
    }

    private void sourceFrames() {
        for(int frame=0;frame<2000&&(appState(103)!=appState(104)||appState(116)!=1||ram[252][176]!=0);frame++) {
            press(0);
        }
        check(appState(1)==5&&appState(103)==appState(104)&&appState(116)==1&&ram[252][176]==0,"source returns to resident DOS prompt");
    }
    private static void desktopSourceChecks() throws Exception {
        DosStorageRegression vm=terminal();
        chain(31,0,"JCTRL.COM",xyz.ignite4inferneo.jvdos.DesktopImage.program(),0);
        chain(31,145,"FULL.JVS","; RESIDENT PARSER COMMENT\nSTORE 7 A\nCLEAR\nINC A\nOUTPUT A\nEND\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        chain(31,146,"LOOP.JVS","GOTO 1\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        chain(31,147,"QUIET.JVS",("STORE 7 A\n"+"INC A\n".repeat(50)+"END\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        DosStorageRegression quiet=terminal();quiet.ram[252][220]=31;quiet.command("CLS");quiet.command("RUN QUIET.JVS");
        check((quiet.ram[252][224]&255)==57&&(quiet.ram[252][130]&255)==12,"source arithmetic does not print blank lines or force scrolling");
        vm.ram[252][220]=31;vm.command("RUN JCTRL.COM");
        vm.click(20,24+28+6+1);vm.press(116);vm.sourceFrames();
        check((vm.ram[252][24]&255)==124&&(vm.ram[252][25]&255)==124&&(vm.ram[252][26]&255)==116,"resident console geometry wraps and scrolls within its surface");
        check((vm.ram[252][224]&255)==8,"JCTRL reuses DOS comment, CLEAR, arithmetic and output semantics");
        vm.press(113);vm.click(20,24+28+12+1);vm.press(116);
        for(int frame=0;frame<1000&&vm.ram[252][176]==0;frame++)vm.press(0);
        check(vm.ram[252][176]==1,"resident source loop starts without blocking desktop");
        vm.mouse(35,40,0);vm.mouse(35,40,1);vm.mouse(40,45,1);vm.mouse(40,45,0);
        vm.press(27);vm.sourceFrames();
        check(vm.r[11]==1&&vm.desktopState(169)==41,"source cancellation and window dragging keep desktop resident");
        System.out.println("PASS: JCTRL source uses full resident DOS interpreter, comments/CLEAR, loop cancellation and responsive window dragging");
    }

    private static void desktopShellChecks() throws Exception {
        DosStorageRegression vm=terminal();chain(28,0,"JCTRL.COM",xyz.ignite4inferneo.jvdos.DesktopImage.program(),0);
        vm.ram[252][220]=28;vm.command("RUN JCTRL.COM");vm.press(115);
        for(int frame=0;frame<50;frame++)vm.press(0);
        check(vm.appState(1)==5 && vm.appState(0)==1,"resident DOS shell starts in guest interpreter");
        vm.command("WRITE WINDOW.TXT HELLO");
        for(int frame=0;frame<200 && find(28,"WINDOW.TXT")==-1;frame++)vm.press(0);
        check(find(28,"WINDOW.TXT")!=-1,"existing DOS WRITE command runs inside window");
        for(int frame=0;frame<50;frame++)vm.press(0);
        check(chainText(28,find(28,"WINDOW.TXT")).equals("HELLO"),"windowed DOS writes real file contents");
        vm.press(113);int count=vm.ram[252][131]&255;vm.type("IGNORED");
        for(int frame=0;frame<20;frame++)vm.press(0);
        check((vm.ram[252][131]&255)==count,"unfocused DOS window receives no keyboard input");
        vm.press(115);vm.command("REN WINDOW.TXT MOVED.TXT");
        for(int frame=0;frame<200 && find(28,"MOVED.TXT")==-1;frame++)vm.press(0);
        check(find(28,"MOVED.TXT")!=-1,"existing DOS rename works after focus returns");
        for(int frame=0;frame<40;frame++)vm.press(0);
        vm.mouse(30,40,0);vm.mouse(30,40,1);vm.mouse(40,60,1);vm.mouse(40,60,0);
        check(vm.desktopState(168)==30&&vm.desktopState(169)==56,"DOS window title drag preserves shell state");
        boolean ink=false;for(int page=416;page<480;page++)for(byte pixel:vm.ram[page])if(pixel!=0)ink=true;
        check(ink,"resident DOS text reaches window framebuffer");
        vm.snapshot("jctrl-dos");
        vm.press(113,118);for(int frame=0;frame<50;frame++)vm.press(0);
        check(find(28,"NEWDIR")==-1,"explorer draft has not executed a command");
        vm.command("NEWDIR");for(int frame=0;frame<150&&find(28,"NEWDIR")==-1;frame++)vm.press(0);
        check(find(28,"NEWDIR")!=-1,"explorer new-folder action uses actual DOS command");
        for(int frame=0;frame<40;frame++)vm.press(0);
        vm.command("MOVE MOVED.TXT NEWDIR");for(int frame=0;frame<150;frame++)vm.press(0);
        int moved=find(28,"MOVED.TXT"),directory=find(28,"NEWDIR");check((Storage.readRange((28<<16)+0xff00+moved+1,1)[0]&255)==directory+1,"MOVE works in actual desktop DOS shell");
        vm.command("CD NEWDIR");for(int frame=0;frame<40;frame++)vm.press(0);vm.command("MOVE MOVED.TXT /");for(int frame=0;frame<150;frame++)vm.press(0);vm.command("CD /");for(int frame=0;frame<40;frame++)vm.press(0);
        vm.press(113,36,40,119);for(int frame=0;frame<50;frame++)vm.press(0);
        vm.command("COPY.TXT");for(int frame=0;frame<200&&find(28,"COPY.TXT")==-1;frame++)vm.press(0);
        check(find(28,"COPY.TXT")!=-1,"explorer copy prepares the real COPY command");
        for(int frame=0;frame<40;frame++)vm.press(0);
        check(chainText(28,find(28,"COPY.TXT")).equals("HELLO"),"explorer copy preserves content");
        vm.press(113,36,40,40,40,127);for(int frame=0;frame<50;frame++)vm.press(0);
        check(find(28,"COPY.TXT")!=-1,"delete draft does not delete before Enter");
        vm.press(10);for(int frame=0;frame<150&&find(28,"COPY.TXT")!=-1;frame++)vm.press(0);
        check(find(28,"COPY.TXT")==-1,"explorer delete executes only after Enter");
        vm.press(113);vm.click(239,60);check(vm.appState(0)==0&&vm.appState(1)==0,"DOS window closes through X"); // Close the moved DOS window through its title-bar X.
        vm.press(115);for(int frame=0;frame<20;frame++)vm.press(0);
        check(vm.appState(1)==5&&vm.appState(0)==1,"closed DOS window can be reopened");
        System.out.println("PASS: actual resident DOS in movable guest window, file writes, rename and focus-gated keyboard");
    }

    private static void browserChecks(DosStorageRegression vm) throws Exception {
        vm.command("CLS"); vm.type("A");
        String glyph="010101111101101000";
        for(int y=0;y<6;y++) for(int x=0;x<4;x++)
            check(vm.pixels[4+y][24+x]==(x<3 && glyph.charAt(y*3+x)=='1'?28:0),"packed font pixel "+x+","+y);
        vm.press(27);
        vm.type("BROWSE "); vm.press(72,16,65,144);
        check(vm.line().equals("BROWSE hA"),"browser URL keyboard letter case"); vm.press(27);
        try(var server=new java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            var request=java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try(var socket=server.accept()) {
                    socket.setSoTimeout(5000);
                    var reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.US_ASCII));
                    StringBuilder headers=new StringBuilder(); String line;
                    while((line=reader.readLine())!=null && !line.isEmpty()) headers.append(line).append("\n");
                    socket.getOutputStream().write("HTTP/1.0 200 OK\r\nContent-Type: text/html\r\n\r\n<h1>HELLO</h1><p>WORLD</p>".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    return headers.toString();
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            // Inject the completed line, independently of host keyboard scan-code mappings.
            String command="BROWSE http://127.0.0.1:"+server.getLocalPort()+"/Case?x=1";
            byte[] line=command.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(line,0,vm.ram[251],64,line.length);
            vm.ram[252][131]=(byte)line.length; // COUNT
            vm.browserNetwork=true;
            try { vm.press(10); } finally { vm.browserNetwork=false; Network.close(); }
            String headers=request.get(5,java.util.concurrent.TimeUnit.SECONDS);
            check(headers.startsWith("GET /Case?x=1 HTTP/1.0\n"),"browser preserves request path case/query");
            check(headers.contains("Host: 127.0.0.1:"+server.getLocalPort()+"\n"),"browser Host header/custom port");
            int length=(vm.ram[252][135]&255)+((vm.ram[253][0]&255)<<8);
            StringBuilder body=new StringBuilder();
            for(int i=0;i<length;i++) body.append((char)(vm.ram[256+i/256][i%256]&255));
            check(body.toString().equals("\nHELLO\n\nWORLD\n"),"guest strips HTTP headers and HTML tags");
            vm.press(40,38,27);
        }
        System.out.println("PASS: guest HTTP request, custom port, case-sensitive path, headers, HTML text and pager exit");
    }

    private static void networkGuestChecks(DosStorageRegression vm) throws Exception {
        try(var server=new java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            var peer=java.util.concurrent.CompletableFuture.runAsync(() -> {
                try(var socket=server.accept()) {
                    socket.setSoTimeout(5000);
                    check(socket.getInputStream().read()==72,"NETECHO guest sends H");
                    socket.getOutputStream().write(new byte[]{0,42,(byte)255});
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            byte[] program=Files.readAllBytes(Path.of("examples/dos/NETECHO.COM"));
            for(int i=0;i<program.length-8;i++) {
                if(program[i]==2 && program[i+2]==0 && program[i+3]==2 && program[i+5]==1 && program[i+6]==30) {
                    if(program[i+1]==3) program[i+4]=(byte)(server.getLocalPort()>>>8);
                    if(program[i+1]==4) program[i+4]=(byte)server.getLocalPort();
                }
            }
            chain(25,0,"NETECHO.COM",program,0); vm.ram[252][220]=25;
            vm.command("RUN NETECHO.COM");
            peer.get(5,java.util.concurrent.TimeUnit.SECONDS);
            check(Network.read(0)==0,"guest network example closes connection and returns");
        } finally { Network.close(); }
    }

    public static void main(String[] args) throws Exception {
        MonitorRegression.run();
        FramePacerRegression.run();
        MemoryRegression.run();
        xyz.ignite4inferneo.jvdos.DesktopAssemblyRegression.run();
        sharedGraphicsChecks();
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

        PeripheralRegression.run(image);
        DesktopInstallRegression.run(image);

        for (int slot=0;slot<16;slot++) file(2,slot,"F"+slot+".TXT","OLD");
        file(2,254,"LAST.TXT","END");
        Storage.writeRange(0x2ff00, new byte[]{0x4a});
        DiskInstaller.installIfMissing();
        check(Storage.readRange(0,8)[5] == 20, "system revision");
        check(Storage.readRange(0x20010,3)[0] == 'O', "installation preserves files");
        DosStorageRegression vm = new DosStorageRegression();
        byte[][] pages = Programs.bootImage();
        check(pages.length <= 255, "boot image fits RAM");
        for(int p=0;p<pages.length;p++) System.arraycopy(pages[p],0,vm.ram[p],0,pages[p].length);
        vm.run();
        autoexecChecks();
        DosStorageRegression desktopLaunch=terminal();desktopLaunch.command("DRIVE 1");desktopLaunch.ram[252][220]=29;desktopLaunch.command("MKDIR CONTEXT");desktopLaunch.command("CD CONTEXT");desktopLaunch.command("JCTRL");
        check(desktopLaunch.r[11]==1&&desktopLaunch.desktopState(15)==29&&desktopLaunch.desktopState(16)==1&&desktopLaunch.r[10]==1,"JCTRL loads from system disk while preserving selected drive, bank and folder");
        desktopLaunch.click(220,5);check(desktopLaunch.r[11]==0,"managed desktop returns to DOS");
        check((desktopLaunch.ram[252][24]&255)==252&&(desktopLaunch.ram[252][25]&255)==244&&(desktopLaunch.ram[252][26]&255)==236,"desktop exit restores fullscreen console geometry");
        vm.command("DRIVE 1"); vm.command("MKDIR REMOTE"); vm.command("CD REMOTE");
        vm.command("WRITE DRIVE.TXT SECOND");
        check(Storage.readData(1,(byte)2,(byte)1,(byte)16)=='S',"DOS writes selected drive folder");
        vm.command("DRIVE 255"); check(vm.r[10]==1,"missing drive preserves selection");
        vm.command("DRIVE 256"); check(vm.r[10]==1,"invalid drive preserves selection");
        vm.command("DRIVE 0"); check(vm.r[10]==0 && vm.ram[253][70]==0,"drive resets folder");
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
        StringBuilder extendedRegisters = new StringBuilder();
        for (char register='I'; register<='P'; register++) {
            extendedRegisters.append("STORE ").append(register-'I'+1).append(' ').append(register).append('\n');
        }
        extendedRegisters.append("ADD I J P\nMULTIPLY P K O\nSUB O L N\nSTORE 255 M\nINC M\nIF N 5 17\nSTORE 99 N\nEND\n");
        file(3,20,"REGS.JVS",extendedRegisters.toString());
        vm.command("RUN REGS.JVS");
        check((vm.ram[252][232]&255)==1 && (vm.ram[252][233]&255)==2,
                "JVS I/J values survive interpreter scratch registers");
        check((vm.ram[252][236]&255)==0 && (vm.ram[252][237]&255)==5
                && (vm.ram[252][238]&255)==9 && (vm.ram[252][239]&255)==3,
                "JVS M-P arithmetic, wrap and conditional branch");
        file(3,21,"RESET.JVS","END\n");
        vm.command("RUN RESET.JVS");
        for (int register=0; register<16; register++) {
            check(vm.ram[252][224+register]==0,"RUN resets JVS register " + register);
        }
        // Store through P with the real COM validator and return through the stable service ABI.
        byte[] extendedCom={2,42,19,28,19,1,(byte)255,(byte)255,
                2,(byte)249,6,2,9,7,10,6,7};
        chain(3,22,"REGS.COM",extendedCom,0);
        vm.command("RUN REGS.COM");
        check((vm.ram[511][255]&255)==42,"COM accepts and executes register P");
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
        browserChecks(vm);
        networkGuestChecks(vm);
        folderChecks(vm);
        moveChecks();
        desktopChecks();
        desktopApplicationChecks();
        pongChecks();
        desktopSourceChecks();
        desktopShellChecks();
        desktopDragChecks();
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

    private static void autoexecChecks() throws Exception {
        chain(2,240,"AUTOEXEC.JVS","STORE 41 A\nINC A\nEND\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        DosStorageRegression vm=terminal();
        check(vm.ram[252][224]==42,"autoexec runs after cold boot");
        vm.command("STORE 0 A");vm.command("REBOOT");
        check(vm.ram[252][224]==42,"autoexec runs on REBOOT");
        vm.command("STORE 0 A");vm.command("VER");
        check(vm.ram[252][224]==0,"autoexec does not rerun at ordinary prompt");
        chain(2,240,"AUTOEXEC.JVS","JCTRL\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII),1);
        vm.command("REBOOT");
        check(vm.r[11]==1,"autoexec can launch bundled desktop");
        vm.click(220,5);vm.ram[252][220]=2;vm.command("DEL AUTOEXEC.JVS");
        vm.command("REBOOT");vm.command("VER");
        check(vm.ram[252][176]==0,"missing autoexec leaves interactive shell");
        System.out.println("PASS: optional AUTOEXEC.JVS cold boot, reboot, single execution and desktop launch");
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
    private static void sharedGraphicsChecks() {
        DosStorageRegression vm=new DosStorageRegression();byte[][] pages=Programs.bootImage();
        for(int p=0;p<pages.length;p++)System.arraycopy(pages[p],0,vm.ram[p],0,pages[p].length);
        // A keyboard poll is a return sentinel, in either caller bank.
        for(int bank=0;bank<2;bank++)vm.ram[(bank<<8)|248][0]=16;
        byte[][] before=java.util.Arrays.stream(vm.ram).map(byte[]::clone).toArray(byte[][]::new);
        for(int bank=0;bank<2;bank++)for(int mode=0;mode<2;mode++)for(int ch=0;ch<256;ch++) {
            for(int[] row:vm.pixels)java.util.Arrays.fill(row,9);
            vm.r[9]=249;vm.r[8]=18;vm.r[11]=0;vm.r[3]=ch;vm.r[4]=mode;
            vm.r[14]=254;vm.r[15]=253;vm.r[16]=127;vm.r[17]=bank;vm.r[18]=248;vm.r[19]=0;
            vm.run();
            check(vm.r[11]==bank&&vm.r[9]==248&&vm.r[8]==0,"graphics cross-bank return");
            int glyph=ch>=32&&ch<=126?ch:'?',index=64+(glyph-32)*2;
            int page=vm.ram[246][index]&255,offset=vm.ram[246][index+1]&255;
            for(int y=0;y<7;y++)for(int x=0;x<5;x++) {
                boolean ink=y<5&&x<3&&(vm.ram[page][offset+y]&(128>>x))!=0;
                int expected=ink?127:mode!=0&&x<4&&y<6?0:9;
                check(vm.pixels[(253+y)&255][(254+x)&255]==expected,"shared glyph pixels, spacing, fallback and coordinate wrap");
            }
            check(vm.r[17]==bank&&vm.r[18]==248&&vm.r[19]==0,"graphics return registers preserved");
        }
        check(java.util.Arrays.deepEquals(before,vm.ram),"shared graphics preserves all guest RAM");
        byte[] desktop=xyz.ignite4inferneo.jvdos.DesktopImage.program();
        for(int i=0;i<desktop.length;i++)vm.ram[256+i/256][i%256]=desktop[i];
        vm.ram[249][9]=16;vm.ram[249][10]=0;vm.ram[249][11]=0;
        for(int missing=0;missing<3;missing++) {
            vm.ram[249][27]='J';vm.ram[249][28]='G';vm.ram[249][29]=1;vm.ram[249][27+missing]=0;
            vm.ram[256+224][0]=93;vm.r[11]=1;vm.r[9]=0;vm.r[8]=0;vm.run();
            check(vm.r[11]==0&&vm.r[9]==249&&vm.r[8]==9,"incompatible graphics ABI returns to DOS");
            check(vm.ram[256+224][0]==93,"incompatible ABI does not initialize desktop");
        }
        System.out.println("PASS: shared resident graphics, all byte characters, opaque/transparent pixels, cross-bank returns and RAM preservation");
    }

    private static void pongChecks() throws Exception {
        byte[] game=Files.readAllBytes(Path.of("examples/jctrl/PONG.COM"));
        DosStorageRegression vm=terminal();
        chain(100,0,"JCTRL.COM",xyz.ignite4inferneo.jvdos.DesktopImage.program(),0);
        chain(100,160,"PONG.COM",game,0);vm.ram[252][220]=100;
        vm.command("RUN PONG.COM");
        check(vm.r[11]==0,"Pong rejects direct DOS execution");
        vm.command("RUN JCTRL.COM");vm.click(20,24+28+6+1);
        byte[] disk=Storage.readRange(100<<16,65536);

        byte[] state=vm.ram[256+128+31];
        for(int n=0;n<150 && (state[0]&255)!=132;n++)vm.press(0);
        int boardTicks=0;
        while((state[0]&255)!=180 && boardTicks<120){vm.press(0);boardTicks++;}
        check((state[0]&255)==180&&boardTicks<60,"Pong advances twelve board updates within sixty desktop ticks");
        System.out.println("Pong: twelve board updates in "+boardTicks+" desktop ticks");
        check(vm.appState(0)!=0&&vm.appState(1)==1,"Pong runs in JCTRL interpreter");
        check((state[0]&255)!=128,"Pong ball advances");
        vm.mouse(100,100,0);for(int n=0;n<120 && (state[4]&255)!=56;n++)vm.press(0);
        check((state[4]&255)>0&&(state[4]&255)<208,"Pong mouse moves paddle");
        // Force the next miss to be the winning point; real guest code handles it.
        state[6]=6;state[0]=(byte)248;state[2]=4;
        for(int attempt=0;attempt<4 && state[6]!=7;attempt++){state[0]=(byte)248;state[2]=4;for(int n=0;n<120 && state[6]!=7;n++)vm.press(0);}
        check(state[6]==7,"Pong scoring reaches seven and stops play");
        vm.press(82);for(int n=0;n<20;n++)vm.press(0);
        check(state[6]==0&&state[7]==0,"Pong R restarts match");
        vm.snapshot("jctrl-pong");vm.press(27);
        check(vm.appState(0)==0,"Pong Escape returns to desktop");
        check(java.util.Arrays.equals(disk,Storage.readRange(100<<16,65536)),"Pong never writes storage");
        System.out.println("PASS: JCTRL Pong launch guard, gameplay, mouse, scoring, restart, cancellation and disk preservation");
    }
    private void run() {
        for(int steps=0;steps<200_000_000;steps++) {
            instructions++;
            byte[] m=ram[(r[11]<<8)|r[9]]; int pc=r[8],op=m[pc]&255;
            int a=m[(pc+1)&255]&255,b=m[(pc+2)&255]&255,c=m[(pc+3)&255]&255,d=m[(pc+4)&255]&255;
            int length=OPCode.values()[op].length(); r[8]=(pc+length)&255;
            switch(op) {
                case 0 -> r[c]=(r[a]+r[b])&255;
                case 1 -> r[c]=(r[a]-r[b])&255;
                case 2 -> r[b]=a;
                case 3 -> r[8]=a;
                case 4 -> throw new AssertionError("Unexpected HALT");
                case 5 -> { }
                case 14 -> displayFrames++;
                case 12 -> {pixelWrites++;pixels[r[b]][r[a]]=r[c];}
                case 13 -> {for(int[] row:pixels)java.util.Arrays.fill(row,0);}
                case 17 -> { if(r[a]==6&&r[c]!=0)zeroDurationFrame=displayFrames; if(r[a]==7){if(r[c]!=0)noteOnFrame=displayFrames;else noteOffFrame=displayFrames;} if(r[b]==60)note60Updates++;if(r[a]<8)voiceControls[r[a]]=r[c]; }
                case 6 -> { if(r[a]==r[b])r[8]=c; }
                case 7 -> r[a]=m[b]&255;
                case 8 -> m[b]=(byte)r[a];
                case 9 -> r[9]=r[a];
                case 10 -> { r[9]=r[a]; r[8]=r[b]; }
                case 11 -> { if(r[b]==r[c]) {r[9]=r[a];r[8]=d;} }
                case 15 -> r[c]=0;
                case 16 -> {
                    if(a==0&&keys.isEmpty()&&(ram[252][176]==0||(r[11]==1&&r[9]<128))&&(!browserNetwork || Network.read(0)==0)) {r[8]=pc;return;}
                    r[b]=switch(a) {
                        case 0 -> keys.isEmpty()?0:keys.removeFirst();
                        case 0x30 -> mouseX;case 0x31 -> mouseY;case 0x32 -> mouseButtons;
                        case 0x33 -> {int wheel=mouseWheel;mouseWheel=0;yield wheel&255;}
                        case 0x40 -> Storage.isMounted(r[10])?1:0;default -> 0;
                    };
                }
                case 18 -> r[d]=Storage.readData(r[10],(byte)r[a],(byte)r[b],(byte)r[c])&255;
                case 19 -> Storage.writeData(r[10],(byte)r[a],(byte)r[b],(byte)r[c],(byte)r[d]);
                case 20 -> r[c]=(r[a]*r[b])&255;
                case 21 -> r[a]=(r[a]+1)&255;
                case 22 -> ram[(r[11]<<8)|r[b]][r[c]]=(byte)r[a];
                case 23 -> { int bank=r[a],page=r[b],offset=r[c]; if(bank>1)throw new AssertionError("Invalid RAM bank");r[11]=bank;r[9]=page;r[8]=offset; }
                case 24 -> r[d]=ram[(r[a]<<8)|r[b]][r[c]]&255;
                case 25 -> ram[(r[b]<<8)|r[c]][r[d]]=(byte)r[a];
                case 26 -> r[c]=r[a]==r[b]?0:r[a]<r[b]?1:2;
                case 27 -> r[d]=ram[(a<<8)|b][c]&255;
                case 28 -> ram[(b<<8)|c][d]=(byte)r[a];
                case 29 -> r[b]=Network.read(r[a])&255;
                case 30 -> Network.write(r[a],(byte)r[b]);
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
