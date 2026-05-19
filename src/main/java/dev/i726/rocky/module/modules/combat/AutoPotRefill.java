package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Silently moves potions from the main inventory into the hotbar
 * without ever opening the inventory screen.
 *
 * Uses PlayerScreenHandler (syncId = 0) which is always active.
 * The SWAP slot action is equivalent to pressing a hotkey number while
 * hovering an item — indistinguishable from normal play on the server.
 *
 * PlayerScreenHandler slot layout (syncId 0):
 *   0       → crafting result
 *   1–4     → crafting input
 *   5–8     → armour slots
 *   9–35    → main inventory  (same as player.inventory.main[9..35])
 *   36–44   → hotbar          (player.inventory.main[0..8])
 *   45      → off-hand
 *
 * SlotActionType.SWAP(slot, button):
 *   slot   = handler slot of the source item (9–35)
 *   button = hotbar index to swap into (0–8)
 */
public final class AutoPotRefill extends Module implements TickListener {

    public enum PotionType { Health, Strength, Speed, FireResistance }

    private final ModeSetting<PotionType> potionType = new ModeSetting<>(
            EncryptedString.of("Potion Type"), PotionType.Health, PotionType.class);

    private final NumberSetting minPotions = new NumberSetting(
            EncryptedString.of("Min Potions"), 1, 9, 3, 1)
            .setDescription(EncryptedString.of("Refill hotbar when potions drop below this count"));

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 50, 2000, 250, 25)
            .setDescription(EncryptedString.of("Milliseconds between each individual refill swap"));

    private final NumberSetting delayJitter = new NumberSetting(
            EncryptedString.of("Delay Jitter"), 0, 200, 50, 5)
            .setDescription(EncryptedString.of("Random extra ms added to each delay (humanisation)"));

    private final TimerUtils moveTimer = new TimerUtils();
    private int nextDelay = 250;

    public AutoPotRefill() {
        super(EncryptedString.of("Pot Refill"),
                EncryptedString.of("Silently moves potions from inventory to hotbar"),
                -1, CategoryManager.INVENTORY);
        addSettings(potionType, minPotions, delay, delayJitter);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        moveTimer.reset();
        rollDelay();
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

        // Nothing to do if hotbar is already stocked or inventory is empty
        if (countPotionsInHotbar() >= (int) minPotions.getValue()) return;
        int invSlot = findPotionInInventory();
        if (invSlot == -1) return;

        // Respect the humanised delay between swaps
        if (!moveTimer.delay(nextDelay)) return;
        moveTimer.reset();
        rollDelay();

        int hotbarSlot = findTargetHotbarSlot();
        if (hotbarSlot == -1) return;

        // Silent swap using the always-open player inventory handler (syncId 0).
        // No screen is opened; no inventory-open packet is sent.
        mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,   // always 0
                invSlot,                                 // handler slot 9–35
                hotbarSlot,                              // hotbar key 0–8
                SlotActionType.SWAP,
                mc.player
        );
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

    /** Randomises the next swap delay for humanisation. */
    private void rollDelay() {
        int jitter = (int)(Math.random() * delayJitter.getValue());
        nextDelay = (int) delay.getValue() + jitter;
    }
}
