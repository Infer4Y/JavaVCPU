package xyz.ignite4inferneo.jvdos;

import java.util.List;

/** One component list for boot, Workshop and the distributable setup program. */
public final class DesktopBundle {
    private DesktopBundle() { }
    public record Component(String name, byte[] bytes) { }
    public static List<Component> components() {
        return List.of(new Component("JCTRL.COM",DesktopImage.program()),
                new Component("TEXTEDIT.COM",TextEditImage.program()),
                new Component("PAINT.COM",PaintImage.program()));
    }
}
