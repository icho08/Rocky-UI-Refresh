package dev.i726.rocky.module.modules.combat;

import com.mojang.authlib.GameProfile;
import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import java.util.UUID;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class Robobot extends Module implements AttackListener, TickListener {

    public enum PvPVersion { V1_8, Modern, Custom }

    private final ModeSetting<PvPVersion> version = new ModeSetting<>(EncryptedString.of("PvP Version"), PvPVersion.Modern, PvPVersion.class)
            .setDescription(EncryptedString.of(
                    "1.8 = 6 dmg/hit (diamond sword, no cooldown), Modern = 4 dmg/hit, Custom = slider"));

    private final NumberSetting customDamage = new NumberSetting(EncryptedString.of("Custom Damage"), 0.5, 20.0, 4.0, 0.5)
            .setDescription(EncryptedString.of("Damage per hit when PvP Version is Custom"));

    private final NumberSetting maxHealth = new NumberSetting(EncryptedString.of("Max Health"), 2.0, 40.0, 20.0, 1.0)
            .setDescription(EncryptedString.of("Total HP the Robobot starts with (20 = 10 hearts)"));

    private final dev.i726.rocky.module.setting.BooleanSetting infiniteHealth =
            new dev.i726.rocky.module.setting.BooleanSetting(EncryptedString.of("Infinite Health"), false)
            .setDescription(EncryptedString.of("Bot cannot die — health damage is ignored, useful for extended training"));

    private RemotePlayer fakePlayer;
    private float health;

    public Robobot() {
        super(EncryptedString.of("Robobot"),
                EncryptedString.of("Spawns a fake player entity for testing — choose PvP Version to match your target server"),
                -1,
                CategoryManager.AUTOMATION);
        addSettings(version, customDamage, maxHealth, infiniteHealth);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) return;

        health = maxHealth.getValueFloat();
        fakePlayer = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "Robobot"));
        fakePlayer.copyPosition(mc.player);
        fakePlayer.yBodyRot = mc.player.yBodyRot;
        fakePlayer.setYRot(mc.player.getYRot());
        fakePlayer.setXRot(mc.player.getXRot());
        fakePlayer.setId(-1337);

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            fakePlayer.getInventory().setItem(i, mc.player.getInventory().getItem(i).copy());
        }

        mc.level.addEntity(fakePlayer);
        eventManager.add(AttackListener.class, this);
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(AttackListener.class, this);
        eventManager.remove(TickListener.class, this);
        if (fakePlayer != null && mc.level != null) {
            mc.level.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
            fakePlayer = null;
        }
        super.onDisable();
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (fakePlayer == null || event.getTarget() != fakePlayer) return;

        float dmg = switch (version.getMode()) {
            case V1_8   -> 6.0f;   // diamond sword, 1.8 flat damage
            case Modern -> 4.0f;   // 1.9+ reduced per-hit
            case Custom -> customDamage.getValueFloat();
        };

        if (!infiniteHealth.getValue()) health -= dmg;

        // Damage animation + sound
        fakePlayer.handleEntityEvent((byte) 2);
        mc.player.playSound(SoundEvents.PLAYER_HURT, 1.0f, 1.0f);

        // Knockback
        double diffX = fakePlayer.getX() - mc.player.getX();
        double diffZ = fakePlayer.getZ() - mc.player.getZ();
        if (diffX != 0 || diffZ != 0) {
            double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
            fakePlayer.setDeltaMovement(new Vec3(diffX / dist * 0.4, 0.3, diffZ / dist * 0.4));
        }

        if (!infiniteHealth.getValue() && health <= 0) {
            mc.player.playSound(SoundEvents.PLAYER_DEATH, 1.0f, 1.0f);
            setEnabled(false);
        }
    }

    @Override
    public void onTick() {
        if (fakePlayer == null || mc.level == null) return;

        // Gravity
        if (!fakePlayer.onGround()) {
            fakePlayer.setDeltaMovement(fakePlayer.getDeltaMovement().add(0, -0.04, 0));
        }
        // Friction
        fakePlayer.setDeltaMovement(fakePlayer.getDeltaMovement().multiply(0.8, 0.98, 0.8));
        // Physics
        fakePlayer.move(MoverType.SELF, fakePlayer.getDeltaMovement());

        if (mc.player != null && !fakePlayer.isDeadOrDying()) {
            // Simple step-up on wall collision
            if (fakePlayer.horizontalCollision && fakePlayer.onGround()) {
                fakePlayer.setDeltaMovement(fakePlayer.getDeltaMovement().add(0, 0.42, 0));
            }
            // Random rotation drift
            if (Math.random() < 0.02) {
                fakePlayer.setYRot(fakePlayer.getYRot() + (float)(Math.random() * 40 - 20));
            }
        }
    }
}
