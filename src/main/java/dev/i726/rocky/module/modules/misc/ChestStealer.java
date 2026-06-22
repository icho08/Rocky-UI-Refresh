package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.entity.*;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

public final class ChestStealer extends Module implements TickListener {

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 0, 500, 80, 5)
            .setDescription(EncryptedString.of("Milliseconds between stealing each item"));

    private final NumberSetting delayJitter = new NumberSetting(
            EncryptedString.of("Delay Jitter"), 0, 150, 30, 5)
            .setDescription(EncryptedString.of("Random extra ms per steal (humanisation)"));

    private final BooleanSetting closeWhenDone = new BooleanSetting(
            EncryptedString.of("Close When Done"), true)
            .setDescription(EncryptedString.of("Close the chest after all items are taken"));

    private final BooleanSetting ignoreTools = new BooleanSetting(
            EncryptedString.of("Ignore Tools"), false)
            .setDescription(EncryptedString.of("Skip tools and armour pieces"));

    private final BooleanSetting stackFirst = new BooleanSetting(
            EncryptedString.of("Stack First"), true)
            .setDescription(EncryptedString.of("Prioritise items that stack with existing inventory items"));

    private final BooleanSetting autoOpen = new BooleanSetting(
            EncryptedString.of("Auto Open"), false)
            .setDescription(EncryptedString.of("Automatically opens the nearest chest/barrel/shulker in range"));

    private final NumberSetting autoOpenRange = new NumberSetting(
            EncryptedString.of("Auto Open Range"), 1, 6, 4.0, 0.5)
            .setDescription(EncryptedString.of("Maximum distance to search for a storage block to auto-open"));

    private final TimerUtils timer      = new TimerUtils();
    private final TimerUtils openTimer  = new TimerUtils();
    private int nextDelay = 80;

    // Tracks chests we've fully looted so we don't immediately re-open them.
    // Maps BlockPos → system time when looting finished (ms). Cleared on disable.
    private static final long LOOTED_COOLDOWN_MS = 30_000L; // 30 seconds
    private final Map<BlockPos, Long> lootedChests = new HashMap<>();
    private BlockPos currentOpenedPos = null;

    public ChestStealer() {
        super(EncryptedString.of("Chest Stealer"),
                EncryptedString.of("Automatically steals items from open chests"),
                -1, CategoryManager.BLATANT);
        addSettings(delay, delayJitter, closeWhenDone, ignoreTools, stackFirst, autoOpen, autoOpenRange);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        lootedChests.clear();
        currentOpenedPos = null;
        rollDelay();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        lootedChests.clear();
        currentOpenedPos = null;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // ── Auto-open: find and open a nearby storage block ──────────────────
        // Purge expired looted-chest entries so we can revisit after cooldown.
        lootedChests.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > LOOTED_COOLDOWN_MS);

        if (autoOpen.getValue() && !(mc.currentScreen instanceof GenericContainerScreen)) {
            if (openTimer.hasReached(500)) {
                BlockPos nearest = findNearestStorage();
                if (nearest != null) {
                    currentOpenedPos = nearest;
                    openStorage(nearest);
                    openTimer.reset();
                }
            }
            return;
        }

        // ── Steal from the open chest screen ─────────────────────────────────
        if (!(mc.currentScreen instanceof GenericContainerScreen)) return;
        if (!timer.hasReached(nextDelay)) return;

        GenericContainerScreenHandler handler = (GenericContainerScreenHandler) mc.player.currentScreenHandler;
        int containerSize = handler.getRows() * 9;

        if (stackFirst.getValue()) {
            for (int i = 0; i < containerSize; i++) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (stack.isEmpty()) continue;
                if (ignoreTools.getValue() && isToolOrArmor(stack)) continue;
                if (hasMatchingStack(stack)) {
                    clickSlot(handler, i);
                    return;
                }
            }
        }

        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (ignoreTools.getValue() && isToolOrArmor(stack)) continue;
            clickSlot(handler, i);
            return;
        }

        // Nothing left to steal — mark this chest as looted so autoOpen won't
        // immediately reopen the same empty chest and loop forever.
        if (currentOpenedPos != null) {
            lootedChests.put(currentOpenedPos, System.currentTimeMillis());
            currentOpenedPos = null;
        }
        if (closeWhenDone.getValue()) mc.player.closeHandledScreen();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void clickSlot(GenericContainerScreenHandler handler, int slot) {
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
        timer.reset();
        rollDelay();
    }

    private void rollDelay() {
        nextDelay = (int) delay.getValue() + (int)(Math.random() * delayJitter.getValue());
    }

    /** Scans nearby loaded chunks for the nearest openable storage block,
     *  skipping any chest that was recently fully looted by us. */
    private BlockPos findNearestStorage() {
        Vec3d eye    = mc.player.getEyePos();
        double maxR  = autoOpenRange.getValue();
        BlockPos best     = null;
        double   bestDist = Double.MAX_VALUE;

        int playerCX = mc.player.getBlockX() >> 4;
        int playerCZ = mc.player.getBlockZ() >> 4;

        for (int cx = playerCX - 1; cx <= playerCX + 1; cx++) {
            for (int cz = playerCZ - 1; cz <= playerCZ + 1; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!isStorageBlock(be)) continue;
                    if (lootedChests.containsKey(be.getPos())) continue; // skip recently looted
                    double d = eye.distanceTo(Vec3d.ofCenter(be.getPos()));
                    if (d <= maxR && d < bestDist) {
                        bestDist = d;
                        best     = be.getPos();
                    }
                }
            }
        }
        return best;
    }

    private boolean isStorageBlock(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof TrappedChestBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity;
    }

    /** Sends a block interaction packet to open the storage block.
     *  Computes the correct hit face from the player's eye position so
     *  the server-side geometry check always passes. */
    private void openStorage(BlockPos pos) {
        Vec3d eye    = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d delta  = eye.subtract(center);

        double ax = Math.abs(delta.x), ay = Math.abs(delta.y), az = Math.abs(delta.z);
        Direction face;
        if (ay >= ax && ay >= az) face = delta.y >= 0 ? Direction.UP    : Direction.DOWN;
        else if (ax >= az)        face = delta.x >= 0 ? Direction.EAST  : Direction.WEST;
        else                      face = delta.z >= 0 ? Direction.SOUTH : Direction.NORTH;

        Vec3d hitVec = center.add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, face, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }

    private boolean hasMatchingStack(ItemStack target) {
        for (int i = 0; i < 36; i++) {
            ItemStack inv = mc.player.getInventory().getStack(i);
            if (!inv.isEmpty() && ItemStack.areItemsEqual(inv, target) && inv.getCount() < inv.getMaxCount())
                return true;
        }
        return false;
    }

    private boolean isToolOrArmor(ItemStack stack) {
        var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable != null) {
            EquipmentSlot s = equippable.slot();
            if (s == EquipmentSlot.HEAD || s == EquipmentSlot.CHEST
                    || s == EquipmentSlot.LEGS || s == EquipmentSlot.FEET) {
                return true;
            }
        }
        return stack.isIn(ItemTags.PICKAXES)
                || stack.isIn(ItemTags.SHOVELS)
                || stack.isIn(ItemTags.AXES)
                || stack.isIn(ItemTags.HOES)
                || stack.isIn(ItemTags.SWORDS);
    }
}
