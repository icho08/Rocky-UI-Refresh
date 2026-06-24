package dev.i726.rocky.mixin;

import dev.i726.rocky.imixin.IKeyBinding;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import static dev.i726.rocky.Rocky.mc;

import com.mojang.blaze3d.platform.InputConstants;

@Mixin(KeyMapping.class)
public abstract class KeyBindingMixin implements IKeyBinding {

	@Shadow
	private InputConstants.Key key;

	@Override
	public boolean isActuallyPressed() {
		int code = key.getValue();
		return InputConstants.isKeyDown(mc.getWindow(), code);
	}

	@Override
	public void resetPressed() {
		setDown(isActuallyPressed());
	}

	@Shadow
	public abstract void setDown(boolean pressed);
}
