package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.item.AxeItem;
import net.minecraft.item.TridentItem;
// import net.minecraft.world.item.SwordItem;
import net.minecraft.util.hit.HitResult;

import java.util.Random;

public final class NoMissDelay extends Module implements AttackListener {
        private final BooleanSetting onlyWeapon = new BooleanSetting(EncryptedString.of("Only weapon"), true);
        private final BooleanSetting air = new BooleanSetting(EncryptedString.of("Air"), true)
                        .setDescription(EncryptedString.of("Whether to stop hits directed to the air"));
        private final BooleanSetting blocks = new BooleanSetting(EncryptedString.of("Blocks"), false)
                        .setDescription(EncryptedString.of("Whether to stop hits directed to blocks"));
        private final NumberSetting passChance = new NumberSetting(EncryptedString.of("Pass-Through %"), 0, 100, 0, 1)
                        .setDescription(EncryptedString.of("Chance to let a miss go through anyway (0 = perfect cancel, higher = more legit)"));
        private final NumberSetting cooldown = new NumberSetting(EncryptedString.of("Post-Cancel CD"), 0, 500, 60, 5)
                        .setDescription(EncryptedString.of("ms to suppress further hits after a miss is cancelled (avoids burst-cancel pattern)"));
        private final BooleanSetting requireMoving = new BooleanSetting(EncryptedString.of("Require Moving"), false)
                        .setDescription(EncryptedString.of("Only cancel misses while you're actually moving (more contextual)"));

        private final TimerUtils cdTimer = new TimerUtils();
        private final Random random = new Random();

        public NoMissDelay() {
                super(EncryptedString.of("No Miss Delay"),
                EncryptedString.of("Removes attack miss cooldown"),
                                -1,
                                CategoryManager.PVP);
                addSettings(onlyWeapon, air, blocks, passChance, cooldown, requireMoving);
        }

        @Override
        public void onEnable() {
                cdTimer.reset();
                eventManager.add(AttackListener.class, this);
                super.onEnable();
        }

        @Override
        public void onDisable() {
                eventManager.remove(AttackListener.class, this);
                super.onDisable();
        }

        private boolean isValidWeapon() {
                if (mc.player == null) return false;
                var item = mc.player.getMainHandStack().getItem();
                return dev.i726.rocky.utils.WorldUtils.isSword(item) || item instanceof AxeItem || item instanceof TridentItem;
        }

        private boolean shouldAllowAttack(HitResult.Type hitType) {
                return switch (hitType) {
                        case ENTITY -> true;
                        case MISS -> !air.getValue();
                        case BLOCK -> !blocks.getValue();
                };
        }

        private boolean isMoving() {
                if (mc.player == null) return false;
                double vx = mc.player.getVelocity().x;
                double vz = mc.player.getVelocity().z;
                return vx * vx + vz * vz > 0.005;
        }

        @Override
        public void onAttack(AttackEvent event) {
                if (mc.player == null || mc.crosshairTarget == null) return;
                
                if (onlyWeapon.getValue() && !isValidWeapon()) return;

                if (requireMoving.getValue() && !isMoving()) return;

                if (!shouldAllowAttack(mc.crosshairTarget.getType())) {
                        // Pass-through chance — occasionally allow the miss to look human
                        int p = passChance.getValueInt();
                        if (p > 0 && random.nextInt(100) < p) return;

                        // Post-cancel cooldown — don't burst-cancel back-to-back misses
                        int cd = cooldown.getValueInt();
                        if (cd > 0 && !cdTimer.delay(cd)) return;

                        event.cancel();
                        cdTimer.reset();
                }
        }
}
