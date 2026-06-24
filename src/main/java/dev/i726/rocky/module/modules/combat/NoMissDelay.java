package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import java.util.Random;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.HitResult;

public final class NoMissDelay extends Module implements AttackListener {

    private final BooleanSetting onlyWeapon = new BooleanSetting(
            EncryptedString.of("Only Weapon"), true);

    private final BooleanSetting air = new BooleanSetting(
            EncryptedString.of("Air"), true)
            .setDescription(EncryptedString.of("Cancel swings aimed at air"));

    private final BooleanSetting blocks = new BooleanSetting(
            EncryptedString.of("Blocks"), false)
            .setDescription(EncryptedString.of("Cancel swings aimed at blocks"));

    private final NumberSetting passChance = new NumberSetting(
            EncryptedString.of("Pass-Through %"), 0, 100, 5, 1)
            .setDescription(EncryptedString.of("Chance to let a miss through anyway — makes pattern look more human"));

    private final NumberSetting cooldown = new NumberSetting(
            EncryptedString.of("Post-Cancel CD"), 0, 500, 80, 5)
            .setDescription(EncryptedString.of("ms before we can cancel another miss (avoids burst-cancel spam)"));

    private final BooleanSetting requireMoving = new BooleanSetting(
            EncryptedString.of("Require Moving"), false)
            .setDescription(EncryptedString.of("Only cancel misses while moving"));

    private final TimerUtils cdTimer = new TimerUtils();
    private final Random     random  = new Random();

    // Track whether the post-cancel cooldown has been armed at least once
    private boolean cdArmed = false;

    public NoMissDelay() {
        super(EncryptedString.of("No Miss Delay"),
                EncryptedString.of("Removes attack penalty on miss"),
                -1, CategoryManager.PVP);
        addSettings(onlyWeapon, air, blocks, passChance, cooldown, requireMoving);
    }

    @Override
    public void onEnable() {
        // Keep cdArmed false so the FIRST miss is always cancellable.
        // It only arms after we cancel one, preventing a burst-cancel in the next cd ms.
        cdArmed = false;
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
        var item = mc.player.getMainHandItem().getItem();
        return dev.i726.rocky.utils.WorldUtils.isSword(item)
                || item instanceof AxeItem
                || item instanceof TridentItem;
    }

    private boolean shouldBlock(HitResult.Type hitType) {
        return switch (hitType) {
            case ENTITY -> false;         // Entity hits are always allowed through
            case MISS   -> air.getValue();
            case BLOCK  -> blocks.getValue();
        };
    }

    private boolean isMoving() {
        if (mc.player == null) return false;
        double vx = mc.player.getDeltaMovement().x;
        double vz = mc.player.getDeltaMovement().z;
        return vx * vx + vz * vz > 0.005;
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (mc.player == null || mc.hitResult == null) return;
        if (onlyWeapon.getValue() && !isValidWeapon()) return;
        if (requireMoving.getValue() && !isMoving()) return;

        if (!shouldBlock(mc.hitResult.getType())) return;

        // Occasionally let a miss through to appear human
        int p = passChance.getValueInt();
        if (p > 0 && random.nextInt(100) < p) return;

        // Respect post-cancel cooldown — don't burst-cancel on every consecutive miss
        int cd = cooldown.getValueInt();
        if (cd > 0 && cdArmed && !cdTimer.delay(cd)) return;

        event.cancel();
        cdTimer.reset();
        cdArmed = true;
    }
}
