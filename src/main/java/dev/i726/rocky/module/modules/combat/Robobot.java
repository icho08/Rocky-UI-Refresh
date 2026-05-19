package dev.i726.rocky.module.modules.combat;

import com.mojang.authlib.GameProfile;
import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class Robobot extends Module implements AttackListener, TickListener {
    private OtherClientPlayerEntity fakePlayer;
    private float health = 100f; // Increased health

    public Robobot() {
        super(EncryptedString.of("Robobot"),
                EncryptedString.of("Spawns a fake player entity for testing"),
                -1,
                CategoryManager.AUTOMATION);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) return;
        
        health = 100f;
        fakePlayer = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), "Robobot"));
        fakePlayer.copyPositionAndRotation(mc.player);
        fakePlayer.bodyYaw = mc.player.bodyYaw;
        fakePlayer.setYaw(mc.player.getYaw());
        fakePlayer.setPitch(mc.player.getPitch());
        fakePlayer.setId(-1337);
        
        // Manual inventory copy
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

        // Simulate damage
        health -= 1.0f; // Half heart per hit to make it last longer
        
        // Damage animation (red flash)
        fakePlayer.handleStatus((byte) 2);
        
        // Damage sound
        mc.player.playSound(SoundEvents.ENTITY_PLAYER_HURT, 1.0f, 1.0f);

        // Apply Knockback
        if (mc.player != null) {
            double diffX = fakePlayer.getX() - mc.player.getX();
            double diffZ = fakePlayer.getZ() - mc.player.getZ();
            if (diffX != 0 || diffZ != 0) {
                double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
                double kb = 0.4; // Standard knockback strength
                fakePlayer.setVelocity(new Vec3d(diffX / dist * kb, 0.3, diffZ / dist * kb));
            }
        }

        if (health <= 0) {
            mc.player.playSound(SoundEvents.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);
            setEnabled(false);
        }
    }

    @Override
    public void onTick() {
        if (fakePlayer == null || mc.world == null) return;

        // Apply basic physics (gravity and movement)
        if (!fakePlayer.isOnGround()) {
            fakePlayer.setVelocity(fakePlayer.getVelocity().add(0, -0.04, 0));
        }
        
        // Apply friction
        fakePlayer.setVelocity(fakePlayer.getVelocity().multiply(0.8, 0.98, 0.8));
        
        // Move the entity
        fakePlayer.move(MovementType.SELF, fakePlayer.getVelocity());

        // Simple wandering logic
        if (mc.player != null && !fakePlayer.isDead()) {
            // Occasionally jump if it hits a wall (simple step-up)
            if (fakePlayer.horizontalCollision && fakePlayer.isOnGround()) {
                fakePlayer.setVelocity(fakePlayer.getVelocity().add(0, 0.42, 0));
            }
            
            // Randomly rotate a bit
            if (Math.random() < 0.02) {
                fakePlayer.setYaw(fakePlayer.getYaw() + (float)(Math.random() * 40 - 20));
            }
        }
    }
}
