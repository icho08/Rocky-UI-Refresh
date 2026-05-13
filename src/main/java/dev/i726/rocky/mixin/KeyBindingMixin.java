package dev.i726.rocky.mixin;

import dev.i726.rocky.imixin.IKeyBinding;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import static dev.i726.rocky.Rocky.mc;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin implements IKeyBinding {

	@Shadow
	private InputUtil.Key boundKey;

	@Override
	public boolean isActuallyPressed() {
		int code = boundKey.getCode();
		return InputUtil.isKeyPressed(mc.getWindow(), code);
	}

	@Override
	public void resetPressed() {
		setPressed(isActuallyPressed());
	}

	@Shadow
	public abstract void setPressed(boolean pressed);
}
