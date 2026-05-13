package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.combat.Hitboxes;
import dev.i726.rocky.module.modules.render.PlayerESP;
import dev.i726.rocky.module.modules.render.Chams;
import dev.i726.rocky.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getTargetingMargin", at = @At("HEAD"), cancellable = true)
    private void onGetTargetingMargin(CallbackInfoReturnable<Float> cir) {
        Hitboxes hitboxes = Rocky.INSTANCE.getModuleManager().getModule(Hitboxes.class);
        if (hitboxes != null && hitboxes.isEnabled()) {
            cir.setReturnValue(hitboxes.getSize());
        }
    }

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void onIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (Rocky.INSTANCE == null) return;
        
        // Check PlayerESP Glow
        PlayerESP esp = Rocky.INSTANCE.getModuleManager().getModule(PlayerESP.class);
        if (esp != null && esp.isEnabled()) {
            boolean glowEnabled = esp.getSettings().stream()
                .filter(s -> s.getName().toString().equals("Glow"))
                .findFirst()
                .filter(s -> s instanceof BooleanSetting)
                .map(s -> ((BooleanSetting) s).getValue())
                .orElse(false);
            
            if (glowEnabled && (Object)this instanceof PlayerEntity && (Object)this != MinecraftClient.getInstance().player) {
                cir.setReturnValue(true);
                return;
            }
        }

        // Check Chams Glow (if user wants Chams to also have the glow effect)
        Chams chams = Rocky.INSTANCE.getModuleManager().getModule(Chams.class);
        if (chams != null && chams.isEnabled()) {
            if ((Object)this instanceof PlayerEntity && (Object)this != MinecraftClient.getInstance().player) {
                cir.setReturnValue(true);
            }
        }
    }
}
