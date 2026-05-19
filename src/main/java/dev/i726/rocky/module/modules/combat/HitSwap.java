package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.event.events.PostAttackListener;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;

public final class HitSwap extends Module implements AttackListener, PostAttackListener, TickListener {
    public enum SwapMode {
        CustomSlot,
        Axe,
        Mace
    }

    private final ModeSetting<SwapMode> mode = new ModeSetting<>(EncryptedString.of("Mode"), SwapMode.CustomSlot, SwapMode.class);
    private final NumberSetting targetSlot = new NumberSetting(EncryptedString.of("Target Slot"), 1, 9, 2, 1);
    private final BooleanSetting swapBack = new BooleanSetting(EncryptedString.of("Swap Back"), true);
    private final NumberSetting delay = new NumberSetting(EncryptedString.of("Back Delay"), 0, 10, 1, 1)
            .setDescription(EncryptedString.of("Ticks to wait before swapping back (0 = instant)"));

    private int originalSlot = -1;
    private int ticksLeft = -1;

    public HitSwap() {
        super(EncryptedString.of("Hit Swap"),
                EncryptedString.of("Swaps to weapon when hitting"),
                -1,
                CategoryManager.PVP);
        addSettings(mode, targetSlot, swapBack, delay);
    }

    @Override
    public void onEnable() {
        eventManager.add(AttackListener.class, this);
        eventManager.add(PostAttackListener.class, this);
        eventManager.add(TickListener.class, this);
        originalSlot = -1;
        ticksLeft = -1;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(AttackListener.class, this);
        eventManager.remove(PostAttackListener.class, this);
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (mc.player == null) return;
        if (originalSlot != -1) return; // Already in a swap sequence

        int slot = -1;

        switch (mode.getMode()) {
            case CustomSlot -> slot = targetSlot.getValueInt() - 1;
            case Axe -> slot = InventoryUtils.getAxeSlot();
            case Mace -> {
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getStack(i).getItem() instanceof MaceItem) {
                        slot = i;
                        break;
                    }
                }
            }
        }

        if (slot == -1 || slot == mc.player.getInventory().getSelectedSlot()) return;

        // Save original slot
        originalSlot = mc.player.getInventory().getSelectedSlot();

        // Switch to the target slot immediately
        InventoryUtils.setInvSlot(slot);

        // If swap back is disabled, we stay on the new slot
        if (!swapBack.getValue()) {
            originalSlot = -1;
        } else if (delay.getValueInt() > 0) {
            ticksLeft = delay.getValueInt();
        }
    }

    @Override
    public void onPostAttack(PostAttackEvent event) {
        if (mc.player == null) return;
        
        // If delay is 0, we swap back immediately at the end of the attack sequence
        if (originalSlot != -1 && swapBack.getValue() && delay.getValueInt() == 0) {
            InventoryUtils.setInvSlot(originalSlot);
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
