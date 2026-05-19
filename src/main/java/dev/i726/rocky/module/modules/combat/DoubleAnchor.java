package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.BlockUtils;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public final class DoubleAnchor extends Module implements TickListener {

    public DoubleAnchor() {
        super(EncryptedString.of("Double Anchor"),
                EncryptedString.of("Activates respawn anchor twice in one tick"),
                -1, CategoryManager.CRYSTAL);
    }

    private BlockPos lastPos;
    private int count;

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        lastPos = null;
        count   = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null || mc.player == null || mc.world == null) return;

        if (!mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) return;

        if (!(mc.crosshairTarget instanceof BlockHitResult h)) return;

        if (!BlockUtils.isAnchorCharged(h.getBlockPos())) return;

        if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                != GLFW.GLFW_PRESS) return;

        // Deduplicate per-block so we only double-send once per right-click session
        if (h.getBlockPos().equals(lastPos)) {
            if (count >= 1) return;
        } else {
            lastPos = h.getBlockPos();
            count   = 0;
        }

        // Use interactBlock for proper sequence tracking — avoids 1.21 desync
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, h);
        count++;
    }
}
