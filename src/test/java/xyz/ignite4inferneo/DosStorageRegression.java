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
    private boolean browserNetwork, realGraphics;
    private int mouseX,mouseY,mouseButtons,mouseWheel;
    private final int[][] pixels = new int[256][256];
    private final ArrayDeque<Integer> keys = new ArrayDeque<>();

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
    private static void desktopCursorChecks() throws Exception {
        DosStorageRegression vm=terminal();

        vm.realGraphics=true;vm.command("RUN JCTRL.COM");
        int[][] frame=java.util.Arrays.stream(vm.pixels).map(int[]::clone).toArray(int[][]::new);
        long writes=vm.pixelWrites;
        // Exercise the real palette conversion and buffer swaps, including colors
        // in window bodies/title bars, the desktop, the toolbar and the footer.
        int[][] path={{40,80},{41,81},{40,80},{20,28},{80,5},{240,200},{20,245},{255,255},{0,0}};
        for(int pass=0;pass<16;pass++)for(int[] point:path)vm.mouse(point[0],point[1],0);
        check(vm.pixelWrites-writes==16L*path.length*4,"real framebuffer pointer motion stays incremental");
        check(java.util.Arrays.deepEquals(frame,vm.pixels),"real framebuffer cursor leaves no trails across colors, overlaps and buffer swaps");
        vm.mouse(20,28,0);vm.mouse(20,28,1);vm.mouse(40,48,1);vm.mouse(40,48,0);
        frame=java.util.Arrays.stream(vm.pixels).map(int[]::clone).toArray(int[][]::new);
        vm.mouse(250,220,0);vm.mouse(40,48,0);
        check(java.util.Arrays.deepEquals(frame,vm.pixels),"full window repaint refreshes the saved cursor background");
        for(int y=0;y<256;y++)for(int x=0;x<256;x++)
            check((Monitor.fetchMemory((byte)x,(byte)y)&255)==vm.pixels[y][x],"native framebuffer matches guest pixel writes");
        vm.snapshot("desktop-cursor-clean");
        System.out.println("PASS: actual framebuffer cursor save/restore, repeated movement, overlaps, edges and window redraws without trails");
    }
    private int appState(int slot) {return ram[256+225][slot]&255;}
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
    }
    private void command(String text) {
        for(char c:text.toCharArray()) {
            if(c=='_'){keys.add(16);keys.add(45);keys.add(144);}
            else keys.add(c=='+'?107:c=='-'?109:(int)c);
        }
        keys.add(10); run();
    }
    private void press(int... codes) { for(int code:codes)keys.add(code);run(); }
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

        xyz.ignite4inferneo.jvdos.JvFileSystem.device(0).put(xyz.ignite4inferneo.jvdos.JvFileSystem.ROOT,"PONG.COM",game,0);
        vm.command("RUN PONG.COM");
        check(vm.r[11]==0,"Pong rejects direct DOS execution");
        vm.command("RUN JCTRL.COM");int row=0;var list=xyz.ignite4inferneo.jvdos.JvFileSystem.device(0).list(xyz.ignite4inferneo.jvdos.JvFileSystem.ROOT);while(!list.get(row).name().equals("PONG.COM"))row++;vm.click(20,53+row*6);
        byte[] disk=Storage.readRange(0,Storage.STORAGE_SIZE);

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
        check(java.util.Arrays.equals(disk,Storage.readRange(0,Storage.STORAGE_SIZE)),"Pong never writes storage");
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
                case 14 -> {displayFrames++;if(realGraphics)Monitor.swapBuffers();}
                case 12 -> {pixelWrites++;pixels[r[b]][r[a]]=r[c];if(realGraphics)Monitor.pushMemory((byte)r[a],(byte)r[b],(byte)r[c]);}
                case 13 -> {for(int[] row:pixels)java.util.Arrays.fill(row,0);if(realGraphics)Monitor.clear();}
                case 17 -> { if(r[a]==6&&r[c]!=0)zeroDurationFrame=displayFrames; if(r[a]==7){if(r[c]!=0)noteOnFrame=displayFrames;else noteOffFrame=displayFrames;} if(r[b]==60)note60Updates++;if(r[a]<8)voiceControls[r[a]]=r[c]; }
                case 6 -> { if(r[a]==r[b])r[8]=c; }
                case 7 -> r[a]=m[b]&255;
                case 8 -> m[b]=(byte)r[a];
                case 9 -> r[9]=r[a];
                case 10 -> { r[9]=r[a]; r[8]=r[b]; }
                case 11 -> { if(r[b]==r[c]) {r[9]=r[a];r[8]=d;} }
                case 15 -> r[c]=realGraphics?Monitor.fetchMemory((byte)r[a],(byte)r[b])&255:pixels[r[b]][r[a]];
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
    public static void main(String[] args)throws Exception {JvFsRegression.main(args);}
}
