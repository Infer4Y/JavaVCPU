import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import xyz.ignite4inferneo.DiskInstaller;
import xyz.ignite4inferneo.DosStorageRegression;
import xyz.ignite4inferneo.Storage;
import xyz.ignite4inferneo.jvdos.JvFileSystem;

/** Captures guest-rendered pixels on a disposable disk; never opens storage.img. */
class WikiScreenshots {
    private static Object vm;

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = DosStorageRegression.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(vm, args);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
    }

    private static void command(String text) throws Exception {
        invoke("command", new Class<?>[]{String.class}, text);
    }

    private static void press(int key) throws Exception {
        invoke("press", new Class<?>[]{int[].class}, (Object) new int[]{key});
    }

    private static void snapshot(String name) throws Exception {
        invoke("snapshot", new Class<?>[]{String.class}, name);
    }

    private static void fresh() throws Exception {
        vm = invoke("terminal", new Class<?>[0]);
    }

    private static void open(String name) throws Exception {
        var entries = JvFileSystem.device(0).list(JvFileSystem.ROOT);
        int row = 0;
        while (!entries.get(row).name().equals(name)) row++;
        invoke("click", new Class<?>[]{int.class, int.class}, 20, 53 + row * 6);
        for (int i = 0; i < 40; i++) press(0);
    }

    public static void main(String[] args) throws Exception {
        Path image = Path.of("build/wiki-screenshots.img");
        Files.createDirectories(image.getParent());
        Files.deleteIfExists(image);
        Storage.load(image);
        DiskInstaller.installIfMissing();
        fresh();
        snapshot("dos-shell");
        var disk = JvFileSystem.device(0);
        disk.put(JvFileSystem.ROOT, "MATH.JVS", Files.readAllBytes(Path.of("examples/dos/MATH.JVS")), 1);
        disk.put(JvFileSystem.ROOT, "WELCOME.TXT", ("Welcome to JV-DOS 2.0!\n\n"
            + "Files share one JVFS volume per drive.\n"
            + "Use MKDIR and CD to organize your work.\n\n"
            + "HELP lists everyday commands.\n"
            + "JCTRL opens the bundled desktop.\n"
            + "Open TEXTEDIT.COM or PAINT.COM in Files.\n").getBytes(StandardCharsets.US_ASCII), 1);
        byte[] smile = new byte[4 + 64 * 64];
        smile[0] = 'J'; smile[1] = 'I'; smile[2] = 64; smile[3] = 64;
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int dx = x - 32, dy = y - 32;
            boolean eye = y >= 19 && y < 26 && (x >= 18 && x < 23 || x >= 41 && x < 46);
            boolean mouth = y >= 39 && y <= 47 && Math.abs(dx) <= 18 && Math.abs(y - (47 - dx * dx / 40)) <= 2;
            smile[4 + y * 64 + x] = (byte) (dx * dx + dy * dy <= 900 && !eye && !mouth ? 124 : 0);
        }
        disk.put(JvFileSystem.ROOT, "SMILE.IMG", smile, 2);
        disk.put(JvFileSystem.ROOT, "THEME.JMT", new byte[]{'J', 'M', 2, 0, (byte)255, 0, 60, (byte)129, (byte)255, 0, 64, (byte)129}, 3);
        command("HELP"); snapshot("dos-help");
        fresh(); command("DIR"); snapshot("dos-directory");
        fresh(); command("EDIT MATH.JVS"); snapshot("dos-editor"); press(27);
        fresh(); command("VIEW WELCOME.TXT"); snapshot("dos-document"); press(27);
        fresh(); command("IMAGE SMILE.IMG"); snapshot("dos-image"); press(27);
        fresh(); command("PLAY THEME.JMT"); snapshot("dos-music"); press(27);
        fresh(); command("MATH"); snapshot("dos-jvs-run");
        fresh(); command("HELLO"); snapshot("dos-com-run");
        fresh(); command("JCTRL"); snapshot("desktop-files");
        press(115); command("DIR");
        for (int i = 0; i < 200; i++) press(0);
        snapshot("jctrl-dos");
        fresh(); command("JCTRL"); open("MATH.JVS"); snapshot("jctrl-source");
        fresh(); command("JCTRL"); open("SMILE.IMG"); snapshot("jctrl-image");
        disk.validate();
        Storage.save();
        System.out.println("Wiki screenshots captured under build/ using " + image);
    }
}
