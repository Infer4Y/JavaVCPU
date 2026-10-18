package xyz.ignite4inferneo.jvdos;

/** Guest JVFS request/reply ABI at 0:249:30. A/B select a RAM bank/page containing
 * a 64-byte mailbox; N/O/P are the return bank/page/offset. Working registers are
 * scratch. Handles 0/1 belong to DOS, 2 to JCTRL, 3 to guest applications.
 * Cluster/size/position fields are little endian; ADDRESS is bank/page/offset.
 * COUNT=0 requests 256 bytes; returned COUNT is the transferred byte count modulo 256.
 * Requests execute synchronously without DISPLAY, input polling or host hooks.
 */
public final class FileSystemAbi {
    private FileSystemAbi() { }
    public static final int ENTRY=30, DOS_PAGE=245, DESKTOP_PAGE=233, PRIVATE_PAGE=254, CACHE_PAGE=244;
    public static final int OP=0,STATUS=1,HANDLE=2,TYPE=3,DIR=4,COOKIE=6,ROW=8,SIZE=9,FIRST=12,
            ADDRESS=14,POSITION=17,COUNT=20,DATA=21,MODE=22,TARGET=23,BUFFER_BANK=25,BUFFER_PAGE=26,BUFFER_OFFSET=27,NAME=32,NEW_NAME=44;
    public static final int RESET=0,LOOKUP=1,NEXT=2,OPEN=3,READ=4,SEEK=5,CREATE=6,WRITE=7,CLOSE=8,
            ABORT=9,MKDIR=10,REMOVE=11,RENAME=12,MOVE=13,PARENT=14,READ_BLOCK=15,WRITE_BLOCK=16,STAT=17;
    public static final int OK=0,NOT_FOUND=1,EXISTS=2,FULL=3,CORRUPT=4,BAD_ARGUMENT=5,
            IS_DIRECTORY=6,NOT_EMPTY=7,EOF=8,BAD_HANDLE=9,UNFORMATTED=10;
}
