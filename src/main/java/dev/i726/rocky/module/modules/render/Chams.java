package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;

import java.awt.Color;

/**
 * Chams — player glow outline visible through walls.
 *
 * Works via EntityMixin.isCurrentlyGlowing() returning true for all players
 * while this module is enabled. Minecraft's built-in outline renderer draws
 * a coloured silhouette of the actual player model, including through walls.
 *
 * No 2-D box overlay — the engine handles everything.
 */
public final class Chams extends Module {

    public Chams() {
        super(EncryptedString.of("Chams"),
                EncryptedString.of("Glow outline on players through walls"),
                -1,
                CategoryManager.ESP);
    }

    /** Used by EntityMixin and any other module that wants the tint colour. */
    public Color getColor() {
        return Color.RED;
    }
}
