package xyz.ignite4inferneo.jvdos;

import xyz.ignite4inferneo.OPCode;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;
import static xyz.ignite4inferneo.OPCode.*;

/** Executes emitted branches at and across page boundaries, rather than checking only sizes. */
public final class DesktopAssemblyRegression {
    private DesktopAssemblyRegression() { }
    public static void run() {
        Consumer<DesktopImage.Asm> local=s -> selection(s,true,0,0);
        var compact=DesktopImage.assembleOptimized(local);
        check(compact.bytes()[6]==COND_JUMP.code(),"same-page comparison uses COND_JUMP");
        check(compact.localConditionals()>0&&compact.localJumps()>0,"local branch forms selected");
        for(int before:new int[]{0,68,72,76,78,79,80,81,82,84,156,160,161,200})
            for(int between:new int[]{0,1,78,80,160})
                for(boolean equal:new boolean[]{false,true}) {
                    Consumer<DesktopImage.Asm> source=s -> selection(s,equal,before,between);
                    var optimized=DesktopImage.assembleOptimized(source);
                    var baseline=new DesktopImage.Asm(Set.of());source.accept(baseline);
                    int[] expected=execute(baseline.finish()),actual=execute(optimized.bytes());
                    check(actual[3]==(equal?42:33),"conditional branch result at "+before+"/"+between);
                    check(actual[0]==expected[0]&&actual[1]==expected[1]&&actual[3]==expected[3],"far/local behavior agrees");
                    check(Arrays.equals(optimized.bytes(),DesktopImage.assembleOptimized(source).bytes()),"layout is deterministic");
                }
        for(int padding:new int[]{0,76,80,160}) {
            var loop=DesktopImage.assembleOptimized(s -> {
                s.store(0,0);s.store(3,1);s.label("loop");s.emit(INCREMENT_REGISTER,0);
                for(int i=0;i<padding;i++)s.store(0,4);
                s.eq(0,1,"done");s.jump("loop");s.label("done");s.emit(STOP);
            });
            check(execute(loop.bytes())[0]==3,"backward loop across pages");
        }
        System.out.println("PASS: relaxed local/far branches, true/false page conditions, backward loops, boundaries and deterministic layout");
    }
    private static void selection(DesktopImage.Asm s,boolean equal,int before,int between) {
        s.store(7,0);s.store(equal?7:8,1);
        for(int i=0;i<before;i++)s.store(0,4);
        s.eq(0,1,"match");s.store(33,3);s.jump("done");
        for(int i=0;i<between;i++)s.store(0,4);
        s.label("match");s.store(42,3);s.label("done");s.emit(STOP);
    }
    private static int[] execute(byte[] bytes) {
        for(int offset=0;offset<bytes.length;) {
            int opcode=bytes[offset]&255;check(opcode<OPCode.values().length,"valid opcode");
            int length=OPCode.values()[opcode].length();check((offset&255)+length<=256,"instruction fits code page");offset+=length;
        }
        int[] r=new int[20];int page=0,pc=0;
        for(int steps=0;steps<10000;steps++) {
            int address=page*256+pc,op=bytes[address]&255;
            int a=pc<255?bytes[address+1]&255:0,b=pc<254?bytes[address+2]&255:0;
            int c=pc<253?bytes[address+3]&255:0,d=pc<252?bytes[address+4]&255:0;
            pc=(pc+OPCode.values()[op].length())&255;
            switch(op) {
                case 2 -> r[b]=a;
                case 3 -> pc=a;
                case 4 -> {return r;}
                case 6 -> {if(r[a]==r[b])pc=c;}
                case 10 -> {page=r[a];pc=r[b];}
                case 11 -> {if(r[b]==r[c]){page=r[a];pc=d;}}
                case 21 -> r[a]=(r[a]+1)&255;
                default -> throw new AssertionError("Unexpected fixture instruction: "+op);
            }
        }
        throw new AssertionError("Branch fixture did not halt");
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
