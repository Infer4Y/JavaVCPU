package xyz.ignite4inferneo;

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
import xyz.ignite4inferneo.jvdos.JvFileSystem;

/** Runs unmodified DOS/JCTRL guest bytecode against a loopback HTTP fixture. */
public final class BrowserRegression {
    private static final int BUFFER=78, END=81, STATE=256+128+81;
    private static final Class<?> VM;
    private static final Method PRESS, COMMAND, CLICK;
    private static final Field RAM, INSTRUCTIONS, DISPLAY, PIXELS;
    private static Object vm;
    private static int frameLimit=8000;
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
    private static int offset(int n){return xyz.ignite4inferneo.jvdos.BrowserImage.stateOffset(n);}
    private static int state(int n){return ram()[STATE][offset(n)]&255;}
    private static int pointer(int n){return state(n)+(state(n+1)<<8);}
    private static long metric(Field f)throws Exception{return f.getLong(vm);}
    private static void frames(int count)throws Exception {for(int i=0;i<count;i++)press(0);}
    private static void until(BooleanSupplier done,String label)throws Exception {
        for(int i=0;i<frameLimit;i++){if(done.getAsBoolean())return;press(0);}
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
    private static void rendered()throws Exception {until(()->state(23)==0&&state(26)==1&&pointer(10)==Math.min(pointer(27)+768,pointer(8)),"page renders");frames(4);}
    private static void navigate(int key)throws Exception {key(key);rendered();}
    private static void open(String text)throws Exception {address(text);int serial=state(93);press(10);until(()->state(93)!=serial,"new request begins");until(()->state(26)==1||state(23)==3,"load completes");check(state(23)!=3,"HTTP succeeds (network="+state(150)+")");rendered();}

    private static final class Fixture implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0,8,InetAddress.getLoopbackAddress());
        final LinkedBlockingQueue<String> requests=new LinkedBlockingQueue<>();
        final String html="<HEAD><title>secret title</title><style>secret css</style></HEAD>"
            +"<h1>Heading</h1><p title='a>b'>Hello &amp; &lt;world&gt; &quot;ok&quot; &#39;x&#39;&nbsp;end</p>"
            +"<script>if (a<b) secretScript()</script><style>secretStyle{}</style><div>Last line</div>";
        final String boundary="A".repeat(250);
        final String spacing="B".repeat(48)+" next word";
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
                            byte[] response=response(path).getBytes(StandardCharsets.US_ASCII);
                            // Headers and body cross independent writes, as on a real socket.
                            var output=socket.getOutputStream();for(int i=0;i<response.length;i+=37){output.write(response,i,Math.min(37,response.length-i));output.flush();}
                        }catch(Exception failure){if(!closed)requests.add("ERROR "+failure);}
                    });
                }catch(Exception failure){if(!closed)throw new RuntimeException(failure);}
            });
        }

        String response(String target){
            String path=target;
            if(target.startsWith("http://")||target.startsWith("https://")){var uri=java.net.URI.create(target);path=uri.getRawPath()+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());}
            String location=null,status="200 OK",extra="";
            if(path.startsWith("/r/")){String code=path.substring(3,6);status=code+" Redirect";location=switch(code){case "302"->"../plain?via="+code;case "303"->"http://"+url("/plain?via="+code);case "307"->"//"+url("/plain?via="+code);default->"/plain?via="+code;};}
            else if(path.equals("/q/start")){status="302 Found";location="?done=1";}
            else if(path.startsWith("/loop")){status="308 Loop";location="/loop";}
            else if(path.equals("/too-long")){status="301 Moved";location="/"+"a".repeat(120);}
            else if(path.equals("/unsupported")){status="302 Found";location="javascript:alert(1)";}
            else if(path.equals("/to-https")){status="301 Moved";location="https://example.test/secure";}
            else if(path.startsWith("/redirect"))status="301 Moved";
            else if(path.startsWith("/denied"))status="403 Forbidden";
            if(location!=null)extra="lOcAtIoN: "+location+"\r\n";
            String body=path.equals("/form-empty")?"<form action=/pick><select><option>must not submit</option></select><select name=x><option>old</option><option selected value=''></option></select><select name=city><option>  New   York  </option></select></form>":path.equals("/many-links")?java.util.stream.IntStream.range(0,22).mapToObj(i->"<p><a href='/plain?link="+i+"'>Link "+i+"</a></p>").collect(java.util.stream.Collectors.joining()):path.equals("/forms")?forms():path.startsWith("/large")?large:path.startsWith("/plain")?"one < two & three\nNext line":path.startsWith("/boundary")?"<p>"+boundary+"</p><p>Tail</p>":path.startsWith("/spacing")?"<p>"+spacing+"</p>":path.startsWith("/nav/")?
                "<h1>Navigation</h1><p><a data-href='/wrong' title='a > b' HREF='../plain?from=link&amp;x=2'>Read &amp; go</a></p><a href=/plain?second=1>"+" ".repeat(64)+"Second link</a>":html;
            String type=path.startsWith("/plain")?"cOnTeNt-TyPe:  text/plain; charset=utf-8":"Content-Type: text/html";
            if(path.equals("/chunk")){
                StringBuilder wire=new StringBuilder("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nTransfer-Encoding: chunked\r\n\r\n");
                for(String chunk:new String[]{"<p>Chunk ","one &a","mp; two</p>"})wire.append(Integer.toHexString(chunk.length())).append(";test=yes\r\n").append(chunk).append("\r\n");
                return wire+"0\r\nX-Trailer: ignored\r\n\r\n";
            }
            if(path.equals("/chunk-bad"))return "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nxx";
            return "HTTP/1.0 "+status+"\r\n"+type+"\r\n"+extra+"\r\n"+body;
        }
        String forms(){return "<h1>Old Net style GET forms</h1>"
            +"<form action='/get?discard=old#fragment'><input name=url value=nintendo.com>"
            +"<select name=year><option>1994</option><option>1995</option><option selected>1996</option><option>1997</option></select>"
            +"<input type=hidden name=scripts value=false><input type=hidden name=decode value=false>"
            +"<input disabled name=ignored value=wrong><input type=checkbox name=unchecked value=wrong><input type=submit value=Go></form>"
            +"<form action=/find><input type=text name=query value=the><input type=submit value=Search></form>"
            +"<form action=/wikipediasearch><input type=search name=search value=old>"
            +"<input type=hidden name=title value='Special:Search'><input type=submit name=fulltext value='Search Wikipedia'></form>"
            +"<form method=post action=/must-not-get><input name=secret value=no></form>"
            +"<a href='/plain?after=form'>An ordinary link</a>";}
        String url(String path){return "127.0.0.1:"+server.getLocalPort()+path;}
        String request()throws Exception {String r=requests.poll(5,TimeUnit.SECONDS);check(r!=null&&!r.startsWith("ERROR"),"fixture receives request");return r;}
        @Override public void close()throws Exception {closed=true;server.close();}
    }

    private static String url(){return new String(ram()[STATE],0,state(0),StandardCharsets.US_ASCII);}
    private static String linkUrl(int index){byte[] record=ram()[256+128+84+index/2];int base=(index%2)*128;return new String(record,base,record[base+126]&255,StandardCharsets.US_ASCII);}
    private static String linkLabel(int index){byte[] record=ram()[256+128+84+index/2];int base=(index%2)*128;return new String(record,base+(record[base+126]&255),record[base+127]&255,StandardCharsets.US_ASCII);}
    private static void formChecks(Fixture fixture)throws Exception {
        open(fixture.url("/forms"));fixture.request();
        check(state(108)==4,"GET forms and regular anchors share the chooser; POST is excluded");
        check(linkUrl(0).equals("/get?url=nintendo.com&year=1996&scripts=false&decode=false"),"selected option and hidden defaults; disabled/unchecked fields omitted: "+linkUrl(0));
        check(linkUrl(1).equals("/find?query=the"),"second form query: "+linkUrl(1));
        check(linkUrl(2).equals("/wikipediasearch?search=old&title=Special%3ASearch&fulltext=Search+Wikipedia"),"GET encoding and named submit: "+linkUrl(2));
        check(linkUrl(3).equals("/plain?after=form"),"anchor following forms");
        key('L');until(()->state(23)==4,"forms chooser");key('2');until(()->state(23)==1,"form opens editable query before submitting");
        check(url().endsWith("/find?query=the"),"form resolves relative action");
        // Change a value in place, including insertion in the middle and deletion.
        key(8);type("at");key(37);type("c");key(35);frames(80);check(url().endsWith("/find?query=thact"),"caret insertion and backspace: "+url());
        invoke(method("snapshot",String.class),"web-form");int serial=state(93);press(10);until(()->state(93)!=serial,"form submits");until(()->state(26)==1||state(23)==3,"form response arrives");rendered();
        check(fixture.request().startsWith("GET /find?query=thact HTTP/1.0"),"edited GET form is sent");
        completedAction('B');check(fixture.request().startsWith("GET /forms "),"Back from submitted form restores document");
        open(fixture.url("/form-empty"));fixture.request();check(linkUrl(0).equals("/pick?x=&city=New+York"),"nameless/empty select and collapsed option text whitespace: "+linkUrl(0));
        open(fixture.url("/many-links"));fixture.request();key('L');until(()->state(23)==4,"many-link chooser");
        key(39);until(()->state(23)==4&&state(185)==9&&state(188)==0,"second link group scans entire document");fixture.request();check(linkUrl(0).equals("/plain?link=9")&&linkUrl(8).equals("/plain?link=17"),"links beyond nine remain reachable");
        key(39);until(()->state(23)==4&&state(185)==18&&state(188)==0,"third link group");fixture.request();check(state(108)==4&&linkUrl(3).equals("/plain?link=21"),"partial final group");
        key(37);until(()->state(23)==4&&state(185)==9&&state(188)==0,"previous link group");fixture.request();
        completedAction('9');check(fixture.request().startsWith("GET /plain?link=17 "),"link in a later group follows correct target");
        System.out.println("PASS: GET forms, select defaults, hidden fields, URL encoding, query editing, submission and Back");
    }
    private static void liveChecks(String[] args)throws Exception {
        frameLimit=40000;
        int port=Integer.parseInt(args[1]);if(port!=0){ram()[STATE][offset(94)]=1;ram()[STATE][offset(148)]=(byte)port;ram()[STATE][offset(149)]=(byte)(port>>>8);}
        open(args[2]);StringBuilder document=new StringBuilder(cache());int screens=1;
        while(state(25)!=0&&screens<40){navigate('N');document.append(cache());screens++;}
        check(state(25)==0,"live page finishes within forty screens");
        String status=""+(char)state(52)+(char)state(53)+(char)state(54);
        check(status.startsWith("2"),"live HTTP status: "+status);
        System.out.println("LIVE "+status+" "+url()+" screens="+screens+" links/forms="+state(108));
        for(int i=0;i<state(108);i++)System.out.println("  "+(i+1)+" "+linkUrl(i));
        Files.writeString(Path.of("build/web-live-rendered.txt"),document);
        invoke(method("snapshot",String.class),"web-live");
        if(args[2].contains("theoldnet.com/lite")){
            check(document.toString().contains("Welcome"),"Old Net welcome rendered");
            check(linkUrl(0).equals("/get?url=nintendo.com&year=1996&scripts=false&decode=false"),"live archived URL form defaults");
            check(linkUrl(1).equals("/find?query=the"),"live archive search form");
            check(linkUrl(2).contains("title=Special%3ASearch"),"live Wikipedia form encoding");
            navigate('H');invoke(method("snapshot",String.class),"web-oldnet");
            key('L');until(()->state(23)==4,"live form chooser");frames(80);invoke(method("snapshot",String.class),"web-oldnet-links");key('2');until(()->state(23)==1,"live search query editor");frames(80);invoke(method("snapshot",String.class),"web-oldnet-form");
            int serial=state(93);press(10);until(()->state(93)!=serial,"live search submission begins");until(()->state(26)==1||state(23)==3,"live search response");check(state(23)!=3,"live search succeeds");rendered();
            check(state(52)=='2',"live search HTTP status");check(url().contains("/find?query=the"),"live search target");invoke(method("snapshot",String.class),"web-oldnet-search");
            System.out.println("PASS: live Old Net homepage, three forms, query editor and archive search submission");
        }
        press(27);frames(100);
    }
    private static void completedAction(int key)throws Exception {
        int serial=state(93);key(key);until(()->state(93)!=serial,"navigation begins");until(()->state(26)==1||state(23)==3,"navigation completes");check(state(23)!=3,"navigation HTTP succeeds");rendered();
    }
    private static void navigationChecks(Fixture fixture)throws Exception {
        for(int status:new int[]{301,302,303,307,308}){
            open(fixture.url("/r/"+status));check(fixture.request().startsWith("GET /r/"+status+" "),"redirect origin");
            check(fixture.request().startsWith("GET /plain?via="+status+" "),"redirect target and URI resolution "+status);
            check(cache().startsWith("one < two & three")&&state(52)=='2',"redirect renders final body and status");
            check(url().endsWith("/plain?via="+status),"redirect updates address");
        }
        open(fixture.url("/q/start"));fixture.request();check(fixture.request().startsWith("GET /q/start?done=1 "),"query-only reference preserves the path");
        open(fixture.url("/nav/base/index"));fixture.request();check(state(108)==2,"quoted and unquoted anchors are collected");
        check(cache().contains("[1]Read & go")&&cache().contains("[2] Second link"),"numbered links preserve decoded labels");
        check(linkLabel(1).trim().equals("Second link"),"indentation does not consume the link label");
        key('L');until(()->state(23)==4,"link chooser opens");frames(80);invoke(method("snapshot",String.class),"web-links");
        completedAction('1');check(fixture.request().startsWith("GET /nav/plain?from=link&x=2 "),"selected relative href resolves and decodes amp entity");
        completedAction('B');check(fixture.request().startsWith("GET /nav/base/index "),"Back restores the document URL");check(state(108)==2,"Back rebuilds links");
        open(fixture.url("/chunk"));fixture.request();check(cache().startsWith("Chunk one & two"),"chunk sizes/extensions/trailers are not rendered");
        for(String path:new String[]{"/loop","/too-long","/unsupported","/chunk-bad"}){
            address(fixture.url(path));press(10);until(()->state(23)==3,"bad navigation/framing is bounded: "+path);frames(64);
            int requests=path.equals("/loop")?6:1;for(int i=0;i<requests;i++)fixture.request();check(Network.read(0)==0,"failed navigation closes transport");
        }
        address(fixture.url("/to-https"));press(10);until(()->state(23)==3,"HTTPS redirect reports gateway requirement");fixture.request();check(url().equals("https://example.test/secure"),"HTTPS target retained for enabling gateway");
        try(Fixture gateway=new Fixture()){
            int port=gateway.server.getLocalPort();ram()[STATE][offset(148)]=(byte)port;ram()[STATE][offset(149)]=(byte)(port>>>8);
            completedAction('G');check(gateway.request().startsWith("GET https://example.test/secure HTTP/1.0"),"gateway uses absolute HTTPS target over existing TCP device");check(state(94)==1,"gateway enabled explicitly");
            key('G');until(()->state(23)==3,"disabling gateway restores direct HTTPS limitation");check(state(94)==0,"gateway toggle off");
        }
        System.out.println("PASS: redirects, relative URLs, loops/bounds, selectable links, Back, chunked framing and optional HTTPS gateway transport");
    }
    public static void main(String[] args)throws Exception {
        Path disk=Path.of(args.length>0?"build/browser-"+args[0]+"-regression.img":"build/browser-regression.img");Files.createDirectories(disk.getParent());
        try(var channel=java.nio.channels.FileChannel.open(disk,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.WRITE)){channel.truncate(0);channel.position(Storage.STORAGE_SIZE-1L);channel.write(java.nio.ByteBuffer.wrap(new byte[1]));}
        Storage.load(disk);JvFileSystem.device(0).format();DiskInstaller.installIfMissing();vm=invoke(method("terminal"));
        byte[] program=Files.readAllBytes(Path.of("examples/jctrl/WEB.COM"));
        check(program.length<=BUFFER*256,"program leaves explicit cache/state workspace");
        var volume=JvFileSystem.device(0);
        volume.put(JvFileSystem.ROOT,"WEB.COM",program,0);
        byte[] sentinel="USER FILE - DO NOT REPLACE".getBytes(StandardCharsets.US_ASCII);
        var existingCache=volume.mkdir(JvFileSystem.ROOT,"WEB.CACHE");
        volume.put(existingCache.first(),"WEB0000.TXT",sentinel,1);
        volume.put(JvFileSystem.ROOT,"KEEP.TXT",sentinel,1);
        invoke(COMMAND,"RUN WEB.COM");check(((int[])field("r").get(vm))[11]==0,"fullscreen guard returns to DOS");
        invoke(COMMAND,"JCTRL");int row=0;var entries=JvFileSystem.device(0).list(JvFileSystem.ROOT);while(!entries.get(row).name().equals("WEB.COM"))row++;invoke(CLICK,20,53+row*6);frames(200);
        check(state(23)==1,"browser opens address prompt");
        check((ram()[481][436-256]&255)==1,"browser opts into shared document surface");
        try(Fixture fixture=new Fixture()){
            if(args.length>0&&args[0].equals("navigation")){navigationChecks(fixture);return;}
            if(args.length>0&&args[0].equals("forms")){formChecks(fixture);return;}
            if(args.length>0&&args[0].equals("live")){liveChecks(args);return;}
            if(args.length>0&&args[0].equals("gateway")){
                int port=Integer.parseInt(args[1]);ram()[STATE][offset(94)]=1;ram()[STATE][offset(148)]=(byte)port;ram()[STATE][offset(149)]=(byte)(port>>>8);
                open(args[2]);check(cache().startsWith("Hello from the origin"),"real guest/TLS gateway body");check(url().equals(args[2]),"HTTPS address retained end to end");
                invoke(method("snapshot",String.class),"web-gateway");press(27);frames(100);System.out.println("PASS: guest WEB.COM through the real verified-TLS gateway");return;
            }
            open(fixture.url("/Case?x=1#section"));String request=fixture.request();
            check(request.startsWith("GET /Case?x=1 HTTP/1.0\r\n"),"path case/query and fragment removal");
            check(request.contains("Host: 127.0.0.1:"+fixture.server.getLocalPort()+"\r\n"),"Host includes custom port");
            check(request.contains("Accept-Encoding: identity"),"no compressed response requested");
            check(state(92)==2,"completed HTML uses JVFS cache");String rendered=cache();invoke(method("snapshot",String.class),"web-browser");
            check(rendered.startsWith("Heading"+" ".repeat(41)),"headings start a new text row");
            check(rendered.contains("Hello & <world> \"ok\" 'x' end"),"named entity decoding and quote-aware tags");
            check(!rendered.contains("secret")&&rendered.contains("Last line"),"head/script/style suppression, including script less-than");
            check(new String(new byte[]{(byte)state(52),(byte)state(53),(byte)state(54)},StandardCharsets.US_ASCII).equals("200"),"status code");
            byte[][] retained=new byte[5][];for(int i=0;i<5;i++)retained[i]=ram()[475+i].clone();
            int wx=ram()[480][168]&255,wy=ram()[480][169]&255;
            var mouse=method("mouse",int.class,int.class,int.class);
            invoke(mouse,wx+20,wy+5,0);invoke(mouse,wx+20,wy+5,1);invoke(mouse,wx+16,wy+9,1);invoke(mouse,wx+16,wy+9,0);frames(8);
            for(int i=0;i<5;i++)check(Arrays.equals(retained[i],ram()[475+i]),"window drag preserves document cells");
            check((ram()[480][168]&255)==wx-4&&(ram()[480][169]&255)==wy+4,"document window moves normally");
            // A document click must not trigger the generic RUN / OPEN toolbar.
            invoke(CLICK,wx+10,wy+20);frames(16);check(state(26)==1,"page click preserves running browser");
            open(fixture.url("/boundary"));fixture.request();check(cache().startsWith(fixture.boundary+" ".repeat(38)+"Tail"),"paragraph padding carries across a 256-byte page");
            open(fixture.url("/spacing"));fixture.request();check(cache().startsWith(fixture.spacing),"space at the wrap boundary is preserved");
            for(String path:new String[]{"/redirect","/denied"}){open(fixture.url(path));fixture.request();check(state(52)==(path.equals("/redirect")?'3':'4'),"HTTP error/redirect status remains visible");}
            open(fixture.url("/plain"));fixture.request();check(cache().startsWith("one < two & three"),"plain text preserves markup characters");check(cache().contains("Next line"),"plain text newline");

            address(fixture.url("/large"));long displays=metric(DISPLAY),instructions=metric(INSTRUCTIONS);int serial=state(93);press(10);until(()->state(93)!=serial,"new request begins");
            until(()->state(26)==1,"first streamed cache");long firstDisplays=metric(DISPLAY)-displays,firstInstructions=metric(INSTRUCTIONS)-instructions;rendered();fixture.request();
            check(pointer(8)==END*256&&state(25)==1,"full cache pauses the response instead of truncating");
            System.out.println("First "+cache().length()+" text bytes: "+firstDisplays+" display operations while receiving, "+firstInstructions+" host-emulated instructions");
            check(firstDisplays<cache().length()*3,"network byte path yields in batches, not per byte");
            check(cache().equals(fixture.large.substring(0,cache().length())),"first stream cache exact");
            int consumed=cache().length();navigate('N');
            while(true){String part=cache();check(part.equals(fixture.large.substring(consumed,consumed+part.length())),"stream preserves bytes across cache boundaries");consumed+=part.length();
                if(state(25)==0)break;
                navigate('N');
            }
            check(consumed==fixture.large.length(),"entire response larger than RAM is readable");
            System.out.println("PASS: streamed "+consumed+" bytes through "+((END-BUFFER)*256)+" bytes of guest cache");
            check(state(92)==2,"large page commits its disk history");
            var diskCache=JvFileSystem.device(0).find(JvFileSystem.ROOT,"WEB.CACHE");
            check(diskCache!=null&&diskCache.type()==4,"cache folder exists");
            var saved=JvFileSystem.device(0).list(diskCache.first());
            check(saved.stream().anyMatch(e->Arrays.equals(JvFileSystem.device(0).read(e),fixture.large.getBytes(StandardCharsets.US_ASCII))),"full rendered document is saved as ordinary JVFS text");
            navigate('H');check(cache().startsWith(fixture.large.substring(0,768)),"Home reloads the beginning beyond the RAM cache");
            navigate('N');check(cache().startsWith(fixture.large.substring(768,1536)),"disk Next");
            navigate('N');check(cache().startsWith(fixture.large.substring(1536)),"disk seek across cluster");
            navigate('P');check(cache().startsWith(fixture.large.substring(768,1536)),"disk Previous crosses old cache boundaries");
            wx=ram()[480][168]&255;wy=ram()[480][169]&255;
            invoke(CLICK,wx+14,wy+160);frames(32);rendered();check(cache().startsWith(fixture.large.substring(0,768)),"footer Previous");
            invoke(CLICK,wx+42,wy+160);frames(32);rendered();check(cache().startsWith(fixture.large.substring(768,1536)),"footer Next");
            field("mouseWheel").setInt(vm,1);frames(32);rendered();check(cache().startsWith(fixture.large.substring(1536)),"wheel Next");
            field("mouseWheel").setInt(vm,-1);frames(32);rendered();check(cache().startsWith(fixture.large.substring(768,1536)),"wheel Previous");
            key('R');rendered();check(fixture.request().startsWith("GET /large "),"reload keeps address");
            for(String bad:new String[]{"127.0.0.1:","127.0.0.1:0/","127.0.0.1:65536/","127.0.0.1:999999/","/missing-host","https://example.test/"}){
                address(bad);press(10);until(()->state(23)==3,"invalid URL rejected");frames(120);check(Network.read(0)==0,"invalid URL leaves no connection");
            }
            address("abc");key(8);check(state(0)==2,"backspace");key(36);check(state(0)==0,"Home clears address");
            long idlePixels=metric(PIXELS);frames(200);check(metric(PIXELS)==idlePixels,"idle address prompt does not repaint");
            check(Arrays.equals(program,JvFileSystem.device(0).read(JvFileSystem.device(0).find(JvFileSystem.ROOT,"WEB.COM"))),"browser preserves the executable");
            check(Arrays.equals(sentinel,volume.read(volume.find(existingCache.first(),"WEB0000.TXT"))),"cache never replaces an existing filename");
            check(Arrays.equals(sentinel,volume.read(volume.find(JvFileSystem.ROOT,"KEEP.TXT"))),"cache preserves unrelated user files");
            check(volume.list(existingCache.first()).stream().allMatch(e->e.length()>0),"cancel removes the uncommitted cache file");
            press(27);frames(100);check(Network.read(0)==0,"close cancels network");check((ram()[481][436-256]&255)==0,"closing restores default application graphics mode");

            var noCache=volume.mkdir(JvFileSystem.ROOT,"NOCACHE");
            volume.put(noCache.first(),"WEB.COM",program,0);
            volume.put(noCache.first(),"WEB.CACHE",sentinel,1);
            vm=invoke(method("terminal"));invoke(COMMAND,"CD NOCACHE");invoke(COMMAND,"JCTRL");
            int fallbackRow=0;var fallbackEntries=volume.list(noCache.first());while(!fallbackEntries.get(fallbackRow).name().equals("WEB.COM"))fallbackRow++;
            invoke(CLICK,20,53+fallbackRow*6);frames(200);
            byte[] fallbackBefore=Storage.readRange(0,Storage.STORAGE_SIZE);
            open(fixture.url("/plain"));fixture.request();check(state(92)==0,"unavailable cache uses RAM mode");
            check(cache().startsWith("one < two & three"),"RAM fallback still renders the response");
            check(Arrays.equals(fallbackBefore,Storage.readRange(0,Storage.STORAGE_SIZE)),"a cache-path collision preserves every disk byte");
            press(27);frames(100);
            System.out.println("PASS: WEB rendering, keyboard, URL validation, persistent history, cache collision/fallback, cancellation and user-file preservation");
        }finally{Network.close();}
    }
}
