package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public final class AutoPot extends Module implements TickListener {
	public enum PotionType {
		Health, Strength, Speed, FireResistance
	}
	
	public enum State {
		IDLE, SWITCHING, READY_TO_THROW, THROWING
	}

	private final ModeSetting<PotionType> potionType = new ModeSetting<>(EncryptedString.of("Potion Type"), PotionType.Health, PotionType.class)
			.setDescription(EncryptedString.of("Type of potion to use"));
	private final NumberSetting healthPercent = new NumberSetting(EncryptedString.of("Health %"), 10, 95, 50, 5);
	private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0, 10, 2, 1);
	private final BooleanSetting switchBack = new BooleanSetting(EncryptedString.of("Switch Back"), true);
	private final BooleanSetting lookDown = new BooleanSetting(EncryptedString.of("Look Down"), true);

	private State currentState = State.IDLE;
	private int delayClock = 0;
	private int previousSlot = -1;
	private float previousPitch = -1;

	public AutoPot() {
		super(EncryptedString.of("Auto Pot"),
                EncryptedString.of("Automatically throws potions"),
				-1,
				CategoryManager.INVENTORY);

		addSettings(potionType, healthPercent, delay, switchBack, lookDown);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		reset();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		restoreState();
		super.onDisable();
	}

	private void reset() {
		currentState = State.IDLE;
		delayClock = 0;
		previousSlot = -1;
		previousPitch = -1;
	}

	private void restoreState() {
		if (previousSlot != -1) {
			InventoryUtils.setInvSlot(previousSlot);
			previousSlot = -1;
		}
		if (previousPitch != -1) {
			mc.player.setPitch(previousPitch);
			previousPitch = -1;
		}
	}

	private boolean shouldUsePot() {
		if (mc.player == null) return false;
		
		float healthPercent = (mc.player.getHealth() / mc.player.getMaxHealth()) * 100;
		
		return switch (potionType.getMode()) {
			case Health -> healthPercent <= this.healthPercent.getValue();
			case Strength -> !mc.player.hasStatusEffect(StatusEffects.STRENGTH);
			case Speed -> !mc.player.hasStatusEffect(StatusEffects.SPEED);
			case FireResistance -> !mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
		};
	}

	private int findPotionSlot() {
		return switch (potionType.getMode()) {
			case Health -> InventoryUtils.findSplash(StatusEffects.INSTANT_HEALTH.value(), 1, 1);
			case Strength -> InventoryUtils.findSplash(StatusEffects.STRENGTH.value(), 1, 1);
			case Speed -> InventoryUtils.findSplash(StatusEffects.SPEED.value(), 1, 1);
			case FireResistance -> InventoryUtils.findSplash(StatusEffects.FIRE_RESISTANCE.value(), 1, 1);
		};
	}

	private boolean hasCorrectPotion() {
		return switch (potionType.getMode()) {
			case Health -> InventoryUtils.isThatSplash(StatusEffects.INSTANT_HEALTH.value(), 1, 1, mc.player.getMainHandStack());
			case Strength -> InventoryUtils.isThatSplash(StatusEffects.STRENGTH.value(), 1, 1, mc.player.getMainHandStack());
			case Speed -> InventoryUtils.isThatSplash(StatusEffects.SPEED.value(), 1, 1, mc.player.getMainHandStack());
			case FireResistance -> InventoryUtils.isThatSplash(StatusEffects.FIRE_RESISTANCE.value(), 1, 1, mc.player.getMainHandStack());
		};
	}

	@Override
	public void onTick() {
		if (mc.currentScreen != null || mc.player == null) return;

		if (shouldUsePot()) {
			switch (currentState) {
				case IDLE -> {
					if (!hasCorrectPotion()) {
						currentState = State.SWITCHING;
						if (switchBack.getValue()) previousSlot = mc.player.getInventory().getSelectedSlot();
						if (lookDown.getValue()) previousPitch = mc.player.getPitch();
					} else {
						currentState = State.READY_TO_THROW;
					}
				}
				
				case SWITCHING -> {
					if (delayClock < delay.getValue()) {
						delayClock++;
						return;
					}
					
					int potSlot = findPotionSlot();
					if (potSlot != -1) {
						InventoryUtils.setInvSlot(potSlot);
						currentState = State.READY_TO_THROW;
					} else {
						currentState = State.IDLE; // No potion found
					}
					delayClock = 0;
				}
				
				case READY_TO_THROW -> {
					if (hasCorrectPotion()) {
						if (lookDown.getValue()) mc.player.setPitch(90F);
						currentState = State.THROWING;
					} else {
						currentState = State.SWITCHING;
					}
				}
				
				case THROWING -> {
					if (delayClock < delay.getValue()) {
						delayClock++;
						return;
					}
					
					ActionResult result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
					if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
					
					currentState = State.IDLE;
					delayClock = 0;
				}
			}
		} else if (currentState != State.IDLE) {
			// Restore state when no longer needed
			restoreState();
			currentState = State.IDLE;
		}
	}
}