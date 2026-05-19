package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public final class AutoArmor extends Module implements TickListener {

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 100, 3000, 500, 50)
            .setDescription(EncryptedString.of("Milliseconds between each armor equip action"));

    private final BooleanSetting helmet     = new BooleanSetting(EncryptedString.of("Helmet"), true);
    private final BooleanSetting chestplate = new BooleanSetting(EncryptedString.of("Chestplate"), true);
    private final BooleanSetting leggings   = new BooleanSetting(EncryptedString.of("Leggings"), true);
    private final BooleanSetting boots      = new BooleanSetting(EncryptedString.of("Boots"), true);

    private final TimerUtils timer = new TimerUtils();

    public AutoArmor() {
        super(EncryptedString.of("Auto Armor"),
                EncryptedString.of("Automatically equips the best armor from your inventory"),
                -1, CategoryManager.INVENTORY);
        addSettings(delay, helmet, chestplate, leggings, boots);
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
        if (mc.player == null || mc.currentScreen != null) return;
        if (!timer.hasReached(delay.getValue())) return;

        EquipmentSlot[]   slots   = { EquipmentSlot.HEAD,  EquipmentSlot.CHEST, EquipmentSlot.LEGS,  EquipmentSlot.FEET };
        BooleanSetting[]  guards  = { helmet,               chestplate,           leggings,             boots };

        for (int s = 0; s < slots.length; s++) {
            if (!guards[s].getValue()) continue;

            EquipmentSlot slot    = slots[s];
            ItemStack     current = mc.player.getEquippedStack(slot);
            int betterInvIdx      = findBetter(slot, current);
            if (betterInvIdx == -1) continue;

            /*
             * PlayerScreenHandler slot mapping (no container open):
             *   0        = crafting output
             *   1-4      = crafting grid
             *   5 = HEAD, 6 = CHEST, 7 = LEGS, 8 = FEET   ← armor
             *   9-35     = main inventory
             *   36-44    = hotbar (inv index 0-8)
             *   45       = offhand
             *
             * Our betterInvIdx comes from the PlayerInventory (0-8 hotbar, 9-35 main).
             * We need to map it to the screen-handler slot number, then QUICK_MOVE it
             * so Minecraft automatically places it in the correct armor slot.
             */
            int handlerSlot = betterInvIdx < 9
                    ? betterInvIdx + 36   // hotbar
                    : betterInvIdx;       // main inventory (already matches)

            mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    handlerSlot, 0, SlotActionType.QUICK_MOVE, mc.player);

            timer.reset();
            return; // one piece per delay cycle
        }
    }

    /**
     * Returns the inventory index (0-35) of a piece better than {@code current}
     * for the given equipment slot, or -1 if none found.
     */
    private int findBetter(EquipmentSlot slot, ItemStack current) {
        int currentProt = protection(slot, current);
        int bestSlot    = -1;
        int bestProt    = currentProt;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            // Fast-path: ArmorItem knows its slot natively
            if (stack.getItem() instanceof ArmorItem armorItem) {
                if (armorItem.getSlotType() != slot) continue;
            } else {
                // Fallback: check EQUIPPABLE component
                var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
                if (equippable == null || equippable.slot() != slot) continue;
            }

            int prot = protection(slot, stack);
            if (prot > bestProt) {
                bestProt = prot;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /**
     * Returns the total armor (protection) value of the item stack.
     * Uses the ATTRIBUTE_MODIFIERS component first; falls back to ArmorItem tier value.
     */
    private int protection(EquipmentSlot slot, ItemStack stack) {
        if (stack.isEmpty()) return 0;

        // Primary: read ATTRIBUTE_MODIFIERS component (1.21 data-driven items)
        var modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            int total = 0;
            for (var entry : modifiers.modifiers()) {
                // Compare the underlying EntityAttribute singletons — safe in the registry system
                if (entry.attribute().value() == EntityAttributes.ARMOR.value()) {
                    EntityAttributeModifier mod = entry.modifier();
                    total += (int) mod.value();
                }
            }
            if (total > 0) return total;
        }

        // Fallback: ArmorItem exposes protection value directly
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getDefensePoints();
        }

        return 0;
    }
}
