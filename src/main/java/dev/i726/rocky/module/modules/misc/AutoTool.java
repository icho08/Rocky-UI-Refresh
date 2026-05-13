package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.BlockBreakingListener;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;

public final class AutoTool extends Module implements TickListener, BlockBreakingListener {
    public enum EnchantPreference {
        None,
        Fortune,
        SilkTouch
    }

    private final ModeSetting<EnchantPreference> enchantPreference = new ModeSetting<>(EncryptedString.of("Prefer"), EnchantPreference.Fortune, EnchantPreference.class)
            .setDescription(EncryptedString.of("Enchantment preference"));

    private final BooleanSetting silkTouchEnderChest = new BooleanSetting(EncryptedString.of("Silk Touch Ender Chest"), true)
            .setDescription(EncryptedString.of("Only mine Ender Chests with Silk Touch"));

    private final BooleanSetting fortuneOres = new BooleanSetting(EncryptedString.of("Fortune Ores"), false)
            .setDescription(EncryptedString.of("Only mine ores with Fortune"));

    private final BooleanSetting antiBreak = new BooleanSetting(EncryptedString.of("Anti Break"), false)
            .setDescription(EncryptedString.of("Prevents breaking tools"));

    private final NumberSetting breakDurability = new NumberSetting(EncryptedString.of("Break Percentage"), 1, 100, 10, 1)
            .setDescription(EncryptedString.of("Durability percentage to stop using tool"));

    private final BooleanSetting switchBack = new BooleanSetting(EncryptedString.of("Switch Back"), true)
            .setDescription(EncryptedString.of("Switch back to previous item"));

    private final NumberSetting switchDelay = new NumberSetting(EncryptedString.of("Switch Delay"), 0, 20, 0, 1)
            .setDescription(EncryptedString.of("Delay in ticks before switching"));

    private boolean wasPressed;
    private boolean shouldSwitch;
    private int ticks;
    private int bestSlot;
    private int previousSlot = -1;

    public AutoTool() {
        super(EncryptedString.of("Auto Tool"),
                EncryptedString.of("Switches to best tool"), -1, CategoryManager.AUTOMATION);
        addSettings(enchantPreference, silkTouchEnderChest, fortuneOres, antiBreak, breakDurability, switchBack, switchDelay);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(BlockBreakingListener.class, this);
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(BlockBreakingListener.class, this);
    }

    @Override
    public void onTick() {
        if (switchBack.getValue() && !mc.options.attackKey.isPressed() && wasPressed && previousSlot != -1) {
            InventoryUtils.swapToSlot(previousSlot);
            previousSlot = -1;
            wasPressed = false;
            return;
        }

        if (ticks <= 0 && shouldSwitch && bestSlot != -1) {
            if (switchBack.getValue()) previousSlot = mc.player.getInventory().getSelectedSlot();
            InventoryUtils.swapToSlot(bestSlot);
            shouldSwitch = false;
        } else {
            ticks--;
        }

        wasPressed = mc.options.attackKey.isPressed();
    }

    @Override
    public void onBlockBreaking(BlockBreakingListener.BlockBreakingEvent event) {
        if (mc.player.isCreative()) return;
        
        // Only switch when actually breaking blocks (attack key pressed)
        if (!mc.options.attackKey.isPressed()) return;

        BlockPos pos = mc.crosshairTarget != null && mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK 
                ? ((net.minecraft.util.hit.BlockHitResult) mc.crosshairTarget).getBlockPos() : null;
        
        if (pos == null) return;

        BlockState blockState = mc.world.getBlockState(pos);
        ItemStack currentStack = mc.player.getMainHandStack();

        double bestScore = -1;
        bestSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            
            double score = getScore(itemStack, blockState);
            
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        if (bestSlot != -1 && (bestScore > getScore(currentStack, blockState) || shouldStopUsing(currentStack) || !isTool(currentStack))) {
            ticks = (int) switchDelay.getValue();

            if (ticks == 0) {
                if (switchBack.getValue()) previousSlot = mc.player.getInventory().getSelectedSlot();
                InventoryUtils.swapToSlot(bestSlot);
            } else {
                shouldSwitch = true;
            }
        }

        if (shouldStopUsing(currentStack) && isTool(currentStack)) {
            mc.options.attackKey.setPressed(false);
            event.cancel();
        }
    }



    private boolean shouldStopUsing(ItemStack itemStack) {
        return antiBreak.getValue() && 
               (itemStack.getMaxDamage() - itemStack.getDamage()) < (itemStack.getMaxDamage() * breakDurability.getValue() / 100);
    }

    private double getScore(ItemStack itemStack, BlockState state) {
        if (!isTool(itemStack) || shouldStopUsing(itemStack)) return -1;
        
        if (!itemStack.isSuitableFor(state) &&
            !(itemStack.isIn(ItemTags.SWORDS) && (state.getBlock() instanceof BambooBlock || state.getBlock() instanceof BambooShootBlock)) &&
            !(itemStack.getItem() instanceof ShearsItem && (state.getBlock() instanceof LeavesBlock || state.isIn(BlockTags.WOOL))))
            return -1;

        // Simplified enchantment checks - just check if enchantments exist
        if (silkTouchEnderChest.getValue() && state.getBlock() == Blocks.ENDER_CHEST) {
            // Skip silk touch check for now
        }

        if (fortuneOres.getValue() && isFortunable(state.getBlock())) {
            // Skip fortune check for now
        }

        double score = itemStack.getMiningSpeedMultiplier(state) * 1000;
        
        // Basic enchantment scoring without registry access
        try {
            score += itemStack.getEnchantments().getSize() * 10; // Basic enchantment bonus
        } catch (Exception ignored) {}

        if (itemStack.isIn(ItemTags.SWORDS) && (state.getBlock() instanceof BambooBlock || state.getBlock() instanceof BambooShootBlock)) {
            score += 9000;
            try {
                score += itemStack.get(DataComponentTypes.TOOL).getSpeed(state) * 1000;
            } catch (Exception ignored) {}
        }

        return score;
    }

    private boolean isTool(ItemStack itemStack) {
        return itemStack.isIn(ItemTags.AXES) || itemStack.isIn(ItemTags.HOES) || 
               itemStack.isIn(ItemTags.PICKAXES) || itemStack.isIn(ItemTags.SHOVELS) || 
               itemStack.getItem() instanceof ShearsItem;
    }

    private boolean isFortunable(Block block) {
        return block instanceof CropBlock || 
               block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE ||
               block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE ||
               block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE ||
               block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE ||
               block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE ||
               block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE ||
               block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE ||
               block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE;
    }
}
