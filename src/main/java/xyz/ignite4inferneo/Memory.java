/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

import java.util.Arrays;
import java.util.HashMap;

public class Memory {
    private static final byte[][] PAGED_MEMORY = new byte[128][128];

    public static void initializeMemoryPages(){
        for (byte[] page : PAGED_MEMORY) {
            Arrays.fill(page, (byte) 0);
        }
    }

    public static byte[] getMemoryPage(byte page){
        return PAGED_MEMORY[page];
    }
}
