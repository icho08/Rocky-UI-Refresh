package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.CameraUpdateListener;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.mixin.KeyBindingAccessor;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;


public final class Freecam extends Module implements TickListener, CameraUpdateListener {
	private final NumberSetting speed = new NumberSetting(EncryptedString.of("Speed"), 1, 10, 1, 1);
	public Vec3 oldPos;
	public Vec3 pos;

	public Freecam() {
		super(EncryptedString.of("Freecam"),
                EncryptedString.of("Fly around without moving"),
				-1,
				CategoryManager.MISC);
		addSettings(speed);

		oldPos = Vec3.ZERO;
		pos = Vec3.ZERO;
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(CameraUpdateListener.class, this);
		if (mc.level != null) {
			this.oldPos = this.pos = mc.player.getEyePosition();
		}

		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(CameraUpdateListener.class, this);

		if (mc.level != null) {
			mc.player.setDeltaMovement(Vec3.ZERO);
			mc.levelRenderer.allChanged();
		}
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.screen != null)
			return;

		mc.options.keyUse.setDown(false);
		mc.options.keyAttack.setDown(false);
		mc.options.keyUp.setDown(false);
		mc.options.keyDown.setDown(false);
		mc.options.keyLeft.setDown(false);
		mc.options.keyRight.setDown(false);
		mc.options.keyJump.setDown(false);
		mc.options.keyShift.setDown(false);

		float f = (float) Math.PI / 180;
		float f2 = (float) Math.PI;
		LocalPlayer clientPlayerEntity = mc.player;
		Vec3 vec3d = new Vec3(-Mth.sin(-mc.player.getYRot() * f - f2), 0.0, -Mth.cos(-clientPlayerEntity.getYRot() * f - f2));
		Vec3 vec3d2 = new Vec3(0.0, 1.0, 0.0);
		Vec3 vec3d3 = vec3d2.cross(vec3d);
		Vec3 vec3d4 = vec3d.cross(vec3d2);
		Vec3 vec3d5 = Vec3.ZERO;
		KeyMapping keyBinding = mc.options.keyUp;

		if (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding).getBoundKey().getValue()) == GLFW.GLFW_PRESS) {
			vec3d5 = vec3d5.add(vec3d);
		}

		KeyMapping keyBinding2 = mc.options.keyDown;
		if (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding2).getBoundKey().getValue()) == GLFW.GLFW_PRESS) {
			vec3d5 = vec3d5.subtract(vec3d);
		}

		KeyMapping keyBinding3 = mc.options.keyLeft;
		if (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding3).getBoundKey().getValue()) == GLFW.GLFW_PRESS) {
			vec3d5 = vec3d5.add(vec3d3);
		}

		KeyMapping keyBinding4 = mc.options.keyRight;
		if (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding4).getBoundKey().getValue()) == GLFW.GLFW_PRESS) {
			vec3d5 = vec3d5.add(vec3d4);
		}

		KeyMapping keyBinding5 = mc.options.keyJump;
		if (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding5).getBoundKey().getValue()) == GLFW.GLFW_PRESS) {
			vec3d5 = vec3d5.add(0.0, speed.getValue(), 0.0);
		}

		KeyMapping keyBinding6 = mc.options.keyShift;
		if (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding6).getBoundKey().getValue()) == GLFW.GLFW_PRESS) {
			vec3d5 = vec3d5.add(0.0, -speed.getValue(), 0.0);
		}

		KeyMapping keyBinding7 = mc.options.keySprint;
		vec3d5 = vec3d5.normalize().scale(speed.getValue() * (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding7).getBoundKey().getValue()) == GLFW.GLFW_PRESS ? 2 : 1));

		oldPos = pos;
		pos = pos.add(vec3d5);
	}

	@Override
	public void onCameraUpdate(CameraUpdateEvent event) {
		float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

		if (mc.screen != null)
			return;

		event.setX(Mth.lerp(tickDelta, oldPos.x, pos.x));
		event.setY(Mth.lerp(tickDelta, oldPos.y, pos.y));
		event.setZ(Mth.lerp(tickDelta, oldPos.z, pos.z));
	}
}
