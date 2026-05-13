package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.*;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

public final class AutoDoubleHand extends Module implements HudListener {
	private final NumberSetting healthThreshold = new NumberSetting(EncryptedString.of("Health"), 1, 20, 8, 1)
			.setDescription(EncryptedString.of("Health to switch to totem"));
	private final NumberSetting playerRange = new NumberSetting(EncryptedString.of("Player Range"), 1, 15, 8, 1)
			.setDescription(EncryptedString.of("Range to check for players"));
	private final BooleanSetting onPop = new BooleanSetting(EncryptedString.of("On Pop"), true)
			.setDescription(EncryptedString.of("Switch to totem when you pop"));
	private final BooleanSetting predictDamage = new BooleanSetting(EncryptedString.of("Predict Damage"), true)
			.setDescription(EncryptedString.of("Predict crystal damage"));
	private final BooleanSetting checkPlayers = new BooleanSetting(EncryptedString.of("Check Players"), true)
			.setDescription(EncryptedString.of("Only activate when players nearby"));

	private boolean wasTotemming = false;
	private float lastHealth = 20f;

	public AutoDoubleHand() {
		super(EncryptedString.of("Double Hand"),
                EncryptedString.of("Manages both hands automatically"),
				-1,
				CategoryManager.INVENTORY);

		addSettings(healthThreshold, playerRange, onPop, predictDamage, checkPlayers);
	}

	@Override
	public void onEnable() {
		eventManager.add(HudListener.class, this);
		wasTotemming = false;
		lastHealth = mc.player != null ? mc.player.getHealth() : 20f;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		super.onDisable();
	}

	private boolean shouldUseTotem() {
		if (mc.player == null || mc.world == null) return false;

		// Check if already holding totem
		if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
			return false;
		}

		// Check for totem pop
		if (onPop.getValue() && mc.player.getHealth() < lastHealth - 5) {
			return true;
		}

		// Check health threshold
		if (mc.player.getHealth() <= healthThreshold.getValue()) {
			return true;
		}

		// Check for nearby players
		if (checkPlayers.getValue()) {
			boolean hasNearbyPlayers = mc.world.getPlayers().stream()
				.anyMatch(player -> player != mc.player && 
					!player.isDead() && 
					mc.player.distanceTo(player) <= playerRange.getValue());
			
			if (!hasNearbyPlayers) return false;
		}

		// Predict crystal damage
		if (predictDamage.getValue()) {
			for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(EndCrystalEntity.class, 
				mc.player.getBoundingBox().expand(6), entity -> true)) {
				
				double distance = mc.player.distanceTo(crystal);
				if (distance <= 6) {
					// Simple damage prediction - if crystal is close and we're low health
					double estimatedDamage = Math.max(0, 12 - distance * 2);
					if (mc.player.getHealth() - estimatedDamage <= 4) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private void switchToTotem() {
		int totemSlot = InventoryUtils.findTotemSlot();
		if (totemSlot != -1) {
			// Simple offhand switch using existing methods
			InventoryUtils.setInvSlot(totemSlot);
			wasTotemming = true;
		}
	}

	private void switchBack() {
		// Find shield or other useful item
		for (int i = 9; i < 36; i++) {
			if (mc.player.getInventory().getStack(i).getItem() == Items.SHIELD) {
				InventoryUtils.setInvSlot(i);
				wasTotemming = false;
				return;
			}
		}
		wasTotemming = false;
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (mc.player == null) return;

		// Update last health for pop detection
		lastHealth = mc.player.getHealth();

		if (shouldUseTotem()) {
			if (!wasTotemming) {
				switchToTotem();
			}
		} else if (wasTotemming && mc.player.getHealth() > healthThreshold.getValue() + 4) {
			// Switch back when health is good and safe
			switchBack();
		}
	}
}
