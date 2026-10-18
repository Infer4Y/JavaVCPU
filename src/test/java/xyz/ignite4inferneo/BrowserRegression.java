import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import xyz.ignite4inferneo.Network;
import xyz.ignite4inferneo.Storage;
import xyz.ignite4inferneo.jvdos.DesktopImage;

/** Runs unmodified DOS/JCTRL guest bytecode against a loopback HTTP fixture. */
public final class BrowserRegression {
    private static final int BUFFER=27, END=30, STATE=256+128+31;
    private static final Class<?> VM;
    private static final Method PRESS, COMMAND, CLICK;
    private static final Field RAM, INSTRUCTIONS, DISPLAY, PIXELS;
    private static Object vm;
    static {
        try {
            VM=Class.forName("xyz.ignite4inferneo.DosStorageRegression");
            PRESS=method("press",int[].class);COMMAND=method("command",String.class);
            CLICK=method("click",int.class,int.class);
            RAM=field("ram");INSTRUCTIONS=field("instructions");DISPLAY=field("displayFrames");PIXELS=field("pixelWrites");
        } catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    private static Method method(String name,Class<?>... types)throws Exception {Method m=VM.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private static Field field(String name)throws Exception {Field f=VM.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object invoke(Method m,Object... args)throws Exception {
        try{return m.invoke(vm,args);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception e)throw e;if(failure.getCause() instanceof Error e)throw e;throw failure;}
    }
    private static void press(int key)throws Exception {invoke(PRESS,(Object)new int[]{key});}
    private static byte[][] ram(){try{return (byte[][])RAM.get(vm);}catch(Exception e){throw new RuntimeException(e);}}
    private static int state(int n){return ram()[STATE][n]&255;}
    private static int pointer(int n){return state(n)+(state(n+1)<<8);}
    private static long metric(Field f)throws Exception{return f.getLong(vm);}
    private static void frames(int count)throws Exception {for(int i=0;i<count;i++)press(0);}
    private static void until(BooleanSupplier done,String label)throws Exception {
        for(int i=0;i<8000;i++){if(done.getAsBoolean())return;press(0);}
        throw new AssertionError(label+" (mode="+state(23)+", ready="+state(26)+", hidden="+state(63)+", size="+pointer(8)+")");
    }
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static String cache(){int length=pointer(8)-(BUFFER<<8);byte[] bytes=new byte[length];for(int i=0;i<length;i++)bytes[i]=ram()[256+128+BUFFER+i/256][i&255];return new String(bytes,StandardCharsets.US_ASCII);}
    private static void key(int code)throws Exception {press(code);frames(32);}
    private static void type(String text)throws Exception {
        for(char ch:text.toCharArray()){
            if(ch>='a'&&ch<='z')key(Character.toUpperCase(ch));
            else if(ch>='A'&&ch<='Z'){key(16);key(ch);key(144);}
            else if(":?#!&_%+".indexOf(ch)>=0){key(16);key(switch(ch){case ':'->59;case '?'->47;case '#'->51;case '!'->49;case '&'->55;case '_'->45;case '%'->53;case '+'->61;default->throw new AssertionError();});key(144);}
            else key(ch);
        }
    }
    private static void address(String text)throws Exception {key('U');frames(160);key(36);type(text);check(state(0)==text.length(),"address length");}
    private static void rendered()throws Exception {until(()->state(23)==0&&state(26)==1&&pointer(10)==Math.min(pointer(27)+512,pointer(8)),"page renders");frames(4);}
    private static void navigate(int key)throws Exception {key(key);rendered();}
    private static void open(String text)throws Exception {address(text);press(10);until(()->state(26)==1||state(23)==3,"load completes");check(state(23)!=3,"HTTP succeeds");rendered();}

    private static final class Fixture implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0,8,InetAddress.getLoopbackAddress());
        final LinkedBlockingQueue<String> requests=new LinkedBlockingQueue<>();
        final String html="<HEAD><title>secret title</title><style>secret css</style></HEAD>"
            +"<h1>Heading</h1><p title='a>b'>Hello &amp; &lt;world&gt; &quot;ok&quot; &#39;x&#39;&nbsp;end</p>"
            +"<script>if (a<b) secretScript()</script><style>secretStyle{}</style><div>Last line</div>";
        final String large=java.util.stream.IntStream.range(0,64).mapToObj(i->String.format("%08dABCDEFGHIJKLMNOPQRSTUVWX",i)).collect(java.util.stream.Collectors.joining());
        volatile boolean closed;
        Fixture()throws Exception {
            Thread.startVirtualThread(()->{
                while(!closed)try {
                    var socket=server.accept();
                    Thread.startVirtualThread(()->{
                        try(socket){socket.setSoTimeout(5000);StringBuilder request=new StringBuilder();var input=socket.getInputStream();
                            while(request.indexOf("\r\n\r\n")<0){int ch=input.read();if(ch<0)throw new AssertionError("unfinished request");request.append((char)ch);if(request.length()>4096)throw new AssertionError("unbounded request");}
                            requests.add(request.toString());String path=request.toString().split(" ")[1];
                            String body=path.startsWith("/large")?large:path.startsWith("/plain")?"one < two & three\nNext line":html;
                            String type=path.startsWith("/plain")?"cOnTeNt-TyPe:  text/plain; charset=utf-8":"Content-Type: text/html";
                            byte[] response=("HTTP/1.0 200 OK\r\n"+type+"\r\n\r\n"+body).getBytes(StandardCharsets.US_ASCII);
                            // Headers and body cross independent writes, as on a real socket.
                            var output=socket.getOutputStream();for(int i=0;i<response.length;i+=37){output.write(response,i,Math.min(37,response.length-i));output.flush();}
                        }catch(Exception failure){if(!closed)requests.add("ERROR "+failure);}
                    });
                }catch(Exception failure){if(!closed)throw new RuntimeException(failure);}
            });
        }
        String url(String path){return "127.0.0.1:"+server.getLocalPort()+path;}
        String request()throws Exception {String r=requests.poll(5,TimeUnit.SECONDS);check(r!=null&&!r.startsWith("ERROR"),"fixture receives request");return r;}
        @Override public void close()throws Exception {closed=true;server.close();}
    }
    public static void main(String[] args)throws Exception {
        Path disk=Path.of("build/browser-regression.img");Files.createDirectories(disk.getParent());
        try(var channel=java.nio.channels.FileChannel.open(disk,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.WRITE)){channel.truncate(0);channel.position(Storage.STORAGE_SIZE-1L);channel.write(java.nio.ByteBuffer.wrap(new byte[1]));}
        Storage.load(disk);vm=invoke(method("terminal"));
        invoke(method("chain",int.class,int.class,String.class,byte[].class,int.class),100,0,"JCTRL.COM",DesktopImage.program(),0);
        byte[] program=Files.readAllBytes(Path.of("examples/jctrl/WEB.COM"));
        check(program.length<=BUFFER*256,"program leaves explicit cache/state workspace");
        invoke(method("chain",int.class,int.class,String.class,byte[].class,int.class),100,160,"WEB.COM",program,0);
        ram()[252][220]=100;
        invoke(COMMAND,"RUN WEB.COM");check(((int[])field("r").get(vm))[11]==0&&ram()[252][220]==100,"fullscreen guard returns to DOS and preserves bank");
        invoke(COMMAND,"RUN JCTRL.COM");invoke(CLICK,20,59);frames(200);
        check(state(23)==1,"browser opens address prompt");byte[] before=Storage.readRange(0,Storage.STORAGE_SIZE);
        try(Fixture fixture=new Fixture()){
            open(fixture.url("/Case?x=1#section"));String request=fixture.request();
            check(request.startsWith("GET /Case?x=1 HTTP/1.0\r\n"),"path case/query and fragment removal");
            check(request.contains("Host: 127.0.0.1:"+fixture.server.getLocalPort()+"\r\n"),"Host includes custom port");
            check(request.contains("Accept-Encoding: identity"),"no compressed response requested");
            String rendered=cache();invoke(method("snapshot",String.class),"web-browser");
            check(rendered.startsWith("Heading"+" ".repeat(25)),"headings start a new text row");
            check(rendered.contains("Hello & <world> \"ok\" 'x' end"),"named entity decoding and quote-aware tags");
            check(!rendered.contains("secret")&&rendered.contains("Last line"),"head/script/style suppression, including script less-than");
            check(new String(new byte[]{(byte)state(52),(byte)state(53),(byte)state(54)},StandardCharsets.US_ASCII).equals("200"),"status code");
            open(fixture.url("/plain"));fixture.request();check(cache().startsWith("one < two & three"),"plain text preserves markup characters");check(cache().contains("Next line"),"plain text newline");

            address(fixture.url("/large"));long displays=metric(DISPLAY),instructions=metric(INSTRUCTIONS);press(10);
            until(()->state(26)==1,"first streamed cache");long firstDisplays=metric(DISPLAY)-displays,firstInstructions=metric(INSTRUCTIONS)-instructions;rendered();fixture.request();
            check(pointer(8)==END*256&&state(25)==1,"full cache pauses the response instead of truncating");
            System.out.println("First "+cache().length()+" text bytes: "+firstDisplays+" display operations while receiving, "+firstInstructions+" host-emulated instructions");
            check(firstDisplays<cache().length()*3,"network byte path yields in batches, not per byte");
            check(cache().equals(fixture.large.substring(0,cache().length())),"first stream cache exact");
            int consumed=cache().length();String previous=cache();navigate('N');check(state(28)==BUFFER+2,"next page");
            navigate('P');check(state(28)==BUFFER,"previous page");check(cache().equals(previous),"paging does not refetch or alter cache");
            navigate('N');navigate('N');
            while(true){String part=cache();check(part.equals(fixture.large.substring(consumed,consumed+part.length())),"stream preserves bytes across cache boundaries");consumed+=part.length();
                if(state(25)==0)break;
                navigate('N');navigate('N');
            }
            check(consumed==fixture.large.length(),"entire response larger than RAM is readable");
            System.out.println("PASS: streamed "+consumed+" bytes through "+((END-BUFFER)*256)+" bytes of guest cache");
            key('R');rendered();check(fixture.request().startsWith("GET /large "),"reload keeps address");
            for(String bad:new String[]{"127.0.0.1:","127.0.0.1:0/","127.0.0.1:65536/","127.0.0.1:999999/","/missing-host","https://example.test/"}){
                address(bad);press(10);until(()->state(23)==3,"invalid URL rejected");frames(120);check(Network.read(0)==0,"invalid URL leaves no connection");
            }
            address("abc");key(8);check(state(0)==2,"backspace");key(36);check(state(0)==0,"Home clears address");
            long idlePixels=metric(PIXELS);frames(200);check(metric(PIXELS)==idlePixels,"idle address prompt does not repaint");
            check(Arrays.equals(before,Storage.readRange(0,Storage.STORAGE_SIZE)),"browser and navigation preserve every disk byte");
            press(27);frames(100);check(Network.read(0)==0,"close cancels network");
            System.out.println("PASS: WEB rendering, keyboard, HTTP URL/port validation, navigation, idle redraw and disk preservation");
        }finally{Network.close();}
    }
}
