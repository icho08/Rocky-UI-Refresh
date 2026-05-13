package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.mixin.MinecraftClientAccessor;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.KeybindSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public final class AutoFireball extends Module implements TickListener {
    private final KeybindSetting fireKey = new KeybindSetting(EncryptedString.of("Fire Key"), GLFW.GLFW_KEY_F, false);
    
    private boolean wasPressed = false;

    public AutoFireball() {
        super(EncryptedString.of("Auto Fireball"),
                EncryptedString.of("Automatically throws fireballs"),
                -1,
                CategoryManager.CRYSTAL);

        addSettings(fireKey);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        wasPressed = false;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null)
            return;

        boolean keyPressed = GLFW.glfwGetKey(mc.getWindow().getHandle(), fireKey.getKey()) == GLFW.GLFW_PRESS;
        
        if (keyPressed && !wasPressed) {
            throwFireball();
        }
        
        wasPressed = keyPressed;
    }

    private void throwFireball() {
        // Check if already holding fire charge
        if (mc.player.getMainHandStack().getItem() == Items.FIRE_CHARGE) {
            ((MinecraftClientAccessor) mc).invokeDoItemUse();
            return;
        }
        if (mc.player.getOffHandStack().getItem() == Items.FIRE_CHARGE) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return;
        }
        
        // Find fire charge in inventory and switch to it
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.FIRE_CHARGE) {
                mc.player.getInventory().setSelectedSlot(i);
                ((MinecraftClientAccessor) mc).invokeDoItemUse();
                return;
            }
        }
    }
}
