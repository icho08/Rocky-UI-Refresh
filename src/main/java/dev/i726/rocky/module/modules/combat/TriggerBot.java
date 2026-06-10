package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.modules.client.Friends;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import dev.i726.rocky.utils.WorldUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public final class TriggerBot extends Module implements TickListener {

    private final BooleanSetting inScreen    = new BooleanSetting(EncryptedString.of("Work In Screen"), false);
    private final BooleanSetting whileUse    = new BooleanSetting(EncryptedString.of("While Use"), false);

    private final MinMaxSetting swordDelay = new MinMaxSetting(EncryptedString.of("Sword Delay"), 0, 1000, 1, 540, 550);
    private final MinMaxSetting axeDelay   = new MinMaxSetting(EncryptedString.of("Axe Delay"), 0, 1000, 1, 780, 800);

    private final BooleanSetting checkShield  = new BooleanSetting(EncryptedString.of("Check Shield"), true);
    private final BooleanSetting swing        = new BooleanSetting(EncryptedString.of("Swing Hand"), true);
    private final BooleanSetting allEntities  = new BooleanSetting(EncryptedString.of("All Entities"), false);
    private final BooleanSetting weaponOnly   = new BooleanSetting(EncryptedString.of("Weapon Only"), true)
            .setDescription(EncryptedString.of("Only attacks if you are holding a weapon (sword/axe/mace)"));

    // Jitter is now post-attack — applies a subtle rotation drift AFTER hitting to humanise timing.
    // It is NEVER applied at hit-time so it cannot desync the server raytrace.
    private final BooleanSetting aimJitter  = new BooleanSetting(EncryptedString.of("Aim Jitter"), true)
            .setDescription(EncryptedString.of("Applies a small rotation drift AFTER each hit — never desync-inducing"));
    private final MinMaxSetting jitterYaw   = new MinMaxSetting(EncryptedString.of("Yaw Jitter"), 0.0, 5.0, 0.1, 0.4, 1.5);
    private final MinMaxSetting jitterPitch = new MinMaxSetting(EncryptedString.of("Pitch Jitter"), 0.0, 5.0, 0.1, 0.2, 0.8);

    private final NumberSetting maxReach          = new NumberSetting(EncryptedString.of("Max Reach"), 2.5, 6.0, 3.0, 0.1);
    private final BooleanSetting respectHurtTime  = new BooleanSetting(EncryptedString.of("Respect Hurt Time"), true);
    private final NumberSetting targetSwitchDelay = new NumberSetting(EncryptedString.of("Target Switch Delay"), 0, 500, 80, 5);
    private final NumberSetting missChance        = new NumberSetting(EncryptedString.of("Miss Chance %"), 0, 30, 0, 1);
    private final BooleanSetting sticky           = new BooleanSetting(EncryptedString.of("Same Player"), false)
            .setDescription(EncryptedString.of("Only hits the player you are currently attacking (good for FFA)"));
    private final BooleanSetting ignoreNpcs       = new BooleanSetting(EncryptedString.of("Ignore NPCs"), true)
            .setDescription(EncryptedString.of("Prevents attacking fake players/bots (e.g. Citizens NPCs)"));

    private final TimerUtils timer       = new TimerUtils();
    private final TimerUtils switchTimer = new TimerUtils();
    private final Random     random      = new Random();

    private int  currentDelay;
    private int  lastTargetId = -1;

    public TriggerBot() {
        super(EncryptedString.of("Trigger Bot"),
                EncryptedString.of("Automatically attacks when an enemy is on your crosshair"),
                -1, CategoryManager.PVP);
        addSettings(inScreen, whileUse, weaponOnly,
                swordDelay, axeDelay, checkShield, swing, allEntities,
                aimJitter, jitterYaw, jitterPitch,
                maxReach, respectHurtTime, targetSwitchDelay, missChance, sticky, ignoreNpcs);
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
        currentDelay = (item instanceof AxeItem) ? axeDelay.getRandomValueInt() : swordDelay.getRandomValueInt();
    }

    /**
     * Pure validation — NO rotation changes happen here.
     * Jitter is applied AFTER the attack packet is sent to prevent server-side ghost hits.
     */
    private boolean canHit(Entity target) {
        if (mc.player == null || target == null) return false;

        // Use eye-to-entity-centre distance, same reference point the server uses
        double dist = mc.player.getEyePos().distanceTo(target.getEyePos());
        // Allow Reach module to extend TBot's hit window when it is enabled
        double reachLimit = maxReach.getValue();
        Reach reachMod = Rocky.INSTANCE.getModuleManager().getModule(Reach.class);
        if (reachMod != null && reachMod.isEnabled()) {
            reachLimit = Math.max(reachLimit, reachMod.getReach() + 0.3);
        }
        if (dist > reachLimit) return false;

        if (respectHurtTime.getValue() && target instanceof LivingEntity le && le.hurtTime > 0) return false;

        if (lastTargetId != target.getId()) {
            int cd = targetSwitchDelay.getValueInt();
            if (cd > 0 && !switchTimer.delay(cd)) return false;
            lastTargetId = target.getId();
            switchTimer.reset();
        }

        if (missChance.getValueInt() > 0 && random.nextInt(100) < missChance.getValueInt()) return false;

        return true;
    }

    /**
     * Apply post-hit jitter — called AFTER the attack packet is sent.
     * The rotation drift is cosmetic / humanising; it does not affect hit registration.
     */
    private void applyPostHitJitter() {
        if (!aimJitter.getValue() || mc.player == null) return;

        float yawJ   = (random.nextFloat() * 2f - 1f) * jitterYaw.getRandomValueFloat();
        float pitchJ = (random.nextFloat() * 2f - 1f) * jitterPitch.getRandomValueFloat();
        if (yawJ == 0f && pitchJ == 0f) return;

        mc.player.setYaw(mc.player.getYaw() + yawJ);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + pitchJ, -90, 90));

        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), false));
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (!inScreen.getValue() && mc.currentScreen != null) return;
        if (Rocky.INSTANCE.getModuleManager().getModule(Friends.class).antiAttack.getValue()
                && Rocky.INSTANCE.getFriendManager().isAimingOverFriend()) return;

        if (!whileUse.getValue() && (mc.player.isUsingItem() || mc.player.isBlocking())) return;

        if (weaponOnly.getValue()) {
            Item held = mc.player.getMainHandStack().getItem();
            if (!(WorldUtils.isSword(held) || held instanceof AxeItem || held instanceof MaceItem)) return;
        }

        if (!(mc.crosshairTarget instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY)) return;

        Entity entity = hit.getEntity();
        if (entity == null) return;

        if (sticky.getValue() && (mc.player.getAttacking() == null || entity != mc.player.getAttacking())) return;
        if (!(entity instanceof PlayerEntity || allEntities.getValue())) return;

        if (ignoreNpcs.getValue() && entity instanceof PlayerEntity pt && mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(pt.getUuid());
            if (entry == null || entry.getLatency() <= 0) return;
        }

        if (entity instanceof PlayerEntity player && checkShield.getValue()
                && player.isBlocking() && !WorldUtils.isShieldFacingAway(player)) return;

        if (!timer.delay(currentDelay)) return;
        if (!canHit(entity)) return;

        // ── Hit first, jitter after ──────────────────────────────────────────
        WorldUtils.hitEntity(entity, swing.getValue());
        applyPostHitJitter();

        rerollDelay();
        timer.reset();
    }
}
