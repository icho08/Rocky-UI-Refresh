package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.mixin.MinecraftClientAccessor;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.KeybindSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.item.BlockItem;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public final class DragClick extends Module implements TickListener {
    private final KeybindSetting dragKey = new KeybindSetting(EncryptedString.of("Drag Key"), GLFW.GLFW_KEY_G, false);
    private final NumberSetting cps = new NumberSetting(EncryptedString.of("CPS"), 1, 20, 12, 1);
    private final BooleanSetting onlyBlocks = new BooleanSetting(EncryptedString.of("Only Blocks"), true);
    
    private final TimerUtils timer = new TimerUtils();
    private boolean isDragging = false;

    public DragClick() {
        super(EncryptedString.of("Drag Click"),
                EncryptedString.of("Simulates drag clicking"),
                -1,
                CategoryManager.AUTOMATION);

        addSettings(dragKey, cps, onlyBlocks);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        isDragging = false;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null)
            return;

        boolean keyPressed = GLFW.glfwGetKey(mc.getWindow().getHandle(), dragKey.getKey()) == GLFW.GLFW_PRESS;
        
        if (keyPressed && !isDragging) {
            isDragging = true;
            timer.reset();
        } else if (!keyPressed) {
            isDragging = false;
        }

        if (isDragging && timer.delay(1000f / cps.getValueFloat())) {
            performDragClick();
            timer.reset();
        }
    }

    private void performDragClick() {
        if (onlyBlocks.getValue() && !(mc.player.getMainHandStack().getItem() instanceof BlockItem))
            return;

        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            ((MinecraftClientAccessor) mc).invokeDoItemUse();
        }
    }
}
