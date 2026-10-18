package xyz.ignite4inferneo;

import java.awt.image.BufferedImage;

final class MonitorRegression {
    static void run() throws Exception {
        var field = Monitor.class.getDeclaredField("frontBuffer");
        field.setAccessible(true);
        Monitor.clear(); Monitor.swapBuffers();
        Monitor.pushMemory((byte)3, (byte)4, (byte)127);
        check(pixel(field,3,4) == 0, "pixel write remains unpublished");
        Monitor.swapBuffers();
        check(pixel(field,3,4) == 0xffffff, "completed frame published");
        BufferedImage first = (BufferedImage)field.get(null);
        Monitor.pushMemory((byte)5, (byte)6, (byte)127);
        check(pixel(field,5,6) == 0, "next frame cannot alter presented frame");
        check((Monitor.fetchMemory((byte)3,(byte)4)&255) == 127, "drawing buffer preserves prior pixels");
        Monitor.swapBuffers();
        check(first != field.get(null), "display exchanges buffer identities");
        check(pixel(field,3,4) == 0xffffff && pixel(field,5,6) == 0xffffff, "incremental frames retain old pixels");
        Monitor.clear();
        check(pixel(field,3,4) == 0xffffff, "clear remains unpublished");
        Monitor.swapBuffers();check(pixel(field,3,4) == 0, "clear published at swap");
        for(int color=0;color<128;color++) {
            Monitor.pushMemory((byte)0,(byte)0,(byte)color);
            check((Monitor.fetchMemory((byte)0,(byte)0)&255)==color,"palette read preserves color "+color);
            Monitor.swapBuffers();
            for(int pass=0;pass<8;pass++) {
                byte saved=Monitor.fetchMemory((byte)0,(byte)0);
                Monitor.pushMemory((byte)0,(byte)0,saved);Monitor.swapBuffers();
            }
            check((Monitor.fetchMemory((byte)0,(byte)0)&255)==color,"repeated save/restore preserves color "+color);
        }
        System.out.println("PASS: framebuffer swaps, published clears and lossless save/restore of all 128 palette colors");
    }
    private static int pixel(java.lang.reflect.Field field,int x,int y) throws Exception {
        return ((BufferedImage)field.get(null)).getRGB(x,y)&0xffffff;
    }
    private static void check(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }
}
