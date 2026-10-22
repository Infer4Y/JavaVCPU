package xyz.ignite4inferneo.jvdos;

import static xyz.ignite4inferneo.OPCode.*;
import static xyz.ignite4inferneo.jvdos.DesktopImage.*;

/** Bounded connected brush strokes, executed by JCTRL rather than its app interpreter. */
final class DesktopGraphicsLine {
    private DesktopGraphicsLine() { }
    private static final int X=449,Y=450,END_X=451,END_Y=452,DX=453,DY=454,SX=455,SY=456,
            STEPS=457,LEFT=458,EX=459,EY=460,BRUSH=461,INK=462,ROW=463,COL=464,RETURN=465;

    static void emit(Asm s) {
        s.label("app.service.line");s.get(A,DesktopDocument.MODE);s.notEqual(A,0,"app.service.bad");
        int[] slots={X,Y,END_X,END_Y,INK,BRUSH},registers={1,2,3,4,5,12};
        for(int n=0;n<slots.length;n++) {
            s.vreadLiteral(registers[n],A);s.put(A,slots[n]);
            if(n<5){String valid=s.unique("line.valid");s.less(A,128,valid);s.jump("app.service.bad");s.label(valid);}
        }
        s.equal(A,1,"line.start");s.equal(A,2,"line.start");s.notEqual(A,4,"app.service.bad");
        s.label("line.start");
        for(int axis=0;axis<2;axis++) {
            s.get(A,axis==0?END_X:END_Y);s.get(B,axis==0?X:Y);String negative=s.unique("line.negative"),done=s.unique("line.delta");
            s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,negative);s.emit(SUB,A,B,A);s.put(A,axis==0?DX:DY);s.set(axis==0?SX:SY,1);s.jump(done);
            s.label(negative);s.emit(SUB,B,A,A);s.put(A,axis==0?DX:DY);s.set(axis==0?SX:SY,255);s.label(done);
        }
        s.get(A,DX);s.get(B,DY);s.emit(COMPARE_UNSIGNED,A,B,C);s.notEqual(C,1,"line.major");s.store(0,C);s.emit(ADD,B,C,A);
        s.label("line.major");s.put(A,STEPS);s.put(A,LEFT);s.set(EX,0);s.set(EY,0);s.call("line.stamp",RETURN);s.get(A,LEFT);s.equal(A,0,"app.service.return");
        s.label("line.step");
        for(int axis=0;axis<2;axis++) {
            s.get(A,axis==0?EX:EY);s.get(B,axis==0?DX:DY);s.emit(ADD,A,B,A);s.get(B,STEPS);String skip=s.unique("line.skip"),done=s.unique("line.advanced");
            s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,skip);s.emit(SUB,A,B,A);s.put(A,axis==0?EX:EY);
            s.get(A,axis==0?X:Y);s.get(B,axis==0?SX:SY);s.emit(ADD,A,B,A);s.put(A,axis==0?X:Y);s.jump(done);
            s.label(skip);s.put(A,axis==0?EX:EY);s.label(done);
        }
        s.call("line.stamp",RETURN);s.get(A,LEFT);s.store(1,B);s.emit(SUB,A,B,A);s.put(A,LEFT);s.notEqual(A,0,"line.step");s.jump("app.service.return");
        s.label("line.stamp");s.set(ROW,0);
        s.label("line.row");s.set(COL,0);
        s.label("line.pixel");s.get(A,X);s.get(B,COL);s.emit(ADD,A,B,A);s.put(A,PX);s.get(A,Y);s.get(B,ROW);s.emit(ADD,A,B,A);s.put(A,PY);s.get(D,INK);s.put(D,PCOLOR);s.call("frame.pixel",RET_PIXEL);
        s.get(A,COL);s.emit(INCREMENT_REGISTER,A);s.put(A,COL);s.get(B,BRUSH);s.ne(A,B,"line.pixel");
        s.get(A,ROW);s.emit(INCREMENT_REGISTER,A);s.put(A,ROW);s.get(B,BRUSH);s.ne(A,B,"line.row");s.ret(RETURN);
    }
}
