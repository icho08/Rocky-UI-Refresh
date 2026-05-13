package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.modules.client.Friends;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.MouseSimulation;
import dev.i726.rocky.utils.TimerUtils;
import dev.i726.rocky.utils.WorldUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public final class TriggerBot extends Module implements TickListener {
    public enum DelayMode {
        Manual,
        Auto
    }

    private final ModeSetting<DelayMode> delayMode = new ModeSetting<>(EncryptedString.of("Delay Mode"), DelayMode.Auto, DelayMode.class)
            .setDescription(EncryptedString.of("Auto uses the game's attack cooldown (recommended for 1.9+), Manual uses custom ms delays"));

    private final BooleanSetting inScreen = new BooleanSetting(EncryptedString.of("Work In Screen"), false);
    private final BooleanSetting whileUse = new BooleanSetting(EncryptedString.of("While Use"), false);
    private final BooleanSetting onLeftClick = new BooleanSetting(EncryptedString.of("On Left Click"), false);
    
    private final MinMaxSetting swordDelay = new MinMaxSetting(EncryptedString.of("Sword Delay"), 0, 1000, 1, 540, 550);
    private final MinMaxSetting axeDelay = new MinMaxSetting(EncryptedString.of("Axe Delay"), 0, 1000, 1, 780, 800);
    
    private final BooleanSetting checkShield = new BooleanSetting(EncryptedString.of("Check Shield"), true);
    private final BooleanSetting swing = new BooleanSetting(EncryptedString.of("Swing Hand"), true);
    private final BooleanSetting allEntities = new BooleanSetting(EncryptedString.of("All Entities"), false);
    private final BooleanSetting weaponOnly = new BooleanSetting(EncryptedString.of("Weapon Only"), true)
            .setDescription(EncryptedString.of("Only attacks if you are holding a weapon (sword/axe/mace)"));
    
    private final BooleanSetting aimJitter = new BooleanSetting(EncryptedString.of("Aim Jitter"), true);
    private final MinMaxSetting jitterYaw = new MinMaxSetting(EncryptedString.of("Yaw Jitter"), 0.0, 5.0, 0.1, 0.4, 1.5);
    private final MinMaxSetting jitterPitch = new MinMaxSetting(EncryptedString.of("Pitch Jitter"), 0.0, 5.0, 0.1, 0.2, 0.8);
    
    private final NumberSetting maxReach = new NumberSetting(EncryptedString.of("Max Reach"), 2.5, 6.0, 3.0, 0.1);
    private final BooleanSetting respectHurtTime = new BooleanSetting(EncryptedString.of("Respect Hurt Time"), true);
    private final NumberSetting targetSwitchDelay = new NumberSetting(EncryptedString.of("Target Switch Delay"), 0, 500, 80, 5);
    private final NumberSetting missChance = new NumberSetting(EncryptedString.of("Miss Chance %"), 0, 30, 0, 1);
    private final BooleanSetting sticky = new BooleanSetting(EncryptedString.of("Same Player"), false)
            .setDescription(EncryptedString.of("Only hits the player that you are currently attacking (good for FFA)"));
    private final BooleanSetting ignoreNpcs = new BooleanSetting(EncryptedString.of("Ignore NPCs"), true)
            .setDescription(EncryptedString.of("Prevents attacking fake players/bots (e.g., Citizens NPCs)"));

    private final TimerUtils timer = new TimerUtils();
    private final TimerUtils switchTimer = new TimerUtils();
    private final Random random = new Random();

    private int currentDelay;
    private int lastTargetId = -1;

    public TriggerBot() {
        super(EncryptedString.of("Trigger Bot"),
                EncryptedString.of("Automatically attacks on crosshair"),
                -1,
                CategoryManager.PVP);
        addSettings(delayMode, inScreen, whileUse, onLeftClick, weaponOnly, swordDelay, axeDelay, checkShield, swing, allEntities, aimJitter, jitterYaw, jitterPitch, maxReach, respectHurtTime, targetSwitchDelay, missChance, sticky, ignoreNpcs);
    }

    @Override
    public void onEnable() {
        rerollDelay();
        lastTargetId = -1;
        switchTimer.reset();
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    private void rerollDelay() {
        if (mc.player == null) return;
        Item item = mc.player.getMainHandStack().getItem();
        if (item instanceof AxeItem) {
            currentDelay = axeDelay.getRandomValueInt();
        } else {
            currentDelay = swordDelay.getRandomValueInt();
        }
    }

    private boolean canHit(Entity target) {
        if (mc.player == null || target == null) return false;
        double dist = mc.player.distanceTo(target);
        if (dist > maxReach.getValue()) return false;
        if (respectHurtTime.getValue() && target instanceof net.minecraft.entity.LivingEntity le && le.hurtTime > 0) return false;

        if (lastTargetId != target.getId()) {
            int cd = targetSwitchDelay.getValueInt();
            if (cd > 0 && !switchTimer.delay(cd)) return false;
            lastTargetId = target.getId();
            switchTimer.reset();
        }

        if (missChance.getValueInt() > 0 && random.nextInt(100) < missChance.getValueInt()) return false;

        if (aimJitter.getValue()) {
            float yawJ = (random.nextFloat() * 2f - 1f) * jitterYaw.getRandomValueFloat();
            float pitchJ = (random.nextFloat() * 2f - 1f) * jitterPitch.getRandomValueFloat();
            if (yawJ != 0f || pitchJ != 0f) {
                mc.player.setYaw(mc.player.getYaw() + yawJ);
                mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + pitchJ, -90, 90));
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), false));
                }
            }
        }
        return true;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (!inScreen.getValue() && mc.currentScreen != null) return;
        if (Rocky.INSTANCE.getModuleManager().getModule(Friends.class).antiAttack.getValue() && Rocky.INSTANCE.getFriendManager().isAimingOverFriend()) return;
        
        // Use Minecraft's native attack key state instead of raw GLFW, which is more reliable
        if (onLeftClick.getValue() && !mc.options.attackKey.isPressed()) return;

        if (!whileUse.getValue() && (mc.player.isUsingItem() || mc.player.isBlocking())) return;

        if (weaponOnly.getValue() && !(WorldUtils.isSword(mc.player.getMainHandStack().getItem()) || mc.player.getMainHandStack().getItem() instanceof AxeItem || mc.player.getMainHandStack().getItem() instanceof MaceItem)) return;

        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = hit.getEntity();
            
            if (sticky.getValue() && (mc.player.getAttacking() == null || entity != mc.player.getAttacking())) return;
            
            if (!(entity instanceof PlayerEntity || (allEntities.getValue() && entity != null))) return;
            
            if (ignoreNpcs.getValue() && entity instanceof PlayerEntity playerTarget) {
                if (playerTarget.getId() != -1337 && mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(playerTarget.getUuid()) == null) {
                    return; // It's an NPC/Bot, so we don't attack
                }
            }

            if (entity instanceof PlayerEntity player && checkShield.getValue() && player.isBlocking() && !WorldUtils.isShieldFacingAway(player)) return;

            boolean ready;
            if (delayMode.isMode(DelayMode.Auto)) {
                // In 1.9+, wait for attack cooldown to be full
                ready = mc.player.getAttackCooldownProgress(0.0f) >= 1.0f;
            } else {
                ready = timer.delay(currentDelay);
            }

            if (ready) {
                if (!canHit(entity)) return;
                WorldUtils.hitEntity(entity, swing.getValue());
                rerollDelay();
                timer.reset();
            }
        }
    }
}
