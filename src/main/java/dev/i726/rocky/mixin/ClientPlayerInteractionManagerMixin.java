package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.event.events.PostAttackListener;
import dev.i726.rocky.module.modules.client.Friends;
import dev.i726.rocky.module.modules.misc.NoBreakDelay;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
        @Shadow
        private int blockBreakingCooldown;

        @Redirect(method = "updateBlockBreakingProgress",
                        at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;blockBreakingCooldown:I", opcode = Opcodes.GETFIELD, ordinal = 0))
        public int updateBlockBreakingProgress(ClientPlayerInteractionManager clientPlayerInteractionManager) {
                int cooldown = this.blockBreakingCooldown;
                return Rocky.INSTANCE != null && Rocky.INSTANCE.getModuleManager().getModule(NoBreakDelay.class).isEnabled() ? 0 : cooldown;
        }

        @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
        private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
                // ── Global friend protection ────────────────────────────────────────
                // Blocks EVERY module from attacking a friend when the Friends module
                // has "Anti Attack" enabled — no need to add per-module checks.
                if (Rocky.INSTANCE != null && target instanceof PlayerEntity pt) {
                        try {
                                Friends friendsMod = Rocky.INSTANCE.getModuleManager().getModule(Friends.class);
                                if (friendsMod != null && friendsMod.antiAttack.getValue()
                                        && Rocky.INSTANCE.getFriendManager().isFriend(pt)) {
                                        ci.cancel();
                                        return;
                                }
                        } catch (Exception ignored) {}
                }

                // ── Normal attack event ─────────────────────────────────────────────
                AttackListener.AttackEvent event = new AttackListener.AttackEvent(target);
                EventManager.fire(event);
                if (event.isCancelled()) ci.cancel();
        }

        @Inject(method = "attackEntity", at = @At("RETURN"))
        private void onAttackEntityPost(PlayerEntity player, Entity target, CallbackInfo ci) {
                EventManager.fire(new PostAttackListener.PostAttackEvent(target));
        }
}
