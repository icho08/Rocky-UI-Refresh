package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import java.util.Random;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public final class FastUse extends Module {
    public enum Mode {
        All,
        Blocks
    }

    private final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Mode"), Mode.All, Mode.class)
            .setDescription(EncryptedString.of("All or Blocks only"));

    // Vanilla cooldown is 4 ticks. Setting it to 0 spams UseItem packets faster
    // than any human and is the kind of pattern Vulcan flags within a second.
    // Default 2 ticks is faster than vanilla but plausible.
    private final NumberSetting cooldown = new NumberSetting(EncryptedString.of("Cooldown"), 0, 4, 2, 1)
            .setDescription(EncryptedString.of("Fast-use cooldown in ticks (vanilla 4)"));

    private final MinMaxSetting jitter = new MinMaxSetting(EncryptedString.of("Jitter (ticks)"), 0, 4, 1, 0, 1)
            .setDescription(EncryptedString.of("Random extra ticks added on top of the cooldown each call"));

    private final NumberSetting failChance = new NumberSetting(EncryptedString.of("Vanilla Chance %"), 0, 100, 0, 1)
            .setDescription(EncryptedString.of("Chance per call to fall back to the vanilla 4-tick cooldown"));

    private final BooleanSetting respectBuilding = new BooleanSetting(EncryptedString.of("Respect Building"), true)
            .setDescription(EncryptedString.of("Skip fast-use while sneaking — looks like manual placement"));

    private final Random random = new Random();

    public FastUse() {
        super(EncryptedString.of("Fast Use"),
                EncryptedString.of("Use items faster"), -1, CategoryManager.AUTOMATION);
        addSettings(mode, cooldown, jitter, failChance, respectBuilding);
    }

    private boolean isPlaceableBlock(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        
        // Exclude interactive blocks and items
        if (itemStack.getItem() instanceof BedItem || 
            itemStack.getItem() instanceof SpawnEggItem) {
            return false;
        }
        
        Block block = blockItem.getBlock();
        
        // Exclude interactive blocks
        if (block instanceof ChestBlock || 
            block instanceof CraftingTableBlock ||
            block instanceof FurnaceBlock ||
            block instanceof EnderChestBlock ||
            block instanceof ShulkerBoxBlock) {
            return false;
        }
        
        return true;
    }

    public int getItemUseCooldown(ItemStack itemStack) {
        if (mode.getMode() == Mode.All || (mode.getMode() == Mode.Blocks && isPlaceableBlock(itemStack))) {
            // Respect-building gate: while sneaking the player is most likely
            // doing precise placement, so let vanilla rate take over.
            try {
                if (respectBuilding.getValue()
                        && net.minecraft.client.Minecraft.getInstance().player != null
                        && net.minecraft.client.Minecraft.getInstance().player.isShiftKeyDown())
                    return 4;
            } catch (Throwable ignored) { }

            // Random vanilla fallback to break perfect cadence
            int vc = failChance.getValueInt();
            if (vc > 0 && random.nextInt(100) < vc) return 4;

            int base = (int) cooldown.getValue();
            int extra = jitter.getMaxValue() > 0 ? jitter.getRandomValueInt() : 0;
            return Math.min(4, base + extra);
        }
        return 4;
    }

    public double getCooldown() {
        return cooldown.getValue();
    }
}

