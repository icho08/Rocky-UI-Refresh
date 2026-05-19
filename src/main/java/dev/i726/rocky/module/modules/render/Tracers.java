package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;

import java.awt.Color;

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
        if (mc == null || mc.world == null || mc.player == null) return;

        Color accent = GuiTheme.accent();
        Color tracerColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 200);
        double rangeSq = maxRange.getValue() * maxRange.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (mc.player.squaredDistanceTo(entity) > rangeSq) continue;

            if (players.getValue() && entity instanceof AbstractClientPlayerEntity player) {
                if (player == mc.player) continue;
                RenderUtils.drawTracer(event.matrices, player.getBoundingBox().getCenter(), tracerColor);
            } else if (mobs.getValue() && entity instanceof MobEntity mob) {
                if (hostilesOnly.getValue() && !(mob instanceof HostileEntity)) continue;
                RenderUtils.drawTracer(event.matrices, mob.getBoundingBox().getCenter(), tracerColor);
            } else if (items.getValue() && entity instanceof ItemEntity item) {
                RenderUtils.drawTracer(event.matrices, item.getBoundingBox().getCenter(), tracerColor);
            }
        }
    }
}
