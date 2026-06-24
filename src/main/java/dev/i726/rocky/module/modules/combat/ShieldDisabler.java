package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.KeybindSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import dev.i726.rocky.utils.KeyUtils;
import dev.i726.rocky.utils.MouseSimulation;
import dev.i726.rocky.utils.WorldUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class ShieldDisabler extends Module implements TickListener {

    private final KeybindSetting activateKey = new KeybindSetting(
            EncryptedString.of("Activate Key"), -1, false)
            .setDescription(EncryptedString.of("Hold to activate shield breaking. -1 = always active when module is on"));

    private final NumberSetting hitDelay = new NumberSetting(
            EncryptedString.of("Hit Delay"), 0, 20, 1, 1)
            .setDescription(EncryptedString.of("Ticks between axe hit and shield-break confirmation"));

    private final NumberSetting switchDelay = new NumberSetting(
            EncryptedString.of("Switch Delay"), 0, 20, 1, 1)
            .setDescription(EncryptedString.of("Ticks to wait after switching to the axe before hitting"));

    private final BooleanSetting switchBack = new BooleanSetting(
            EncryptedString.of("Switch Back"), true)
            .setDescription(EncryptedString.of("Return to original slot after the shield breaks"));

    private final BooleanSetting stun = new BooleanSetting(
            EncryptedString.of("Stun"), false)
            .setDescription(EncryptedString.of("Strike twice to stun — adds a second hit after the first"));

    private final NumberSetting stunDelay = new NumberSetting(
            EncryptedString.of("Stun Delay"), 1, 10, 3, 1)
            .setDescription(EncryptedString.of("Ticks between the first and second stun hit"));

    private final BooleanSetting clickSimulate = new BooleanSetting(
            EncryptedString.of("Click Simulation"), false)
            .setDescription(EncryptedString.of("Simulate a left click for legit CPS counters"));

    private int previousSlot = -1;
    private int hitClock     = 0;
    private int switchClock  = 0;
    private int stunClock    = 0;
    private boolean stunPending = false;

    public ShieldDisabler() {
        super(EncryptedString.of("Shield Breaker"),
                EncryptedString.of("Disables enemy shields using an axe"),
                -1, CategoryManager.PVP);
        addSettings(activateKey, switchDelay, hitDelay, switchBack, stun, stunDelay, clickSimulate);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        restoreSlot();
        super.onDisable();
    }

    private void resetState() {
        hitClock     = hitDelay.getValueInt();
        switchClock  = switchDelay.getValueInt();
        stunClock    = 0;
        stunPending  = false;
        previousSlot = -1;
    }

    private void restoreSlot() {
        if (switchBack.getValue() && previousSlot != -1) {
            InventoryUtils.setInvSlot(previousSlot);
        }
        previousSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.screen != null || mc.player == null) return;

        // Keybind gate — if a key is bound, require it held
        if (activateKey.getKey() != -1 && !KeyUtils.isKeyPressed(activateKey.getKey())) {
            restoreSlot();
            resetState();
            return;
        }

        if (!(mc.hitResult instanceof EntityHitResult entityHit)) {
            restoreSlot();
            resetState();
            return;
        }

        Entity entity = entityHit.getEntity();
        if (!(entity instanceof Player player)) {
            restoreSlot();
            resetState();
            return;
        }

        if (mc.player.isUsingItem()) return;

        // Target is not blocking — restore slot and reset
        if (!(player.isHolding(Items.SHIELD) && player.isBlocking())
                || WorldUtils.isShieldFacingAway(player)) {
            restoreSlot();
            resetState();
            return;
        }

        // Handle pending stun second hit
        if (stunPending) {
            if (stunClock > 0) { stunClock--; return; }
            if (clickSimulate.getValue())
                MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            WorldUtils.hitEntity(player, true);
            stunPending = false;
            return;
        }

        // Switch delay before swapping to axe
        if (switchClock > 0) {
            if (previousSlot == -1)
                previousSlot = mc.player.getInventory().getSelectedSlot();
            switchClock--;
            return;
        }

        if (!InventoryUtils.selectAxe()) return; // No axe found

        // Hit delay after axe is selected
        if (hitClock > 0) { hitClock--; return; }

        // Deliver the shield-breaking hit
        if (clickSimulate.getValue())
            MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT);

        WorldUtils.hitEntity(player, true);

        if (stun.getValue()) {
            stunPending = true;
            stunClock   = stunDelay.getValueInt();
        }

        hitClock    = hitDelay.getValueInt();
        switchClock = switchDelay.getValueInt();
    }
}
