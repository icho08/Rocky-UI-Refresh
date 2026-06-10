package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * God Bridge - Edge-safe backward bridging for 1.21.10
 * - Prevents falling off edges while building backward
 * - Stops movement when near edge + looking down + moving backward
 * - Manual block placement (user right-clicks)
 */
public class GodBridge extends Module implements TickListener {

    public static GodBridge INSTANCE;

    private final NumberSetting edgeThreshold = new NumberSetting(
            EncryptedString.of("Edge Threshold"), 10, 40, 25, 1)
            .setDescription(EncryptedString.of("How close to edge (in 100ths) before stopping"));

    private final NumberSetting downPitch = new NumberSetting(
            EncryptedString.of("Down Pitch"), 30, 80, 60, 1)
            .setDescription(EncryptedString.of("Pitch angle to consider looking down"));

    private final BooleanSetting autoSprint = new BooleanSetting(
            EncryptedString.of("Auto Sprint"), true)
            .setDescription(EncryptedString.of("Enable sprint while bridging"));

    private final NumberSetting decelRate = new NumberSetting(
            EncryptedString.of("Decel Rate"), 1, 10, 3, 1)
            .setDescription(EncryptedString.of("How fast to slow down (lower = slower, less detectable)"));

    public GodBridge() {
        super(EncryptedString.of("God Bridge"),
                EncryptedString.of("Stops movement near edges while looking down"),
                -1, CategoryManager.BRIDGING);
        INSTANCE = this;
        addSettings(edgeThreshold, downPitch, autoSprint, decelRate);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        mc.options.sprintKey.setPressed(false);
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;

        if (!player.isOnGround()) return;

        // Check if looking down and moving backward
        boolean lookingDown = player.getPitch() > downPitch.getValueInt();
        if (!lookingDown) return;

        boolean movingBackward = player.input.playerInput.backward();
        if (!movingBackward) return;

        if (autoSprint.getValue() && !player.isSprinting()) {
            mc.options.sprintKey.setPressed(true);
        }

        // Get the actual movement direction (opposite of where looking)
        Direction facing = player.getHorizontalFacing();
        Direction moveDir = facing.getOpposite(); // Backward movement direction
        
        BlockPos standing = BlockPos.ofFloored(player.getX(), player.getY() - 1, player.getZ());
        
        double edgeThresholdValue = edgeThreshold.getValueInt() / 100.0;

        // Check edge distance in movement direction
        double edgeDist = getEdgeDistance(player, moveDir);
        
        boolean nearEdge = edgeDist < edgeThresholdValue && mc.world.getBlockState(standing.offset(moveDir)).isAir();

        // Also check perpendicular directions if moving diagonally
        boolean movingLeft = player.input.playerInput.left();
        boolean movingRight = player.input.playerInput.right();
        
        if (movingLeft || movingRight) {
            Direction perpDir = getPerpendicularDir(moveDir, movingLeft);
            double perpEdgeDist = getEdgeDistance(player, perpDir);
            if (perpEdgeDist < edgeThresholdValue && mc.world.getBlockState(standing.offset(perpDir)).isAir()) {
                nearEdge = true;
            }
        }

        if (nearEdge) {
            // GRADUAL deceleration instead of instant stop (less detectable by anticheat)
            Vec3d vel = player.getVelocity();
            double decelFactor = 1.0 - (decelRate.getValueInt() * 0.05); // 0.05 per level
            player.setVelocity(vel.x * decelFactor, vel.y, vel.z * decelFactor);
        }
    }

    private double getEdgeDistance(ClientPlayerEntity player, Direction dir) {
        double px = player.getX();
        double pz = player.getZ();

        return switch (dir) {
            case NORTH -> pz - Math.floor(pz);
            case SOUTH -> Math.ceil(pz) - pz;
            case WEST -> px - Math.floor(px);
            case EAST -> Math.ceil(px) - px;
            default -> 1.0;
        };
    }

    private Direction getPerpendicularDir(Direction dir, boolean left) {
        return switch (dir) {
            case NORTH -> left ? Direction.WEST : Direction.EAST;
            case SOUTH -> left ? Direction.EAST : Direction.WEST;
            case WEST -> left ? Direction.SOUTH : Direction.NORTH;
            case EAST -> left ? Direction.NORTH : Direction.SOUTH;
            default -> Direction.NORTH;
        };
    }
}

