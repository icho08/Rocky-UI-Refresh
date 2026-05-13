package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.util.math.BlockPos;

public final class BridgeAssist extends Module implements TickListener {
    private final NumberSetting edgeDistance = new NumberSetting(EncryptedString.of("Edge Distance"), 0.05, 0.5, 0.25, 0.01)
            .setDescription(EncryptedString.of("How close to the block edge before sneaking starts"));
    private final NumberSetting lookAhead = new NumberSetting(EncryptedString.of("Look-Ahead Ticks"), 0, 10, 3, 1)
            .setDescription(EncryptedString.of("How many ticks of velocity to project when checking for a fall"));
    private final NumberSetting minHeight = new NumberSetting(EncryptedString.of("Min Fall Height"), 1, 10, 1, 1)
            .setDescription(EncryptedString.of("Only sneak if there's at least this much air below"));
    private final NumberSetting holdTicks = new NumberSetting(EncryptedString.of("Sneak Hold Ticks"), 0, 10, 1, 1)
            .setDescription(EncryptedString.of("Extra ticks to keep sneaking after the edge clears (smooths cadence)"));
    private final BooleanSetting onlyForward = new BooleanSetting(EncryptedString.of("Only Forward"), false)
            .setDescription(EncryptedString.of("Only auto-sneak when actively walking forward"));

    private int holdClock = 0;

    public BridgeAssist() {
        super(EncryptedString.of("Bridge Assist"),
                EncryptedString.of("Helps with bridging"), -1, CategoryManager.BRIDGING);
        addSettings(edgeDistance, lookAhead, minHeight, holdTicks, onlyForward);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        holdClock = 0;
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        mc.options.sneakKey.setPressed(false);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (onlyForward.getValue() && !mc.options.forwardKey.isPressed()) {
            mc.options.sneakKey.setPressed(false);
            return;
        }

        boolean shouldSneak = isNearEdge();
        if (shouldSneak) {
            holdClock = holdTicks.getValueInt();
        } else if (holdClock > 0) {
            holdClock--;
            shouldSneak = true;
        }
        mc.options.sneakKey.setPressed(shouldSneak);
    }

    private boolean isNearEdge() {
        double x = mc.player.getX();
        double z = mc.player.getZ();
        double vx = mc.player.getVelocity().x;
        double vz = mc.player.getVelocity().z;

        // Check current position
        BlockPos currentBelow = BlockPos.ofFloored(x, mc.player.getY() - 1, z);
        if (mc.world.getBlockState(currentBelow).isAir() && hasMinFallHeight(currentBelow)) {
            return true;
        }

        // Predict next position with velocity
        int la = lookAhead.getValueInt();
        double nextX = x + vx * la;
        double nextZ = z + vz * la;

        // Check if we're approaching an edge
        double distToEdgeX = Math.min(nextX - Math.floor(nextX), Math.ceil(nextX) - nextX);
        double distToEdgeZ = Math.min(nextZ - Math.floor(nextZ), Math.ceil(nextZ) - nextZ);

        double edge = edgeDistance.getValue();
        if (distToEdgeX <= edge || distToEdgeZ <= edge) {
            BlockPos nextBelow = BlockPos.ofFloored(nextX, mc.player.getY() - 1, nextZ);
            return mc.world.getBlockState(nextBelow).isAir() && hasMinFallHeight(nextBelow);
        }

        return false;
    }

    private boolean hasMinFallHeight(BlockPos pos) {
        int fallHeight = 0;
        BlockPos checkPos = pos;
        int min = minHeight.getValueInt();

        while (fallHeight < min && mc.world.getBlockState(checkPos).isAir()) {
            checkPos = checkPos.down();
            fallHeight++;
        }

        return fallHeight >= min;
    }
}
