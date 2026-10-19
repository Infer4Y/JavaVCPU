package xyz.ignite4inferneo.jvdos;

import static xyz.ignite4inferneo.OPCode.*;
import static xyz.ignite4inferneo.jvdos.DesktopImage.*;

/** Optional retained text surface. Uses the existing application framebuffer RAM.
 * Applications share the desktop's font and repaint path, including window moves.
 */
final class DesktopDocument {
    private DesktopDocument() { }
    static final int MODE=436, PAGE=437, OFFSET=438;
    private static final int READ_PAGE=439, READ_OFFSET=440, LINE=441, COLUMN=442, X=446, Y=447;
    static final int COLUMNS=48, ROWS=23;
    static final int EDITOR=448;

    static void emit(Asm s) {
        // Route the reader controls through the same key mailbox as the keyboard.
        s.label("document.wheel");s.get(A,WHEEL);s.equal(A,0,"mouse");s.less(A,128,"document.down");s.set(APP_KEY,33);s.jump("mouse");
        s.label("document.down");s.set(APP_KEY,34);s.jump("mouse");
        s.label("document.click");s.get(A,EDITOR);s.notEqual(A,0,"mouse.cursor");s.get(A,HITY);s.less(A,154,"mouse.cursor");s.get(A,HITX);s.less(A,6,"mouse.cursor");s.less(A,34,"document.prev");s.less(A,62,"document.next");s.less(A,86,"document.home");s.less(A,122,"document.reload");s.less(A,146,"document.url");s.less(A,178,"document.links");s.less(A,210,"document.back");s.jump("mouse.cursor");
        String[] controls={"prev","next","home","reload","url","links","back"};
        for(int i=0;i<controls.length;i++){s.label("document."+controls[i]);s.set(APP_KEY,"PNHRULB".charAt(i));s.jump("mouse.cursor");}

        s.label("document.enable");s.set(EDITOR,0);s.set(MODE,1);s.set(PRINT_COL,0);s.set(PRINT_ROW,0);
        s.call("frame.clear",RET_CLEAR);s.jump("app.service.return");

        s.label("document.frame");
        s.vreadLiteral(1,A);s.get(B,APP_LIMIT);s.store(10,C);s.emit(SUB,B,C,B);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,2,"app.service.bad");
        s.less(A,22,"document.span");s.less(A,32,"app.service.bad");s.label("document.span");s.appPage(A);s.put(A,EDITOR);
        s.set(MODE,1);s.set(DIRTY,1);s.jump("app.service.return");

        s.label("document.print");s.get(D,PRINT_CHAR);s.equal(D,13,"app.print.done");
        s.equal(D,10,"document.newline");s.less(D,32,"app.print.done");
        s.get(B,PAGE);s.get(C,OFFSET);s.store(1,A);s.emit(STORE_MEM_ON_BANK,D,A,B,C);
        s.set(DIRTY,1);s.inc16(OFFSET,PAGE);s.get(A,PRINT_COL);s.emit(INCREMENT_REGISTER,A);s.put(A,PRINT_COL);
        s.notEqual(A,COLUMNS,"app.print.done");s.jump("document.row");
        // Newlines skip blank cells, which CLEAR initialized to zero.
        s.label("document.newline");s.inc16(OFFSET,PAGE);s.get(A,PRINT_COL);s.emit(INCREMENT_REGISTER,A);s.put(A,PRINT_COL);s.notEqual(A,COLUMNS,"document.newline");
        s.label("document.row");s.set(PRINT_COL,0);s.get(A,PRINT_ROW);s.emit(INCREMENT_REGISTER,A);s.put(A,PRINT_ROW);s.notEqual(A,ROWS,"app.print.done");
        s.set(PRINT_ROW,0);s.call("frame.clear",RET_CLEAR);s.jump("app.print.done");

        s.label("document.draw");
        s.relative(4,14);s.get(A,TX);s.put(A,RX);s.get(A,TY);s.put(A,RY);s.set(RW,208);s.set(RH,140);s.set(COLOR,127);s.call("rect",RETURN_RECT);
        s.get(A,RX);s.store(4,B);s.emit(ADD,A,B,A);s.put(A,X);s.get(A,RY);s.store(2,B);s.emit(ADD,A,B,A);s.put(A,Y);
        s.set(READ_PAGE,219);s.set(READ_OFFSET,0);s.set(LINE,0);s.set(COLUMN,0);
        s.get(A,EDITOR);s.equal(A,0,"document.cell");s.put(A,READ_PAGE);
        s.label("document.cell");s.get(B,READ_PAGE);s.get(C,READ_OFFSET);s.store(1,A);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.put(D,CHAR);s.less(D,33,"document.advance");
        s.get(A,COLUMN);s.store(4,B);s.emit(MULTIPLY,A,B,A);s.get(B,X);s.emit(ADD,A,B,A);s.put(A,TX);
        s.get(A,LINE);s.store(6,B);s.emit(MULTIPLY,A,B,A);s.get(B,Y);s.emit(ADD,A,B,A);s.put(A,TY);s.set(INK,0);s.get(A,EDITOR);s.notEqual(A,0,"document.color");s.get(A,LINE);s.notEqual(A,0,"document.ink");s.set(INK,9);s.jump("document.ink");
        s.label("document.color");s.get(B,READ_PAGE);s.store(5,A);s.emit(ADD,B,A,B);s.get(C,READ_OFFSET);s.store(1,A);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.put(D,INK);
        s.label("document.ink");s.call("glyph",RETURN_GLYPH);
        s.label("document.advance");s.inc16(READ_OFFSET,READ_PAGE);s.get(A,COLUMN);s.emit(INCREMENT_REGISTER,A);s.put(A,COLUMN);s.notEqual(A,COLUMNS,"document.cell");
        s.set(COLUMN,0);s.get(A,LINE);s.emit(INCREMENT_REGISTER,A);s.put(A,LINE);s.notEqual(A,ROWS,"document.cell");
        s.get(A,EDITOR);s.notEqual(A,0,"document.draw.done");s.relative(6,158);s.text("P:PREV N:NEXT H:TOP R:RELOAD U:URL L:LINKS B:BACK");s.label("document.draw.done");s.ret(RETURN_VIEW);
    }
}
