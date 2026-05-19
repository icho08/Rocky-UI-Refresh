package dev.i726.rocky.module.modules.render;
import dev.i726.rocky.gui.GuiTheme;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.modules.client.ClickGUI;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.utils.ColorUtils;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public final class PlayerESP extends Module implements GameRenderListener {
	public enum ShapeMode {
		Lines,
		Sides,
		Both
	}

	public enum TracerTarget {
		Head,
		Body,
		Feet
	}

	private final ModeSetting<ShapeMode> shapeMode = new ModeSetting<>(EncryptedString.of("Style"), ShapeMode.Both, ShapeMode.class);
	private final NumberSetting opacity = new NumberSetting(EncryptedString.of("Opacity"), 0, 255, 100, 1);
	private final NumberSetting outlineOpacity = new NumberSetting(EncryptedString.of("Outline Opacity"), 0, 255, 200, 5);
	private final NumberSetting range = new NumberSetting(EncryptedString.of("Range"), 10, 500, 100, 10);
	private final BooleanSetting tracers = new BooleanSetting(EncryptedString.of("Tracers"), false)
			.setDescription(EncryptedString.of("Draws a line from your player to the other"));
	private final ModeSetting<TracerTarget> tracerTarget = new ModeSetting<>(EncryptedString.of("Tracer Target"), TracerTarget.Body, TracerTarget.class);
	private final NumberSetting tracerOpacity = new NumberSetting(EncryptedString.of("Tracer Opacity"), 50, 255, 180, 5)
			.setDescription(EncryptedString.of("Opacity of tracer lines"));
	private final BooleanSetting glow = new BooleanSetting(EncryptedString.of("Glow"), false)
			.setDescription(EncryptedString.of("Applies the vanilla glowing effect to players"));

	public PlayerESP() {
		super(EncryptedString.of("Player ESP"),
                EncryptedString.of("Highlights players"),
				-1,
				CategoryManager.ESP);
		addSettings(shapeMode, opacity, outlineOpacity, range, tracers, tracerTarget, tracerOpacity, glow);
	}

	@Override
	public void onEnable() {
		eventManager.add(GameRenderListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(GameRenderListener.class, this);
		super.onDisable();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (mc.player == null || mc.world == null) return;
		
		Camera cam = mc.gameRenderer.getCamera();
		if (cam == null) return;

		MatrixStack matrices = event.matrices;
		matrices.push();
		
		// Matrices from WorldRenderer already include the view transformation.

		// Disable depth test so ESP renders through walls
		GL11.glDisable(GL11.GL_DEPTH_TEST);

		for (PlayerEntity player : mc.world.getPlayers()) {
			if (player == mc.player || player.isDead() || player.isRemoved()) continue;
			
			// Distance culling for performance
			double distance = mc.player.distanceTo(player);
			if (distance > range.getValue()) continue;

			// Render 3D box
			render3D(player, matrices);

			// Render tracer if enabled
			if (tracers.getValue()) {
				renderTracer(player, matrices, cam);
			}
		}

		// Flush the buffer while depth test is disabled
		mc.getBufferBuilders().getEntityVertexConsumers().draw();

		// Re-enable depth test
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		matrices.pop();
	}

	private void render3D(PlayerEntity player, MatrixStack matrices) {
		float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
		double xPos = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
		double yPos = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY());
		double zPos = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());

		double minX = xPos - player.getWidth() / 2;
		double minY = yPos;
		double minZ = zPos - player.getWidth() / 2;
		double maxX = xPos + player.getWidth() / 2;
		double maxY = yPos + player.getHeight();
		double maxZ = zPos + player.getWidth() / 2;

		ShapeMode mode = shapeMode.getMode();

		if (mode == ShapeMode.Sides || mode == ShapeMode.Both) {
			RenderUtils.renderFilledBox(
					matrices,
					minX, minY, minZ,
					maxX, maxY, maxZ,
					getColor(opacity.getValueInt()));
		}

		if (mode == ShapeMode.Lines || mode == ShapeMode.Both) {
			RenderUtils.drawOutlinedBox(
					matrices,
					new net.minecraft.util.math.Box(minX, minY, minZ, maxX, maxY, maxZ),
					getColor(outlineOpacity.getValueInt()));
		}
	}

	private void renderTracer(PlayerEntity player, MatrixStack matrices, Camera cam) {
		float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
		double heightOffset = switch (tracerTarget.getMode()) {
			case Head -> player.getHeight();
			case Body -> player.getHeight() / 2;
			case Feet -> 0.0;
		};
		Vec3d targetPos = player.getLerpedPos(tickDelta).add(0, heightOffset, 0);
		Vec3d camPos = cam.getPos();

		RenderUtils.renderLine(matrices, getColor(tracerOpacity.getValueInt()), camPos, targetPos);
	}

	private Color getColor(int alpha) {
		Color a = GuiTheme.accent();
		return new Color(a.getRed(), a.getGreen(), a.getBlue(), alpha);
	}
}
