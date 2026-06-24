package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.item.Items;

public final class TotemOffhand extends Module implements TickListener {
    private final NumberSetting switchDelay = new NumberSetting(EncryptedString.of("Switch Delay"), 0, 5, 0, 1);
    private final NumberSetting equipDelay = new NumberSetting(EncryptedString.of("Equip Delay"), 1, 5, 1, 1);
    private final BooleanSetting switchBack = new BooleanSetting(EncryptedString.of("Switch Back"), false);

    private int switchClock, equipClock, switchBackClock;
    private int previousSlot = -1;
    boolean sent, active = false;

    public TotemOffhand() {
        super(EncryptedString.of("Totem Swap"),
                EncryptedString.of("Swaps totem to offhand"), -1, CategoryManager.INVENTORY);
        addSettings(switchDelay, equipDelay, switchBack);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        reset();

        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if(mc.screen != null)
            return;

        if(mc.player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING)
            active = true;

        if(active) {
            if (switchClock < switchDelay.getValueInt()) {
                switchClock++;
                return;
            }

            if(previousSlot == -1)
                previousSlot = mc.player.getInventory().getSelectedSlot();

            if (InventoryUtils.selectItemFromHotbar(Items.TOTEM_OF_UNDYING)) {
                if (equipClock < equipDelay.getValueInt()) {
                    equipClock++;
                    return;
                }

                if (!sent) {
                    mc.getConnection().getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                    sent = true;
                    return;
                }
            }

            if(switchBackClock < switchDelay.getValueInt()) {
                switchBackClock++;
            } else {
                if(switchBack.getValue())
                    InventoryUtils.setInvSlot(previousSlot);

                reset();
            }
        }
    }

    public void reset() {
        switchClock = 0;
        equipClock = 0;
        switchBackClock = 0;
        previousSlot = -1;

        sent = false;
        active = false;
    }
}