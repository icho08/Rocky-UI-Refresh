package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.event.events.PacketSendListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;

import net.minecraft.util.math.Vec3d;

import java.awt.*;

public final class TargetHud extends Module implements HudListener, PacketSendListener {
	private final NumberSetting xCoord = new NumberSetting(EncryptedString.of("X"), 0, 1920, 500, 1);
	private final NumberSetting yCoord = new NumberSetting(EncryptedString.of("Y"), 0, 1080, 500, 1);
	private final BooleanSetting hudTimeout = new BooleanSetting(EncryptedString.of("Timeout"), true)
			.setDescription(EncryptedString.of("Target hud will disappear after 10 seconds"));
	private long lastAttackTime = 0;
	public static float animation;
	private static final long timeout = 10000;

	public TargetHud() {
		super(EncryptedString.of("Target HUD"),
                EncryptedString.of("Shows target information"),
				-1,
				CategoryManager.GUI);
		addSettings(xCoord, yCoord, hudTimeout);
	}

	@Override
	public void onEnable() {
		eventManager.add(HudListener.class, this);
		eventManager.add(PacketSendListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		eventManager.remove(PacketSendListener.class, this);
		super.onDisable();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		DrawContext context = event.context;

		int x = xCoord.getValueInt();
		int y = yCoord.getValueInt();

		RenderUtils.unscaledProjection();
		if ((!hudTimeout.getValue() || (System.currentTimeMillis() - lastAttackTime <= timeout)) &&
				mc.player.getAttacking() != null && mc.player.getAttacking() instanceof PlayerEntity player && player.isAlive()) {
			animation = RenderUtils.fast(animation, mc.player.getAttacking() instanceof PlayerEntity player1 && player1.isAlive() ? 0 : 1, 15f);

			PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
			float tx = (float) x;
			float ty = (float) y;
			org.joml.Matrix3x2fStack matrixStack = context.getMatrices();
			matrixStack.pushMatrix();

			RenderUtils.renderRoundedQuad(context, new Color(31, 41, 55, 200), x, y, x + 180, y + 70, 5, 10);
			RenderUtils.renderRoundedQuad(context, Utils.getMainColor(255, 1), x, y + 18, x + 180, y + 20, 0, 10);

			TextRenderer.drawString(player.getName().getString(), context, x + 25, y + 3, new Color(229, 231, 235).getRGB());

			if (entry == null) {
				TextRenderer.drawString("Bot", context, x + 25, y + 25, new Color(239, 68, 68).getRGB());
				matrixStack.popMatrix();
				RenderUtils.scaledProjection();
				return;
			}

			float health = player.getHealth() + player.getAbsorptionAmount();
			TextRenderer.drawString("HP: " + Math.round(health), context, x + 25, y + 25, new Color(59, 130, 246).getRGB());
			
			// Health bar
			int barWidth = 150;
			int barHeight = 4;
			context.fill(x + 25, y + 37, x + 25 + barWidth, y + 37 + barHeight, new Color(55, 65, 81).getRGB());
			int healthWidth = (int)(barWidth * (health / 20.0f));
			context.fill(x + 25, y + 37, x + 25 + healthWidth, y + 37 + barHeight, new Color(34, 197, 94).getRGB());

			TextRenderer.drawString("Ping: " + entry.getLatency() + "ms", context, x + 25, y + 47, new Color(156, 163, 175).getRGB());

			// PlayerSkinDrawer API changed in 1.21.10 - needs update
			// PlayerSkinDrawer.draw(context, entry.getSkinTextures().texture(), x + 3, y + 3, 16);
			matrixStack.popMatrix();
		} else {
			animation = RenderUtils.fast(animation, 1, 15f);
		}
		RenderUtils.scaledProjection();
	}

	private Color getDamageTickColor(int hurtTime) {
		return switch (hurtTime) {
			case 0 -> null;
			case 10 -> new Color(255, 0, 0, 255);
			case 9 -> new Color(255, 50, 0, 255);
			case 8 -> new Color(255, 100, 0, 255);
			case 7 -> new Color(255, 150, 0, 255);
			case 6 -> new Color(255, 255, 0, 255);
			case 5 -> new Color(200, 255, 0, 255);
			case 4 -> new Color(175, 255, 0, 255);
			case 3 -> new Color(100, 255, 0, 255);
			case 2 -> new Color(50, 255, 0, 255);
			case 1 -> new Color(0, 255, 0, 255);
			default -> throw new IllegalStateException("uv" + hurtTime);
		};
	}

	@Override
	public void onPacketSend(PacketSendListener.PacketSendEvent event) {
		if (event.packet instanceof PlayerInteractEntityC2SPacket packet) {
			packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
				@Override
				public void interact(Hand hand) {

				}

				@Override
				public void interactAt(Hand hand, Vec3d pos) {

				}

				@Override
				public void attack() {
					if (mc.targetedEntity instanceof PlayerEntity) {
						lastAttackTime = System.currentTimeMillis();
					}
				}
			});
		}
	}
}