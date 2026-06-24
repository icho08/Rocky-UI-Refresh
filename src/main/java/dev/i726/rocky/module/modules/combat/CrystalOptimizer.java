package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.PacketSendListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import dev.i726.rocky.utils.WorldUtils;
import java.util.Random;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CrystalOptimizer extends Module implements PacketSendListener {
        private final NumberSetting chance = new NumberSetting(EncryptedString.of("Kill Chance %"), 0, 100, 100, 1)
                        .setDescription(EncryptedString.of("Chance to client-kill the crystal — lower it to look like server lag"));
        private final MinMaxSetting killDelay = new MinMaxSetting(EncryptedString.of("Kill Delay"), 0, 200, 1, 0, 0)
                        .setDescription(EncryptedString.of("Random ms wait before client-kill — keeps cadence noisy"));
        private final BooleanSetting requireWeapon = new BooleanSetting(EncryptedString.of("Require Tool"), true)
                        .setDescription(EncryptedString.of("Only kill if you're holding a weapon/tool (vanilla insta-break behaviour)"));
        private final BooleanSetting requireStrength = new BooleanSetting(EncryptedString.of("Strength Bypass"), true)
                        .setDescription(EncryptedString.of("Skip the weakness check while you have strength override"));
        private final NumberSetting maxReach = new NumberSetting(EncryptedString.of("Max Reach"), 3.0, 8.0, 5.0, 0.1)
                        .setDescription(EncryptedString.of("Don't kill crystals further than this away (vanilla entity-reach is 3.0–6.0)"));

        private final TimerUtils delayTimer = new TimerUtils();
        private final Random random = new Random();
        private int pendingDelay = 0;

        public CrystalOptimizer() {
                super(EncryptedString.of("Crystal Optimizer"),
                EncryptedString.of("Optimizes crystal PvP"),
                                -1,
                                CategoryManager.CRYSTAL);
                addSettings(chance, killDelay, requireWeapon, requireStrength, maxReach);
        }

        @Override
        public void onEnable() {
                pendingDelay = 0;
                delayTimer.reset();
                eventManager.add(PacketSendListener.class, this);
                super.onEnable();
        }

        @Override
        public void onDisable() {
                eventManager.remove(PacketSendListener.class, this);
                super.onDisable();
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
                if (!(event.packet instanceof ServerboundInteractPacket interactPacket)) return;

                interactPacket.dispatch(new ServerboundInteractPacket.Handler() {
                        @Override
                        public void onInteraction(InteractionHand hand) {}

                        @Override
                        public void onInteraction(InteractionHand hand, Vec3 pos) {}

                        @Override
                        public void onAttack() {
                                if (mc.hitResult == null) return;
                                if (mc.hitResult.getType() != HitResult.Type.ENTITY) return;
                                if (!(mc.hitResult instanceof EntityHitResult hit)) return;
                                if (!(hit.getEntity() instanceof EndCrystal)) return;

                                // Reach gate
                                if (mc.player.distanceTo(hit.getEntity()) > maxReach.getValue()) return;

                                // Original "tool/strength/no-weakness" gate
                                MobEffectInstance weakness = mc.player.getEffect(MobEffects.WEAKNESS);
                                MobEffectInstance strength = mc.player.getEffect(MobEffects.STRENGTH);
                                boolean toolOk  = !requireWeapon.getValue() || WorldUtils.isTool(mc.player.getMainHandItem());
                                boolean strOver = requireStrength.getValue() && weakness != null && strength != null && strength.getAmplifier() > weakness.getAmplifier();
                                if (!(weakness == null || strOver || toolOk)) return;

                                // Random kill chance
                                int c = chance.getValueInt();
                                if (c < 100 && random.nextInt(100) >= c) return;

                                // Random pre-kill delay so insta-kills aren't perfectly tick-aligned
                                if (killDelay.getMaxValue() > 0) {
                                        if (pendingDelay <= 0) {
                                                pendingDelay = killDelay.getRandomValueInt();
                                                delayTimer.reset();
                                                return;
                                        }
                                        if (!delayTimer.delay(pendingDelay)) return;
                                        pendingDelay = 0;
                                }

                                hit.getEntity().discard();
                                hit.getEntity().setRemoved(Entity.RemovalReason.KILLED);
                                hit.getEntity().onClientRemoval();
                        }
                });
        }
}
