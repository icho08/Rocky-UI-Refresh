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
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public final class AutoPotRefill extends Module implements TickListener {
	public enum PotionType {
		Health, Strength, Speed, FireResistance
	}

	private final ModeSetting<PotionType> potionType = new ModeSetting<>(EncryptedString.of("Potion Type"), PotionType.Health, PotionType.class)
			.setDescription(EncryptedString.of("Type of potion to refill"));
	private final NumberSetting minPotions = new NumberSetting(EncryptedString.of("Min Potions"), 1, 9, 3, 1)
			.setDescription(EncryptedString.of("Minimum potions to keep in hotbar"));
	private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0, 10, 1, 1);
	private final BooleanSetting autoOpen = new BooleanSetting(EncryptedString.of("Auto Open"), true)
			.setDescription(EncryptedString.of("Automatically open inventory"));

	private int delayClock = 0;

	public AutoPotRefill() {
		super(EncryptedString.of("Pot Refill"),
                EncryptedString.of("Refills potions from inventory"),
				-1,
				CategoryManager.INVENTORY);
		addSettings(potionType, minPotions, delay, autoOpen);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		delayClock = 0;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		if (mc.currentScreen instanceof InventoryScreen) {
			mc.currentScreen.close();
		}
		super.onDisable();
	}

	private int countPotionsInHotbar() {
		int count = 0;
		for (int i = 0; i < 9; i++) {
			if (isPotionOfType(mc.player.getInventory().getStack(i))) {
				count++;
			}
		}
		return count;
	}

	private boolean isPotionOfType(net.minecraft.item.ItemStack stack) {
		return switch (potionType.getMode()) {
			case Health -> InventoryUtils.isThatSplash(StatusEffects.INSTANT_HEALTH.value(), 1, 1, stack);
			case Strength -> InventoryUtils.isThatSplash(StatusEffects.STRENGTH.value(), 1, 1, stack);
			case Speed -> InventoryUtils.isThatSplash(StatusEffects.SPEED.value(), 1, 1, stack);
			case FireResistance -> InventoryUtils.isThatSplash(StatusEffects.FIRE_RESISTANCE.value(), 1, 1, stack);
		};
	}

	private int findPotionInInventory() {
		return switch (potionType.getMode()) {
			case Health -> InventoryUtils.findPot(StatusEffects.INSTANT_HEALTH.value(), 1, 1);
			case Strength -> InventoryUtils.findPot(StatusEffects.STRENGTH.value(), 1, 1);
			case Speed -> InventoryUtils.findPot(StatusEffects.SPEED.value(), 1, 1);
			case FireResistance -> InventoryUtils.findPot(StatusEffects.FIRE_RESISTANCE.value(), 1, 1);
		};
	}

	private int findEmptyHotbarSlot() {
		for (int i = 0; i < 9; i++) {
			if (mc.player.getInventory().getStack(i).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	private boolean needsRefill() {
		return countPotionsInHotbar() < minPotions.getValue() && findPotionInInventory() != -1;
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;

		boolean needsRefill = needsRefill();

		// Auto open inventory if needed
		if (needsRefill && autoOpen.getValue() && !(mc.currentScreen instanceof InventoryScreen)) {
			mc.setScreen(new InventoryScreen(mc.player));
			return;
		}

		// Only work when inventory is open
		if (!(mc.currentScreen instanceof InventoryScreen inventoryScreen)) {
			delayClock = 0;
			return;
		}

		// Don't auto-close if player manually opened inventory
		if (!autoOpen.getValue() && mc.currentScreen instanceof InventoryScreen) {
			// Still refill potions but don't auto-close
		}

		// Check if we need to refill
		if (!needsRefill) {
			delayClock = 0;
			return;
		}

		// Delay handling
		if (delayClock < delay.getValue()) {
			delayClock++;
			return;
		}

		// Find empty slot in hotbar
		int emptySlot = findEmptyHotbarSlot();
		if (emptySlot == -1) return;

		// Find potion in inventory
		int potionSlot = findPotionInInventory();
		if (potionSlot == -1) return;

		// Move potion to hotbar
		mc.interactionManager.clickSlot(
			inventoryScreen.getScreenHandler().syncId,
			potionSlot,
			emptySlot,
			SlotActionType.SWAP,
			mc.player
		);

		delayClock = 0;
	}
}
