package dev.i726.rocky.mixin;

import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    // Time override is handled in WorldTimeMixin (targeting World directly,
    // since getTimeOfDay() is declared on World, not ClientWorld)
}
