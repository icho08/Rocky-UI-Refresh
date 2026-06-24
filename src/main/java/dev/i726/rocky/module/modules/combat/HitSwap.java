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
import net.minecraft.world.item.MaceItem;

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

        int currentSlot = mc.player.getInventory().getSelectedSlot();

        // Safety recovery: if the player manually switched back to the original slot
        // while we thought we were in a swap sequence, clear the stale state so
        // future attacks are not permanently blocked by the guard below.
        if (originalSlot != -1 && currentSlot == originalSlot) {
            originalSlot = -1;
            ticksLeft = -1;
        }

        if (originalSlot != -1) {
            // Still in an active swap sequence (axe/mace is held).
            // Refresh the swap-back timer so rapid consecutive attacks don't cause
            // a premature swap-back mid-combo — the countdown resets on every hit.
            if (swapBack.getValue() && delay.getValueInt() > 0) {
                ticksLeft = delay.getValueInt();
            }
            return;
        }

        int slot = -1;

        switch (mode.getMode()) {
            case CustomSlot -> slot = targetSlot.getValueInt() - 1;
            case Axe -> {
                // In Axe mode, only swap if currently holding a sword — this is what
                // "sword+axe combo" means: sword for sustain, axe for shield-break.
                // If already holding the axe (or something else), skip.
                if (dev.i726.rocky.utils.WorldUtils.isSword(
                        mc.player.getInventory().getItem(currentSlot).getItem())) {
                    slot = InventoryUtils.getAxeSlot();
                }
            }
            case Mace -> {
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getItem(i).getItem() instanceof MaceItem) {
                        slot = i;
                        break;
                    }
                }
            }
        }

        if (slot == -1 || slot == currentSlot) return;

        originalSlot = currentSlot;
        InventoryUtils.setInvSlot(slot);

        if (!swapBack.getValue()) {
            originalSlot = -1;
        } else if (delay.getValueInt() > 0) {
            ticksLeft = delay.getValueInt();
        }
    }

    @Override
    public void onPostAttack(PostAttackEvent event) {
        if (mc.player == null) return;
        // Instant swap-back (delay == 0): return to original slot at the end of the
        // attack so the next hit re-evaluates whether to swap again.
        if (originalSlot != -1 && swapBack.getValue() && delay.getValueInt() == 0) {
            InventoryUtils.setInvSlot(originalSlot);
            originalSlot = -1;
        }
    }

    @Override
    public void onTick() {
        if (originalSlot != -1 && ticksLeft > 0) {
            ticksLeft--;
            if (ticksLeft == 0) {
                InventoryUtils.setInvSlot(originalSlot);
                originalSlot = -1;
                ticksLeft = -1;
            }
        }
    }
}
