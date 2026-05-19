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

/**
 * Refills potions from the main inventory to the hotbar.
 *
 * Two modes controlled by the "Silent" setting:
 *
 * Silent ON  — uses PlayerScreenHandler (syncId = 0, always active) so no
 *              inventory screen is ever opened.  The SWAP action is identical
 *              to pressing a number key while hovering an item — undetectable.
 *
 * Silent OFF — opens the inventory screen (original behaviour), performs the
 *              swap, then closes automatically when done.
 *
 * PlayerScreenHandler slot layout (syncId 0):
 *   0       → crafting result
 *   1–4     → crafting input
 *   5–8     → armour slots
 *   9–35    → main inventory  (matches player.inventory.main[9..35])
 *   36–44   → hotbar          (player.inventory.main[0..8])
 *   45      → off-hand
 */
public final class AutoPotRefill extends Module implements TickListener {

    public enum PotionType { Health, Strength, Speed, FireResistance }

    private final ModeSetting<PotionType> potionType = new ModeSetting<>(
            EncryptedString.of("Potion Type"), PotionType.Health, PotionType.class);

    private final NumberSetting minPotions = new NumberSetting(
            EncryptedString.of("Min Potions"), 1, 9, 3, 1)
            .setDescription(EncryptedString.of("Refill hotbar when potions drop below this count"));

    private final BooleanSetting silent = new BooleanSetting(
            EncryptedString.of("Silent"), true)
            .setDescription(EncryptedString.of("On: swap without opening inventory (anticheat-safe). Off: open inventory screen like a normal player"));

    private final BooleanSetting autoClose = new BooleanSetting(
            EncryptedString.of("Auto Close"), true)
            .setDescription(EncryptedString.of("Close inventory screen once the hotbar is full (only used when Silent is Off)"));

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 50, 2000, 250, 25)
            .setDescription(EncryptedString.of("Milliseconds between each individual refill swap"));

    private final NumberSetting delayJitter = new NumberSetting(
            EncryptedString.of("Delay Jitter"), 0, 200, 50, 5)
            .setDescription(EncryptedString.of("Random extra ms added to each delay (humanisation)"));

    private final TimerUtils moveTimer = new TimerUtils();
    private int nextDelay = 250;
    private boolean wasAutoOpened = false;

    public AutoPotRefill() {
        super(EncryptedString.of("Pot Refill"),
                EncryptedString.of("Refills potions from inventory to hotbar"),
                -1, CategoryManager.INVENTORY);
        addSettings(potionType, minPotions, silent, autoClose, delay, delayJitter);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        moveTimer.reset();
        rollDelay();
        wasAutoOpened = false;
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

    @Override
    public void onTick() {
        if (mc.player == null) return;

        boolean needsRefill = countPotionsInHotbar() < (int) minPotions.getValue()
                && findPotionInInventory() != -1;

        if (!needsRefill) {
            if (wasAutoOpened && autoClose.getValue()
                    && mc.currentScreen instanceof InventoryScreen) {
                mc.currentScreen.close();
                wasAutoOpened = false;
            }
            return;
        }

        if (!moveTimer.delay(nextDelay)) return;

        if (silent.getValue()) {
            // ── Silent mode: use PlayerScreenHandler (syncId 0), no screen needed ──
            int invSlot    = findPotionInInventory();
            int hotbarSlot = findTargetHotbarSlot();
            if (invSlot == -1 || hotbarSlot == -1) return;

            mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,  // always 0
                    invSlot,                                // handler slot 9–35
                    hotbarSlot,                             // hotbar key 0–8
                    SlotActionType.SWAP,
                    mc.player
            );
        } else {
            // ── Screen mode: open inventory if not already open ──
            if (!(mc.currentScreen instanceof InventoryScreen)) {
                mc.setScreen(new InventoryScreen(mc.player));
                wasAutoOpened = true;
                return;
            }

            InventoryScreen invScreen = (InventoryScreen) mc.currentScreen;
            int invSlot    = findPotionInInventory();
            int hotbarSlot = findTargetHotbarSlot();
            if (invSlot == -1 || hotbarSlot == -1) return;

            mc.interactionManager.clickSlot(
                    invScreen.getScreenHandler().syncId,
                    invSlot,
                    hotbarSlot,
                    SlotActionType.SWAP,
                    mc.player
            );
        }

        moveTimer.reset();
        rollDelay();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    /** Returns a main-inventory handler slot (9–35) containing a matching potion, or -1. */
    private int findPotionInInventory() {
        return switch (potionType.getMode()) {
            case Health         -> InventoryUtils.findPot(StatusEffects.INSTANT_HEALTH.value(), 1, 1);
            case Strength       -> InventoryUtils.findPot(StatusEffects.STRENGTH.value(), 1, 1);
            case Speed          -> InventoryUtils.findPot(StatusEffects.SPEED.value(), 1, 1);
            case FireResistance -> InventoryUtils.findPot(StatusEffects.FIRE_RESISTANCE.value(), 1, 1);
        };
    }

    /** Returns an empty hotbar slot index (0–8), or the first non-potion slot. */
    private int findTargetHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (!isPotionOfType(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    private void rollDelay() {
        int jitter = (int)(Math.random() * delayJitter.getValue());
        nextDelay  = (int) delay.getValue() + jitter;
    }
}
