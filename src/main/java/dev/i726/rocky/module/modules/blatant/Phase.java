package dev.i726.rocky.module.modules.blatant;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;

public final class Phase extends Module implements TickListener {

    public Phase() {
        super(EncryptedString.of("Phase"),
                EncryptedString.of("Walk through blocks using noClip — will desync or kick on almost every server"),
                -1, CategoryManager.BLATANT);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        if (mc.player != null) {
            mc.player.noPhysics = true;
            mc.player.fallDistance = 0f;
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        if (mc.player != null) {
            mc.player.noPhysics = false;
        }
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        // noClip lets the player pass through blocks using normal WASD movement.
        // No velocity override — that's what caused it to act like a flight module.
        mc.player.noPhysics = true;
        mc.player.fallDistance = 0f;
    }
}
