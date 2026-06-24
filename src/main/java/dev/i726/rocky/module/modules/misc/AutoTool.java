package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.BlockBreakingListener;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import net.minecraft.world.level.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class AutoTool extends Module implements TickListener, BlockBreakingListener {

    public enum EnchantPreference { None, Fortune, SilkTouch }

    private final ModeSetting<EnchantPreference> enchantPreference = new ModeSetting<>(
            EncryptedString.of("Prefer"), EnchantPreference.Fortune, EnchantPreference.class)
            .setDescription(EncryptedString.of("Enchantment to prefer when multiple tools match"));

    private final BooleanSetting silkTouchEnderChest = new BooleanSetting(
            EncryptedString.of("Silk Touch Ender Chest"), true)
            .setDescription(EncryptedString.of("Only mine Ender Chests with a Silk Touch tool"));

    private final BooleanSetting fortuneOres = new BooleanSetting(
            EncryptedString.of("Fortune Ores"), false)
            .setDescription(EncryptedString.of("Skip ores entirely if no Fortune tool is available"));

    private final BooleanSetting antiBreak = new BooleanSetting(
            EncryptedString.of("Anti Break"), false)
            .setDescription(EncryptedString.of("Stop using a tool before it breaks"));

    private final NumberSetting breakDurability = new NumberSetting(
            EncryptedString.of("Break Percentage"), 1, 100, 10, 1)
            .setDescription(EncryptedString.of("Durability % threshold to stop using a tool"));

    private final BooleanSetting switchBack = new BooleanSetting(
            EncryptedString.of("Switch Back"), true)
            .setDescription(EncryptedString.of("Return to the previous slot after breaking stops"));

    private final NumberSetting switchDelay = new NumberSetting(
            EncryptedString.of("Switch Delay"), 0, 20, 0, 1)
            .setDescription(EncryptedString.of("Ticks to wait before switching tool"));

    private boolean wasPressed;
    private boolean shouldSwitch;
    private int ticks;
    private int bestSlot;
    private int previousSlot = -1;

    public AutoTool() {
        super(EncryptedString.of("Auto Tool"),
                EncryptedString.of("Switches to the best tool for the block being mined"),
                -1, CategoryManager.AUTOMATION);
        addSettings(enchantPreference, silkTouchEnderChest, fortuneOres, antiBreak, breakDurability, switchBack, switchDelay);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(BlockBreakingListener.class, this);
        wasPressed = false;
        shouldSwitch = false;
        ticks = 0;
        previousSlot = -1;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(BlockBreakingListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (switchBack.getValue() && !mc.options.keyAttack.isDown() && wasPressed && previousSlot != -1) {
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

        wasPressed = mc.options.keyAttack.isDown();
    }

    @Override
    public void onBlockBreaking(BlockBreakingListener.BlockBreakingEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.isCreative()) return;
        if (!mc.options.keyAttack.isDown()) return;

        BlockPos pos = mc.hitResult != null
                && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos()
                : null;
        if (pos == null) return;

        BlockState blockState = mc.level.getBlockState(pos);
        ItemStack currentStack = mc.player.getMainHandItem();

        double bestScore = -1;
        bestSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            double score = getScore(stack, blockState);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        if (bestSlot != -1 && (bestScore > getScore(currentStack, blockState)
                || shouldStopUsing(currentStack)
                || !isTool(currentStack))) {
            ticks = (int) switchDelay.getValue();
            if (ticks == 0) {
                if (switchBack.getValue()) previousSlot = mc.player.getInventory().getSelectedSlot();
                InventoryUtils.swapToSlot(bestSlot);
            } else {
                shouldSwitch = true;
            }
        }

        if (shouldStopUsing(currentStack) && isTool(currentStack)) {
            mc.options.keyAttack.setDown(false);
            event.cancel();
        }
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    private double getScore(ItemStack stack, BlockState state) {
        if (!isTool(stack) || shouldStopUsing(stack)) return -1;

        boolean suitable = stack.isCorrectToolForDrops(state)
                || (stack.is(ItemTags.SWORDS) && isBamboo(state))
                || (stack.getItem() instanceof ShearsItem
                        && (state.getBlock() instanceof LeavesBlock || state.is(BlockTags.WOOL)));

        if (!suitable) return -1;

        // Hard requirements from settings
        if (silkTouchEnderChest.getValue() && state.getBlock() == Blocks.ENDER_CHEST
                && enchLevel(stack, Enchantments.SILK_TOUCH) == 0) return -1;

        if (fortuneOres.getValue() && isFortunable(state.getBlock())
                && enchLevel(stack, Enchantments.FORTUNE) == 0) return -1;

        double score = stack.getDestroySpeed(state) * 1000;

        // Enchantment preference bonus
        if (enchantPreference.isMode(EnchantPreference.Fortune)) {
            score += enchLevel(stack, Enchantments.FORTUNE) * 500;
        } else if (enchantPreference.isMode(EnchantPreference.SilkTouch)) {
            score += enchLevel(stack, Enchantments.SILK_TOUCH) * 500;
        }

        // Sword bonus for bamboo
        if (stack.is(ItemTags.SWORDS) && isBamboo(state)) {
            score += 9000;
            try {
                var tool = stack.get(DataComponents.TOOL);
                if (tool != null) score += tool.getMiningSpeed(state) * 1000;
            } catch (Exception ignored) {}
        }

        return score;
    }

    private boolean shouldStopUsing(ItemStack stack) {
        return antiBreak.getValue()
                && stack.getMaxDamage() > 0
                && (stack.getMaxDamage() - stack.getDamageValue()) < (stack.getMaxDamage() * breakDurability.getValue() / 100.0);
    }

    private boolean isTool(ItemStack stack) {
        return stack.is(ItemTags.AXES)
                || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.getItem() instanceof ShearsItem;
    }

    private boolean isBamboo(BlockState state) {
        return state.getBlock() instanceof BambooStalkBlock || state.getBlock() instanceof BambooSaplingBlock;
    }

    private boolean isFortunable(Block block) {
        return block instanceof CropBlock
                || block == Blocks.COAL_ORE         || block == Blocks.DEEPSLATE_COAL_ORE
                || block == Blocks.IRON_ORE          || block == Blocks.DEEPSLATE_IRON_ORE
                || block == Blocks.GOLD_ORE          || block == Blocks.DEEPSLATE_GOLD_ORE
                || block == Blocks.DIAMOND_ORE       || block == Blocks.DEEPSLATE_DIAMOND_ORE
                || block == Blocks.EMERALD_ORE       || block == Blocks.DEEPSLATE_EMERALD_ORE
                || block == Blocks.LAPIS_ORE         || block == Blocks.DEEPSLATE_LAPIS_ORE
                || block == Blocks.REDSTONE_ORE      || block == Blocks.DEEPSLATE_REDSTONE_ORE
                || block == Blocks.COPPER_ORE        || block == Blocks.DEEPSLATE_COPPER_ORE;
    }

    // ── Enchantment helpers ───────────────────────────────────────────────────

    private int enchLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        if (mc.level == null) return 0;
        return mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(key.identifier())
                .map(e -> EnchantmentHelper.getItemEnchantmentLevel(e, stack))
                .orElse(0);
    }
}
