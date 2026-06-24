package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.render.NoParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleManagerMixin {

    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private <T extends ParticleOptions> void onAddParticle(T effect,
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

    private boolean isCombatParticle(ParticleOptions effect) {
        return effect.getType() == ParticleTypes.CRIT
                || effect.getType() == ParticleTypes.SWEEP_ATTACK
                || effect.getType() == ParticleTypes.DAMAGE_INDICATOR
                || effect.getType() == ParticleTypes.EXPLOSION
                || effect.getType() == ParticleTypes.EXPLOSION_EMITTER;
    }
}
