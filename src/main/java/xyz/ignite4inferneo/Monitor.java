/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

import javax.swing.*;
import module java.desktop;

/**
 * Provides the virtual CPU's 128-by-128-pixel display.
 *
 * <p>Graphics instructions update an off-screen framebuffer through {@link #pushMemory(byte, byte,
 * byte)}. Call {@link #display()} to schedule the completed framebuffer for painting in the Swing
 * window. A color
 * operand uses seven bits in {@code RRGGGBB} order: red and blue each have four intensity levels,
 * and green has eight.</p>
 */
public class Monitor {
    /** The Swing window used to present the framebuffer. */
    private static JFrame display;

    /** The 128-by-128 framebuffer updated by the virtual CPU. */
    private static final BufferedImage buffer = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);

    /** Guards framebuffer updates while Swing paints the image. */
    private static final Object bufferLock = new Object();

    /** Component responsible for rendering the framebuffer. */
    private static final JPanel displayPanel = new JPanel() {
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            ((Graphics2D) graphics).setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            synchronized (bufferLock) {
                graphics.drawImage(buffer, 0, 0, getWidth(), getHeight(), this);
            }
        }
    };

    /**
     * Creates and shows the display window.
     *
     * <p>This method must be called before {@link #display()} presents the framebuffer.</p>
     */
    public static void initialize(){
        runOnEventDispatchThread(() -> {
            if (display != null) {
                return;
            }

            display = new JFrame("JavaVCPU display out");
            displayPanel.setPreferredSize(new Dimension(256, 256));
            display.setContentPane(displayPanel);
            display.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            display.pack();
            display.setVisible(true);
        });
    }

    /**
     * Schedules the current framebuffer to be drawn in the display window.
     *
     * <p>Framebuffer changes are not visible until this method is called.</p>
     */
    public static void display(){
        if (display == null) {
            throw new IllegalStateException("Display has not been initialized");
        }
        displayPanel.repaint();
    }

    /**
     * Stores a palette color at one framebuffer pixel.
     *
     * @param x horizontal pixel coordinate, from {@code 0} through {@code 127}
     * @param y vertical pixel coordinate, from {@code 0} through {@code 127}
     * @param color seven-bit palette value encoded as {@code RRGGGBB}
     * @throws ArrayIndexOutOfBoundsException if either coordinate is outside the framebuffer
     */
    public static void pushMemory(byte x, byte y, byte color){
        synchronized (bufferLock) {
            buffer.setRGB(x, y, getRGB(color));
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


}
