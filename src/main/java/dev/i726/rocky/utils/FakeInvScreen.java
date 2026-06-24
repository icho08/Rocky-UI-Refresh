package dev.i726.rocky.utils;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

public class FakeInvScreen extends InventoryScreen {
	public FakeInvScreen(Player player) {
		super(player);
	}

	@Override
	protected void slotClicked(Slot slot, int slotId, int button, ContainerInput actionType) {
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean down) {
		return false;
	}
}
