package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.MathUtils;
import dev.i726.rocky.utils.TimerUtils;

public final class AutoJumpReset extends Module implements TickListener {
	private final NumberSetting chance = new NumberSetting(EncryptedString.of("Chance"), 0, 100, 85, 1)
			.setDescription(EncryptedString.of("Chance to jump when hit"));
	private final NumberSetting minDelay = new NumberSetting(EncryptedString.of("Min Delay"), 0, 10, 1, 1)
			.setDescription(EncryptedString.of("Minimum ticks to wait before jumping"));
	private final NumberSetting maxDelay = new NumberSetting(EncryptedString.of("Max Delay"), 0, 10, 3, 1)
			.setDescription(EncryptedString.of("Maximum ticks to wait before jumping"));
	private final BooleanSetting onlyPlayers = new BooleanSetting(EncryptedString.of("Only Players"), true)
			.setDescription(EncryptedString.of("Only jump when hit by players"));
	private final BooleanSetting randomTiming = new BooleanSetting(EncryptedString.of("Random Timing"), true)
			.setDescription(EncryptedString.of("Randomize jump timing for bypass"));

	private final TimerUtils jumpTimer = new TimerUtils();
	private boolean shouldJump = false;
	private int jumpDelay = 0;
	private int lastHurtTime = 0;

	public AutoJumpReset() {
		super(EncryptedString.of("Jump Reset"),
                EncryptedString.of("Automatically jump resets"),
				-1,
				CategoryManager.PVP);
		addSettings(chance, minDelay, maxDelay, onlyPlayers, randomTiming);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		shouldJump = false;
		jumpDelay = 0;
		lastHurtTime = 0;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.screen != null) return;

		// Don't jump while using items (eating, blocking, etc.)
		if (mc.player.isUsingItem()) return;

		// Detect new hit
		if (mc.player.hurtTime > lastHurtTime && mc.player.hurtTime == mc.player.hurtDuration) {
			// Check if we should jump based on chance
			if (MathUtils.randomInt(1, 100) <= chance.getValueInt()) {
				// Check if hit by player (if setting enabled)
				if (onlyPlayers.getValue() && mc.player.getLastHurtByMob() != null && 
					!(mc.player.getLastHurtByMob() instanceof net.minecraft.world.entity.player.Player)) {
					return;
				}

				// Set up jump with random timing for bypass
				shouldJump = true;
				if (randomTiming.getValue()) {
					jumpDelay = MathUtils.randomInt(minDelay.getValueInt(), maxDelay.getValueInt());
				} else {
					jumpDelay = minDelay.getValueInt();
				}
				jumpTimer.reset();
			}
		}

		lastHurtTime = mc.player.hurtTime;

		// Execute jump with delay
		if (shouldJump && mc.player.onGround() && jumpTimer.delay(jumpDelay * 50)) { // Convert ticks to ms
			mc.player.jumpFromGround();
			shouldJump = false;
		}

		// Reset if we're no longer on ground or hurt time expired
		if (!mc.player.onGround() || mc.player.hurtTime == 0) {
			shouldJump = false;
		}
	}
}
