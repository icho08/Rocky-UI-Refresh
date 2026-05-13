package dev.i726.rocky.module.modules.client;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.PacketReceiveListener;
import dev.i726.rocky.gui.vape.RockyGui;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import org.lwjgl.glfw.GLFW;

public final class ClickGUI extends Module implements PacketReceiveListener {
	private final BooleanSetting preventClose = new BooleanSetting(EncryptedString.of("Prevent Close"), true)
			.setDescription(EncryptedString.of("For servers with freeze plugins that don't let you open the GUI"));

	public ClickGUI() {
		super(EncryptedString.of("Click GUI"),
                EncryptedString.of("Opens the GUI menu"),
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				CategoryManager.GUI);

		addSettings(preventClose);
	}

	@Override
	public void onEnable() {
		if (Rocky.INSTANCE == null || mc == null || mc.getWindow() == null) {
			this.setEnabledStatus(false);
			return;
		}

		eventManager.add(PacketReceiveListener.class, this);
		Rocky.INSTANCE.previousScreen = mc.currentScreen;

		mc.setScreenAndRender(Rocky.INSTANCE.getRockyGui());

		if (mc.currentScreen instanceof InventoryScreen) {
			Rocky.INSTANCE.guiInitialized = true;
		}

		super.onEnable();
	}

	@Override
	public void onDisable() {
		if (Rocky.INSTANCE == null || mc == null) return;

		eventManager.remove(PacketReceiveListener.class, this);

		if (mc.currentScreen instanceof RockyGui) {
			mc.setScreenAndRender(Rocky.INSTANCE.previousScreen);
		} else if (mc.currentScreen instanceof InventoryScreen) {
			Rocky.INSTANCE.guiInitialized = false;
		}

		super.onDisable();
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (Rocky.INSTANCE.guiInitialized) {
			if (event.packet instanceof OpenScreenS2CPacket) {
				if (preventClose.getValue())
					event.cancel();
			}
		}
	}
}