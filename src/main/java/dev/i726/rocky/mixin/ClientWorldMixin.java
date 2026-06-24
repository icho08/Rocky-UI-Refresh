package dev.i726.rocky.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientLevel.class)
public abstract class ClientWorldMixin {
    // Time override is handled in WorldTimeMixin (targeting World directly,
    // since getTimeOfDay() is declared on World, not ClientWorld)
}
