package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.SwordItem;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public final class ChestStealer extends Module implements TickListener {

    private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0, 500, 80, 5)
            .setDescription(EncryptedString.of("Milliseconds between stealing each item"));
    private final BooleanSetting closeWhenDone = new BooleanSetting(EncryptedString.of("Close When Done"), true)
            .setDescription(EncryptedString.of("Closes the chest after all items are taken"));
    private final BooleanSetting ignoreTools = new BooleanSetting(EncryptedString.of("Ignore Tools"), false)
            .setDescription(EncryptedString.of("Skip tools and armor pieces"));
    private final BooleanSetting stackFirst = new BooleanSetting(EncryptedString.of("Stack First"), true)
            .setDescription(EncryptedString.of("Prioritise items that stack with existing inventory items"));

    private final TimerUtils timer = new TimerUtils();

    public ChestStealer() {
        super(EncryptedString.of("Chest Stealer"),
                EncryptedString.of("Automatically steals items from open chests"),
                -1, CategoryManager.INVENTORY);
        addSettings(delay, closeWhenDone, ignoreTools, stackFirst);
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
        if (!(mc.currentScreen instanceof GenericContainerScreen)) return;
        if (!timer.hasReached(delay.getValue())) return;

        GenericContainerScreenHandler handler = (GenericContainerScreenHandler) mc.player.currentScreenHandler;
        int containerSize = handler.getRows() * 9;

        // First pass: items that already stack in the inventory
        if (stackFirst.getValue()) {
            for (int i = 0; i < containerSize; i++) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (stack.isEmpty()) continue;
                if (ignoreTools.getValue() && isToolOrArmor(stack)) continue;
                if (hasMatchingStack(stack)) {
                    clickSlot(handler, i);
                    return;
                }
            }
        }

        // Second pass: take everything else
        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (ignoreTools.getValue() && isToolOrArmor(stack)) continue;

            clickSlot(handler, i);
            return;
        }

        // All items taken
        if (closeWhenDone.getValue()) {
            mc.player.closeHandledScreen();
        }
    }

    private void clickSlot(GenericContainerScreenHandler handler, int slot) {
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
        timer.reset();
    }

    private boolean hasMatchingStack(ItemStack target) {
        for (int i = 0; i < 36; i++) {
            ItemStack inv = mc.player.getInventory().getStack(i);
            if (!inv.isEmpty() && ItemStack.areItemsEqual(inv, target) && inv.getCount() < inv.getMaxCount())
                return true;
        }
        return false;
    }

    private boolean isToolOrArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem
                || stack.getItem() instanceof MiningToolItem
                || stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem;
    }
}
