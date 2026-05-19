package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.screen.slot.SlotActionType;

public final class AutoPotRefill extends Module implements TickListener {

    public enum PotionType { Health, Strength, Speed, FireResistance }

    private final ModeSetting<PotionType> potionType = new ModeSetting<>(
            EncryptedString.of("Potion Type"), PotionType.Health, PotionType.class);

    private final NumberSetting minPotions = new NumberSetting(
            EncryptedString.of("Min Potions"), 1, 9, 3, 1)
            .setDescription(EncryptedString.of("Refill hotbar when potions drop below this count"));

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 50, 1000, 200, 50)
            .setDescription(EncryptedString.of("Milliseconds between each individual refill move"));

    private final BooleanSetting autoOpen = new BooleanSetting(
            EncryptedString.of("Auto Open"), true)
            .setDescription(EncryptedString.of("Automatically open inventory to refill (suppress screen render if Off)"));

    private final BooleanSetting autoClose = new BooleanSetting(
            EncryptedString.of("Auto Close"), true)
            .setDescription(EncryptedString.of("Close inventory once the hotbar is full"));

    private final TimerUtils moveTimer = new TimerUtils();
    private boolean wasAutoOpened = false;

    public AutoPotRefill() {
        super(EncryptedString.of("Pot Refill"),
                EncryptedString.of("Refills potions from inventory to hotbar"),
                -1, CategoryManager.INVENTORY);
        addSettings(potionType, minPotions, delay, autoOpen, autoClose);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        wasAutoOpened = false;
        moveTimer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        if (wasAutoOpened && mc.currentScreen instanceof InventoryScreen) {
            mc.currentScreen.close();
        }
        wasAutoOpened = false;
        super.onDisable();
    }

    private int countPotionsInHotbar() {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            if (isPotionOfType(mc.player.getInventory().getStack(i))) count++;
        }
        return count;
    }

    private boolean isPotionOfType(net.minecraft.item.ItemStack stack) {
        return switch (potionType.getMode()) {
            case Health         -> InventoryUtils.isThatSplash(StatusEffects.INSTANT_HEALTH.value(), 1, 1, stack);
            case Strength       -> InventoryUtils.isThatSplash(StatusEffects.STRENGTH.value(), 1, 1, stack);
            case Speed          -> InventoryUtils.isThatSplash(StatusEffects.SPEED.value(), 1, 1, stack);
            case FireResistance -> InventoryUtils.isThatSplash(StatusEffects.FIRE_RESISTANCE.value(), 1, 1, stack);
        };
    }

    /**
     * Returns the inventory slot index (9-35) where a matching potion was found,
     * or -1 if none available.
     */
    private int findPotionInInventory() {
        return switch (potionType.getMode()) {
            case Health         -> InventoryUtils.findPot(StatusEffects.INSTANT_HEALTH.value(), 1, 1);
            case Strength       -> InventoryUtils.findPot(StatusEffects.STRENGTH.value(), 1, 1);
            case Speed          -> InventoryUtils.findPot(StatusEffects.SPEED.value(), 1, 1);
            case FireResistance -> InventoryUtils.findPot(StatusEffects.FIRE_RESISTANCE.value(), 1, 1);
        };
    }

    /** Finds an empty hotbar slot (index 0-8) or one holding a non-useful item. */
    private int findTargetHotbarSlot() {
        // Prefer truly empty slots first
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        // Then any non-potion slot
        for (int i = 0; i < 9; i++) {
            if (!isPotionOfType(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        boolean needsRefill = countPotionsInHotbar() < minPotions.getValue()
                && findPotionInInventory() != -1;

        if (!needsRefill) {
            // Close if we auto-opened and refill is done
            if (wasAutoOpened && autoClose.getValue()
                    && mc.currentScreen instanceof InventoryScreen) {
                mc.currentScreen.close();
                wasAutoOpened = false;
            }
            return;
        }

        // Open inventory if needed
        if (autoOpen.getValue() && !(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
            wasAutoOpened = true;
            return;
        }

        if (!(mc.currentScreen instanceof InventoryScreen invScreen)) return;

        if (!moveTimer.delay((int) delay.getValue())) return;
        moveTimer.reset();

        int invSlot     = findPotionInInventory();   // inventory index 9-35
        int hotbarSlot  = findTargetHotbarSlot();    // hotbar index 0-8
        if (invSlot == -1 || hotbarSlot == -1) return;

        int syncId = invScreen.getScreenHandler().syncId;

        /*
         * In PlayerScreenHandler (inventory open, no container):
         *   Slots 9-35  → main inventory (matches inventory index directly)
         *   Slots 36-44 → hotbar (inventory index + 36)
         *
         * SlotActionType.SWAP swaps handler-slot `slot` with hotbar key `button` (0-8).
         * So: slot = invSlot (9-35 handler slot), button = hotbarSlot (0-8).
         */
        mc.interactionManager.clickSlot(
                syncId,
                invSlot,           // handler slot of the potion (9-35)
                hotbarSlot,        // hotbar key 0-8
                SlotActionType.SWAP,
                mc.player
        );
    }
}
