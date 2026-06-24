package dev.i726.rocky;

import java.lang.reflect.Method;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import java.lang.reflect.Field;

public class Test {
    public static void main() {
        for (Method m : ClientIntentionPacket.class.getDeclaredMethods()) {
            System.out.println("Method: " + m.getName() + " returns " + m.getReturnType().getName());
        }
        for (Field f : ClientIntentionPacket.class.getDeclaredFields()) {
            System.out.println("Field: " + f.getName() + " type " + f.getType().getName());
        }
    }
}
