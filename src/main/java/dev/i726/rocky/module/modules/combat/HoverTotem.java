package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.mixin.HandledScreenMixin;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

public final class HoverTotem extends Module implements TickListener {
	private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0, 10, 1, 1)
			.setDescription(EncryptedString.of("Delay between totem swaps"));
	private final BooleanSetting mainHand = new BooleanSetting(EncryptedString.of("Main Hand"), true)
			.setDescription(EncryptedString.of("Also equip totem in main hand"));
	private final NumberSetting totemSlot = new NumberSetting(EncryptedString.of("Main Hand Slot"), 1, 9, 9, 1)
			.setDescription(EncryptedString.of("Hotbar slot for main hand totem"));
	private final BooleanSetting autoSwitch = new BooleanSetting(EncryptedString.of("Auto Switch"), true)
			.setDescription(EncryptedString.of("Auto switch to totem slot"));
	private final BooleanSetting onlyEmpty = new BooleanSetting(EncryptedString.of("Only Empty Slots"), false)
			.setDescription(EncryptedString.of("Only fill empty slots, don't replace items"));

	private int delayClock = 0;

	public HoverTotem() {
		super(EncryptedString.of("Hover Totem"),
                EncryptedString.of("Holds totem while hovering"),
				-1,
				CategoryManager.CRYSTAL);
		addSettings(delay, mainHand, totemSlot, autoSwitch, onlyEmpty);
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
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (!(mc.screen instanceof InventoryScreen inv) || mc.player == null) {
			delayClock = 0;
			return;
		}

		Slot hoveredSlot = ((HandledScreenMixin) inv).getHoveredSlot();
		if (hoveredSlot == null || hoveredSlot.getItem().getItem() != Items.TOTEM_OF_UNDYING) {
			return;
		}

		// Auto switch to totem slot
		if (autoSwitch.getValue()) {
			mc.player.getInventory().setSelectedSlot(totemSlot.getValueInt() - 1);
		}

		// Delay handling
		if (delayClock > 0) {
			delayClock--;
			return;
		}

		int slotIndex = hoveredSlot.getContainerSlot();
		if (slotIndex > 35) return; // Invalid slot

		// Priority 1: Offhand (most important for survival)
		if (!mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			mc.gameMode.handleInventoryMouseClick(inv.getMenu().containerId, slotIndex, 40, ContainerInput.SWAP, mc.player);
			delayClock = delay.getValueInt();
			return;
		}

		// Priority 2: Main hand slot if enabled
		if (mainHand.getValue()) {
			int targetSlot = totemSlot.getValueInt() - 1;
			boolean slotEmpty = mc.player.getInventory().getItem(targetSlot).isEmpty();
			boolean hasTotem = mc.player.getInventory().getItem(targetSlot).getItem() == Items.TOTEM_OF_UNDYING;
			
			if (!hasTotem && (!onlyEmpty.getValue() || slotEmpty)) {
				mc.gameMode.handleInventoryMouseClick(inv.getMenu().containerId, slotIndex, targetSlot, ContainerInput.SWAP, mc.player);
				delayClock = delay.getValueInt();
			}
		}
	}
}
