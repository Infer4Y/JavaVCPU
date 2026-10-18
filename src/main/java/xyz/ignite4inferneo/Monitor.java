/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

import javax.swing.*;
import module java.desktop;

/**
 * Provides the virtual CPU's 256-by-256-pixel framebuffer and scaled Swing display.
 *
 * <p>Graphics instructions update an off-screen framebuffer through {@link #pushMemory(byte, byte,
 * byte)}. Call {@link #display()} to schedule the completed framebuffer for painting in the Swing
 * window. The window renders the framebuffer at 800 by 800 pixels with nearest-neighbor scaling.
 * A color operand uses seven bits in {@code RRGGGBB} order: red and blue each have four intensity
 * levels, and green has eight.</p>
 */
public class Monitor {
    /** Width and height of the byte-addressable framebuffer. */
    public static final int FRAMEBUFFER_SIZE = 256;

    /** The Swing window used to present the framebuffer. */
    private static JFrame display;

    /** The 256-by-256 framebuffer updated by the virtual CPU. */
    private static BufferedImage buffer = new BufferedImage(
            FRAMEBUFFER_SIZE, FRAMEBUFFER_SIZE, BufferedImage.TYPE_INT_RGB);

    /** Completed frame owned by the presenter; guest writes only touch buffer. */
    private static BufferedImage frontBuffer = new BufferedImage(
            FRAMEBUFFER_SIZE, FRAMEBUFFER_SIZE, BufferedImage.TYPE_INT_RGB);

    /** Guards buffer swaps, guest accesses and Swing painting. */
    private static final Object bufferLock = new Object();

    /** Component that renders the framebuffer with nearest-neighbor scaling. */
    private static final JPanel displayPanel = new JPanel() {
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            ((Graphics2D) graphics).setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            synchronized (bufferLock) {
                graphics.drawImage(frontBuffer, 0, 0, getWidth(), getHeight(), this);
            }
        }
    };

    /**
     * Creates and shows the scaled display window.
     *
     * <p>This method must be called before {@link #display()} presents the framebuffer.</p>
     */
    public static void initialize(){
        runOnEventDispatchThread(() -> {
            if (display != null) {
                return;
            }

            display = new JFrame("JavaVCPU display out");
            displayPanel.setPreferredSize(new Dimension(800, 800));
            display.setContentPane(displayPanel);
            Input.installMouse(displayPanel);
            display.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            display.pack();
            display.setVisible(true);
        });
    }

    /**
     * Swaps the completed drawing buffer into the display and schedules a repaint.
     *
     * <p>Framebuffer changes are not visible until this method is called.</p>
     */
    public static void display(){
        if (display == null) {
            throw new IllegalStateException("Display has not been initialized");
        }
        swapBuffers();
        displayPanel.repaint();
    }

    /** Publish atomically, then preserve the frame in the next drawing buffer for incremental drawing.
     * The paint lock prevents Swing from retaining the old front while it becomes writable.
     */
    static void swapBuffers() {
        synchronized (bufferLock) {
            BufferedImage previousFront = frontBuffer;
            frontBuffer = buffer;
            buffer = previousFront;
            int[] presented = ((DataBufferInt) frontBuffer.getRaster().getDataBuffer()).getData();
            int[] drawing = ((DataBufferInt) buffer.getRaster().getDataBuffer()).getData();
            System.arraycopy(presented, 0, drawing, 0, presented.length);
        }
    }

    /**
     * Stores a palette color at one framebuffer pixel.
     *
     * @param x horizontal pixel coordinate, from {@code 0} through {@code 255}
     * @param y vertical pixel coordinate, from {@code 0} through {@code 255}
     * @param color seven-bit palette value encoded as {@code RRGGGBB}
     * @throws ArrayIndexOutOfBoundsException if either coordinate is outside the framebuffer
     */
    public static void pushMemory(byte x, byte y, byte color){
        synchronized (bufferLock) {
            buffer.setRGB(Byte.toUnsignedInt(x), Byte.toUnsignedInt(y), getRGB(color));
        }
    }

    /**
     * Clears the framebuffer to black.
     *
     * <p>Call {@link #display()} afterward to present the cleared image.</p>
     */
    public static void clear(){
        synchronized (bufferLock) {
            Graphics graphics = buffer.getGraphics();
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, buffer.getWidth(), buffer.getHeight());
            graphics.dispose();
        }
    }

    /**
     * Converts the virtual CPU's {@code RRGGGBB} palette value to a 24-bit RGB color.
     *
     * <p>The high bit is ignored so signed byte values map to the same seven-bit palette range.</p>
     *
     * @param color seven-bit palette value encoded as {@code RRGGGBB}
     * @return packed RGB value with eight bits per color channel
     */
    private static int getRGB(byte color) {
        int packedColor = color & 0x7F;

        int red   = ((packedColor >> 5) & 0b11)  * 255 / 3;
        int green = ((packedColor >> 2) & 0b111) * 255 / 7;
        int blue  = ( packedColor       & 0b11)  * 255 / 3;

        return (red << 16) | (green << 8) | blue;
    }

    /** Runs an action synchronously on Swing's event-dispatch thread. */
    private static void runOnEventDispatchThread(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }

        try {
            SwingUtilities.invokeAndWait(action);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize display", exception);
        }
    }
    /**
     * Returns the palette color stored at one framebuffer pixel.
     *
     * @param x horizontal pixel coordinate, from {@code 0} through {@code 255}
     * @param y vertical pixel coordinate, from {@code 0} through {@code 255}
     * @return seven-bit palette value encoded as {@code RRGGGBB}
     * @throws ArrayIndexOutOfBoundsException if either coordinate is outside the framebuffer
     */
    public static byte fetchMemory(byte x, byte y) {
        synchronized (bufferLock) {
            return rgbToByte(buffer.getRGB(Byte.toUnsignedInt(x), Byte.toUnsignedInt(y)));
        }
    }

    /** Converts a 24-bit RGB framebuffer value back to the virtual CPU palette format. */
    private static byte rgbToByte(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        // getRGB truncates channel expansion. Round to the nearest palette level
        // when reading it back so save/restore cannot progressively darken pixels.
        int packedRed = (red * 3 + 127) / 255;
        int packedGreen = (green * 7 + 127) / 255;
        int packedBlue = (blue * 3 + 127) / 255;

        return (byte) ((packedRed << 5) | (packedGreen << 2) | packedBlue);
    }
}
