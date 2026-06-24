package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.gui.screens.DeathScreen;

public final class AutoRespawn extends Module implements TickListener {

        public AutoRespawn() {
                super(EncryptedString.of("Auto Respawn"),
                EncryptedString.of("Respawns instantly on death"),
                                -1,
                                CategoryManager.AUTOMATION);
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
                if (mc.screen instanceof DeathScreen) {
                        mc.player.respawn();
                        mc.setScreen(null);
                }
        }
}
