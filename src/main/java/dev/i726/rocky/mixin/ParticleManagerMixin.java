package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.render.NoParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private <T extends ParticleEffect> void onAddParticle(T effect,
                                                           double x, double y, double z,
                                                           double vx, double vy, double vz,
                                                           CallbackInfoReturnable<Particle> cir) {
        if (Rocky.INSTANCE == null) return;
        NoParticles mod = Rocky.INSTANCE.getModuleManager().getModule(NoParticles.class);
        if (mod == null || !mod.isEnabled()) return;

        if (mod.isAll()) {
            cir.setReturnValue(null);
            return;
        }

        if (isCombatParticle(effect)) {
            cir.setReturnValue(null);
        }
    }

    private boolean isCombatParticle(ParticleEffect effect) {
        return effect.getType() == ParticleTypes.CRIT
                || effect.getType() == ParticleTypes.ENHANCED_CRIT
                || effect.getType() == ParticleTypes.SWEEP_ATTACK
                || effect.getType() == ParticleTypes.DAMAGE_INDICATOR
                || effect.getType() == ParticleTypes.EXPLOSION
                || effect.getType() == ParticleTypes.EXPLOSION_EMITTER;
    }
}
