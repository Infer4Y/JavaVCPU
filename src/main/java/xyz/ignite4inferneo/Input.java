/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

import module java.desktop;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.BitSet;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Input {
    public static final byte KEYBOARD_EVENT = 0x00;
    public static final int KEYBOARD_STATE_START = 0x01;
    public static final int KEYBOARD_STATE_END = 0x20;

    private static final BitSet keyState = new BitSet(256);
    /** Make/break keyboard events consumed by the CPU from input port {@code 0}. */
    private static final ConcurrentLinkedQueue<Byte> keyEvents = new ConcurrentLinkedQueue<>();
    private static volatile byte mouseX;
    private static volatile byte mouseY;
    private static volatile byte mouseButtons;
    private static int mouseWheel;

    private Input() {
    }

    public static void initialize() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(Input::handleKeyEvent);
    }

    private static boolean handleKeyEvent(KeyEvent event) {
        int scanCode = toVirtualScanCode(event);

        if (scanCode < 0 || scanCode > 255) {
            return false;
        }

        synchronized (Input.class) {
            if (event.getID() == KeyEvent.KEY_PRESSED) {
                if (!keyState.get(scanCode)) {
                    keyState.set(scanCode);
                    keyEvents.offer((byte) scanCode); // make event
                }
            } else if (event.getID() == KeyEvent.KEY_RELEASED) {
                keyState.clear(scanCode);
                keyEvents.offer((byte) (scanCode | 0x80)); // break event
            }
        }

        return false; // Swing still receives the key event
    }

    public static byte read(byte port) {
        return switch (Byte.toUnsignedInt(port)) {
            case 0x00 -> readKeyboardEvent();

            case 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
                 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
                 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20 ->
                    readKeyBank((Byte.toUnsignedInt(port) - 0x01) * 8);

            case 0x30 -> mouseX;
            case 0x31 -> mouseY;
            case 0x32 -> mouseButtons;
            case 0x33 -> readMouseWheel();

            default -> 0;
        };
    }

    private static byte readKeyBank(int firstScanCode) {
        synchronized (Input.class) {
            int result = 0;

            for (int bit = 0; bit < 8; bit++) {
                if (keyState.get(firstScanCode + bit)) {
                    result |= 1 << bit;
                }
            }

            return (byte) result;
        }
    }

    private static int toVirtualScanCode(KeyEvent event) {
        return event.getKeyCode();
    }

    public static void installMouse(JComponent panel) {
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                updateMousePosition(event, panel);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                updateMousePosition(event, panel);
            }
        });

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                updateMousePosition(event, panel);
                mouseButtons |= buttonMask(event.getButton());
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                updateMousePosition(event, panel);
                mouseButtons &= (byte) ~buttonMask(event.getButton());
            }
        });

        panel.addMouseWheelListener(event -> {
            synchronized (Input.class) {
                mouseWheel += event.getWheelRotation();
            }
        });
    }

    private static void updateMousePosition(MouseEvent event, JComponent panel) {
        int x = event.getX() * Monitor.FRAMEBUFFER_SIZE / Math.max(1, panel.getWidth());
        int y = event.getY() * Monitor.FRAMEBUFFER_SIZE / Math.max(1, panel.getHeight());

        mouseX = (byte) Math.clamp(x, 0, Monitor.FRAMEBUFFER_SIZE - 1);
        mouseY = (byte) Math.clamp(y, 0, Monitor.FRAMEBUFFER_SIZE - 1);
    }

    private static byte buttonMask(int button) {
        return switch (button) {
            case MouseEvent.BUTTON1 -> 0b001;
            case MouseEvent.BUTTON2 -> 0b010;
            case MouseEvent.BUTTON3 -> 0b100;
            default -> 0;
        };
    }


    private static byte readKeyboardEvent() {
        Byte event = keyEvents.poll();
        return event == null ? 0 : event;
    }

    private static byte readMouseWheel() {
        synchronized (Input.class) {
            int movement = mouseWheel;
            mouseWheel = 0;
            return (byte) Math.clamp(movement, -127, 127);
        }
    }
}
