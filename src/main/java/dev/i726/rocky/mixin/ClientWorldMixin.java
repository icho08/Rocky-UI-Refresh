package dev.i726.rocky.mixin;

import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    // Weather overrides are handled via reflection in TimeChanger.onTick()
    // because getRainGradient / getThunderGradient were renamed in 1.21.10.
}
