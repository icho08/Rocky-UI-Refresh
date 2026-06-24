package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.mixin.MinecraftClientAccessor;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.MathUtils;
import dev.i726.rocky.utils.MouseSimulation;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;


public final class AutoClicker extends Module implements TickListener {
        private final BooleanSetting onlyWeapon = new BooleanSetting(EncryptedString.of("Only Weapon"), true)
                        .setDescription(EncryptedString.of("Only left clicks with weapon in hand"));
        private final BooleanSetting onlyBlocks = new BooleanSetting(EncryptedString.of("Only Blocks"), true)
                        .setDescription(EncryptedString.of("Only right clicks blocks"));
        private final BooleanSetting onClick = new BooleanSetting(EncryptedString.of("On Click"), true);

        private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0, 1000, 0, 1);
        private final NumberSetting chance = new NumberSetting(EncryptedString.of("Chance"), 0, 100, 100, 1);
        private final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Actions"), Mode.All, Mode.class);
        private final TimerUtils timer = new TimerUtils();

        public enum Mode {
                All, Left, Right
        }

        public AutoClicker() {
                super(EncryptedString.of("Auto Clicker"),
                EncryptedString.of("Automatically clicks"),
                                -1,
                                CategoryManager.AUTOMATION);

                addSettings(onlyWeapon, onlyBlocks, onClick, delay, chance, mode);
        }

        @Override
        public void onEnable() {
                eventManager.add(TickListener.class, this);
                timer.reset();
                super.onEnable();
        }

        @Override
        public void onDisable() {
                eventManager.remove(TickListener.class, this);
                super.onDisable();
        }

        //using this cuz its faster/instant
        @Override
        public void onTick() {
                if (mc.player == null)
                        return;

                if (mc.screen != null)
                        return;

                if (mc.hitResult == null)
                        return;

                if (timer.delay(delay.getValueFloat()) && chance.getValueInt() >= MathUtils.randomInt(1, 100)) {
                        if (mode.isMode(Mode.Left)) {
                                performLeftClick();
                        }

                        if (mode.isMode(Mode.Right)) {
                                performRightClick();
                        }

                        if (mode.isMode(Mode.All)) {
                                performLeftClick();
                                performRightClick();
                        }
                }
        }

        private void performRightClick() {
                Item mainhand = mc.player.getMainHandItem().getItem();
                Item offhand = mc.player.getOffhandItem().getItem();

                if (mainhand.components().has(DataComponents.FOOD))
                        return;

                if (offhand.components().has(DataComponents.FOOD))
                        return;

                if (mainhand instanceof ProjectileWeaponItem || offhand instanceof ProjectileWeaponItem)
                        return;

                if (onClick.getValue() && GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS)
                        return;

                MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

                ((MinecraftClientAccessor) mc).invokeStartUseItem();
                timer.reset();
        }

        private void performLeftClick() {
                Item mainhand = mc.player.getMainHandItem().getItem();
                Item offhand = mc.player.getOffhandItem().getItem();

                if (mc.hitResult.getType() == HitResult.Type.BLOCK)
                        return;

                if (mc.player.isUsingItem())
                        return;

                if (onlyWeapon.getValue() && !(dev.i726.rocky.utils.WorldUtils.isSword(mainhand) || mainhand instanceof AxeItem))
                        return;

                if (onClick.getValue() && GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
                        return;

                MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT);

                ((MinecraftClientAccessor) mc).invokeStartAttack();
                timer.reset();
        }
}
