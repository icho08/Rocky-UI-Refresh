package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;

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
        if (mc.player == null || mc.level == null) return false;
        if (mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) return false;

        if (onPop.getValue() && mc.player.getHealth() < lastHealth - 5) return true;
        if (mc.player.getHealth() <= healthThreshold.getValue()) return true;

        if (checkPlayers.getValue()) {
            boolean hasNearby = mc.level.players().stream()
                    .anyMatch(p -> p != mc.player && !p.isDeadOrDying()
                            && mc.player.distanceTo(p) <= playerRange.getValue());
            if (!hasNearby) return false;
        }

        if (predictDamage.getValue()) {
            for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class,
                    mc.player.getBoundingBox().inflate(6), e -> true)) {
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
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO, Direction.DOWN));
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
            if (mc.player.getInventory().getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
                // Find an empty hotbar slot to QUICK_MOVE into
                for (int h = 0; h < 9; h++) {
                    if (mc.player.getInventory().getItem(h).isEmpty()) {
                        mc.gameMode.handleInventoryMouseClick(
                                mc.player.inventoryMenu.containerId,
                                i, h, net.minecraft.world.inventory.ContainerInput.SWAP, mc.player);
                        prevSlot = mc.player.getInventory().getSelectedSlot();
                        InventoryUtils.setInvSlot(h);
                        swapping = true;
                        equipClock = 0;
                        return;
                    }
                }
                // No empty hotbar slot — QUICK_MOVE straight to offhand
                int handlerSlot = i < 9 ? i + 36 : i;
                mc.gameMode.handleInventoryMouseClick(
                        mc.player.inventoryMenu.containerId,
                        handlerSlot, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player);
                return;
            }
        }
    }
}
