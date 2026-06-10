package dev.i726.rocky.mixin;

import dev.i726.rocky.imixin.IPlayerRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateMixin implements IPlayerRenderState {

    @Unique
    private UUID rocky$entityUuid = null;

    @Override
    public UUID rocky$getEntityUuid() {
        return rocky$entityUuid;
    }

    @Override
    public void rocky$setEntityUuid(UUID uuid) {
        this.rocky$entityUuid = uuid;
    }
}
