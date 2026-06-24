package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

public final class AutoInventoryTotem extends Module implements TickListener {
	private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0, 10, 1, 1);
	private final BooleanSetting mainHand = new BooleanSetting(EncryptedString.of("Main Hand"), false)
			.setDescription(EncryptedString.of("Also put totem in main hand"));
	private final BooleanSetting autoOpen = new BooleanSetting(EncryptedString.of("Auto Open"), true)
			.setDescription(EncryptedString.of("Automatically opens inventory"));
	private final BooleanSetting forceTotem = new BooleanSetting(EncryptedString.of("Force Replace"), false)
			.setDescription(EncryptedString.of("Replace items to place totems"));

	private int delayClock = 0;
	private boolean needsTotem = false;

	public AutoInventoryTotem() {
		super(EncryptedString.of("Auto Totem"),
                EncryptedString.of("Keeps totem in offhand"),
				-1,
				CategoryManager.INVENTORY);
		addSettings(delay, mainHand, autoOpen, forceTotem);
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
		if (mc.screen instanceof InventoryScreen) {
			mc.screen.onClose();
		}
		super.onDisable();
	}

	private boolean needsTotems() {
		boolean offhandEmpty = mc.player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING;
		boolean mainHandEmpty = !mainHand.getValue() || 
			(mc.player.getMainHandItem().isEmpty() || 
			(forceTotem.getValue() && mc.player.getMainHandItem().getItem() != Items.TOTEM_OF_UNDYING));
		
		return offhandEmpty || (mainHand.getValue() && mainHandEmpty);
	}

	private int getTotemCount() {
		return InventoryUtils.countItemExceptHotbar(item -> item == Items.TOTEM_OF_UNDYING);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) return;

		needsTotem = needsTotems() && getTotemCount() > 0;

		// Auto open inventory if needed
		if (needsTotem && autoOpen.getValue() && !(mc.screen instanceof InventoryScreen)) {
			mc.setScreen(new InventoryScreen(mc.player));
			return;
		}

		// Only work when inventory is open
		if (!(mc.screen instanceof InventoryScreen)) {
			delayClock = 0;
			return;
		}

		// Don't auto-close if player manually opened inventory
		if (!autoOpen.getValue() && mc.screen instanceof InventoryScreen) {
			// Still equip totems but don't auto-close
		}

		// Delay handling
		if (delayClock < delay.getValue()) {
			delayClock++;
			return;
		}

		// Equip totem in offhand first (priority)
		if (mc.player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING) {
			int totemSlot = InventoryUtils.findTotemSlot();
			if (totemSlot != -1) {
				mc.gameMode.handleInventoryMouseClick(
					((InventoryScreen) mc.screen).getMenu().containerId,
					totemSlot, 40, ContainerInput.SWAP, mc.player
				);
				delayClock = 0;
				return;
			}
		}

		// Equip totem in main hand if enabled
		if (mainHand.getValue()) {
			boolean shouldEquipMain = mc.player.getMainHandItem().isEmpty() || 
				(forceTotem.getValue() && mc.player.getMainHandItem().getItem() != Items.TOTEM_OF_UNDYING);
			
			if (shouldEquipMain) {
				int totemSlot = InventoryUtils.findTotemSlot();
				if (totemSlot != -1) {
					mc.gameMode.handleInventoryMouseClick(
						((InventoryScreen) mc.screen).getMenu().containerId,
						totemSlot, mc.player.getInventory().getSelectedSlot(), ContainerInput.SWAP, mc.player
					);
					delayClock = 0;
					return;
				}
			}
		}

		delayClock = 0;
	}
}
