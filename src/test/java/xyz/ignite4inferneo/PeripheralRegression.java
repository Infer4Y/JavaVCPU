package xyz.ignite4inferneo;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Offline, local TCP and real CPU helper regression checks. */
public final class PeripheralRegression {
    static void run(Path bootImage) throws Exception {
        Path second=bootImage.resolveSibling("second-disk-regression.img");
        Files.deleteIfExists(second); Storage.mount(1,second);
        byte[] saved=Main.REGISTERS.clone();
        try {
            byte bootLast=Storage.readData((byte)255,(byte)255,(byte)255);
            Arrays.fill(Main.REGISTERS,(byte)0);
            Main.REGISTERS[10]=1; Main.REGISTERS[0]=(byte)255;
            Main.REGISTERS[1]=(byte)255; Main.REGISTERS[2]=(byte)255; Main.REGISTERS[3]=42;
            Main.writeStorage((byte)0,(byte)1,(byte)2,(byte)3);
            Main.readStorage((byte)0,(byte)1,(byte)2,(byte)10);
            check(Main.REGISTERS[10]==42,"CPU storage selector alias");
            check(Storage.readData((byte)255,(byte)255,(byte)255)==bootLast,"boot disk isolated");
            Storage.save(); Storage.mount(1,second);
            check(Storage.readData(1,(byte)255,(byte)255,(byte)255)==42,"second disk persistence");
            Main.REGISTERS[10]=1; Main.readInput((byte)0x40,(byte)4);
            check(Main.REGISTERS[4]==1,"mounted detection");
            Main.REGISTERS[10]=(byte)255; Main.readInput((byte)0x40,(byte)4);
            check(Main.REGISTERS[4]==0,"absent detection");
            try { Storage.mount(2,bootImage); throw new AssertionError("Duplicate image accepted"); }
            catch(java.io.IOException expected) { }
            networkChecks();
        } finally { Network.close(); System.arraycopy(saved,0,Main.REGISTERS,0,saved.length); }
        System.out.println("PASS: multiple disk isolation/persistence and asynchronous TCP byte transfers/errors");
    }
    private static void networkChecks() throws Exception {
        Network.write(0,(byte)4); Network.write(0,(byte)1);
        check((Network.read(0)&128)!=0 && Network.read(1)==2,"invalid network configuration");
        Network.write(0,(byte)4);
        try(ServerSocket server=new ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            CompletableFuture<Void> peer=CompletableFuture.runAsync(() -> {
                try(var socket=server.accept()) {
                    socket.setSoTimeout(5000);
                    byte[] request=socket.getInputStream().readNBytes(3);
                    check(Arrays.equals(request,new byte[]{0,42,(byte)255}),"TCP outbound bytes");
                    socket.getOutputStream().write(new byte[]{(byte)255,0,73});
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            for(byte c:"127.0.0.1".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) Network.write(2,c);
            Network.write(3,(byte)(server.getLocalPort()>>>8)); Network.write(4,(byte)server.getLocalPort());
            Main.REGISTERS[0]=0; Main.REGISTERS[1]=1;
            Main.writeNetwork((byte)0,(byte)1);
            await(() -> (Network.read(0)&Network.CONNECTED)!=0,"connect");
            Main.REGISTERS[0]=5;
            for(byte value:new byte[]{0,42,(byte)255}) { Main.REGISTERS[1]=value; Main.writeNetwork((byte)0,(byte)1); }
            Network.write(0,(byte)2);
            await(() -> (Network.read(0)&Network.EOF)!=0,"receive EOF");
            check(Network.read(8)==3,"receive byte count");
            Main.REGISTERS[0]=6; Main.readNetwork((byte)0,(byte)0);
            check((Main.REGISTERS[0]&255)==255,"network address/destination alias");
            check(Network.read(6)==0 && Network.read(6)==73 && (Network.read(0)&Network.RX_READY)==0,"binary receive/consume");
            peer.get(5,TimeUnit.SECONDS);
        }
        Network.write(0,(byte)4);
        for(int i=0;i<4097;i++) Network.write(5,(byte)i);
        check(Network.read(1)==5,"bounded transmit queue");
        Network.write(0,(byte)4); check(Network.read(0)==0 && Network.read(8)==0,"reset");
        int unusedPort;
        try(var unused=new ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())) { unusedPort=unused.getLocalPort(); }
        for(byte c:"127.0.0.1".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) Network.write(2,c);
        Network.write(3,(byte)(unusedPort>>>8)); Network.write(4,(byte)unusedPort); Network.write(0,(byte)1);
        await(() -> (Network.read(0)&Network.ERROR)!=0,"connection refused status");
        check(Network.read(1)==3,"connection error code");
        Network.write(0,(byte)4); check(Network.read(0)==0,"reset clears failed connection");
    }
    private static void await(BooleanSupplier condition,String message) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!condition.getAsBoolean()) {
            if(System.nanoTime()>deadline) throw new AssertionError("Timed out: "+message);
            Thread.sleep(2);
        }
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
