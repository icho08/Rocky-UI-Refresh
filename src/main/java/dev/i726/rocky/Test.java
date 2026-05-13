package dev.i726.rocky;

import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class Test {
    public static void main() {
        for (Method m : HandshakeC2SPacket.class.getDeclaredMethods()) {
            System.out.println("Method: " + m.getName() + " returns " + m.getReturnType().getName());
        }
        for (Field f : HandshakeC2SPacket.class.getDeclaredFields()) {
            System.out.println("Field: " + f.getName() + " type " + f.getType().getName());
        }
    }
}
