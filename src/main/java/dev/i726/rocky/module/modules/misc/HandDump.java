package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Hand Dump — automatically deposits items from your hand or hotbar into a chest
 * without manually dragging anything.
 *
 * Modes:
 *   Hand Only  — deposits only the currently held item.
 *   Hotbar     — deposits every non-empty hotbar slot (0–8).
 *   Full Inv   — deposits every non-empty inventory slot (0–35).
 *
 * How it works:
 *   1. If "Auto Open" is on, finds the nearest chest/barrel/shulker and opens it.
 *   2. Once a GenericContainerScreen is open, QUICK_MOVEs items from the
 *      player's inventory into the chest one by one (respecting the delay).
 *   3. Closes the chest when finished if "Close When Done" is on.
 *
 * The chest screen opens for as little as one tick, so it's nearly invisible.
 * Uses QUICK_MOVE (shift-click equivalent) — standard anticheat-safe interaction.
 *
 * PlayerScreenHandler slot layout reference (for the opposite direction, see AutoPotRefill).
 * GenericContainerScreenHandler layout:
 *   0 .. (containerSize-1)          → chest slots
 *   containerSize .. containerSize+26 → player main inventory  (inv index 9–35)
 *   containerSize+27 .. containerSize+35 → hotbar             (inv index 0–8)
 */
public final class HandDump extends Module implements TickListener {

    public enum DumpMode { Hand, Hotbar, FullInventory }

    private final ModeSetting<DumpMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), DumpMode.Hotbar, DumpMode.class)
            .setDescription(EncryptedString.of("Which slots to dump into the chest"));

    private final BooleanSetting autoOpen = new BooleanSetting(
            EncryptedString.of("Auto Open"), true)
            .setDescription(EncryptedString.of("Automatically opens the nearest chest/barrel/shulker in range"));

    private final NumberSetting autoOpenRange = new NumberSetting(
            EncryptedString.of("Auto Open Range"), 1, 6, 4.0, 0.5)
            .setDescription(EncryptedString.of("Maximum distance to search for a storage block to open"));

    private final BooleanSetting closeWhenDone = new BooleanSetting(
            EncryptedString.of("Close When Done"), true)
            .setDescription(EncryptedString.of("Close the chest after depositing all items"));

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 0, 500, 60, 5)
            .setDescription(EncryptedString.of("Milliseconds between each slot deposit"));

    private final NumberSetting delayJitter = new NumberSetting(
            EncryptedString.of("Delay Jitter"), 0, 150, 25, 5)
            .setDescription(EncryptedString.of("Random extra ms per deposit (humanisation)"));

    private final TimerUtils moveTimer = new TimerUtils();
    private final TimerUtils openTimer = new TimerUtils();
    private int nextDelay = 60;

    public HandDump() {
        super(EncryptedString.of("Hand Dump"),
                EncryptedString.of("Deposits hand/hotbar items into a chest without manual dragging"),
                -1, CategoryManager.BLATANT);
        addSettings(mode, autoOpen, autoOpenRange, closeWhenDone, delay, delayJitter);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        rollDelay();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        // ── Auto-open: find and open a nearby storage block ──────────────────
        if (autoOpen.getValue() && !(mc.screen instanceof ContainerScreen)) {
            if (openTimer.hasReached(500)) {
                BlockPos nearest = findNearestStorage();
                if (nearest != null) {
                    openStorage(nearest);
                    openTimer.reset();
                }
            }
            return;
        }

        // ── Deposit into the open container ──────────────────────────────────
        if (!(mc.screen instanceof ContainerScreen)) return;
        if (!moveTimer.hasReached(nextDelay)) return;

        ChestMenu handler =
                (ChestMenu) mc.player.containerMenu;
        int containerSize = handler.getRowCount() * 9;

        // Resolve which inventory indices to dump
        int slotStart, slotEnd;
        switch (mode.getMode()) {
            case Hand -> {
                slotStart = mc.player.getInventory().getSelectedSlot();
                slotEnd   = slotStart;
            }
            case Hotbar -> {
                slotStart = 0;
                slotEnd   = 8;
            }
            default -> { // FullInventory
                slotStart = 0;
                slotEnd   = 35;
            }
        }

        // Find the next non-empty player slot to dump
        // In GenericContainerScreenHandler the player inventory slots start at containerSize:
        //   containerSize +  0..26  → main inv  (player inv 9–35)
        //   containerSize + 27..35  → hotbar     (player inv 0–8)
        for (int invIdx = slotStart; invIdx <= slotEnd; invIdx++) {
            if (mc.player.getInventory().getItem(invIdx).isEmpty()) continue;

            // Convert player inventory index → handler slot index
            int handlerSlot = invIndexToHandlerSlot(invIdx, containerSize);
            if (handlerSlot == -1) continue;

            mc.gameMode.handleInventoryMouseClick(
                    handler.containerId,
                    handlerSlot,
                    0,
                    ContainerInput.QUICK_MOVE,
                    mc.player
            );
            moveTimer.reset();
            rollDelay();
            return;
        }

        // Nothing left to dump — close and disable (HandDump is a one-shot action)
        if (closeWhenDone.getValue()) mc.player.closeContainer();
        this.toggle();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Converts a player inventory index (0–35) to its handler slot index inside
     * a GenericContainerScreenHandler.
     *
     *   Player inv 9–35  → handler slot  containerSize + (invIdx - 9)       [0..26]
     *   Player inv 0–8   → handler slot  containerSize + 27 + invIdx         [27..35]
     */
    private static int invIndexToHandlerSlot(int invIdx, int containerSize) {
        if (invIdx >= 9 && invIdx <= 35) return containerSize + (invIdx - 9);
        if (invIdx >= 0 && invIdx <= 8)  return containerSize + 27 + invIdx;
        return -1;
    }

    /** Scans nearby loaded chunks for the nearest openable storage block. */
    private BlockPos findNearestStorage() {
        Vec3 eye   = mc.player.getEyePosition();
        double maxR = autoOpenRange.getValue();
        BlockPos best     = null;
        double   bestDist = Double.MAX_VALUE;

        int playerCX = mc.player.getBlockX() >> 4;
        int playerCZ = mc.player.getBlockZ() >> 4;

        for (int cx = playerCX - 1; cx <= playerCX + 1; cx++) {
            for (int cz = playerCZ - 1; cz <= playerCZ + 1; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!isStorageBlock(be)) continue;
                    double d = eye.distanceTo(Vec3.atCenterOf(be.getBlockPos()));
                    if (d <= maxR && d < bestDist) {
                        bestDist = d;
                        best     = be.getBlockPos();
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

    private void openStorage(BlockPos pos) {
        Vec3 eye    = mc.player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 delta  = eye.subtract(center);

        double ax = Math.abs(delta.x), ay = Math.abs(delta.y), az = Math.abs(delta.z);
        Direction face;
        if (ay >= ax && ay >= az) face = delta.y >= 0 ? Direction.UP   : Direction.DOWN;
        else if (ax >= az)        face = delta.x >= 0 ? Direction.EAST  : Direction.WEST;
        else                      face = delta.z >= 0 ? Direction.SOUTH : Direction.NORTH;

        Vec3 hitVec = center.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, face, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }

    private void rollDelay() {
        nextDelay = (int) delay.getValue() + (int)(Math.random() * delayJitter.getValue());
    }
}
