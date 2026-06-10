package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.List;

public final class Chams extends Module implements GameRenderListener {

    public final NumberSetting opacity = new NumberSetting(
            EncryptedString.of("Opacity"), 0, 255, 150, 5)
            .setDescription(EncryptedString.of("Fill opacity of the chams overlay"));

    public Chams() {
        super(EncryptedString.of("Chams"),
                EncryptedString.of("See players through walls with a colored overlay"),
                -1,
                CategoryManager.ESP);
        addSettings(opacity);
    }

    @Override
    public void onEnable() {
        eventManager.add(GameRenderListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(GameRenderListener.class, this);
        super.onDisable();
    }

    @Override
    public void onGameRender(GameRenderEvent event) {
        if (mc == null || mc.world == null || mc.player == null) return;

        Color accent = GuiTheme.accent();
        Color fill = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), opacity.getValueInt());

        List<AbstractClientPlayerEntity> players = mc.world.getPlayers();
        float tickDelta = event.delta;

        for (AbstractClientPlayerEntity player : players) {
            if (player == mc.player) continue;

            Vec3d pos = player.getLerpedPos(tickDelta);
            float hw = player.getWidth() / 2f;
            float h  = player.getHeight();

            RenderUtils.renderFilledBox(event.matrices,
                    pos.x - hw, pos.y,     pos.z - hw,
                    pos.x + hw, pos.y + h, pos.z + hw,
                    fill);
        }
    }

    public Color getColor() {
        Color a = GuiTheme.accent();
        return new Color(a.getRed(), a.getGreen(), a.getBlue(), opacity.getValueInt());
    }
}
