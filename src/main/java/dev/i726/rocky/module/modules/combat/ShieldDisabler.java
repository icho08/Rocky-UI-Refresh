package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.KeybindSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import dev.i726.rocky.utils.MouseSimulation;
import dev.i726.rocky.utils.WorldUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class ShieldDisabler extends Module implements TickListener, AttackListener {
        // Defaults bumped from 0 → 1 so the swap and the hit are at least 1 tick
        // apart. Same-tick swap-then-attack trips strict autoclicker / cadence
        // rules on Vulcan and GrimAC.
        private final NumberSetting hitDelay = new NumberSetting(EncryptedString.of("Hit Delay"), 0, 20, 1, 1);
        private final NumberSetting switchDelay = new NumberSetting(EncryptedString.of("Switch Delay"), 0, 20, 1, 1);
        private final BooleanSetting switchBack = new BooleanSetting(EncryptedString.of("Switch Back"), true);
        private final BooleanSetting stun = new BooleanSetting(EncryptedString.of("Stun"), false);
        private final BooleanSetting clickSimulate = new BooleanSetting(EncryptedString.of("Click Simulation"), false);
        private final BooleanSetting requireHoldAxe = new BooleanSetting(EncryptedString.of("Hold Axe"), false);

        int previousSlot, hitClock, switchClock;

        public ShieldDisabler() {
                super(EncryptedString.of("Shield Breaker"),
                EncryptedString.of("Disables enemy shields with axe"),
                                -1,
                                CategoryManager.PVP);

                addSettings(switchDelay, hitDelay, switchBack, stun, clickSimulate, requireHoldAxe);
        }

        @Override
        public void onEnable() {
                eventManager.add(TickListener.class, this);
                eventManager.add(AttackListener.class, this);

                hitClock = hitDelay.getValueInt();
                switchClock = switchDelay.getValueInt();
                previousSlot = -1;
                super.onEnable();
        }

        @Override
        public void onDisable() {
                eventManager.remove(TickListener.class, this);
                eventManager.remove(AttackListener.class, this);
                super.onDisable();
        }

        @Override
        public void onTick() {
                if (mc.currentScreen != null)
                        return;

                if(requireHoldAxe.getValue() && !(mc.player.getMainHandStack().getItem() instanceof AxeItem))
                        return;

                if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
                        Entity entity = entityHit.getEntity();

                        if (mc.player.isUsingItem())
                                return;

                        if (entity instanceof PlayerEntity player) {
                                if (WorldUtils.isShieldFacingAway(player))
                                        return;

                                if (player.isHolding(Items.SHIELD) && player.isBlocking()) {
                                        if (switchClock > 0) {
                                                if (previousSlot == -1)
                                                        previousSlot = mc.player.getInventory().getSelectedSlot();

                                                switchClock--;
                                                return;
                                        }

                                        if (InventoryUtils.selectAxe()) {
                                                if (hitClock > 0) {
                                                        hitClock--;
                                                } else {
                                                        if (clickSimulate.getValue())
                                                                MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT);

                                                        WorldUtils.hitEntity(player, true);

                                                        if (stun.getValue()) {
                                                                if (clickSimulate.getValue())
                                                                        MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT);

                                                                WorldUtils.hitEntity(player, true);
                                                        }

                                                        hitClock = hitDelay.getValueInt();
                                                        switchClock = switchDelay.getValueInt();
                                                }
                                        }
                                } else if (previousSlot != -1) {
                                        if (switchBack.getValue())
                                                InventoryUtils.setInvSlot(previousSlot);

                                        previousSlot = -1;
                                }
                        }
                }
        }

        @Override
        public void onAttack(AttackListener.AttackEvent event) {
                if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
                        event.cancel();
        }
}
