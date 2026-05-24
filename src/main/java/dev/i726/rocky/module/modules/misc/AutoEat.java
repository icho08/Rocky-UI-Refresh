package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class AutoEat extends Module implements TickListener {

    private final NumberSetting hungerThreshold = new NumberSetting(
            EncryptedString.of("Hunger Threshold"), 1, 20, 16, 1)
            .setDescription(EncryptedString.of("Start eating when food level drops to or below this value"));

    private final BooleanSetting preferBestFood = new BooleanSetting(
            EncryptedString.of("Prefer Best Food"), true)
            .setDescription(EncryptedString.of("Pick the food with highest nutrition+saturation in hotbar"));

    private final BooleanSetting stopInCombat = new BooleanSetting(
            EncryptedString.of("Stop In Combat"), false)
            .setDescription(EncryptedString.of("Pause eating for 4 seconds after taking damage"));

    private int combatCooldown = 0;
    private float lastHealth = 20f;
    private boolean wasEating = false;

    public AutoEat() {
        super(EncryptedString.of("Auto Eat"),
                EncryptedString.of("Automatically eats food when hungry"),
                -1, CategoryManager.AUTOMATION);
        addSettings(hungerThreshold, preferBestFood, stopInCombat);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        combatCooldown = 0;
        wasEating = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        releaseKey();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) { releaseKey(); return; }

        // Damage detection for combat cooldown
        float health = mc.player.getHealth();
        if (stopInCombat.getValue()) {
            if (health < lastHealth) combatCooldown = 80; // 4 seconds
            if (combatCooldown > 0) combatCooldown--;
        }
        lastHealth = health;

        // Determine whether we actually need to eat
        int foodLevel = mc.player.getHungerManager().getFoodLevel();
        boolean needsFood = foodLevel <= hungerThreshold.getValueInt();

        if (!needsFood || (stopInCombat.getValue() && combatCooldown > 0)) {
            releaseKey();
            return;
        }

        // Don't interrupt if we're already using an item (like attacking or holding a sword)
        // only switch if we are NOT holding down the attack key or another important key
        if (mc.options.attackKey.isPressed() || mc.player.isUsingItem()) {
            return;
        }

        int bestSlot = findBestFoodSlot();
        if (bestSlot == -1) { releaseKey(); return; }

        // Switch to food slot if needed
        if (mc.player.getInventory().getSelectedSlot() != bestSlot) {
            mc.player.getInventory().setSelectedSlot(bestSlot);
            return;
        }

        // Begin eating
        mc.options.useKey.setPressed(true);
        wasEating = true;
    }

    private void releaseKey() {
        if (wasEating) {
            mc.options.useKey.setPressed(false);
            wasEating = false;
        }
    }

    private int findBestFoodSlot() {
        int bestSlot  = -1;
        float bestScore = -1f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            var food = stack.get(DataComponentTypes.FOOD);
            if (food == null) continue;

            float score = preferBestFood.getValue()
                    ? food.nutrition() + food.saturation()
                    : 1f; // any food is fine — just pick first

            if (score > bestScore) {
                bestScore = score;
                bestSlot  = i;
            }
        }
        return bestSlot;
    }
}
