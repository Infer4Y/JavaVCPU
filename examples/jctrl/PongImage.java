package xyz.ignite4inferneo.jvdos;

import java.nio.file.Files;
import java.nio.file.Path;
import static xyz.ignite4inferneo.OPCode.*;

/** Pong assembled exclusively from existing guest instructions. No host game loop. */
public final class PongImage {
    private static final int A=0,B=1,C=2,D=3,E=4,F=5,I=12,J=13,K=14,L=15,M=16;
    private static final int X=0,Y=1,DX=2,DY=3,PLAYER=4,AI=5,LEFT=6,RIGHT=7;
    private static DesktopImage.Asm s;
    private static void get(int slot,int reg){s.emit(LOAD_MEM_ABSOLUTE,1,31,slot,reg);}
    private static void put(int slot,int reg){s.emit(STORE_MEM_ABSOLUTE,reg,1,31,slot);}
    private static void set(int slot,int value){s.store(value,D);put(slot,D);}
    private static void add(int slot,int amount){get(slot,A);s.store(amount,B);s.emit(ADD,A,B,A);put(slot,A);}
    private static void rect(int xSlot,int x,int ySlot,int y,int w,int h,int color){
        if(xSlot<0)s.store(x,E);else get(xSlot,E);
        if(ySlot<0)s.store(y,F);else get(ySlot,F);
        s.store(color,K);
        // Small rectangles use straight-line writes instead of interpreted loop branches.
        if(w*h<=128){
            s.store(2,C);
            for(int row=0;row<h;row+=2){
                if(row%8==0){s.emit(READ_INPUT,0,D);s.equal(D,82,"restart");}
                s.store(0,B);s.emit(ADD,E,B,A);
                for(int col=0;col<w;col+=2){
                    s.emit(PUSH_GRA_MEM,A,F,K);
                    if(col+2<w)s.emit(ADD,A,C,A);
                }
                if(row+2<h)s.emit(ADD,F,C,F);
            }
            return;
        }
        s.store(w,I);s.store(h,J);
        String back=s.unique("rect.back");s.ref(back,true,A);put(30,A);s.ref(back,false,A);put(31,A);s.jump("rect");s.label(back);
    }
    private static void objects(int color){rect(-1,12,PLAYER,0,4,32,color);rect(-1,240,AI,0,4,32,color);rect(X,0,Y,0,4,4,color);}
    public static byte[] program(){
        byte[] bytes=DesktopImage.assembleOptimized(PongImage::emitProgram).bytes();
        if(bytes.length>31*256)throw new IllegalStateException("Code overlaps state page");
        return bytes;
    }
    private static void emitProgram(DesktopImage.Asm assembler){
        s=assembler;
        // Adjacent native pixels alias only on JCTRL's half-resolution surface.
        s.store(255,A);s.store(255,B);s.store(0,C);s.emit(PUSH_GRA_MEM,A,B,C);
        s.store(254,A);s.store(254,B);s.store(127,C);s.emit(PUSH_GRA_MEM,A,B,C);
        s.store(255,A);s.store(255,B);s.emit(READ_GRA_MEM,A,B,D);s.equal(D,127,"restart");
        s.store(0,A);s.store(249,B);s.store(9,C);s.emit(CHANGE_BANK_PAGE_JUMP,A,B,C);
        s.label("restart");s.emit(CLEAR_GRA);set(LEFT,0);set(RIGHT,0);set(PLAYER,112);set(AI,112);set(9,0);
        s.label("serve");set(X,128);set(Y,128);set(DX,4);set(DY,2);
        // A short visible serve pause, with DISPLAY yielding to the desktop each time.
        objects(127);for(int n=0;n<12;n++){s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.equal(D,82,"restart");}
        s.label("frame");
        for(int slot:new int[]{PLAYER,AI,X,Y}){get(slot,A);put(slot+10,A);}
        s.emit(READ_INPUT,0,D);s.equal(D,82,"restart");
        s.emit(READ_INPUT,0x31,A);s.less(A,16,"player.low");s.less(A,224,"player.mid");s.store(224,A);s.label("player.mid");s.store(16,B);s.emit(SUB,A,B,A);s.jump("player.done");s.label("player.low");s.store(0,A);s.label("player.done");put(PLAYER,A);
        // AI moves every other frame, so angled shots can outrun it.
        add(9,1);get(9,A);s.equal(A,1,"ai.done");set(9,0);
        get(AI,A);s.store(16,B);s.emit(ADD,A,B,A);get(Y,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,0,"ai.done");s.equal(C,1,"ai.down");get(AI,A);s.equal(A,0,"ai.done");add(AI,254);s.jump("ai.done");s.label("ai.down");get(AI,A);s.equal(A,224,"ai.done");add(AI,2);s.label("ai.done");
        get(X,A);get(DX,B);s.emit(ADD,A,B,A);put(X,A);get(Y,A);get(DY,B);s.emit(ADD,A,B,A);put(Y,A);
        s.equal(A,24,"bounce.y");s.equal(A,252,"bounce.y");s.jump("collide");
        s.label("bounce.y");get(DY,A);s.store(0,B);s.emit(SUB,B,A,A);put(DY,A);
        s.label("collide");get(X,A);s.equal(A,16,"hit.left");s.equal(A,236,"hit.right");s.equal(A,0,"point.right");s.equal(A,252,"point.left");s.jump("draw");
        for(int paddle: new int[]{PLAYER,AI}){
            s.label(paddle==PLAYER?"hit.left":"hit.right");get(Y,A);get(paddle,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"draw");s.emit(SUB,A,B,A);s.less(A,32,paddle==PLAYER?"bounce.left":"bounce.right");s.jump("draw");
            s.label(paddle==PLAYER?"bounce.left":"bounce.right");set(DX,paddle==PLAYER?4:252);s.jump("draw");
        }
        for(int score:new int[]{LEFT,RIGHT}){
            s.label(score==LEFT?"point.left":"point.right");
            rect(-1,12,PLAYER+10,0,4,32,0);rect(-1,240,AI+10,0,4,32,0);rect(X+10,0,Y+10,0,4,4,0);add(score,1);get(score,A);s.store(8,B);s.emit(MULTIPLY,A,B,A);s.store(score==LEFT?16:160,B);s.emit(ADD,A,B,A);put(8,A);
            rect(8,0,-1,8,4,4,score==LEFT?48:96);get(score,A);s.equal(A,7,"winner."+score);s.jump("serve");
            s.label("winner."+score);rect(-1,score==LEFT?8:236,-1,32,8,192,score==LEFT?48:96);s.jump("finished");
        }
        s.label("draw");
        rect(X+10,0,Y+10,0,4,4,0);
        get(PLAYER,A);get(PLAYER+10,B);s.eq(A,B,"player.unchanged");
        rect(-1,12,PLAYER+10,0,4,32,0);rect(-1,12,PLAYER,0,4,32,127);
        s.label("player.unchanged");
        get(AI,A);get(AI+10,B);s.eq(A,B,"ai.unchanged");
        s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"ai.strip.up");
        rect(-1,240,AI+10,0,4,2,0);
        get(AI,A);s.store(30,B);s.emit(ADD,A,B,A);put(20,A);
        rect(-1,240,20,0,4,2,127);s.jump("ai.unchanged");
        s.label("ai.strip.up");
        get(AI+10,A);s.store(30,B);s.emit(ADD,A,B,A);put(20,A);
        rect(-1,240,20,0,4,2,0);rect(-1,240,AI,0,4,2,127);
        s.label("ai.unchanged");
        rect(X,0,Y,0,4,4,127);s.emit(DISPLAY);s.jump("frame");
        s.label("finished");s.emit(DISPLAY);s.emit(READ_INPUT,0,D);s.equal(D,82,"restart");s.jump("finished");
        s.label("rect");s.store(0,L);
        s.label("rect.row");s.emit(READ_INPUT,0,D);s.equal(D,82,"restart");s.store(0,M);
        s.label("rect.pixel");s.emit(ADD,E,M,A);s.emit(ADD,F,L,B);s.emit(PUSH_GRA_MEM,A,B,K);s.store(2,C);s.emit(ADD,M,C,M);s.eq(M,I,"rect.next");s.jump("rect.pixel");
        s.label("rect.next");s.store(2,C);s.emit(ADD,L,C,L);s.eq(L,J,"rect.done");s.jump("rect.row");
        s.label("rect.done");get(30,A);get(31,B);s.emit(CHANGE_PAGE_JUMP,A,B);

    }
    public static void main(String[] args)throws Exception{byte[] bytes=program();Files.write(Path.of(args[0]),bytes);System.out.println("PONG.COM: "+bytes.length+" bytes");}
}
