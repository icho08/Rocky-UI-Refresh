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

        EquipmentSlot[]  slots  = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        BooleanSetting[] guards = { helmet, chestplate, leggings, boots };

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
             * betterInvIdx comes from the PlayerInventory (0-8 hotbar, 9-35 main).
             * We QUICK_MOVE it so Minecraft places it in the correct armor slot.
             */
            int handlerSlot = betterInvIdx < 9
                    ? betterInvIdx + 36
                    : betterInvIdx;

            mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    handlerSlot, 0, SlotActionType.QUICK_MOVE, mc.player);

            timer.reset();
            return;
        }
    }

    /**
     * Returns the inventory index (0-35) of a piece better than {@code current}
     * for the given equipment slot, or -1 if none found.
     * Uses the EQUIPPABLE component (1.21+) to determine the correct slot.
     */
    private int findBetter(EquipmentSlot slot, ItemStack current) {
        int currentProt = protection(current);
        int bestSlot    = -1;
        int bestProt    = currentProt;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable == null || equippable.slot() != slot) continue;

            int prot = protection(stack);
            if (prot > bestProt) {
                bestProt = prot;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /**
     * Returns the total armor (protection) value via ATTRIBUTE_MODIFIERS component.
     */
    private int protection(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        var modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            int total = 0;
            for (var entry : modifiers.modifiers()) {
                if (entry.attribute().value() == EntityAttributes.ARMOR.value()) {
                    EntityAttributeModifier mod = entry.modifier();
                    total += (int) mod.value();
                }
            }
            if (total > 0) return total;
        }

        return 0;
    }
}
