package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.*;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class AutoDoubleHand extends Module implements HudListener {
    private final NumberSetting healthThreshold = new NumberSetting(EncryptedString.of("Health"), 1, 20, 8, 1)
            .setDescription(EncryptedString.of("Health to switch to totem"));
    private final NumberSetting playerRange = new NumberSetting(EncryptedString.of("Player Range"), 1, 15, 8, 1)
            .setDescription(EncryptedString.of("Range to check for players"));
    private final BooleanSetting onPop = new BooleanSetting(EncryptedString.of("On Pop"), true)
            .setDescription(EncryptedString.of("Switch to totem when you pop"));
    private final BooleanSetting predictDamage = new BooleanSetting(EncryptedString.of("Predict Damage"), true)
            .setDescription(EncryptedString.of("Predict crystal damage"));
    private final BooleanSetting checkPlayers = new BooleanSetting(EncryptedString.of("Check Players"), true)
            .setDescription(EncryptedString.of("Only activate when players nearby"));

    // Separate equip-delay counter so we don't send SWAP in the same tick as the slot change
    private float lastHealth = 20f;
    private boolean swapping = false;
    private int equipClock = 0;
    private int prevSlot = -1;

    public AutoDoubleHand() {
        super(EncryptedString.of("Double Hand"),
                EncryptedString.of("Manages both hands automatically"),
                -1,
                CategoryManager.INVENTORY);
        addSettings(healthThreshold, playerRange, onPop, predictDamage, checkPlayers);
    }

    @Override
    public void onEnable() {
        eventManager.add(HudListener.class, this);
        swapping = false;
        equipClock = 0;
        prevSlot = -1;
        lastHealth = mc.player != null ? mc.player.getHealth() : 20f;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(HudListener.class, this);
        swapping = false;
        super.onDisable();
    }

    private boolean shouldUseTotem() {
        if (mc.player == null || mc.world == null) return false;
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return false;

        if (onPop.getValue() && mc.player.getHealth() < lastHealth - 5) return true;
        if (mc.player.getHealth() <= healthThreshold.getValue()) return true;

        if (checkPlayers.getValue()) {
            boolean hasNearby = mc.world.getPlayers().stream()
                    .anyMatch(p -> p != mc.player && !p.isDead()
                            && mc.player.distanceTo(p) <= playerRange.getValue());
            if (!hasNearby) return false;
        }

        if (predictDamage.getValue()) {
            for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(EndCrystalEntity.class,
                    mc.player.getBoundingBox().expand(6), e -> true)) {
                double dist = mc.player.distanceTo(crystal);
                if (dist <= 6) {
                    double est = Math.max(0, 12 - dist * 2);
                    if (mc.player.getHealth() - est <= 4) return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onRenderHud(HudEvent event) {
        if (mc.player == null) return;

        lastHealth = mc.player.getHealth();

        if (!shouldUseTotem()) {
            swapping = false;
            equipClock = 0;
            return;
        }

        if (swapping) {
            // Wait 1 tick after selecting slot before sending SWAP packet
            if (equipClock < 1) {
                equipClock++;
                return;
            }
            // Send the actual offhand swap packet
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ORIGIN, Direction.DOWN));
            // Restore previous slot
            if (prevSlot != -1) InventoryUtils.setInvSlot(prevSlot);
            swapping = false;
            equipClock = 0;
            prevSlot = -1;
            return;
        }

        // Try hotbar first — fastest path
        if (InventoryUtils.selectItemFromHotbar(Items.TOTEM_OF_UNDYING)) {
            prevSlot = mc.player.getInventory().getSelectedSlot();
            swapping = true;
            equipClock = 0;
            return;
        }

        // Totem not in hotbar — use QUICK_MOVE to push one from inventory into an available slot
        // Find totem in inventory (slots 9-35)
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                // Find an empty hotbar slot to QUICK_MOVE into
                for (int h = 0; h < 9; h++) {
                    if (mc.player.getInventory().getStack(h).isEmpty()) {
                        mc.interactionManager.clickSlot(
                                mc.player.playerScreenHandler.syncId,
                                i, h, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                        prevSlot = mc.player.getInventory().getSelectedSlot();
                        InventoryUtils.setInvSlot(h);
                        swapping = true;
                        equipClock = 0;
                        return;
                    }
                }
                // No empty hotbar slot — QUICK_MOVE straight to offhand
                int handlerSlot = i < 9 ? i + 36 : i;
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        handlerSlot, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, mc.player);
                return;
            }
        }
    }
}
