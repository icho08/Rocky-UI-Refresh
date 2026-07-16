package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AutoArmor extends Module implements TickListener {

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 0, 5, 1, 1)
            .setDescription(EncryptedString.of("Tick delay between equips"));

    private final BooleanSetting helmet     = new BooleanSetting(EncryptedString.of("Helmet"), true);
    private final BooleanSetting chestplate = new BooleanSetting(EncryptedString.of("Chestplate"), true);
    private final BooleanSetting leggings   = new BooleanSetting(EncryptedString.of("Leggings"), true);
    private final BooleanSetting boots      = new BooleanSetting(EncryptedString.of("Boots"), true);

    private final TimerUtils timer = new TimerUtils();
    private int tickTimer;

    public AutoArmor() {
        super(EncryptedString.of("Auto Armor"),
                EncryptedString.of("Equips best armor from inventory"),
                -1, CategoryManager.INVENTORY);
        addSettings(delay, helmet, chestplate, leggings, boots);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        tickTimer = 0;
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

        if (tickTimer > 0) {
            tickTimer--;
            return;
        }

        EquipmentSlot[]  slots  = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        BooleanSetting[] guards = { helmet, chestplate, leggings, boots };

        for (int s = 0; s < slots.length; s++) {
            if (!guards[s].getValue()) continue;

            EquipmentSlot slot    = slots[s];
            ItemStack     current = mc.player.getItemBySlot(slot);
            int bestInvIdx        = findBest(slot, current);
            if (bestInvIdx == -1) continue;

            int invSlot    = bestInvIdx < 9 ? bestInvIdx + 36 : bestInvIdx;
            int armorSlot = 5 + s;

            var menu = mc.player.containerMenu;

            mc.gameMode.handleContainerInput(
                    menu.containerId, invSlot, 0, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(
                    menu.containerId, armorSlot, 0, ContainerInput.PICKUP, mc.player);

            tickTimer = (int) delay.getValue();
            return;
        }
    }

    private int findBest(EquipmentSlot slot, ItemStack current) {
        int currentScore = score(current);
        int bestSlot     = -1;
        int bestScore    = currentScore;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            EquipmentSlot itemSlot = getSlot(stack);
            if (itemSlot == null || itemSlot != slot) continue;

            int sc = score(stack);
            if (sc > bestScore) {
                bestScore = sc;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private EquipmentSlot getSlot(ItemStack stack) {
        var item = stack.getItem();
        if (item == Items.LEATHER_HELMET || item == Items.CHAINMAIL_HELMET
                || item == Items.IRON_HELMET || item == Items.GOLDEN_HELMET
                || item == Items.DIAMOND_HELMET || item == Items.NETHERITE_HELMET
                || item == Items.TURTLE_HELMET || item == Items.COPPER_HELMET)
            return EquipmentSlot.HEAD;
        if (item == Items.LEATHER_CHESTPLATE || item == Items.CHAINMAIL_CHESTPLATE
                || item == Items.IRON_CHESTPLATE || item == Items.GOLDEN_CHESTPLATE
                || item == Items.DIAMOND_CHESTPLATE || item == Items.NETHERITE_CHESTPLATE
                || item == Items.COPPER_CHESTPLATE || item == Items.ELYTRA)
            return EquipmentSlot.CHEST;
        if (item == Items.LEATHER_LEGGINGS || item == Items.CHAINMAIL_LEGGINGS
                || item == Items.IRON_LEGGINGS || item == Items.GOLDEN_LEGGINGS
                || item == Items.DIAMOND_LEGGINGS || item == Items.NETHERITE_LEGGINGS
                || item == Items.COPPER_LEGGINGS)
            return EquipmentSlot.LEGS;
        if (item == Items.LEATHER_BOOTS || item == Items.CHAINMAIL_BOOTS
                || item == Items.IRON_BOOTS || item == Items.GOLDEN_BOOTS
                || item == Items.DIAMOND_BOOTS || item == Items.NETHERITE_BOOTS
                || item == Items.COPPER_BOOTS)
            return EquipmentSlot.FEET;
        return null;
    }

    private int score(ItemStack stack) {
        var item = stack.getItem();
        if (item == Items.LEATHER_HELMET) return 101;
        if (item == Items.LEATHER_CHESTPLATE) return 103;
        if (item == Items.LEATHER_LEGGINGS) return 102;
        if (item == Items.LEATHER_BOOTS) return 101;
        if (item == Items.COPPER_HELMET) return 101;
        if (item == Items.COPPER_CHESTPLATE) return 104;
        if (item == Items.COPPER_LEGGINGS) return 103;
        if (item == Items.COPPER_BOOTS) return 101;
        if (item == Items.CHAINMAIL_HELMET) return 202;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 205;
        if (item == Items.CHAINMAIL_LEGGINGS) return 204;
        if (item == Items.CHAINMAIL_BOOTS) return 201;
        if (item == Items.GOLDEN_HELMET) return 202;
        if (item == Items.GOLDEN_CHESTPLATE) return 205;
        if (item == Items.GOLDEN_LEGGINGS) return 204;
        if (item == Items.GOLDEN_BOOTS) return 201;
        if (item == Items.IRON_HELMET) return 302;
        if (item == Items.IRON_CHESTPLATE) return 306;
        if (item == Items.IRON_LEGGINGS) return 305;
        if (item == Items.IRON_BOOTS) return 302;
        if (item == Items.DIAMOND_HELMET) return 403;
        if (item == Items.DIAMOND_CHESTPLATE) return 408;
        if (item == Items.DIAMOND_LEGGINGS) return 406;
        if (item == Items.DIAMOND_BOOTS) return 403;
        if (item == Items.NETHERITE_HELMET) return 503;
        if (item == Items.NETHERITE_CHESTPLATE) return 508;
        if (item == Items.NETHERITE_LEGGINGS) return 506;
        if (item == Items.NETHERITE_BOOTS) return 503;
        if (item == Items.TURTLE_HELMET) return 302;
        if (item == Items.ELYTRA) return 201;
        return 0;
    }
}
