package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class Step extends Module implements TickListener {

    private static final Identifier STEP_ID = Identifier.fromNamespaceAndPath("rocky", "step_height");

    private final NumberSetting height = new NumberSetting(EncryptedString.of("Height"), 0.6, 2.5, 1.0, 0.1)
            .setDescription(EncryptedString.of("Max block height the player can step over (vanilla = 0.6)"));

    private boolean modifierApplied = false;

    public Step() {
        super(EncryptedString.of("Step"),
                EncryptedString.of("Automatically steps up blocks without jumping"),
                -1, CategoryManager.MOVEMENT);
        addSettings(height);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        removeModifier();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        var attr = mc.player.getAttribute(Attributes.STEP_HEIGHT);
        if (attr == null) return;

        double target = height.getValue();
        double base   = attr.getBaseValue();
        double needed = target - base;

        // Re-apply if the modifier got wiped (e.g. dimension change / respawn)
        boolean hasModifier = attr.getModifier(STEP_ID) != null;

        if (!hasModifier && needed > 0) {
            attr.addTransientModifier(new AttributeModifier(
                    STEP_ID, needed, AttributeModifier.Operation.ADD_VALUE));
            modifierApplied = true;
        } else if (hasModifier) {
            // Refresh if the height setting changed
            double current = attr.getModifier(STEP_ID).amount();
            if (Math.abs(current - needed) > 0.001) {
                attr.removeModifier(STEP_ID);
                if (needed > 0) {
                    attr.addTransientModifier(new AttributeModifier(
                            STEP_ID, needed, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }
    }

    private void removeModifier() {
        if (mc != null && mc.player != null) {
            var attr = mc.player.getAttribute(Attributes.STEP_HEIGHT);
            if (attr != null) attr.removeModifier(STEP_ID);
        }
        modifierApplied = false;
    }
}
