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
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

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
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    mc.player.getYaw(), previousPitch, mc.player.isOnGround(), false));
            mc.player.setPitch(previousPitch);
            previousPitch = -1;
        }
    }

    private boolean shouldUsePot() {
        if (mc.player == null) return false;
        float hp = (mc.player.getHealth() / mc.player.getMaxHealth()) * 100;
        return switch (potionType.getMode()) {
            case Health         -> hp <= healthPercent.getValue();
            case Strength       -> !mc.player.hasStatusEffect(StatusEffects.STRENGTH);
            case Speed          -> !mc.player.hasStatusEffect(StatusEffects.SPEED);
            case FireResistance -> !mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
        };
    }

    private int findPotionSlot() {
        return switch (potionType.getMode()) {
            case Health         -> InventoryUtils.findSplash(StatusEffects.INSTANT_HEALTH.value(), 1, 1);
            case Strength       -> InventoryUtils.findSplash(StatusEffects.STRENGTH.value(), 1, 1);
            case Speed          -> InventoryUtils.findSplash(StatusEffects.SPEED.value(), 1, 1);
            case FireResistance -> InventoryUtils.findSplash(StatusEffects.FIRE_RESISTANCE.value(), 1, 1);
        };
    }

    private boolean hasCorrectPotion() {
        if (mc.player == null) return false;
        return switch (potionType.getMode()) {
            case Health         -> InventoryUtils.isThatSplash(StatusEffects.INSTANT_HEALTH.value(), 1, 1, mc.player.getMainHandStack());
            case Strength       -> InventoryUtils.isThatSplash(StatusEffects.STRENGTH.value(), 1, 1, mc.player.getMainHandStack());
            case Speed          -> InventoryUtils.isThatSplash(StatusEffects.SPEED.value(), 1, 1, mc.player.getMainHandStack());
            case FireResistance -> InventoryUtils.isThatSplash(StatusEffects.FIRE_RESISTANCE.value(), 1, 1, mc.player.getMainHandStack());
        };
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null || mc.player == null) return;

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
                    if (previousPitch == -1) previousPitch = mc.player.getPitch();
                    // Send server-side look-down packet AND update client pitch
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                            mc.player.getYaw(), 90f, mc.player.isOnGround(), false));
                    mc.player.setPitch(90f);
                }
                currentState = State.THROWING;
            }

            case THROWING -> {
                if (hasCorrectPotion()) {
                    ActionResult result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
                }
                restoreState();
                currentState = State.IDLE;
            }
        }
    }
}
