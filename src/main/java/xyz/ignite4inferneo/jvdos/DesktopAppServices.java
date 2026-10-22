package xyz.ignite4inferneo.jvdos;

import static xyz.ignite4inferneo.OPCode.*;
import static xyz.ignite4inferneo.jvdos.DesktopImage.*;
import static xyz.ignite4inferneo.jvdos.DesktopServices.*;

/** Shared routines executed by JCTRL itself, rather than interpreted per app byte. */
final class DesktopAppServices {
    private DesktopAppServices() { }
    private static final int OP=406, SOURCE=407, OFFSET=408, LEFT=409, PART=410,
        RAW=411, SHIFT=412, RESULT=413;
    static final int STATUS=414;

    static void emit(Asm s) {
        s.label("vm.app.service");s.get(A,APP);s.notEqual(A,1,"app.tick.error");
        s.set(STATUS,OK);s.vreadLiteral(0,A);s.put(A,OP);
        s.equal(A,QUERY,"app.service.query");s.equal(A,TEXT,"app.service.text");
        s.equal(A,PACKED_TEXT,"app.service.packed");s.equal(A,NETWORK_PACKED,"app.service.packed");s.equal(A,KEYBOARD,"app.service.key");
        s.equal(A,CONTEXT,"app.service.context");s.equal(A,DOCUMENT,"document.enable");
        s.equal(A,DOCUMENT_FRAME,"document.frame");s.equal(A,GRAPHICS_SPAN,"app.service.graphics");s.equal(A,GRAPHICS_CURSOR,"app.service.cursor");s.equal(A,GRAPHICS_LINE,"app.service.line");
        s.equal(A,CACHE_BEGIN,"app.cache.begin");s.equal(A,CACHE_FLUSH,"app.cache.flush");s.equal(A,CACHE_FINISH,"app.cache.finish");s.equal(A,CACHE_NAVIGATE,"app.cache.navigate");s.equal(A,CACHE_ABORT,"app.cache.abort");s.jump("app.service.bad");
        s.label("app.service.query");s.store(DesktopServices.VERSION,A);s.vwriteLiteral(3,A);s.jump("app.service.return");
        s.label("app.service.context");s.get(A,CWD);s.vwriteLiteral(1,A);s.get(A,DesktopFileSystem.CWD_H);s.vwriteLiteral(2,A);s.jump("app.service.return");

        // Validate the complete plain span before drawing any character. The
        // packed variant additionally checks each cursor movement, including gaps.
        s.label("app.service.text");s.vreadLiteral(3,A);s.put(A,LEFT);s.jump("app.service.source");
        s.label("app.service.packed");s.set(LEFT,80);s.set(PART,0);
        s.label("app.service.source");s.vreadLiteral(1,A);s.get(B,APP_LIMIT);s.emit(COMPARE_UNSIGNED,A,B,C);s.equal(C,1,"app.service.source.valid");s.jump("app.service.bad");
        s.label("app.service.source.valid");s.put(A,SOURCE);s.vreadLiteral(2,A);s.put(A,OFFSET);
        s.get(A,OP);s.notEqual(A,TEXT,"app.service.loop");s.get(A,SOURCE);s.get(B,APP_LIMIT);s.emit(INCREMENT_REGISTER,A);s.ne(A,B,"app.service.loop");
        s.get(A,LEFT);s.notEqual(A,0,"app.service.last.sum");s.get(A,OFFSET);s.notEqual(A,0,"app.service.bad");s.jump("app.service.loop");
        s.label("app.service.last.sum");s.get(B,OFFSET);s.emit(ADD,A,B,C);s.equal(C,0,"app.service.loop");s.emit(COMPARE_UNSIGNED,C,B,D);s.equal(D,1,"app.service.bad");
        s.label("app.service.loop");s.get(B,SOURCE);s.get(C,APP_LIMIT);s.emit(COMPARE_UNSIGNED,B,C,A);s.equal(A,1,"app.service.read");s.jump("app.service.bad");
        s.label("app.service.read");s.appPage(B);s.get(C,OFFSET);s.store(1,A);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.put(D,PRINT_CHAR);
        s.get(A,OP);s.equal(A,TEXT,"app.service.draw");s.equal(D,0,"app.service.return");
        s.label("app.service.draw");s.get(A,OP);s.equal(A,NETWORK_PACKED,"app.service.transmit");s.call("app.print",RET_PRINT);s.jump("app.service.advance");s.label("app.service.transmit");s.get(D,PRINT_CHAR);s.store(5,A);s.emit(WRITE_NETWORK,A,D);s.label("app.service.advance");s.inc16(OFFSET,SOURCE);
        s.get(A,OP);s.equal(A,TEXT,"app.service.count");s.get(A,PART);s.emit(INCREMENT_REGISTER,A);s.put(A,PART);s.notEqual(A,3,"app.service.count");s.set(PART,0);s.inc16(OFFSET,SOURCE);s.inc16(OFFSET,SOURCE);
        s.label("app.service.count");s.get(A,LEFT);s.store(1,B);s.emit(SUB,A,B,A);s.put(A,LEFT);s.notEqual(A,0,"app.service.loop");s.jump("app.service.return");

        s.label("app.service.key");s.vreadLiteral(1,A);s.put(A,RAW);s.vreadLiteral(2,A);s.less(A,2,"app.service.key.shift");s.jump("app.service.bad");
        s.label("app.service.key.shift");s.put(A,SHIFT);s.set(RESULT,0);s.get(D,RAW);
        s.equal(D,16,"app.service.shift.on");s.equal(D,144,"app.service.shift.off");
        s.less(D,65,"app.service.key.table");s.less(D,91,"app.service.letter");s.jump("app.service.key.table");
        s.label("app.service.letter");s.get(A,SHIFT);s.notEqual(A,0,"app.service.key.result");s.store(32,A);s.emit(ADD,D,A,D);s.jump("app.service.key.result");
        s.label("app.service.shift.on");s.set(SHIFT,1);s.jump("app.service.key.done");s.label("app.service.shift.off");s.set(SHIFT,0);s.jump("app.service.key.done");
        s.label("app.service.key.table");s.ref("app.service.keys",true,A);s.put(A,SOURCE);s.ref("app.service.keys",false,A);s.put(A,OFFSET);
        s.label("app.service.key.find");s.store(1,A);s.get(B,SOURCE);s.get(C,OFFSET);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);s.equal(D,0,"app.service.key.done");s.get(A,RAW);s.eq(A,D,"app.service.key.found");s.store(5,A);s.emit(ADD,C,A,A);s.put(A,OFFSET);s.jump("app.service.key.find");
        s.label("app.service.key.found");s.get(A,SHIFT);s.emit(ADD,C,A,C);s.emit(INCREMENT_REGISTER,C);s.store(1,A);s.emit(LOAD_MEM_ON_BANK,A,B,C,D);
        s.label("app.service.key.result");s.put(D,RESULT);
        s.label("app.service.key.done");s.get(A,SHIFT);s.vwriteLiteral(2,A);s.get(A,RESULT);s.vwriteLiteral(3,A);s.jump("app.service.return");
        s.label("app.service.bad");s.set(STATUS,BAD_ARGUMENT);
        s.label("app.service.return");s.get(A,STATUS);s.vwriteLiteral(0,A);
        s.vreadLiteral(17,A);s.vwriteLiteral(11,A);s.vreadLiteral(18,A);s.vwriteLiteral(9,A);s.vreadLiteral(19,A);s.vwriteLiteral(8,A);
        // One bounded service per desktop slice: repaint once per block and let
        // input/window dragging run before another request is accepted.
        s.set(BUDGET,1);s.jump("vm.done");
        emitGraphics(s);DesktopGraphicsLine.emit(s);
        s.lateData.add(DesktopAppServices::emitKeys);DesktopPageCache.emit(s);DesktopDocument.emit(s);
    }
    private static void emitGraphics(Asm s) {
        s.label("app.service.cursor");s.get(A,DesktopDocument.MODE);s.notEqual(A,0,"app.service.bad");
        s.vreadLiteral(1,B);s.vreadLiteral(2,C);s.less(B,32,"graphics.cursor.column");s.jump("app.service.bad");
        s.label("graphics.cursor.column");s.less(C,21,"graphics.cursor.row");s.jump("app.service.bad");
        s.label("graphics.cursor.row");s.put(B,PRINT_COL);s.put(C,PRINT_ROW);s.jump("app.service.return");
        s.label("app.service.graphics");s.get(A,DesktopDocument.MODE);s.notEqual(A,0,"app.service.bad");
        s.vreadLiteral(1,J);s.less(J,31,"graphics.page");s.jump("app.service.bad");
        s.label("graphics.page");s.appPage(J);s.vreadLiteral(2,K);s.vreadLiteral(4,M);s.vreadLiteral(12,E);s.put(E,PART);
        s.less(M,128,"graphics.y");s.jump("app.service.bad");s.label("graphics.y");s.less(E,2,"graphics.mode");s.jump("app.service.bad");
        s.label("graphics.mode");s.less(K,129,"graphics.buffer");s.jump("app.service.bad");
        s.label("graphics.buffer");s.store(1,I);s.equal(E,0,"graphics.address");
        // Check every incoming color before publishing any part of the row.
        s.store(0,A);s.emit(ADD,K,A,B);s.store(128,C);
        s.label("graphics.validate");s.emit(LOAD_MEM_ON_BANK,I,J,B,D);s.less(D,128,"graphics.valid");s.jump("app.service.bad");
        s.label("graphics.valid");s.emit(INCREMENT_REGISTER,B);s.store(1,A);s.emit(SUB,C,A,C);s.notEqual(C,0,"graphics.validate");
        s.label("graphics.address");s.store(0,B);s.store(2,C);
        s.label("graphics.divide");s.less(M,2,"graphics.offset");s.emit(SUB,M,C,M);s.emit(INCREMENT_REGISTER,B);s.jump("graphics.divide");
        s.label("graphics.offset");s.store(128,A);s.emit(MULTIPLY,M,A,M);s.store(160,A);s.emit(ADD,B,A,L);s.store(128,N);s.get(O,PART);
        s.label("graphics.copy");s.equal(O,1,"graphics.write");s.emit(LOAD_MEM_ON_BANK,I,L,M,D);s.emit(STORE_MEM_ON_BANK,D,I,J,K);s.jump("graphics.next");
        s.label("graphics.write");s.emit(LOAD_MEM_ON_BANK,I,J,K,D);s.emit(LOAD_MEM_ON_BANK,I,L,M,E);s.eq(D,E,"graphics.next");s.emit(STORE_MEM_ON_BANK,D,I,L,M);s.put(D,PCOLOR);
        s.store(128,A);s.emit(SUB,A,N,A);s.put(A,PX);s.vreadLiteral(4,A);s.put(A,PY);s.call("frame.changed",RET_PIXEL);
        s.label("graphics.next");s.emit(INCREMENT_REGISTER,K);s.emit(INCREMENT_REGISTER,M);s.store(1,A);s.emit(SUB,N,A,N);s.notEqual(N,0,"graphics.copy");s.jump("app.service.return");
    }
    private static void emitKeys(Asm s) {
        int[][] keys={{32,32,32},{44,44,60},{45,45,95},{46,46,62},{47,47,63},
            {48,48,41},{49,49,33},{50,50,64},{51,51,35},{52,52,36},{53,53,37},
            {54,54,94},{55,55,38},{56,56,42},{57,57,40},{59,59,58},{61,61,43},
            {91,91,123},{92,92,124},{93,93,125},{192,96,126},{222,39,34},
            {96,48,48},{97,49,49},{98,50,50},{99,51,51},{100,52,52},{101,53,53},
            {102,54,54},{103,55,55},{104,56,56},{105,57,57},{106,42,42},{107,43,43},{109,45,45},{110,46,46},{111,47,47}};
        s.reserve(246);s.labels.put("app.service.keys",s.page*256+s.pos+2);
        for(int[] key:keys)s.emit(STORE_MEM_ABSOLUTE,D,key[0],key[1],key[2]);s.emit(STORE_MEM_ABSOLUTE,D,0,0,0);
    }
}
