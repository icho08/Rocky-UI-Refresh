package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import java.awt.Color;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

public final class Tracers extends Module implements GameRenderListener {

    private final BooleanSetting players = new BooleanSetting(
            EncryptedString.of("Players"), true
    ).setDescription(EncryptedString.of("Draw tracers to other players"));

    private final BooleanSetting mobs = new BooleanSetting(
            EncryptedString.of("Mobs"), true
    ).setDescription(EncryptedString.of("Draw tracers to mobs"));

    private final BooleanSetting hostilesOnly = new BooleanSetting(
            EncryptedString.of("Hostiles Only"), true
    ).setDescription(EncryptedString.of("Only draw tracers to hostile mobs"));

    private final BooleanSetting items = new BooleanSetting(
            EncryptedString.of("Items"), false
    ).setDescription(EncryptedString.of("Draw tracers to dropped items"));

    private final NumberSetting maxRange = new NumberSetting(
            EncryptedString.of("Range"), 10, 512, 128, 8
    ).setDescription(EncryptedString.of("Maximum tracer render distance"));

    public Tracers() {
        super(
                EncryptedString.of("Tracers"),
                EncryptedString.of("Draws lines to nearby entities"),
                -1,
                CategoryManager.ESP
        );
        addSettings(players, mobs, hostilesOnly, items, maxRange);
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
        if (mc == null || mc.level == null || mc.player == null) return;

        Color accent = GuiTheme.accent();
        Color tracerColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 200);
        double rangeSq = maxRange.getValue() * maxRange.getValue();
        float tickDelta = event.delta;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (mc.player.distanceToSqr(entity) > rangeSq) continue;

            Vec3 lerpedPos = entity.getPosition(tickDelta);
            Vec3 center = lerpedPos.add(0, entity.getBbHeight() / 2.0, 0);

            if (players.getValue() && entity instanceof AbstractClientPlayer player) {
                if (player == mc.player) continue;
                RenderUtils.drawTracer(event.matrices, center, tracerColor);
            } else if (mobs.getValue() && entity instanceof Mob mob) {
                if (hostilesOnly.getValue() && !(mob instanceof Monster)) continue;
                RenderUtils.drawTracer(event.matrices, center, tracerColor);
            } else if (items.getValue() && entity instanceof ItemEntity) {
                RenderUtils.drawTracer(event.matrices, center, tracerColor);
            }
        }
    }
}
