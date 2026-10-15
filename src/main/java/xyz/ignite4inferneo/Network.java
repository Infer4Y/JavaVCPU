package xyz.ignite4inferneo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** A dedicated byte-wide network controller. One outbound TCP connection, independent of RAM/storage.
 * Register accesses never wait for DNS, connection setup, remote reads or writes.
 * All queues are bounded. A full receive queue applies TCP backpressure instead of losing bytes.
 */
public final class Network {
    public static final int CONNECTING=1, CONNECTED=2, RX_READY=4, TX_BUSY=8, EOF=16, ERROR=128;
    private static final int RX_LIMIT=65535, TX_LIMIT=4096;
    private static final StringBuilder host=new StringBuilder();
    private static final ByteArrayOutputStream tx=new ByteArrayOutputStream();
    private static final ArrayDeque<Byte> rx=new ArrayDeque<>();
    private static int port, error;
    private static boolean connecting, connected, sending, eof;
    private static long generation;
    private static Socket socket;
    private Network() { }

    public static synchronized byte read(int address) {
        return switch(address) {
            case 0 -> (byte)((connecting?CONNECTING:0)|(connected?CONNECTED:0)|(!rx.isEmpty()?RX_READY:0)
                    |(sending?TX_BUSY:0)|(eof?EOF:0)|(error!=0?ERROR:0));
            case 1 -> (byte)error;
            case 3 -> (byte)(port>>>8);
            case 4 -> (byte)port;
            case 6 -> { Byte value=rx.poll(); Network.class.notifyAll(); yield value==null?0:value; }
            case 7 -> (byte)(rx.size()>>>8);
            case 8 -> (byte)rx.size();
            default -> 0;
        };
    }

    public static synchronized void write(int address,byte value) {
        int data=Byte.toUnsignedInt(value);
        switch(address) {
            case 0 -> {
                switch(data) {
                    case 1 -> connect();
                    case 2 -> send();
                    case 3 -> close();
                    case 4 -> { close(); host.setLength(0); port=0; }
                    default -> error=1;
                }
            }
            case 1 -> error=0;
            case 2 -> {
                if(data==0) host.setLength(0);
                else if(data<33 || data>126) error=2;
                else if(host.length()==253) error=5;
                else host.append((char)data);
            }
            case 3 -> port=(port&255)|(data<<8);
            case 4 -> port=(port&0xff00)|data;
            case 5 -> { if(tx.size()==TX_LIMIT) error=5; else tx.write(data); }
            default -> error=1;
        }
    }

    private static void connect() {
        if(connecting || connected || sending) { error=6; return; }
        if(host.isEmpty() || port==0) { error=2; return; }
        close();
        connecting=true;
        String destination=host.toString(); int destinationPort=port; long session=generation;
        Socket opening=new Socket(); socket=opening;
        Thread.startVirtualThread(() -> receive(session,opening,destination,destinationPort));
    }

    private static void receive(long session,Socket connection,String destination,int destinationPort) {
        try {
            connection.connect(new InetSocketAddress(destination,destinationPort),5000);
            connection.setSoTimeout(1000);
            synchronized(Network.class) {
                if(session!=generation) return;
                connecting=false; connected=true;
            }
            var input=connection.getInputStream();
            byte[] buffer=new byte[1024];
            while(true) {
                int count;
                try { count=input.read(buffer); } catch(SocketTimeoutException ignored) { continue; }
                synchronized(Network.class) {
                    if(session!=generation) return;
                    if(count<0) { eof=true; connected=false; return; }
                    for(int i=0;i<count;i++) {
                        while(rx.size()==RX_LIMIT && session==generation) Network.class.wait();
                        if(session!=generation) return;
                        rx.add(buffer[i]);
                    }
                }
            }
        } catch(IOException | RuntimeException failure) {
            synchronized(Network.class) { if(session==generation) error=connecting?3:4; }
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            try { connection.close(); } catch(IOException ignored) { }
            synchronized(Network.class) {
                if(session==generation) { connected=false; connecting=false; socket=null; }
            }
        }
    }

    private static void send() {
        if(sending) { error=6; return; }
        if(!connected || socket==null) { error=4; return; }
        byte[] bytes=tx.toByteArray(); tx.reset(); sending=true;
        Socket connection=socket; long session=generation;
        Thread.startVirtualThread(() -> {
            try { connection.getOutputStream().write(bytes); connection.getOutputStream().flush(); }
            catch(IOException failure) {
                synchronized(Network.class) {
                    if(session==generation) {
                        error=4; connected=false;
                        try { connection.close(); } catch(IOException ignored) { }
                    }
                }
            } finally { synchronized(Network.class) { if(session==generation) sending=false; } }
        });
    }

    /** Cancels in-flight operations; late completions cannot alter a later connection. */
    public static synchronized void close() {
        generation++;
        if(socket!=null) try { socket.close(); } catch(IOException ignored) { }
        socket=null; connecting=false; connected=false; sending=false; eof=false; error=0;
        rx.clear(); tx.reset(); Network.class.notifyAll();
    }
}
