package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.gui.screen.DeathScreen;

public final class AutoRespawn extends Module implements TickListener {

        public AutoRespawn() {
                super(EncryptedString.of("Auto Respawn"),
                EncryptedString.of("Respawns instantly on death"),
                                -1,
                                CategoryManager.PLAYER);
        }

        @Override
        public void onEnable() {
                eventManager.add(TickListener.class, this);
                super.onEnable();
        }

        @Override
        public void onDisable() {
                eventManager.remove(TickListener.class, this);
                super.onDisable();
        }

        @Override
        public void onTick() {
                if (mc.player == null) return;
                if (mc.currentScreen instanceof DeathScreen) {
                        mc.player.requestRespawn();
                        mc.setScreen(null);
                }
        }
}
