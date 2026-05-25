package dev.i726.rocky.mixin;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.stream.Collectors;

@Mixin(value = FabricLoaderImpl.class, remap = false)
public class FabricLoaderMixin {

    @Inject(method = "getAllMods", at = @At("RETURN"), cancellable = true, remap = false)
    private void hideRocky(CallbackInfoReturnable<Collection<ModContainer>> cir) {
        Collection<ModContainer> filtered = cir.getReturnValue().stream()
                .filter(m -> !m.getMetadata().getId().equals("rocky"))
                .collect(Collectors.toList());
        cir.setReturnValue(filtered);
    }
}
