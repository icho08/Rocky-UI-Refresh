package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public final class NightVision extends Module implements TickListener {
	private final NumberSetting brightness = new NumberSetting(EncryptedString.of("Brightness"), 1, 10, 5, 1)
			.setDescription(EncryptedString.of("Night vision effect strength"));

	public NightVision() {
		super(EncryptedString.of("Fullbright"),
                EncryptedString.of("Maximum brightness"),
				-1,
				CategoryManager.ESP);
		addSettings(brightness);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		// Remove night vision effect when disabled
		if (mc.player != null && mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
			mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
		}
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;

		// Apply night vision effect with specified brightness
		int amplifier = brightness.getValueInt() - 1; // 0-9 range for amplifier
		StatusEffectInstance nightVision = new StatusEffectInstance(
			StatusEffects.NIGHT_VISION, 
			300, // 15 seconds duration
			amplifier, 
			false, // not ambient
			false, // don't show particles
			false  // don't show icon
		);
		
		mc.player.addStatusEffect(nightVision);
	}
}
