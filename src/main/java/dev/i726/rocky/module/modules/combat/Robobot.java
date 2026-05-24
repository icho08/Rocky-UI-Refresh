package dev.i726.rocky.module.modules.combat;

import com.mojang.authlib.GameProfile;
import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class Robobot extends Module implements AttackListener, TickListener {

    public enum PvPVersion { V1_8, Modern, Custom }

    private final ModeSetting<PvPVersion> version = new ModeSetting<>(EncryptedString.of("PvP Version"), PvPVersion.Modern, PvPVersion.class)
            .setDescription(EncryptedString.of(
                    "1.8 = 6 dmg/hit (diamond sword, no cooldown), Modern = 4 dmg/hit, Custom = slider"));

    private final NumberSetting customDamage = new NumberSetting(EncryptedString.of("Custom Damage"), 0.5, 20.0, 4.0, 0.5)
            .setDescription(EncryptedString.of("Damage per hit when PvP Version is Custom"));

    private final NumberSetting maxHealth = new NumberSetting(EncryptedString.of("Max Health"), 2.0, 40.0, 20.0, 1.0)
            .setDescription(EncryptedString.of("Total HP the Robobot starts with (20 = 10 hearts)"));

    private OtherClientPlayerEntity fakePlayer;
    private float health;

    public Robobot() {
        super(EncryptedString.of("Robobot"),
                EncryptedString.of("Spawns a fake player entity for testing — choose PvP Version to match your target server"),
                -1,
                CategoryManager.AUTOMATION);
        addSettings(version, customDamage, maxHealth);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) return;

        health = maxHealth.getValueFloat();
        fakePlayer = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), "Robobot"));
        fakePlayer.copyPositionAndRotation(mc.player);
        fakePlayer.bodyYaw = mc.player.bodyYaw;
        fakePlayer.setYaw(mc.player.getYaw());
        fakePlayer.setPitch(mc.player.getPitch());
        fakePlayer.setId(-1337);

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            fakePlayer.getInventory().setStack(i, mc.player.getInventory().getStack(i).copy());
        }

        mc.world.addEntity(fakePlayer);
        eventManager.add(AttackListener.class, this);
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(AttackListener.class, this);
        eventManager.remove(TickListener.class, this);
        if (fakePlayer != null && mc.world != null) {
            mc.world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
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

        health -= dmg;

        // Damage animation + sound
        fakePlayer.handleStatus((byte) 2);
        mc.player.playSound(SoundEvents.ENTITY_PLAYER_HURT, 1.0f, 1.0f);

        // Knockback
        double diffX = fakePlayer.getX() - mc.player.getX();
        double diffZ = fakePlayer.getZ() - mc.player.getZ();
        if (diffX != 0 || diffZ != 0) {
            double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
            fakePlayer.setVelocity(new Vec3d(diffX / dist * 0.4, 0.3, diffZ / dist * 0.4));
        }

        if (health <= 0) {
            mc.player.playSound(SoundEvents.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);
            setEnabled(false);
        }
    }

    @Override
    public void onTick() {
        if (fakePlayer == null || mc.world == null) return;

        // Gravity
        if (!fakePlayer.isOnGround()) {
            fakePlayer.setVelocity(fakePlayer.getVelocity().add(0, -0.04, 0));
        }
        // Friction
        fakePlayer.setVelocity(fakePlayer.getVelocity().multiply(0.8, 0.98, 0.8));
        // Physics
        fakePlayer.move(MovementType.SELF, fakePlayer.getVelocity());

        if (mc.player != null && !fakePlayer.isDead()) {
            // Simple step-up on wall collision
            if (fakePlayer.horizontalCollision && fakePlayer.isOnGround()) {
                fakePlayer.setVelocity(fakePlayer.getVelocity().add(0, 0.42, 0));
            }
            // Random rotation drift
            if (Math.random() < 0.02) {
                fakePlayer.setYaw(fakePlayer.getYaw() + (float)(Math.random() * 40 - 20));
            }
        }
    }
}
