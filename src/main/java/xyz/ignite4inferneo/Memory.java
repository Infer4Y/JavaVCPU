package xyz.ignite4inferneo;

import java.util.Arrays;
import java.util.HashMap;

public class Memory {
    private static final HashMap<Byte, byte[]> PAGED_MEMORY = HashMap.newHashMap(127);

    public static void initializeMemoryPages(){
        PAGED_MEMORY.forEach((_, memory) -> {
            memory = new byte[127];
            Arrays.fill(memory, (byte) 0);
        });
    }

    public static byte[] getMemoryPage(byte page){
        return PAGED_MEMORY.get(page);
    }
}
