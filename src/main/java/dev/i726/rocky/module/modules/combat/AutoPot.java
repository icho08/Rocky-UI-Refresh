package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;

public final class AutoPot extends Module implements TickListener {

    public enum PotionType { Health, Strength, Speed, FireResistance }

    private enum State { IDLE, SWITCHING, PITCHING, THROWING }

    private final ModeSetting<PotionType> potionType = new ModeSetting<>(
            EncryptedString.of("Potion Type"), PotionType.Health, PotionType.class);

    private final NumberSetting healthPercent = new NumberSetting(
            EncryptedString.of("Health %"), 10, 95, 50, 5)
            .setDescription(EncryptedString.of("Throw pot when health drops below this %"));

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 0, 500, 80, 10)
            .setDescription(EncryptedString.of("Milliseconds between each state transition"));

    private final BooleanSetting switchBack = new BooleanSetting(
            EncryptedString.of("Switch Back"), true);

    private final BooleanSetting lookDown = new BooleanSetting(
            EncryptedString.of("Look Down"), true)
            .setDescription(EncryptedString.of("Look down so the potion lands at your feet"));

    private State     currentState = State.IDLE;
    private int       previousSlot = -1;
    private float     previousPitch = -1;
    private final TimerUtils stateTimer = new TimerUtils();

    public AutoPot() {
        super(EncryptedString.of("Auto Pot"),
                EncryptedString.of("Automatically throws splash potions"),
                -1, CategoryManager.INVENTORY);
        addSettings(potionType, healthPercent, delay, switchBack, lookDown);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        restoreState();
        super.onDisable();
    }

    private void reset() {
        currentState  = State.IDLE;
        previousSlot  = -1;
        previousPitch = -1;
        stateTimer.reset();
    }

    private void restoreState() {
        if (mc.player == null) return;
        if (previousSlot != -1) {
            InventoryUtils.setInvSlot(previousSlot);
            previousSlot = -1;
        }
        if (previousPitch != -1) {
            // Send look packet to restore server-side pitch
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                    mc.player.yRot(), previousPitch, mc.player.onGround(), false));
            mc.player.setXRot(previousPitch);
            previousPitch = -1;
        }
    }

    private boolean shouldUsePot() {
        if (mc.player == null) return false;
        float hp = (mc.player.getHealth() / mc.player.getMaxHealth()) * 100;
        return switch (potionType.getMode()) {
            case Health         -> hp <= healthPercent.getValue();
            case Strength       -> !mc.player.hasEffect(MobEffects.STRENGTH);
            case Speed          -> !mc.player.hasEffect(MobEffects.SPEED);
            case FireResistance -> !mc.player.hasEffect(MobEffects.FIRE_RESISTANCE);
        };
    }

    private int findPotionSlot() {
        return switch (potionType.getMode()) {
            case Health         -> InventoryUtils.findSplash(MobEffects.INSTANT_HEALTH.value(), 1, 1);
            case Strength       -> InventoryUtils.findSplash(MobEffects.STRENGTH.value(), 1, 1);
            case Speed          -> InventoryUtils.findSplash(MobEffects.SPEED.value(), 1, 1);
            case FireResistance -> InventoryUtils.findSplash(MobEffects.FIRE_RESISTANCE.value(), 1, 1);
        };
    }

    private boolean hasCorrectPotion() {
        if (mc.player == null) return false;
        return switch (potionType.getMode()) {
            case Health         -> InventoryUtils.isThatSplash(MobEffects.INSTANT_HEALTH.value(), 1, 1, mc.player.getMainHandItem());
            case Strength       -> InventoryUtils.isThatSplash(MobEffects.STRENGTH.value(), 1, 1, mc.player.getMainHandItem());
            case Speed          -> InventoryUtils.isThatSplash(MobEffects.SPEED.value(), 1, 1, mc.player.getMainHandItem());
            case FireResistance -> InventoryUtils.isThatSplash(MobEffects.FIRE_RESISTANCE.value(), 1, 1, mc.player.getMainHandItem());
        };
    }

    @Override
    public void onTick() {
        if (mc.screen != null || mc.player == null) return;

        if (!shouldUsePot()) {
            if (currentState != State.IDLE) restoreState();
            currentState = State.IDLE;
            return;
        }

        if (!stateTimer.delay((int) delay.getValue())) return;
        stateTimer.reset();

        switch (currentState) {
            case IDLE -> {
                if (hasCorrectPotion()) {
                    currentState = State.PITCHING;
                } else {
                    if (switchBack.getValue())
                        previousSlot = mc.player.getInventory().getSelectedSlot();
                    currentState = State.SWITCHING;
                }
            }

            case SWITCHING -> {
                int slot = findPotionSlot();
                if (slot != -1) {
                    InventoryUtils.setInvSlot(slot);
                    currentState = State.PITCHING;
                } else {
                    currentState = State.IDLE;
                }
            }

            case PITCHING -> {
                if (lookDown.getValue()) {
                    if (previousPitch == -1) previousPitch = mc.player.xRot();
                    // Send server-side look-down packet AND update client pitch
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                            mc.player.yRot(), 90f, mc.player.onGround(), false));
                    mc.player.setXRot(90f);
                }
                currentState = State.THROWING;
            }

            case THROWING -> {
                if (hasCorrectPotion()) {
                    InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);
                }
                restoreState();
                currentState = State.IDLE;
            }
        }
    }
}
