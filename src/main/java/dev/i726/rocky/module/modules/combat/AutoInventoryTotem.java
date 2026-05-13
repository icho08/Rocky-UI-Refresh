package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

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
		if (mc.currentScreen instanceof InventoryScreen) {
			mc.currentScreen.close();
		}
		super.onDisable();
	}

	private boolean needsTotems() {
		boolean offhandEmpty = mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING;
		boolean mainHandEmpty = !mainHand.getValue() || 
			(mc.player.getMainHandStack().isEmpty() || 
			(forceTotem.getValue() && mc.player.getMainHandStack().getItem() != Items.TOTEM_OF_UNDYING));
		
		return offhandEmpty || (mainHand.getValue() && mainHandEmpty);
	}

	private int getTotemCount() {
		return InventoryUtils.countItemExceptHotbar(item -> item == Items.TOTEM_OF_UNDYING);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null) return;

		needsTotem = needsTotems() && getTotemCount() > 0;

		// Auto open inventory if needed
		if (needsTotem && autoOpen.getValue() && !(mc.currentScreen instanceof InventoryScreen)) {
			mc.setScreen(new InventoryScreen(mc.player));
			return;
		}

		// Only work when inventory is open
		if (!(mc.currentScreen instanceof InventoryScreen)) {
			delayClock = 0;
			return;
		}

		// Don't auto-close if player manually opened inventory
		if (!autoOpen.getValue() && mc.currentScreen instanceof InventoryScreen) {
			// Still equip totems but don't auto-close
		}

		// Delay handling
		if (delayClock < delay.getValue()) {
			delayClock++;
			return;
		}

		// Equip totem in offhand first (priority)
		if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
			int totemSlot = InventoryUtils.findTotemSlot();
			if (totemSlot != -1) {
				mc.interactionManager.clickSlot(
					((InventoryScreen) mc.currentScreen).getScreenHandler().syncId,
					totemSlot, 40, SlotActionType.SWAP, mc.player
				);
				delayClock = 0;
				return;
			}
		}

		// Equip totem in main hand if enabled
		if (mainHand.getValue()) {
			boolean shouldEquipMain = mc.player.getMainHandStack().isEmpty() || 
				(forceTotem.getValue() && mc.player.getMainHandStack().getItem() != Items.TOTEM_OF_UNDYING);
			
			if (shouldEquipMain) {
				int totemSlot = InventoryUtils.findTotemSlot();
				if (totemSlot != -1) {
					mc.interactionManager.clickSlot(
						((InventoryScreen) mc.currentScreen).getScreenHandler().syncId,
						totemSlot, mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player
					);
					delayClock = 0;
					return;
				}
			}
		}

		delayClock = 0;
	}
}
