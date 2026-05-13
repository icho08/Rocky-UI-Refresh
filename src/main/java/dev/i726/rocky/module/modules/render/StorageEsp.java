package dev.i726.rocky.module.modules.render;

import org.lwjgl.opengl.GL11;
import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.event.events.PacketReceiveListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.block.entity.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public final class StorageEsp extends Module implements GameRenderListener, PacketReceiveListener {
	private final NumberSetting alpha = new NumberSetting(EncryptedString.of("Alpha"), 1, 255, 125, 1);
	private final NumberSetting range = new NumberSetting(EncryptedString.of("Range"), 10, 1000, 100, 10);
	private final BooleanSetting tracers = new BooleanSetting(EncryptedString.of("Tracers"), false)
			.setDescription(EncryptedString.of("Draws a line from your player to the storage block"));
	
	// Cache for donut bypass - stores hidden storage positions
	private final Set<BlockPos> hiddenStorages = new HashSet<>();

	public StorageEsp() {
		super(EncryptedString.of("Storage ESP"),
                EncryptedString.of("Shows chests through walls"),
				-1,
				CategoryManager.ESP);
		addSettings(alpha, range, tracers);
	}

	@Override
	public void onEnable() {
		eventManager.add(PacketReceiveListener.class, this);
		eventManager.add(GameRenderListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketReceiveListener.class, this);
		eventManager.remove(GameRenderListener.class, this);
		super.onDisable();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (mc.player == null || mc.world == null) return;
		
		renderStorages(event);
	}

	private Color getColor(BlockEntity blockEntity, int alpha) {
		if (blockEntity instanceof TrappedChestBlockEntity) {
			return new Color(255, 140, 0, alpha); // Orange for trapped chests
		} else if (blockEntity instanceof ChestBlockEntity) {
			return new Color(139, 69, 19, alpha); // Brown for regular chests
		} else if (blockEntity instanceof EnderChestBlockEntity) {
			return new Color(128, 0, 128, alpha); // Purple for ender chests
		} else if (blockEntity instanceof MobSpawnerBlockEntity) {
			return new Color(64, 64, 64, alpha); // Dark gray for spawners
		} else if (blockEntity instanceof ShulkerBoxBlockEntity) {
			return new Color(148, 0, 211, alpha); // Dark violet for shulker boxes
		} else if (blockEntity instanceof FurnaceBlockEntity || blockEntity instanceof BlastFurnaceBlockEntity || blockEntity instanceof SmokerBlockEntity) {
			return new Color(169, 169, 169, alpha); // Light gray for furnaces
		} else if (blockEntity instanceof BarrelBlockEntity) {
			return new Color(160, 82, 45, alpha); // Saddle brown for barrels
		} else if (blockEntity instanceof EnchantingTableBlockEntity) {
			return new Color(75, 0, 130, alpha); // Indigo for enchanting tables
		} else if (blockEntity instanceof BrewingStandBlockEntity) {
			return new Color(255, 215, 0, alpha); // Gold for brewing stands
		} else if (blockEntity instanceof HopperBlockEntity) {
			return new Color(105, 105, 105, alpha); // Dim gray for hoppers
		} else if (blockEntity instanceof DispenserBlockEntity || blockEntity instanceof DropperBlockEntity) {
			return new Color(192, 192, 192, alpha); // Silver for dispensers/droppers
		}
		return new Color(255, 255, 255, alpha); // White for unknown storage
	}

	private boolean isStorageBlock(BlockEntity blockEntity) {
		return blockEntity instanceof ChestBlockEntity ||
			   blockEntity instanceof TrappedChestBlockEntity ||
			   blockEntity instanceof EnderChestBlockEntity ||
			   blockEntity instanceof ShulkerBoxBlockEntity ||
			   blockEntity instanceof BarrelBlockEntity ||
			   blockEntity instanceof FurnaceBlockEntity ||
			   blockEntity instanceof BlastFurnaceBlockEntity ||
			   blockEntity instanceof SmokerBlockEntity ||
			   blockEntity instanceof EnchantingTableBlockEntity ||
			   blockEntity instanceof BrewingStandBlockEntity ||
			   blockEntity instanceof HopperBlockEntity ||
			   blockEntity instanceof DispenserBlockEntity ||
			   blockEntity instanceof DropperBlockEntity ||
			   blockEntity instanceof MobSpawnerBlockEntity;
	}

	private void renderStorages(GameRenderEvent event) {
		Camera cam = mc.gameRenderer.getCamera();
		if (cam == null) return;

		MatrixStack matrices = event.matrices;
		matrices.push();
		
		// Disable depth test to render through walls
		GL11.glDisable(GL11.GL_DEPTH_TEST);

		Vec3d playerPos = mc.player.getEntityPos();
		double maxRange = range.getValue();
		double maxRangeSquared = maxRange * maxRange;

		// Iterate through loaded chunks efficiently
		int playerChunkX = (int) playerPos.x >> 4;
		int playerChunkZ = (int) playerPos.z >> 4;
		int chunkRange = (int) Math.ceil(maxRange / 16.0) + 1;

		for (int chunkX = playerChunkX - chunkRange; chunkX <= playerChunkX + chunkRange; chunkX++) {
			for (int chunkZ = playerChunkZ - chunkRange; chunkZ <= playerChunkZ + chunkRange; chunkZ++) {
				WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
				if (chunk == null) continue;

				for (BlockPos blockPos : chunk.getBlockEntityPositions()) {
					// Distance culling - check squared distance for performance
					double distanceSquared = playerPos.squaredDistanceTo(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
					if (distanceSquared > maxRangeSquared) continue;

					BlockEntity blockEntity = mc.world.getBlockEntity(blockPos);
					if (blockEntity == null || !isStorageBlock(blockEntity)) continue;

					// Render storage block
					renderStorageBlock(matrices, blockPos, blockEntity);

					// Render tracer if enabled
					if (tracers.getValue()) {
						renderTracer(matrices, blockPos, blockEntity);
					}
				}
			}
		}

		// Re-enable depth test
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		matrices.pop();
	}

	private void renderStorageBlock(MatrixStack matrices, BlockPos blockPos, BlockEntity blockEntity) {
		Color color = getColor(blockEntity, alpha.getValueInt());
		
		// Render slightly smaller box for better visibility
		RenderUtils.renderFilledBox(matrices, 
			blockPos.getX() + 0.05F, blockPos.getY() + 0.05F, blockPos.getZ() + 0.05F,
			blockPos.getX() + 0.95F, blockPos.getY() + 0.95F, blockPos.getZ() + 0.95F, 
			color);
	}

	private void renderTracer(MatrixStack matrices, BlockPos blockPos, BlockEntity blockEntity) {
		Vec3d playerPos = mc.player.getEntityPos();
		Vec3d storagePos = new Vec3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
		Color tracerColor = getColor(blockEntity, 255);
		
		RenderUtils.renderLine(matrices, tracerColor, playerPos, storagePos);
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		// Donut bypass functionality removed
	}
}
