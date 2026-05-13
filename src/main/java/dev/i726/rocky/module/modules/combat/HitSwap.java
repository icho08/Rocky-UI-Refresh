package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;

public final class HitSwap extends Module implements AttackListener, TickListener {
    private final NumberSetting targetSlot = new NumberSetting(EncryptedString.of("Target Slot"), 1, 9, 2, 1);
    private final BooleanSetting autoMace = new BooleanSetting(EncryptedString.of("Auto Mace"), true)
            .setDescription(EncryptedString.of("Automatically finds a Mace and swaps to it"));
    private final NumberSetting delay = new NumberSetting(EncryptedString.of("Back Delay"), 0, 10, 1, 1)
            .setDescription(EncryptedString.of("Ticks to wait before swapping back"));
    private final BooleanSetting swapBack = new BooleanSetting(EncryptedString.of("Swap Back"), true);

    private int originalSlot = -1;
    private int ticksLeft = -1;

    public HitSwap() {
        super(EncryptedString.of("Hit Swap"),
                EncryptedString.of("Swaps to weapon when hitting"),
                -1,
                CategoryManager.PVP);
        addSettings(targetSlot, autoMace, delay, swapBack);
    }

    @Override
    public void onEnable() {
        eventManager.add(AttackListener.class, this);
        eventManager.add(TickListener.class, this);
        originalSlot = -1;
        ticksLeft = -1;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(AttackListener.class, this);
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (mc.player == null) return;
        if (originalSlot != -1) return; // Already swapping

        int slot = -1;
        if (autoMace.getValue()) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() instanceof MaceItem) {
                    slot = i;
                    break;
                }
            }
        }

        if (slot == -1) {
            slot = targetSlot.getValueInt() - 1;
        }

        if (slot == mc.player.getInventory().getSelectedSlot()) return;

        originalSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.setInvSlot(slot);
        
        if (swapBack.getValue()) {
            ticksLeft = delay.getValueInt();
        } else {
            // If not swapping back, we stay on the new slot
            originalSlot = -1;
        }
    }

    @Override
    public void onTick() {
        if (originalSlot != -1 && ticksLeft != -1) {
            if (ticksLeft <= 0) {
                InventoryUtils.setInvSlot(originalSlot);
                originalSlot = -1;
                ticksLeft = -1;
            } else {
                ticksLeft--;
            }
        }
    }
}
